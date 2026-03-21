package app

import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

fun main() {
    val apiClient = FamilyMessengerApiClient.create(
        baseUrl = "http://localhost:8080",
        engineFactory = { defaultHttpClient(createPlatformHttpClient()) },
    )
    val viewModel = AppViewModel(
        initialPlatform = platformType(),
        contactsRepository = ContactsRepository(apiClient),
    )

    ComposeViewport(document.body!!) {
        FamilyMessengerApp(viewModel)
    }
}
