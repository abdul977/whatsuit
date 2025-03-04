package com.example.whatsuit.service

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.whatsuit.data.AppDatabase
import com.example.whatsuit.data.ConversationHistory
import com.example.whatsuit.data.GeminiConfig
import com.example.whatsuit.data.PromptTemplate
import com.example.whatsuit.data.ConversationManager
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock

class GeminiService(private val context: Context) {
    private companion object {
        private const val TAG = "GeminiService"
        private const val CONVERSATION_TAG = "ConversationTracking"
        private const val HISTORY_TAG = "HistoryManagement"
        private const val DEFAULT_HISTORY_LIMIT = 10
        private const val MAX_WORDS = 50 // Limited for concise responses
        private const val MAX_HISTORY_MESSAGES = 5 // Number of recent messages to include
    private const val ANALYSIS_PROMPT = """
            Analyze this conversation history and provide insights in the following format:
            
            1. Next Steps:
            - What actions should be taken next with this conversation?
            - What business opportunities exist?
            
            2. Follow-up Actions:
            - Does this require follow-up contact?
            - When and how should we follow up?
            
            3. Negotiation Points:
            - What negotiation strategies would be effective?
            - What key points need to be addressed?

            4. Suggested Reply:
            - A short, professional message (max 2 sentences) to move the conversation forward based on the analysis.
            
            Conversation History:
            {context}
            
            Provide a detailed but concise analysis focusing on actionable insights.
        """
    }

    private val database = AppDatabase.getDatabase(context)
    private val geminiDao = database.geminiDao()
    private val conversationManager = ConversationManager(context)
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

    private suspend fun logConversationFlow(notificationId: Long) {
        try {
            val notification = database.notificationDao().getNotificationByIdSync(notificationId)
            Log.d(CONVERSATION_TAG, """
                Conversation Flow:
                NotificationID: $notificationId
                ConversationID: ${notification?.conversationId}
                Timestamp: ${notification?.timestamp}
                Title: ${notification?.title}
            """.trimIndent())

            val history = database.conversationHistoryDao().getHistoryForNotificationSync(notificationId)
            Log.d(HISTORY_TAG, """
                History Entries: ${history.size}
                Latest Entry: ${history.firstOrNull()?.timestamp}
                History IDs: ${history.map { it.id }}
            """.trimIndent())
        } catch (e: Exception) {
            Log.e(TAG, "Error logging conversation flow", e)
        }
    }

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

    fun generateReply(
        notificationId: Long,
        message: String,
        callback: ResponseCallback
    ) = scope.launch {
        try {
            // Log conversation flow first
            logConversationFlow(notificationId)
            
            ensureInitialized()
            val config = geminiDao.getConfig() ?: throw IllegalStateException("Gemini not configured")
            Log.d(TAG, "Retrieved Gemini config: modelName=${config.modelName}, historyLimit=${config.maxHistoryPerThread}")
            val model = generativeModel ?: throw IllegalStateException("Gemini model not initialized")

            // Get the notification to access its threadId
            val notification = database.notificationDao().getNotificationByIdSync(notificationId)
                ?: throw IllegalStateException("Notification not found")
            
            // Get all historical conversations for this thread
            val conversationHistory = database.conversationHistoryDao()
                .getHistoryForConversationSync(notification.conversationId)
            
            // Get enhanced conversation context including participants
            val contextData = conversationManager.getConversationContext(notificationId)
            Log.d(CONVERSATION_TAG, "Retrieved conversation context: threadId=${contextData.threadId}, historySize=${contextData.historySize}")

            // Get active template or use default
            val template = database.geminiDao().getActiveTemplate() ?: PromptTemplate.createDefault()
            
            // Build conversation context string
            val contextString = buildString {
                // Metadata section
                append("Conversation Info:\n")
                append("- Thread ID: ${contextData.threadId}\n")
                append("- Messages: ${contextData.historySize}\n")
                if (contextData.participants.isNotEmpty()) {
                    append("- Participants: ${contextData.participants.joinToString(", ")}\n")
                }
                append("\n")

                // Recent conversation history
                if (conversationHistory.isNotEmpty()) {
                    append("Recent Messages:\n")
                    conversationHistory.asReversed().take(5).forEach { entry ->
                        append("User: ${entry.message}\n")
                        append("Assistant: ${entry.response}\n")
                        append("---\n")
                    }
                } else {
                    append("No previous messages\n")
                }
            }
            
            // Process template with context and current message
            val prompt = PromptTemplate.processTemplate(
                template = template.template,
                context = contextString,
                message = message
            )
            
            // Get response from Gemini
            Log.d(TAG, "Using template: ${template.name}")
            Log.d(TAG, "Sending prompt to Gemini with context length: ${prompt.length}")
            
            // Generate response
            val response = withContext(Dispatchers.IO) {
                model.generateContent(prompt)
            }
            
            // Process and limit response to exactly 50 words
            val generatedText = response.text?.let { rawText ->
                limitWords(rawText, MAX_WORDS)
            } ?: throw IllegalStateException("Empty response from Gemini")

            val fullResponse = StringBuilder()

            // Simulate streaming for better UX
            val words = generatedText.split(" ")
            for (word in words) {
                withContext(Dispatchers.Main) {
                    fullResponse.append(word).append(" ")
                    callback.onPartialResponse("$word ")
                }
                Thread.sleep(50)
            }

            val finalResponse = fullResponse.toString().trim()

            // Save conversation history
            try {
                withContext(Dispatchers.IO) {
                    val notification = database.notificationDao().getNotificationByIdSync(notificationId)
                    if (notification != null) {
                        val conversationHistory = ConversationHistory(
                            notificationId = notificationId,
                            conversationId = notification.conversationId ?: "",
                            message = message,
                            response = finalResponse,
                            timestamp = System.currentTimeMillis()
                        )
                        
                        database.runInTransaction {
                            runBlocking {
                                database.conversationHistoryDao().insert(conversationHistory)
                                Log.d(HISTORY_TAG, "Saved new conversation history entry")
                                
                                geminiDao.pruneConversationHistory(
                                    notificationId = notificationId,
                                    keepCount = config.maxHistoryPerThread
                                )
                                Log.d(HISTORY_TAG, "Pruned old history entries")
                            }
                        }
                    } else {
                        Log.w(TAG, "Skipping history save - notification $notificationId not found")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving conversation history", e)
            }

            withContext(Dispatchers.Main) {
                callback.onComplete(finalResponse)
            }
            Log.d(TAG, "Completed response generation")

        } catch (e: Exception) {
            Log.e(TAG, "Error generating reply", e)
            withContext(Dispatchers.Main) {
                callback.onError(RuntimeException("Failed to generate reply: ${e.message}", e))
            }
        }
    }

    private fun buildEnhancedHistoryContext(
        history: List<ConversationHistory>,
        conversationContext: ConversationManager.ConversationContext
    ): String {
        if (history.isEmpty()) return "No previous conversation."
        
        return buildString {
            append("Conversation Context:\n")
            append("Thread ID: ${conversationContext.threadId}\n")
            append("History Size: ${conversationContext.historySize}\n")
            append("Last Activity: ${conversationContext.lastActivity}\n\n")
            
            append("Conversation History:\n")
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
        scope.cancel()
    }

    fun analyzeConversation(
        conversationId: String,
        callback: ResponseCallback
    ) = scope.launch {
        try {
            ensureInitialized()
            val model = generativeModel ?: throw IllegalStateException("Gemini model not initialized")
            
            val history = database.getConversationHistoryDao().getHistoryForConversationSync(conversationId)
            
            if (history.isEmpty()) {
                callback.onComplete("No conversation history to analyze.")
                return@launch
            }
            
            val context = buildHistoryContext(history)
            val prompt = ANALYSIS_PROMPT.replace("{context}", context)
            
            val response = withContext(Dispatchers.IO) {
                model.generateContent(prompt)
            }
            
            val analysis = response.text ?: "Unable to generate analysis."
            
            val latestHistoryEntry = history.maxByOrNull { it.timestamp }
            if (latestHistoryEntry != null) {
                withContext(Dispatchers.IO) {
                    database.withTransaction {
                        val updatedHistory = latestHistoryEntry.copy(
                            analysis = analysis,
                            analysisTimestamp = System.currentTimeMillis()
                        )
                        database.conversationHistoryDao().update(updatedHistory)
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                callback.onComplete(analysis)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing conversation", e)
            withContext(Dispatchers.Main) {
                callback.onError(RuntimeException("Failed to analyze conversation: ${e.message}", e))
            }
        }
    }

    private fun buildHistoryContext(history: List<ConversationHistory>): String {
        if (history.isEmpty()) return "No previous conversation."
        
        return buildString {
            history.asReversed().forEach { entry ->
                append("User: ${entry.message}\n")
                append("Assistant: ${entry.response}\n\n")
            }
        }.trimEnd()
    }
}
