package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.TutorialDiagramType
import com.example.ui.theme.StudioTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Code-drawn geometric TK Studio emblem (hexagonal code brackets + interlocking T/K monogram).
 * Zero PNG/SVG assets.
 */
@Composable
fun StudioBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDesc: String = "TK Studio emblem"
) {
    val p = StudioTheme.palette
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = contentDesc }
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.45f

        // Subtle ambient glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(p.glow.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 1.2f
            ),
            radius = r * 1.2f,
            center = Offset(cx, cy)
        )

        // Hexagonal outer frame
        val hex = Path().apply {
            for (i in 0 until 6) {
                val angle = Math.toRadians((60 * i - 90).toDouble())
                val x = cx + r * cos(angle).toFloat()
                val y = cy + r * sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path = hex,
            brush = Brush.linearGradient(listOf(p.card, p.raised)),
        )
        drawPath(
            path = hex,
            brush = Brush.linearGradient(listOf(p.cyan, p.primary, p.violet)),
            style = Stroke(width = w * 0.065f, join = StrokeJoin.Round)
        )

        // Geometric "T" + "K" interlocking monogram
        val strokeW = w * 0.085f
        val monoBrush = Brush.linearGradient(listOf(p.cyan, p.text))
        // T crossbar + stem
        drawLine(
            brush = monoBrush,
            start = Offset(cx - r * 0.48f, cy - r * 0.34f),
            end = Offset(cx + r * 0.48f, cy - r * 0.34f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            brush = monoBrush,
            start = Offset(cx - r * 0.14f, cy - r * 0.34f),
            end = Offset(cx - r * 0.14f, cy + r * 0.42f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        // K diagonals
        drawLine(
            brush = Brush.linearGradient(listOf(p.primary, p.cyan)),
            start = Offset(cx + r * 0.36f, cy - r * 0.08f),
            end = Offset(cx - r * 0.12f, cy + r * 0.14f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            brush = Brush.linearGradient(listOf(p.primary, p.cyan)),
            start = Offset(cx - r * 0.02f, cy + r * 0.10f),
            end = Offset(cx + r * 0.40f, cy + r * 0.42f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Code-drawn folder icon with animated open-flap motion.
 */
@Composable
fun FolderCanvasIcon(
    isOpen: Boolean,
    tint: Color? = null,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val motion = StudioTheme.motion
    val folderColor = tint ?: p.primary
    val flapFactor by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(durationMillis = motion.standardDurationMs),
        label = "folderFlap"
    )
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Back tab
        val backPath = Path().apply {
            moveTo(w * 0.10f, h * 0.24f)
            lineTo(w * 0.42f, h * 0.24f)
            lineTo(w * 0.50f, h * 0.34f)
            lineTo(w * 0.90f, h * 0.34f)
            lineTo(w * 0.90f, h * 0.82f)
            lineTo(w * 0.10f, h * 0.82f)
            close()
        }
        drawPath(backPath, color = folderColor.copy(alpha = 0.55f))

        // Inner document sheet peeking out when open
        if (flapFactor > 0.05f) {
            drawRoundRect(
                color = p.cyan.copy(alpha = 0.8f * flapFactor),
                topLeft = Offset(w * 0.22f, h * (0.30f - 0.08f * flapFactor)),
                size = Size(w * 0.56f, h * 0.28f),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }

        // Front flap tilts down slightly on open
        val skew = w * 0.08f * flapFactor
        val topY = h * (0.38f + 0.06f * flapFactor)
        val frontPath = Path().apply {
            moveTo(w * 0.10f - skew * 0.5f, topY)
            lineTo(w * 0.90f + skew * 0.5f, topY)
            lineTo(w * 0.88f, h * 0.84f)
            lineTo(w * 0.12f, h * 0.84f)
            close()
        }
        drawPath(
            path = frontPath,
            brush = Brush.verticalGradient(listOf(folderColor.copy(alpha = 0.95f), folderColor.copy(alpha = 0.7f)))
        )
        drawPath(
            path = frontPath,
            color = p.highlight,
            style = Stroke(width = 1.5f)
        )
    }
}

/**
 * Code-drawn file icon badge styled by extension.
 */
@Composable
fun FileTypeCanvasBadge(
    extension: String,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val accent = when (extension.lowercase()) {
        "html", "htm" -> p.warning
        "css", "scss" -> p.cyan
        "js", "ts" -> p.success
        "kt", "java" -> p.violet
        "json", "xml" -> p.primary
        "apk", "zip", "tkbak", "tkenc" -> p.error
        else -> p.textSecondary
    }
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val fold = w * 0.26f
        val docPath = Path().apply {
            moveTo(w * 0.16f, h * 0.08f)
            lineTo(w * 0.84f - fold, h * 0.08f)
            lineTo(w * 0.84f, h * 0.08f + fold)
            lineTo(w * 0.84f, h * 0.92f)
            lineTo(w * 0.16f, h * 0.92f)
            close()
        }
        drawPath(docPath, color = accent.copy(alpha = 0.16f))
        drawPath(docPath, color = accent, style = Stroke(width = 2.2f, join = StrokeJoin.Round))
        // Corner fold
        val foldPath = Path().apply {
            moveTo(w * 0.84f - fold, h * 0.08f)
            lineTo(w * 0.84f - fold, h * 0.08f + fold)
            lineTo(w * 0.84f, h * 0.08f + fold)
        }
        drawPath(foldPath, color = accent, style = Stroke(width = 1.8f))
        // Code lines inside doc
        drawLine(accent, Offset(w * 0.28f, h * 0.46f), Offset(w * 0.70f, h * 0.46f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawLine(accent.copy(alpha = 0.7f), Offset(w * 0.28f, h * 0.60f), Offset(w * 0.58f, h * 0.60f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawLine(accent.copy(alpha = 0.5f), Offset(w * 0.28f, h * 0.74f), Offset(w * 0.64f, h * 0.74f), strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
}

/**
 * Code-drawn empty state vector artwork (concentric blueprint rings + workspace brackets).
 */
@Composable
fun EmptyStateCanvasArt(
    size: Dp = 88.dp,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        drawCircle(
            brush = Brush.radialGradient(listOf(p.primary.copy(alpha = 0.22f), Color.Transparent)),
            radius = w * 0.48f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = p.border,
            radius = w * 0.36f,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = p.cyan.copy(alpha = 0.5f),
            radius = w * 0.22f,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
        // Code brackets < />
        val s = w * 0.14f
        drawLine(p.primary, Offset(cx - s * 1.1f, cy), Offset(cx - s * 0.55f, cy - s * 0.6f), 3.5f, StrokeCap.Round)
        drawLine(p.primary, Offset(cx - s * 1.1f, cy), Offset(cx - s * 0.55f, cy + s * 0.6f), 3.5f, StrokeCap.Round)
        drawLine(p.cyan, Offset(cx + s * 1.1f, cy), Offset(cx + s * 0.55f, cy - s * 0.6f), 3.5f, StrokeCap.Round)
        drawLine(p.cyan, Offset(cx + s * 1.1f, cy), Offset(cx + s * 0.55f, cy + s * 0.6f), 3.5f, StrokeCap.Round)
        drawLine(p.text, Offset(cx + s * 0.15f, cy - s * 0.65f), Offset(cx - s * 0.15f, cy + s * 0.65f), 3f, StrokeCap.Round)
    }
}

/**
 * Code-drawn interactive architecture diagrams for Tutorial lessons.
 */
@Composable
fun TutorialDiagramCanvas(
    diagramType: TutorialDiagramType,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    Canvas(modifier = modifier) {
        val w = this.size.width
        val h = this.size.height
        val nodeW = w * 0.24f
        val nodeH = h * 0.36f
        val yMid = h * 0.5f - nodeH / 2f

        fun drawNode(x: Float, y: Float, accent: Color) {
            drawRoundRect(
                color = accent.copy(alpha = 0.18f),
                topLeft = Offset(x, y),
                size = Size(nodeW, nodeH),
                cornerRadius = CornerRadius(14f, 14f)
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(x, y),
                size = Size(nodeW, nodeH),
                cornerRadius = CornerRadius(14f, 14f),
                style = Stroke(width = 2.5f)
            )
            drawCircle(accent, radius = 5f, center = Offset(x + 14f, y + 14f))
        }

        fun drawArrow(x1: Float, x2: Float, y: Float, color: Color) {
            drawLine(color, Offset(x1, y), Offset(x2, y), strokeWidth = 3f, cap = StrokeCap.Round)
            drawLine(color, Offset(x2 - 10f, y - 7f), Offset(x2, y), strokeWidth = 3f, cap = StrokeCap.Round)
            drawLine(color, Offset(x2 - 10f, y + 7f), Offset(x2, y), strokeWidth = 3f, cap = StrokeCap.Round)
        }

        when (diagramType) {
            TutorialDiagramType.WORKSPACE_FLOW -> {
                drawNode(w * 0.06f, yMid, p.primary)
                drawArrow(w * 0.31f, w * 0.37f, h * 0.5f, p.cyan)
                drawNode(w * 0.38f, yMid, p.cyan)
                drawArrow(w * 0.63f, w * 0.69f, h * 0.5f, p.success)
                drawNode(w * 0.70f, yMid, p.success)
            }
            TutorialDiagramType.LIVE_CODING_SPLIT -> {
                // Split editor | preview pane diagram
                drawRoundRect(
                    color = p.primary.copy(alpha = 0.16f),
                    topLeft = Offset(w * 0.08f, h * 0.16f),
                    size = Size(w * 0.38f, h * 0.68f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                drawRoundRect(
                    color = p.primary,
                    topLeft = Offset(w * 0.08f, h * 0.16f),
                    size = Size(w * 0.38f, h * 0.68f),
                    cornerRadius = CornerRadius(14f, 14f),
                    style = Stroke(2.5f)
                )
                for (i in 0..3) {
                    drawLine(p.cyan, Offset(w * 0.13f, h * (0.28f + i * 0.13f)), Offset(w * (0.34f - i * 0.03f), h * (0.28f + i * 0.13f)), 3f, StrokeCap.Round)
                }
                drawArrow(w * 0.47f, w * 0.53f, h * 0.5f, p.cyan)
                drawRoundRect(
                    color = p.success.copy(alpha = 0.14f),
                    topLeft = Offset(w * 0.54f, h * 0.16f),
                    size = Size(w * 0.38f, h * 0.68f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                drawRoundRect(
                    color = p.success,
                    topLeft = Offset(w * 0.54f, h * 0.16f),
                    size = Size(w * 0.38f, h * 0.68f),
                    cornerRadius = CornerRadius(14f, 14f),
                    style = Stroke(2.5f)
                )
                drawCircle(p.success, radius = h * 0.14f, center = Offset(w * 0.73f, h * 0.5f), style = Stroke(3f))
            }
            TutorialDiagramType.APK_ANATOMY -> {
                val boxW = w * 0.76f
                val left = w * 0.12f
                listOf(p.primary, p.cyan, p.violet).forEachIndexed { idx, col ->
                    val top = h * (0.14f + idx * 0.26f)
                    drawRoundRect(col.copy(alpha = 0.18f), Offset(left, top), Size(boxW, h * 0.18f), CornerRadius(10f, 10f))
                    drawRoundRect(col, Offset(left, top), Size(boxW, h * 0.18f), CornerRadius(10f, 10f), style = Stroke(2f))
                }
            }
            TutorialDiagramType.SIGNING_VAULT -> {
                drawCircle(p.warning.copy(alpha = 0.2f), radius = h * 0.34f, center = Offset(w * 0.34f, h * 0.5f))
                drawCircle(p.warning, radius = h * 0.34f, center = Offset(w * 0.34f, h * 0.5f), style = Stroke(3f))
                drawArrow(w * 0.49f, w * 0.60f, h * 0.5f, p.success)
                drawNode(w * 0.62f, yMid, p.success)
            }
            TutorialDiagramType.GITHUB_CI_PIPELINE -> {
                drawNode(w * 0.06f, yMid, p.violet)
                drawArrow(w * 0.31f, w * 0.37f, h * 0.5f, p.primary)
                drawNode(w * 0.38f, yMid, p.primary)
                drawArrow(w * 0.63f, w * 0.69f, h * 0.5f, p.cyan)
                drawNode(w * 0.70f, yMid, p.cyan)
            }
            TutorialDiagramType.BACKUP_ROLLBACK -> {
                drawNode(w * 0.10f, yMid, p.cyan)
                drawArrow(w * 0.36f, w * 0.62f, h * 0.36f, p.success)
                drawArrow(w * 0.62f, w * 0.36f, h * 0.64f, p.warning)
                drawNode(w * 0.66f, yMid, p.violet)
            }
        }
    }
}

/**
 * Code-drawn geometric iconography for the 5 bottom navigation destinations.
 * Supports selected filled/glow states and unselected outlined states.
 */
@Composable
fun NavTabCanvasIcon(
    route: String,
    selected: Boolean,
    tint: Color,
    contentDescription: String,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.095f

        when (route) {
            "home" -> {
                val house = Path().apply {
                    moveTo(w * 0.50f, h * 0.12f)
                    lineTo(w * 0.88f, h * 0.44f)
                    lineTo(w * 0.80f, h * 0.44f)
                    lineTo(w * 0.80f, h * 0.86f)
                    lineTo(w * 0.20f, h * 0.86f)
                    lineTo(w * 0.20f, h * 0.44f)
                    lineTo(w * 0.12f, h * 0.44f)
                    close()
                }
                if (selected) {
                    drawPath(house, color = tint.copy(alpha = 0.24f))
                }
                drawPath(house, color = tint, style = Stroke(width = strokeW, join = StrokeJoin.Round))
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.41f, h * 0.56f),
                    size = Size(w * 0.18f, h * 0.30f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
            }
            "project" -> {
                val folder = Path().apply {
                    moveTo(w * 0.12f, h * 0.24f)
                    lineTo(w * 0.44f, h * 0.24f)
                    lineTo(w * 0.52f, h * 0.34f)
                    lineTo(w * 0.88f, h * 0.34f)
                    lineTo(w * 0.88f, h * 0.82f)
                    lineTo(w * 0.12f, h * 0.82f)
                    close()
                }
                if (selected) {
                    drawPath(folder, color = tint.copy(alpha = 0.24f))
                }
                drawPath(folder, color = tint, style = Stroke(width = strokeW, join = StrokeJoin.Round))
                drawLine(tint, Offset(w * 0.26f, h * 0.56f), Offset(w * 0.74f, h * 0.56f), strokeW, StrokeCap.Round)
            }
            "live_coding" -> {
                if (selected) {
                    drawRoundRect(
                        color = tint.copy(alpha = 0.16f),
                        topLeft = Offset(w * 0.08f, h * 0.16f),
                        size = Size(w * 0.84f, h * 0.68f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
                // <
                drawLine(tint, Offset(w * 0.34f, h * 0.30f), Offset(w * 0.16f, h * 0.50f), strokeW, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.34f, h * 0.70f), strokeW, StrokeCap.Round)
                // /
                drawLine(tint, Offset(w * 0.56f, h * 0.24f), Offset(w * 0.44f, h * 0.76f), strokeW, StrokeCap.Round)
                // >
                drawLine(tint, Offset(w * 0.66f, h * 0.30f), Offset(w * 0.84f, h * 0.50f), strokeW, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.84f, h * 0.50f), Offset(w * 0.66f, h * 0.70f), strokeW, StrokeCap.Round)
            }
            "apk_tool" -> {
                val shield = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.84f, h * 0.24f)
                    lineTo(w * 0.80f, h * 0.60f)
                    lineTo(w * 0.50f, h * 0.88f)
                    lineTo(w * 0.20f, h * 0.60f)
                    lineTo(w * 0.16f, h * 0.24f)
                    close()
                }
                if (selected) {
                    drawPath(shield, color = tint.copy(alpha = 0.24f))
                }
                drawPath(shield, color = tint, style = Stroke(width = strokeW, join = StrokeJoin.Round))
                drawLine(tint, Offset(w * 0.36f, h * 0.50f), Offset(w * 0.47f, h * 0.62f), strokeW, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.47f, h * 0.62f), Offset(w * 0.66f, h * 0.39f), strokeW, StrokeCap.Round)
            }
            else -> {
                // Profile emblem
                if (selected) {
                    drawCircle(tint.copy(alpha = 0.20f), radius = w * 0.40f, center = Offset(w * 0.5f, h * 0.5f))
                }
                drawCircle(tint, radius = w * 0.16f, center = Offset(w * 0.50f, h * 0.34f), style = Stroke(strokeW))
                drawArc(
                    color = tint,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * 0.20f, h * 0.54f),
                    size = Size(w * 0.60f, h * 0.44f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
        }
    }
}

