package app

import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun platformType(): PlatformType = PlatformType.ANDROID

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp)
