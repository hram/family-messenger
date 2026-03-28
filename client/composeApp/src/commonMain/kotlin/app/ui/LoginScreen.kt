package app.ui

import app.AppViewModel
import app.isQrScannerSupported
import app.QrScannerSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
internal fun LoginScreen(state: AppUiState, viewModel: AppViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 40.dp),
        ) {
            LoginCard(state, viewModel)
        }
    }
}

@Composable
private fun LoginCard(state: AppUiState, viewModel: AppViewModel) {
    var showQrScanner by remember { mutableStateOf(false) }

    if (showQrScanner) {
        QrScannerSheet(
            onResult = { scanned ->
                if (scanned != null) {
                    val code = extractCodeFromQr(scanned)
                    val url = extractUrlFromQr(scanned) ?: state.onboarding.baseUrl
                    if (code != null && url.isNotBlank()) {
                        viewModel.submitScannedAuth(
                            baseUrl = url,
                            inviteCode = code,
                        )
                    } else {
                        if (code != null) viewModel.updateInviteCode(code)
                        if (url.isNotBlank()) viewModel.updateBaseUrl(url)
                    }
                }
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false },
        )
    }

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, CardBorder, RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Logo row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(TgBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "Family Messenger",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                    )
                    Text(
                        "Семейный чат",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
            }

            // Server URL field
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Адрес сервера", fontSize = 13.sp, color = TextSecondary)
                OutlinedTextField(
                    value = state.onboarding.baseUrl,
                    onValueChange = viewModel::updateBaseUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AppTestTags.OnboardingBaseUrl),
                    placeholder = { Text("http://192.168.1.10:8081", color = TextSecondary, fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(8.dp),
                    colors = loginFieldColors(),
                )
                Text(
                    "IP-адрес вашего домашнего сервера",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }

            // Invite code field
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Код приглашения", fontSize = 13.sp, color = TextSecondary)
                OutlinedTextField(
                    value = state.onboarding.inviteCode,
                    onValueChange = viewModel::updateInviteCode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AppTestTags.OnboardingInviteCode),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                    ),
                    trailingIcon = {
                        if (state.onboarding.inviteCode.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateInviteCode("") }) {
                                Icon(
                                    imageVector = AppIcons.Clear,
                                    contentDescription = "Очистить",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = loginFieldColors(),
                )
                Text(
                    "Код из приложения администратора",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }

            // QR button — только на Android/iOS
            if (isQrScannerSupported()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = CardBorder, thickness = 0.5.dp)
                    Text("или войти через QR", fontSize = 12.sp, color = TextSecondary)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = CardBorder, thickness = 0.5.dp)
                }
                OutlinedButton(
                    onClick = { showQrScanner = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TgBlue),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TgBlue),
                ) {
                    Icon(
                        imageVector = AppIcons.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Сканировать QR-код", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Submit button
            val canSubmit = state.onboarding.baseUrl.isNotEmpty() &&
                    state.onboarding.inviteCode.isNotEmpty()
            Button(
                onClick = viewModel::submitAuth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(AppTestTags.OnboardingSubmit),
                enabled = canSubmit,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TgBlue,
                    disabledContainerColor = CardBorder,
                ),
            ) {
                Text("Войти", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            // Platform status row
            Surface(
                color = SurfaceBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen),
                    )
                    Text(
                        "Платформа: ${state.platformName}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Извлекает код из QR payload или legacy deeplink. */
private fun extractCodeFromQr(qr: String): String? =
    parseInviteQrPayload(qr)?.code
        ?: Regex("""[?&]code=([^&]+)""").find(qr)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

/** Извлекает адрес сервера из QR payload или legacy deeplink. */
private fun extractUrlFromQr(qr: String): String? =
    parseInviteQrPayload(qr)?.url
        ?: Regex("""[?&]url=([^&]+)""").find(qr)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

private data class InviteQrPayload(
    val code: String,
    val url: String,
)

private fun parseInviteQrPayload(qr: String): InviteQrPayload? = runCatching {
    val json = Json.parseToJsonElement(qr) as? JsonObject ?: return null
    val code = json["code"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
    val url = json["url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
    InviteQrPayload(code = code, url = url)
}.getOrNull()


@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = CardBorder,
    focusedBorderColor = TgBlue,
    focusedLabelColor = TgBlue,
)

