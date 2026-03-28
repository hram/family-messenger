package app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import androidx.compose.foundation.Image


@Composable
fun QrCodeDialog(
    inviteCode: String,
    serverUrl: String,
    displayName: String,
    onDismiss: () -> Unit,
) {
    val qrPayload = """{"code":"${inviteCode.jsonEscape()}","url":"${serverUrl.jsonEscape()}"}"""
    val painter = rememberQrCodePainter(qrPayload)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = TgBlue)
            }
        },
        title = {
            Text(
                "QR-код приглашения",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    displayName,
                    fontSize = 14.sp,
                    color = TextSecondary,
                )

                Image(
                    painter = painter,
                    contentDescription = "QR-код для $displayName",
                    modifier = Modifier
                        .size(220.dp)
                        .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                )

                Text(
                    inviteCode,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = TgBlueDark,
                    letterSpacing = 2.sp,
                )
                Text(
                    "Отсканируйте QR или введите код и адрес сервера вручную",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
        },
        shape = RoundedCornerShape(16.dp),
    )
}

private fun String.jsonEscape(): String = buildString(length + 8) {
    for (ch in this@jsonEscape) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
}
