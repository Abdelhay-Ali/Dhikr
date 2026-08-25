package com.abdelhay.dhikr.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdelhay.dhikr.prayer.AdhanChoice
import com.abdelhay.dhikr.prayer.AdhanNotifier
import com.abdelhay.dhikr.prayer.AdhanPreview
import com.abdelhay.dhikr.prayer.PrayerCalculator
import com.abdelhay.dhikr.prayer.PrayerName
import com.abdelhay.dhikr.util.HijriDate
import com.abdelhay.dhikr.util.formatClock
import com.abdelhay.dhikr.util.formatPrayerTime
import com.abdelhay.dhikr.util.toArabicDigits
import com.abdelhay.dhikr.vm.PrayerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrayerScreen(
    vm: PrayerViewModel,
    onRequestLocationPermission: () -> Unit,
    onOpenQibla: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val next by vm.next.collectAsStateWithLifecycle()
    val countdown by vm.countdown.collectAsStateWithLifecycle()
    val since by vm.since.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val ar = settings.arabicNumerals

    var showManual by remember { mutableStateOf(false) }
    var previewKey by remember { mutableStateOf<String?>(null) }

    // نوقف المعاينة عند مغادرة الشاشة حتى لا يستمر الصوت
    DisposableEffect(Unit) { onDispose { AdhanPreview.stop() } }
    var message by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val soundChoices = remember { AdhanNotifier.availableChoices(context) }
    fun fmt(millis: Long) = formatPrayerTime(millis, settings.use12Hour, ar)

    Scaffold(
        topBar = { TopAppBar(title = { Text("مواقيت الصلاة") }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!settings.hasLocation) {
                LocationPrompt(
                    locating = locating,
                    onDetect = {
                        onRequestLocationPermission()
                        vm.detectLocation { ok ->
                            message = if (ok) null
                            else "تعذّر تحديد الموقع. فعّل خدمة الموقع، أو أدخل الإحداثيات يدويًا."
                        }
                    },
                    onManual = { showManual = true }
                )
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                return@Column
            }

            // التاريخ الهجري
            val hijri = remember(settings.hijriOffset) {
                HijriDate.of(offsetDays = settings.hijriOffset)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    HijriDate.format(hijri, ar),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                HijriDate.occasion(hijri)?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // بعد الأذان بنصف ساعة أو أقلّ: نعرض ما مضى عليه لا ما بقي لما بعده
            val elapsed = since
            if (elapsed != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("أُذّن لصلاة", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(elapsed.first.name.label, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            fmt(elapsed.first.timeMillis),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "مضى ${formatClock(elapsed.second, ar)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        next?.let { n ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "و${n.name.label} بعد ${formatClock(countdown, ar)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // العدّ التنازلي للصلاة القادمة
            if (elapsed == null) next?.let { n ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("الصلاة القادمة", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(n.name.label, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            fmt(n.timeMillis),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "بقي ${formatClock(countdown, ar)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // مواقيت اليوم
            today?.let { day ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        day.slots.forEach { slot ->
                            val isNext = next?.name == slot.name
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isNext) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    slot.name.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (slot.name == PrayerName.SUNRISE)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    fmt(slot.timeMillis),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (isNext) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // القبلة
            Surface(
                onClick = onOpenQibla,
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("اتجاه القبلة", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "بوصلة ومعايرة",
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

            // الموقع
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        settings.placeName.ifBlank { "موقع محفوظ" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "%.3f، %.3f".format(settings.latitude, settings.longitude),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showManual = true }) { Text("تعديل") }
                IconButton(onClick = {
                    onRequestLocationPermission()
                    vm.detectLocation()
                }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "تحديد الموقع")
                }
            }

            HorizontalDivider()

            Text("طريقة الحساب", style = MaterialTheme.typography.titleLarge)
            Text(
                "اختر ما تعمل به الجهة الرسمية في بلدك — الفروق بينها في زاويتي الفجر والعشاء، وقد تبلغ عشر دقائق.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrayerCalculator.methods.forEach { (m, label) ->
                    FilterChip(
                        selected = settings.calculationMethod == m.name,
                        onClick = { vm.setMethod(m.name) },
                        label = { Text(label) }
                    )
                }
            }

            Text("مذهب العصر", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.madhab == "SHAFI",
                    onClick = { vm.setMadhab("SHAFI") },
                    label = { Text("الجمهور") }
                )
                FilterChip(
                    selected = settings.madhab == "HANAFI",
                    onClick = { vm.setMadhab("HANAFI") },
                    label = { Text("الحنفي") }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("تنبيه الأذان", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "إشعار عند دخول كل وقت",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.prayerNotifications,
                    onCheckedChange = { vm.setPrayerNotifications(it) }
                )
            }

            HorizontalDivider()

            Text("صوت الأذان", style = MaterialTheme.typography.titleLarge)
            Text(
                "الأصوات مضمّنة داخل التطبيق — اضغط زرّ السماع لتجربة الصوت قبل اختياره.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SoundPicker(
                choices = soundChoices,
                selected = settings.adhanSound,
                previewKey = previewKey,
                onSelect = { vm.setAdhanSound(it) },
                onPreview = { key ->
                    AdhanPreview.toggle(context, key) { previewKey = AdhanPreview.playingKey }
                }
            )

            val fajrChoices = remember { AdhanNotifier.fajrChoices(context) }
            if (fajrChoices.size > 1) {
                Text("أذان الفجر", style = MaterialTheme.typography.titleLarge)
                Text(
                    "أذان الفجر يزيد «الصلاة خير من النوم»، فله صوت مستقل.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SoundPicker(
                    choices = fajrChoices,
                    selected = settings.adhanSoundFajr,
                    previewKey = previewKey,
                    onSelect = { vm.setAdhanSoundFajr(it) },
                    onPreview = { key ->
                        AdhanPreview.toggle(context, key) { previewKey = AdhanPreview.playingKey }
                    }
                )
            }

            if (!AdhanNotifier.canScheduleExact(context)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "الأذان قد يتأخّر دقائق",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "امنح التطبيق إذن «التنبيهات والتذكيرات» ليرفع الأذان في وقته بالضبط.",
                            style = MaterialTheme.typography.labelMedium
                        )
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                )
                            }
                        }) { Text("فتح الإعداد") }
                    }
                }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("تنبيه قبل الأذان", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "تنبيه هادئ قبل دخول الوقت لتستعدّ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.preAdhanEnabled,
                    onCheckedChange = { vm.setPreAdhan(it, settings.preAdhanMinutes) }
                )
            }
            if (settings.preAdhanEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 20, 30).forEach { m ->
                        FilterChip(
                            selected = settings.preAdhanMinutes == m,
                            onClick = { vm.setPreAdhan(true, m) },
                            label = { Text("${m.toArabicDigits(ar)} د") }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("نظام ١٢ ساعة (ص / م)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (settings.use12Hour) "مثال: ٥:١٤ ص" else "مثال: ٠٥:١٤",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.use12Hour,
                    onCheckedChange = { vm.setUse12Hour(it) }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("تذكير أذكار ما بعد الصلاة", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "بعد كل أذان بعشر دقائق",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.afterPrayerAdhkar,
                    onCheckedChange = { vm.setAfterPrayerAdhkar(it) }
                )
            }

            Text("ضبط التاريخ الهجري", style = MaterialTheme.typography.titleLarge)
            Text(
                "الحساب فلكي وقد يخالف رؤية بلدك يومًا. عدّله كما يوافق تقويمك.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-2, -1, 0, 1, 2).forEach { off ->
                    FilterChip(
                        selected = settings.hijriOffset == off,
                        onClick = { vm.setHijriOffset(off) },
                        label = {
                            Text(
                                when {
                                    off == 0 -> "بلا تعديل"
                                    off > 0 -> "+$off"
                                    else -> "$off"
                                }
                            )
                        }
                    )
                }
            }

            Text(
                "المواقيت تُحسب على جهازك بمعادلات فلكية، دون إنترنت ودون إرسال موقعك إلى أي جهة.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showManual) {
        ManualLocationDialog(
            initialLat = settings.latitude,
            initialLng = settings.longitude,
            initialName = settings.placeName,
            onDismiss = { showManual = false },
            onConfirm = { lat, lng, name ->
                vm.setManualLocation(lat, lng, name)
                showManual = false
            }
        )
    }
}

@Composable
private fun LocationPrompt(locating: Boolean, onDetect: () -> Unit, onManual: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("حدّد موقعك أولًا", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "المواقيت تُحسب من خطّي الطول والعرض. يُقرأ الموقع مرّة واحدة ويُحفظ على جهازك.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDetect, enabled = !locating) {
            Text(if (locating) "جارٍ التحديد…" else "استخدم موقعي")
        }
        TextButton(onClick = onManual) { Text("أدخل الإحداثيات يدويًا") }
    }
}

@Composable
private fun ManualLocationDialog(
    initialLat: Double,
    initialLng: Double,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, String) -> Unit
) {
    var lat by remember { mutableStateOf(if (initialLat == 0.0) "" else initialLat.toString()) }
    var lng by remember { mutableStateOf(if (initialLng == 0.0) "" else initialLng.toString()) }
    var name by remember { mutableStateOf(initialName) }

    val valid = lat.toDoubleOrNull() != null && lng.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحديد الموقع يدويًا") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("اسم المدينة") }, singleLine = true
                )
                OutlinedTextField(
                    value = lat, onValueChange = { lat = it },
                    label = { Text("خط العرض") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    )
                )
                OutlinedTextField(
                    value = lng, onValueChange = { lng = it },
                    label = { Text("خط الطول") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    )
                )
                Text(
                    "تجدها في خرائط جوجل: اضغط مطوّلًا على موضعك فتظهر الإحداثيات.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(lat.toDouble(), lng.toDouble(), name.trim()) }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** قائمة أصوات مع زرّ سماع لكل صوت مضمّن. */
@Composable
private fun SoundPicker(
    choices: List<AdhanChoice>,
    selected: String,
    previewKey: String?,
    onSelect: (String) -> Unit,
    onPreview: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        choices.forEach { choice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == choice.key,
                    onClick = { onSelect(choice.key) }
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    choice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (AdhanNotifier.isBundled(choice.key)) {
                    IconButton(onClick = { onPreview(choice.key) }) {
                        Icon(
                            if (previewKey == choice.key) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (previewKey == choice.key) "إيقاف" else "سماع"
                        )
                    }
                }
            }
        }
    }
}
