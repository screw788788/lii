package dev.lumen.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val icon: ImageBitmap,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val DOCK_SIZE = 4
        const val ICON_DP = 58
        const val SHADOW_DP = 6
    }

    private val context: Context = application
    private val launcherApps: LauncherApps = application.getSystemService(LauncherApps::class.java)
    private val prefs = application.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE)

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var homeKeys by mutableStateOf(readList("home"))
        private set

    var dockKeys by mutableStateOf(readList("dock"))
        private set

    /** false = pick your own home apps (drawer for the rest); true = every app lives on the home pages. */
    var allOnHome by mutableStateOf(prefs.getBoolean("all_on_home", false))
        private set

    var homeSignal by mutableIntStateOf(0)
        private set

    var resumeSignal by mutableIntStateOf(0)
        private set

    var now by mutableLongStateOf(System.currentTimeMillis())
        private set

    val homeApps: List<AppEntry> by derivedStateOf {
        if (allOnHome) {
            val docked = dockKeys.toSet()
            apps.filter { it.key !in docked }
        } else {
            resolve(homeKeys)
        }
    }
    val dockApps: List<AppEntry> by derivedStateOf { resolve(dockKeys) }

    private var refreshJob: Job? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh(delayMs = 400)
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(application, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    override fun onCleared() {
        runCatching { context.unregisterReceiver(packageReceiver) }
    }

    // ---- events -----------------------------------------------------------

    fun onHomePressed() {
        homeSignal++
    }

    fun onResumed() {
        resumeSignal++
    }

    fun updateTime() {
        now = System.currentTimeMillis()
    }

    // ---- layout editing ---------------------------------------------------

    fun addToHome(app: AppEntry) {
        if (app.key in homeKeys) return
        dockKeys = dockKeys - app.key
        homeKeys = homeKeys + app.key
        save("dock", dockKeys)
        save("home", homeKeys)
    }

    fun removeFromHome(app: AppEntry) {
        homeKeys = homeKeys - app.key
        save("home", homeKeys)
    }

    fun addToDock(app: AppEntry) {
        if (app.key in dockKeys || dockKeys.size >= DOCK_SIZE) return
        homeKeys = homeKeys - app.key
        dockKeys = dockKeys + app.key
        save("home", homeKeys)
        save("dock", dockKeys)
    }

    fun removeFromDock(app: AppEntry) {
        dockKeys = dockKeys - app.key
        save("dock", dockKeys)
    }

   fun changeAllOnHome(value: Boolean) {
       allOnHome = value
        prefs.edit().putBoolean("all_on_home", value).apply()
    }

    fun resetLayout() {
        prefs.edit().remove("home").remove("dock").apply()
        homeKeys = emptyList()
        dockKeys = emptyList()
        ensureDefaults(apps)
    }

    // ---- loading ----------------------------------------------------------

    private fun refresh(delayMs: Long = 0L) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val list = withContext(Dispatchers.Default) { loadApps() }
            apps = list
            ensureDefaults(list)
        }
    }

    private fun loadApps(): List<AppEntry> {
        val density = context.resources.displayMetrics.density
        val iconPx = (ICON_DP * density).toInt().coerceAtLeast(96)
        val padPx = (SHADOW_DP * density).toInt().coerceAtLeast(6)
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .map { info ->
                AppEntry(
                    key = info.componentName.flattenToString(),
                    label = info.label?.toString().orEmpty(),
                    component = info.componentName,
                    icon = info.getIcon(0).toIconBitmap(iconPx, padPx).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun resolve(keys: List<String>): List<AppEntry> {
        val map = apps.associateBy { it.key }
        return keys.mapNotNull { map[it] }
    }

    /**
     * First run: dock = phone / messages / browser / camera,
     * home = the usual suspects (gallery, calendar, settings, clock, maps, ...) the system can find.
     */
    private fun ensureDefaults(list: List<AppEntry>) {
        if (list.isEmpty()) return

        if (!prefs.contains("dock")) {
            val picks = defaultDockPackages()
                .mapNotNull { pkg -> list.firstOrNull { it.component.packageName == pkg } }
                .distinctBy { it.key }
                .take(DOCK_SIZE)
            dockKeys = picks.map { it.key }
            save("dock", dockKeys)
        }

        if (!prefs.contains("home")) {
            val docked = dockKeys.toSet()
            val picks = defaultHomePackages()
                .mapNotNull { pkg -> list.firstOrNull { it.component.packageName == pkg } }
                .distinctBy { it.key }
                .filter { it.key !in docked }
                .take(12)
                .toMutableList()
            if (picks.size < 4) {
                val have = picks.map { it.key }.toSet()
                picks += list.filter { it.key !in docked && it.key !in have }.take(8 - picks.size)
            }
            homeKeys = picks.map { it.key }
            save("home", homeKeys)
        }
    }

    private fun firstPackage(intent: Intent): String? {
        val pm = context.packageManager
        val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (resolved != null && resolved != "android") return resolved
        return pm.queryIntentActivities(intent, 0)
            .firstOrNull()?.activityInfo?.packageName
            ?.takeIf { it != "android" }
    }

    private fun defaultDockPackages(): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        return intents.mapNotNull { firstPackage(it) }
    }

    private fun defaultHomePackages(): List<String> {
        val categories = listOf(
            Intent.CATEGORY_APP_GALLERY,
            Intent.CATEGORY_APP_CALENDAR,
            Intent.CATEGORY_APP_EMAIL,
            Intent.CATEGORY_APP_MAPS,
            Intent.CATEGORY_APP_MUSIC,
            Intent.CATEGORY_APP_CALCULATOR,
            Intent.CATEGORY_APP_MARKET,
            Intent.CATEGORY_APP_FILES,
            Intent.CATEGORY_APP_WEATHER,
        )
        val intents = buildList {
            add(Intent(Settings.ACTION_SETTINGS))
            add(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            categories.forEach { add(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, it)) }
        }
        return intents.mapNotNull { firstPackage(it) }
    }

    // ---- persistence ------------------------------------------------------

    private fun readList(name: String): List<String> =
        prefs.getString(name, null)?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

    private fun save(name: String, list: List<String>) {
        prefs.edit().putString(name, list.joinToString(",")).apply()
    }
}
