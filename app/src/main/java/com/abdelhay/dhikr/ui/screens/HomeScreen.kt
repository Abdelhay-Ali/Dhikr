package com.abdelhay.dhikr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdelhay.dhikr.data.AdhkarSet
import com.abdelhay.dhikr.data.Dhikr
import com.abdelhay.dhikr.util.humanDuration
import com.abdelhay.dhikr.util.toArabicDigits
import com.abdelhay.dhikr.vm.DhikrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: DhikrViewModel,
    onOpenCounter: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onOpenSet: (AdhkarSet) -> Unit
) {
    val adhkar by vm.activeAdhkar.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val secondsToday by vm.secondsToday.collectAsStateWithLifecycle()

    val doneCount = adhkar.count { it.isCompleted }
    val ar = settings.arabicNumerals

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("وِرْدُ اليوم", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onStats) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "الإحصاءات")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "الإعدادات")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("أضف ذكرًا") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DayHeader(
                    done = doneCount,
                    total = adhkar.size,
                    streak = streak,
                    secondsToday = secondsToday,
                    arabicDigits = ar
                )
            }

            item {
                AdhkarSetButtons(onOpenSet)
            }

            if (adhkar.isEmpty()) {
                item {
                    EmptyState(onAdd)
                }
            }

            items(adhkar, key = { it.id }) { d ->
                DhikrCard(
                    dhikr = d,
                    arabicDigits = ar,
                    onClick = { onOpenCounter(d.id) },
                    onLongClick = { onEdit(d.id) }
                )
            }
        }
    }
}

@Composable
private fun DayHeader(
    done: Int,
    total: Int,
    streak: Int,
    secondsToday: Long,
    arabicDigits: Boolean
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            text = if (total > 0 && done == total) "اكتمل وردك اليوم، تقبّل الله"
            else "أنجزتَ ${done.toArabicDigits(arabicDigits)} من ${total.toArabicDigits(arabicDigits)}",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        val parts = buildList {
            if (secondsToday > 0) add("ذكرتَ اليوم ${humanDuration(secondsToday, arabicDigits)}")
            if (streak > 0) add("مواظبة ${streak.toArabicDigits(arabicDigits)} يومًا")
        }
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" • "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DhikrCard(
    dhikr: Dhikr,
    arabicDigits: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val complete = dhikr.isCompleted
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = dhikr.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                if (complete) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "مكتمل",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${dhikr.done.toArabicDigits(arabicDigits)} / ${dhikr.target.toArabicDigits(arabicDigits)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(dhikr.progress)
                            .fillMaxHeight()
                            .background(
                                if (complete) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onLongClick) { Text("تعديل") }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("لا يوجد ذكر في وردك بعد.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "ابدأ بذكر واحد وعدد صغير تلتزم به.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd) { Text("أضف ذكرًا") }
    }
}

/** أزرار الأوراد الجاهزة فوق قائمة الورد الشخصي. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdhkarSetButtons(onOpenSet: (AdhkarSet) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdhkarSet.entries.forEach { set ->
            Surface(
                onClick = { onOpenSet(set) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(set.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            set.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "وِردك الخاص",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
