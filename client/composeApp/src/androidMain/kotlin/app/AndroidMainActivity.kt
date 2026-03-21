package app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class AndroidMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apiClient = FamilyMessengerApiClient.create(
            baseUrl = "http://10.0.2.2:8080",
            engineFactory = { defaultHttpClient(createPlatformHttpClient()) },
        )
        val viewModel = AppViewModel(
            initialPlatform = platformType(),
            contactsRepository = ContactsRepository(apiClient),
        )
        setContent {
            FamilyMessengerApp(viewModel)
        }
    }
}
