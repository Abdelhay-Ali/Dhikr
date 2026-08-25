package com.abdelhay.dhikr.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** فاصل — موضع محفوظ في المصحف. */
@Entity(tableName = "bookmark")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surah: Int,
    val ayah: Int,
    val surahName: String,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * الختمة الجارية.
 *
 * الموضع يُحفظ بالترقيم المتصل للآيات (١..٦٢٣٦) لا بـ(سورة، آية)،
 * فيصير حساب «كم بقي» و«هل أنا متأخّر» طرحًا بسيطًا.
 */
@Entity(tableName = "khatma")
data class Khatma(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** yyyy-MM-dd */
    val startDate: String,
    /** كم جزءًا في اليوم — يقبل الكسر (٠٫٥ = نصف جزء). */
    val juzPerDay: Double = 1.0,
    /** آخر آية قرأها بالترقيم المتصل؛ صفر يعني لم يبدأ بعد. */
    val progress: Int = 0,
    /** yyyy-MM-dd لآخر يوم سُجّل فيه تقدّم — لحساب المواظبة. */
    val lastReadDate: String = "",
    val completedAt: Long? = null,
    val isActive: Boolean = true
) {
    val totalVerses: Int get() = 6236
    val fraction: Float get() = (progress.toFloat() / totalVerses).coerceIn(0f, 1f)
    val isDone: Boolean get() = progress >= totalVerses
}

@Dao
interface QuranDao {

    // ── الفواصل ──

    @Query("SELECT * FROM bookmark ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<Bookmark>>

    @Insert
    suspend fun addBookmark(b: Bookmark): Long

    @Delete
    suspend fun deleteBookmark(b: Bookmark)

    @Query("DELETE FROM bookmark WHERE surah = :surah AND ayah = :ayah")
    suspend fun deleteBookmarkAt(surah: Int, ayah: Int)

    @Query("SELECT COUNT(*) FROM bookmark WHERE surah = :surah AND ayah = :ayah")
    suspend fun bookmarkCountAt(surah: Int, ayah: Int): Int

    // ── الختمة ──

    @Query("SELECT * FROM khatma WHERE isActive = 1 LIMIT 1")
    fun observeActiveKhatma(): Flow<Khatma?>

    @Query("SELECT * FROM khatma WHERE isActive = 1 LIMIT 1")
    suspend fun activeKhatma(): Khatma?

    @Query("SELECT * FROM khatma ORDER BY id DESC")
    fun observeAllKhatmas(): Flow<List<Khatma>>

    @Insert
    suspend fun insertKhatma(k: Khatma): Long

    @Update
    suspend fun updateKhatma(k: Khatma)

    @Delete
    suspend fun deleteKhatma(k: Khatma)

    @Query("UPDATE khatma SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAll()
}
