package app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember

class AndroidMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeAndroidPlatform(applicationContext)
        setContent {
            val app = remember { ClientApp.create(createPlatformServices()) }
            FamilyMessengerApp(app.viewModel)
        }
    }
}
