package app

import app.ui.FamilyMessengerApp
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val icon = BitmapPainter(
        loadImageBitmap(
            object {}::class.java.classLoader!!.getResourceAsStream("icon.png")!!
        )
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Family Messenger",
        icon = icon,
    ) {
        val app = remember { ClientApp.create(createPlatformServices()) }
        FamilyMessengerApp(app.viewModel)
    }
}
