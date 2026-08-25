package com.abdelhay.dhikr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdelhay.dhikr.data.SurahInfo
import com.abdelhay.dhikr.util.toArabicDigits
import com.abdelhay.dhikr.vm.KhatmaState
import com.abdelhay.dhikr.vm.QuranViewModel
import kotlin.math.abs

private enum class QuranTab(val label: String) {
    SURAHS("السور"), BOOKMARKS("الفواصل"), KHATMA("الختمة")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranIndexScreen(
    vm: QuranViewModel,
    onOpenSurah: (Int, Int?) -> Unit,
    onOpenKhatma: () -> Unit,
    onOpenRecitation: () -> Unit
) {
    var tab by remember { mutableStateOf(QuranTab.SURAHS) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ar = settings.arabicNumerals

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المصحف") },
                actions = {
                    IconButton(onClick = onOpenRecitation) {
                        Icon(Icons.Filled.Headphones, contentDescription = "التلاوة والقرّاء")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                QuranTab.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = tab == t,
                        onClick = { tab = t },
                        shape = SegmentedButtonDefaults.itemShape(i, QuranTab.entries.size)
                    ) { Text(t.label) }
                }
            }

            when (tab) {
                QuranTab.SURAHS -> SurahList(vm, ar) { id -> onOpenSurah(id, null) }
                QuranTab.BOOKMARKS -> BookmarkList(vm, ar, onOpenSurah)
                QuranTab.KHATMA -> KhatmaPane(vm, ar, onOpenKhatma)
            }
        }
    }
}

// ─────────────────────── السور ───────────────────────

@Composable
private fun SurahList(vm: QuranViewModel, ar: Boolean, onOpen: (Int) -> Unit) {
    val index by vm.index.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = vm::search,
            placeholder = { Text("ابحث باسم السورة أو رقمها") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (settings.lastSurah in 1..114 && query.isBlank()) {
                val last = index.firstOrNull { it.id == settings.lastSurah }
                if (last != null) {
                    item {
                        Surface(
                            onClick = { onOpen(last.id) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("تابع القراءة", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "سورة ${last.name} — الآية ${settings.lastVerse.coerceAtLeast(1).toArabicDigits(ar)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            items(index, key = { it.id }) { s -> SurahRow(s, ar) { onOpen(s.id) } }

            if (index.isEmpty()) {
                item {
                    Text(
                        "لا توجد سورة بهذا الاسم.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahRow(s: SurahInfo, arabicDigits: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.id.toArabicDigits(arabicDigits),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("سورة ${s.name}", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${s.revelationLabel} • ${s.verseCount.toArabicDigits(arabicDigits)} آية",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────── الفواصل ───────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkList(vm: QuranViewModel, ar: Boolean, onOpen: (Int, Int?) -> Unit) {
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()

    if (bookmarks.isEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("لا فواصل بعد.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "افتح أي سورة واضغط زر الفاصل، فيُحفظ موضعك للرجوع إليه.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(bookmarks, key = { it.id }) { b ->
            Surface(
                onClick = { onOpen(b.surah, b.ayah) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("سورة ${b.surahName}", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "الآية ${b.ayah.toArabicDigits(ar)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.removeBookmark(b) }) {
                        Icon(Icons.Filled.Close, contentDescription = "حذف الفاصل")
                    }
                }
            }
        }
    }
}

// ─────────────────────── الختمة ───────────────────────

@Composable
private fun KhatmaPane(vm: QuranViewModel, ar: Boolean, onOpen: () -> Unit) {
    val state by vm.khatmaState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val s = state
        if (s == null) {
            StartKhatma(vm, ar)
        } else {
            ActiveKhatma(s, vm, ar, onOpen)
        }
    }
}

@Composable
private fun StartKhatma(vm: QuranViewModel, ar: Boolean) {
    var juzPerDay by remember { mutableStateOf(1.0) }
    val days = kotlin.math.ceil(30.0 / juzPerDay).toInt()

    Text("ابدأ ختمة", style = MaterialTheme.typography.headlineMedium)
    Text(
        "اختر وردك اليومي، ويحسب التطبيق موعد الختم ويتابع معك يومًا بيوم.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(4.dp))
    listOf(
        0.5 to "نصف جزء يوميًا",
        1.0 to "جزء يوميًا",
        2.0 to "جزءان يوميًا",
        4.0 to "أربعة أجزاء يوميًا"
    ).forEach { (value, label) ->
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (juzPerDay == value) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = juzPerDay == value, onClick = { juzPerDay = value })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "تختم في ${kotlin.math.ceil(30.0 / value).toInt().toArabicDigits(ar)} يومًا",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { vm.startKhatma(juzPerDay) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("ابدأ الختمة (${days.toArabicDigits(ar)} يومًا)") }
}

@Composable
private fun ActiveKhatma(s: KhatmaState, vm: QuranViewModel, ar: Boolean, onOpen: () -> Unit) {
    var confirmEnd by remember { mutableStateOf(false) }

    // التقدّم
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("اليوم ${s.dayNumber.toArabicDigits(ar)} من ${s.estimatedDays.toArabicDigits(ar)}",
                style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "${(s.khatma.fraction * 100).toInt().toArabicDigits(ar)}٪ من المصحف",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { s.khatma.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (s.khatma.progress == 0) "لم تبدأ القراءة بعد"
                else "وصلت إلى سورة ${s.currentSurahName} — الآية ${s.currentAyah.toArabicDigits(ar)} " +
                    "(الجزء ${s.currentJuz.toArabicDigits(ar)})",
                style = MaterialTheme.typography.bodyMedium
            )
            if (s.khatma.progress > 0 && !s.khatma.isDone) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "تستأنف من سورة ${s.resumeSurahName} — الآية ${s.resumeAyah.toArabicDigits(ar)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // ورد اليوم
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("ورد اليوم", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (s.isTodayDone) {
                Text(
                    "أتممتَ ورد اليوم، تقبّل الله.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Text(
                    "اقرأ إلى سورة ${s.targetSurahName} — الآية ${s.targetAyah.toArabicDigits(ar)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "بقي ${s.remainingToday.toArabicDigits(ar)} آية",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) { Text("افتح ورد اليوم") }
        }
    }

    // مقارنة بالخطة
    val delta = s.delta
    val aheadLabel = when {
        s.khatma.progress == 0 -> "ابدأ اليوم؛ الخطة تنتظرك."
        delta >= 0 -> "أنت متقدّم على الخطة بـ ${delta.toArabicDigits(ar)} آية."
        else -> "أنت متأخّر عن الخطة بـ ${abs(delta).toArabicDigits(ar)} آية."
    }
    Text(
        aheadLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = if (delta >= 0) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.error
    )

    HorizontalDivider()

    Text("تعديل الورد اليومي", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.5 to "نصف", 1.0 to "جزء", 2.0 to "جزءان", 4.0 to "أربعة").forEach { (v, l) ->
            FilterChip(
                selected = s.khatma.juzPerDay == v,
                onClick = { vm.setJuzPerDay(v) },
                label = { Text(l) }
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { confirmEnd = true },
        modifier = Modifier.fillMaxWidth()
    ) { Text("إنهاء الختمة") }

    Text(
        "زر «✓» داخل شاشة القراءة يسجّل موضعك في الختمة، وزر الفاصل يحفظه للرجوع إليه. " +
            "الأول للورد، والثاني لعلامة شخصية.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("إنهاء الختمة") },
            text = { Text("سيتوقّف تتبّع هذه الختمة. يمكنك بدء ختمة جديدة في أي وقت.") },
            confirmButton = {
                TextButton(onClick = { vm.endKhatma(); confirmEnd = false }) { Text("إنهاء") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("إلغاء") } }
        )
    }
}
