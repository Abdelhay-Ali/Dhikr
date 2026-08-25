package com.abdelhay.dhikr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdelhay.dhikr.util.humanDuration
import com.abdelhay.dhikr.util.toArabicDigits
import com.abdelhay.dhikr.vm.DhikrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: DhikrViewModel, onBack: () -> Unit) {
    val days by vm.daySummaries.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val lifetime by vm.lifetimeTotal.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val secondsToday by vm.secondsToday.collectAsStateWithLifecycle()
    val secondsWeek by vm.secondsLast7Days.collectAsStateWithLifecycle()
    val secondsTotal by vm.secondsTotal.collectAsStateWithLifecycle()
    val ar = settings.arabicNumerals

    val recent = days.take(30).reversed()
    val max = (recent.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإحصاءات") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox("المواظبة", "${streak.toArabicDigits(ar)} يوم", Modifier.weight(1f))
                StatBox("مجموع العدّ", lifetime.toArabicDigits(ar), Modifier.weight(1f))
            }

            Text("زمن الذكر", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox("اليوم", humanDuration(secondsToday, ar), Modifier.weight(1f))
                StatBox("آخر ٧ أيام", humanDuration(secondsWeek, ar), Modifier.weight(1f))
            }
            StatBox("منذ البداية", humanDuration(secondsTotal, ar), Modifier.fillMaxWidth())
            Text(
                "المؤقّت يتوقّف تلقائيًا بعد دقيقتين بلا ضغط، فالرقم يعبّر عن وقت الذكر فعلًا لا عن وقت فتح الشاشة.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("آخر ثلاثين يومًا", style = MaterialTheme.typography.titleLarge)

            if (recent.isEmpty()) {
                Text(
                    "لا يوجد سجل بعد. سيظهر هنا بعد أول يوم من الورد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    recent.forEach { day ->
                        val h = (day.total.toFloat() / max).coerceIn(0.02f, 1f)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(h)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (day.isFullDay) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                )
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "العمود الأخضر يعني اكتمال الورد كاملًا في ذلك اليوم.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Text("تفصيل الأيام", style = MaterialTheme.typography.titleLarge)
            days.take(14).forEach { day ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(day.date, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${day.completedCount.toArabicDigits(ar)}/${day.dhikrCount.toArabicDigits(ar)} • " +
                            day.total.toArabicDigits(ar),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
