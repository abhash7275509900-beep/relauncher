package com.zenith.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val label: String,
    val pkg: String,
    val activity: String,
    val icon: ImageBitmap,
    val category: Int = -1
)

class AppRepository(private val ctx: Context) {
    val packs = IconPackManager(ctx)

    /** overrides: package -> "iconPackPackage|drawableName". Anything missing falls back to the app's own icon. */
    fun load(iconPack: String = "", overrides: Map<String, String> = emptyMap()): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { ri ->
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    pkg = ri.activityInfo.packageName,
                    activity = ri.activityInfo.name,
                    icon = custom(ri.activityInfo.packageName, ri.activityInfo.name, iconPack, overrides)
                        ?: ri.loadIcon(pm).toBitmap(144, 144).asImageBitmap(),
                    category = ri.activityInfo.applicationInfo.category
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun custom(pkg: String, activity: String, pack: String, overrides: Map<String, String>) =
        overrides[pkg]?.split("|", limit = 2)?.takeIf { it.size == 2 }?.let { packs.drawable(it[0], it[1]) }
            ?: if (pack.isNotBlank()) packs.iconFor(pack, pkg, activity) else null

    /** Default apps for well-known categories (calendar, email, maps...) as chosen on this device. */
    fun packagesForCategories(cats: List<String>, apps: List<AppInfo>): List<String> {
        val pm = ctx.packageManager
        val known = apps.map { it.pkg }.toSet()
        return cats.mapNotNull {
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(it), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.filter { it in known }.distinct()
    }

    /** Phone, messages, browser, camera — whatever the device has set as default. */
    fun defaultDockPackages(apps: List<AppInfo>): List<String> {
        val pm = ctx.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        val known = apps.map { it.pkg }.toSet()
        return intents
            .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .filter { it in known }
            .distinct()
    }
}
