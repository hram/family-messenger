package app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.UserRole

@Composable
internal fun TgTopBar(
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
internal fun AvatarCircle(name: String, size: Int) {
    val initials = name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    val bg = AvatarPalette[name.hashCode().and(0x7FFFFFFF) % AvatarPalette.size]

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
internal fun TgChip(label: String, onClick: () -> Unit, icon: ImageVector? = null) {
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
internal fun TgTextField(
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
            unfocusedBorderColor = InputBorder,
            focusedBorderColor   = TgBlue,
            focusedLabelColor    = TgBlue,
        ),
    )
}

internal fun ContactSummary.isFamilyGroup(): Boolean =
    user.id == FAMILY_GROUP_CHAT_ID || user.role == UserRole.FAMILY

internal fun ContactSummary.subtitleText(): String =
    when {
        isFamilyGroup() -> "group chat"
        isOnline -> "online"
        else -> "offline · ${user.role.name.lowercase()}"
    }
