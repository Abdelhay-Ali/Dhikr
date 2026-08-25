package com.abdelhay.dhikr.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DhikrDao {

    @Query("SELECT * FROM dhikr ORDER BY orderIndex ASC, id ASC")
    fun observeAll(): Flow<List<Dhikr>>

    @Query("SELECT * FROM dhikr WHERE isActive = 1 ORDER BY orderIndex ASC, id ASC")
    fun observeActive(): Flow<List<Dhikr>>

    @Query("SELECT * FROM dhikr WHERE isActive = 1 ORDER BY orderIndex ASC, id ASC")
    suspend fun activeOnce(): List<Dhikr>

    @Query("SELECT * FROM dhikr ORDER BY orderIndex ASC, id ASC")
    suspend fun allOnce(): List<Dhikr>

    @Query("SELECT * FROM dhikr WHERE id = :id")
    fun observeOne(id: Long): Flow<Dhikr?>

    @Query("SELECT * FROM dhikr WHERE id = :id")
    suspend fun byId(id: Long): Dhikr?

    @Query("SELECT COUNT(*) FROM dhikr")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM dhikr")
    suspend fun nextOrderIndex(): Int

    @Insert
    suspend fun insert(dhikr: Dhikr): Long

    @Insert
    suspend fun insertAll(items: List<Dhikr>)

    @Update
    suspend fun update(dhikr: Dhikr)

    @Delete
    suspend fun delete(dhikr: Dhikr)

    @Query("UPDATE dhikr SET raw = :raw, lifetime = lifetime + :addToLifetime, lastResetDate = :today WHERE id = :id")
    suspend fun setRaw(id: Long, raw: Int, addToLifetime: Int, today: String)

    // ---------- السجل اليومي ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: DailyLog)

    @Query("SELECT * FROM daily_log WHERE date = :date")
    suspend fun logsFor(date: String): List<DailyLog>

    @Query(
        """
        SELECT date,
               SUM(done) AS total,
               SUM(CASE WHEN done >= target AND target > 0 THEN 1 ELSE 0 END) AS completedCount,
               COUNT(*) AS dhikrCount
        FROM daily_log
        GROUP BY date
        ORDER BY date DESC
        LIMIT :limit
        """
    )
    fun observeDaySummaries(limit: Int = 120): Flow<List<DaySummary>>

    @Query("SELECT COALESCE(SUM(lifetime), 0) FROM dhikr")
    fun observeLifetimeTotal(): Flow<Long>

    // ---------- المؤقّت ----------

    @Query("SELECT * FROM session_day WHERE date = :date")
    suspend fun sessionDay(date: String): SessionDay?

    @Query("SELECT * FROM session_day WHERE date = :date")
    fun observeSessionDay(date: String): Flow<SessionDay?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessionDay(day: SessionDay)

    @Query("SELECT COALESCE(SUM(seconds), 0) FROM session_day")
    fun observeSecondsTotal(): Flow<Long>

    @Query("SELECT COALESCE(SUM(seconds), 0) FROM session_day WHERE date >= :fromDate")
    fun observeSecondsSince(fromDate: String): Flow<Long>

    @Query("UPDATE dhikr SET secondsLifetime = secondsLifetime + :delta WHERE id = :id")
    suspend fun addDhikrSeconds(id: Long, delta: Long)
}

@Database(
    entities = [Dhikr::class, DailyLog::class, SessionDay::class, Bookmark::class, Khatma::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dhikrDao(): DhikrDao
    abstract fun quranDao(): QuranDao
}

/**
 * ترقية من الإصدار ١: إضافة حقلي المصدر والزمن، وجدول أزمنة الأيام.
 * ترقية غير هدّامة — بيانات المستخدم القديمة تبقى كما هي.
 */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dhikr ADD COLUMN source TEXT")
        db.execSQL("ALTER TABLE dhikr ADD COLUMN secondsLifetime INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS session_day (" +
                "date TEXT NOT NULL PRIMARY KEY, " +
                "seconds INTEGER NOT NULL DEFAULT 0, " +
                "sessions INTEGER NOT NULL DEFAULT 0)"
        )
    }
}

/** ترقية ٢ ← ٣: جدولا الفواصل والختمة. */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS bookmark (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "surah INTEGER NOT NULL, ayah INTEGER NOT NULL, " +
                "surahName TEXT NOT NULL, note TEXT, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS khatma (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "startDate TEXT NOT NULL, juzPerDay REAL NOT NULL DEFAULT 1.0, " +
                "progress INTEGER NOT NULL DEFAULT 0, lastReadDate TEXT NOT NULL DEFAULT '', " +
                "completedAt INTEGER, isActive INTEGER NOT NULL DEFAULT 1)"
        )
    }
}

class Converters {
    @TypeConverter fun modeToString(m: CountMode): String = m.name
    @TypeConverter fun stringToMode(s: String): CountMode =
        runCatching { CountMode.valueOf(s) }.getOrDefault(CountMode.UP)
}
