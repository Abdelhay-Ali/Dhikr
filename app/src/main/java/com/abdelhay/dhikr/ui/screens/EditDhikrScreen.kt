package com.abdelhay.dhikr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdelhay.dhikr.data.CountMode
import com.abdelhay.dhikr.data.Preset
import com.abdelhay.dhikr.data.PresetCategory
import com.abdelhay.dhikr.data.Presets
import com.abdelhay.dhikr.vm.DhikrViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditDhikrScreen(
    vm: DhikrViewModel,
    dhikrId: Long?,          // null = إضافة جديدة
    onDone: () -> Unit
) {
    val existing by (dhikrId?.let { vm.observe(it) } ?: kotlinx.coroutines.flow.flowOf<com.abdelhay.dhikr.data.Dhikr?>(null))
        .collectAsStateWithLifecycle(initialValue = null)

    var text by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("33") }
    var mode by remember { mutableStateOf(CountMode.UP) }
    var note by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        val e = existing
        if (e != null && !loaded) {
            text = e.text; target = e.target.toString(); mode = e.mode
            note = e.note.orEmpty(); source = e.source.orEmpty()
            loaded = true
        }
    }

    val valid = text.isNotBlank() && (target.toIntOrNull() ?: 0) > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (dhikrId == null) "ذكر جديد" else "تعديل الذكر") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (dhikrId != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "حذف")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Button(
                    onClick = {
                        val t = target.toIntOrNull() ?: 33
                        val e = existing
                        if (e == null) {
                            vm.add(text.trim(), t, mode, note.trim().ifBlank { null }, source.trim().ifBlank { null })
                        } else {
                            vm.save(
                                e.copy(
                                    text = text.trim(),
                                    target = t,
                                    mode = mode,
                                    note = note.trim().ifBlank { null },
                                    source = source.trim().ifBlank { null },
                                    raw = if (mode != e.mode) {
                                        if (mode == CountMode.UP) e.done else (t - e.done).coerceAtLeast(0)
                                    } else e.raw
                                )
                            )
                        }
                        onDone()
                    },
                    enabled = valid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) { Text("حفظ") }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("نص الذكر") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { v -> target = v.filter { it.isDigit() }.take(5) },
                    label = { Text("العدد") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.width(120.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 7, 10, 33, 100).forEach { n ->
                        AssistChip(onClick = { target = n.toString() }, label = { Text("$n") })
                    }
                }
            }

            Column {
                Text("طريقة العد", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == CountMode.UP,
                        onClick = { mode = CountMode.UP },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("تصاعدي ٠ ← العدد") }
                    SegmentedButton(
                        selected = mode == CountMode.DOWN,
                        onClick = { mode = CountMode.DOWN },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("تنازلي العدد ← ٠") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (mode == CountMode.DOWN) "يعرض العدّاد ما تبقّى عليك."
                    else "يعرض العدّاد ما أنجزتَه.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("ملاحظة أو فضل الذكر (اختياري)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (dhikrId == null) {
                HorizontalDivider()
                Text("أو اختر من الأذكار المأثورة", style = MaterialTheme.typography.titleLarge)
                Text(
                    "الاختيار يملأ الحقول أعلاه، ولك أن تعدّل العدد قبل الحفظ.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Presets.byCategory().forEach { (category, items) ->
                    if (items.isNotEmpty()) {
                        PresetCategoryBlock(
                            category = category,
                            items = items,
                            onPick = { p ->
                                text = p.text
                                target = p.target.toString()
                                note = p.note.orEmpty()
                                source = p.source.orEmpty()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف الذكر") },
            text = { Text("سيُحذف الذكر وسجلّه من الورد.") },
            confirmButton = {
                TextButton(onClick = {
                    existing?.let { vm.delete(it) }
                    confirmDelete = false
                    onDone()
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") }
            }
        )
    }
}

/** قسم قابل للطيّ لكل باب من أبواب الأذكار — المكتبة كبيرة، وطيّها يجعلها قابلة للتصفّح. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetCategoryBlock(
    category: PresetCategory,
    items: List<Preset>,
    onPick: (Preset) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    category.label,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            items.forEach { p ->
                Surface(
                    onClick = { onPick(p) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                p.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("×${p.target}", style = MaterialTheme.typography.labelMedium)
                        }
                        if (!p.source.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                p.source!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
