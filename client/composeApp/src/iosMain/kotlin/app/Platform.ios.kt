package app

import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun platformType(): PlatformType = PlatformType.IOS

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin)
