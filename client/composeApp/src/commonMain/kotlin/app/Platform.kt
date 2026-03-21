package app

import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient

expect fun platformType(): PlatformType

expect fun createPlatformHttpClient(): HttpClient
