package com.abdelhay.dhikr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.abdelhay.dhikr.R

// لوحة مستمدة من مادة السبحة نفسها: خرز الكهرمان على خلفية ليل المسجد،
// والأخضر اليشمي لبلاط المحراب يدل على الاكتمال.
val Kahraman = Color(0xFFE0A94E)   // خرزة الكهرمان — لون العدّاد
val KahramanDeep = Color(0xFFB8801F)
val Yashm = Color(0xFF4EA88C)      // اليشم — لون الإتمام
val NightInk = Color(0xFF0F1A1E)
val NightSurface = Color(0xFF16252A)
val NightRaised = Color(0xFF1E3138)
val Parchment = Color(0xFFECE3D2)
val ParchmentBg = Color(0xFFF6F0E4)
val ParchmentSurface = Color(0xFFFFFBF2)
val InkText = Color(0xFF17262A)
val MutedNight = Color(0xFF8FA3A8)
val MutedDay = Color(0xFF5F7076)

private val Dark = darkColorScheme(
    primary = Kahraman,
    onPrimary = NightInk,
    primaryContainer = Color(0xFF3A2E15),
    onPrimaryContainer = Kahraman,
    secondary = Yashm,
    onSecondary = NightInk,
    secondaryContainer = Color(0xFF1B3A33),
    onSecondaryContainer = Yashm,
    tertiary = Kahraman,
    onTertiary = NightInk,
    background = NightInk,
    onBackground = Parchment,
    surface = NightSurface,
    onSurface = Parchment,
    surfaceVariant = NightRaised,
    onSurfaceVariant = MutedNight,
    outline = Color(0xFF31474E),
    error = Color(0xFFE07A5F)
)

private val Light = lightColorScheme(
    primary = KahramanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4E4C4),
    onPrimaryContainer = Color(0xFF4A3608),
    secondary = Color(0xFF2F7A63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E9E0),
    onSecondaryContainer = Color(0xFF10382C),
    tertiary = KahramanDeep,
    onTertiary = Color.White,
    background = ParchmentBg,
    onBackground = InkText,
    surface = ParchmentSurface,
    onSurface = InkText,
    surfaceVariant = Color(0xFFEBE2D1),
    onSurfaceVariant = MutedDay,
    outline = Color(0xFFD5C9B2),
    error = Color(0xFFB3452F)
)

/**
 * خطّ المصحف: Amiri Quran — نسخ مبني أصلًا لركن النص القرآني، يضبط مواضع
 * العلامات الصغيرة (الألف الخنجرية، السكون المستدير، علامات الوقف) التي تتزاحم
 * وتعلو الحروف في الخطوط العامة. رخصة OFL، والنسخة مرفقة في assets.
 */
val QuranFamily: FontFamily get() = MushafFont.KFGQPC.family

/**
 * خط المصحف.
 *
 * خطّ مجمع الملك فهد (KFGQPC Uthmanic Script HAFS) هو الخطّ الذي صُمّم لهذا النصّ
 * بعينه: يملك U+065E لتنوين الضمّ المتراكب، ويرسم أرقام الآيات مزخرفةً داخل
 * دائرتها، فلا يحتاج نصًّا معدَّلًا ولا علامة ۝ قبل الرقم.
 *
 * ‼️ الملف غير مرفق: ضع `kfgqpc_hafs.ttf` في `app/src/main/res/font/`.
 */
enum class MushafFont(
    val key: String,
    val label: String,
    val note: String,
    /** كم يحتاج السطر من ارتفاع بالنسبة إلى حجم الخط، حتى لا تُقصّ العلامات العليا. */
    val lineHeightFactor: Float
) {
    KFGQPC("kfgqpc", "مصحف المدينة", "خط مجمع الملك فهد", 2.4f);

    val family: FontFamily
        get() = FontFamily(Font(R.font.kfgqpc_hafs))

    /**
     * تطبيع النصّ للعرض.
     *
     * النصّ يحمل الألف الخنجرية في موضعين مختلفين:
     * - ٨٦٠٩ موضعًا فوق الحرف مباشرة: `حَٰ`
     * - ١١١٧ موضعًا فوق **تطويل** يحملها بين حرفين: `نَّـٰت`
     *
     * وخطّ المصحف لا يُحسن وضع العلامة على التطويل فتبدو زائغة عن موضعها،
     * فنحذف التطويل الحامل لتستقرّ الألف على الحرف السابق. التطويل محرف تمديد
     * لا حرف ولا حركة، فحذفه لا يمسّ النصّ.
     */
    fun normalize(text: String): String =
        text.replace("\u0640\u0670", "\u0670")

    /** رقم الآية: الخط يرسم الأرقام مزخرفة داخل الدائرة، فلا نسبقها بـ U+06DD. */
    fun ayahMark(number: Int, arabicDigits: Boolean = true): String {
        if (!arabicDigits) return number.toString()
        val map = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return buildString { number.toString().forEach { append(map[it - '0']) } }
    }

    companion object {
        fun from(key: String?): MushafFont = KFGQPC
    }
}


/** نصوص الأذكار مشكولة أيضًا، فتستحق الخطّ نفسه. */
/** خط الواجهة يبقى خط النظام — خط المصحف مخصَّص للنص القرآني وحده. */
val ArabicFamily: FontFamily = FontFamily.Default

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ArabicFamily, fontWeight = FontWeight.Light,
        fontSize = 84.sp, lineHeight = 92.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ArabicFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 40.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ArabicFamily, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 34.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ArabicFamily, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, lineHeight = 34.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ArabicFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 26.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ArabicFamily, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp
    )
)

/**
 * نمط نصّ المصحف.
 *
 * خط Amiri Quran يحتاج **٢٫٤٥ ضعف حجم الخط** ارتفاعًا لسطره الواحد (ascent 1.815em،
 * descent 0.634em) لأن علامات الضبط القرآنية تتراكم فوق الحرف: شدّة فوقها تنوين ضمّ
 * فوقهما ألف خنجرية. وأي ارتفاع أقلّ يقصّ العلامات العليا فتختفي.
 *
 * ولهذا نضبط ثلاثة أمور معًا:
 * - lineHeight لا يقلّ عن ٢٫٦ من حجم الخط،
 * - includeFontPadding مفعَّل حتى لا يُقتطع حشو الخط،
 * - Trim.None فلا يُقصّ أعلى السطر الأول ولا أسفل الأخير.
 */
@Composable
fun quranTextStyle(
    fontSizeSp: Float,
    align: TextAlign = TextAlign.Justify,
    font: MushafFont = MushafFont.KFGQPC
): TextStyle = TextStyle(
    fontFamily = font.family,
    fontSize = fontSizeSp.sp,
    lineHeight = (fontSizeSp * font.lineHeightFactor).sp,
    textAlign = align,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

@Composable
fun DhikrTheme(
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        content = content
    )
}
