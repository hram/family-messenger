@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import org.jetbrains.skiko.wasm.onWasmReady

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    onWasmReady {
        CanvasBasedWindow("Family Messenger") {
            val app = remember { ClientApp.create(createPlatformServices()) }
            FamilyMessengerApp(app.viewModel)
        }
    }
}
