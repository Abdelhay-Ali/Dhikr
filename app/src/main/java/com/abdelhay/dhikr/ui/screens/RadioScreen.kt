package com.abdelhay.dhikr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdelhay.dhikr.audio.RadioBus
import com.abdelhay.dhikr.audio.RadioService
import com.abdelhay.dhikr.audio.RadioStation
import com.abdelhay.dhikr.vm.RadioViewModel

/**
 * إذاعات القرآن.
 *
 * بثٌّ مباشر على مدار الساعة — من السعودية ومصر ومحطات القرّاء. وهو المخرج لمن
 * أراد صوتًا لا تقطّعه الآيات، ولقرّاء لم تُقطَّع تلاواتهم آيةً آية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    vm: RadioViewModel,
    surahVm: com.abdelhay.dhikr.vm.SurahReciterViewModel,
    surahNames: List<Pair<Int, String>>
) {

    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val stations by vm.stations.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()

    val current by RadioBus.station.collectAsStateWithLifecycle()
    val playing by RadioBus.playing.collectAsStateWithLifecycle()
    val connecting by RadioBus.connecting.collectAsStateWithLifecycle()
    val error by RadioBus.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إذاعات القرآن") },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "تحديث القائمة")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("البثّ المباشر") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("مصاحف كاملة") })
            }

            if (current != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                current?.name.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (connecting) "جارٍ الاتصال…" else "بثّ مباشر",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (connecting) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        TextButton(onClick = { RadioService.stop(context) }) { Text("إيقاف") }
                    }
                }
            }

            error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { RadioBus.clearError() }) { Text("حسنًا") }
                    }
                }
            }

            if (tab == 0) OutlinedTextField(
                value = query,
                onValueChange = vm::search,
                placeholder = { Text("ابحث عن محطة أو قارئ") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (loading && stations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (tab == 1) {
                SurahReciterPane(surahVm, surahNames)
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stations, key = { it.id + it.url }) { s ->
                    StationRow(
                        station = s,
                        isCurrent = current?.url == s.url,
                        playing = playing,
                        onPlay = { RadioService.play(context, s) },
                        onStop = { RadioService.stop(context) }
                    )
                }
                if (stations.isEmpty()) {
                    item {
                        Text(
                            "لا توجد محطة بهذا الاسم.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationRow(
    station: RadioStation,
    isCurrent: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        onClick = { if (isCurrent && playing) onStop() else onPlay() },
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isCurrent && playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                station.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * قرّاء المصاحف الكاملة.
 *
 * هؤلاء ملفاتهم سورةٌ كاملة لا آية آية، فلا تظليل ولا تكرار بالآية — وهذا مذكور
 * للمستخدم صراحةً كي لا يتوقّع ما لا يقدر عليه المصدر.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahReciterPane(
    vm: com.abdelhay.dhikr.vm.SurahReciterViewModel,
    surahNames: List<Pair<Int, String>>
) {
    val context = LocalContext.current
    val reciters by vm.reciters.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<com.abdelhay.dhikr.audio.SurahReciter?>(null) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::search,
            placeholder = { Text("ابحث باسم القارئ") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Text(
            "تلاوات سورةً كاملة — بلا تظليل الآيات ولا تكرارها، وفيها من لا تجده في تلاوة الآيات.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (loading && reciters.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reciters, key = { it.id }) { r ->
                Surface(
                    onClick = { picked = r },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        r.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            if (reciters.isEmpty()) {
                item {
                    Text(
                        "لم تُجلب القائمة بعد. تحقّق من الاتصال ثم اضغط زرّ التحديث.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
        }
    }

    val r = picked
    if (r != null) {
        ModalBottomSheet(onDismissRequest = { picked = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(r.name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(surahNames, key = { it.first }) { (id, name) ->
                        Surface(
                            onClick = {
                                com.abdelhay.dhikr.audio.RadioService.playSurah(
                                    context, "سورة $name — ${r.name}", r.urlOf(id)
                                )
                                picked = null
                            },
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "$id. سورة $name",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
