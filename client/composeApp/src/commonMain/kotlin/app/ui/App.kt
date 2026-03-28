package app.ui

import app.AppViewModel
import app.SetupViewModel
import app.SetupUiState
import app.platformBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familymessenger.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ── Theme wrapper ─────────────────────────────────────────────────────────────
@Composable
fun FamilyMessengerApp(viewModel: AppViewModel, setupViewModel: SetupViewModel? = null) {
    val state by viewModel.state.collectAsState()
    val setupState by (setupViewModel?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(SetupUiState()) }).collectAsState()
    val isDark = isSystemInDarkTheme()

    CompositionLocalProvider(LocalAppColors provides if (isDark) DarkColors else LightColors) {
    MaterialTheme(
        colorScheme = if (isDark) darkColorScheme(
            primary            = TgBlue,
            onPrimary          = Color.White,
            surface            = SurfaceBg,
            background         = AppBg,
            secondaryContainer = TgBlueTint,
        ) else lightColorScheme(
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
                    state.screen == Screen.SPLASH -> SplashScreen(state)
                    state.screen == Screen.ONBOARDING -> LoginScreen(state, viewModel)
                    state.screen == Screen.SETUP -> if (setupViewModel != null) SetupScreen(setupViewModel) else {}
                    isWide -> WideLayout(state, viewModel)
                    else -> when (state.screen) {
                        Screen.CONTACTS  -> ContactsScreen(state, viewModel)
                        Screen.CHAT      -> ChatScreen(state, viewModel)
                        Screen.SETTINGS  -> SettingsScreen(state, viewModel)
                        Screen.ADMIN     -> AdminScreen(state, viewModel)
                        Screen.SPLASH, Screen.ONBOARDING, Screen.SETUP -> {}
                    }
                }

                TgBanner(
                    errorMessage = if (state.screen == Screen.SETUP) setupState.errorMessage else state.errorMessage,
                    statusMessage = if (state.screen == Screen.SETUP) null else state.statusMessage,
                    onDismiss = if (state.screen == Screen.SETUP) setupViewModel?.let { { it.clearError() } } else viewModel::clearBanner,
                )

                if (if (state.screen == Screen.SETUP) setupState.isBusy else state.isBusy) {
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
    } // CompositionLocalProvider
}

// ── Banner ────────────────────────────────────────────────────────────────────
@Composable
private fun TgBanner(errorMessage: UiText?, statusMessage: UiText?, onDismiss: (() -> Unit)?) {
    val banner = errorMessage ?: statusMessage ?: return
    val isError = errorMessage != null
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        color = if (isError) BannerErrorBg else BannerSuccessBg,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = banner.resolve(),
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                color = TextPrimary,
            )
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.action_ok), color = TgBlue, fontWeight = FontWeight.Medium)
                }
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
                .background(SidebarBg),
        ) {
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
                selectedContactId = state.selectedContactId,
                onContactClick = viewModel::openChat,
            )
        }

        // VerticalDivider: CMP 1.7.1 bundles Material3 ~1.3.x — available.
        // If it fails to compile, replace with:
        //   Box(Modifier.width(0.5.dp).fillMaxHeight().background(Divider))
        VerticalDivider(color = CardBorder, thickness = 0.5.dp)

        // Right column — chat or empty placeholder, with settings overlay on top
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val selectedContact = state.contacts.find { it.user.id == state.selectedContactId }
            if (selectedContact != null) {
                val contactStatus = selectedContact.subtitleText()
                Column(modifier = Modifier.fillMaxSize()) {
                    TgTopBar(
                        title = state.selectedContactName ?: stringResource(Res.string.screen_chat),
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
                        Text(stringResource(Res.string.chat_select_contact), color = TextSecondary, fontSize = 15.sp)
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
