package com.example.smsforwarder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `shouldForward returns true for comma-separated list match`() {
        assertTrue(processor.shouldForward("+1234567890", "+1111111111,+1234567890,+2222222222"))
    }

    @Test
    fun `shouldForward returns true for comma-separated list match with whitespace`() {
        assertTrue(processor.shouldForward("+1234567890", "+1111111111, +1234567890 , +2222222222"))
    }

    @Test
    fun `shouldForward returns true for comma-separated list with suffix match`() {
        assertTrue(processor.shouldForward("+1234567890", "111111,567890,222222"))
    }

    @Test
    fun `shouldForward returns false if sender is not in comma-separated list`() {
        assertFalse(processor.shouldForward("+1234567890", "+1111111111,+2222222222"))
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
