package com.abdelhay.dhikr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdelhay.dhikr.audio.QuranAudio
import com.abdelhay.dhikr.audio.Reciters
import com.abdelhay.dhikr.util.toArabicDigits
import com.abdelhay.dhikr.vm.QuranViewModel

/**
 * اختيار القارئ وتنزيل التلاوة للاستماع دون إنترنت.
 *
 * التنزيل سورةً سورةً عمدًا: المصحف كاملًا يبلغ مئات الميغابايتات، والقارئ
 * غالبًا يريد ما يقرؤه هذه الأيام لا كل شيء.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecitationScreen(vm: QuranViewModel, onBack: () -> Unit) {

    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val index by vm.index.collectAsStateWithLifecycle()
    val download by vm.downloader.state.collectAsStateWithLifecycle()
    val ar = settings.arabicNumerals

    val reciter = Reciters.from(settings.reciter)
    var storedMb by remember(settings.reciter, download.done) {
        mutableIntStateOf(QuranAudio.downloadedMb(context, reciter))
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التلاوة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            item {
                Text(
                    "القارئ (${Reciters.all.size.toArabicDigits(ar)})",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "تغيير القارئ لا يحذف ما نزّلتَه — لكل قارئ مجلده الخاص.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("ابحث باسم القارئ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            items(Reciters.search(query), key = { it.key }) { r ->
                Surface(
                    onClick = { vm.setReciter(r.key) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (r.key == settings.reciter) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = r.key == settings.reciter,
                            onClick = { vm.setReciter(r.key) }
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${r.style} • ${r.kbps.toArabicDigits(ar)} kbps • " +
                                    "المصحف كاملًا نحو ${r.fullQuranMb.toArabicDigits(ar)} م.ب",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("المنزَّل لهذا القارئ", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${storedMb.toArabicDigits(ar)} ميغابايت على جهازك",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (storedMb > 0) {
                        TextButton(onClick = { confirmDelete = true }) { Text("حذف") }
                    }
                }

                if (download.running) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "تنزيل سورة ${index.firstOrNull { it.id == download.surah }?.name.orEmpty()} — " +
                                    "${download.done.toArabicDigits(ar)}/${download.total.toArabicDigits(ar)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { download.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        IconButton(onClick = { vm.cancelDownload() }) {
                            Icon(Icons.Filled.Close, contentDescription = "إلغاء")
                        }
                    }
                }

                download.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(12.dp))
                Text("تنزيل سورة", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
            }

            items(index, key = { "dl-${it.id}" }) { info ->
                val complete = QuranAudio.isDownloaded(context, reciter, info.id, info.verseCount)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${info.id.toArabicDigits(ar)}. سورة ${info.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { vm.downloadSurah(info.id) },
                        enabled = !download.running && !complete
                    ) {
                        Icon(
                            if (complete) Icons.Filled.DownloadDone else Icons.Filled.Download,
                            contentDescription = if (complete) "منزَّلة" else "تنزيل",
                            tint = if (complete) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف التلاوات المنزَّلة") },
            text = { Text("سيُحذف ما نزّلتَه لهذا القارئ فقط، ويبقى ما لغيره.") },
            confirmButton = {
                TextButton(onClick = {
                    QuranAudio.deleteAll(context, reciter)
                    storedMb = 0
                    confirmDelete = false
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } }
        )
    }
}
