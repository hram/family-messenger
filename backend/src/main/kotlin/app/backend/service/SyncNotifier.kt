package app.backend.service

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory pub/sub для long-polling: позволяет разбудить всех слушателей семьи
 * при появлении нового sync-события.
 */
class SyncNotifier {
    private val nextSubscriptionId = AtomicLong(1)
    // familyId → (subscriptionId → Channel)
    private val channels = ConcurrentHashMap<Long, ConcurrentHashMap<Long, Channel<Unit>>>()

    fun subscribe(familyId: Long): Subscription {
        val subscriptionId = nextSubscriptionId.getAndIncrement()
        val channel = Channel<Unit>(Channel.CONFLATED)
        channels.getOrPut(familyId) { ConcurrentHashMap() }[subscriptionId] = channel
        return Subscription(familyId, subscriptionId, channel)
    }

    fun unsubscribe(subscription: Subscription) {
        val familyChannels = channels[subscription.familyId] ?: return
        familyChannels.remove(subscription.id)?.close()
        if (familyChannels.isEmpty()) {
            channels.remove(subscription.familyId, familyChannels)
        }
    }

    /** Будит всех участников семьи, ожидающих новых данных. */
    fun notifyFamily(familyId: Long) {
        channels[familyId]?.values?.forEach { it.trySend(Unit) }
    }

    data class Subscription(
        val familyId: Long,
        val id: Long,
        val channel: Channel<Unit>,
    )
}
