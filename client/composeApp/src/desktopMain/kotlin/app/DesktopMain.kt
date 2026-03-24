package app

import app.ui.FamilyMessengerApp
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Family Messenger",
    ) {
        val app = remember { ClientApp.create(createPlatformServices()) }
        FamilyMessengerApp(app.viewModel)
    }
}
