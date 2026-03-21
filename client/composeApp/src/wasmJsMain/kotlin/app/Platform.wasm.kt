package app

import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun platformType(): PlatformType = PlatformType.WEB

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Js)
