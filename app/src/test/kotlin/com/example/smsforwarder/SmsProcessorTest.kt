package com.example.smsforwarder

import org.junit.Assert.*
import org.junit.Test

class SmsProcessorTest {

    private val processor = SmsProcessor()

    @Test
    fun `shouldForward returns true for exact match`() {
        assertTrue(processor.shouldForward("+1234567890", "+1234567890"))
    }

    @Test
    fun `shouldForward returns true for suffix match`() {
        assertTrue(processor.shouldForward("+1234567890", "567890"))
    }

    @Test
    fun `shouldForward returns false for no match`() {
        assertFalse(processor.shouldForward("+1234567890", "999999"))
    }

    @Test
    fun `buildEmailJson constructs correct json`() {
        val to = "to@example.com"
        val from = "from@example.com"
        val subject = "Test Subject"
        val body = "Test Body"
        
        val json = processor.buildEmailJson(to, from, subject, body)
        
        assertTrue(json.contains("\"to\": [{\"email\": \"$to\"}]"))
        assertTrue(json.contains("\"from\": {\"email\": \"$from\"}"))
        assertTrue(json.contains("\"subject\": \"$subject\""))
        assertTrue(json.contains("\"value\": \"$body\""))
    }
}
