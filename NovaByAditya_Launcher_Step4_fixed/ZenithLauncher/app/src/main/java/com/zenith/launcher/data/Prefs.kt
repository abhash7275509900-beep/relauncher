package com.zenith.launcher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Folder(val id: String, val name: String, val pkgs: List<String>)

/** type is "app" (ref = package) or "folder" (ref = folder id). */
data class HomeItem(val id: String, val type: String, val ref: String, val page: Int, val x: Int, val y: Int)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    var themeId by mutableStateOf(sp.getString("themeId", "obsidian") ?: "obsidian")
        private set
    /** 0 = theme accent, 1 = wallpaper colours (Android 12+), 2 = custom */
    @set:JvmName("assignAccentMode")
    var accentMode by mutableIntStateOf(sp.getInt("accentMode", 0))
        private set
    var accentIndex by mutableIntStateOf(sp.getInt("accent", 0))
        private set
    @set:JvmName("assignColumns")
    var columns by mutableIntStateOf(sp.getInt("cols", 4))
        private set
    @set:JvmName("assignIconScale")
    var iconScale by mutableFloatStateOf(sp.getFloat("iconScale", 1f))
        private set
    var showLabels by mutableStateOf(sp.getBoolean("labels", true))
        private set
    @set:JvmName("assignDrawerOpacity")
    var drawerOpacity by mutableFloatStateOf(sp.getFloat("drawerOpacity", 0.92f))
        private set
    @set:JvmName("assignReducedMotion")
    var reducedMotion by mutableStateOf(sp.getBoolean("reduced", false))
        private set
    @set:JvmName("assignHaptics")
    var haptics by mutableStateOf(sp.getBoolean("haptics", true))
        private set
    /** 0 = original icons, 1 = monochrome */
    @set:JvmName("assignIconStyle")
    var iconStyle by mutableIntStateOf(sp.getInt("iconStyle", 0))
        private set
    /** 0 = light, 1 = bold, 2 = compact */
    @set:JvmName("assignClockStyle")
    var clockStyle by mutableIntStateOf(sp.getInt("clockStyle", 0))
        private set
    @set:JvmName("assignWallpaperDim")
    var wallpaperDim by mutableFloatStateOf(sp.getFloat("wallpaperDim", 0.10f))
        private set
    @set:JvmName("assignHighContrast")
    var highContrast by mutableStateOf(sp.getBoolean("highContrast", false))
        private set
    /** Gesture actions: none, drawer, search, notifications, edit, settings */
    @set:JvmName("assignGestureUp")
    var gestureUp by mutableStateOf(sp.getString("gUp", "drawer") ?: "drawer")
        private set
    @set:JvmName("assignGestureDown")
    var gestureDown by mutableStateOf(sp.getString("gDown", "notifications") ?: "notifications")
        private set
    @set:JvmName("assignGestureDouble")
    var gestureDouble by mutableStateOf(sp.getString("gDouble", "none") ?: "none")
        private set
    @set:JvmName("assignGestureLong")
    var gestureLong by mutableStateOf(sp.getString("gLong", "edit") ?: "edit")
        private set
    /** Swipe distance in dp needed to trigger a gesture. */
    @set:JvmName("assignGestureSens")
    var gestureSens by mutableFloatStateOf(sp.getFloat("gSens", 48f))
        private set
    @set:JvmName("assignHapticLevel")
    var hapticLevel by mutableIntStateOf(sp.getInt("hapticLevel", 1))
        private set
    /** 0 off, 1 follow system dark mode, 2 by time of day */
    @set:JvmName("assignScheduleMode")
    var scheduleMode by mutableIntStateOf(sp.getInt("schedMode", 0))
        private set
    @set:JvmName("assignDayTheme")
    var dayTheme by mutableStateOf(sp.getString("dayTheme", "arctic") ?: "arctic")
        private set
    @set:JvmName("assignNightTheme")
    var nightTheme by mutableStateOf(sp.getString("nightTheme", "obsidian") ?: "obsidian")
        private set
    @set:JvmName("assignDayStart")
    var dayStart by mutableIntStateOf(sp.getInt("dayStart", 7))
        private set
    @set:JvmName("assignNightStart")
    var nightStart by mutableIntStateOf(sp.getInt("nightStart", 20))
        private set
    @set:JvmName("assignIconPack")
    var iconPack by mutableStateOf(sp.getString("iconPack", "") ?: "")
        private set
    var iconOverrides by mutableStateOf(loadOverrides())
        private set
    @set:JvmName("assignNotificationDots")
    var notificationDots by mutableStateOf(sp.getBoolean("dots", false))
        private set
    var customThemes by mutableStateOf(loadCustomThemes())
        private set
    @set:JvmName("assignOnboarded")
    var onboarded by mutableStateOf(sp.getBoolean("onboarded", false))
        private set
    @set:JvmName("assignHidden")
    var hidden by mutableStateOf(sp.getStringSet("hidden", emptySet())?.toSet() ?: emptySet())
        private set
    @set:JvmName("assignDock")
    var dock by mutableStateOf(
        (sp.getString("dock", "") ?: "").split(",").filter { it.isNotBlank() }
    )
        private set
    var usage by mutableStateOf(loadUsage())
        private set
    var folders by mutableStateOf(loadFolders())
        private set

    var homeCols by mutableIntStateOf(sp.getInt("homeCols", 4).coerceIn(3, 6))
        private set
    var homeRows by mutableIntStateOf(sp.getInt("homeRows", 5).coerceIn(3, 7))
        private set
    var homePages by mutableIntStateOf(sp.getInt("homePages", 1).coerceIn(1, MAX_PAGES))
        private set
    var homeItems by mutableStateOf(loadHome())
        private set

    init {
        // Schema versioning so later steps can migrate safely.
        val old = sp.getInt("schema", 0)
        if (old < 3 && homeItems.isEmpty() && folders.isNotEmpty()) {
            // Step 1 showed folders in a row; place them on the first Home page.
            val items = folders.mapIndexed { i, f ->
                HomeItem(UUID.randomUUID().toString(), "folder", f.id, 0, i % homeCols, i / homeCols)
            }
            setHome(items, 1)
        }
        if (old < SCHEMA) sp.edit().putInt("schema", SCHEMA).apply()
    }

    private fun loadHome(): List<HomeItem> = try {
        val arr = JSONArray(sp.getString("home", "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val type = o.optString("type")
            val ref = o.optString("ref")
            if ((type != "app" && type != "folder") || ref.isBlank()) return@mapNotNull null
            HomeItem(o.optString("id").ifBlank { UUID.randomUUID().toString() }, type, ref,
                o.optInt("page", 0).coerceIn(0, MAX_PAGES - 1), o.optInt("x", 0).coerceAtLeast(0), o.optInt("y", 0).coerceAtLeast(0))
        }
    } catch (_: Exception) { emptyList() }

    /** Saves the whole layout in one write so a crash can't leave it half-saved. */
    fun setHome(items: List<HomeItem>, pages: Int) {
        homeItems = items
        homePages = pages.coerceIn(1, MAX_PAGES)
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("type", it.type).put("ref", it.ref)
                .put("page", it.page).put("x", it.x).put("y", it.y))
        }
        sp.edit().putString("home", arr.toString()).putInt("homePages", homePages).apply()
    }

    fun setHomeGrid(cols: Int, rows: Int) {
        homeCols = cols; homeRows = rows
        sp.edit().putInt("homeCols", cols).putInt("homeRows", rows).apply()
    }

    private fun loadOverrides(): Map<String, String> = try {
        val o = JSONObject(sp.getString("iconOverrides", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it.contains("|") }
    } catch (_: Exception) { emptyMap() }

    private fun loadCustomThemes(): List<String> = try {
        val a = JSONArray(sp.getString("customThemes", "[]") ?: "[]")
        (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.take(10)
    } catch (_: Exception) { emptyList() }

    private fun loadUsage(): Map<String, Int> =
        sp.all.filterKeys { it.startsWith("use_") }
            .mapKeys { it.key.removePrefix("use_") }
            .mapValues { (it.value as? Int) ?: 0 }

    /** Corrupt or partial data never crashes startup: it falls back to no folders. */
    private fun loadFolders(): List<Folder> = try {
        val arr = JSONArray(sp.getString("folders", "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            val pk = o.optJSONArray("pkgs") ?: JSONArray()
            Folder(id, o.optString("name", "Folder").take(24), (0 until pk.length()).map { pk.optString(it) }.filter { it.isNotBlank() })
        }
    } catch (_: Exception) { emptyList() }

    private fun saveFolders(list: List<Folder>) {
        folders = list
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(JSONObject().put("id", f.id).put("name", f.name).put("pkgs", JSONArray(f.pkgs)))
        }
        sp.edit().putString("folders", arr.toString()).apply()
    }

    fun setTheme(v: String) { themeId = v; sp.edit().putString("themeId", v).apply() }
    fun setAccentMode(v: Int) { accentMode = v; sp.edit().putInt("accentMode", v).apply() }
    fun setAccent(v: Int) { accentIndex = v; accentMode = 2; sp.edit().putInt("accent", v).putInt("accentMode", 2).apply() }
    fun setColumns(v: Int) { columns = v; sp.edit().putInt("cols", v).apply() }
    fun setIconScale(v: Float) { iconScale = v; sp.edit().putFloat("iconScale", v).apply() }
    fun setLabels(v: Boolean) { showLabels = v; sp.edit().putBoolean("labels", v).apply() }
    fun setDrawerOpacity(v: Float) { drawerOpacity = v; sp.edit().putFloat("drawerOpacity", v).apply() }
    fun setReducedMotion(v: Boolean) { reducedMotion = v; sp.edit().putBoolean("reduced", v).apply() }
    fun setHaptics(v: Boolean) { haptics = v; sp.edit().putBoolean("haptics", v).apply() }
    fun setIconStyle(v: Int) { iconStyle = v; sp.edit().putInt("iconStyle", v).apply() }
    fun setClockStyle(v: Int) { clockStyle = v; sp.edit().putInt("clockStyle", v).apply() }
    fun setWallpaperDim(v: Float) { wallpaperDim = v; sp.edit().putFloat("wallpaperDim", v).apply() }
    fun setHighContrast(v: Boolean) { highContrast = v; sp.edit().putBoolean("highContrast", v).apply() }
    fun setGestureUp(v: String) { gestureUp = v; sp.edit().putString("gUp", v).apply() }
    fun setGestureDown(v: String) { gestureDown = v; sp.edit().putString("gDown", v).apply() }
    fun setGestureDouble(v: String) { gestureDouble = v; sp.edit().putString("gDouble", v).apply() }
    fun setGestureLong(v: String) { gestureLong = v; sp.edit().putString("gLong", v).apply() }
    fun setGestureSens(v: Float) { gestureSens = v; sp.edit().putFloat("gSens", v).apply() }
    fun setHapticLevel(v: Int) { hapticLevel = v; sp.edit().putInt("hapticLevel", v).apply() }
    fun setScheduleMode(v: Int) { scheduleMode = v; sp.edit().putInt("schedMode", v).apply() }
    fun setDayTheme(v: String) { dayTheme = v; sp.edit().putString("dayTheme", v).apply() }
    fun setNightTheme(v: String) { nightTheme = v; sp.edit().putString("nightTheme", v).apply() }
    fun setDayStart(v: Int) { dayStart = v; sp.edit().putInt("dayStart", v).apply() }
    fun setNightStart(v: Int) { nightStart = v; sp.edit().putInt("nightStart", v).apply() }
    fun setIconPack(v: String) { iconPack = v; sp.edit().putString("iconPack", v).apply() }
    fun setNotificationDots(v: Boolean) { notificationDots = v; sp.edit().putBoolean("dots", v).apply() }
    fun setHidden(s: Set<String>) { hidden = s; sp.edit().putStringSet("hidden", s).apply() }
    fun replaceFolders(list: List<Folder>) = saveFolders(list)

    fun setIconOverride(pkg: String, value: String?) {
        iconOverrides = if (value == null) iconOverrides - pkg else iconOverrides + (pkg to value)
        saveOverrides()
    }
    fun replaceIconOverrides(m: Map<String, String>) { iconOverrides = m; saveOverrides() }
    private fun saveOverrides() {
        val o = JSONObject(); iconOverrides.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString("iconOverrides", o.toString()).apply()
    }

    fun replaceCustomThemes(list: List<String>) {
        customThemes = list.take(10)
        sp.edit().putString("customThemes", JSONArray(customThemes).toString()).apply()
    }
    fun addCustomTheme(json: String) = replaceCustomThemes(customThemes + json)

    fun setOnboarded(v: Boolean = true) { onboarded = v; sp.edit().putBoolean("onboarded", v).apply() }

    fun hide(pkg: String) { hidden = hidden + pkg; sp.edit().putStringSet("hidden", hidden).apply() }
    fun unhide(pkg: String) { hidden = hidden - pkg; sp.edit().putStringSet("hidden", hidden).apply() }

    fun setDock(list: List<String>) {
        dock = list
        sp.edit().putString("dock", list.joinToString(",")).apply()
    }

    fun recordLaunch(pkg: String) {
        val n = (usage[pkg] ?: 0) + 1
        usage = usage + (pkg to n)
        sp.edit().putInt("use_$pkg", n).apply()
    }

    // ── Folders ──
    fun createFolder(name: String, pkg: String): String {
        val f = Folder(UUID.randomUUID().toString(), name, listOf(pkg))
        saveFolders(folders + f)
        return f.id
    }
    private fun update(id: String, f: (Folder) -> Folder) = saveFolders(folders.map { if (it.id == id) f(it) else it })
    fun addToFolder(id: String, pkg: String) = update(id) { if (pkg in it.pkgs) it else it.copy(pkgs = it.pkgs + pkg) }
    fun removeFromFolder(id: String, pkg: String) = update(id) { it.copy(pkgs = it.pkgs - pkg) }
    fun renameFolder(id: String, name: String) = update(id) { it.copy(name = name.trim().take(24).ifBlank { it.name }) }
    fun deleteFolder(id: String) {
        saveFolders(folders.filter { it.id != id })
        setHome(homeItems.filter { !(it.type == "folder" && it.ref == id) }, homePages)
    }

    /** Resets everything to defaults (confirmation is handled by the UI). */
    fun resetAll() {
        sp.edit().clear().putInt("schema", SCHEMA).putBoolean("onboarded", true).apply()
        themeId = "obsidian"; accentMode = 0; accentIndex = 0; columns = 4; iconScale = 1f
        showLabels = true; drawerOpacity = 0.92f; reducedMotion = false; haptics = true
        hidden = emptySet(); dock = emptyList(); usage = emptyMap(); folders = emptyList(); onboarded = true
        homeCols = 4; homeRows = 5; homePages = 1; homeItems = emptyList()
        iconStyle = 0; clockStyle = 0; wallpaperDim = 0.10f; highContrast = false
        gestureUp = "drawer"; gestureDown = "notifications"; gestureDouble = "none"; gestureLong = "edit"; gestureSens = 48f
        hapticLevel = 1; scheduleMode = 0; dayTheme = "arctic"; nightTheme = "obsidian"; dayStart = 7; nightStart = 20
        iconPack = ""; iconOverrides = emptyMap(); notificationDots = false; customThemes = emptyList()
    }

    companion object { const val SCHEMA = 3; const val MAX_PAGES = 5 }
}
