package com.example.smsforwarder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidatorTest {
    private val validator = SettingsValidator()

    @Test
    fun `valid settings pass`() {
        assertNull(validator.validate(validSettings))
    }

    @Test
    fun `numbers are required unless forwarding all`() {
        assertTrue(validator.validate(validSettings.copy(targetNumbers = ""))!!.contains("at least one phone number"))
        assertNull(validator.validate(validSettings.copy(targetNumbers = "", forwardAll = true)))
    }

    @Test
    fun `short numbers are rejected unless forwarding all`() {
        assertTrue(validator.validate(validSettings.copy(targetNumbers = "+15550123456, 12"))!!.contains("12"))
        assertNull(validator.validate(validSettings.copy(targetNumbers = "12", forwardAll = true)))
    }

    @Test
    fun `pdf pin must be long enough only when encryption is on`() {
        assertEquals(
            "The PDF PIN must be at least 4 characters.",
            validator.validate(validSettings.copy(encryptPdf = true, pdfPin = "123")),
        )
        assertNull(validator.validate(validSettings.copy(encryptPdf = true, pdfPin = "1234")))
        assertNull(validator.validate(validSettings.copy(encryptPdf = false, pdfPin = "")))
    }

    @Test
    fun `api key and addresses are required`() {
        assertEquals("Enter your Resend API key.", validator.validate(validSettings.copy(apiKey = " ")))
        assertEquals("Enter a valid To email address.", validator.validate(validSettings.copy(toEmail = "nope")))
        assertEquals("Enter a valid From email address.", validator.validate(validSettings.copy(fromEmail = "")))
    }
}
