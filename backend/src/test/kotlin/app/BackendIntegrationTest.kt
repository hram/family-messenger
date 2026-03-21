package app

import com.familymessenger.contract.ErrorCode
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager
import java.util.UUID

class BackendIntegrationTest {
    @Test
    fun registerDeviceReturnsSessionPayload() = backendTestApp {
        val response = registerDevice(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop",
            platform = "desktop",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json()
        assertTrue(body.success())
        assertEquals("Parent", body.data().req("user").obj().req("displayName").text())
        assertEquals("parent", body.data().req("user").obj().req("role").text())
        assertTrue(body.data().req("session").obj().req("token").text().isNotBlank())
    }

    @Test
    fun registerDeviceRejectsInviteReuseWhenLimitReached() = backendTestApp {
        val first = registerDevice(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop",
            platform = "desktop",
        )
        assertEquals(HttpStatusCode.OK, first.status)

        val second = registerDevice(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop-2",
            platform = "desktop",
        )

        assertEquals(HttpStatusCode.Conflict, second.status)
        val body = second.json()
        assertFalse(body.success())
        assertEquals(ErrorCode.CONFLICT.name, body.errorCode())
    }

    @Test
    fun loginReturnsNewTokenForRegisteredDevice() = backendTestApp {
        registerDevice(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop",
            platform = "desktop",
        )

        val login = login(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop",
            platform = "desktop",
        )

        assertEquals(HttpStatusCode.OK, login.status)
        val body = login.json()
        assertTrue(body.success())
        assertEquals("Parent", body.data().req("user").obj().req("displayName").text())
        assertTrue(body.data().req("session").obj().req("token").text().isNotBlank())
    }

    @Test
    fun profileRequiresBearerToken() = backendTestApp {
        val response = client.get("/api/profile/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = response.json()
        assertFalse(body.success())
        assertEquals(ErrorCode.UNAUTHORIZED.name, body.errorCode())
    }

    @Test
    fun profileReturnsCurrentUserForValidToken() = backendTestApp {
        val auth = registerDevice(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop",
            platform = "desktop",
        ).json()
        val token = auth.token()

        val response = authorizedGet("/api/profile/me", token)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json()
        assertTrue(body.success())
        assertEquals("Parent", body.data().req("user").obj().req("displayName").text())
        assertEquals("Demo Family", body.data().req("family").obj().req("name").text())
    }

    @Test
    fun sendMessageAndSyncExposeMessageToRecipient() = backendTestApp {
        val parent = registerDevice(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop",
            platform = "desktop",
        ).json()
        val child = registerDevice(
            inviteCode = "CHILD-DEMO",
            deviceName = "child-desktop",
            platform = "desktop",
        ).json()

        val parentToken = parent.token()
        val childToken = child.token()
        val childUserId = child.data().req("user").obj().req("id").long()
        val clientMessageUuid = UUID.randomUUID().toString()

        val sendResponse = authorizedPost(
            path = "/api/messages/send",
            token = parentToken,
            body = """
                {
                  "recipientUserId": $childUserId,
                  "clientMessageUuid": "$clientMessageUuid",
                  "type": "text",
                  "body": "hello child"
                }
            """.trimIndent(),
        )

        assertEquals(HttpStatusCode.OK, sendResponse.status)
        val sendBody = sendResponse.json()
        assertTrue(sendBody.success())
        assertEquals(childUserId, sendBody.data().req("message").obj().req("recipientUserId").long())
        assertEquals(clientMessageUuid, sendBody.data().req("message").obj().req("clientMessageUuid").text())

        val syncResponse = authorizedGet("/api/messages/sync?since_id=0", childToken)

        assertEquals(HttpStatusCode.OK, syncResponse.status)
        val syncBody = syncResponse.json()
        assertTrue(syncBody.success())
        val messages = syncBody.data().req("messages").arr()
        assertEquals(1, messages.size)
        assertEquals(clientMessageUuid, messages[0].jsonObject.req("clientMessageUuid").text())
        assertTrue(syncBody.data().req("nextSinceId").long() > 0)
    }

    @Test
    fun sendMessageIsDeduplicatedByClientMessageUuid() = backendTestApp {
        val parent = registerDevice(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop",
            platform = "desktop",
        ).json()
        val child = registerDevice(
            inviteCode = "CHILD-DEMO",
            deviceName = "child-desktop",
            platform = "desktop",
        ).json()

        val token = parent.token()
        val recipientUserId = child.data().req("user").obj().req("id").long()
        val clientMessageUuid = UUID.randomUUID().toString()
        val requestBody = """
            {
              "recipientUserId": $recipientUserId,
              "clientMessageUuid": "$clientMessageUuid",
              "type": "text",
              "body": "dedup me"
            }
        """.trimIndent()

        val first = authorizedPost("/api/messages/send", token, requestBody)
        val second = authorizedPost("/api/messages/send", token, requestBody)

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.OK, second.status)

        val firstMessage = first.json().data().req("message").obj()
        val secondMessage = second.json().data().req("message").obj()
        assertEquals(firstMessage.req("id").text(), secondMessage.req("id").text())
        assertEquals(firstMessage.req("clientMessageUuid").text(), secondMessage.req("clientMessageUuid").text())
    }

    @Test
    fun authEndpointsRespectRateLimit() = backendTestApp(
        authRateLimitMaxRequests = 2,
        authRateLimitWindowSeconds = 60,
    ) {
        registerDevice(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop",
            platform = "desktop",
            clientKey = "setup-client",
        )

        val first = login(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop",
            platform = "desktop",
            clientKey = "rate-limit-client",
        )
        val second = login(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop",
            platform = "desktop",
            clientKey = "rate-limit-client",
        )
        val third = login(
            inviteCode = "PARENT-DEMO",
            deviceName = "parent-desktop",
            platform = "desktop",
            clientKey = "rate-limit-client",
        )

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(HttpStatusCode.TooManyRequests, third.status)
        val body = third.json()
        assertFalse(body.success())
        assertEquals(ErrorCode.RATE_LIMITED.name, body.errorCode())
    }

    @Test
    fun openApiJsonExposesProtectedRoutesAndBearerAuth() = backendTestApp {
        val response = client.get("/api-docs/openapi.json")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"/api/profile/me\""))
        assertTrue(body.contains("\"/api/messages/send\""))
        assertTrue(body.contains("\"bearerAuth\""))
        assertTrue(body.contains("\"security\""))
    }
}

private fun backendTestApp(
    authRateLimitMaxRequests: Int = 10,
    authRateLimitWindowSeconds: Int = 60,
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    val jdbcUrl = testJdbcUrl()
    resetTestDatabase(jdbcUrl)

    environment {
        config = MapApplicationConfig(
            "app.name" to "family-messenger-backend-test",
            "app.version" to "test",
            "app.database.jdbcUrl" to jdbcUrl,
            "app.database.user" to "sa",
            "app.database.password" to "",
            "app.database.driver" to "org.h2.Driver",
            "app.database.bootstrapSchema" to "true",
            "app.database.seedOnStart" to "true",
            "app.auth.tokenTtlHours" to "720",
            "app.rateLimit.enabled" to "true",
            "app.rateLimit.authWindowSeconds" to authRateLimitWindowSeconds.toString(),
            "app.rateLimit.authMaxRequestsPerWindow" to authRateLimitMaxRequests.toString(),
        )
    }

    application {
        module()
    }

    block()
}

private suspend fun ApplicationTestBuilder.registerDevice(
    inviteCode: String,
    deviceName: String,
    platform: String,
    clientKey: String = "test-client",
): HttpResponse = client.post("/api/auth/register-device") {
    contentType(ContentType.Application.Json)
    header("X-Forwarded-For", clientKey)
    setBody(
        """
            {
              "inviteCode": "$inviteCode",
              "deviceName": "$deviceName",
              "platform": "$platform"
            }
        """.trimIndent(),
    )
}

private suspend fun ApplicationTestBuilder.login(
    inviteCode: String,
    deviceName: String,
    platform: String,
    clientKey: String = "test-client",
): HttpResponse = client.post("/api/auth/login") {
    contentType(ContentType.Application.Json)
    header("X-Forwarded-For", clientKey)
    setBody(
        """
            {
              "inviteCode": "$inviteCode",
              "deviceName": "$deviceName",
              "platform": "$platform"
            }
        """.trimIndent(),
    )
}

private suspend fun ApplicationTestBuilder.authorizedGet(path: String, token: String): HttpResponse =
    client.get(path) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

private suspend fun ApplicationTestBuilder.authorizedPost(path: String, token: String, body: String): HttpResponse =
    client.post(path) {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Authorization, "Bearer $token")
        setBody(body)
    }

private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

private fun JsonObject.success(): Boolean = this["success"]!!.jsonPrimitive.boolean

private fun JsonObject.data(): JsonObject = this["data"]!!.jsonObject

private fun JsonObject.errorCode(): String = this["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

private fun JsonObject.token(): String = data().req("session").obj().req("token").text()

private fun JsonObject.obj(): JsonObject = this

private fun JsonObject.req(key: String) = this.getValue(key)

private fun kotlinx.serialization.json.JsonElement.obj(): JsonObject = this.jsonObject

private fun kotlinx.serialization.json.JsonElement.arr(): JsonArray = this.jsonArray

private fun kotlinx.serialization.json.JsonElement.text(): String = this.jsonPrimitive.content

private fun kotlinx.serialization.json.JsonElement.long(): Long = this.jsonPrimitive.content.toLong()

private fun testJdbcUrl(): String =
    "jdbc:h2:mem:backend-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"

private fun resetTestDatabase(jdbcUrl: String) {
    DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
        connection.createStatement().use { statement ->
            statement.execute("DROP ALL OBJECTS")
        }
    }
}
