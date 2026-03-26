package app.backend.service

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SyncNotifierTest {
    @Test
    fun notifyFamilyWakesAllSubscribersInSameFamily() = runBlocking {
        val notifier = SyncNotifier()
        val first = notifier.subscribe(familyId = 1)
        val second = notifier.subscribe(familyId = 1)
        val otherFamily = notifier.subscribe(familyId = 2)

        try {
            val firstWake = async { withTimeout(1_000) { first.channel.receive() } }
            val secondWake = async { withTimeout(1_000) { second.channel.receive() } }

            notifier.notifyFamily(1)

            assertNotNull(firstWake.await())
            assertNotNull(secondWake.await())
            assertNull(withTimeoutOrNull(200) { otherFamily.channel.receive() })
        } finally {
            notifier.unsubscribe(first)
            notifier.unsubscribe(second)
            notifier.unsubscribe(otherFamily)
        }
    }
}
