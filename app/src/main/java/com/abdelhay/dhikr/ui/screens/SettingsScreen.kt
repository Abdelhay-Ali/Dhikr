package com.abdelhay.dhikr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdelhay.dhikr.vm.DhikrViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: DhikrViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionTitle("العدّ")
            SwitchRow("اهتزاز عند كل عدّة", s.vibrate, { vm.setVibrate(it) })
            SwitchRow("نقرة صوتية", s.clickSound, { vm.setSound(it) })
            SwitchRow("العد بأزرار الصوت", s.volumeKeysCount, { vm.setVolumeKeys(it) },
                sub = "يتيح الذكر والشاشة أمامك دون النظر إليها")
            SwitchRow("إبقاء الشاشة مضاءة أثناء العد", s.keepScreenOn, { vm.setKeepScreenOn(it) })
            SwitchRow("الانتقال تلقائيًا للذكر التالي عند الإتمام", s.autoAdvance, { vm.setAutoAdvance(it) })
            SwitchRow("الأرقام العربية الهندية ٠١٢٣", s.arabicNumerals, { vm.setArabicNumerals(it) })

            Spacer(Modifier.height(8.dp))
            SectionTitle("حجم خط الذكر")
            Slider(
                value = s.fontScale,
                onValueChange = { vm.setFontScale(it) },
                valueRange = 0.8f..1.8f,
                steps = 4
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle("التذكير بالورد")
            SwitchRow("تفعيل التذكير", s.remindersEnabled, { vm.setReminders(it) })

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.reminderTimes.forEach { t ->
                    InputChip(
                        selected = false,
                        onClick = { vm.setReminderTimes(s.reminderTimes - t) },
                        label = { Text(t) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "حذف الموعد") }
                    )
                }
                AssistChip(onClick = { showTimePicker = true }, label = { Text("+ موعد") })
            }
            Text(
                "يظهر في الإشعار نصّ أول ذكر لم يكتمل والمتبقّي منه.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle("بداية اليوم")
            Text(
                "يُصفَّر الورد عند الساعة ${s.dayStartHour}:00 بدل منتصف الليل، فذكر ما بعد العشاء " +
                    "يبقى محسوبًا ليومه.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = s.dayStartHour.toFloat(),
                onValueChange = { vm.setDayStartHour(it.toInt()) },
                valueRange = 0f..12f,
                steps = 11
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle("اللغة والبداية")
            Text(
                "الواجهة تُترجَم، أمّا القرآن والتفسير والأذكار فتبقى بالعربية.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ar" to "العربية", "en" to "English").forEach { (k, label) ->
                    FilterChip(
                        selected = s.language == k,
                        onClick = { vm.setLanguage(k) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("الشاشة التي يفتح عليها التطبيق", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "prayer" to "المواقيت",
                    "wird" to "الورد",
                    "quran" to "المصحف",
                    "radio" to "الإذاعة"
                ).forEach { (k, label) ->
                    FilterChip(
                        selected = s.startTab == k,
                        onClick = { vm.setStartTab(k) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle("المظهر")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "تبع النظام", "light" to "فاتح", "dark" to "داكن").forEach { (k, label) ->
                    val selected = when (s.darkTheme) {
                        null -> k == "system"
                        true -> k == "dark"
                        false -> k == "light"
                    }
                    FilterChip(selected = selected, onClick = { vm.setDark(k) }, label = { Text(label) })
                }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { vm.resetAll() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("تصفير كل الأذكار الآن") }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                val t = String.format("%02d:%02d", h, m)
                if (t !in s.reminderTimes) vm.setReminderTimes((s.reminderTimes + t).sorted())
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    sub: String? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("موعد التذكير") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("إضافة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
