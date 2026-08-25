package com.abdelhay.dhikr.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdelhay.dhikr.audio.AyahRef
import com.abdelhay.dhikr.data.QcfAyah
import com.abdelhay.dhikr.data.QcfPage
import com.abdelhay.dhikr.util.toArabicDigits

/**
 * صفحة مصحف المدينة، سطرًا سطرًا كما طُبعت.
 *
 * الأسطر محسوبة مسبقًا لا تُترك للالتفاف، وحجم الخط يُشتقّ من عرض الشاشة:
 *
 *     حجم الخط = عرض الشاشة × وحدات الإم ÷ عرض السطر
 *
 * فيملأ كل سطر عرض الصفحة تمامًا كما في المصحف — بلا تسوية ولا فجوات، ولا
 * حاجة إلى مسافات بين الكلمات (وهي غير موجودة في هذه الخطوط أصلًا).
 */
@Composable
fun MushafPageView(
    page: QcfPage,
    fontFamily: FontFamily,
    arabicDigits: Boolean,
    juz: Int?,
    /**
     * التكبير. عند ١ تملأ الصفحة عرض الشاشة تمامًا كالمطبوع، وفوقه تكبر السطور
     * ويصير التمرير الأفقي متاحًا — فلا تنكسر الأسطر ولا تفقد الصفحة هيئتها.
     */
    zoom: Float = 1f,
    /** الآية المتلوّة الآن — تُظلَّل. */
    highlight: AyahRef?,
    surahNameOf: (Int) -> String,
    onAyahTap: (QcfAyah) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)

    val z = zoom.coerceIn(1f, 3f)
    // عرض الصفحة بعد التكبير، ومنه يُشتقّ حجم الخط
    val contentPx = (widthPx * z).toInt()

    /**
     * الحجم الأساس محسوب على عرض السطر المعتاد في هذه الصفحة، فيبقى الخطّ واحدًا
     * في المصحف كلّه تقريبًا كما في المطبوع.
     */
    val baseSizeSp = remember(contentPx, page.lineWidth) {
        if (contentPx == 0 || page.lineWidth <= 0) 0f
        else with(density) {
            // هامش ٠٫٥٪ يقي من فروق قياس النظام عن قياسنا
            (contentPx.toFloat() * 0.995f * page.unitsPerEm / page.lineWidth).toSp().value
        }
    }

    /**
     * السطر الأعرض من المعتاد يُصغَّر بقدر زيادته فيملأ العرض ولا يتجاوزه.
     * وبدون هذا يخرج أوّل السطر عن الشاشة فيُقصّ — وهو ما كان يحدث في ٣٧٤ صفحة.
     */
    fun sizeOf(line: com.abdelhay.dhikr.data.QcfLine): Float {
        if (line.width <= 0) return baseSizeSp
        // عرض السطر المعتاد، وإن كان أعرض سطر في الصفحة أوسع منه فهو الحدّ
        val fit = maxOf(page.lineWidth, 1)
        return if (line.width > fit) baseSizeSp * fit / line.width else baseSizeSp
    }

    val hScroll = rememberScrollState()

    Column(
        modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width }
            .then(if (z > 1f) Modifier.horizontalScroll(hScroll) else Modifier)
            .then(
                if (z > 1f && contentPx > 0) Modifier.width(with(density) { contentPx.toDp() })
                else Modifier
            )
    ) {
        if (baseSizeSp > 0f) {
            page.lines.forEach { line ->
                if (line.surahStart != 0) {
                    SurahBanner(surahNameOf(line.surahStart))
                    // التوبة وحدها بلا بسملة
                    if (line.surahStart != 9) Basmala(baseSizeSp)
                }
                LineText(
                    page = page,
                    line = line,
                    fontFamily = fontFamily,
                    fontSizeSp = sizeOf(line),
                    highlight = highlight,
                    highlightColor = highlightColor,
                    onAyahTap = onAyahTap
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildString {
                append("صفحة ${page.page.toArabicDigits(arabicDigits)}")
                if (juz != null) append(" • الجزء ${juz.toArabicDigits(arabicDigits)}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LineText(
    page: QcfPage,
    line: com.abdelhay.dhikr.data.QcfLine,
    fontFamily: FontFamily,
    fontSizeSp: Float,
    highlight: AyahRef?,
    highlightColor: Color,
    onAyahTap: (QcfAyah) -> Unit
) {
    val text = remember(line, highlight, highlightColor) {
        val lit = highlight?.let { h ->
            page.ayahs.firstOrNull { it.surah == h.surah && it.ayah == h.ayah }
                ?.let { page.rangeOf(line, it) }
        }
        buildAnnotatedString {
            if (lit == null) {
                append(line.glyphs)
            } else {
                append(line.glyphs.substring(0, lit.first))
                withStyle(SpanStyle(background = highlightColor)) {
                    append(line.glyphs.substring(lit.first, lit.last + 1))
                }
                append(line.glyphs.substring(lit.last + 1))
            }
        }
    }

    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = text,
        style = TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.9f).sp,
            textAlign = TextAlign.Center,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None
            )
        ),
        softWrap = false,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(line) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    val offset = l.getOffsetForPosition(pos).coerceIn(0, line.glyphs.length - 1)
                    page.ayahAt(line.startIndex + offset)?.let(onAyahTap)
                }
            },
        onTextLayout = { layout = it }
    )
}

@Composable
private fun Basmala(fontSizeSp: Float) {
    Text(
        text = "\uFDFD",
        style = TextStyle(fontSize = (fontSizeSp * 0.85f).sp, textAlign = TextAlign.Center),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun SurahBanner(name: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp)
    ) {
        Text(
            "سورة $name",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        )
    }
}
