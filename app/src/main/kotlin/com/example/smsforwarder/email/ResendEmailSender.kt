package com.example.smsforwarder.email

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Sends email through the Resend REST API (https://resend.com/docs/api-reference/emails/send-email). */
class ResendEmailSender(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultClient,
) : EmailSender {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.resend.com"

        private val defaultClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

        fun toJson(email: Email): String {
            val json =
                JSONObject()
                    .put("from", email.from)
                    .put("to", JSONArray().put(email.to))
                    .put("subject", email.subject)
                    .put("text", email.text)
            if (email.attachments.isNotEmpty()) {
                val attachments = JSONArray()
                email.attachments.forEach {
                    attachments.put(JSONObject().put("filename", it.filename).put("content", it.content.toByteString().base64()))
                }
                json.put("attachments", attachments)
            }
            return json.toString()
        }
    }

    override suspend fun send(
        email: Email,
        idempotencyKey: String,
    ): SendResult =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url("$baseUrl/emails")
                    .addHeader("Authorization", "Bearer $apiKey")
                    // Resend returns the original result for a repeated key (24h), so retries can't double-send.
                    .addHeader("Idempotency-Key", idempotencyKey)
                    .post(toJson(email).toRequestBody("application/json".toMediaType()))
                    .build()

            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> SendResult.Success
                        response.code == 429 || response.code >= 500 ->
                            SendResult.Retryable("Resend returned HTTP ${response.code}")
                        else -> SendResult.Failed("Resend returned HTTP ${response.code}: ${response.body?.string()?.take(200)}")
                    }
                }
            } catch (e: IOException) {
                SendResult.Retryable("Network error: ${e.message}")
            }
        }
}
