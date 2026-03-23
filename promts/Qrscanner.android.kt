package app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlin.math.roundToInt

// ── Palette (локальная копия, чтобы не зависеть от commonMain) ─────────────
private val QrBlue       = Color(0xFF2AABEE)
private val QrBlueTint   = Color(0xFFE8F4FD)
private val QrBlueDark   = Color(0xFF1A8DD1)
private val QrCardBorder = Color(0xFFE8E8E8)
private val QrSurfaceBg  = Color(0xFFF5F5F5)
private val QrTextSec    = Color(0xFF8A8A8A)
private val QrScanLine   = Color(0xCC2AABEE)   // синяя линия, 80% opacity

actual fun isQrScannerSupported(): Boolean = true

@Composable
actual fun QrScannerSheet(
    onResult: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var barcodeView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var resultDelivered by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner, barcodeView) {
        val view = barcodeView
        val observer = LifecycleEventObserver { _, event ->
            if (view != null) when (event) {
                Lifecycle.Event.ON_RESUME  -> view.resume()
                Lifecycle.Event.ON_PAUSE   -> view.pause()
                Lifecycle.Event.ON_DESTROY -> view.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view?.pause()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 0.dp,   // без тени — в стиле приложения
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Заголовок
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Сканировать QR-код",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                    )
                    Text(
                        "Наведите камеру на QR-код приглашения. Код и адрес сервера подставятся автоматически.",
                        fontSize = 13.sp,
                        color = QrTextSec,
                        lineHeight = 18.sp,
                    )
                }

                // Область камеры / состояние без разрешения
                if (hasPermission) {
                    CameraPreview(
                        barcodeView = barcodeView,
                        resultDelivered = resultDelivered,
                        onViewCreated = { barcodeView = it },
                        onResult = { raw ->
                            if (!resultDelivered) {
                                resultDelivered = true
                                onResult(raw)
                            }
                        },
                    )
                } else {
                    NoPermissionState(
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                    )
                }

                // Кнопка отмены
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Отмена", color = QrTextSec, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ── Превью камеры с оверлеем ──────────────────────────────────────────────────
@Composable
private fun CameraPreview(
    barcodeView: DecoratedBarcodeView?,
    resultDelivered: Boolean,
    onViewCreated: (DecoratedBarcodeView) -> Unit,
    onResult: (String) -> Unit,
) {
    val density = LocalDensity.current
    var boxHeightPx by remember { mutableStateOf(0) }

    // Анимация линии сканирования
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanFraction by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue  = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
        ),
        label = "scanLine",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .onSizeChanged { boxHeightPx = it.height },
    ) {
        // Камера
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                DecoratedBarcodeView(ctx).apply {
                    statusView?.text = ""
                    barcodeView?.decoderFactory =
                        DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                    initializeFromIntent(null)
                    decodeContinuous(object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult?) {
                            val text = result?.text ?: return
                            onResult(text)
                        }
                    })
                    resume()
                    onViewCreated(this)
                }
            },
            update = { view ->
                onViewCreated(view)
                if (!resultDelivered) view.resume() else view.pause()
            },
        )

        // Анимированная линия
        if (boxHeightPx > 0) {
            val offsetY = (boxHeightPx * scanFraction).roundToInt()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset { IntOffset(0, offsetY) }
                    .background(QrScanLine),
            )
        }

        // Угловые маркеры (синие, Telegram-стиль)
        val cornerSize = 20.dp
        val cornerPad  = 28.dp
        val cornerStroke = 3.dp

        // top-left
        QrCorner(Modifier.align(Alignment.TopStart).padding(start = cornerPad, top = cornerPad),
            top = true, left = true, size = cornerSize, stroke = cornerStroke)
        // top-right
        QrCorner(Modifier.align(Alignment.TopEnd).padding(end = cornerPad, top = cornerPad),
            top = true, left = false, size = cornerSize, stroke = cornerStroke)
        // bottom-left
        QrCorner(Modifier.align(Alignment.BottomStart).padding(start = cornerPad, bottom = cornerPad),
            top = false, left = true, size = cornerSize, stroke = cornerStroke)
        // bottom-right
        QrCorner(Modifier.align(Alignment.BottomEnd).padding(end = cornerPad, bottom = cornerPad),
            top = false, left = false, size = cornerSize, stroke = cornerStroke)
    }
}

@Composable
private fun QrCorner(
    modifier: Modifier,
    top: Boolean,
    left: Boolean,
    size: androidx.compose.ui.unit.Dp,
    stroke: androidx.compose.ui.unit.Dp,
) {
    val shape = RoundedCornerShape(
        topStart     = if (top && left)   4.dp else 0.dp,
        topEnd       = if (top && !left)  4.dp else 0.dp,
        bottomStart  = if (!top && left)  4.dp else 0.dp,
        bottomEnd    = if (!top && !left) 4.dp else 0.dp,
    )
    Box(
        modifier = modifier
            .size(size)
            .border(
                width = stroke,
                color = QrBlue,
                shape = shape,
            ),
    )
}

// ── Состояние без разрешения ──────────────────────────────────────────────────
@Composable
private fun NoPermissionState(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        color = QrSurfaceBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Иконка камеры
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(QrBlueTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Camera,
                    contentDescription = null,
                    tint = QrBlue,
                    modifier = Modifier.size(26.dp),
                )
            }

            Text(
                "Нужен доступ к камере",
                modifier = Modifier.padding(top = 14.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
            )
            Text(
                "Без разрешения сканирование QR недоступно",
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 12.sp,
                color = QrTextSec,
            )
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.padding(top = 16.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QrBlue),
            ) {
                Text("Разрешить камеру", fontWeight = FontWeight.Medium)
            }
        }
    }
}