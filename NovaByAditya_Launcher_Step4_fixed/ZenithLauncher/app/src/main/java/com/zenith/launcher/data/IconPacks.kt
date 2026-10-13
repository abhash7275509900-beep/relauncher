package com.zenith.launcher.data

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val pkg: String, val label: String)

/**
 * Supports the common icon-pack convention (ADW / Nova / Go / Apex): an installed app that declares
 * one of the THEME actions and ships an appfilter.xml mapping components to drawables.
 * Anything missing or unreadable falls back to the app's own icon.
 */
class IconPackManager(private val ctx: Context) {
    private val actions = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME"
    )
    private val cache = HashMap<String, Map<String, String>>()

    fun installed(): List<IconPackInfo> {
        val pm = ctx.packageManager
        return actions.flatMap { a -> pm.queryIntentActivities(Intent(a), 0) }
            .map { it.activityInfo.packageName }
            .distinct()
            .mapNotNull { p ->
                try { IconPackInfo(p, pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString()) }
                catch (_: Exception) { null }
            }
            .sortedBy { it.label.lowercase() }
    }

    fun clearCache() = cache.clear()

    /** "package/activity" -> drawable name */
    private fun mapping(pack: String): Map<String, String> = cache.getOrPut(pack) { parse(pack) }

    private fun parse(pack: String): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            val res = ctx.packageManager.getResourcesForApplication(pack)
            val id = res.getIdentifier("appfilter", "xml", pack)
            val parser: XmlPullParser = if (id != 0) {
                res.getXml(id)
            } else {
                XmlPullParserFactory.newInstance().newPullParser().also { it.setInput(res.assets.open("appfilter.xml"), null) }
            }
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null && comp.startsWith("ComponentInfo{")) {
                        val inner = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                        val slash = inner.indexOf('/')
                        if (slash > 0) {
                            val p = inner.substring(0, slash)
                            var cls = inner.substring(slash + 1)
                            if (cls.startsWith(".")) cls = p + cls
                            out["$p/$cls"] = dr
                        }
                    }
                }
                ev = parser.next()
            }
        } catch (_: Exception) { }
        return out
    }

    fun drawable(pack: String, name: String, size: Int = 144): ImageBitmap? = try {
        val res = ctx.packageManager.getResourcesForApplication(pack)
        val id = res.getIdentifier(name, "drawable", pack)
        if (id == 0) null else ResourcesCompat.getDrawable(res, id, null)?.toBitmap(size, size)?.asImageBitmap()
    } catch (_: Exception) { null }

    fun iconFor(pack: String, pkg: String, activity: String): ImageBitmap? {
        val n = mapping(pack)["$pkg/$activity"] ?: return null
        return drawable(pack, n)
    }

    /** Distinct drawable names the pack maps, for the per-app icon picker (capped). */
    fun drawableNames(pack: String, cap: Int = 400): List<String> = mapping(pack).values.distinct().sorted().take(cap)
}
