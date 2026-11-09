package androidx.compose.ui.res

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Int-id overload of painterResource backing `R.drawable.*` references on
 * desktop. The Authr logo vector has been converted 1:1 from its Android
 * vector drawable into ImageVector builder code (same path data, same
 * colors, same 24dp size).
 */
private val authrLogoVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "ic_authr_logo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Abstract Book/Quill Logo — teal base
        path(fill = SolidColor(Color(0xFF00F5D4))) {
            moveTo(12f, 21f)
            curveTo(9f, 21f, 5f, 19f, 3f, 15f)
            curveTo(5f, 16f, 8f, 16.5f, 11.5f, 15.5f)
            curveTo(12f, 15.3f, 12f, 14.8f, 12f, 14.8f)
            curveTo(12f, 14.8f, 12.3f, 15.2f, 12.5f, 15.5f)
            curveTo(16f, 16.5f, 19f, 16f, 21f, 15f)
            curveTo(19f, 19f, 15f, 21f, 12f, 21f)
            close()
        }
        // Blue base reflection
        path(fill = SolidColor(Color(0xFF48CAE4))) {
            moveTo(12f, 23f)
            curveTo(8f, 23f, 4f, 21f, 2f, 17f)
            curveTo(5f, 18.5f, 8f, 18.5f, 11.5f, 17.5f)
            curveTo(12f, 17.3f, 12f, 16.8f, 12f, 16.8f)
            curveTo(12f, 16.8f, 12.3f, 17.3f, 12.5f, 17.5f)
            curveTo(16f, 18.5f, 19f, 18.5f, 22f, 17f)
            curveTo(20f, 21f, 16f, 23f, 12f, 23f)
            close()
        }
        // White quill diamond
        path(fill = SolidColor(Color(0xFFFFFFFF))) {
            moveTo(12f, 5f)
            lineTo(14.5f, 10.5f)
            lineTo(12f, 15.5f)
            lineTo(9.5f, 10.5f)
            close()
        }
        // Quill detail stroke
        path(
            stroke = SolidColor(Color(0xFF1C2541)),
            strokeLineWidth = 0.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 9f)
            lineTo(12f, 13f)
            moveTo(12f, 9f)
            curveTo(12f, 9f, 11.5f, 8f, 12f, 7f)
            curveTo(12.5f, 8f, 12f, 9f, 12f, 9f)
            close()
        }
    }.build()
}

@Composable
fun painterResource(id: Int): Painter {
    return when (id) {
        0x7f080001 -> rememberVectorPainter(authrLogoVector)
        else -> error("Unknown drawable id $id")
    }
}
