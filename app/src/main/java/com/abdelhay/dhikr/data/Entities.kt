package com.abdelhay.dhikr.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** طريقة العد: تصاعدي من صفر إلى الهدف، أو تنازلي من الهدف إلى صفر. */
enum class CountMode { UP, DOWN }

@Entity(tableName = "dhikr")
data class Dhikr(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** نص الذكر كما يقرأه المستخدم. */
    val text: String,

    /** العدد المطلوب في اليوم. */
    val target: Int = 33,

    val mode: CountMode = CountMode.UP,

    /**
     * القيمة الخام للعدّاد.
     * في الوضع التصاعدي: تبدأ من 0 وتزيد.
     * في الوضع التنازلي: تبدأ من [target] وتنقص إلى 0.
     */
    val raw: Int = 0,

    val orderIndex: Int = 0,

    /** الأذكار غير المفعّلة تبقى محفوظة لكنها خارج ورد اليوم. */
    val isActive: Boolean = true,

    /** آخر يوم أُعيد فيه ضبط العدّاد (yyyy-MM-dd) — أساس التصفير التلقائي. */
    val lastResetDate: String = "",

    /** المجموع التراكمي منذ إنشاء الذكر — لا يُصفَّر. */
    val lifetime: Long = 0,

    /** ملاحظة أو فضل الذكر، تظهر تحت النص. */
    val note: String? = null,

    /** تخريج الحديث أو موضع الآية. */
    val source: String? = null,

    /** الثواني التي قضاها المستخدم على هذا الذكر تراكميًّا. */
    val secondsLifetime: Long = 0
) {
    /** عدد ما تم فعلًا اليوم، موحّدًا بين وضعي العد. */
    val done: Int get() = if (mode == CountMode.UP) raw else (target - raw).coerceAtLeast(0)

    /** الرقم الكبير المعروض على الشاشة: تصاعدي يعرض المُنجز، تنازلي يعرض المتبقّي. */
    val display: Int get() = if (mode == CountMode.UP) raw else raw

    val remaining: Int get() = (target - done).coerceAtLeast(0)

    val isCompleted: Boolean get() = done >= target && target > 0

    val progress: Float get() = if (target <= 0) 0f else (done.toFloat() / target).coerceIn(0f, 1f)

    /** قيمة العدّاد عند بداية يوم جديد. */
    fun startValue(): Int = if (mode == CountMode.UP) 0 else target
}

/** سجل يوم واحد لذكر واحد — يُستعمل في الإحصاءات وسلسلة المواظبة. */
@Entity(
    tableName = "daily_log",
    primaryKeys = ["date", "dhikrId"],
    indices = [Index("date")]
)
data class DailyLog(
    val date: String,      // yyyy-MM-dd
    val dhikrId: Long,
    val done: Int,
    val target: Int
)

/** ملخّص يوم كامل، ناتج استعلام تجميعي. */
data class DaySummary(
    val date: String,
    val total: Int,
    val completedCount: Int,
    val dhikrCount: Int
) {
    val isFullDay: Boolean get() = dhikrCount > 0 && completedCount == dhikrCount
}

/** زمن الذكر في يوم واحد — أساس المؤقت والإحصاءات الزمنية. */
@Entity(tableName = "session_day")
data class SessionDay(
    @PrimaryKey val date: String,   // yyyy-MM-dd
    val seconds: Long = 0,
    val sessions: Int = 0
)
