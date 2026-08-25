package com.abdelhay.dhikr

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.abdelhay.dhikr.data.AppDatabase
import com.abdelhay.dhikr.data.DhikrRepository
import com.abdelhay.dhikr.data.MIGRATION_1_2
import com.abdelhay.dhikr.data.MIGRATION_2_3
import com.abdelhay.dhikr.data.MushafRepository
import com.abdelhay.dhikr.data.QuranRepository
import com.abdelhay.dhikr.data.SettingsStore
import com.abdelhay.dhikr.prayer.AdhanNotifier
import com.abdelhay.dhikr.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DhikrApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "dhikr.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    val repository: DhikrRepository by lazy { DhikrRepository(database.dhikrDao()) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val quran: QuranRepository by lazy { QuranRepository(this) }
    val mushaf: MushafRepository by lazy { MushafRepository(this) }
    val quranDao by lazy { database.quranDao() }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch {
            val sound = settings.flow.first().adhanSound
            AdhanNotifier.createChannels(this@DhikrApp, sound)
        }
    }
}

/** وصول موحّد للتبعيات من أي مكان (Receiver / ViewModel). */
val Context.app: DhikrApp get() = applicationContext as DhikrApp
