package com.zenith.launcher

import com.zenith.launcher.data.Folder
import com.zenith.launcher.data.HomeItem
import com.zenith.launcher.data.Prefs
import com.zenith.launcher.ui.NovaThemes
import com.zenith.launcher.ui.ThemeIO
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Backup/restore of layout, folders, dock and settings. No secrets, no usage data, no notification data. */
object Backup {
    const val FORMAT = 1
    const val MAX_BYTES = 512 * 1024

    class Result(val ok: Boolean, val message: String)

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun export(p: Prefs, appVersion: String): String {
        val settings = JSONObject()
            .put("themeId", p.themeId).put("accentMode", p.accentMode).put("accent", p.accentIndex)
            .put("columns", p.columns).put("iconScale", p.iconScale.toDouble()).put("labels", p.showLabels)
            .put("drawerOpacity", p.drawerOpacity.toDouble()).put("reducedMotion", p.reducedMotion)
            .put("haptics", p.haptics).put("hapticLevel", p.hapticLevel).put("iconStyle", p.iconStyle)
            .put("clockStyle", p.clockStyle).put("wallpaperDim", p.wallpaperDim.toDouble())
            .put("highContrast", p.highContrast).put("gestureUp", p.gestureUp).put("gestureDown", p.gestureDown)
            .put("gestureDouble", p.gestureDouble).put("gestureLong", p.gestureLong)
            .put("gestureSens", p.gestureSens.toDouble()).put("scheduleMode", p.scheduleMode)
            .put("dayTheme", p.dayTheme).put("nightTheme", p.nightTheme)
            .put("dayStart", p.dayStart).put("nightStart", p.nightStart)
        val items = JSONArray()
        p.homeItems.forEach {
            items.put(JSONObject().put("type", it.type).put("ref", it.ref).put("page", it.page).put("x", it.x).put("y", it.y))
        }
        val folders = JSONArray()
        p.folders.forEach { folders.put(JSONObject().put("id", it.id).put("name", it.name).put("pkgs", JSONArray(it.pkgs))) }
        val layout = JSONObject()
            .put("cols", p.homeCols).put("rows", p.homeRows).put("pages", p.homePages)
            .put("items", items).put("folders", folders)
            .put("dock", JSONArray(p.dock)).put("hidden", JSONArray(p.hidden.toList()))
        val overrides = JSONObject(); p.iconOverrides.forEach { (k, v) -> overrides.put(k, v) }
        val payload = JSONObject().put("settings", settings).put("layout", layout)
            .put("iconPack", p.iconPack).put("iconOverrides", overrides)
            .put("customThemes", JSONArray(p.customThemes)).toString()
        return JSONObject().put("format", "nova-backup").put("formatVersion", FORMAT)
            .put("exportDate", Instant.now().toString()).put("appVersion", appVersion)
            .put("checksum", sha256(payload)).put("payload", payload).toString(2)
    }

    /** Validates everything before touching any setting. Apps that aren't installed are skipped. */
    fun restore(text: String, p: Prefs, installed: Set<String>): Result {
        if (text.length > MAX_BYTES) return Result(false, "Backup file is too large")
        val root = try { JSONObject(text) } catch (_: Exception) { return Result(false, "This isn't a Nova backup") }
        if (root.optString("format") != "nova-backup") return Result(false, "This isn't a Nova backup")
        val ver = root.optInt("formatVersion", 0)
        if (ver < 1 || ver > FORMAT) return Result(false, "Backup is from an unsupported version")
        val payloadStr = root.optString("payload")
        if (sha256(payloadStr) != root.optString("checksum")) return Result(false, "Backup is damaged (checksum mismatch)")
        val payload = try { JSONObject(payloadStr) } catch (_: Exception) { return Result(false, "Backup contents are unreadable") }
        val s = payload.optJSONObject("settings") ?: return Result(false, "Backup has no settings")
        val l = payload.optJSONObject("layout") ?: return Result(false, "Backup has no layout")

        // Custom themes first so a restored theme id can be validated.
        val customs = payload.optJSONArray("customThemes")?.let { a ->
            (0 until a.length()).map { a.optString(it) }.filter { ThemeIO.parse(it).theme != null }.take(10)
        } ?: emptyList()
        val customIds = customs.mapNotNull { ThemeIO.parse(it).theme?.id }
        val validIds = NovaThemes.map { it.id } + customIds
        fun themeOr(k: String, d: String) = s.optString(k, d).takeIf { it in validIds } ?: d
        val actions = setOf("none", "drawer", "search", "notifications", "edit", "settings")
        fun actionOr(k: String, d: String) = s.optString(k, d).takeIf { it in actions } ?: d

        val cols = l.optInt("cols", 4).coerceIn(3, 6)
        val rows = l.optInt("rows", 5).coerceIn(3, 7)
        val pages = l.optInt("pages", 1).coerceIn(1, Prefs.MAX_PAGES)

        val folderList = mutableListOf<Folder>()
        var skipped = 0
        l.optJSONArray("folders")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                val pk = o.optJSONArray("pkgs") ?: JSONArray()
                val all = (0 until pk.length()).map { pk.optString(it) }.filter { it.isNotBlank() }
                val ok = all.filter { it in installed }
                skipped += all.size - ok.size
                folderList.add(Folder(id, o.optString("name", "Folder").take(24), ok))
            }
        }
        val folderIds = folderList.map { it.id }.toSet()

        val seen = HashSet<String>()
        val homeList = mutableListOf<HomeItem>()
        l.optJSONArray("items")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val type = o.optString("type"); val ref = o.optString("ref")
                val pg = o.optInt("page", -1); val x = o.optInt("x", -1); val y = o.optInt("y", -1)
                if (pg !in 0 until pages || x !in 0 until cols || y !in 0 until rows) { skipped++; continue }
                val valid = (type == "app" && ref in installed) || (type == "folder" && ref in folderIds)
                if (!valid) { skipped++; continue }
                if (!seen.add("$pg:$x:$y")) { skipped++; continue }
                homeList.add(HomeItem(UUID.randomUUID().toString(), type, ref, pg, x, y))
            }
        }
        val dock = l.optJSONArray("dock")?.let { a -> (0 until a.length()).map { a.optString(it) } }
            ?.filter { it in installed }?.take(5) ?: emptyList()
        val hidden = l.optJSONArray("hidden")?.let { a -> (0 until a.length()).map { a.optString(it) } }
            ?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val ov = payload.optJSONObject("iconOverrides")
        val overrides = ov?.keys()?.asSequence()?.associateWith { ov.optString(it) }
            ?.filterValues { it.contains("|") } ?: emptyMap()

        // ---- everything validated: apply ----
        p.replaceCustomThemes(customs)
        p.setTheme(themeOr("themeId", "obsidian"))
        p.setAccentMode(s.optInt("accentMode", 0).coerceIn(0, 2))
        p.setAccent(s.optInt("accent", 0).coerceIn(0, 5)); p.setAccentMode(s.optInt("accentMode", 0).coerceIn(0, 2))
        p.setColumns(s.optInt("columns", 4).coerceIn(3, 6))
        p.setIconScale(s.optDouble("iconScale", 1.0).toFloat().coerceIn(0.7f, 1.4f))
        p.setLabels(s.optBoolean("labels", true))
        p.setDrawerOpacity(s.optDouble("drawerOpacity", 0.92).toFloat().coerceIn(0.3f, 1f))
        p.setReducedMotion(s.optBoolean("reducedMotion", false))
        p.setHaptics(s.optBoolean("haptics", true)); p.setHapticLevel(s.optInt("hapticLevel", 1).coerceIn(0, 2))
        p.setIconStyle(s.optInt("iconStyle", 0).coerceIn(0, 1)); p.setClockStyle(s.optInt("clockStyle", 0).coerceIn(0, 2))
        p.setWallpaperDim(s.optDouble("wallpaperDim", 0.1).toFloat().coerceIn(0f, 0.6f))
        p.setHighContrast(s.optBoolean("highContrast", false))
        p.setGestureUp(actionOr("gestureUp", "drawer")); p.setGestureDown(actionOr("gestureDown", "notifications"))
        p.setGestureDouble(actionOr("gestureDouble", "none")); p.setGestureLong(actionOr("gestureLong", "edit"))
        p.setGestureSens(s.optDouble("gestureSens", 48.0).toFloat().coerceIn(24f, 120f))
        p.setScheduleMode(s.optInt("scheduleMode", 0).coerceIn(0, 2))
        p.setDayTheme(themeOr("dayTheme", "arctic")); p.setNightTheme(themeOr("nightTheme", "obsidian"))
        p.setDayStart(s.optInt("dayStart", 7).coerceIn(0, 23)); p.setNightStart(s.optInt("nightStart", 20).coerceIn(0, 23))
        p.setIconPack(payload.optString("iconPack", "")); p.replaceIconOverrides(overrides)
        p.setHomeGrid(cols, rows)
        p.replaceFolders(folderList)
        p.setHome(homeList, pages)
        p.setDock(dock); p.setHidden(hidden)
        return Result(true, if (skipped == 0) "Backup restored" else "Backup restored. Skipped $skipped item(s) for apps that aren't installed or no longer fit.")
    }
}
