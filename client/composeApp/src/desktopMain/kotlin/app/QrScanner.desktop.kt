package app

import androidx.compose.runtime.Composable

actual fun isQrScannerSupported(): Boolean = false

@Composable
actual fun QrScannerSheet(
    onResult: (String?) -> Unit,
    onDismiss: () -> Unit,
) = Unit
