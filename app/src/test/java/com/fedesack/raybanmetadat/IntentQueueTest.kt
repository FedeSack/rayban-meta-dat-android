package com.fedesack.raybanmetadat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentQueueTest {
    @Test
    fun enqueueKeepsLockedSchemaFieldsAndPreferDefaultsToAuto() {
        val queue = IntentQueue()
        val intent =
            VoiceIntent.create(
                utterance = "  fix the HUD  ",
                source = IntentSource.CHAT,
                deviceId = "tablet",
                appVersion = "0.1.0-debug",
                voiceDevMode = true,
                id = "11111111-1111-1111-1111-111111111111",
                ts = "2026-09-07T21:00:00Z",
            )
        queue.enqueue(intent)
        val stored = queue.last()!!.intent
        assertEquals("11111111-1111-1111-1111-111111111111", stored.id)
        assertEquals("fix the HUD", stored.utterance)
        assertEquals(IntentSource.CHAT, stored.source)
        assertEquals("2026-09-07T21:00:00Z", stored.ts)
        assertEquals("tablet", stored.deviceId)
        assertEquals("0.1.0-debug", stored.appVersion)
        assertTrue(stored.voiceDevMode)
        assertEquals(IntentPrefer.AUTO, stored.prefer)
        assertEquals(1, queue.size)
    }

    @Test
    fun queueAcceptsWhenVoiceDevModeIsOff() {
        val queue = IntentQueue()
        queue.enqueue(sampleIntent(voiceDevMode = false, source = IntentSource.MIC))
        assertEquals(1, queue.size)
        assertFalse(queue.last()!!.intent.voiceDevMode)
        assertEquals(IntentSource.MIC, queue.last()!!.intent.source)
    }

    @Test
    fun dropsOldestWhenOverCapacity() {
        val queue = IntentQueue(capacity = 2)
        queue.enqueue(sampleIntent(id = "a", utterance = "one"))
        queue.enqueue(sampleIntent(id = "b", utterance = "two"))
        queue.enqueue(sampleIntent(id = "c", utterance = "three"))
        assertEquals(listOf("b", "c"), queue.snapshot().map { it.intent.id })
    }

    @Test
    fun storesAckAgainstMatchingIntentId() {
        val queue = IntentQueue()
        queue.enqueue(sampleIntent(id = "intent-1"))
        val ack =
            DevIntentAck(
                intentId = "intent-1",
                status = DevIntentStatus.QUEUED,
                kind = DevIntentKind.SKILL,
                message = "en cola",
            )
        queue.ack(ack)
        assertEquals(ack, queue.last()!!.ack)
        assertEquals(DevIntentStatus.QUEUED, queue.last()!!.ack!!.status)
    }
}

class IntentJsonTest {
    @Test
    fun encodeMatchesLockedShapeIncludingNestedFlags() {
        val intent =
            VoiceIntent(
                id = "11111111-1111-1111-1111-111111111111",
                utterance = """hello "world"""",
                source = IntentSource.CHAT,
                ts = "2026-09-07T21:00:00Z",
                deviceId = "tablet",
                appVersion = "0.1.0-debug",
                voiceDevMode = true,
                prefer = IntentPrefer.AUTO,
            )
        assertEquals(
            """{"id":"11111111-1111-1111-1111-111111111111","utterance":"hello \"world\"","source":"chat","ts":"2026-09-07T21:00:00Z","deviceId":"tablet","appVersion":"0.1.0-debug","flags":{"voiceDevMode":true},"prefer":"auto"}""",
            IntentJson.encode(intent),
        )
    }

    @Test
    fun encodeWritesVoiceDevModeFalseAndDatSource() {
        val json = IntentJson.encode(sampleIntent(voiceDevMode = false, source = IntentSource.DAT, prefer = IntentPrefer.SKILL))
        assertTrue(json.contains("\"source\":\"dat\""))
        assertTrue(json.contains("\"flags\":{\"voiceDevMode\":false}"))
        assertTrue(json.contains("\"prefer\":\"skill\""))
    }

    @Test
    fun decodeAckReadsLockedDevResponse() {
        val ack =
            IntentJson.decodeAck(
                """{"intentId":"11111111-1111-1111-1111-111111111111","status":"queued","kind":"skill","prUrl":"https://example.com/pr/1","commit":"abc123","skillId":"hud-fix","message":"en cola"}""",
            )
        assertEquals("11111111-1111-1111-1111-111111111111", ack!!.intentId)
        assertEquals(DevIntentStatus.QUEUED, ack.status)
        assertEquals(DevIntentKind.SKILL, ack.kind)
        assertEquals("https://example.com/pr/1", ack.prUrl)
        assertEquals("abc123", ack.commit)
        assertEquals("hud-fix", ack.skillId)
        assertEquals("en cola", ack.message)
    }

    @Test
    fun decodeAckRequiresIntentIdAndStatus() {
        assertNull(IntentJson.decodeAck("""{"status":"queued"}"""))
        assertNull(IntentJson.decodeAck("""{"intentId":"x"}"""))
        assertNull(IntentJson.decodeAck("""{"intentId":"x","status":"nope"}"""))
    }

    @Test
    fun preferAndSourceParseKnownValues() {
        assertEquals(IntentPrefer.AUTO, IntentPrefer.parse(null))
        assertEquals(IntentPrefer.AUTO, IntentPrefer.parse("auto"))
        assertEquals(IntentPrefer.SKILL, IntentPrefer.parse("SKILL"))
        assertEquals(IntentPrefer.APK, IntentPrefer.parse("apk"))
        assertEquals(IntentPrefer.AUTO, IntentPrefer.parse("maybe"))
        assertEquals(IntentSource.CHAT, IntentSource.parse(null))
        assertEquals(IntentSource.DAT, IntentSource.parse("dat"))
        assertEquals(IntentSource.MIC, IntentSource.parse("MIC"))
        assertEquals(IntentSource.CHAT, IntentSource.parse("voice"))
    }
}

class IntentEgressTest {
    @Test
    fun postsOnlyWhenVoiceDevModeIsOn() {
        val queue = IntentQueue()
        val client = RecordingWebhookClient()
        var voiceDevMode = false
        var url = "https://example.com/hook"
        val egress =
            IntentEgress(
                queue = queue,
                client = client,
                isVoiceDevMode = { voiceDevMode },
                webhookUrl = { url },
            )

        val off = egress.submit(sampleIntent(id = "off", voiceDevMode = false))
        assertEquals(1, queue.size)
        assertFalse(off.posted)
        assertEquals(IntentEgressResult.REASON_FLAG_OFF, off.reason)
        assertEquals(0, client.posts.size)
        assertEquals("Queued (Dev mode off — no POST)", off.statusLine)

        voiceDevMode = true
        val on = egress.submit(sampleIntent(id = "on", voiceDevMode = true, utterance = "ship it"))
        assertEquals(2, queue.size)
        assertTrue(on.posted)
        assertEquals(IntentEgressResult.REASON_POSTED, on.reason)
        assertEquals(1, client.posts.size)
        assertEquals(url, client.posts[0].first)
        assertEquals(IntentJson.encode(queue.snapshot().last().intent), client.posts[0].second)
        assertTrue(client.posts[0].second.contains("\"source\":\"chat\""))
        assertTrue(client.posts[0].second.contains("\"flags\":{\"voiceDevMode\":true}"))
        assertEquals(DevIntentStatus.QUEUED, queue.last()!!.ack!!.status)
        assertEquals("en cola", on.ack!!.message)
    }

    @Test
    fun skipsPostWhenWebhookUrlMissingOrUnsupported() {
        val client = RecordingWebhookClient()
        val emptyUrl =
            IntentEgress(
                queue = IntentQueue(),
                client = client,
                isVoiceDevMode = { true },
                webhookUrl = { "  " },
            ).submit(sampleIntent(id = "blank"))
        assertFalse(emptyUrl.posted)
        assertEquals(IntentEgressResult.REASON_NO_URL, emptyUrl.reason)

        val badUrl =
            IntentEgress(
                queue = IntentQueue(),
                client = client,
                isVoiceDevMode = { true },
                webhookUrl = { "ftp://example.com/hook" },
            ).submit(sampleIntent(id = "ftp"))
        assertFalse(badUrl.posted)
        assertEquals(IntentEgressResult.REASON_BAD_URL, badUrl.reason)
        assertEquals(0, client.posts.size)
        assertTrue(IntentWebhookUrls.isSupported("https://example.com"))
        assertTrue(IntentWebhookUrls.isSupported("http://10.0.0.8:8080/hook"))
        assertFalse(IntentWebhookUrls.isSupported("file:///tmp/x"))
    }

    @Test
    fun recordsHttpFailureWithoutDroppingTheQueuedIntent() {
        val queue = IntentQueue()
        val client = RecordingWebhookClient(response = Result.failure(IllegalStateException("HTTP 503 down")))
        val result =
            IntentEgress(
                queue = queue,
                client = client,
                isVoiceDevMode = { true },
                webhookUrl = { "https://example.com/hook" },
            ).submit(sampleIntent(id = "fail"))
        assertEquals(1, queue.size)
        assertFalse(result.posted)
        assertEquals(IntentEgressResult.REASON_HTTP_ERROR, result.reason)
        assertEquals("HTTP 503 down", result.error)
        assertNull(queue.last()!!.ack)
    }
}

private class RecordingWebhookClient(
    private val response: Result<String> =
        Result.success(
            """{"intentId":"on","status":"queued","kind":"skill","message":"en cola"}""",
        ),
) : IntentWebhookClient {
    val posts = mutableListOf<Pair<String, String>>()

    override fun post(
        url: String,
        json: String,
    ): Result<String> {
        posts += url to json
        return response
    }
}

private fun sampleIntent(
    id: String = "11111111-1111-1111-1111-111111111111",
    utterance: String = "hello",
    source: IntentSource = IntentSource.CHAT,
    voiceDevMode: Boolean = true,
    prefer: IntentPrefer = IntentPrefer.AUTO,
): VoiceIntent =
    VoiceIntent(
        id = id,
        utterance = utterance,
        source = source,
        ts = "2026-09-07T21:00:00Z",
        deviceId = "tablet",
        appVersion = "0.1.0-debug",
        voiceDevMode = voiceDevMode,
        prefer = prefer,
    )
