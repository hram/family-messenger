package app.ui

import app.AppViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.MessageStatus
import com.familymessenger.contract.MessageType

@Composable
internal fun ChatScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TgTopBar(
            title = state.selectedContactName ?: "Chat",
            subtitle = "offline-first · polling sync",
            leadingContent = {
                IconButton(onClick = viewModel::backToContacts) {
                    Icon(AppIcons.Back, contentDescription = "Back", tint = Color.White)
                }
            },
        )
        ChatPanel(state = state, viewModel = viewModel)
    }
}

@Composable
internal fun ChatPanel(state: AppUiState, viewModel: AppViewModel) {
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
            color = CardBg,
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
                        unfocusedBorderColor = InputBorder,
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

private fun senderNameColor(name: String): Color =
    AvatarPalette[name.hashCode().and(0x7FFFFFFF) % AvatarPalette.size]

private fun MessageStatus.prettyLabel(): String = when (this) {
    MessageStatus.LOCAL_PENDING -> "pending"
    MessageStatus.SENT          -> "sent"
    MessageStatus.DELIVERED     -> "delivered"
    MessageStatus.READ          -> "read"
}
