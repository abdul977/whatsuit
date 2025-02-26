package com.example.whatsuit.service

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GeminiService(private val context: Context) {
    private companion object {
        private const val TAG = "GeminiService"
        private const val API_KEY = "AIzaSyBoLBIqECGckfiRALFxXrD50a_94oFvl-0" // Store in local.properties
    }

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = API_KEY
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    interface ResponseCallback {
        fun onPartialResponse(text: String)
        fun onComplete(fullResponse: String)
        fun onError(error: Throwable)
    }

    fun generateReply(message: String, callback: ResponseCallback) {
        scope.launch {
            try {
                val prompt = "Generate a concise and friendly reply (maximum 2 sentences) to: $message"
                Log.d(TAG, "Generating reply for message: $message")
                
                // Generate response
                val response = withContext(Dispatchers.IO) {
                    generativeModel.generateContent(prompt)
                }
                
                val fullResponse = StringBuilder()
                val generatedText = response.text ?: ""

                // Simulate streaming for better UX
                generatedText.split(" ").forEach { word ->
                    withContext(Dispatchers.Main) {
                        fullResponse.append(word).append(" ")
                        callback.onPartialResponse("$word ")
                        kotlinx.coroutines.delay(50) // Small delay for natural flow
                    }
                }

                withContext(Dispatchers.Main) {
                    callback.onComplete(fullResponse.toString())
                }
                Log.d(TAG, "Completed response generation: $fullResponse")

            } catch (e: Exception) {
                Log.e(TAG, "Error generating reply", e)
                withContext(Dispatchers.Main) {
                    callback.onError(e)
                }
            }
        }
    }

    fun shutdown() {
        scope.cancel() // Cancel all coroutines when shutting down
    }
}