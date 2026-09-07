package com.fedesack.raybanmetadat

import java.net.HttpURLConnection
import java.net.URI

fun interface IntentWebhookClient {
    fun post(
        url: String,
        json: String,
    ): Result<String>
}

class HttpUrlIntentWebhookClient(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 8_000,
) : IntentWebhookClient {
    override fun post(
        url: String,
        json: String,
    ): Result<String> =
        runCatching {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.outputStream.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
                if (code !in 200..299) {
                    error("HTTP $code $body")
                }
                body
            } finally {
                connection.disconnect()
            }
        }
}
