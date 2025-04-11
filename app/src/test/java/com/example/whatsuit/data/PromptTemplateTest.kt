package com.example.whatsuit.data

import org.junit.Test
import org.junit.Assert.*

class PromptTemplateTest {

    @Test
    fun `createDefault creates template with correct values`() {
        val template = PromptTemplate.createDefault()

        assertEquals("Default Concise Response", template.name)
        assertTrue(template.template.contains("{context}"))
        assertTrue(template.template.contains("{message}"))
        assertTrue(template.isActive)
    }

    @Test
    fun `processTemplate correctly replaces placeholders`() {
        val templateText = """
            System: Generate a clear response
            Context: {context}
            User: {message}
            Assistant: Provide a direct response
        """.trimIndent()

        val processed = PromptTemplate.processTemplate(
            template = templateText,
            context = "Previous chat history",
            message = "Hello world"
        )

        assertTrue(processed.contains("Previous chat history"))
        assertTrue(processed.contains("Hello world"))
        assertFalse(processed.contains("{context}"))
        assertFalse(processed.contains("{message}"))
    }

    @Test
    fun `processTemplate handles empty context`() {
        val templateText = "Context: {context}\nMessage: {message}"

        val processed = PromptTemplate.processTemplate(
            template = templateText,
            context = "",
            message = "test message"
        )

        assertEquals("Context: \nMessage: test message", processed)
    }

    @Test
    fun `processTemplate handles special characters in input`() {
        val templateText = "Context: {context}\nMessage: {message}"
        val message = "Hello! How are you? 👋"
        val context = "Previous message with special ch@racters"

        val processed = PromptTemplate.processTemplate(
            template = templateText,
            context = context,
            message = message
        )

        assertEquals(
            "Context: Previous message with special ch@racters\nMessage: Hello! How are you? 👋",
            processed
        )
    }

    @Test
    fun `processTemplate maintains whitespace and formatting`() {
        val templateText = """
            System: Start here
                {context}
            User:
                {message}
            End
        """.trimIndent()

        val processed = PromptTemplate.processTemplate(
            templateText,
            "context\nwith\nnewlines",
            "message text"
        )

        // Verify line count is maintained
        assertEquals(7, processed.lines().size)

        // Verify indentation is preserved
        assertTrue(processed.contains("    context"))
        assertTrue(processed.contains("    message text"))
    }
}