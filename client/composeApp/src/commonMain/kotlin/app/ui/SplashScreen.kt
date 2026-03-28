package app.ui

import app.appLogoPainter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.familymessenger.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SplashScreen(state: AppUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Image(
                painter = appLogoPainter(),
                contentDescription = null,
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(30.dp)),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(Res.string.app_name),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Text(
                    stringResource(Res.string.app_tagline),
                    fontSize = 15.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 0.dp,
                modifier = Modifier.border(0.5.dp, CardBorder, RoundedCornerShape(12.dp)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = TgBlue,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = when {
                            state.currentUser != null -> stringResource(Res.string.splash_loading_chats, state.currentUser.displayName)
                            else -> stringResource(Res.string.splash_preparing)
                        },
                        fontSize = 14.sp,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}
