package com.example.whatsuit.service

import android.content.Context
import android.util.Log
import com.example.whatsuit.data.NotificationEntity
import androidx.room.withTransaction
import com.example.whatsuit.data.AppDatabase
import com.example.whatsuit.data.ConversationHistory
import com.example.whatsuit.data.ConversationPrompt
import com.example.whatsuit.data.GeminiConfig
import com.example.whatsuit.data.PromptTemplate
import com.example.whatsuit.data.ConversationManager
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds
import java.util.Collections

class GeminiService(private val context: Context) {
    private companion object {
        private const val TAG = "GeminiService"
        private const val PROCESSED_TTL_MS = 60000L // 1 minute TTL
        private const val CONVERSATION_TAG = "ConversationTracking"
        private const val HISTORY_TAG = "HistoryManagement"
        private const val NOTIFICATION_TAG = "NotificationTracking"
        private const val DEFAULT_HISTORY_LIMIT = 10
        private const val MAX_WORDS = 50 // Limited for concise responses
        private const val MAX_HISTORY_MESSAGES = 5 // Number of recent messages to include

        // Rate limiting constants
        private const val MAX_REQUESTS_PER_MINUTE = 15
        private const val RATE_LIMIT_WINDOW_MS = 60000L // 1 minute in milliseconds
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 16000L
        private const val MAX_RETRIES_FOR_RATE_LIMIT = 3
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

    // Class properties
    private val database = AppDatabase.getDatabase(context)
    private val geminiDao = database.geminiDao()
    private val conversationManager = ConversationManager(context)
    private val processedNotifications = Collections.synchronizedSet(HashSet<Long>())
    private var generativeModel: GenerativeModel? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    interface ResponseCallback {
        fun onPartialResponse(text: String)
        fun onComplete(fullResponse: String)
        fun onError(error: Throwable)
    }

    private val initMutex = kotlinx.coroutines.sync.Mutex()
    private val rateLimitMutex = kotlinx.coroutines.sync.Mutex()
    private val requestTimestamps = Collections.synchronizedList(mutableListOf<Long>())
    @Volatile
    private var isInitialized = false
    private var initializationDeferred: kotlinx.coroutines.Deferred<Boolean>? = null
    private val pendingNotifications = mutableListOf<Pair<Long, ResponseCallback>>()
    private var initializationRetryCount = 0
    private val maxRetries = 3

    /**
     * Non-suspend version of initialize for Java interoperability
     * This method launches a coroutine to run the suspend initialize function
     */
    fun initializeFromJava() {
        Log.d(TAG, "Initializing Gemini service from Java")
        scope.launch {
            try {
                initialize()
                Log.d(TAG, "Initialization from Java completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during initialization from Java", e)
            }
        }
    }

    private fun getOrCreateConversationId(notification: NotificationEntity): String {
        // Use existing conversation ID if available
        if (!notification.conversationId.isNullOrBlank()) {
            return notification.conversationId!!
        }

        // Create a new conversation ID based on notification properties
        val newId = "conv-${notification.packageName}-${System.currentTimeMillis()}"
        notification.setConversationId(newId)

        // Update the notification in database
        scope.launch(Dispatchers.IO) {
            try {
                database.notificationDao().update(notification)
                Log.d(CONVERSATION_TAG, "Created new conversation ID: $newId")
            } catch (e: Exception) {
                Log.e(CONVERSATION_TAG, "Failed to update conversation ID", e)
            }
        }

        return newId
    }

    private suspend fun logConversationFlow(_notification: NotificationEntity?) {
        try {
            val notificationId = _notification?.id ?: return
        Log.d(TAG, "Thread ${Thread.currentThread().id}: Processing logConversationFlow")
            Log.d(CONVERSATION_TAG, """
                Conversation Flow:
                NotificationID: $notificationId
                ConversationID: ${_notification.conversationId}
                Timestamp: ${_notification.timestamp}
                Title: ${_notification.title}
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

            try {
                val config = geminiDao.getConfig()
                if (config != null && !config.apiKey.isNullOrBlank()) {
                    Log.d(TAG, "Initializing Gemini with model: ${config.modelName}")

                    // Add more robust initialization
                    try {
                        generativeModel = GenerativeModel(
                            modelName = config.modelName,
                            apiKey = config.apiKey
                        )
                        isInitialized = true
                        Log.d(TAG, "Gemini initialization successful")
                        return@withLock true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing Gemini model", e)
                        isInitialized = false
                        generativeModel = null
                    }
                } else {
                    Log.w(TAG, "Missing Gemini configuration - will skip initialization")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving Gemini config", e)
            }

            return@withLock false
        }
    }

    private suspend fun processPendingNotifications() {
        val notifications = synchronized(pendingNotifications) {
            val pending = pendingNotifications.toList()
            pendingNotifications.clear()
            pending
        }

        for ((notificationId, callback) in notifications) {
            try {
                generateReply(notificationId, "", callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing queued notification $notificationId", e)
                callback.onError(e)
            }
        }
    }

    fun queueNotification(notificationId: String, callback: ResponseCallback) {
        val numericId = try {
            notificationId.toLong()
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Invalid notification ID format: $notificationId")
            callback.onError(e)
            return
        }

        synchronized(pendingNotifications) {
            pendingNotifications.add(Pair(numericId, callback))
            Log.d(TAG, "Queued notification: $notificationId")
        }
    }

    private suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            initialize()
        }
    }

    private suspend fun getNotificationWithRetry(notificationId: Long, maxRetries: Int = 3): NotificationEntity? {
        var attempts = 0
        var lastError: Exception? = null

        while (attempts < maxRetries) {
            try {
                val notification = database.withTransaction {
                    database.notificationDao().getNotificationByIdSync(notificationId)
                }

                if (notification != null) {
                    Log.d(NOTIFICATION_TAG, "Retrieved notification on attempt ${attempts + 1}: $notificationId")
                    return notification
                } else {
                    Log.w(NOTIFICATION_TAG, "Notification not found on attempt ${attempts + 1}: $notificationId")
                }

                attempts++
                if (attempts < maxRetries) {
                    val delayTime = (1000L * (1L shl attempts))
                    Log.d(NOTIFICATION_TAG, "Retrying after $delayTime ms")
                    delay(delayTime.milliseconds)
                }

            } catch (e: Exception) {
                lastError = e
                Log.e(NOTIFICATION_TAG, "Error retrieving notification on attempt ${attempts + 1}", e)
                attempts++
                if (attempts < maxRetries) {
                    val delayTime = (1000L * (1L shl attempts))
                    Log.d(NOTIFICATION_TAG, "Retrying after $delayTime ms")
                    delay(delayTime.milliseconds)
                }
            }
        }

        val errorMessage = "Failed to retrieve notification after $maxRetries attempts"
        Log.e(NOTIFICATION_TAG, errorMessage, lastError)
        return null
    }

    fun generateReply(
        notificationId: Long,
        initialMessage: String,
        callback: ResponseCallback
    ) = scope.launch {
        try {
            // Skip if we've recently processed this notification
            //if (processedNotifications.contains(notificationId)) {
            //    Log.d(TAG, "Skipping duplicate processing of notification: $notificationId")
            //    withContext(Dispatchers.Main) {
            //        callback.onComplete("This message was recently processed.")
            //    }
            //    return@launch
            //}

            val notification = getNotificationWithRetry(notificationId)
                ?: throw IllegalStateException("Unable to retrieve notification after retries")

            // Get conversation context to check for rapid messages
            var message = initialMessage
            val contextData = conversationManager.getConversationContext(notificationId, notification)

            // If there are recent messages in the sequence, wait briefly for more
            if (contextData.recentMessages.size > 1 &&
                System.currentTimeMillis() - contextData.recentMessages.last().timestamp < 1000) {

                Log.d(TAG, "Detected rapid message sequence, waiting for more messages...")
                delay(1000) // Wait 1 second for potential follow-up messages

                // Refresh context to get any new messages
                val updatedContext = conversationManager.getConversationContext(notificationId, notification)

                // Combine all recent messages for context
                var updatedMessage = updatedContext.recentMessages
                    .joinToString("\n") { it.content }

                message = updatedMessage // Use combined messages as input
                Log.d(TAG, "Processing combined messages: $updatedMessage")
            }

            // Track this notification to prevent duplicate processing
            processedNotifications.add(notificationId)
            scope.launch {
                delay(PROCESSED_TTL_MS)
                processedNotifications.remove(notificationId)
            }

            // Check rate limits before proceeding
            if (!checkRateLimits()) {
                retryWithBackoff(
                    maxRetries = MAX_RETRIES_FOR_RATE_LIMIT,
                    initialDelay = INITIAL_RETRY_DELAY_MS
                ) {
                    if (!checkRateLimits()) {
                        throw RateLimitException("Rate limit exceeded. Please try again later.")
                    }
                }
            }
            // Special case for API testing
            val isTestMode = notificationId == -1L

            // Get or create notification based on test mode
            val testOrRealNotification = if (isTestMode) {
                // Create fully initialized dummy notification for testing
                NotificationEntity().apply {
                    setId(-1L)
                    setConversationId("test-conversation")
                    setPackageName("com.example.whatsuit.test")
                    setAppName("WhatSuit Test")
                    setTitle("Test Notification")
                    setContent("Test message content")
                    setTimestamp(System.currentTimeMillis())
                    setIcon("")
                    setAutoReplied(false)
                    setAutoReplyDisabled(false)
                    setAutoReplyContent(null)
                    setGroupTimestamp(null)
                    setGroupCount(null)
                }
            } else {
                // Get notification with retries for normal operation
                getNotificationWithRetry(notificationId)
                    ?: throw IllegalStateException("Unable to retrieve notification after retries")
            }

            // Log conversation flow except for test mode
            if (!isTestMode) {
                logConversationFlow(testOrRealNotification)
                Log.d(TAG, "Processing real notification: id=$notificationId")
            } else {
                Log.d(TAG, "Running in test mode with dummy notification")
            }

            ensureInitialized()
            val config = geminiDao.getConfig() ?: throw IllegalStateException("Gemini not configured")
            Log.d(TAG, "Retrieved Gemini config: modelName=${config.modelName}, historyLimit=${config.maxHistoryPerThread}")
            val model = generativeModel ?: throw IllegalStateException("Gemini model not initialized")

            Log.d(NOTIFICATION_TAG, "Thread ${Thread.currentThread().id}: Using cached notification")

            // Get all historical conversations for this thread
            val existingHistory = database.conversationHistoryDao()
                .getHistoryForConversationSync(testOrRealNotification.conversationId)

            // Get enhanced conversation context including participants and store result
            val currentContextData = conversationManager.getConversationContext(notificationId, testOrRealNotification)
            Log.d(CONVERSATION_TAG, "Retrieved conversation context: threadId=${currentContextData.threadId}, historySize=${currentContextData.historySize}")

            // Check for custom prompt for this conversation
            val conversationId = testOrRealNotification.conversationId
            val customPrompt = database.conversationPromptDao().getByConversationIdBlocking(conversationId)

            // Use custom prompt if available, otherwise use active template or default
            val template = if (customPrompt != null) {
                Log.d(TAG, "Using custom prompt for conversation ID: $conversationId")
                PromptTemplate(
                    id = 0,
                    name = customPrompt.name,
                    template = customPrompt.promptTemplate,
                    isActive = true
                )
            } else {
                Log.d(TAG, "No custom prompt found for conversation ID: $conversationId, using default template")
                database.geminiDao().getActiveTemplate() ?: PromptTemplate.createDefault()
            }

            // Build conversation context string
            val contextString = buildString {
                // Metadata section
                append("Conversation Info:\n")
                append("- Thread ID: ${currentContextData.threadId}\n")
                append("- Messages: ${currentContextData.historySize}\n")
                if (currentContextData.participants.isNotEmpty()) {
                    append("- Participants: ${currentContextData.participants.joinToString(", ")}\n")
                }
                append("\n")

                // Recent conversation history
                if (existingHistory.isNotEmpty()) {
                    append("Recent Messages:\n")
                    existingHistory.asReversed().take(5).forEach { entry ->
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

            // Log detailed generation attempt
            val startTime = System.currentTimeMillis()
            Log.i(TAG, """
                ====== GENERATING AUTO-REPLY ======
                Template: ${template.name}
                Context Length: ${contextString.length}
                Message: $message
                Thread ID: ${currentContextData.threadId}
                History Size: ${currentContextData.historySize}
                ================================
            """.trimIndent())

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

            // Log successful generation
            Log.i(TAG, """
                ====== AUTO-REPLY GENERATED ======
                Generated Text: $generatedText
                Word Count: ${generatedText.split(" ").size}
                Generation Time: ${System.currentTimeMillis() - startTime}ms
                ================================
            """.trimIndent())

            // Save conversation history
            try {
                withContext(Dispatchers.IO) {
                    // Use cached notification to avoid another lookup
                    val newHistory = ConversationHistory(
                        notificationId = notificationId,
                        conversationId = notification.conversationId ?: "",
                        message = message,
                        response = finalResponse,
                        timestamp = System.currentTimeMillis()
                    )

                    database.runInTransaction {
                        runBlocking {
                            database.conversationHistoryDao().insert(newHistory)
                            Log.d(HISTORY_TAG, "Saved new conversation history entry")


                            geminiDao.pruneConversationHistory(
                                notificationId = notificationId,
                                keepCount = config.maxHistoryPerThread
                            )
                            Log.d(HISTORY_TAG, "Pruned old history entries")
                        }
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
                    when {
                        e is RateLimitException -> {
                            callback.onComplete("I'm currently busy processing other requests. Please try again in a moment.")
                        }
                        isRateLimitError(e) -> {
                            callback.onComplete("The service is experiencing high demand. Please try again shortly.")
                        }
                        else -> {
                            callback.onError(RuntimeException("Failed to generate reply: ${e.message}", e))
                        }
                    }
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

            // Check rate limits before proceeding
            if (!checkRateLimits()) {
                retryWithBackoff(
                    maxRetries = MAX_RETRIES_FOR_RATE_LIMIT,
                    initialDelay = INITIAL_RETRY_DELAY_MS
                ) {
                    if (!checkRateLimits()) {
                        throw RateLimitException("Rate limit exceeded. Please try again later.")
                    }
                }
            }

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
                when {
                    e is RateLimitException -> {
                        callback.onComplete("Analysis temporarily unavailable due to high demand. Please try again in a moment.")
                    }
                    isRateLimitError(e) -> {
                        callback.onComplete("The service is experiencing high demand. Please try again shortly.")
                    }
                    else -> {
                        callback.onError(RuntimeException("Failed to analyze conversation: ${e.message}", e))
                    }
                }
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

    private fun isRateLimitError(e: Exception): Boolean {
        val errorMessage = e.message?.lowercase() ?: ""
        return errorMessage.contains("resource has been exhausted") ||
               errorMessage.contains("code: 429") ||
               errorMessage.contains("resource_exhausted") ||
               errorMessage.contains("rate limit")
    }

    private suspend fun checkRateLimits(): Boolean = rateLimitMutex.withLock {
        val currentTime = System.currentTimeMillis()
        val windowStart = currentTime - RATE_LIMIT_WINDOW_MS

        // Remove timestamps older than our window
        requestTimestamps.removeAll { it < windowStart }

        // Check if we're at the limit
        if (requestTimestamps.size >= MAX_REQUESTS_PER_MINUTE) {
            Log.w(TAG, "Rate limit reached: ${requestTimestamps.size} requests in the last minute")
            return false
        }

        // Add current timestamp and allow the request
        requestTimestamps.add(currentTime)
        return true
    }

    private suspend fun retryWithBackoff(
        maxRetries: Int = MAX_RETRIES_FOR_RATE_LIMIT,
        initialDelay: Long = INITIAL_RETRY_DELAY_MS,
        block: suspend () -> Unit
    ) {
        var currentDelay = initialDelay

        repeat(maxRetries) { attempt ->
            try {
                block()
                return // Success, exit the function
            } catch (e: Exception) {
                if (!isRateLimitError(e) || attempt == maxRetries - 1) {
                    throw e // Rethrow if not a rate limit error or last attempt
                }

                Log.w(TAG, "Rate limit hit, retrying in ${currentDelay}ms (attempt ${attempt + 1}/$maxRetries)")
                delay(currentDelay)

                // Exponential backoff with jitter
                currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    class RateLimitException(message: String) : Exception(message)
}
