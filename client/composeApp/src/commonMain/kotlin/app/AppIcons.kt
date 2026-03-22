package app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 3.5f)
                lineTo(13.4f, 3.8f)
                lineTo(14.0f, 5.5f)
                lineTo(15.8f, 6.2f)
                lineTo(17.2f, 5.2f)
                lineTo(18.5f, 6.5f)
                lineTo(17.5f, 7.9f)
                lineTo(18.2f, 9.7f)
                lineTo(20f, 10.3f)
                lineTo(20f, 12f)
                lineTo(18.2f, 12.7f)
                lineTo(17.5f, 14.5f)
                lineTo(18.5f, 15.9f)
                lineTo(17.2f, 17.2f)
                lineTo(15.8f, 16.2f)
                lineTo(14.0f, 16.9f)
                lineTo(13.4f, 18.6f)
                lineTo(12f, 19f)
                lineTo(10.6f, 18.6f)
                lineTo(10.0f, 16.9f)
                lineTo(8.2f, 16.2f)
                lineTo(6.8f, 17.2f)
                lineTo(5.5f, 15.9f)
                lineTo(6.5f, 14.5f)
                lineTo(5.8f, 12.7f)
                lineTo(4f, 12f)
                lineTo(4f, 10.3f)
                lineTo(5.8f, 9.7f)
                lineTo(6.5f, 7.9f)
                lineTo(5.5f, 6.5f)
                lineTo(6.8f, 5.2f)
                lineTo(8.2f, 6.2f)
                lineTo(10f, 5.5f)
                lineTo(10.6f, 3.8f)
                close()
                moveTo(12f, 9.3f)
                arcToRelative(2.7f, 2.7f, 0f, true, true, 0f, 5.4f)
                arcToRelative(2.7f, 2.7f, 0f, true, true, 0f, -5.4f)
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder("Refresh", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20f, 6f)
                lineTo(20f, 11f)
                lineTo(15f, 11f)
                moveTo(4f, 18f)
                lineTo(4f, 13f)
                lineTo(9f, 13f)
                moveTo(6.5f, 9.5f)
                curveTo(7.8f, 7.6f, 9.8f, 6.5f, 12f, 6.5f)
                curveTo(14.7f, 6.5f, 17.1f, 8.1f, 18.3f, 10.5f)
                moveTo(17.5f, 14.5f)
                curveTo(16.2f, 16.4f, 14.2f, 17.5f, 12f, 17.5f)
                curveTo(9.3f, 17.5f, 6.9f, 15.9f, 5.7f, 13.5f)
            }
        }.build()
    }

    val Back: ImageVector by lazy {
        ImageVector.Builder("Back", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(14.5f, 6f)
                lineTo(8.5f, 12f)
                lineTo(14.5f, 18f)
            }
        }.build()
    }

    val Attach: ImageVector by lazy {
        ImageVector.Builder("Attach", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color(0xFF8A8A8A)),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.5f, 12f)
                lineTo(14.6f, 5.9f)
                curveTo(16.3f, 4.2f, 19.1f, 4.2f, 20.8f, 5.9f)
                curveTo(22.4f, 7.5f, 22.4f, 10.2f, 20.8f, 11.9f)
                lineTo(11.2f, 21.5f)
                curveTo(8.9f, 23.8f, 5.1f, 23.8f, 2.8f, 21.5f)
                curveTo(0.6f, 19.3f, 0.6f, 15.7f, 2.8f, 13.4f)
                lineTo(11.4f, 4.8f)
            }
        }.build()
    }

    val Send: ImageVector by lazy {
        ImageVector.Builder("Send", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(22f, 2f)
                lineTo(11f, 13f)
                moveTo(22f, 2f)
                lineTo(15f, 22f)
                lineTo(11f, 13f)
                lineTo(2f, 9f)
                close()
            }
        }.build()
    }

    val Eye: ImageVector by lazy {
        ImageVector.Builder("Eye", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color(0xFF8A8A8A)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(2f, 12f)
                curveTo(4.5f, 7.6f, 8f, 5.4f, 12f, 5.4f)
                curveTo(16f, 5.4f, 19.5f, 7.6f, 22f, 12f)
                curveTo(19.5f, 16.4f, 16f, 18.6f, 12f, 18.6f)
                curveTo(8f, 18.6f, 4.5f, 16.4f, 2f, 12f)
                close()
                moveTo(12f, 9.2f)
                arcToRelative(2.8f, 2.8f, 0f, true, true, 0f, 5.6f)
                arcToRelative(2.8f, 2.8f, 0f, true, true, 0f, -5.6f)
            }
        }.build()
    }

    val EyeOff: ImageVector by lazy {
        ImageVector.Builder("EyeOff", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color(0xFF8A8A8A)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3f, 3f)
                lineTo(21f, 21f)
                moveTo(10.6f, 5.6f)
                curveTo(11.1f, 5.5f, 11.5f, 5.4f, 12f, 5.4f)
                curveTo(16f, 5.4f, 19.5f, 7.6f, 22f, 12f)
                curveTo(20.9f, 13.9f, 19.6f, 15.4f, 18.1f, 16.5f)
                moveTo(13.9f, 18.4f)
                curveTo(13.3f, 18.5f, 12.7f, 18.6f, 12f, 18.6f)
                curveTo(8f, 18.6f, 4.5f, 16.4f, 2f, 12f)
                curveTo(3f, 10.3f, 4.2f, 8.9f, 5.5f, 7.8f)
                moveTo(9.8f, 9.8f)
                curveTo(9.1f, 10.5f, 8.8f, 11.2f, 8.8f, 12f)
                curveTo(8.8f, 13.8f, 10.2f, 15.2f, 12f, 15.2f)
                curveTo(12.8f, 15.2f, 13.5f, 14.9f, 14.2f, 14.2f)
            }
        }.build()
    }

    val Clear: ImageVector by lazy {
        ImageVector.Builder("Clear", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color(0xFF8A8A8A)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 7f)
                lineTo(17f, 17f)
                moveTo(17f, 7f)
                lineTo(7f, 17f)
            }
        }.build()
    }

    val Location: ImageVector by lazy {
        ImageVector.Builder("Location", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color(0xFF1A8DD1)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 21f)
                curveTo(15.8f, 16.8f, 18f, 13.6f, 18f, 10.4f)
                curveTo(18f, 6.9f, 15.3f, 4f, 12f, 4f)
                curveTo(8.7f, 4f, 6f, 6.9f, 6f, 10.4f)
                curveTo(6f, 13.6f, 8.2f, 16.8f, 12f, 21f)
                close()
                moveTo(12f, 8.2f)
                arcToRelative(2.2f, 2.2f, 0f, true, true, 0f, 4.4f)
                arcToRelative(2.2f, 2.2f, 0f, true, true, 0f, -4.4f)
            }
        }.build()
    }

    val Bolt: ImageVector by lazy {
        ImageVector.Builder("Bolt", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(13.2f, 2f)
                lineTo(6.8f, 12f)
                lineTo(11.3f, 12f)
                lineTo(9.8f, 22f)
                lineTo(17.2f, 10.8f)
                lineTo(12.8f, 10.8f)
                close()
            }
        }.build()
    }

    val Chat: ImageVector by lazy {
        ImageVector.Builder("Chat", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color(0xFF8A8A8A)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 6f)
                curveTo(5f, 4.9f, 5.9f, 4f, 7f, 4f)
                lineTo(17f, 4f)
                curveTo(18.1f, 4f, 19f, 4.9f, 19f, 6f)
                lineTo(19f, 14f)
                curveTo(19f, 15.1f, 18.1f, 16f, 17f, 16f)
                lineTo(10f, 16f)
                lineTo(6f, 20f)
                lineTo(6f, 16f)
                curveTo(5.4f, 15.8f, 5f, 15.1f, 5f, 14f)
                close()
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color(0xFF8A8A8A)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 12.5f)
                lineTo(10f, 16.5f)
                lineTo(18f, 8.5f)
            }
        }.build()
    }

    val DoubleCheck: ImageVector by lazy {
        ImageVector.Builder("DoubleCheck", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color(0xFF2AABEE)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 12.5f)
                lineTo(7.2f, 16.2f)
                lineTo(12.2f, 11.2f)
                moveTo(9.8f, 12.5f)
                lineTo(13.5f, 16.2f)
                lineTo(20.5f, 9.2f)
            }
        }.build()
    }
}
