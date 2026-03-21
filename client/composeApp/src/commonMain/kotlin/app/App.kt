package app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FamilyMessengerApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Family Messenger") })
            },
        ) { padding ->
            when (state.screen) {
                Screen.ONBOARDING -> OnboardingScreen(
                    modifier = Modifier.padding(padding),
                    onContinue = viewModel::loadContacts,
                )

                Screen.CONTACTS -> ContactsScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onOpenChat = viewModel::openChat,
                    onOpenSettings = viewModel::openSettings,
                )

                Screen.CHAT -> ChatScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                )

                Screen.SETTINGS -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                )
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Step 1 onboarding scaffold")
        Text("Shared client logic, DTO wiring, and platform entry points are in place.")
        Button(onClick = onContinue) {
            Text("Open contacts")
        }
    }
}

@Composable
private fun ContactsScreen(
    modifier: Modifier,
    state: AppUiState,
    onOpenChat: (com.familymessenger.contract.ContactSummary) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Contacts")
        LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
            items(state.contacts) { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat(contact) }
                        .padding(vertical = 8.dp),
                ) {
                    Text("${contact.user.displayName} · online=${contact.isOnline}")
                }
            }
        }
        Button(onClick = onOpenSettings) {
            Text("Settings")
        }
    }
}

@Composable
private fun ChatScreen(
    modifier: Modifier,
    state: AppUiState,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Chat with contact ${state.selectedContactId ?: "-"}")
        state.messages.forEach { message ->
            Text("${message.type}: ${message.body ?: message.quickActionCode ?: "location"}")
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    state: AppUiState,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings")
        Text("Base URL: ${state.serverBaseUrl}")
        Text("Platform: ${state.platform}")
    }
}
