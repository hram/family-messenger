package app.ui

import app.AppViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.familymessenger.composeapp.generated.resources.*
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.MessageStatus
import com.familymessenger.contract.MessageType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ── Sealed list item — сообщение или разделитель даты ────────────────────────
private sealed interface ChatItem {
    data class Message(val payload: MessagePayload) : ChatItem
    data class DateDivider(val label: String)       : ChatItem
}

// ── Экран ─────────────────────────────────────────────────────────────────────
@Composable
internal fun ChatScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TgTopBar(
            title = state.selectedContactName ?: stringResource(Res.string.screen_chat),
            subtitle = stringResource(Res.string.chat_subtitle),
            leadingContent = {
                IconButton(onClick = viewModel::backToContacts) {
                    Icon(
                        AppIcons.Back,
                        contentDescription = stringResource(Res.string.content_desc_back),
                        tint = Color.White,
                    )
                }
            },
        )
        ChatPanel(state = state, viewModel = viewModel)
    }
}

// ── Панель чата ───────────────────────────────────────────────────────────────
@Composable
internal fun ChatPanel(state: AppUiState, viewModel: AppViewModel) {
    val listState = rememberLazyListState()
    var draftField by rememberSaveable(
        state.selectedContactId, state.draftMessage, state.screen,
        stateSaver = TextFieldValue.Saver,
    ) {
        mutableStateOf(TextFieldValue(state.draftMessage, TextRange(state.draftMessage.length)))
    }

    val todayLabel     = stringResource(Res.string.chat_date_today)
    val yesterdayLabel = stringResource(Res.string.chat_date_yesterday)

    // Строим плоский список: вставляем DateDivider перед первым сообщением каждого дня
    val chatItems = remember(state.messages, todayLabel, yesterdayLabel) {
        buildChatItems(state.messages, todayLabel, yesterdayLabel)
    }

    // Автоскролл к последнему сообщению
    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty()) listState.animateScrollToItem(chatItems.size - 1)
    }

    // Синхронизация черновика
    LaunchedEffect(state.draftMessage, state.selectedContactId, state.screen) {
        if (draftField.text != state.draftMessage) {
            draftField = TextFieldValue(state.draftMessage, TextRange(state.draftMessage.length))
        }
    }

    // Sticky date pill — текст для текущей видимой даты
    val stickyDate by remember(chatItems) {
        derivedStateOf {
            val firstIndex = listState.firstVisibleItemIndex
            // Ищем ближайший DateDivider выше или на текущей позиции
            (firstIndex downTo 0)
                .map { chatItems.getOrNull(it) }
                .filterIsInstance<ChatItem.DateDivider>()
                .firstOrNull()
                ?.label
        }
    }

    // Pill видна только во время скролла — прячется через 1.2с после остановки
    var pillVisible by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                pillVisible = true
            } else {
                delay(1200)
                pillVisible = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Область сообщений + sticky pill поверх неё
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(AppBg),
        ) {
            if (chatItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.chat_no_messages),
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        count = chatItems.size,
                        key = { index ->
                            when (val item = chatItems[index]) {
                                is ChatItem.DateDivider -> "divider_${item.label}"
                                is ChatItem.Message    -> item.payload.clientMessageUuid
                            }
                        },
                    ) { index ->
                        when (val item = chatItems[index]) {
                            is ChatItem.DateDivider -> DateDividerItem(label = item.label)
                            is ChatItem.Message     -> {
                                val isFamilyChat = state.selectedContactId == FAMILY_GROUP_CHAT_ID
                                val senderName = if (isFamilyChat) {
                                    when (item.payload.senderUserId) {
                                        state.currentUser?.id -> state.currentUser?.displayName
                                        else -> state.contacts
                                            .find { it.user.id == item.payload.senderUserId }
                                            ?.user?.displayName
                                    }
                                } else null
                                MessageBubble(
                                    message = item.payload,
                                    mine = state.currentUser?.id == item.payload.senderUserId,
                                    senderName = senderName,
                                    showSenderName = isFamilyChat,
                                )
                            }
                        }
                    }
                }
            }

            // Sticky date pill — поверх списка, по центру сверху
            StickyDatePillOverlay(
                visible = pillVisible && stickyDate != null,
                label = stickyDate ?: "",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .zIndex(1f),
            )
        }

        // Input bar
        Surface(color = CardBg, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.alpha(0f),
                ) {
                    Icon(
                        AppIcons.Attach,
                        contentDescription = stringResource(Res.string.content_desc_attach),
                        tint = TextSecondary,
                    )
                }
                OutlinedTextField(
                    value = draftField,
                    onValueChange = { value: TextFieldValue ->
                        draftField = value
                        viewModel.updateDraftMessage(value.text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || event.key != Key.Enter) return@onPreviewKeyEvent false
                            if (event.isCtrlPressed) {
                                val updatedText = buildString {
                                    append(draftField.text.substring(0, draftField.selection.start))
                                    append('\n')
                                    append(draftField.text.substring(draftField.selection.end))
                                }
                                val newCursor = draftField.selection.start + 1
                                draftField = TextFieldValue(updatedText, TextRange(newCursor))
                                viewModel.updateDraftMessage(updatedText)
                                return@onPreviewKeyEvent true
                            }
                            viewModel.sendCurrentDraft()
                            true
                        }
                        .testTag(AppTestTags.ChatInput),
                    placeholder = {
                        Text(
                            stringResource(Res.string.chat_input_placeholder),
                            color = TextSecondary,
                            fontSize = 14.sp,
                        )
                    },
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = InputBorder,
                        focusedBorderColor = TgBlue,
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
                    Icon(
                        AppIcons.Send,
                        contentDescription = stringResource(Res.string.content_desc_send),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ── Sticky date pill ──────────────────────────────────────────────────────────

// Отдельная composable-функция вне ColumnScope — AnimatedVisibility резолвится
// в top-level версию, а не в ColumnScope-расширение.
@Composable
private fun StickyDatePillOverlay(visible: Boolean, label: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        StickyDatePill(label = label)
    }
}

@Composable
private fun StickyDatePill(label: String) {
    Surface(
        color = DateOverlayBg,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

// ── Статичный разделитель даты внутри списка ──────────────────────────────────
@Composable
private fun DateDividerItem(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = DateOverlayBg,
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

// ── Пузырь сообщения ──────────────────────────────────────────────────────────
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
                        Text(message.quickActionCode?.name ?: "", fontSize = 17.sp, color = TextPrimary)
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
                            message.location?.label ?: stringResource(Res.string.chat_location_default),
                            fontSize = 16.sp,
                            color = TextPrimary,
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        message.createdAt.formatChatTime(),
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    if (mine) {
                        Icon(
                            painter = painterResource(message.status.iconResource()),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Вставляет DateDivider перед первым сообщением каждого нового дня.
 * Метка: todayLabel, yesterdayLabel, или "12 марта" / "12 марта 2024" (если год отличается).
 */
private fun buildChatItems(
    messages: List<MessagePayload>,
    todayLabel: String,
    yesterdayLabel: String,
): List<ChatItem> {
    if (messages.isEmpty()) return emptyList()

    val tz = TimeZone.currentSystemDefault()
    val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(tz).date
    val yesterday = kotlinx.datetime.LocalDate.fromEpochDays(today.toEpochDays() - 1)

    val result = mutableListOf<ChatItem>()
    var lastDate: kotlinx.datetime.LocalDate? = null

    for (message in messages) {
        val date = message.createdAt?.toLocalDateTime(tz)?.date
        if (date != null && date != lastDate) {
            val label = when (date) {
                today     -> todayLabel
                yesterday -> yesterdayLabel
                else      -> if (date.year == today.year) {
                    "${date.dayOfMonth} ${date.month.russianName()}"
                } else {
                    "${date.dayOfMonth} ${date.month.russianName()} ${date.year}"
                }
            }
            result += ChatItem.DateDivider(label)
            lastDate = date
        }
        result += ChatItem.Message(message)
    }
    return result
}

private fun kotlinx.datetime.Month.russianName(): String = when (this) {
    kotlinx.datetime.Month.JANUARY   -> "января"
    kotlinx.datetime.Month.FEBRUARY  -> "февраля"
    kotlinx.datetime.Month.MARCH     -> "марта"
    kotlinx.datetime.Month.APRIL     -> "апреля"
    kotlinx.datetime.Month.MAY       -> "мая"
    kotlinx.datetime.Month.JUNE      -> "июня"
    kotlinx.datetime.Month.JULY      -> "июля"
    kotlinx.datetime.Month.AUGUST    -> "августа"
    kotlinx.datetime.Month.SEPTEMBER -> "сентября"
    kotlinx.datetime.Month.OCTOBER   -> "октября"
    kotlinx.datetime.Month.NOVEMBER  -> "ноября"
    kotlinx.datetime.Month.DECEMBER  -> "декабря"
    else                              -> toString()
}

private fun senderNameColor(name: String): Color =
    AvatarPalette[name.hashCode().and(0x7FFFFFFF) % AvatarPalette.size]

private fun kotlinx.datetime.Instant?.formatChatTime(): String {
    val instant = this ?: return "--:--"
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minute = localDateTime.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}

private fun MessageStatus.iconResource(): DrawableResource = when (this) {
    MessageStatus.LOCAL_PENDING -> Res.drawable.ic_status_pending
    MessageStatus.SENT          -> Res.drawable.ic_status_sent
    MessageStatus.DELIVERED     -> Res.drawable.ic_status_delivered
    MessageStatus.READ          -> Res.drawable.ic_status_read
}
