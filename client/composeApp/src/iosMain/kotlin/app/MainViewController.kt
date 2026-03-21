package app

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    val apiClient = FamilyMessengerApiClient.create(
        baseUrl = "http://localhost:8080",
        engineFactory = { defaultHttpClient(createPlatformHttpClient()) },
    )
    val viewModel = AppViewModel(
        initialPlatform = platformType(),
        contactsRepository = ContactsRepository(apiClient),
    )
    FamilyMessengerApp(viewModel)
}
