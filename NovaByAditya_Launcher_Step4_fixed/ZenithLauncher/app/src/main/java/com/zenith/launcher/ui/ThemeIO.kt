package com.zenith.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Theme import/export. Imported files are treated as data only: size-limited, strictly validated,
 * and never executed. Themes with unreadable text contrast are rejected.
 */
object ThemeIO {
    private const val MAX_BYTES = 8 * 1024
    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

    private fun hex(c: Color) = String.format("#%06X", c.toArgb() and 0xFFFFFF)

    fun toJson(t: NovaTheme): String = JSONObject()
        .put("format", "nova-theme").put("version", 1)
        .put("name", t.name).put("dark", t.dark)
        .put("background", hex(t.background)).put("surface", hex(t.surface)).put("elevated", hex(t.elevated))
        .put("primary", hex(t.primary)).put("secondary", hex(t.secondary))
        .put("text", hex(t.text)).put("textSecondary", hex(t.textSecondary))
        .put("corner", t.corner).put("font", t.font).put("glow", t.glow)
        .toString(2)

    private fun lum(c: Color): Double {
        fun f(v: Float): Double { val x = v.toDouble(); return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        return 0.2126 * f(c.red) + 0.7152 * f(c.green) + 0.0722 * f(c.blue)
    }
    fun contrast(a: Color, b: Color): Double {
        val l1 = lum(a); val l2 = lum(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    /** Returns the theme, or null with the reason in [error]. */
    class Result(val theme: NovaTheme?, val error: String?)

    fun parse(text: String): Result {
        if (text.length > MAX_BYTES) return Result(null, "Theme file is too large")
        val o = try { JSONObject(text) } catch (_: Exception) { return Result(null, "Not a valid theme file") }
        if (o.optString("format") != "nova-theme" || o.optInt("version", 0) != 1) return Result(null, "Unsupported theme format")
        val name = o.optString("name").filter { !it.isISOControl() }.trim().take(24)
        if (name.isEmpty()) return Result(null, "Theme needs a name")
        fun col(k: String): Color? {
            val v = o.optString(k)
            return if (HEX.matches(v)) Color(android.graphics.Color.parseColor(v)) else null
        }
        val bg = col("background"); val sf = col("surface"); val el = col("elevated")
        val pr = col("primary"); val se = col("secondary"); val tx = col("text"); val ts = col("textSecondary")
        if (bg == null || sf == null || el == null || pr == null || se == null || tx == null || ts == null)
            return Result(null, "A colour is missing or not in #RRGGBB form")
        val corner = o.optInt("corner", 24)
        val font = o.optInt("font", 0)
        if (corner !in 0..40 || font !in 0..2) return Result(null, "Corner or font value is out of range")
        if (contrast(tx, bg) < 4.5 || contrast(tx, sf) < 4.5 || contrast(ts, sf) < 4.5 || contrast(pr, sf) < 3.0)
            return Result(null, "Text contrast is too low to be readable")
        val dark = o.optBoolean("dark", lum(bg) < 0.4)
        val key = text.filter { !it.isWhitespace() }
        val id = "custom_" + MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(10)
        return Result(NovaTheme(id, name, dark, bg, sf, el, pr, se, tx, ts, corner, font, o.optBoolean("glow", false)), null)
    }
}

/** Built-in themes plus the user's imported ones. */
fun allThemes(prefs: com.zenith.launcher.data.Prefs): List<NovaTheme> =
    NovaThemes + prefs.customThemes.mapNotNull { ThemeIO.parse(it).theme }
