package app.ui

import app.AppViewModel
import app.platformBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.MessageStatus
import com.familymessenger.contract.MessageType
import com.familymessenger.contract.UserRole

// ── Telegram colour palette ───────────────────────────────────────────────────
private val TgBlue        = Color(0xFF2AABEE)
private val TgBlueDark    = Color(0xFF1A8DD1)
private val TgBlueTint    = Color(0xFFE8F4FD)
private val AppBg         = Color(0xFFE9EAEF)
private val BubbleMe      = Color(0xFFDCEFD2)
private val BubbleThem    = Color(0xFFFFFFFF)
private val SidebarBg     = Color(0xFFFFFFFF)
private val OnlineGreen   = Color(0xFF4DB269)
private val TextPrimary   = Color(0xFF000000)
private val TextSecondary = Color(0xFF8A8A8A)
private val Divider       = Color(0xFFE8E8E8)

// ── Theme wrapper ─────────────────────────────────────────────────────────────
@Composable
fun FamilyMessengerApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary            = TgBlue,
            onPrimary          = Color.White,
            surface            = Color.White,
            background         = AppBg,
            secondaryContainer = TgBlueTint,
        ),
    ) {
        Scaffold { padding ->
            // BoxWithConstraints works on all targets (Android, iOS, Desktop, WASM)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBg)
                    .padding(padding),
            ) {
                val isWide = maxWidth > 600.dp

                platformBackHandler(
                    enabled = !isWide && state.screen in setOf(Screen.CHAT, Screen.SETTINGS, Screen.ADMIN),
                    onBack = viewModel::backToContacts,
                )

                when {
                    state.screen == Screen.ONBOARDING -> LoginScreen(state, viewModel)
                    state.screen == Screen.SETUP -> SetupScreen(state, viewModel)
                    isWide -> WideLayout(state, viewModel)
                    else -> when (state.screen) {
                        Screen.CONTACTS  -> ContactsScreen(state, viewModel)
                        Screen.CHAT      -> ChatScreen(state, viewModel)
                        Screen.SETTINGS  -> SettingsScreen(state, viewModel)
                        Screen.ADMIN     -> AdminScreen(state, viewModel)
                        Screen.ONBOARDING, Screen.SETUP -> {}
                    }
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

// ── Banner ────────────────────────────────────────────────────────────────────
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

// ── Wide layout (desktop / web, > 600dp) ─────────────────────────────────────
@Composable
private fun WideLayout(state: AppUiState, viewModel: AppViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {

        // Left column — contacts list (fixed width)
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(Color.White),
        ) {
            TgTopBar(
                title = "Chats",
                subtitle = state.currentUser?.displayName ?: "",
                trailingContent = {
                    IconButton(
                        onClick = viewModel::openSettings,
                        modifier = Modifier.testTag(AppTestTags.TopBarSettings),
                    ) {
                        Icon(AppIcons.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                    IconButton(
                        onClick = viewModel::refreshContacts,
                        modifier = Modifier.testTag(AppTestTags.TopBarRefresh),
                    ) {
                        Icon(AppIcons.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
            )
            ContactsPanel(
                contacts = state.contacts,
                unreadCounts = state.unreadCounts,
                selectedContactId = state.selectedContactId,
                onContactClick = viewModel::openChat,
            )
        }

        // VerticalDivider: CMP 1.7.1 bundles Material3 ~1.3.x — available.
        // If it fails to compile, replace with:
        //   Box(Modifier.width(0.5.dp).fillMaxHeight().background(Divider))
        VerticalDivider(color = Divider, thickness = 0.5.dp)

        // Right column — chat or empty placeholder, with settings overlay on top
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val selectedContact = state.contacts.find { it.user.id == state.selectedContactId }
            if (selectedContact != null) {
                val contactStatus = selectedContact.subtitleText()
                Column(modifier = Modifier.fillMaxSize()) {
                    TgTopBar(
                        title = state.selectedContactName ?: "Chat",
                        subtitle = contactStatus,
                    )
                    ChatPanel(state = state, viewModel = viewModel)
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().background(AppBg),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(AppIcons.Chat, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                        Text("Select a contact", color = TextSecondary, fontSize = 15.sp)
                    }
                }
            }

            // Settings overlay: dimmed backdrop + panel from the right edge
            if (state.screen == Screen.SETTINGS || state.screen == Screen.ADMIN) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(onClick = viewModel::backToContacts),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(360.dp)
                        .fillMaxHeight(),
                    color = AppBg,
                ) {
                    if (state.screen == Screen.ADMIN) {
                        AdminPanel(state, viewModel)
                    } else {
                        SettingsPanel(state, viewModel)
                    }
                }
            }
        }
    }
}

// ── Onboarding ────────────────────────────────────────────────────────────────
// ── Contacts screen (mobile wrapper) ─────────────────────────────────────────
@Composable
private fun ContactsScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TgTopBar(
            title = "Chats",
            subtitle = state.currentUser?.displayName ?: "",
            trailingContent = {
                IconButton(
                    onClick = viewModel::openSettings,
                    modifier = Modifier.testTag(AppTestTags.TopBarSettings),
                ) {
                    Icon(AppIcons.Settings, contentDescription = "Settings", tint = Color.White)
                }
                IconButton(
                    onClick = viewModel::refreshContacts,
                    modifier = Modifier.testTag(AppTestTags.TopBarRefresh),
                ) {
                    Icon(AppIcons.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
            },
        )
        ContactsPanel(
            contacts = state.contacts,
            unreadCounts = state.unreadCounts,
            onContactClick = viewModel::openChat,
        )
    }
}

// ── Contacts panel (reusable — used by ContactsScreen and WideLayout) ─────────
@Composable
private fun ContactsPanel(
    contacts: List<ContactSummary>,
    unreadCounts: Map<Long, Int> = emptyMap(),
    selectedContactId: Long? = null,  // highlights active row in wide layout (AppState.kt:44)
    onContactClick: (ContactSummary) -> Unit,
) {
    if (contacts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(AppBg),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(AppIcons.Chat, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
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
        LazyColumn(modifier = Modifier.fillMaxSize().background(SidebarBg)) {
            items(items = contacts, key = { it.user.id }) { contact ->
                ContactRow(
                    contact = contact,
                    unreadCount = unreadCounts[contact.user.id] ?: 0,
                    isSelected = contact.user.id == selectedContactId,
                    onClick = { onContactClick(contact) },
                )
                HorizontalDivider(color = Divider, thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: ContactSummary,
    unreadCount: Int = 0,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(contactRowTag(contact.user.id))
            .background(if (isSelected) TgBlueTint else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            AvatarCircle(name = contact.user.displayName, size = 50)
            if (contact.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(OnlineGreen),
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
                contact.subtitleText(),
                fontSize = 13.sp,
                color = if (contact.isOnline || contact.isFamilyGroup()) TgBlue else TextSecondary,
            )
        }

        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TgBlue)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    unreadCount.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── Chat screen (mobile wrapper) ──────────────────────────────────────────────
@Composable
private fun ChatScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TgTopBar(
            title = state.selectedContactName ?: "Chat",
            subtitle = "offline-first · polling sync",
            leadingContent = {
                // Back button shown only in mobile navigation
                IconButton(onClick = viewModel::backToContacts) {
                    Icon(AppIcons.Back, contentDescription = "Back", tint = Color.White)
                }
            },
        )
        ChatPanel(state = state, viewModel = viewModel)
    }
}

// ── Chat panel (reusable — used by ChatScreen and WideLayout) ─────────────────
@Composable
private fun ChatPanel(state: AppUiState, viewModel: AppViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Messages area
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
                        val isFamilyChat = state.selectedContactId == FAMILY_GROUP_CHAT_ID
                        val senderName = if (isFamilyChat) {
                            when (message.senderUserId) {
                                state.currentUser?.id -> state.currentUser?.displayName
                                else -> state.contacts.find { it.user.id == message.senderUserId }?.user?.displayName
                            }
                        } else {
                            null
                        }
                        MessageBubble(
                            message = message,
                            mine = state.currentUser?.id == message.senderUserId,
                            senderName = senderName,
                            showSenderName = isFamilyChat,
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
                    Icon(AppIcons.Attach, contentDescription = "Attach", tint = TextSecondary)
                }
                OutlinedTextField(
                    value = state.draftMessage,
                    onValueChange = viewModel::updateDraftMessage,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(AppTestTags.ChatInput),
                    placeholder = { Text("Message", color = TextSecondary, fontSize = 14.sp) },
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFFE0E0E0),
                        focusedBorderColor   = TgBlue,
                    ),
                    singleLine = false,
                    maxLines = 4,
                )
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .testTag(AppTestTags.ChatSend)
                        .clip(CircleShape)
                        .background(TgBlue)
                        .clickable(onClick = viewModel::sendCurrentDraft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessagePayload,
    mine: Boolean,
    senderName: String? = null,
    showSenderName: Boolean = false,
) {
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
                if (showSenderName && !mine && !senderName.isNullOrBlank()) {
                    Text(
                        senderName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = senderNameColor(senderName),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                when (message.type) {
                    MessageType.TEXT -> Text(
                        message.body.orEmpty(),
                        fontSize = 18.sp,
                        color = TextPrimary,
                        lineHeight = 24.sp,
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
                            Icon(AppIcons.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                        Text(
                            message.quickActionCode?.name ?: "",
                            fontSize = 17.sp,
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
                            Icon(AppIcons.Location, contentDescription = null, tint = TgBlueDark, modifier = Modifier.size(28.dp))
                        }
                        Text(
                            message.location?.label ?: "My location",
                            fontSize = 16.sp,
                            color = TextPrimary,
                        )
                    }
                }

                // Time + delivery status
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message.status.prettyLabel(), fontSize = 12.sp, color = TextSecondary)
                    if (mine) {
                        Icon(
                            imageVector = if (message.status == MessageStatus.READ) AppIcons.DoubleCheck else AppIcons.Check,
                            contentDescription = null,
                            tint = if (message.status == MessageStatus.READ) TgBlue else TextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Settings screen (mobile wrapper) ─────────────────────────────────────────
@Composable
private fun SettingsScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TgTopBar(
            title = "Settings",
            leadingContent = {
                IconButton(onClick = viewModel::backToContacts) {
                    Icon(AppIcons.Back, contentDescription = "Back", tint = Color.White)
                }
            },
        )
        SettingsPanel(state, viewModel)
    }
}

// ── Settings panel (reusable — used by SettingsScreen and WideLayout overlay) ─
@Composable
private fun SettingsPanel(state: AppUiState, viewModel: AppViewModel) {
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
            TgInfoRow("Role",     buildString {
                append(state.currentUser?.role?.name?.lowercase() ?: "—")
                if (state.currentUser?.isAdmin == true) append(" · admin")
            })
            HorizontalDivider(color = Divider, thickness = 0.5.dp)
            TgInfoRow("Platform", state.platformName)
            HorizontalDivider(color = Divider, thickness = 0.5.dp)
            TgInfoRow("Pending",  state.pendingMessageCount.toString())
            HorizontalDivider(color = Divider, thickness = 0.5.dp)
            TgInfoRow("Cursor",   state.syncCursor.toString())
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
                    Text("Open Administration", fontWeight = FontWeight.Medium)
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Log out", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AdminScreen(state: AppUiState, viewModel: AppViewModel) {
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
private fun AdminPanel(state: AppUiState, viewModel: AppViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!state.admin.unlocked) {
            Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
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

        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
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
                                        Text("Remove", color = Color(0xFFFF3B30))
                                    }
                                }
                            }
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
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

    val palette = listOf(
        Color(0xFF2AABEE), Color(0xFF4DB269), Color(0xFFE8A840),
        Color(0xFF8B5CF6), Color(0xFFE05454), Color(0xFF1A8DD1),
    )
    val bg = palette[name.hashCode().and(0x7FFFFFFF) % palette.size]

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
private fun TgChip(label: String, onClick: () -> Unit, icon: ImageVector? = null) {
    Surface(
        onClick = onClick,
        color = TgBlueTint,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = TgBlueDark, modifier = Modifier.size(14.dp))
            }
            Text(
                label,
                fontSize = 13.sp,
                color = TgBlueDark,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color(0xFFDDDDDD),
            focusedBorderColor   = TgBlue,
            focusedLabelColor    = TgBlue,
        ),
    )
}

@Composable
private fun SettingsSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        color = Color.White,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), content = content)
    }
}

private fun senderNameColor(name: String): Color {
    val palette = listOf(
        Color(0xFF2AABEE),
        Color(0xFF4DB269),
        Color(0xFFE8A840),
        Color(0xFF8B5CF6),
        Color(0xFFE05454),
        Color(0xFF1A8DD1),
    )
    return palette[name.hashCode().and(0x7FFFFFFF) % palette.size]
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

private fun ContactSummary.isFamilyGroup(): Boolean =
    user.id == FAMILY_GROUP_CHAT_ID || user.role == UserRole.FAMILY

private fun ContactSummary.subtitleText(): String =
    when {
        isFamilyGroup() -> "group chat"
        isOnline -> "online"
        else -> "offline · ${user.role.name.lowercase()}"
    }
