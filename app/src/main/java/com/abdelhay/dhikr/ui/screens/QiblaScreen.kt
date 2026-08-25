package com.abdelhay.dhikr.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdelhay.dhikr.prayer.QiblaCompass
import com.abdelhay.dhikr.prayer.QiblaReading
import com.abdelhay.dhikr.util.toArabicDigits
import com.abdelhay.dhikr.vm.PrayerViewModel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(vm: PrayerViewModel, onBack: () -> Unit) {

    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ar = settings.arabicNumerals

    var reading by remember { mutableStateOf<QiblaReading?>(null) }

    DisposableEffect(settings.latitude, settings.longitude) {
        if (!settings.hasLocation) return@DisposableEffect onDispose { }
        val compass = QiblaCompass(
            context, settings.latitude, settings.longitude
        ) { reading = it }
        compass.start()
        onDispose { compass.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اتجاه القبلة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            if (!settings.hasLocation) {
                Text(
                    "حدّد موقعك أولًا من شاشة المواقيت.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                return@Column
            }

            val r = reading

            if (r != null && !r.hasSensor) {
                Text(
                    "لا يوجد مستشعر بوصلة في هذا الجهاز.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "اتجاه القبلة من موقعك ${r.qiblaBearing.toInt().toArabicDigits(ar)}° " +
                        "من الشمال. استعن ببوصلة أخرى أو بخريطة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                return@Column
            }

            // تنبيه المعايرة — يظهر فقط حين تسوء دقّة المستشعر
            if (r != null && r.accuracy.needsCalibration) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("البوصلة تحتاج معايرة", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "حرّك الهاتف في الهواء على شكل الرقم ٨ مرّتين أو ثلاثًا، " +
                                "وابتعد عن المعادن والشواحن والحاسوب.",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            CompassRose(
                heading = r?.heading ?: 0f,
                needleAngle = r?.needleAngle ?: 0f,
                aligned = r?.isAligned == true,
                arabicDigits = ar
            )

            Spacer(Modifier.height(28.dp))

            if (r != null) {
                Text(
                    if (r.isAligned) "أنت الآن مستقبل القبلة" else "أدر الهاتف حتى تنطبق الإبرة على العلامة",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (r.isAligned) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "القبلة على ${r.qiblaBearing.toInt().toArabicDigits(ar)}° من الشمال" +
                        " • دقّة المستشعر ${r.accuracy.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "تبعد عن الكعبة نحو " +
                        "${QiblaCompass.distanceToKaaba(settings.latitude, settings.longitude)
                            .toArabicDigits(ar)} كم",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                CircularProgressIndicator()
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "ضع الهاتف مستويًا أفقيًّا لتصحّ القراءة.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CompassRose(
    heading: Float,
    needleAngle: Float,
    aligned: Boolean,
    arabicDigits: Boolean
) {
    // ندوّر باتجاه أقصر مسافة حتى لا تلفّ البوصلة دورة كاملة عند العبور من ٣٥٩ إلى ١
    var continuousHeading by remember { mutableFloatStateOf(heading) }
    LaunchedEffect(heading) {
        var delta = heading - (continuousHeading % 360f + 360f) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        continuousHeading += delta
    }
    val animatedHeading by animateFloatAsState(continuousHeading, tween(120), label = "heading")

    val ring = MaterialTheme.colorScheme.surfaceVariant
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant
    val qiblaColor = if (aligned) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.primary
    val northColor = MaterialTheme.colorScheme.error

    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(300.dp)) {
            val radius = min(size.width, size.height) / 2f - 12f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(color = ring, radius = radius, center = center, style = Stroke(width = 3f))
            drawCircle(
                color = ring, radius = radius * 0.72f, center = center,
                style = Stroke(width = 1.5f)
            )

            // قرص البوصلة يدور عكس اتجاه الجهاز، فيبقى الشمال شمالًا
            rotate(degrees = -animatedHeading, pivot = center) {
                for (deg in 0 until 360 step 15) {
                    val major = deg % 90 == 0
                    val a = Math.toRadians(deg - 90.0)
                    val outer = radius
                    val inner = radius - if (major) 22f else 10f
                    drawLine(
                        color = if (deg == 0) northColor else markColor,
                        start = Offset(
                            center.x + inner * cos(a).toFloat(),
                            center.y + inner * sin(a).toFloat()
                        ),
                        end = Offset(
                            center.x + outer * cos(a).toFloat(),
                            center.y + outer * sin(a).toFloat()
                        ),
                        strokeWidth = if (major) 4f else 2f
                    )
                }

                // علامة القبلة على الحلقة
                val qa = Math.toRadians(needleAngle + animatedHeading - 90.0)
                drawCircle(
                    color = qiblaColor,
                    radius = 13f,
                    center = Offset(
                        center.x + (radius - 34f) * cos(qa).toFloat(),
                        center.y + (radius - 34f) * sin(qa).toFloat()
                    )
                )
            }

            // إبرة القبلة الثابتة في المركز، تشير دائمًا إلى الكعبة
            rotate(degrees = needleAngle, pivot = center) {
                val needle = Path().apply {
                    moveTo(center.x, center.y - radius * 0.66f)
                    lineTo(center.x - 16f, center.y + 14f)
                    lineTo(center.x + 16f, center.y + 14f)
                    close()
                }
                drawPath(needle, color = qiblaColor)
                drawCircle(color = qiblaColor, radius = 9f, center = center)
            }

            // مؤشّر ثابت أعلى الشاشة يمثّل اتجاه رأس الهاتف
            drawLine(
                color = markColor,
                start = Offset(center.x, center.y - radius - 10f),
                end = Offset(center.x, center.y - radius + 14f),
                strokeWidth = 5f
            )
        }
    }
}
