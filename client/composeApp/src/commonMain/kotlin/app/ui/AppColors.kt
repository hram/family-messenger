package app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Color scheme definition ───────────────────────────────────────────────────

internal data class AppColorScheme(
    val tgBlue: Color,
    val tgBlueDark: Color,
    val tgBlueTint: Color,
    val tgBlueBorder: Color,
    val appBg: Color,
    val setupBg: Color,
    val splashBg: Color,
    val surfaceBg: Color,
    val sidebarBg: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val inputBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val onlineGreen: Color,
    val errorRed: Color,
    val destructiveRed: Color,
    val linkBlue: Color,
    val warnBg: Color,
    val warnBorder: Color,
    val warnText: Color,
    val bannerErrorBg: Color,
    val bannerSuccessBg: Color,
    val bubbleMe: Color,
    val bubbleThem: Color,
    val dateOverlayBg: Color,
    val strengthWeak: Color,
    val strengthMedium: Color,
    val strengthStrong: Color,
)

internal val LightColors = AppColorScheme(
    tgBlue         = Color(0xFF2AABEE),
    tgBlueDark     = Color(0xFF1A8DD1),
    tgBlueTint     = Color(0xFFE8F4FD),
    tgBlueBorder   = Color(0xFFB5D4F4),
    appBg          = Color(0xFFE9EAEF),
    setupBg        = Color(0xFFF0F2F5),
    splashBg       = Color(0xFFE5EEF7),
    surfaceBg      = Color(0xFFF5F5F5),
    sidebarBg      = Color(0xFFFFFFFF),
    cardBg         = Color(0xFFFFFFFF),
    cardBorder     = Color(0xFFE8E8E8),
    inputBorder    = Color(0xFFE0E0E0),
    textPrimary    = Color(0xFF000000),
    textSecondary  = Color(0xFF8A8A8A),
    onlineGreen    = Color(0xFF4DB269),
    errorRed       = Color(0xFFE24B4A),
    destructiveRed = Color(0xFFFF3B30),
    linkBlue       = Color(0xFF185FA5),
    warnBg         = Color(0xFFFFF8E8),
    warnBorder     = Color(0xFFF0C060),
    warnText       = Color(0xFF633806),
    bannerErrorBg   = Color(0xFFF9D6D0),
    bannerSuccessBg = Color(0xFFD8F0D8),
    bubbleMe       = Color(0xFFDCEFD2),
    bubbleThem     = Color(0xFFFFFFFF),
    dateOverlayBg  = Color(0x59000000),
    strengthWeak   = Color(0xFFE24B4A),
    strengthMedium = Color(0xFFEF9F27),
    strengthStrong = Color(0xFF639922),
)

internal val DarkColors = AppColorScheme(
    tgBlue         = Color(0xFF2AABEE),
    tgBlueDark     = Color(0xFF1A8DD1),
    tgBlueTint     = Color(0xFF0D2D3F),
    tgBlueBorder   = Color(0xFF1E4A6A),
    appBg          = Color(0xFF1C1C1E),
    setupBg        = Color(0xFF161618),
    splashBg       = Color(0xFF18222C),
    surfaceBg      = Color(0xFF2C2C2E),
    sidebarBg      = Color(0xFF1C1C1E),
    cardBg         = Color(0xFF2C2C2E),
    cardBorder     = Color(0xFF3A3A3C),
    inputBorder    = Color(0xFF3A3A3C),
    textPrimary    = Color(0xFFF2F2F7),
    textSecondary  = Color(0xFF8D8D93),
    onlineGreen    = Color(0xFF4DB269),
    errorRed       = Color(0xFFFF6B6B),
    destructiveRed = Color(0xFFFF453A),
    linkBlue       = Color(0xFF5AC8FA),
    warnBg         = Color(0xFF2A2010),
    warnBorder     = Color(0xFF7A5A1A),
    warnText       = Color(0xFFF0C060),
    bannerErrorBg   = Color(0xFF3D1A18),
    bannerSuccessBg = Color(0xFF0F2B1A),
    bubbleMe       = Color(0xFF1E3A28),
    bubbleThem     = Color(0xFF2C2C2E),
    dateOverlayBg  = Color(0x59000000),
    strengthWeak   = Color(0xFFFF6B6B),
    strengthMedium = Color(0xFFEF9F27),
    strengthStrong = Color(0xFF639922),
)

internal val LocalAppColors = staticCompositionLocalOf { LightColors }

// ── Composable-aware color accessors ─────────────────────────────────────────
// Прозрачные замены старых плоских констант — в @Composable-контексте работают
// без изменений на местах использования, читают из LocalAppColors.

internal val TgBlue: Color         @Composable get() = LocalAppColors.current.tgBlue
internal val TgBlueDark: Color     @Composable get() = LocalAppColors.current.tgBlueDark
internal val TgBlueTint: Color     @Composable get() = LocalAppColors.current.tgBlueTint
internal val TgBlueBorder: Color   @Composable get() = LocalAppColors.current.tgBlueBorder
internal val AppBg: Color          @Composable get() = LocalAppColors.current.appBg
internal val SetupBg: Color        @Composable get() = LocalAppColors.current.setupBg
internal val SplashBg: Color       @Composable get() = LocalAppColors.current.splashBg
internal val SurfaceBg: Color      @Composable get() = LocalAppColors.current.surfaceBg
internal val SidebarBg: Color      @Composable get() = LocalAppColors.current.sidebarBg
internal val CardBg: Color         @Composable get() = LocalAppColors.current.cardBg
internal val CardBorder: Color     @Composable get() = LocalAppColors.current.cardBorder
internal val InputBorder: Color    @Composable get() = LocalAppColors.current.inputBorder
internal val TextPrimary: Color    @Composable get() = LocalAppColors.current.textPrimary
internal val TextSecondary: Color  @Composable get() = LocalAppColors.current.textSecondary
internal val OnlineGreen: Color    @Composable get() = LocalAppColors.current.onlineGreen
internal val ErrorRed: Color       @Composable get() = LocalAppColors.current.errorRed
internal val DestructiveRed: Color @Composable get() = LocalAppColors.current.destructiveRed
internal val LinkBlue: Color       @Composable get() = LocalAppColors.current.linkBlue
internal val WarnBg: Color         @Composable get() = LocalAppColors.current.warnBg
internal val WarnBorder: Color     @Composable get() = LocalAppColors.current.warnBorder
internal val WarnText: Color       @Composable get() = LocalAppColors.current.warnText
internal val BannerErrorBg: Color   @Composable get() = LocalAppColors.current.bannerErrorBg
internal val BannerSuccessBg: Color @Composable get() = LocalAppColors.current.bannerSuccessBg
internal val BubbleMe: Color        @Composable get() = LocalAppColors.current.bubbleMe
internal val BubbleThem: Color      @Composable get() = LocalAppColors.current.bubbleThem
internal val DateOverlayBg: Color   @Composable get() = LocalAppColors.current.dateOverlayBg
internal val StrengthWeak: Color    @Composable get() = LocalAppColors.current.strengthWeak
internal val StrengthMedium: Color @Composable get() = LocalAppColors.current.strengthMedium
internal val StrengthStrong: Color @Composable get() = LocalAppColors.current.strengthStrong

// Avatar / sender-name palette — одинакова для обеих тем
internal val AvatarPalette = listOf(
    Color(0xFF2AABEE), Color(0xFF4DB269), Color(0xFFE8A840),
    Color(0xFF8B5CF6), Color(0xFFE05454), Color(0xFF1A8DD1),
)
