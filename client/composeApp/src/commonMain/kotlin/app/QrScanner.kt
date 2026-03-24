package app

import androidx.compose.runtime.Composable

expect fun isQrScannerSupported(): Boolean

@Composable
expect fun QrScannerSheet(onResult: (String?) -> Unit, onDismiss: () -> Unit)
