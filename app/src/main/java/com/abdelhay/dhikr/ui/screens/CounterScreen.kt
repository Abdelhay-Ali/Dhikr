package com.abdelhay.dhikr.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdelhay.dhikr.data.CountMode
import com.abdelhay.dhikr.ui.components.BeadRing
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.abdelhay.dhikr.ui.theme.quranTextStyle
import com.abdelhay.dhikr.util.Haptics
import com.abdelhay.dhikr.util.formatClock
import com.abdelhay.dhikr.util.VolumeKeyBus
import com.abdelhay.dhikr.util.toArabicDigits
import com.abdelhay.dhikr.vm.DhikrViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterScreen(
    vm: DhikrViewModel,
    dhikrId: Long,
    onBack: () -> Unit,
    onSwitch: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    DisposableEffect(Unit) { onDispose { haptics.release() } }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val list by vm.activeAdhkar.collectAsStateWithLifecycle()
    val dhikr by vm.observe(dhikrId).collectAsStateWithLifecycle(initialValue = null)

    val ar = settings.arabicNumerals
    var pulse by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pulse) 1.06f else 1f, tween(90), label = "pulse")

    // شاشة العدّاد وحدها هي التي تلتقط أزرار الصوت
    DisposableEffect(Unit) {
        VolumeKeyBus.active = true
        onDispose { VolumeKeyBus.active = false }
    }

    // المؤقّت يبدأ بدخول الشاشة ويتوقّف بمغادرتها أو بخروج التطبيق إلى الخلفية
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, dhikrId) {
        vm.enterSession(dhikrId)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.enterSession(dhikrId)
                Lifecycle.Event.ON_PAUSE -> vm.leaveSession()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.leaveSession()
        }
    }

    val elapsed by vm.session.elapsed.collectAsStateWithLifecycle()
    val timerRunning by vm.session.running.collectAsStateWithLifecycle()

    val count: () -> Unit = {
        if (dhikr != null && !(dhikr!!.isCompleted && dhikr!!.mode == CountMode.DOWN && dhikr!!.raw == 0)) {
            vm.increment(dhikrId)
            if (settings.vibrate) haptics.tick()
            if (settings.clickSound) haptics.click()
            pulse = true
        }
    }

    LaunchedEffect(pulse) { if (pulse) { kotlinx.coroutines.delay(90); pulse = false } }

    // rememberUpdatedState حتى لا يلتقط المستمع نسخة قديمة من حالة الذكر
    val currentCount by rememberUpdatedState(count)
    LaunchedEffect(Unit) {
        VolumeKeyBus.events.collectLatest { if (VolumeKeyBus.active) currentCount() }
    }

    // عند الإتمام: اهتزاز مميّز ثم الانتقال إلى الذكر التالي غير المكتمل
    LaunchedEffect(dhikrId) {
        vm.completed.collectLatest { id ->
            if (id != dhikrId) return@collectLatest
            if (settings.vibrate) haptics.complete()
            if (settings.autoAdvance) {
                kotlinx.coroutines.delay(700)
                val next = list.firstOrNull { !it.isCompleted && it.id != dhikrId }
                if (next != null) onSwitch(next.id)
            }
        }
    }

    val d = dhikr
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SessionClock(
                        seconds = elapsed,
                        running = timerRunning,
                        arabicDigits = ar
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.undo(dhikrId) }) {
                        Icon(Icons.Outlined.Undo, contentDescription = "تراجع عن آخر عدّة")
                    }
                    IconButton(onClick = { vm.resetOne(dhikrId) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "تصفير هذا الذكر")
                    }
                }
            )
        }
    ) { padding ->
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // منطقة العدّ هي الشاشة كلها: لا يحتاج المستخدم تصويب إبهامه على زر صغير
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = count
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    // الذكر أولى بالمساحة من العدّاد: نصف الشاشة له، والباقي للطوق
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // خطّ المصحف هنا أيضًا: كثير من الأذكار آيات، وعلامات ضبطها
                // لا يُحسن رسمَها خطُّ النظام
                Text(
                    text = d.text,
                    style = quranTextStyle(18f * settings.fontScale, TextAlign.Center),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!d.note.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        d.note!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!d.source.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        d.source!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            BeadRing(
                size = 210.dp,
                progress = d.progress,
                target = d.target,
                done = d.done,
                isComplete = d.isCompleted,
                beadColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                completeColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.scale(scale)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = d.display.toArabicDigits(ar),
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Light,
                        color = if (d.isCompleted) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = when {
                            d.isCompleted -> "تمّ"
                            d.mode == CountMode.DOWN -> "المتبقّي"
                            else -> "من ${d.target.toArabicDigits(ar)}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "اضغط في أي موضع من الشاشة",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.weight(1f))

            // التنقل بين أذكار الورد دون العودة للقائمة
            val index = list.indexOfFirst { it.id == dhikrId }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { list.getOrNull(index - 1)?.let { onSwitch(it.id) } },
                    enabled = index > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    Spacer(Modifier.width(4.dp))
                    Text("السابق")
                }
                Text(
                    text = if (index >= 0)
                        "${(index + 1).toArabicDigits(ar)} / ${list.size.toArabicDigits(ar)}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { list.getOrNull(index + 1)?.let { onSwitch(it.id) } },
                    enabled = index >= 0 && index < list.lastIndex
                ) {
                    Text("التالي")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            }
        }
    }
}

/**
 * ساعة الحلقة. النقطة إلى جانب الرقم تنبض ما دام المؤقّت يعدّ،
 * وتخفت حين يتوقّف لخمول — إشارة صامتة إلى أن الزمن لم يعد يُحسب.
 */
@Composable
private fun SessionClock(seconds: Long, running: Boolean, arabicDigits: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (running) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.outline
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatClock(seconds, arabicDigits),
            style = MaterialTheme.typography.labelMedium,
            color = if (running) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!running && seconds > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "متوقّف",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
