package app.backend.service

import app.backend.config.RateLimitConfig
import app.backend.error.RateLimitedException
import java.util.concurrent.ConcurrentHashMap

class RateLimitService(
    private val config: RateLimitConfig,
) {
    private val authWindowHits = ConcurrentHashMap<String, MutableList<Long>>()

    fun checkAuth(clientKey: String, nowMillis: Long = System.currentTimeMillis()) {
        if (!config.enabled) {
            return
        }

        val windowStart = nowMillis - (config.authWindowSeconds * 1000)
        val hits = authWindowHits.computeIfAbsent(clientKey) { mutableListOf() }
        synchronized(hits) {
            hits.removeAll { it < windowStart }
            if (hits.size >= config.authMaxRequestsPerWindow) {
                throw RateLimitedException("Too many authentication attempts")
            }
            hits += nowMillis
        }
    }
}
