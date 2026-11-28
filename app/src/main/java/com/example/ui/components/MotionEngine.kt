package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.BuildStage
import com.example.ui.theme.StudioTheme

/**
 * Luminous circuit/core pipeline animation that tracks actual BuildStage progression.
 */
@Composable
fun CircuitBuildLoader(
    activeStage: BuildStage,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val motion = StudioTheme.motion
    val stages = listOf(
        BuildStage.PREPARING,
        BuildStage.SCANNING,
        BuildStage.VALIDATING,
        BuildStage.PACKAGING,
        BuildStage.VERIFYING
    )
    val activeIdx = stages.indexOf(activeStage).let { idx ->
        when {
            activeStage == BuildStage.DONE -> stages.size
            activeStage == BuildStage.COMPILING -> 3
            idx >= 0 -> idx
            else -> 0
        }
    }

    val pulse = if (isRunning && motion.pulseEnabled) {
        val inf = rememberInfiniteTransition(label = "circuitPulse")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "pulseVal"
        ).value
    } else 0.5f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
    ) {
        val w = size.width
        val h = size.height
        val padX = 28f
        val stepX = (w - padX * 2) / (stages.size - 1).coerceAtLeast(1)
        val cy = h / 2f

        // Base circuit bus trace
        drawLine(
            color = p.border,
            start = Offset(padX, cy),
            end = Offset(w - padX, cy),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )

        // Energized trace up to active stage
        val activeEndX = if (activeIdx >= stages.size) w - padX else padX + stepX * activeIdx
        drawLine(
            brush = Brush.horizontalGradient(listOf(p.primary, p.cyan, p.success)),
            start = Offset(padX, cy),
            end = Offset(activeEndX, cy),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )

        if (isRunning && motion.pulseEnabled && activeIdx < stages.size) {
            val packetX = padX + (activeEndX - padX) * pulse
            drawCircle(
                color = p.cyan,
                radius = 7f,
                center = Offset(packetX, cy)
            )
        }

        // Circuit nodes
        stages.forEachIndexed { idx, _ ->
            val nx = padX + stepX * idx
            val completed = idx < activeIdx || activeStage == BuildStage.DONE
            val current = idx == activeIdx && isRunning
            val nodeColor = when {
                activeStage == BuildStage.FAILED && idx == activeIdx -> p.error
                completed -> p.success
                current -> p.cyan
                else -> p.raised
            }
            if (current && motion.pulseEnabled) {
                drawCircle(
                    color = p.cyan.copy(alpha = 0.28f * (1f - pulse)),
                    radius = 16f + 10f * pulse,
                    center = Offset(nx, cy)
                )
            }
            drawCircle(
                color = p.card,
                radius = 13f,
                center = Offset(nx, cy)
            )
            drawCircle(
                color = nodeColor,
                radius = 11f,
                center = Offset(nx, cy),
                style = Stroke(width = 3.5f)
            )
            if (completed) {
                drawCircle(color = p.success, radius = 4.5f, center = Offset(nx, cy))
            }
        }
    }
}

/**
 * Scanning beam overlay sweeping across file rows during real scan tasks.
 */
@Composable
fun ScannerBeamBar(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val motion = StudioTheme.motion
    if (!isScanning) return

    val beam = if (motion.pulseEnabled) {
        val inf = rememberInfiniteTransition(label = "scanBeam")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
            label = "beamPos"
        ).value
    } else 0.5f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        drawRoundRect(
            color = p.raised,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(6f, 6f)
        )
        val beamW = size.width * 0.28f
        val startX = (size.width - beamW) * beam
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, p.cyan, p.primary, Color.Transparent)),
            topLeft = Offset(startX, 0f),
            size = Size(beamW, size.height),
            cornerRadius = CornerRadius(6f, 6f)
        )
    }
}

/**
 * Code-drawn key ring / secure vault motion for Signing Wizard.
 */
@Composable
fun SigningVaultCanvas(
    isLocked: Boolean,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val motion = StudioTheme.motion
    val ringAngle = if (!isLocked && motion.pulseEnabled) {
        val inf = rememberInfiniteTransition(label = "keyRing")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
            label = "angle"
        ).value
    } else 0f

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = minOf(cx, cy) * 0.78f
        rotate(degrees = ringAngle, pivot = Offset(cx, cy)) {
            drawArc(
                brush = Brush.sweepGradient(listOf(p.primary, p.cyan, p.warning, p.primary)),
                startAngle = 0f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 4.5f, cap = StrokeCap.Round)
            )
        }
        val lockColor = if (isLocked) p.success else p.warning
        drawRoundRect(
            color = lockColor.copy(alpha = 0.18f),
            topLeft = Offset(cx - r * 0.36f, cy - r * 0.15f),
            size = Size(r * 0.72f, r * 0.56f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = lockColor,
            topLeft = Offset(cx - r * 0.36f, cy - r * 0.15f),
            size = Size(r * 0.72f, r * 0.56f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(2.5f)
        )
        drawArc(
            color = lockColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - r * 0.22f, cy - r * 0.45f),
            size = Size(r * 0.44f, r * 0.52f),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Draws verified checkmark outline ONLY after real verification passes.
 */
@Composable
fun VerificationCheckCanvas(
    verified: Boolean,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val motion = StudioTheme.motion
    val progress by animateFloatAsState(
        targetValue = if (verified) 1f else 0f,
        animationSpec = tween(durationMillis = if (motion.reduceMotion) 0 else 260),
        label = "verifyDraw"
    )
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val col = if (verified) p.success else p.textSecondary.copy(alpha = 0.45f)
        drawCircle(
            color = col.copy(alpha = 0.15f),
            radius = w * 0.42f,
            center = Offset(w / 2f, h / 2f)
        )
        drawCircle(
            color = col,
            radius = w * 0.42f,
            center = Offset(w / 2f, h / 2f),
            style = Stroke(2.5f)
        )
        if (progress > 0.05f) {
            val check = Path().apply {
                moveTo(w * 0.30f, h * 0.52f)
                lineTo(w * 0.45f, h * 0.67f)
                if (progress > 0.45f) {
                    val t = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    lineTo(w * (0.45f + 0.27f * t), h * (0.67f - 0.31f * t))
                }
            }
            drawPath(check, color = p.success, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Backup vault layered disk animation.
 */
@Composable
fun BackupVaultCanvas(
    isActive: Boolean,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val motion = StudioTheme.motion
    val shift = if (isActive && motion.pulseEnabled) {
        val inf = rememberInfiniteTransition(label = "vaultLayer")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "layerShift"
        ).value
    } else 0f

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val colors = listOf(p.violet, p.primary, p.cyan)
        colors.forEachIndexed { idx, col ->
            val y = h * (0.20f + idx * 0.22f) - if (idx == 0) shift else 0f
            drawRoundRect(
                color = col.copy(alpha = 0.20f),
                topLeft = Offset(w * 0.14f, y),
                size = Size(w * 0.72f, h * 0.20f),
                cornerRadius = CornerRadius(10f, 10f)
            )
            drawRoundRect(
                color = col,
                topLeft = Offset(w * 0.14f, y),
                size = Size(w * 0.72f, h * 0.20f),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(2.2f)
            )
        }
    }
}
