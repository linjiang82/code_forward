package com.example.smsforwarder

import org.junit.Assert.assertEquals
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
    fun `shouldForward ignores targets shorter than the minimum length`() {
        assertFalse(processor.shouldForward("+1234567890", "0"))
        assertFalse(processor.shouldForward("+1234567890", "890"))
        assertTrue(processor.shouldForward("+1234567890", "890, 7890"))
    }

    @Test
    fun `shouldForward ignores formatting characters`() {
        assertTrue(processor.shouldForward("+1 (234) 567-890", "567-890"))
        assertTrue(processor.shouldForward("+1234567890", "+1 234 567 890"))
    }

    @Test
    fun `shouldForward matches alphanumeric senders case-insensitively`() {
        assertTrue(processor.shouldForward("MyBank", "mybank"))
    }

    @Test
    fun `shouldForward returns false for blank target or sender`() {
        assertFalse(processor.shouldForward("+1234567890", " , "))
        assertFalse(processor.shouldForward("", "1234"))
    }

    @Test
    fun `shouldForward with forwardAll matches any sender and ignores the target list`() {
        assertTrue(processor.shouldForward("+1234567890", "", forwardAll = true))
        assertTrue(processor.shouldForward("MyBank", "+1111111111", forwardAll = true))
    }

    @Test
    fun `invalidTargets reports entries that are too short`() {
        assertEquals(listOf("12"), processor.invalidTargets("+1234567890, 12"))
    }

    @Test
    fun `combineParts joins multipart messages per sender in order`() {
        val parts =
            listOf(
                SmsMessage("+111", "Hello "),
                SmsMessage("+222", "Other"),
                SmsMessage("+111", "world"),
            )

        assertEquals(
            listOf(SmsMessage("+111", "Hello world"), SmsMessage("+222", "Other")),
            processor.combineParts(parts),
        )
    }
}
