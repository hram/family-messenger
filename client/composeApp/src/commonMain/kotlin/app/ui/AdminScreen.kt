package app.ui

import app.AppViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familymessenger.contract.UserRole

@Composable
internal fun AdminScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TgTopBar(
            title = "Administration",
            subtitle = state.currentUser?.displayName ?: "",
            leadingContent = {
                IconButton(onClick = viewModel::openSettings) {
                    Icon(AppIcons.Back, contentDescription = "Back", tint = Color.White)
                }
            },
        )
        AdminPanel(state, viewModel)
    }
}

@Composable
internal fun AdminPanel(state: AppUiState, viewModel: AppViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!state.admin.unlocked) {
            Surface(color = CardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Master password required", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("Administration is locked until the master password is confirmed.", color = TextSecondary)
                    PasswordField(
                        value = state.admin.masterPassword,
                        onValueChange = viewModel::updateAdminMasterPassword,
                        label = "Master Password",
                        modifier = Modifier.testTag(AppTestTags.AdminPassword),
                    )
                    Button(
                        onClick = viewModel::unlockAdmin,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag(AppTestTags.AdminUnlock),
                        colors = ButtonDefaults.buttonColors(containerColor = TgBlue),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Unlock")
                    }
                }
            }
            return
        }

        Surface(color = CardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Family Members", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (state.admin.members.isEmpty()) {
                    Text("No family members yet", color = TextSecondary)
                } else {
                    state.admin.members.forEach { member ->
                        var showQr by remember { mutableStateOf(false) }
                        if (showQr) {
                            QrCodeDialog(
                                inviteCode = member.inviteCode,
                                serverUrl = state.onboarding.baseUrl,
                                displayName = member.displayName,
                                onDismiss = { showQr = false },
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(member.displayName, fontWeight = FontWeight.Medium)
                                    Text(
                                        buildString {
                                            append(member.role.name.lowercase())
                                            if (member.isAdmin) append(" · administrator")
                                            append(" · ")
                                            append(if (member.isRegistered) "registered" else "invite pending")
                                            append(" · ")
                                            append(if (member.isActive) "active" else "removed")
                                            append(" · ")
                                            append(member.inviteCode)
                                        },
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    InviteActionButton(
                                        icon = AppIcons.QrCode,
                                        label = "QR-код",
                                        onClick = { showQr = true },
                                    )
                                    TextButton(
                                        onClick = { viewModel.removeAdminMember(member.inviteCode) },
                                        modifier = Modifier.testTag(AppTestTags.AdminMemberRemovePrefix + member.inviteCode),
                                    ) {
                                        Text("Remove", color = DestructiveRed)
                                    }
                                }
                            }
                            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        Surface(color = CardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add family member", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                TgTextField(
                    value = state.admin.newMemberName,
                    onValueChange = viewModel::updateAdminNewMemberName,
                    label = "Display Name",
                    modifier = Modifier.testTag(AppTestTags.AdminMemberName),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(UserRole.PARENT to "Parent", UserRole.CHILD to "Child").forEach { (role, label) ->
                        val selected = state.admin.newMemberRole == role
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) TgBlue else TgBlueTint)
                                .clickable { viewModel.updateAdminNewMemberRole(role) }
                                .padding(vertical = 12.dp)
                                .testTag(AppTestTags.AdminMemberRolePrefix + role.name.lowercase()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (selected) Color.White else TgBlueDark, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (state.admin.newMemberRole == UserRole.PARENT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Administrator", fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text("Can open admin section and manage family members", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = state.admin.newMemberIsAdmin,
                            onCheckedChange = viewModel::updateAdminNewMemberIsAdmin,
                            modifier = Modifier.testTag(AppTestTags.AdminMemberAdmin),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = TgBlue,
                            ),
                        )
                    }
                }
                Button(
                    onClick = viewModel::createAdminMember,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag(AppTestTags.AdminMemberCreate),
                    colors = ButtonDefaults.buttonColors(containerColor = TgBlue),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Create Invite")
                }
            }
        }
    }
}
