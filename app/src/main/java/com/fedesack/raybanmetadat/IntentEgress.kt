package com.fedesack.raybanmetadat

data class IntentEgressResult(
    val posted: Boolean,
    val reason: String,
    val responseBody: String? = null,
    val ack: DevIntentAck? = null,
    val error: String? = null,
) {
    val statusLine: String
        get() =
            when (reason) {
                REASON_POSTED -> ack?.message?.takeIf { it.isNotBlank() } ?: "Posted"
                REASON_FLAG_OFF -> "Queued (Dev mode off — no POST)"
                REASON_NO_URL -> "Queued (no webhook URL)"
                REASON_BAD_URL -> "Queued (webhook must be http/https)"
                REASON_HTTP_ERROR -> "Queued (POST failed: ${error ?: "error"})"
                else -> "Queued ($reason)"
            }

    companion object {
        const val REASON_POSTED = "posted"
        const val REASON_FLAG_OFF = "flag_off"
        const val REASON_NO_URL = "no_url"
        const val REASON_BAD_URL = "bad_url"
        const val REASON_HTTP_ERROR = "http_error"
    }
}

object IntentWebhookUrls {
    fun isSupported(url: String): Boolean {
        val value = url.trim()
        return value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
    }
}

class IntentEgress(
    private val queue: IntentQueue,
    private val client: IntentWebhookClient,
    private val isVoiceDevMode: () -> Boolean,
    private val webhookUrl: () -> String,
) {
    fun submit(intent: VoiceIntent): IntentEgressResult {
        queue.enqueue(intent)
        if (!isVoiceDevMode()) {
            return IntentEgressResult(posted = false, reason = IntentEgressResult.REASON_FLAG_OFF)
        }
        val url = webhookUrl().trim()
        if (url.isEmpty()) {
            return IntentEgressResult(posted = false, reason = IntentEgressResult.REASON_NO_URL)
        }
        if (!IntentWebhookUrls.isSupported(url)) {
            return IntentEgressResult(posted = false, reason = IntentEgressResult.REASON_BAD_URL)
        }
        val json = IntentJson.encode(intent)
        return client.post(url, json).fold(
            onSuccess = { body ->
                val ack = IntentJson.decodeAck(body)?.let { queue.ack(it) }
                IntentEgressResult(
                    posted = true,
                    reason = IntentEgressResult.REASON_POSTED,
                    responseBody = body,
                    ack = ack,
                )
            },
            onFailure = { error ->
                IntentEgressResult(
                    posted = false,
                    reason = IntentEgressResult.REASON_HTTP_ERROR,
                    error = error.message,
                )
            },
        )
    }
}
