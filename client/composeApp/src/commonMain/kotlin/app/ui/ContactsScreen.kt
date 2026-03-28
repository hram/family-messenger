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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familymessenger.composeapp.generated.resources.*
import com.familymessenger.contract.ContactSummary
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ContactsScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TgTopBar(
            title = stringResource(Res.string.screen_chats),
            subtitle = state.currentUser?.displayName ?: "",
            trailingContent = {
                IconButton(
                    onClick = viewModel::openSettings,
                    modifier = Modifier.testTag(AppTestTags.TopBarSettings),
                ) {
                    Icon(AppIcons.Settings, contentDescription = stringResource(Res.string.content_desc_settings), tint = Color.White)
                }
                IconButton(
                    onClick = viewModel::refreshContacts,
                    modifier = Modifier.testTag(AppTestTags.TopBarRefresh),
                ) {
                    Icon(AppIcons.Refresh, contentDescription = stringResource(Res.string.content_desc_refresh), tint = Color.White)
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

@Composable
internal fun ContactsPanel(
    contacts: List<ContactSummary>,
    unreadCounts: Map<Long, Int> = emptyMap(),
    selectedContactId: Long? = null,
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
                    stringResource(Res.string.contacts_empty_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Text(
                    stringResource(Res.string.contacts_empty_subtitle),
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
                HorizontalDivider(color = CardBorder, thickness = 1.dp)
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
