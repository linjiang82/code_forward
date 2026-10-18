package com.example.smsforwarder.data.pdf

import android.content.Context
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import com.example.smsforwarder.domain.EncryptedPdfBuilder
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Renders an SMS into a PDF protected with a password (AES-256), so the email itself carries no
 * readable content. Text is drawn by Android's own text stack, so any script or emoji the phone
 * can display ends up in the PDF.
 */
class EncryptedSmsPdf(context: Context) : EncryptedPdfBuilder {
    private val context = context.applicationContext

    private companion object {
        // A4 in PDF points
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48
    }

    override fun build(
        sender: String,
        body: String,
        receivedAt: String,
        password: String,
    ): ByteArray = encrypt(render(sender, body, receivedAt), password)

    private fun render(
        sender: String,
        body: String,
        receivedAt: String,
    ): ByteArray {
        val text =
            SpannableStringBuilder().apply {
                append("From: $sender\nReceived: $receivedAt", StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append("\n\n")
                append(body)
            }
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 14f }
        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val contentHeight = PAGE_HEIGHT - 2 * MARGIN
        val layout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1f)
                .build()

        val document = PdfDocument()
        try {
            var firstLine = 0
            var pageNumber = 1
            while (firstLine < layout.lineCount) {
                // Fit as many whole lines on this page as possible.
                val top = layout.getLineTop(firstLine)
                var lastLine = firstLine
                while (lastLine + 1 < layout.lineCount && layout.getLineBottom(lastLine + 1) - top <= contentHeight) {
                    lastLine++
                }

                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create())
                page.canvas.apply {
                    translate(MARGIN.toFloat(), MARGIN.toFloat())
                    clipRect(0, 0, contentWidth, layout.getLineBottom(lastLine) - top)
                    translate(0f, -top.toFloat())
                    layout.draw(this)
                }
                document.finishPage(page)
                firstLine = lastLine + 1
            }
            return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun encrypt(
        pdf: ByteArray,
        password: String,
    ): ByteArray {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(pdf).use { document ->
            // Random owner password: nobody can lift the protection without knowing the user password.
            val policy =
                StandardProtectionPolicy(UUID.randomUUID().toString(), password, AccessPermission()).apply {
                    encryptionKeyLength = 256
                }
            document.protect(policy)
            return ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
    }
}
