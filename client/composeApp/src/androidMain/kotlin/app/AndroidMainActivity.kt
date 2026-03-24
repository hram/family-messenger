package app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.ui.FamilyMessengerApp
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat

class AndroidMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeAndroidPlatform(applicationContext)
        requestNotificationPermissionIfNeeded()
        setContent {
            val app = remember { ClientApp.create(createPlatformServices()) }
            FamilyMessengerApp(app.viewModel)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
    }
}
