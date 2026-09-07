package com.fedesack.raybanmetadat

data class QueuedIntent(
    val intent: VoiceIntent,
    val ack: DevIntentAck? = null,
)

class IntentQueue(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val lock = Any()
    private val items = ArrayDeque<QueuedIntent>()

    val size: Int
        get() = synchronized(lock) { items.size }

    fun enqueue(intent: VoiceIntent): VoiceIntent =
        synchronized(lock) {
            while (items.size >= capacity) {
                items.removeFirst()
            }
            items.addLast(QueuedIntent(intent))
            intent
        }

    fun snapshot(): List<QueuedIntent> = synchronized(lock) { items.toList() }

    fun last(): QueuedIntent? = synchronized(lock) { items.lastOrNull() }

    fun ack(response: DevIntentAck): DevIntentAck =
        synchronized(lock) {
            val index = items.indexOfFirst { it.intent.id == response.intentId }
            if (index >= 0) {
                items[index] = items[index].copy(ack = response)
            }
            response
        }

    companion object {
        const val DEFAULT_CAPACITY = 32
    }
}
