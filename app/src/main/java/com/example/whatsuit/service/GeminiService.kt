package com.example.whatsuit.service

import android.content.Context
import android.util.Log
import com.example.whatsuit.data.AppDatabase
import com.example.whatsuit.data.ConversationHistory
import com.example.whatsuit.data.GeminiConfig
import com.example.whatsuit.data.PromptTemplate
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GeminiService(private val context: Context) {
    private companion object {
        private const val TAG = "GeminiService"
        private const val DEFAULT_HISTORY_LIMIT = 10
        private const val MAX_WORDS = 50
    }

    private val database = AppDatabase.getDatabase(context)
    private val geminiDao = database.geminiDao()
    private var generativeModel: GenerativeModel? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    interface ResponseCallback {
        fun onPartialResponse(text: String)
        fun onComplete(fullResponse: String)
        fun onError(error: Throwable)
    }

    private val initMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile
    private var isInitialized = false
    private var initializationDeferred: kotlinx.coroutines.Deferred<Boolean>? = null

    // Java-friendly initialization
    private suspend fun doInitialize(): Boolean {
        return try {
            val config = geminiDao.getConfig()
            if (config != null && !config.apiKey.isNullOrEmpty()) {
                Log.d(TAG, "Initializing Gemini with model: ${config.modelName}")
                generativeModel = GenerativeModel(
                    modelName = config.modelName,
                    apiKey = config.apiKey
                )
                isInitialized = true
                Log.d(TAG, "Gemini initialization successful")
                true
            } else {
                Log.w(TAG, "Missing Gemini configuration - will skip initialization")
                isInitialized = false
                generativeModel = null
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Gemini", e)
            isInitialized = false
            generativeModel = null
            false
        }
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        initMutex.withLock {
            if (isInitialized) return@withLock true

            val existingDeferred = initializationDeferred
            if (existingDeferred?.isActive == true) {
                try {
                    return@withLock existingDeferred.await()
                } catch (e: Exception) {
                    Log.w(TAG, "Previous initialization failed, will retry", e)
                    // Continue to create a new deferred
                }
            }

            val newDeferred = scope.async {
                doInitialize()
            }
            
            initializationDeferred = newDeferred
            
            try {
                return@withLock newDeferred.await()
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                if (initializationDeferred === newDeferred) {
                    initializationDeferred = null
                }
                return@withLock false
            }
        }
    }

    private suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            initialize()
        }
    }

    // Java-friendly reply generation
    fun generateReply(
        notificationId: Long,
        message: String,
        callback: ResponseCallback
    ) = scope.launch {
        try {
            ensureInitialized()
            val config = geminiDao.getConfig() ?: throw IllegalStateException("Gemini not configured")
            Log.d(TAG, "Retrieved Gemini config: modelName=${config.modelName}, historyLimit=${config.maxHistoryPerThread}")
            val model = generativeModel ?: throw IllegalStateException("Gemini model not initialized")
            
            // Get conversation history
            val history = geminiDao.getConversationHistory(
                notificationId = notificationId,
                limit = config.maxHistoryPerThread
            )
            
            // Get active prompt template
            val template = geminiDao.getActiveTemplate() ?: PromptTemplate.createDefault()
            Log.d(TAG, "Using prompt template: ${template.name}")
            
            // Build context from history
            val context = buildHistoryContext(history)
            
            // Generate prompt using template
            val prompt = PromptTemplate.processTemplate(
                template = template.template,
                context = context,
                message = message
            )
            
            Log.d(TAG, "Generating reply for message: $message")
            Log.d(TAG, "Using history context: $context")
            Log.d(TAG, "Generated prompt: $prompt")
            
            // Generate response
            val response = withContext(Dispatchers.IO) {
                model.generateContent(prompt)
            }
            
            val fullResponse = StringBuilder()
            val generatedText = limitWords(response.text ?: "", MAX_WORDS)

            // Simulate streaming for better UX
            generatedText.split(" ").forEach { word ->
                withContext(Dispatchers.Main) {
                    fullResponse.append(word).append(" ")
                    callback.onPartialResponse("$word ")
                    kotlinx.coroutines.delay(50) // Small delay for natural flow
                }
            }

            val finalResponse = fullResponse.toString().trim()

            // Save conversation history if associated notification exists
            try {
                val notificationExists = database.notificationDao().getNotificationByIdSync(notificationId) != null
                if (notificationExists) {
                    val conversationHistory = ConversationHistory(
                        notificationId = notificationId,
                        message = message,
                        response = finalResponse,
                        timestamp = System.currentTimeMillis()
                    )
                    database.conversationHistoryDao().insert(conversationHistory)
                    Log.d(TAG, "Saved conversation history")

                    // Prune old history entries
                    geminiDao.pruneConversationHistory(
                        notificationId = notificationId,
                        keepCount = config.maxHistoryPerThread
                    )
                    Log.d(TAG, "Pruned old history entries")
                } else {
                    Log.w(TAG, "Skipping conversation history save - notification $notificationId does not exist")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving conversation history", e)
            }

            withContext(Dispatchers.Main) {
                callback.onComplete(finalResponse)
            }
            Log.d(TAG, "Completed response generation: $finalResponse")

        } catch (e: Exception) {
            Log.e(TAG, "Error generating reply", e)
            withContext(Dispatchers.Main) {
                callback.onError(e)
            }
        }
    }

    private fun buildHistoryContext(history: List<ConversationHistory>): String {
        if (history.isEmpty()) return "No previous conversation."
        
        return buildString {
            append("Previous conversation history:\n\n")
            history.asReversed().forEach { entry ->
                append("User: ${entry.message}\n")
                append("Assistant: ${entry.response}\n\n")
            }
        }.trimEnd()
    }

    private fun limitWords(text: String, maxWords: Int): String {
        val words = text.split("\\s+".toRegex())
        return if (words.size <= maxWords) {
            text
        } else {
            words.take(maxWords).joinToString(" ") + "..."
        }
    }

    fun shutdown() {
        scope.cancel() // Cancel all coroutines when shutting down
    }
}
