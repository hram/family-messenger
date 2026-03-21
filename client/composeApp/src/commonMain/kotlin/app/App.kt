@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.MessageStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMessengerApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()

    MaterialTheme {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFF7EFE2), Color(0xFFD7E9F5)),
                        ),
                    )
                    .padding(padding),
            ) {
                when (state.screen) {
                    Screen.ONBOARDING -> OnboardingScreen(state, viewModel)
                    Screen.CONTACTS -> ContactsScreen(state, viewModel)
                    Screen.CHAT -> ChatScreen(state, viewModel)
                    Screen.SETTINGS -> SettingsScreen(state, viewModel)
                }

                BannerArea(state, viewModel)

                if (state.isBusy) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(18.dp).size(28.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerArea(state: AppUiState, viewModel: AppViewModel) {
    val banner = state.errorMessage ?: state.statusMessage ?: return
    Surface(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        color = if (state.errorMessage != null) Color(0xFFF9D6D0) else Color(0xFFD8F0D8),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(banner, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::clearBanner) {
                Text("OK")
            }
        }
    }
}

@Composable
private fun OnboardingScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Family Messenger",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Регистрация и логин работают через invite code. Клиент использует shared sync engine, локальный кэш и polling.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = state.onboarding.authMode == AuthMode.REGISTER,
                onClick = { viewModel.setAuthMode(AuthMode.REGISTER) },
                label = { Text("Register") },
            )
            FilterChip(
                selected = state.onboarding.authMode == AuthMode.LOGIN,
                onClick = { viewModel.setAuthMode(AuthMode.LOGIN) },
                label = { Text("Login") },
            )
        }

        OutlinedTextField(
            value = state.onboarding.baseUrl,
            onValueChange = viewModel::updateBaseUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server Base URL") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.onboarding.inviteCode,
            onValueChange = viewModel::updateInviteCode,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Invite Code") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.onboarding.deviceName,
            onValueChange = viewModel::updateDeviceName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Device Name") },
            singleLine = true,
        )

        Button(
            onClick = viewModel::submitAuth,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (state.onboarding.authMode == AuthMode.REGISTER) "Register Device" else "Login")
        }

        Surface(
            color = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Платформа: ${state.platformName}")
                Text("Локальный оффлайн режим: кэш контактов, кэш сообщений, очередь pending messages.")
                Text("Поддержка quick actions, mark delivered/read и location share abstraction включена в shared код.")
            }
        }
    }
}

@Composable
private fun ContactsScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Контакты",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = buildString {
                append(state.currentUser?.displayName ?: "Unknown")
                append(" · syncCursor=")
                append(state.syncCursor)
                append(" · pending=")
                append(state.pendingMessageCount)
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = viewModel::refreshContacts) {
                Text("Refresh")
            }
            Button(onClick = viewModel::openSettings) {
                Text("Settings")
            }
        }

        if (state.contacts.isEmpty()) {
            EmptyCard("Контактов пока нет. Если invite использован только один раз, зарегистрируй второго участника и обнови список.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.contacts) { contact ->
                    ContactCard(contact = contact, onClick = { viewModel.openChat(contact) })
                }
            }
        }
    }
}

@Composable
private fun ContactCard(contact: ContactSummary, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.9f),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(contact.user.displayName, fontWeight = FontWeight.Bold)
                Text("role=${contact.user.role} · online=${contact.isOnline}")
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (contact.isOnline) Color(0xFF32A852) else Color(0xFFB0B0B0),
                        shape = RoundedCornerShape(999.dp),
                    ),
            )
        }
    }
}

@Composable
private fun ChatScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(state.selectedContactName ?: "Чат", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("offline-first queue + incremental sync")
            }
            TextButton(onClick = viewModel::backToContacts) {
                Text("Назад")
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.availableQuickActions().forEach { code ->
                AssistChip(
                    onClick = { viewModel.sendQuickAction(code) },
                    label = { Text(code.name) },
                )
            }
            AssistChip(
                onClick = viewModel::shareLocation,
                label = { Text("Share location") },
            )
            AssistChip(
                onClick = viewModel::markConversationRead,
                label = { Text("Mark read") },
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.9f),
        ) {
            if (state.messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("История пока пустая")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages) { message ->
                        MessageBubble(
                            message = message,
                            mine = state.currentUser?.id == message.senderUserId,
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.draftMessage,
            onValueChange = viewModel::updateDraftMessage,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Message") },
        )

        Button(
            onClick = viewModel::sendCurrentDraft,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Send text")
        }
    }
}

@Composable
private fun MessageBubble(message: MessagePayload, mine: Boolean) {
    val bg = if (mine) Color(0xFFE0F0D9) else Color(0xFFF1E5D1)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = bg,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                when (message.type) {
                    com.familymessenger.contract.MessageType.TEXT -> message.body.orEmpty()
                    com.familymessenger.contract.MessageType.QUICK_ACTION -> "Quick action: ${message.quickActionCode}"
                    com.familymessenger.contract.MessageType.LOCATION -> "Location: ${message.location?.label ?: "${message.location?.latitude}, ${message.location?.longitude}"}"
                },
            )
            Text(
                text = "status=${message.status.prettyLabel()} · uuid=${message.clientMessageUuid.take(8)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = state.onboarding.baseUrl,
            onValueChange = viewModel::updateBaseUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server Base URL") },
        )
        Button(onClick = viewModel::saveBaseUrl, modifier = Modifier.fillMaxWidth()) {
            Text("Save base URL")
        }

        SettingToggle(
            title = "Polling sync",
            value = state.settings.pollingEnabled,
            onToggle = viewModel::updatePollingEnabled,
        )
        SettingToggle(
            title = "Optional push placeholder",
            value = state.settings.pushEnabled,
            onToggle = viewModel::updatePushEnabled,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.9f),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Current user: ${state.currentUser?.displayName ?: "-"}")
                Text("Platform: ${state.platformName}")
                Text("Pending queue: ${state.pendingMessageCount}")
                Text("Sync cursor: ${state.syncCursor}")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = viewModel::backToContacts, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
            Button(onClick = viewModel::logout, modifier = Modifier.weight(1f)) {
                Text("Logout")
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    value: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.9f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f))
            Switch(checked = value, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.85f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(18.dp),
            textAlign = TextAlign.Start,
        )
    }
}

private fun MessageStatus.prettyLabel(): String = when (this) {
    MessageStatus.LOCAL_PENDING -> "pending"
    MessageStatus.SENT -> "sent"
    MessageStatus.DELIVERED -> "delivered"
    MessageStatus.READ -> "read"
}
