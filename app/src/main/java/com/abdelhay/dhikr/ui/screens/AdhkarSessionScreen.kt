package com.abdelhay.dhikr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdelhay.dhikr.data.AdhkarSet
import com.abdelhay.dhikr.data.Preset
import androidx.compose.ui.text.style.TextAlign
import com.abdelhay.dhikr.ui.theme.quranTextStyle
import com.abdelhay.dhikr.util.Haptics
import com.abdelhay.dhikr.util.toArabicDigits
import com.abdelhay.dhikr.vm.AdhkarSessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhkarSessionScreen(
    vm: AdhkarSessionViewModel,
    set: AdhkarSet,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    DisposableEffect(Unit) { onDispose { haptics.release() } }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val ar = settings.arabicNumerals

    LaunchedEffect(set) { vm.load(set) }

    val items = remember(set) { set.presets() }
    val done = items.indices.count { (counts.getOrNull(it) ?: 0) >= items[it].target }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(set.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.reset(set) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "إعادة الورد")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {

            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    if (done == items.size) "أتممتَ ${set.title}، تقبّل الله"
                    else "${done.toArabicDigits(ar)} من ${items.size.toArabicDigits(ar)}",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (items.isEmpty()) 0f else done.toFloat() / items.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    set.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(items) { i, preset ->
                    AdhkarCard(
                        preset = preset,
                        count = counts.getOrNull(i) ?: 0,
                        arabicDigits = ar,
                        fontScale = settings.fontScale,
                        onTap = {
                            val current = counts.getOrNull(i) ?: 0
                            if (current < preset.target) {
                                vm.increment(set, i, items.size, preset.target)
                                if (settings.vibrate) {
                                    if (current + 1 >= preset.target) haptics.complete() else haptics.tick()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdhkarCard(
    preset: Preset,
    count: Int,
    arabicDigits: Boolean,
    fontScale: Float,
    onTap: () -> Unit
) {
    val complete = count >= preset.target
    val remaining = (preset.target - count).coerceAtLeast(0)

    Surface(
        onClick = onTap,
        shape = RoundedCornerShape(18.dp),
        color = if (complete) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        tonalElevation = if (complete) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = preset.text,
                style = quranTextStyle(18f * fontScale, TextAlign.Start),
                color = if (complete) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )

            if (!preset.note.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    preset.note!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!preset.source.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    preset.source!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (complete) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "تمّ",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "تمّ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    // عدّاد دائري صغير: اضغط البطاقة ليزيد
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            remaining.toArabicDigits(arabicDigits),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (preset.target > 1)
                            "المتبقّي من ${preset.target.toArabicDigits(arabicDigits)}"
                        else "اضغط بعد قراءته",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
