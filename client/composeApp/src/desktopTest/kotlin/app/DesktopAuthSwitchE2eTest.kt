package app

import app.module as backendModule
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.server.application.serverConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.ServerSocket
import java.util.UUID

class DesktopAuthSwitchE2eTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var backend: RealBackendServer
    private lateinit var clientApp: ClientApp

    @Before
    fun setUp() {
        stopKoin()
        backend = RealBackendServer.start()
        clientApp = ClientApp.create(testPlatformServices(backend.baseUrl))
        rule.setContent {
            FamilyMessengerApp(clientApp.viewModel)
        }
    }

    @After
    fun tearDown() {
        if (::clientApp.isInitialized) {
            clientApp.close()
        } else {
            stopKoin()
        }
        if (::backend.isInitialized) {
            backend.close()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun desktopHappyPathSwitchesUsersAndKeepsChatsSeparated() {
        registerThroughUi("PARENT-DEMO")
        rule.waitForContact(0)
        rule.onNodeWithText("Demo Family").assertExists()

        logoutThroughUi()

        registerThroughUi("CHILD-DEMO")
        rule.waitForContact(0)
        rule.waitForText("Parent")

        rule.onNodeWithTag(contactRowTag(0)).performClick()
        sendMessage("family hello from child")

        rule.onNodeWithText("Parent").performClick()
        sendMessage("direct hello from child")

        logoutThroughUi()

        loginThroughUi("PARENT-DEMO")
        rule.waitForContact(0)
        rule.waitForText("Child")

        rule.onNodeWithTag(contactRowTag(0)).performClick()
        rule.waitForText("family hello from child")
        rule.onNodeWithText("direct hello from child").assertDoesNotExist()

        rule.onNodeWithText("Child").performClick()
        rule.waitForText("direct hello from child")
        rule.onNodeWithText("family hello from child").assertDoesNotExist()
    }

    private fun registerThroughUi(inviteCode: String) {
        rule.onNodeWithTag(AppTestTags.OnboardingRegisterTab).performClick()
        rule.onNodeWithTag(AppTestTags.OnboardingBaseUrl).performTextReplacement(backend.baseUrl)
        rule.onNodeWithTag(AppTestTags.OnboardingInviteCode).performTextReplacement(inviteCode)
        rule.onNodeWithTag(AppTestTags.OnboardingSubmit).performClick()
    }

    private fun loginThroughUi(inviteCode: String) {
        rule.onNodeWithTag(AppTestTags.OnboardingLoginTab).performClick()
        rule.onNodeWithTag(AppTestTags.OnboardingBaseUrl).performTextReplacement(backend.baseUrl)
        rule.onNodeWithTag(AppTestTags.OnboardingInviteCode).performTextReplacement(inviteCode)
        rule.onNodeWithTag(AppTestTags.OnboardingSubmit).performClick()
    }

    private fun logoutThroughUi() {
        rule.onNodeWithTag(AppTestTags.TopBarSettings)
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForTag(AppTestTags.SettingsLogout)
        rule.onNodeWithTag(AppTestTags.SettingsLogout).performClick()
        rule.waitForTag(AppTestTags.OnboardingSubmit)
    }

    private fun sendMessage(message: String) {
        rule.onNodeWithTag(AppTestTags.ChatInput).performTextReplacement(message)
        rule.onNodeWithTag(AppTestTags.ChatSend).performClick()
        rule.waitForText(message)
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeContentTestRule.waitForContact(contactId: Long, timeoutMillis: Long = 15_000L) {
    waitForTag(contactRowTag(contactId), timeoutMillis)
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeContentTestRule.waitForText(text: String, timeoutMillis: Long = 15_000L) {
    waitUntil(timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeContentTestRule.waitForTag(tag: String, timeoutMillis: Long = 15_000L) {
    waitUntil(timeoutMillis) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun testPlatformServices(baseUrl: String): PlatformServices =
    PlatformServices(
        platformInfo = PlatformInfo(
            type = PlatformType.DESKTOP,
            displayName = "Desktop Test",
            defaultBaseUrl = baseUrl,
        ),
        httpClient = HttpClient(OkHttp),
        settingsStore = InMemoryStore(),
        secureStore = InMemoryStore(),
        geolocationService = object : GeolocationService {
            override suspend fun currentLocation() = null
        },
        notificationService = object : NotificationService {
            override fun notify(title: String, body: String) = Unit
        },
    )

private class InMemoryStore : KeyValueStore {
    private val values = linkedMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

private class RealBackendServer private constructor(
    val baseUrl: String,
    private val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>,
) : AutoCloseable {
    override fun close() {
        server.stop(1_000, 3_000)
    }

    companion object {
        fun start(): RealBackendServer {
            val port = ServerSocket(0).use { it.localPort }
            val jdbcUrl = "jdbc:h2:mem:desktop-e2e-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
            val environment = applicationEnvironment {
                config = MapApplicationConfig(
                    "app.name" to "family-messenger-desktop-e2e",
                    "app.version" to "test",
                    "app.database.jdbcUrl" to jdbcUrl,
                    "app.database.user" to "sa",
                    "app.database.password" to "",
                    "app.database.driver" to "org.h2.Driver",
                    "app.database.bootstrapSchema" to "true",
                    "app.database.seedOnStart" to "true",
                    "app.auth.tokenTtlHours" to "720",
                    "app.rateLimit.enabled" to "true",
                    "app.rateLimit.authWindowSeconds" to "60",
                    "app.rateLimit.authMaxRequestsPerWindow" to "20",
                )
            }
            val rootConfig = serverConfig(environment) {
                module {
                    backendModule()
                }
            }
            val server = embeddedServer(Netty, rootConfig) {
                connectors.add(
                    EngineConnectorBuilder().apply {
                        this.port = port
                        this.host = "127.0.0.1"
                    },
                )
            }.start(wait = false)

            val baseUrl = "http://127.0.0.1:$port"
            waitForHealth(baseUrl)
            return RealBackendServer(baseUrl, server)
        }

        private fun waitForHealth(baseUrl: String) {
            val client = HttpClient(OkHttp)
            try {
                repeat(50) {
                    runCatching {
                        runBlocking { client.get("$baseUrl/api/health") }
                    }.onSuccess { response ->
                        if (response.status.value == 200) return
                    }
                    Thread.sleep(100)
                }
            } finally {
                client.close()
            }
            error("Backend did not become healthy at $baseUrl")
        }
    }
}
