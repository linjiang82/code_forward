package com.example.smsforwarder.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smsforwarder.data.pdf.EncryptedSmsPdf
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedSmsPdfTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun pdfOpensOnlyWithThePin() {
        val pdf = EncryptedSmsPdf(context).build("+15550123456", "Your code is 482913", "Oct 18, 2026 3:38 PM", "1234")
        // Kept for manual inspection: adb pull /sdcard/Android/data/com.example.smsforwarder/files/
        context.getExternalFilesDir(null)?.let { File(it, "test-encrypted.pdf").writeBytes(pdf) }

        assertTrue(failsToOpen(pdf, ""))
        assertTrue(failsToOpen(pdf, "0000"))

        PDDocument.load(pdf, "1234").use { document ->
            assertTrue(document.isEncrypted)
            assertEquals(256, document.encryption.length)
            val text = PDFTextStripper().getText(document)
            assertTrue(text, text.contains("+15550123456"))
            assertTrue(text, text.contains("Your code is 482913"))
        }
    }

    @Test
    fun longAndNonLatinMessagesSpanMultiplePages() {
        val body = "您的验证码是 482913 🔐\n" + "Line of a very long message. ".repeat(400)

        val pdf = EncryptedSmsPdf(context).build("银行", body, "2026年10月18日", "1234")
        context.getExternalFilesDir(null)?.let { File(it, "test-encrypted-long.pdf").writeBytes(pdf) }

        PDDocument.load(pdf, "1234").use { document ->
            assertTrue(document.numberOfPages > 1)
        }
    }

    private fun failsToOpen(
        pdf: ByteArray,
        password: String,
    ): Boolean =
        try {
            PDDocument.load(pdf, password).close()
            false
        } catch (e: InvalidPasswordException) {
            true
        }
}
