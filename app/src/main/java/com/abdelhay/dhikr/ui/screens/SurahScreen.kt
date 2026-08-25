package com.abdelhay.dhikr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Headphones
import com.abdelhay.dhikr.audio.PlaybackBus
import com.abdelhay.dhikr.audio.Reciters
import com.abdelhay.dhikr.ui.components.ReciterSheet
import com.abdelhay.dhikr.ui.components.ListenControls
import com.abdelhay.dhikr.ui.components.MushafPageView
import com.abdelhay.dhikr.ui.components.PlaybackBar
import com.abdelhay.dhikr.util.ForceOrientation
import com.abdelhay.dhikr.util.toArabicDigits
import com.abdelhay.dhikr.vm.QuranViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahScreen(
    vm: QuranViewModel,
    surahId: Int,
    /** آية يُفتح عندها المصحف — تأتي من الفاصل. */
    startAyah: Int? = null,
    onBack: () -> Unit
) {
    val surah by vm.openSurah.collectAsStateWithLifecycle()
    val qcfPages by vm.qcfPages.collectAsStateWithLifecycle()
    val index by vm.index.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tafsir by vm.tafsir.collectAsStateWithLifecycle()
    val playingRef by PlaybackBus.current.collectAsStateWithLifecycle()
    val isPlaying by PlaybackBus.playing.collectAsStateWithLifecycle()
    val playingAyah = playingRef?.takeIf { it.surah == surahId }?.ayah
    val round by PlaybackBus.round.collectAsStateWithLifecycle()
    var showReciters by remember { mutableStateOf(false) }

    ForceOrientation(settings.mushafLandscape)
    val ar = settings.arabicNumerals
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(surahId) { vm.open(surahId) }

    // قبل الصفحات عنصران في القائمة: تنبيه التفسير ورأس السورة.
    // بدون طرحهما يسبق رقم الصفحة المعروض موضعَ القارئ بصفحتين.
    // عنصر واحد قبل الصفحات: تنبيه التفسير. رأس السورة صار داخل الصفحة نفسها.
    val headerItems = 1
    fun pageAt(index: Int) = qcfPages.getOrNull(index - headerItems)
    val currentPage = pageAt(listState.firstVisibleItemIndex)

    /**
     * فتح الصفحة التي تحوي آيةً بعينها.
     * الفاصل يحفظ أول آية في الصفحة، فالعودة إليه تفتح الصفحة نفسها لا آيةً في وسطها.
     */
    LaunchedEffect(qcfPages, startAyah) {
        if (qcfPages.isEmpty()) return@LaunchedEffect
        val target = startAyah
            ?: settings.lastVerse.takeIf { settings.lastSurah == surahId && it > 0 }
            ?: return@LaunchedEffect
        val idx = qcfPages.indexOfFirst { p -> p.ayahs.any { it.ayah >= target && it.surah == surahId } }
        if (idx >= 0) listState.scrollToItem(idx + headerItems)
    }

    LaunchedEffect(listState.firstVisibleItemIndex, qcfPages) {
        val page = currentPage ?: return@LaunchedEffect
        vm.rememberPosition(surahId, page.firstAyah?.ayah ?: 1)
    }

    /** أول آية في الصفحة المعروضة — الفاصل يحفظ الصفحة لا الآية التي وقعت عليها العين. */
    fun visibleAyah(): Int = currentPage?.firstAyah?.ayah ?: 1

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(surah?.let { "سورة ${it.name}" } ?: "")
                        currentPage?.let { p ->
                            Text(
                                "الجزء ${vm.juzOfPage(p.page).toArabicDigits(ar)} • صفحة ${p.page.toArabicDigits(ar)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        if (playingAyah != null) vm.togglePlayback()
                        else vm.playFrom(surahId, currentPage?.firstAyah?.ayah ?: 1)
                    }) {
                        Icon(
                            if (playingAyah != null && isPlaying) Icons.Filled.Pause
                            else Icons.Filled.PlayArrow,
                            contentDescription = "تلاوة السورة"
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
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(onClick = {
                    vm.addBookmark(surahId, visibleAyah())
                }) {
                    Icon(Icons.Filled.Bookmark, contentDescription = "ضع فاصلًا هنا")
                }
            }
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

        val fontSize = (22 * settings.quranFontScale).sp
        val lineHeight = (52 * settings.quranFontScale).sp

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

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
            contentPadding = PaddingValues(18.dp, 4.dp, 18.dp, 96.dp)
        ) {
            item {
                Text(
                    "اضغط على أي آية لقراءة تفسيرها",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }


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

    // لوحة التفسير
    val t = tafsir
    if (t != null && t.first == surahId) {
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
                    "سورة ${surah?.name.orEmpty()} — الآية ${t.second.toArabicDigits(ar)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                // الآية برسم المصحف نفسه، لا بالحروف — فلا يتكرّر خطأ التركيب
                val qp = qcfPages.firstOrNull { p ->
                    p.ayahs.any { it.surah == t.first && it.ayah == t.second }
                }
                val glyphs = qp?.ayahs?.firstOrNull { it.surah == t.first && it.ayah == t.second }
                if (qp != null && glyphs != null) {
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
                Spacer(Modifier.height(14.dp))
                ListenControls(
                    onListenFromHere = { vm.playFrom(surahId, t.second); vm.hideTafsir() },
                    onRepeatAyah = { n -> vm.repeatAyah(surahId, t.second, n); vm.hideTafsir() },
                    onRepeatPage = { n ->
                        val page = qcfPages.firstOrNull { p ->
                            p.ayahs.any { it.surah == surahId && it.ayah == t.second }
                        }
                        val f = page?.firstAyah
                        val l = page?.lastAyah
                        if (f != null && l != null) {
                            vm.repeatRange(f.surah, f.ayah, l.surah, l.ayah, "تكرار صفحة", n)
                        }
                        vm.hideTafsir()
                    },
                    onListenToEnd = {
                        vm.playFrom(surahId, t.second, toEndOfQuran = true); vm.hideTafsir()
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
}



