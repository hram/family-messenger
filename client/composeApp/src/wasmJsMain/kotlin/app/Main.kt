@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        val app = remember { ClientApp.create(createPlatformServices()) }
        FamilyMessengerApp(app.viewModel)
    }
}
