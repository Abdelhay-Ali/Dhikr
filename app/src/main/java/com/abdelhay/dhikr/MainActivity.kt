package com.abdelhay.dhikr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.abdelhay.dhikr.ui.screens.*
import com.abdelhay.dhikr.ui.theme.DhikrTheme
import com.abdelhay.dhikr.prayer.AdhanPlayerService
import com.abdelhay.dhikr.util.LocalStrings
import com.abdelhay.dhikr.util.ProvideStrings
import com.abdelhay.dhikr.util.VolumeKeyBus
import com.abdelhay.dhikr.vm.DhikrViewModel
import com.abdelhay.dhikr.vm.PrayerViewModel
import com.abdelhay.dhikr.data.AdhkarSet
import androidx.compose.material.icons.outlined.Radio
import com.abdelhay.dhikr.vm.AdhkarSessionViewModel
import com.abdelhay.dhikr.vm.RadioViewModel
import com.abdelhay.dhikr.vm.SurahReciterViewModel
import com.abdelhay.dhikr.vm.QuranViewModel

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_DHIKR = "open_dhikr"
        const val EXTRA_OPEN_TAB = "open_tab"
    }

    private var pendingDhikrId: Long? = null
    private var pendingTab: String? = null

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntent(intent)
        askNotificationPermission()

        setContent {
            val vm: DhikrViewModel = viewModel()
            val quranVm: QuranViewModel = viewModel()
            val prayerVm: PrayerViewModel = viewModel()
            val adhkarVm: AdhkarSessionViewModel = viewModel()
            val radioVm: RadioViewModel = viewModel()
            val surahReciterVm: SurahReciterViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()

            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            DhikrTheme(darkOverride = settings.darkTheme) {
                // الاتجاه يتبع اللغة: العربية من اليمين، والإنجليزية من اليسار
                val direction =
                    if (settings.language == "en") LayoutDirection.Ltr else LayoutDirection.Rtl
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                  ProvideStrings(settings.language) {
                    AppScaffold(
                        vm = vm,
                        quranVm = quranVm,
                        prayerVm = prayerVm,
                        adhkarVm = adhkarVm,
                        radioVm = radioVm,
                        surahReciterVm = surahReciterVm,
                        startDhikrId = pendingDhikrId,
                        startTab = pendingTab,
                        homeTab = settings.startTab,
                        onRequestLocationPermission = ::askLocationPermission
                    )
                  }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        recreate()
    }

    private fun readIntent(intent: Intent?) {
        pendingDhikrId = intent?.getLongExtra(EXTRA_OPEN_DHIKR, -1L)?.takeIf { it > 0 }
        pendingTab = intent?.getStringExtra(EXTRA_OPEN_TAB)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val isVolume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        // الأذان أولى بزرّ الصوت من العدّاد: أول ضغطة تُسكته
        if (isVolume && AdhanPlayerService.isPlaying) {
            AdhanPlayerService.stop(this)
            return true
        }

        if (isVolume && VolumeKeyBus.enabled && VolumeKeyBus.active) {
            VolumeKeyBus.events.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val isVolume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isVolume && AdhanPlayerService.isPlaying) return true
        if (isVolume && VolumeKeyBus.enabled && VolumeKeyBus.active) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun askLocationPermission() {
        locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }
}

private object Route {
    const val WIRD = "wird"
    const val QURAN = "quran"
    const val PRAYER = "prayer"
    const val RADIO = "radio"
    const val COUNTER = "counter/{id}"
    const val SURAH = "surah/{id}?ayah={ayah}"
    const val EDIT = "edit?id={id}"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val QIBLA = "qibla"
    const val KHATMA_READ = "khatma_read"
    const val RECITATION = "recitation"
    const val ADHKAR_SET = "adhkar/{set}"
    fun adhkarSet(key: String) = "adhkar/$key"
    fun counter(id: Long) = "counter/$id"
    fun surah(id: Int, ayah: Int? = null) =
        if (ayah == null) "surah/$id?ayah=-1" else "surah/$id?ayah=$ayah"
    fun edit(id: Long?) = if (id == null) "edit?id=-1" else "edit?id=$id"
}

private data class Tab(val route: String, val icon: ImageVector, val label: @Composable () -> String)

private val tabs = listOf(
    Tab(Route.WIRD, Icons.Outlined.RadioButtonChecked) { LocalStrings.current.tabWird },
    Tab(Route.QURAN, Icons.Outlined.MenuBook) { LocalStrings.current.tabQuran },
    Tab(Route.PRAYER, Icons.Outlined.Mosque) { LocalStrings.current.tabPrayer },
    Tab(Route.RADIO, Icons.Outlined.Radio) { LocalStrings.current.tabRadio }
)

@Composable
private fun AppScaffold(
    vm: DhikrViewModel,
    quranVm: QuranViewModel,
    prayerVm: PrayerViewModel,
    adhkarVm: AdhkarSessionViewModel,
    radioVm: RadioViewModel,
    surahReciterVm: SurahReciterViewModel,
    startDhikrId: Long?,
    startTab: String?,
    homeTab: String,
    onRequestLocationPermission: () -> Unit
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    // الشريط السفلي يظهر في الأقسام الثلاثة فقط، لا في شاشات القراءة والعدّ
    val showBar = currentRoute in tabs.map { it.route }

    LaunchedEffect(startDhikrId, startTab) {
        vm.onResumeApp()
        when {
            startDhikrId != null -> nav.navigate(Route.counter(startDhikrId))
            startTab == "prayer" -> nav.navigate(Route.PRAYER)
            startTab == "after_prayer" -> nav.navigate(Route.adhkarSet(AdhkarSet.AFTER_PRAYER.key))
            startTab == "quran" -> nav.navigate(Route.QURAN)
            startTab == "radio" -> nav.navigate(Route.RADIO)
        }
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = entry?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label()) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = when (homeTab) {
                "quran" -> Route.QURAN
                "wird" -> Route.WIRD
                "radio" -> Route.RADIO
                else -> Route.PRAYER
            },
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        ) {
            composable(Route.WIRD) {
                HomeScreen(
                    vm = vm,
                    onOpenCounter = { nav.navigate(Route.counter(it)) },
                    onAdd = { nav.navigate(Route.edit(null)) },
                    onEdit = { nav.navigate(Route.edit(it)) },
                    onStats = { nav.navigate(Route.STATS) },
                    onSettings = { nav.navigate(Route.SETTINGS) },
                    onOpenSet = { nav.navigate(Route.adhkarSet(it.key)) }
                )
            }

            composable(Route.QURAN) {
                QuranIndexScreen(
                    vm = quranVm,
                    onOpenSurah = { id, ayah -> nav.navigate(Route.surah(id, ayah)) },
                    onOpenKhatma = { nav.navigate(Route.KHATMA_READ) },
                    onOpenRecitation = { nav.navigate(Route.RECITATION) }
                )
            }

            composable(Route.PRAYER) {
                PrayerScreen(
                    vm = prayerVm,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onOpenQibla = { nav.navigate(Route.QIBLA) }
                )
            }

            composable(
                Route.SURAH,
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("ayah") { type = NavType.IntType; defaultValue = -1 }
                )
            ) { e ->
                val id = e.arguments?.getInt("id") ?: 1
                val ayah = e.arguments?.getInt("ayah")?.takeIf { it > 0 }
                SurahScreen(
                    vm = quranVm,
                    surahId = id,
                    startAyah = ayah,
                    onBack = { nav.popBackStack() }
                )
            }

            composable(
                Route.COUNTER,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                val id = e.arguments?.getLong("id") ?: return@composable
                CounterScreen(
                    vm = vm,
                    dhikrId = id,
                    onBack = { nav.popBackStack() },
                    onSwitch = { next ->
                        nav.navigate(Route.counter(next)) { popUpTo(Route.WIRD) }
                    }
                )
            }

            composable(
                Route.EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
            ) { e ->
                val id = e.arguments?.getLong("id")?.takeIf { it > 0 }
                EditDhikrScreen(vm = vm, dhikrId = id, onDone = { nav.popBackStack() })
            }

            composable(
                Route.ADHKAR_SET,
                arguments = listOf(navArgument("set") { type = NavType.StringType })
            ) { e ->
                AdhkarSessionScreen(
                    vm = adhkarVm,
                    set = AdhkarSet.from(e.arguments?.getString("set")),
                    onBack = { nav.popBackStack() }
                )
            }

            composable(Route.RADIO) {
                val names by quranVm.index.collectAsStateWithLifecycle()
                RadioScreen(
                    vm = radioVm,
                    surahVm = surahReciterVm,
                    surahNames = names.map { it.id to it.name }
                )
            }

            composable(Route.RECITATION) {
                RecitationScreen(vm = quranVm, onBack = { nav.popBackStack() })
            }

            composable(Route.KHATMA_READ) {
                KhatmaReaderScreen(vm = quranVm, onBack = { nav.popBackStack() })
            }

            composable(Route.QIBLA) { QiblaScreen(prayerVm, onBack = { nav.popBackStack() }) }
            composable(Route.STATS) { StatsScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Route.SETTINGS) { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
        }
    }
}
