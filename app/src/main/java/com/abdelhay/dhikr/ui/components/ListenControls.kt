package com.abdelhay.dhikr.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp

/** خيارات عدد التكرار. صفر يعني بلا نهاية. */
val repeatChoices = listOf(3, 5, 7, 10, 0)

fun repeatLabel(n: Int): String = when (n) {
    0 -> "بلا نهاية"
    else -> "$n مرّات"
}

/**
 * أزرار الاستماع تحت آية.
 *
 * الترتيب مقصود: الاستماع المتّصل أولًا لأنه الأكثر استعمالًا، ثم التكرار —
 * وهو أداة الحفظ لا أداة القراءة.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListenControls(
    onListenFromHere: () -> Unit,
    onRepeatAyah: (Int) -> Unit,
    onRepeatPage: (Int) -> Unit,
    onListenToEnd: (() -> Unit)? = null
) {
    var sheet by remember { mutableStateOf<String?>(null) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onListenFromHere) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("استمع من هنا")
        }
        OutlinedButton(onClick = { sheet = "ayah" }) {
            Icon(Icons.Filled.Repeat, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("كرّر الآية")
        }
        OutlinedButton(onClick = { sheet = "page" }) {
            Icon(Icons.Filled.Repeat, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("كرّر الصفحة")
        }
        if (onListenToEnd != null) {
            TextButton(onClick = onListenToEnd) { Text("إلى آخر المصحف") }
        }
    }

    val target = sheet
    if (target != null) {
        AlertDialog(
            onDismissRequest = { sheet = null },
            title = { Text(if (target == "ayah") "تكرار الآية" else "تكرار الصفحة") },
            text = {
                Column {
                    Text(
                        "كم مرّة تريد إعادتها؟",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    repeatChoices.forEach { n ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    if (target == "ayah") onRepeatAyah(n) else onRepeatPage(n)
                                    sheet = null
                                }
                            ) { Text(repeatLabel(n)) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { sheet = null }) { Text("إلغاء") } }
        )
    }
}

/** شريط يظهر أثناء التلاوة: الآية الجارية، جولة التكرار، وزرّ الإيقاف. */
@Composable
fun PlaybackBar(
    label: String,
    reciterName: String,
    round: Pair<Int, Int>,
    playing: Boolean,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onPickReciter: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                // اسم القارئ زرٌّ في موضعه الطبيعي: حيث ينظر المستمع أصلًا
                TextButton(
                    onClick = onPickReciter,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) { Text(reciterName, style = MaterialTheme.typography.labelMedium) }
                val (current, total) = round
                if (total != 1 && current > 0) {
                    Text(
                        if (total == 0) "تكرار — الجولة $current"
                        else "تكرار $current من $total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            TextButton(onClick = onToggle) { Text(if (playing) "إيقاف مؤقّت" else "متابعة") }
            TextButton(onClick = onStop) { Text("إنهاء") }
        }
    }
}

/**
 * اختيار القارئ من داخل المصحف.
 *
 * تغييره أثناء التلاوة يعيد الآية الجارية بالصوت الجديد ويُبقي المدى والتكرار،
 * فمن لم يعجبه صوتٌ في وسط ورده لا يفقد موضعه.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciterSheet(
    selectedKey: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("اختر القارئ", style = MaterialTheme.typography.titleLarge)
            Text(
                "التغيير أثناء التلاوة يعيد الآية الجارية بالصوت الجديد.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("ابحث باسم القارئ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            com.abdelhay.dhikr.audio.Reciters.search(query).forEach { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = r.key == selectedKey,
                        onClick = { onPick(r.key); onDismiss() }
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${r.style} • ${r.kbps} kbps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
