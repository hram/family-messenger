@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.MessageStatus
import com.familymessenger.contract.MessageType

// ── Telegram colour palette ──────────────────────────────────────────────────
private val TgBlue        = Color(0xFF2AABEE)
private val TgBlueDark    = Color(0xFF1A8DD1)
private val TgBlueTint    = Color(0xFFE8F4FD)
private val AppBg         = Color(0xFFE9EAEF)
private val BubbleMe      = Color(0xFFDCEFD2)   // green-tinted like Telegram
private val BubbleThem    = Color(0xFFFFFFFF)
private val SidebarBg     = Color(0xFFFFFFFF)
private val OnlineGreen   = Color(0xFF4DB269)
private val TextPrimary   = Color(0xFF000000)
private val TextSecondary = Color(0xFF8A8A8A)
private val Divider       = Color(0xFFE8E8E8)

// ── Theme wrapper ────────────────────────────────────────────────────────────
@Composable
fun FamilyMessengerApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary        = TgBlue,
            onPrimary      = Color.White,
            surface        = Color.White,
            background     = AppBg,
            secondaryContainer = TgBlueTint,
        ),
    ) {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBg)
                    .padding(padding),
            ) {
                when (state.screen) {
                    Screen.ONBOARDING -> OnboardingScreen(state, viewModel)
                    Screen.CONTACTS   -> ContactsScreen(state, viewModel)
                    Screen.CHAT       -> ChatScreen(state, viewModel)
                    Screen.SETTINGS   -> SettingsScreen(state, viewModel)
                }

                TgBanner(state, viewModel)

                if (state.isBusy) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(14.dp).size(24.dp),
                            color = TgBlue,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

// ── Banner ───────────────────────────────────────────────────────────────────
@Composable
private fun TgBanner(state: AppUiState, viewModel: AppViewModel) {
    val banner = state.errorMessage ?: state.statusMessage ?: return
    val isError = state.errorMessage != null
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        color = if (isError) Color(0xFFF9D6D0) else Color(0xFFD8F0D8),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = banner,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                color = TextPrimary,
            )
            TextButton(onClick = viewModel::clearBanner) {
                Text("OK", color = TgBlue, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Onboarding ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TgBlue)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                "Family Messenger",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Mode tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
            ) {
                listOf(AuthMode.REGISTER to "Register", AuthMode.LOGIN to "Login").forEach { (mode, label) ->
                    val selected = state.onboarding.authMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) TgBlue else Color.Transparent)
                            .clickable { viewModel.setAuthMode(mode) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.White else TextSecondary,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            TgTextField(
                value = state.onboarding.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = "Server Base URL",
            )
            TgTextField(
                value = state.onboarding.inviteCode,
                onValueChange = viewModel::updateInviteCode,
                label = "Invite Code",
            )

            Button(
                onClick = viewModel::submitAuth,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TgBlue),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    if (state.onboarding.authMode == AuthMode.REGISTER) "Register Device" else "Login",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Platform: ${state.platformName}", fontSize = 13.sp, color = TextSecondary)
                    Text("Offline cache · pending queue · polling sync", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }
    }
}

// ── Contacts ─────────────────────────────────────────────────────────────────
@Composable
private fun ContactsScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TgTopBar(
            title = "Chats",
            subtitle = state.currentUser?.displayName ?: "",
            trailingContent = {
                IconButton(onClick = viewModel::openSettings) {
                    Text("⚙", color = Color.White, fontSize = 18.sp)
                }
                IconButton(onClick = viewModel::refreshContacts) {
                    Text("↻", color = Color.White, fontSize = 20.sp)
                }
            },
        )

        if (state.contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(AppBg),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("💬", fontSize = 48.sp)
                    Text(
                        "No contacts yet",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                    Text(
                        "Register a second member and refresh",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(SidebarBg),
            ) {
                items(state.contacts) { contact ->
                    ContactRow(contact = contact, onClick = { viewModel.openChat(contact) })
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = Divider,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar with online dot
        Box {
            AvatarCircle(name = contact.user.displayName, size = 50)
            if (contact.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(OnlineGreen)
                        .padding(2.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                contact.user.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (contact.isOnline) "online" else "offline · ${contact.user.role}",
                fontSize = 13.sp,
                color = if (contact.isOnline) TgBlue else TextSecondary,
            )
        }
    }
}

// ── Chat ──────────────────────────────────────────────────────────────────────
@Composable
private fun ChatScreen(state: AppUiState, viewModel: AppViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TgTopBar(
            title = state.selectedContactName ?: "Chat",
            subtitle = "offline-first · polling sync",
            leadingContent = {
                IconButton(onClick = viewModel::backToContacts) {
                    Text("‹", color = Color.White, fontSize = 26.sp, lineHeight = 26.sp)
                }
            },
        )

        // Quick action chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.availableQuickActions().forEach { code ->
                TgChip(label = code.name, onClick = { viewModel.sendQuickAction(code) })
            }
            TgChip(label = "📍 Location", onClick = viewModel::shareLocation)
        }

        // Messages
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(AppBg),
        ) {
            if (state.messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No messages yet", color = TextSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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

        // Input bar
        Surface(
            color = Color.White,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = {}) {
                    Text("📎", fontSize = 20.sp)
                }
                OutlinedTextField(
                    value = state.draftMessage,
                    onValueChange = viewModel::updateDraftMessage,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message", color = TextSecondary, fontSize = 14.sp) },
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFFE0E0E0),
                        focusedBorderColor = TgBlue,
                    ),
                    singleLine = false,
                    maxLines = 4,
                )
                // Send button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(TgBlue)
                        .clickable(onClick = viewModel::sendCurrentDraft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("➤", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessagePayload, mine: Boolean) {
    val alignment = if (mine) Alignment.End else Alignment.Start
    val bubbleBg  = if (mine) BubbleMe else BubbleThem
    val tailShape = if (mine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Surface(
            color = bubbleBg,
            shape = tailShape,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(min = 80.dp, max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                when (message.type) {
                    MessageType.TEXT -> Text(
                        message.body.orEmpty(),
                        fontSize = 15.sp,
                        color = TextPrimary,
                        lineHeight = 20.sp,
                    )
                    MessageType.QUICK_ACTION -> Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(TgBlue),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⚡", fontSize = 12.sp)
                        }
                        Text(
                            message.quickActionCode?.name ?: "",
                            fontSize = 14.sp,
                            color = TextPrimary,
                        )
                    }
                    MessageType.LOCATION -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TgBlueTint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("📍", fontSize = 28.sp)
                        }
                        Text(
                            message.location?.label ?: "My location",
                            fontSize = 13.sp,
                            color = TextPrimary,
                        )
                    }
                }

                // Time + status
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        message.status.prettyLabel(),
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                    if (mine) {
                        Text(
                            if (message.status == MessageStatus.READ) "✓✓" else "✓",
                            fontSize = 11.sp,
                            color = if (message.status == MessageStatus.READ) TgBlue else TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

// ── Settings ──────────────────────────────────────────────────────────────────
@Composable
private fun SettingsScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TgTopBar(
            title = "Settings",
            leadingContent = {
                IconButton(onClick = viewModel::backToContacts) {
                    Text("‹", color = Color.White, fontSize = 26.sp, lineHeight = 26.sp)
                }
            },
        )

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
                    label = "Server Base URL",
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::saveBaseUrl,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TgBlue),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Save", fontWeight = FontWeight.Medium)
                }
            }

            SettingsSection {
                TgToggleRow(
                    title = "Polling sync",
                    value = state.settings.pollingEnabled,
                    onToggle = viewModel::updatePollingEnabled,
                )
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                TgToggleRow(
                    title = "Push notifications",
                    value = state.settings.pushEnabled,
                    onToggle = viewModel::updatePushEnabled,
                )
            }

            SettingsSection {
                TgInfoRow("User",     state.currentUser?.displayName ?: "—")
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                TgInfoRow("Platform", state.platformName)
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                TgInfoRow("Pending",  state.pendingMessageCount.toString())
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                TgInfoRow("Cursor",   state.syncCursor.toString())
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::logout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Log out", fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Shared small components ───────────────────────────────────────────────────

@Composable
private fun TgTopBar(
    title: String,
    subtitle: String = "",
    leadingContent: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TgBlue)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent()
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
        trailingContent()
    }
}

@Composable
private fun AvatarCircle(name: String, size: Int) {
    val initials = name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    val colors = listOf(
        Color(0xFF2AABEE), Color(0xFF4DB269), Color(0xFFE8A840),
        Color(0xFF8B5CF6), Color(0xFFE05454), Color(0xFF1A8DD1),
    )
    val bg = colors[name.hashCode().and(0x7FFFFFFF) % colors.size]

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = Color.White,
            fontSize = (size * 0.36f).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TgChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = TgBlueTint,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 13.sp,
            color = TgBlueDark,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TgTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color(0xFFDDDDDD),
            focusedBorderColor = TgBlue,
            focusedLabelColor = TgBlue,
        ),
    )
}

@Composable
private fun SettingsSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 1.dp),
        color = Color.White,
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

private fun MessageStatus.prettyLabel(): String = when (this) {
    MessageStatus.LOCAL_PENDING -> "pending"
    MessageStatus.SENT          -> "sent"
    MessageStatus.DELIVERED     -> "delivered"
    MessageStatus.READ          -> "read"
}
