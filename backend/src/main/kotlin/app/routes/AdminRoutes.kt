package app.routes

import app.plugins.sessionPrincipal
import app.service.AdminService
import app.support.respondOk
import com.familymessenger.contract.AdminCreateMemberRequest
import com.familymessenger.contract.AdminCreateMemberResponse
import com.familymessenger.contract.AdminMembersResponse
import com.familymessenger.contract.AdminRemoveMemberRequest
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.VerifyAdminAccessRequest
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route

fun Route.adminRoutes(adminService: AdminService) {
    post("/admin/verify", {
        description = "Verify administrator access with master password and return current child management list."
        request {
            body<VerifyAdminAccessRequest>()
        }
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<AdminMembersResponse>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        call.respondOk(adminService.verifyAccess(principal, call.receive()))
    }

    post("/admin/members/create", {
        description = "Create a new family member invite. Requires administrator session and master password."
        request {
            body<AdminCreateMemberRequest>()
        }
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<AdminCreateMemberResponse>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        call.respondOk(adminService.createMember(principal, call.receive()))
    }

    post("/admin/members/remove", {
        description = "Remove a family member invite/user. Requires administrator session and master password."
        request {
            body<AdminRemoveMemberRequest>()
        }
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<AdminMembersResponse>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        call.respondOk(adminService.removeMember(principal, call.receive()))
    }
}
