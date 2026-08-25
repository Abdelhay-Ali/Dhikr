package com.abdelhay.dhikr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.abdelhay.dhikr.audio.AyahRef
import androidx.compose.material.icons.filled.Headphones
import com.abdelhay.dhikr.audio.PlaybackBus
import com.abdelhay.dhikr.audio.Reciters
import com.abdelhay.dhikr.ui.components.ReciterSheet
import com.abdelhay.dhikr.ui.components.ListenControls
import com.abdelhay.dhikr.ui.components.MushafPageView
import com.abdelhay.dhikr.ui.components.PlaybackBar
import com.abdelhay.dhikr.ui.theme.MushafFont
import com.abdelhay.dhikr.ui.theme.QuranFamily
import com.abdelhay.dhikr.util.ForceOrientation
import com.abdelhay.dhikr.util.toArabicDigits
import com.abdelhay.dhikr.vm.QuranViewModel

/**
 * شاشة الختمة — منفصلة تمامًا عن تصفّح السور.
 *
 * تفتح **ورد اليوم وحده**: من حيث وقفتَ إلى مقدار الأجزاء التي حدّدتها، لا أكثر.
 * فإذا بلغتَ آخره خرجت من وضع الختمة. وما تقرؤه في تبويب السور قراءةٌ حرّة
 * لا تمسّ هذا الموضع بحال.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhatmaReaderScreen(
    vm: QuranViewModel,
    onBack: () -> Unit
) {
    val portion by vm.portion.collectAsStateWithLifecycle()
    val done by vm.portionDone.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tafsir by vm.tafsir.collectAsStateWithLifecycle()
    val qcfPages by vm.qcfPages.collectAsStateWithLifecycle()
    val index by vm.index.collectAsStateWithLifecycle()
    val playingRef by PlaybackBus.current.collectAsStateWithLifecycle()
    val isPlaying by PlaybackBus.playing.collectAsStateWithLifecycle()
    val round by PlaybackBus.round.collectAsStateWithLifecycle()
    var showReciters by remember { mutableStateOf(false) }

    ForceOrientation(settings.mushafLandscape)
    val ar = settings.arabicNumerals
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        vm.loadTodayPortion()
        onDispose { vm.clearPortion(); vm.hideTafsir() }
    }

    // صفحات الورد بخطوط المصحف
    LaunchedEffect(portion) {
        portion?.let { vm.openPagesOfPortion(it.startGlobal, it.endGlobal) }
    }

    // نحفظ الصفحة المعروضة نفسها لا التي قبلها، فتُفتح هي عند العودة
    LaunchedEffect(listState.firstVisibleItemIndex, qcfPages) {
        val page = qcfPages.getOrNull(listState.firstVisibleItemIndex) ?: return@LaunchedEffect
        val a = page.firstAyah ?: return@LaunchedEffect
        vm.saveKhatmaPageAt(a.surah, a.ayah)
    }

    // بلوغ آخر الورد يُنهي الجلسة
    val atEnd = !listState.canScrollForward
    var scrolled by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) scrolled = true
    }
    LaunchedEffect(atEnd, scrolled, qcfPages) {
        if (qcfPages.isEmpty() || done) return@LaunchedEffect
        val single = qcfPages.size == 1
        if (atEnd && (scrolled || single)) vm.finishPortion()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ورد الختمة", style = MaterialTheme.typography.titleLarge)
                        qcfPages.getOrNull(listState.firstVisibleItemIndex)?.let { page ->
                            run {
                                Text(
                                    "صفحة ${page.page.toArabicDigits(ar)} • الجزء ${vm.juzOfPage(page.page).toArabicDigits(ar)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (playingRef != null) vm.togglePlayback()
                        else {
                            val f = qcfPages.firstOrNull()?.firstAyah
                            val l = qcfPages.lastOrNull()?.lastAyah
                            if (f != null && l != null) {
                                vm.repeatRange(f.surah, f.ayah, l.surah, l.ayah, "ورد الختمة", 1)
                            }
                        }
                    }) {
                        Icon(
                            if (playingRef != null && isPlaying) Icons.Filled.Pause
                            else Icons.Filled.PlayArrow,
                            contentDescription = "تلاوة الورد"
                        )
                    }
                    IconButton(onClick = { vm.setMushafLandscape(!settings.mushafLandscape) }) {
                        Icon(
                            if (settings.mushafLandscape) Icons.Filled.StayCurrentPortrait
                            else Icons.Filled.StayCurrentLandscape,
                            contentDescription = "تدوير الصفحة"
                        )
                    }
                    IconButton(onClick = { showReciters = true }) {
                        Icon(Icons.Filled.Headphones, contentDescription = "اختيار القارئ")
                    }
                    IconButton(onClick = { vm.setFontScale(settings.quranFontScale - 0.25f) }) {
                        Icon(Icons.Filled.TextDecrease, contentDescription = "تصغير الخط")
                    }
                    IconButton(onClick = { vm.setFontScale(settings.quranFontScale + 0.25f) }) {
                        Icon(Icons.Filled.TextIncrease, contentDescription = "تكبير الخط")
                    }
                }
            )
        }
    ) { padding ->

        if (qcfPages.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // شريط تقدّم داخل الورد نفسه
            val readPages = (listState.firstVisibleItemIndex + 1).coerceAtMost(qcfPages.size)
            LinearProgressIndicator(
                progress = { if (qcfPages.isEmpty()) 0f else readPages.toFloat() / qcfPages.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            val firstJuz = qcfPages.firstOrNull()?.let { vm.juzOfPage(it.page) }
            val lastJuz = qcfPages.lastOrNull()?.let { vm.juzOfPage(it.page) }
            val juzLabel = when {
                firstJuz == null -> ""
                firstJuz == lastJuz -> " • الجزء ${firstJuz.toArabicDigits(ar)}"
                else -> " • الأجزاء ${firstJuz.toArabicDigits(ar)}–${lastJuz!!.toArabicDigits(ar)}"
            }
            Text(
                "صفحة ${readPages.toArabicDigits(ar)} من ${qcfPages.size.toArabicDigits(ar)}$juzLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )

            if (playingRef != null) {
                PlaybackBar(
                    label = "الآية ${(playingRef?.ayah ?: 1).toArabicDigits(ar)}",
                    reciterName = Reciters.from(settings.reciter).name,
                    round = round,
                    playing = isPlaying,
                    onToggle = { vm.togglePlayback() },
                    onStop = { vm.stopPlayback() },
                    onPickReciter = { showReciters = true }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(18.dp, 0.dp, 18.dp, 48.dp)
            ) {
                items(qcfPages, key = { it.page }) { qp ->
                    MushafPageView(
                        page = qp,
                        fontFamily = vm.fontOf(qp.page),
                        arabicDigits = ar,
                        juz = vm.juzOfPage(qp.page),
                        zoom = settings.quranFontScale,
                        highlight = playingRef,
                        surahNameOf = { id -> index.firstOrNull { it.id == id }?.name.orEmpty() },
                        onAyahTap = { a -> vm.showTafsir(a.surah, a.ayah) }
                    )
                }
            }
        }
    }


    if (showReciters) {
        ReciterSheet(
            selectedKey = settings.reciter,
            onPick = { vm.setReciter(it) },
            onDismiss = { showReciters = false }
        )
    }

    val t = tafsir
    if (t != null) {
        ModalBottomSheet(onDismissRequest = { vm.hideTafsir() }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "الآية ${t.second.toArabicDigits(ar)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                val qp = qcfPages.firstOrNull { p ->
                    p.ayahs.any { it.surah == t.first && it.ayah == t.second }
                }
                val glyphs = qp?.ayahs?.firstOrNull { it.surah == t.first && it.ayah == t.second }
                if (qp != null && glyphs != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = glyphs.glyphs,
                        style = TextStyle(
                            fontFamily = vm.fontOf(qp.page),
                            fontSize = (20 * settings.quranFontScale).sp,
                            lineHeight = (44 * settings.quranFontScale).sp,
                            textAlign = TextAlign.Justify
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))
                ListenControls(
                    onListenFromHere = { vm.playFrom(t.first, t.second); vm.hideTafsir() },
                    onRepeatAyah = { n -> vm.repeatAyah(t.first, t.second, n); vm.hideTafsir() },
                    onRepeatPage = { n ->
                        val page = qcfPages.firstOrNull { p ->
                            p.ayahs.any { it.surah == t.first && it.ayah == t.second }
                        }
                        val f = page?.firstAyah
                        val l = page?.lastAyah
                        if (f != null && l != null) {
                            vm.repeatRange(f.surah, f.ayah, l.surah, l.ayah, "تكرار صفحة", n)
                        }
                        vm.hideTafsir()
                    }
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Text(
                    t.third.ifBlank { "لا يوجد تفسير لهذه الآية في هذه النسخة." },
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 30.sp
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "التفسير الميسّر — مجمع الملك فهد لطباعة المصحف الشريف",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (done) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("أتممتَ ورد اليوم") },
            text = {
                Text(
                    "تقبّل الله منك. يفتح لك الورد التالي حين تعود.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = onBack) { Text("خروج من الختمة") }
            }
        )
    }
}

