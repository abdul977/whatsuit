package com.example.whatsuit.service

import android.content.Context
import android.util.Log
import com.example.whatsuit.data.AppDatabase
import com.example.whatsuit.data.ConversationHistory
import com.example.whatsuit.data.GeminiConfig
import com.example.whatsuit.data.PromptTemplate
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /**
     * Initialize the service with configuration from database
     */
    suspend fun initialize() {
        val config = geminiDao.getConfig()
        if (config != null) {
            generativeModel = GenerativeModel(
                modelName = config.modelName,
                apiKey = config.apiKey
            )
        }
    }

    interface ResponseCallback {
        fun onPartialResponse(text: String)
        fun onComplete(fullResponse: String)
        fun onError(error: Throwable)
    }

    suspend fun generateReply(
        notificationId: Long,
        message: String,
        callback: ResponseCallback
    ) {
        try {
            val config = geminiDao.getConfig() ?: throw IllegalStateException("Gemini not configured")
            val model = generativeModel ?: throw IllegalStateException("Gemini model not initialized")
            
            // Get conversation history
            val history = geminiDao.getConversationHistory(
                notificationId = notificationId,
                limit = config.maxHistoryPerThread
            )
            
            // Get active prompt template
            val template = geminiDao.getActiveTemplate() ?: PromptTemplate.createDefault()
            
            // Build context from history
            val context = buildHistoryContext(history)
            
            // Generate prompt using template
            val prompt = PromptTemplate.processTemplate(
                template = template.template,
                context = context,
                message = message
            )
            
            Log.d(TAG, "Generating reply for message: $message")
            Log.d(TAG, "Using context: $context")
            
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

            // Save to conversation history
            val finalResponse = fullResponse.toString().trim()
            geminiDao.insertConversation(
                ConversationHistory.create(
                    notificationId = notificationId,
                    message = message,
                    response = finalResponse
                )
            )

            // Prune old history if needed
            geminiDao.pruneConversationHistory(
                notificationId = notificationId,
                keepCount = config.maxHistoryPerThread
            )

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
        
        return history.joinToString("\n") { entry ->
            "User: ${entry.message}\nAssistant: ${entry.response}"
        }
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