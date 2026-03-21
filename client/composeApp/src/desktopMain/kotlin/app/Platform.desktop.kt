package app

import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java

actual fun platformType(): PlatformType = PlatformType.DESKTOP

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Java)
