package app.ui

import app.AppViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familymessenger.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TgTopBar(
            title = stringResource(Res.string.screen_settings),
            leadingContent = {
                IconButton(onClick = viewModel::backToContacts) {
                    Icon(AppIcons.Back, contentDescription = stringResource(Res.string.content_desc_back), tint = Color.White)
                }
            },
        )
        SettingsPanel(state, viewModel)
    }
}

@Composable
internal fun SettingsPanel(state: AppUiState, viewModel: AppViewModel) {
    if (!state.settings.unlocked) {
        LockedSettingsPanel(state, viewModel)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SettingsSection {
            TgTextField(
                value = state.onboarding.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = stringResource(Res.string.settings_server_base_url),
                modifier = Modifier.testTag(AppTestTags.SettingsBaseUrl),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::saveBaseUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag(AppTestTags.SettingsSaveBaseUrl),
                colors = ButtonDefaults.buttonColors(containerColor = TgBlue),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(stringResource(Res.string.action_save), fontWeight = FontWeight.Medium)
            }
        }

        SettingsSection {
            TgToggleRow(
                title = stringResource(Res.string.settings_polling_sync),
                value = state.settings.pollingEnabled,
                onToggle = viewModel::updatePollingEnabled,
            )
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TgToggleRow(
                title = stringResource(Res.string.settings_push_notifications),
                value = state.settings.pushEnabled,
                onToggle = viewModel::updatePushEnabled,
            )
        }

        SettingsSection {
            val currentRole = state.currentUser?.role?.localizedLabel() ?: stringResource(Res.string.label_unknown)
            val roleValue = if (state.currentUser?.isAdmin == true) {
                currentRole + stringResource(Res.string.settings_role_admin_suffix)
            } else {
                currentRole
            }
            TgInfoRow(stringResource(Res.string.settings_user), state.currentUser?.displayName ?: stringResource(Res.string.label_unknown))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TgInfoRow(stringResource(Res.string.settings_role), roleValue)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TgInfoRow(stringResource(Res.string.settings_platform), state.platformName)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TgInfoRow(stringResource(Res.string.settings_pending), state.pendingMessageCount.toString())
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TgInfoRow(stringResource(Res.string.settings_cursor), state.syncCursor.toString())
        }

        if (state.currentUser?.isAdmin == true) {
            SettingsSection {
                Button(
                    onClick = viewModel::openAdmin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AppTestTags.SettingsOpenAdmin),
                    colors = ButtonDefaults.buttonColors(containerColor = TgBlueDark),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(Res.string.settings_open_admin), fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = viewModel::logout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp)
                .testTag(AppTestTags.SettingsLogout),
            colors = ButtonDefaults.buttonColors(containerColor = DestructiveRed),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(Res.string.settings_logout), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LockedSettingsPanel(state: AppUiState, viewModel: AppViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(color = CardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.admin_unlock_title), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(Res.string.admin_unlock_description), color = TextSecondary)
                PasswordField(
                    value = state.settings.masterPassword,
                    onValueChange = viewModel::updateSettingsMasterPassword,
                    label = stringResource(Res.string.admin_master_password_label),
                    modifier = Modifier.testTag(AppTestTags.SettingsLockPassword),
                )
                Button(
                    onClick = viewModel::unlockSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag(AppTestTags.SettingsUnlock),
                    colors = ButtonDefaults.buttonColors(containerColor = TgBlue),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(Res.string.admin_unlock_button))
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        color = CardBg,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), content = content)
    }
}

@Composable
private fun TgToggleRow(title: String, value: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, color = TextPrimary)
        Switch(
            checked = value,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = TgBlue),
        )
    }
}

@Composable
private fun TgInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = TextPrimary)
        Text(value, fontSize = 15.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
