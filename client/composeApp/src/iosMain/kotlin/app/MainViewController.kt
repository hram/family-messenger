package app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    val app = remember { ClientApp.create(createPlatformServices()) }
    FamilyMessengerApp(app.viewModel)
}
