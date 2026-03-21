package app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val apiClient = FamilyMessengerApiClient.create(
        baseUrl = "http://localhost:8080",
        engineFactory = { defaultHttpClient(createPlatformHttpClient()) },
    )
    val viewModel = AppViewModel(
        initialPlatform = platformType(),
        contactsRepository = ContactsRepository(apiClient),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Family Messenger",
    ) {
        FamilyMessengerApp(viewModel)
    }
}
