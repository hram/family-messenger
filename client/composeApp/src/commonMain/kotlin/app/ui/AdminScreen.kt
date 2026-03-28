package app.ui

import app.AppViewModel
import app.copyTextToClipboard
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
import com.familymessenger.composeapp.generated.resources.*
import com.familymessenger.contract.UserRole
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdminScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TgTopBar(
            title = stringResource(Res.string.screen_admin),
            subtitle = state.currentUser?.displayName ?: "",
            leadingContent = {
                IconButton(onClick = viewModel::openSettings) {
                    Icon(AppIcons.Back, contentDescription = stringResource(Res.string.content_desc_back), tint = Color.White)
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
                    Text(stringResource(Res.string.admin_unlock_title), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(Res.string.admin_unlock_description), color = TextSecondary)
                    PasswordField(
                        value = state.admin.masterPassword,
                        onValueChange = viewModel::updateAdminMasterPassword,
                        label = stringResource(Res.string.admin_master_password_label),
                        modifier = Modifier.testTag(AppTestTags.AdminPassword),
                    )
                    Button(
                        onClick = viewModel::unlockAdmin,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag(AppTestTags.AdminUnlock),
                        colors = ButtonDefaults.buttonColors(containerColor = TgBlue),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(Res.string.admin_unlock_button))
                    }
                }
            }
            return
        }

        Surface(color = CardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.admin_members_title), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (state.admin.members.isEmpty()) {
                    Text(stringResource(Res.string.admin_members_empty), color = TextSecondary)
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
                                val memberRole = member.role.localizedLabel().lowercase()
                                val memberAdminSuffix = if (member.isAdmin) stringResource(Res.string.settings_role_admin_suffix) else ""
                                val memberRegisteredStatus = if (member.isRegistered) {
                                    stringResource(Res.string.admin_member_registered)
                                } else {
                                    stringResource(Res.string.admin_member_invite_pending)
                                }
                                val memberActiveStatus = if (member.isActive) {
                                    stringResource(Res.string.admin_member_active)
                                } else {
                                    stringResource(Res.string.admin_member_removed)
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(member.displayName, fontWeight = FontWeight.Medium)
                                    Text(
                                        buildString {
                                            append(memberRole)
                                            append(memberAdminSuffix)
                                            append(" · ")
                                            append(memberRegisteredStatus)
                                            append(" · ")
                                            append(memberActiveStatus)
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
                                        icon = AppIcons.Copy,
                                        label = stringResource(Res.string.action_copy),
                                        onClick = { copyTextToClipboard(member.inviteCode) },
                                    )
                                    InviteActionButton(
                                        icon = AppIcons.QrCode,
                                        label = stringResource(Res.string.action_qr_code),
                                        onClick = { showQr = true },
                                    )
                                    TextButton(
                                        onClick = { viewModel.removeAdminMember(member.inviteCode) },
                                        modifier = Modifier.testTag(AppTestTags.AdminMemberRemovePrefix + member.inviteCode),
                                    ) {
                                        Text(stringResource(Res.string.action_remove), color = DestructiveRed)
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
                Text(stringResource(Res.string.admin_add_member_title), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                TgTextField(
                    value = state.admin.newMemberName,
                    onValueChange = viewModel::updateAdminNewMemberName,
                    label = stringResource(Res.string.admin_member_display_name_label),
                    modifier = Modifier.testTag(AppTestTags.AdminMemberName),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(UserRole.PARENT, UserRole.CHILD).forEach { role ->
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
                            Text(role.localizedLabel(), color = if (selected) Color.White else TgBlueDark, fontWeight = FontWeight.Medium)
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
                            Text(stringResource(Res.string.admin_member_admin_toggle_label), fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(stringResource(Res.string.admin_member_admin_toggle_desc), fontSize = 12.sp, color = TextSecondary)
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
                    Text(stringResource(Res.string.admin_create_invite))
                }
            }
        }
    }
}
