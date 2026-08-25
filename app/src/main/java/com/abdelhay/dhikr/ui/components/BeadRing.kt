package com.abdelhay.dhikr.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * العنصر المميّز للتطبيق: حلقة الخرز.
 * حين يكون الهدف صغيرًا (٩٩ فأقل) تُرسم كل عدّة خرزةً مستقلة كما في السبحة،
 * فيرى المستخدم موضعه في الطوق لا نسبة مئوية مجرّدة.
 * وللأهداف الكبيرة تتحوّل إلى قوس متصل مع خرزة متحركة على طرفه.
 */
@Composable
fun BeadRing(
    progress: Float,
    target: Int,
    done: Int,
    modifier: Modifier = Modifier,
    size: Dp = 300.dp,
    beadColor: Color,
    trackColor: Color,
    completeColor: Color,
    isComplete: Boolean,
    content: @Composable () -> Unit
) {
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(220),
        label = "progress"
    )
    val filled = if (isComplete) completeColor else beadColor

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val radius = min(this.size.width, this.size.height) / 2f - 22f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            if (target in 1..99) {
                val beadRadius = (2f * PI * radius / target / 2.6f)
                    .toFloat().coerceIn(3f, 14f)
                for (i in 0 until target) {
                    // نبدأ من أعلى الدائرة ونسير عكس عقارب الساعة موافقةً لاتجاه القراءة العربية
                    val angle = (-PI / 2 - 2 * PI * i / target).toFloat()
                    val p = Offset(
                        center.x + radius * cos(angle),
                        center.y + radius * sin(angle)
                    )
                    val isDone = i < done
                    val isCurrent = i == done - 1
                    drawCircle(
                        color = if (isDone) filled else trackColor,
                        radius = if (isCurrent) beadRadius * 1.55f else beadRadius,
                        center = p
                    )
                }
            } else {
                val stroke = 12f
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = filled,
                    startAngle = -90f,
                    sweepAngle = -360f * animated,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = stroke)
                )
                val head = (-PI / 2 - 2 * PI * animated).toFloat()
                drawCircle(
                    color = filled,
                    radius = 13f,
                    center = Offset(center.x + radius * cos(head), center.y + radius * sin(head))
                )
            }
        }
        content()
    }
}
