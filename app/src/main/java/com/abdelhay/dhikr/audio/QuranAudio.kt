package com.abdelhay.dhikr.audio

import android.content.Context
import java.io.File

/**
 * قارئ من قرّاء موقع everyayah.com.
 *
 * اخترنا هذا المصدر لأنه لا يحتاج واجهة برمجية ولا مفتاحًا: عنوان كل آية يُبنى
 * من رقم السورة والآية مباشرة، فلا طلب شبكة زائدًا قبل التشغيل، ويعمل التنزيل
 * والتشغيل بالمنطق نفسه.
 *
 * [folder] اسم المجلد على الخادم، و[kbps] يحدّد حجم التنزيل.
 */
data class Reciter(
    val key: String,
    val name: String,
    val folder: String,
    val kbps: Int,
    val style: String = "مرتَّل"
) {
    /** تقدير حجم المصحف كاملًا بالميغابايت بهذه الجودة. */
    val fullQuranMb: Int get() = (kbps * 60L * 60L * 55L / 8L / 1024L).toInt()
}

object Reciters {

    val all = listOf(
        // ── مشاهير القرّاء ──
        Reciter("husary", "محمود خليل الحصري", "Husary_128kbps", 128),
        Reciter("husary_mujawwad", "الحصري — مجوَّد", "Husary_Mujawwad_64kbps", 64, "مجوَّد"),
        Reciter("husary_muallim", "الحصري — المعلّم", "Husary_Muallim_128kbps", 128, "معلّم"),
        Reciter("abdulbasit", "عبد الباسط عبد الصمد", "Abdul_Basit_Murattal_64kbps", 64),
        Reciter("abdulbasit_192", "عبد الباسط — جودة عالية", "Abdul_Basit_Murattal_192kbps", 192),
        Reciter("abdulbasit_mujawwad", "عبد الباسط — مجوَّد", "Abdul_Basit_Mujawwad_128kbps", 128, "مجوَّد"),
        Reciter("minshawi", "محمد صديق المنشاوي", "Minshawy_Murattal_128kbps", 128),
        Reciter("minshawi_mujawwad", "المنشاوي — مجوَّد", "Minshawy_Mujawwad_192kbps", 192, "مجوَّد"),
        Reciter("minshawi_teacher", "المنشاوي — المعلّم", "Minshawy_Teacher_128kbps", 128, "معلّم"),
        Reciter("banna", "محمود علي البنا", "mahmoud_ali_al_banna_32kbps", 32),
        Reciter("tablaway", "محمد محمود الطبلاوي", "Mohammad_al_Tablaway_128kbps", 128),
        Reciter("mostafa_ismaeel", "مصطفى إسماعيل", "Mostafa_Ismaeel_128kbps", 128, "مجوَّد"),

        // ── أئمة الحرمين ──
        Reciter("sudais", "عبد الرحمن السديس", "Abdurrahmaan_As-Sudais_192kbps", 192),
        Reciter("shuraim", "سعود الشريم", "Saood_ash-Shuraym_128kbps", 128),
        Reciter("maher", "ماهر المعيقلي", "MaherAlMuaiqly128kbps", 128),
        Reciter("juhany", "عبد الله عواد الجهني", "Abdullaah_3awwaad_Al-Juhaynee_128kbps", 128),
        Reciter("dussary", "ياسر الدوسري", "Yasser_Ad-Dussary_128kbps", 128),
        Reciter("qasim", "عبد المحسن القاسم", "Muhsin_Al_Qasim_192kbps", 192),
        Reciter("budair", "صلاح البدير", "Salah_Al_Budair_128kbps", 128),
        Reciter("hudhaify", "علي الحذيفي", "Hudhaify_128kbps", 128),
        Reciter("matroud", "عبد الله المطرود", "Abdullah_Matroud_128kbps", 128),

        // ── قرّاء معاصرون ──
        Reciter("alafasy", "مشاري راشد العفاسي", "Alafasy_128kbps", 128),
        Reciter("shatri", "أبو بكر الشاطري", "Abu_Bakr_Ash-Shaatree_128kbps", 128),
        Reciter("ghamdi", "سعد الغامدي", "Ghamadi_40kbps", 40),
        Reciter("qatami", "ناصر القطامي", "Nasser_Alqatami_128kbps", 128),
        Reciter("ajamy", "أحمد بن علي العجمي", "Ahmed_ibn_Ali_al-Ajamy_128kbps", 128),
        Reciter("jibreel", "محمد جبريل", "Muhammad_Jibreel_128kbps", 128),
        Reciter("ayyoub", "محمد أيوب", "Muhammad_Ayyoub_128kbps", 128),
        Reciter("fares", "فارس عباد", "Fares_Abbad_64kbps", 64),
        Reciter("basfar", "عبد الله بصفر", "Abdullah_Basfar_192kbps", 192),
        Reciter("akhdar", "إبراهيم الأخضر", "Ibrahim_Akhdar_64kbps", 64),
        Reciter("hani_rifai", "هاني الرفاعي", "Hani_Rifai_192kbps", 192),
        Reciter("ali_jaber", "علي جابر", "Ali_Jaber_64kbps", 64),
        Reciter("bukhatir", "صلاح بو خاطر", "Salaah_AbdulRahman_Bukhatir_128kbps", 128),
        Reciter("tunaiji", "خليفة الطنيجي", "khalefa_al_tunaiji_64kbps", 64),
        Reciter("yaser_salamah", "ياسر سلامة", "Yaser_Salamah_128kbps", 128),
        Reciter("abdulkareem", "محمد عبد الكريم", "Muhammad_AbdulKareem_128kbps", 128),
        Reciter("sahl_yassin", "سهل ياسين", "Sahl_Yassin_128kbps", 128),
        Reciter("aziz_alili", "عزيز عليلي", "aziz_alili_128kbps", 128),
        Reciter("alaqimy", "أكرم العلاقمي", "Akram_AlAlaqimy_128kbps", 128),
        Reciter("neana", "أحمد نعينع", "Ahmed_Neana_128kbps", 128),
        Reciter("suesy", "علي حجاج السويسي", "Ali_Hajjaj_AlSuesy_128kbps", 128),
        Reciter("sowaid", "أيمن سويد", "Ayman_Sowaid_64kbps", 64, "معلّم")
    )

    fun from(key: String?): Reciter = all.firstOrNull { it.key == key } ?: all.first()

    /** بحث بالاسم — القائمة طويلة، والبحث أسرع من التمرير. */
    fun search(query: String): List<Reciter> {
        val q = query.trim()
        if (q.isEmpty()) return all
        val n = normalize(q)
        return all.filter { normalize(it.name).contains(n) || normalize(it.style).contains(n) }
    }

    private fun normalize(s: String): String = s
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي')
        .replace("ال", "")
        .trim()
}

object QuranAudio {

    private const val BASE = "https://everyayah.com/data"

    /** اسم ملف الآية: ثلاث خانات للسورة وثلاث للآية، كما في المصدر. */
    fun fileName(surah: Int, ayah: Int): String = "%03d%03d.mp3".format(surah, ayah)

    fun url(reciter: Reciter, surah: Int, ayah: Int): String =
        "$BASE/${reciter.folder}/${fileName(surah, ayah)}"

    /** مجلد التنزيل لكل قارئ على حدة. */
    fun dir(context: Context, reciter: Reciter): File =
        File(context.filesDir, "recitations/${reciter.key}").apply { mkdirs() }

    fun localFile(context: Context, reciter: Reciter, surah: Int, ayah: Int): File =
        File(dir(context, reciter), fileName(surah, ayah))

    /** المصدر المفضَّل: الملف المنزَّل إن وُجد، وإلا البثّ من الشبكة. */
    fun source(context: Context, reciter: Reciter, surah: Int, ayah: Int): String {
        val f = localFile(context, reciter, surah, ayah)
        return if (f.exists() && f.length() > 0) f.absolutePath else url(reciter, surah, ayah)
    }

    fun isDownloaded(context: Context, reciter: Reciter, surah: Int, ayah: Int): Boolean =
        localFile(context, reciter, surah, ayah).let { it.exists() && it.length() > 0 }

    /** حجم ما نُزّل لهذا القارئ بالميغابايت. */
    fun downloadedMb(context: Context, reciter: Reciter): Int {
        val bytes = dir(context, reciter).listFiles()?.sumOf { it.length() } ?: 0L
        return (bytes / (1024 * 1024)).toInt()
    }

    fun deleteAll(context: Context, reciter: Reciter) {
        dir(context, reciter).listFiles()?.forEach { it.delete() }
    }
}
