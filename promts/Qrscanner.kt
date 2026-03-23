// ─────────────────────────────────────────────────────────────────────────────
// ФАЙЛ 1: client/composeApp/src/commonMain/kotlin/app/QrScanner.kt
//
// expect-объявление — общий контракт для всех платформ.
// ─────────────────────────────────────────────────────────────────────────────

package app

/**
 * Возвращает true на платформах, где QR-сканер доступен (Android, iOS).
 * На Desktop и Web — false.
 */
expect fun isQrScannerSupported(): Boolean

/**
 * Composable-обёртка для запуска нативного QR-сканера.
 * На Android/iOS показывает экран камеры и возвращает результат через [onResult].
 * На Desktop/Web — не вызывается (скрыта через [isQrScannerSupported]).
 *
 * @param onResult  вызывается с отсканированным текстом, либо null при отмене
 * @param onDismiss вызывается при закрытии без результата
 */
@androidx.compose.runtime.Composable
expect fun QrScannerSheet(
    onResult: (String?) -> Unit,
    onDismiss: () -> Unit,
)


// ─────────────────────────────────────────────────────────────────────────────
// ФАЙЛ 2: client/composeApp/src/androidMain/kotlin/app/QrScanner.android.kt
//
// actual для Android — используем CameraX + ML Kit или ZXing.
// Здесь заглушка: реальную реализацию подключить отдельно.
// ─────────────────────────────────────────────────────────────────────────────

//package app
//
//actual fun isQrScannerSupported(): Boolean = true
//
//@androidx.compose.runtime.Composable
//actual fun QrScannerSheet(onResult: (String?) -> Unit, onDismiss: () -> Unit) {
//    // TODO: подключить androidx.camera.core + com.google.mlkit:barcode-scanning
//    // или io.github.g0dkar:qrcode-kotlin для декодирования
//    //
//    // Пример структуры:
//    // AndroidView(factory = { PreviewView(it) }) { ... }
//    // cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
//    // imageAnalysis.setAnalyzer { imageProxy -> ... BarcodeScanning ... }
//}


// ─────────────────────────────────────────────────────────────────────────────
// ФАЙЛ 3: client/composeApp/src/iosMain/kotlin/app/QrScanner.ios.kt
//
// actual для iOS — используем AVFoundation через UIViewControllerRepresentable.
// ─────────────────────────────────────────────────────────────────────────────

//package app
//
//actual fun isQrScannerSupported(): Boolean = true
//
//@androidx.compose.runtime.Composable
//actual fun QrScannerSheet(onResult: (String?) -> Unit, onDismiss: () -> Unit) {
//    // TODO: UIKitViewController { AVCaptureSession + AVCaptureMetadataOutput }
//    // через androidx.compose.ui.interop.UIKitView или UIViewController interop
//}


// ─────────────────────────────────────────────────────────────────────────────
// ФАЙЛ 4: client/composeApp/src/desktopMain/kotlin/app/QrScanner.desktop.kt
// ─────────────────────────────────────────────────────────────────────────────

//package app
//
//actual fun isQrScannerSupported(): Boolean = false
//
//@androidx.compose.runtime.Composable
//actual fun QrScannerSheet(onResult: (String?) -> Unit, onDismiss: () -> Unit) {
//    // Никогда не вызывается — isQrScannerSupported() == false
//}


// ─────────────────────────────────────────────────────────────────────────────
// ФАЙЛ 5: client/composeApp/src/wasmJsMain/kotlin/app/QrScanner.wasmJs.kt
// ─────────────────────────────────────────────────────────────────────────────

//package app
//
//actual fun isQrScannerSupported(): Boolean = false
//
//@androidx.compose.runtime.Composable
//actual fun QrScannerSheet(onResult: (String?) -> Unit, onDismiss: () -> Unit) {
//    // Никогда не вызывается — isQrScannerSupported() == false
//}