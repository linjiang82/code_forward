package com.example.smsforwarder.email

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResendEmailSenderTest {
    private lateinit var server: MockWebServer
    private lateinit var sender: ResendEmailSender

    private val email = Email(from = "from@example.com", to = "to@example.com", subject = "Subject", text = "Body")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sender = ResendEmailSender("re_test_key", baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends authorized request with idempotency key and resend payload`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"abc"}"""))

            val result = sender.send(email, idempotencyKey = "work-123")

            assertEquals(SendResult.Success, result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/emails", request.path)
            assertEquals("Bearer re_test_key", request.getHeader("Authorization"))
            assertEquals("work-123", request.getHeader("Idempotency-Key"))
            val body = JSONObject(request.body.readUtf8())
            assertEquals("from@example.com", body.getString("from"))
            assertEquals("to@example.com", body.getJSONArray("to").getString(0))
            assertEquals("Subject", body.getString("subject"))
            assertEquals("Body", body.getString("text"))
        }

    @Test
    fun `toJson escapes quotes, backslashes, newlines and control characters`() {
        val text = "He said \"hi\"\nC:\\path\ttab\u0001end"
        val subject = "Forwarded SMS from \"Bank\""

        val json = JSONObject(ResendEmailSender.toJson(email.copy(subject = subject, text = text)))

        assertEquals(subject, json.getString("subject"))
        assertEquals(text, json.getString("text"))
    }

    @Test
    fun `toJson includes attachments as base64 and omits the field when there are none`() {
        val bytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x00, -1)

        val json = JSONObject(ResendEmailSender.toJson(email.copy(attachments = listOf(Attachment("sms.pdf", bytes)))))

        val attachment = json.getJSONArray("attachments").getJSONObject(0)
        assertEquals("sms.pdf", attachment.getString("filename"))
        assertEquals(java.util.Base64.getEncoder().encodeToString(bytes), attachment.getString("content"))
        assertTrue(!JSONObject(ResendEmailSender.toJson(email)).has("attachments"))
    }

    @Test
    fun `rate limiting and server errors are retryable`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(429))
            server.enqueue(MockResponse().setResponseCode(503))

            assertTrue(sender.send(email, "k") is SendResult.Retryable)
            assertTrue(sender.send(email, "k") is SendResult.Retryable)
        }

    @Test
    fun `client errors fail without retry and include the response message`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"domain is not verified"}"""))

            val result = sender.send(email, "k")

            assertTrue(result is SendResult.Failed)
            assertTrue((result as SendResult.Failed).reason.contains("domain is not verified"))
        }

    @Test
    fun `network errors are retryable`() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            assertTrue(sender.send(email, "k") is SendResult.Retryable)
        }
}
