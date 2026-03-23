package app

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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Palette ───────────────────────────────────────────────────────────────────
private val LoginBlue      = Color(0xFF2AABEE)
private val LoginBlueDark  = Color(0xFF1A8DD1)
private val LoginBlueTint  = Color(0xFFE8F4FD)
private val LoginPageBg    = Color(0xFFE9EAEF)
private val LoginCardBg    = Color(0xFFFFFFFF)
private val LoginBorder    = Color(0xFFE8E8E8)
private val LoginSurfaceBg = Color(0xFFF5F5F5)
private val LoginOnlineGreen = Color(0xFF4DB269)
private val TextSecondary  = Color(0xFF8A8A8A)

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
internal fun LoginScreen(state: AppUiState, viewModel: AppViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginPageBg),
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
    var codeField by remember { mutableStateOf(TextFieldValue("")) }
    var showQrScanner by remember { mutableStateOf(false) }

    // QR scanner sheet — рендерится только на Android/iOS
    if (showQrScanner) {
        QrScannerSheet(
            onResult = { scanned ->
                if (scanned != null) {
                    val code = extractCodeFromQr(scanned)
                    val url  = extractUrlFromQr(scanned)
                    if (code != null) {
                        val fmt = formatInviteCode(code)
                        codeField = TextFieldValue(fmt, selection = TextRange(fmt.length))
                        viewModel.updateInviteCode(fmt)
                    }
                    if (url != null) viewModel.updateBaseUrl(url)
                }
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false },
        )
    }

    Surface(
        color = LoginCardBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, LoginBorder, RoundedCornerShape(16.dp)),
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
                        .background(LoginBlue),
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

            // Invite code field with auto-format XXXX-XXXX
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Код приглашения", fontSize = 13.sp, color = TextSecondary)
                OutlinedTextField(
                    value = codeField,
                    onValueChange = { raw ->
                        val formatted = formatInviteCode(raw.text)
                        // Place cursor at end
                        codeField = TextFieldValue(
                            text = formatted,
                            selection = TextRange(formatted.length),
                        )
                        viewModel.updateInviteCode(formatted)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AppTestTags.OnboardingInviteCode),
                    placeholder = { Text("XXXX-XXXX", color = TextSecondary, fontSize = 15.sp, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                    ),
                    trailingIcon = {
                        if (codeField.text.isNotEmpty()) {
                            IconButton(onClick = {
                                codeField = TextFieldValue("")
                                viewModel.updateInviteCode("")
                            }) {
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
                    "Код из приложения администратора — формат XXXX-XXXX",
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
                    HorizontalDivider(modifier = Modifier.weight(1f), color = LoginBorder, thickness = 0.5.dp)
                    Text("или войти через QR", fontSize = 12.sp, color = TextSecondary)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = LoginBorder, thickness = 0.5.dp)
                }
                OutlinedButton(
                    onClick = { showQrScanner = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LoginBlue),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LoginBlue),
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
                    state.onboarding.inviteCode.length == 9
            Button(
                onClick = viewModel::submitAuth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(AppTestTags.OnboardingSubmit),
                enabled = canSubmit,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LoginBlue,
                    disabledContainerColor = LoginBorder,
                ),
            ) {
                Text("Войти", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            // Platform status row
            Surface(
                color = LoginSurfaceBg,
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
                            .background(LoginOnlineGreen),
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

/** Извлекает код из QR deeplink: familymessenger://join?code=XXXX-XXXX&url=... */
private fun extractCodeFromQr(qr: String): String? =
    Regex("""[?&]code=([A-Z0-9]{4}-[A-Z0-9]{4})""").find(qr)?.groupValues?.get(1)

/** Извлекает адрес сервера из QR deeplink */
private fun extractUrlFromQr(qr: String): String? =
    Regex("""[?&]url=([^&]+)""").find(qr)?.groupValues?.get(1)
        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }

/**
 * Formats raw input into XXXX-XXXX:
 * strips non-alphanumeric, uppercases, inserts dash after 4 chars, caps at 9.
 */
private fun formatInviteCode(raw: String): String {
    val clean = raw.uppercase().filter { it.isLetterOrDigit() }.take(8)
    return if (clean.length > 4) "${clean.take(4)}-${clean.drop(4)}" else clean
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = LoginBorder,
    focusedBorderColor = LoginBlue,
    focusedLabelColor = LoginBlue,
)