package com.example.whatsuit

import android.content.Context
import com.example.whatsuit.data.AppDatabase
import com.example.whatsuit.data.GeminiConfig
import com.example.whatsuit.data.GeminiDao
import com.example.whatsuit.data.PromptTemplate
import com.example.whatsuit.service.GeminiService
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.MockitoAnnotations

@RunWith(MockitoJUnitRunner::class)
class GeminiServiceTest {
    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var database: AppDatabase

    @Mock
    private lateinit var geminiDao: GeminiDao

    private lateinit var geminiService: GeminiService

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(context.applicationContext).thenReturn(context)
        `when`(AppDatabase.getDatabase(context)).thenReturn(database)
        `when`(database.geminiDao()).thenReturn(geminiDao)
        geminiService = GeminiService(context)
    }

    @Test
    fun `initialize with valid config succeeds`() = runBlocking {
        val config = GeminiConfig(
            apiKey = "test_key",
            modelName = "gemini-1.5-flash",
            maxHistoryPerThread = 10
        )
        `when`(geminiDao.getConfig()).thenReturn(config)

        geminiService.initialize()

        verify(geminiDao).getConfig()
    }

    @Test
    fun `generateReply uses active template`() = runBlocking {
        val config = GeminiConfig(
            apiKey = "test_key",
            modelName = "gemini-1.5-flash",
            maxHistoryPerThread = 10
        )
        val template = PromptTemplate(
            name = "Test Template",
            template = "Test {message} with {context}"
        )

        `when`(geminiDao.getConfig()).thenReturn(config)
        `when`(geminiDao.getActiveTemplate()).thenReturn(template)
        `when`(geminiDao.getConversationHistory(any(), any())).thenReturn(emptyList())

        var responseReceived = false
        geminiService.initialize()
        geminiService.generateReply(1L, "test message", object : GeminiService.ResponseCallback {
            override fun onPartialResponse(text: String) {}
            override fun onComplete(fullResponse: String) {
                responseReceived = true
            }
            override fun onError(error: Throwable) {}
        })

        verify(geminiDao).getActiveTemplate()
        verify(geminiDao).getConversationHistory(eq(1L), eq(10))
    }

    @Test(expected = IllegalStateException::class)
    fun `generateReply without initialization throws exception`() = runBlocking {
        geminiService.generateReply(1L, "test", object : GeminiService.ResponseCallback {
            override fun onPartialResponse(text: String) {}
            override fun onComplete(fullResponse: String) {}
            override fun onError(error: Throwable) {}
        })
    }

    @Test
    fun `generateReply enforces word limit`() = runBlocking {
        val config = GeminiConfig(
            apiKey = "test_key",
            modelName = "gemini-1.5-flash",
            maxHistoryPerThread = 10
        )
        val template = PromptTemplate.createDefault()

        `when`(geminiDao.getConfig()).thenReturn(config)
        `when`(geminiDao.getActiveTemplate()).thenReturn(template)
        `when`(geminiDao.getConversationHistory(any(), any())).thenReturn(emptyList())

        var response = ""
        geminiService.initialize()
        geminiService.generateReply(1L, "test message", object : GeminiService.ResponseCallback {
            override fun onPartialResponse(text: String) {}
            override fun onComplete(fullResponse: String) {
                response = fullResponse
            }
            override fun onError(error: Throwable) {}
        })

        assert(response.split(" ").size <= 50) { "Response exceeds 50 word limit" }
    }
}