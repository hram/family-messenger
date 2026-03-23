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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.UUID

class BackendIntegrationTest {
    @Test
    fun setupStatusIsFalseOnCleanDatabaseWithoutSeed() = backendTestApp(seedOnStart = false) {
        val response = client.get("/api/setup/status")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json()
        assertTrue(body.success())
        assertEquals("false", body.data().req("initialized").text())
    }

    @Test
    fun bootstrapInitializesSystemAndReturnsInviteCodes() = backendTestApp(seedOnStart = false) {
        val bootstrap = bootstrap(
            """
                {
                  "masterPassword": "super-secret-pass",
                  "familyName": "Family One",
                  "members": [
                    { "displayName": "Masha", "role": "parent", "isAdmin": true },
                    { "displayName": "Pasha", "role": "child" }
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(HttpStatusCode.OK, bootstrap.status)
        val body = bootstrap.json()
        assertTrue(body.success())
        assertEquals("Family One", body.data().req("family").obj().req("name").text())
        assertEquals(2, body.data().req("invites").arr().size)
        assertTrue(body.data().req("invites").arr().any { it.jsonObject.req("isAdmin").jsonPrimitive.boolean })

        val parentInvite = body.data().req("invites").arr().first { it.jsonObject.req("displayName").text() == "Masha" }
            .jsonObject.req("inviteCode").text()

        val login = login(
            inviteCode = parentInvite,
            platform = "web",
        )
        assertEquals(HttpStatusCode.OK, login.status)
        assertEquals("true", login.json().data().req("user").obj().req("isAdmin").text())

        val statusAfterBootstrap = client.get("/api/setup/status").json()
        assertEquals("true", statusAfterBootstrap.data().req("initialized").text())
        assertEquals("Family One", statusAfterBootstrap.data().req("familyName").text())
    }

    @Test
    fun bootstrapCannotRunTwice() = backendTestApp(seedOnStart = false) {
        val payload = """
            {
              "masterPassword": "super-secret-pass",
              "familyName": "Family One",
              "members": [
                { "displayName": "Masha", "role": "parent", "isAdmin": true }
              ]
            }
        """.trimIndent()

        val first = bootstrap(payload)
        val second = bootstrap(payload)

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertEquals(ErrorCode.CONFLICT.name, second.json().errorCode())
    }

    @Test
    fun loginReturnsSessionPayload() = backendTestApp {
        val response = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json()
        assertTrue(body.success())
        assertEquals("Parent", body.data().req("user").obj().req("displayName").text())
        assertEquals("parent", body.data().req("user").obj().req("role").text())
        assertEquals("true", body.data().req("user").obj().req("isAdmin").text())
        assertTrue(body.data().req("session").obj().req("token").text().isNotBlank())
    }

    @Test
    fun loginReusesBoundInviteForSameUserAndPlatform() = backendTestApp {
        val first = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        )
        assertEquals(HttpStatusCode.OK, first.status)

        val second = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        )

        assertEquals(HttpStatusCode.OK, second.status)
        val body = second.json()
        assertTrue(body.success())
        assertEquals("Parent", body.data().req("user").obj().req("displayName").text())
    }

    @Test
    fun boundInviteCanAuthenticateOnNewPlatformWithoutSeparateRegistration() = backendTestApp {
        val first = login(
            inviteCode = "CHILD-DEMO",
            platform = "desktop",
        )
        assertEquals(HttpStatusCode.OK, first.status)

        val second = login(
            inviteCode = "CHILD-DEMO",
            platform = "android",
        )

        assertEquals(HttpStatusCode.OK, second.status)
        val body = second.json()
        assertTrue(body.success())
        assertEquals("Child", body.data().req("user").obj().req("displayName").text())
        assertEquals("child", body.data().req("user").obj().req("role").text())
    }

    @Test
    fun loginReturnsNewTokenForRegisteredDevice() = backendTestApp {
        login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        )

        val login = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        )

        assertEquals(HttpStatusCode.OK, login.status)
        val body = login.json()
        assertTrue(body.success())
        assertEquals("Parent", body.data().req("user").obj().req("displayName").text())
        assertTrue(body.data().req("session").obj().req("token").text().isNotBlank())
    }

    @Test
    fun loginCreatesDeviceForNewPlatformWhenInviteAlreadyBound() = backendTestApp {
        login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        )

        val login = login(
            inviteCode = "PARENT-DEMO",
            platform = "android",
        )

        assertEquals(HttpStatusCode.OK, login.status)
        val body = login.json()
        assertTrue(body.success())
        assertEquals("Parent", body.data().req("user").obj().req("displayName").text())
        assertTrue(body.data().req("session").obj().req("token").text().isNotBlank())
    }

    @Test
    fun loginResolvesUserFromInviteCodeWithoutCrossRoleLeak() = backendTestApp {
        login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        )
        login(
            inviteCode = "CHILD-DEMO",
            platform = "desktop",
        )

        val login = login(
            inviteCode = "CHILD-DEMO",
            platform = "desktop",
        )

        assertEquals(HttpStatusCode.OK, login.status)
        val body = login.json()
        assertTrue(body.success())
        assertEquals("Child", body.data().req("user").obj().req("displayName").text())
        assertEquals("child", body.data().req("user").obj().req("role").text())
    }

    @Test
    fun contactsAlwaysIncludeFamilyGroupChat() = backendTestApp {
        val auth = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        ).json()

        val response = authorizedGet("/api/contacts", auth.token())

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json()
        assertTrue(body.success())
        val contacts = body.data().req("contacts").arr()
        assertEquals(1, contacts.size)
        assertEquals("0", contacts[0].jsonObject.req("user").obj().req("id").text())
        assertEquals("Demo Family", contacts[0].jsonObject.req("user").obj().req("displayName").text())
        assertEquals("family", contacts[0].jsonObject.req("user").obj().req("role").text())
    }

    @Test
    fun familyGroupMessageIsVisibleToOtherFamilyMembers() = backendTestApp {
        val parent = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        ).json()
        val child = login(
            inviteCode = "CHILD-DEMO",
            platform = "desktop",
        ).json()

        val clientMessageUuid = UUID.randomUUID().toString()
        val sendResponse = authorizedPost(
            path = "/api/messages/send",
            token = parent.token(),
            body = """
                {
                  "recipientUserId": 0,
                  "clientMessageUuid": "$clientMessageUuid",
                  "type": "text",
                  "body": "hello family"
                }
            """.trimIndent(),
        )

        assertEquals(HttpStatusCode.OK, sendResponse.status)
        val sendBody = sendResponse.json()
        assertTrue(sendBody.success())
        assertEquals("0", sendBody.data().req("message").obj().req("recipientUserId").text())

        val syncResponse = authorizedGet("/api/messages/sync?since_id=0", child.token())

        assertEquals(HttpStatusCode.OK, syncResponse.status)
        val syncBody = syncResponse.json()
        assertTrue(syncBody.success())
        val messages = syncBody.data().req("messages").arr()
        assertEquals(1, messages.size)
        assertEquals("0", messages[0].jsonObject.req("recipientUserId").text())
        assertEquals(clientMessageUuid, messages[0].jsonObject.req("clientMessageUuid").text())
    }

    /**
     * End-to-end auth switching scenario on a clean test database.
     *
     * The test registers both demo users, logs in as parent and child, and verifies that:
     * - each token resolves to the expected user profile and role
     * - contacts are rebuilt per current user session
     * - the family chat is always present
     * - direct messages stay in the correct dialog and do not leak into the other user's
     *   "sent by me" side after switching accounts
     *
     * This is an API-level integration test. It validates backend session and data isolation,
     * not desktop/mobile UI state restoration.
     */
    @Test
    fun authSwitchingKeepsUsersChatsAndMessagesSeparated() = backendTestApp {
        login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        )
        login(
            inviteCode = "CHILD-DEMO",
            platform = "desktop",
        )

        val parentSession = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        ).json()
        val childSession = login(
            inviteCode = "CHILD-DEMO",
            platform = "desktop",
        ).json()

        val parentToken = parentSession.token()
        val childToken = childSession.token()
        val parentUser = parentSession.data().req("user").obj()
        val childUser = childSession.data().req("user").obj()
        val parentUserId = parentUser.req("id").long()
        val childUserId = childUser.req("id").long()

        val parentProfile = authorizedGet("/api/profile/me", parentToken).json()
        assertEquals(parentUserId, parentProfile.data().req("user").obj().req("id").long())
        assertEquals("parent", parentProfile.data().req("user").obj().req("role").text())

        val parentContacts = authorizedGet("/api/contacts", parentToken).json().data().req("contacts").arr()
        assertEquals(2, parentContacts.size)
        assertEquals("0", parentContacts[0].jsonObject.req("user").obj().req("id").text())
        assertEquals(childUserId, parentContacts[1].jsonObject.req("user").obj().req("id").long())

        val parentToChildUuid = UUID.randomUUID().toString()
        authorizedPost(
            path = "/api/messages/send",
            token = parentToken,
            body = """
                {
                  "recipientUserId": $childUserId,
                  "clientMessageUuid": "$parentToChildUuid",
                  "type": "text",
                  "body": "message from parent"
                }
            """.trimIndent(),
        ).also { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }

        val childProfile = authorizedGet("/api/profile/me", childToken).json()
        assertEquals(childUserId, childProfile.data().req("user").obj().req("id").long())
        assertEquals("child", childProfile.data().req("user").obj().req("role").text())

        val childContacts = authorizedGet("/api/contacts", childToken).json().data().req("contacts").arr()
        assertEquals(2, childContacts.size)
        assertEquals("0", childContacts[0].jsonObject.req("user").obj().req("id").text())
        assertEquals(parentUserId, childContacts[1].jsonObject.req("user").obj().req("id").long())

        val childSyncAfterParentMessage = authorizedGet("/api/messages/sync?since_id=0", childToken).json()
        val childMessages = childSyncAfterParentMessage.data().req("messages").arr()
        assertTrue(childMessages.any { it.jsonObject.req("clientMessageUuid").text() == parentToChildUuid })
        assertFalse(childMessages.any {
            val payload = it.jsonObject
            payload.req("senderUserId").long() == childUserId && payload.req("recipientUserId").long() == parentUserId
        })

        val childToParentUuid = UUID.randomUUID().toString()
        authorizedPost(
            path = "/api/messages/send",
            token = childToken,
            body = """
                {
                  "recipientUserId": $parentUserId,
                  "clientMessageUuid": "$childToParentUuid",
                  "type": "text",
                  "body": "reply from child"
                }
            """.trimIndent(),
        ).also { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }

        val parentSyncAfterReply = authorizedGet("/api/messages/sync?since_id=0", parentToken).json()
        val parentMessages = parentSyncAfterReply.data().req("messages").arr()
        assertTrue(parentMessages.any { it.jsonObject.req("clientMessageUuid").text() == parentToChildUuid })
        assertTrue(parentMessages.any { it.jsonObject.req("clientMessageUuid").text() == childToParentUuid })
        assertFalse(parentMessages.any {
            val payload = it.jsonObject
            payload.req("senderUserId").long() == parentUserId && payload.req("recipientUserId").long() == parentUserId
        })
    }

    /**
     * End-to-end family group chat scenario on a clean test database with three users.
     *
     * The test creates a parent and two children in the same family, then verifies that:
     * - every user sees the family group chat plus the other two family members in contacts
     * - messages sent to recipientUserId = 0 form one shared conversation for the whole family
     * - after switching between three different sessions, each user still sees the same shared
     *   family chat history instead of a mixed or user-specific copy
     *
     * This is an API-level integration test for backend message and session isolation.
     */
    @Test
    fun familyGroupChatRemainsSharedWhenSwitchingBetweenThreeUsers() = backendTestApp {
        val parentRegistration = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
            clientKey = "family-group-parent-registration",
        ).json()
        val childRegistration = login(
            inviteCode = "CHILD-DEMO",
            platform = "desktop",
            clientKey = "family-group-child-registration",
        ).json()

        insertInvite(
            code = "SIBLING-DEMO",
            displayName = "Sibling",
            role = "CHILD",
        )

        val siblingRegistration = login(
            inviteCode = "SIBLING-DEMO",
            platform = "desktop",
            clientKey = "family-group-sibling-registration",
        ).json()

        val parentSession = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
            clientKey = "family-group-parent-session",
        ).json()
        val childSession = login(
            inviteCode = "CHILD-DEMO",
            platform = "desktop",
            clientKey = "family-group-child-session",
        ).json()
        val siblingSession = login(
            inviteCode = "SIBLING-DEMO",
            platform = "desktop",
            clientKey = "family-group-sibling-session",
        ).json()

        val parentToken = parentSession.token()
        val childToken = childSession.token()
        val siblingToken = siblingSession.token()

        val parentUserId = parentRegistration.data().req("user").obj().req("id").long()
        val childUserId = childRegistration.data().req("user").obj().req("id").long()
        val siblingUserId = siblingRegistration.data().req("user").obj().req("id").long()

        val parentContacts = authorizedGet("/api/contacts", parentToken).json().data().req("contacts").arr()
        assertEquals(3, parentContacts.size)
        assertEquals("0", parentContacts[0].jsonObject.req("user").obj().req("id").text())
        assertTrue(parentContacts.any { it.jsonObject.req("user").obj().req("id").long() == childUserId })
        assertTrue(parentContacts.any { it.jsonObject.req("user").obj().req("id").long() == siblingUserId })

        val childContacts = authorizedGet("/api/contacts", childToken).json().data().req("contacts").arr()
        assertEquals(3, childContacts.size)
        assertEquals("0", childContacts[0].jsonObject.req("user").obj().req("id").text())
        assertTrue(childContacts.any { it.jsonObject.req("user").obj().req("id").long() == parentUserId })
        assertTrue(childContacts.any { it.jsonObject.req("user").obj().req("id").long() == siblingUserId })

        val siblingContacts = authorizedGet("/api/contacts", siblingToken).json().data().req("contacts").arr()
        assertEquals(3, siblingContacts.size)
        assertEquals("0", siblingContacts[0].jsonObject.req("user").obj().req("id").text())
        assertTrue(siblingContacts.any { it.jsonObject.req("user").obj().req("id").long() == parentUserId })
        assertTrue(siblingContacts.any { it.jsonObject.req("user").obj().req("id").long() == childUserId })

        val parentFamilyMessageUuid = UUID.randomUUID().toString()
        authorizedPost(
            path = "/api/messages/send",
            token = parentToken,
            body = """
                {
                  "recipientUserId": 0,
                  "clientMessageUuid": "$parentFamilyMessageUuid",
                  "type": "text",
                  "body": "hello from parent"
                }
            """.trimIndent(),
        ).also { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }

        val childSyncAfterParent = authorizedGet("/api/messages/sync?since_id=0", childToken).json()
        val childMessagesAfterParent = childSyncAfterParent.data().req("messages").arr()
        assertTrue(childMessagesAfterParent.any { it.jsonObject.req("clientMessageUuid").text() == parentFamilyMessageUuid })
        assertTrue(childMessagesAfterParent.all { it.jsonObject.req("recipientUserId").text() == "0" })
        assertEquals(
            parentUserId,
            childMessagesAfterParent.first { it.jsonObject.req("clientMessageUuid").text() == parentFamilyMessageUuid }
                .jsonObject.req("senderUserId").long(),
        )

        val childFamilyMessageUuid = UUID.randomUUID().toString()
        authorizedPost(
            path = "/api/messages/send",
            token = childToken,
            body = """
                {
                  "recipientUserId": 0,
                  "clientMessageUuid": "$childFamilyMessageUuid",
                  "type": "text",
                  "body": "hello from child"
                }
            """.trimIndent(),
        ).also { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }

        val siblingSyncAfterTwoMessages = authorizedGet("/api/messages/sync?since_id=0", siblingToken).json()
        val siblingMessagesAfterTwo = siblingSyncAfterTwoMessages.data().req("messages").arr()
        assertTrue(siblingMessagesAfterTwo.any { it.jsonObject.req("clientMessageUuid").text() == parentFamilyMessageUuid })
        assertTrue(siblingMessagesAfterTwo.any { it.jsonObject.req("clientMessageUuid").text() == childFamilyMessageUuid })
        assertTrue(siblingMessagesAfterTwo.all { it.jsonObject.req("recipientUserId").text() == "0" })
        assertEquals(
            parentUserId,
            siblingMessagesAfterTwo.first { it.jsonObject.req("clientMessageUuid").text() == parentFamilyMessageUuid }
                .jsonObject.req("senderUserId").long(),
        )
        assertEquals(
            childUserId,
            siblingMessagesAfterTwo.first { it.jsonObject.req("clientMessageUuid").text() == childFamilyMessageUuid }
                .jsonObject.req("senderUserId").long(),
        )

        val siblingFamilyMessageUuid = UUID.randomUUID().toString()
        authorizedPost(
            path = "/api/messages/send",
            token = siblingToken,
            body = """
                {
                  "recipientUserId": 0,
                  "clientMessageUuid": "$siblingFamilyMessageUuid",
                  "type": "text",
                  "body": "hello from sibling"
                }
            """.trimIndent(),
        ).also { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }

        val parentSyncAfterThreeMessages = authorizedGet("/api/messages/sync?since_id=0", parentToken).json()
        val parentMessagesAfterThree = parentSyncAfterThreeMessages.data().req("messages").arr()
        assertTrue(parentMessagesAfterThree.any { it.jsonObject.req("clientMessageUuid").text() == parentFamilyMessageUuid })
        assertTrue(parentMessagesAfterThree.any { it.jsonObject.req("clientMessageUuid").text() == childFamilyMessageUuid })
        assertTrue(parentMessagesAfterThree.any { it.jsonObject.req("clientMessageUuid").text() == siblingFamilyMessageUuid })
        assertTrue(parentMessagesAfterThree.all { it.jsonObject.req("recipientUserId").text() == "0" })
        assertEquals(
            parentUserId,
            parentMessagesAfterThree.first { it.jsonObject.req("clientMessageUuid").text() == parentFamilyMessageUuid }
                .jsonObject.req("senderUserId").long(),
        )
        assertEquals(
            childUserId,
            parentMessagesAfterThree.first { it.jsonObject.req("clientMessageUuid").text() == childFamilyMessageUuid }
                .jsonObject.req("senderUserId").long(),
        )
        assertEquals(
            siblingUserId,
            parentMessagesAfterThree.first { it.jsonObject.req("clientMessageUuid").text() == siblingFamilyMessageUuid }
                .jsonObject.req("senderUserId").long(),
        )
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
        val auth = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        ).json()
        val token = auth.token()

        val response = authorizedGet("/api/profile/me", token)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json()
        assertTrue(body.success())
        assertEquals("Parent", body.data().req("user").obj().req("displayName").text())
        assertEquals("Demo Family", body.data().req("family").obj().req("name").text())
        assertEquals("true", body.data().req("user").obj().req("isAdmin").text())
    }

    @Test
    fun bootstrapRequiresAtLeastOneParentAdministrator() = backendTestApp(seedOnStart = false) {
        val response = bootstrap(
            """
                {
                  "masterPassword": "super-secret-pass",
                  "familyName": "Family One",
                  "members": [
                    { "displayName": "Masha", "role": "parent", "isAdmin": false },
                    { "displayName": "Pasha", "role": "child" }
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.json()
        assertFalse(body.success())
        assertEquals(ErrorCode.INVALID_REQUEST.name, body.errorCode())
    }

    @Test
    fun administratorCanManageFamilyMembersAfterUnlockingWithMasterPassword() = backendTestApp(seedOnStart = false) {
        val bootstrap = bootstrap(
            """
                {
                  "masterPassword": "super-secret-pass",
                  "familyName": "Family One",
                  "members": [
                    { "displayName": "Masha", "role": "parent", "isAdmin": true },
                    { "displayName": "Pasha", "role": "child" }
                  ]
                }
            """.trimIndent(),
        ).json()

        val bootstrapParentInvite = bootstrap.data().req("invites").arr()
            .first { it.jsonObject.req("displayName").text() == "Masha" }
            .jsonObject.req("inviteCode").text()

        val parentAuth = login(bootstrapParentInvite, "web").json()
        val parentToken = parentAuth.token()

        val unlock = authorizedPost(
            path = "/api/admin/verify",
            token = parentToken,
            body = """{"masterPassword":"super-secret-pass"}""",
        )
        assertEquals(HttpStatusCode.OK, unlock.status)
        val unlockBody = unlock.json()
        assertTrue(unlockBody.success())
        val initialMembers = unlockBody.data().req("members").arr()
        assertEquals(2, initialMembers.size)
        assertTrue(initialMembers.any { it.jsonObject.req("displayName").text() == "Masha" })
        assertTrue(initialMembers.any { it.jsonObject.req("displayName").text() == "Pasha" })

        val createChild = authorizedPost(
            path = "/api/admin/members/create",
            token = parentToken,
            body = """{"masterPassword":"super-secret-pass","displayName":"Sasha","role":"child","isAdmin":false}""",
        )
        assertEquals(HttpStatusCode.OK, createChild.status)
        val createChildBody = createChild.json()
        assertTrue(createChildBody.success())
        val child = createChildBody.data().req("member").obj()
        assertEquals("Sasha", child.req("displayName").text())
        assertEquals("child", child.req("role").text())
        assertEquals("false", child.req("isAdmin").text())
        assertEquals("false", child.req("isRegistered").text())
        assertEquals("true", child.req("isActive").text())
        val childInvite = child.req("inviteCode").text()
        assertTrue(childInvite.isNotBlank())

        val createParent = authorizedPost(
            path = "/api/admin/members/create",
            token = parentToken,
            body = """{"masterPassword":"super-secret-pass","displayName":"Olga","role":"parent","isAdmin":true}""",
        )
        assertEquals(HttpStatusCode.OK, createParent.status)
        val createParentBody = createParent.json()
        assertTrue(createParentBody.success())
        val parent = createParentBody.data().req("member").obj()
        assertEquals("Olga", parent.req("displayName").text())
        assertEquals("parent", parent.req("role").text())
        assertEquals("true", parent.req("isAdmin").text())
        val createdParentInvite = parent.req("inviteCode").text()

        val newChildLogin = login(childInvite, "android").json()
        assertEquals("Sasha", newChildLogin.data().req("user").obj().req("displayName").text())
        assertEquals("false", newChildLogin.data().req("user").obj().req("isAdmin").text())

        val newParentLogin = login(createdParentInvite, "desktop").json()
        assertEquals("Olga", newParentLogin.data().req("user").obj().req("displayName").text())
        assertEquals("true", newParentLogin.data().req("user").obj().req("isAdmin").text())

        val afterRegistration = authorizedPost(
            path = "/api/admin/verify",
            token = parentToken,
            body = """{"masterPassword":"super-secret-pass"}""",
        ).json()
        val createdChildRow = afterRegistration.data().req("members").arr()
            .firstOrNull { it.jsonObject.req("inviteCode").text() == childInvite }
        assertNotNull(createdChildRow)
        assertEquals("true", createdChildRow!!.jsonObject.req("isRegistered").text())
        val createdParentRow = afterRegistration.data().req("members").arr()
            .firstOrNull { it.jsonObject.req("inviteCode").text() == createdParentInvite }
        assertNotNull(createdParentRow)
        assertEquals("true", createdParentRow!!.jsonObject.req("isRegistered").text())
        assertEquals("true", createdParentRow.jsonObject.req("isAdmin").text())

        val remove = authorizedPost(
            path = "/api/admin/members/remove",
            token = parentToken,
            body = """{"masterPassword":"super-secret-pass","inviteCode":"$childInvite"}""",
        )
        assertEquals(HttpStatusCode.OK, remove.status)
        val removeBody = remove.json()
        assertTrue(removeBody.success())
        val removedChildRow = removeBody.data().req("members").arr()
            .first { it.jsonObject.req("inviteCode").text() == childInvite }
        assertEquals("false", removedChildRow.jsonObject.req("isActive").text())

        val removedLogin = login(childInvite, "android")
        assertEquals(HttpStatusCode.Unauthorized, removedLogin.status)
        assertEquals(ErrorCode.UNAUTHORIZED.name, removedLogin.json().errorCode())
    }

    @Test
    fun childCannotAccessAdministrationRoutes() = backendTestApp {
        val childAuth = login(
            inviteCode = "CHILD-DEMO",
            platform = "desktop",
        ).json()

        val response = authorizedPost(
            path = "/api/admin/verify",
            token = childAuth.token(),
            body = """{"masterPassword":"super-secret-pass"}""",
        )

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = response.json()
        assertFalse(body.success())
        assertEquals(ErrorCode.FORBIDDEN.name, body.errorCode())
    }

    @Test
    fun sendMessageAndSyncExposeMessageToRecipient() = backendTestApp {
        val parent = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        ).json()
        val child = login(
            inviteCode = "CHILD-DEMO",
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
    fun syncExposesOnlyReceiptsRelevantToCurrentUser() = backendTestApp {
        val parent = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        ).json()
        val child = login(
            inviteCode = "CHILD-DEMO",
            platform = "android",
        ).json()

        val parentToken = parent.token()
        val childToken = child.token()
        val parentUserId = parent.data().req("user").obj().req("id").long()
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
        val messageId = sendResponse.json().data().req("message").obj().req("id").long()

        val childInitialSync = authorizedGet("/api/messages/sync?since_id=0", childToken).json()
        val childMessages = childInitialSync.data().req("messages").arr()
        assertTrue(childMessages.any { it.jsonObject.req("id").long() == messageId })
        val childReceipts = childInitialSync.data().req("receipts").arr()
        assertTrue(
            childReceipts.none {
                it.jsonObject.req("messageId").long() == messageId &&
                    it.jsonObject.req("userId").long() == parentUserId
            },
        )

        val markReadResponse = authorizedPost(
            path = "/api/messages/mark-read",
            token = childToken,
            body = """{"messageIds":[$messageId]}""",
        )
        assertEquals(HttpStatusCode.OK, markReadResponse.status)

        val parentSyncAfterRead = authorizedGet("/api/messages/sync?since_id=0", parentToken).json()
        val parentReceipts = parentSyncAfterRead.data().req("receipts").arr()
        assertTrue(
            parentReceipts.any {
                it.jsonObject.req("messageId").long() == messageId &&
                    it.jsonObject.req("userId").long() == childUserId &&
                    it.jsonObject.req("readAt").toString() != "null"
            },
        )
    }

    @Test
    fun sendMessageIsDeduplicatedByClientMessageUuid() = backendTestApp {
        val parent = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
        ).json()
        val child = login(
            inviteCode = "CHILD-DEMO",
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
    fun clientLogsAreStoredForAuthenticatedUser() = backendTestApp {
        val parent = login(
            inviteCode = "PARENT-DEMO",
            platform = "android",
        ).json()
        val parentToken = parent.token()
        val parentUserId = parent.data().req("user").obj().req("id").long()

        val response = authorizedPost(
            path = "/api/client-logs",
            token = parentToken,
            body = """
                {
                  "entries": [
                    {
                      "eventId": "${UUID.randomUUID()}",
                      "level": "error",
                      "tag": "FamilyMessengerUnread",
                      "message": "badge did not clear",
                      "details": "contactId=2",
                      "occurredAt": "2026-03-22T20:30:00Z"
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.json().success())
        assertEquals(1, queryClientLogCount(parentUserId))
        assertEquals("FamilyMessengerUnread", latestClientLogTag(parentUserId))
    }

    @Test
    fun authEndpointsRespectRateLimit() = backendTestApp(
        authRateLimitMaxRequests = 2,
        authRateLimitWindowSeconds = 60,
    ) {
        login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
            clientKey = "setup-client",
        )

        val first = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
            clientKey = "rate-limit-client",
        )
        val second = login(
            inviteCode = "PARENT-DEMO",
            platform = "desktop",
            clientKey = "rate-limit-client",
        )
        val third = login(
            inviteCode = "PARENT-DEMO",
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

private val backendTestLock = Any()
private var currentTestJdbcUrl: String? = null

private fun backendTestApp(
    authRateLimitMaxRequests: Int = 10,
    authRateLimitWindowSeconds: Int = 60,
    seedOnStart: Boolean = true,
    block: suspend ApplicationTestBuilder.() -> Unit,
) = synchronized(backendTestLock) {
    testApplication {
        val jdbcUrl = testJdbcUrl()
        currentTestJdbcUrl = jdbcUrl
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
                "app.database.seedOnStart" to seedOnStart.toString(),
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
}

private suspend fun ApplicationTestBuilder.bootstrap(body: String): HttpResponse = client.post("/api/setup/bootstrap") {
    contentType(ContentType.Application.Json)
    setBody(body)
}

private suspend fun ApplicationTestBuilder.login(
    inviteCode: String,
    platform: String,
    clientKey: String = "test-client",
): HttpResponse = client.post("/api/auth/login") {
    contentType(ContentType.Application.Json)
    header("X-Forwarded-For", clientKey)
    setBody(
        """
            {
              "inviteCode": "$inviteCode",
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
    "jdbc:h2:file:${Files.createTempDirectory("family-messenger-backend-test-${UUID.randomUUID()}").resolve("db").toAbsolutePath()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"

private fun insertInvite(
    code: String,
    displayName: String,
    role: String,
    familyName: String = "Demo Family",
    jdbcUrl: String = checkNotNull(currentTestJdbcUrl) { "No active test JDBC URL" },
) {
    DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
        val familyId =
            connection.prepareStatement("SELECT id FROM families WHERE name = ?").useAndReturn { statement ->
                statement.setString(1, familyName)
                statement.executeQuery().use { resultSet ->
                    check(resultSet.next()) { "Family not found: $familyName" }
                    resultSet.getLong("id")
                }
            }

        connection.prepareStatement(
            """
            INSERT INTO invites (
                family_id,
                code,
                user_id,
                role,
                display_name,
                is_active,
                max_uses,
                uses_count,
                created_at,
                expires_at
            ) VALUES (?, ?, NULL, ?, ?, TRUE, 1, 0, CURRENT_TIMESTAMP(), NULL)
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, familyId)
            statement.setString(2, code)
            statement.setString(3, role)
            statement.setString(4, displayName)
            statement.executeUpdate()
        }
    }
}

private fun queryClientLogCount(
    userId: Long,
    jdbcUrl: String = checkNotNull(currentTestJdbcUrl) { "No active test JDBC URL" },
): Int =
    DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
        connection.prepareStatement("SELECT COUNT(*) FROM client_logs WHERE user_id = ?").useAndReturn { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { resultSet ->
                check(resultSet.next())
                resultSet.getInt(1)
            }
        }
    }

private fun latestClientLogTag(
    userId: Long,
    jdbcUrl: String = checkNotNull(currentTestJdbcUrl) { "No active test JDBC URL" },
): String =
    DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
        connection.prepareStatement("SELECT tag FROM client_logs WHERE user_id = ? ORDER BY id DESC LIMIT 1").useAndReturn { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { resultSet ->
                check(resultSet.next())
                resultSet.getString("tag")
            }
        }
    }

private fun resetTestDatabase(jdbcUrl: String) {
    DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
        connection.createStatement().use { statement ->
            statement.execute("DROP ALL OBJECTS")
        }
    }
}

private inline fun <T> PreparedStatement.useAndReturn(block: (PreparedStatement) -> T): T =
    use { statement -> block(statement) }
