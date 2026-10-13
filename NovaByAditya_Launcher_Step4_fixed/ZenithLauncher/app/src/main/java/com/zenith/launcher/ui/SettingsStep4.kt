@file:OptIn(ExperimentalMaterial3Api::class)

package com.zenith.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.zenith.launcher.Backup
import com.zenith.launcher.LauncherViewModel
import java.io.ByteArrayOutputStream
import kotlin.math.abs

private val ACTIONS = listOf(
    "none" to "Do nothing", "drawer" to "Open app drawer", "search" to "Open search",
    "notifications" to "Show notifications", "edit" to "Edit Home", "settings" to "Open Nova settings"
)

@Composable
private fun ActionPicker(label: String, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(ACTIONS.firstOrNull { it.first == value }?.second ?: value) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                ACTIONS.forEach { (k, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { open = false; onChange(k) }) }
            }
        }
    }
}

@Composable
private fun ThemePicker(label: String, value: String, vm: LauncherViewModel, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val themes = allThemes(vm.prefs)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(themes.firstOrNull { it.id == value }?.name ?: "Obsidian Ultra") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                themes.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { open = false; onChange(t.id) }) }
            }
        }
    }
}

@Composable
private fun GestureTestPanel(vm: LauncherViewModel) {
    var result by remember { mutableStateOf("Try a swipe, double tap or long press here") }
    val th = vm.prefs.gestureSens
    Box(
        Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(th) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = {
                        val dp = abs(total) / density
                        result = when {
                            total < 0 && dp >= th -> "Swipe up detected"
                            total > 0 && dp >= th -> "Swipe down detected"
                            else -> "Swipe too short (${dp.toInt()} dp, needs ${th.toInt()} dp)"
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { result = "Double tap detected" }, onLongPress = { result = "Long press detected" })
            },
        contentAlignment = Alignment.Center
    ) { Text(result, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp)) }
}

private fun readCapped(ctx: Context, uri: Uri, cap: Int): String? = try {
    ctx.contentResolver.openInputStream(uri)?.use { ins ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            total += n
            if (total > cap) return null
            out.write(buf, 0, n)
        }
        out.toString("UTF-8")
    }
} catch (_: Exception) { null }

@Composable
fun ColumnScope.PersonalizationSettings(vm: LauncherViewModel) {
    val p = vm.prefs
    val ctx = LocalContext.current
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" } }

    Section("Gestures") {
        ActionPicker("Swipe up", p.gestureUp) { p.setGestureUp(it) }
        ActionPicker("Swipe down", p.gestureDown) { p.setGestureDown(it) }
        ActionPicker("Double tap", p.gestureDouble) { p.setGestureDouble(it) }
        ActionPicker("Long press", p.gestureLong) { p.setGestureLong(it) }
        Text("Swipe distance: ${p.gestureSens.toInt()} dp")
        Slider(value = p.gestureSens, onValueChange = { p.setGestureSens(it) }, valueRange = 24f..120f)
        GestureTestPanel(vm)
        Text(
            "Android's own edge gestures (back, home, recents) can't be overridden by any launcher. " +
                "Lock-screen actions aren't offered because they'd need an Accessibility service.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Section("Haptic strength") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Light", "Medium", "Strong").forEachIndexed { i, n ->
                FilterChip(selected = p.hapticLevel == i, onClick = { p.setHapticLevel(i); vm.buzz() }, label = { Text(n) })
            }
        }
        Text("Use the Haptic feedback switch above to turn vibration off completely.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Section("Scheduled themes") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Off", "System dark mode", "Time of day").forEachIndexed { i, n ->
                FilterChip(selected = p.scheduleMode == i, onClick = { p.setScheduleMode(i) }, label = { Text(n) })
            }
        }
        if (p.scheduleMode != 0) {
            ThemePicker("Day theme", p.dayTheme, vm) { p.setDayTheme(it) }
            ThemePicker("Night theme", p.nightTheme, vm) { p.setNightTheme(it) }
        }
        if (p.scheduleMode == 2) {
            Text("Day starts at ${p.dayStart}:00")
            Slider(value = p.dayStart.toFloat(), onValueChange = { p.setDayStart(it.toInt()) }, valueRange = 0f..23f, steps = 22)
            Text("Night starts at ${p.nightStart}:00")
            Slider(value = p.nightStart.toFloat(), onValueChange = { p.setNightStart(it.toInt()) }, valueRange = 0f..23f, steps = 22)
        }
        if (p.scheduleMode != 0) {
            Text("While a schedule is on, it chooses the theme. Previewing a theme still works.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Section("Icon pack") {
        if (vm.iconPacks.isEmpty()) {
            Text("No icon packs found. Install one from your app store, then come back here.")
        } else {
            (listOf("" to "System icons") + vm.iconPacks.map { it.pkg to it.label }).forEach { (k, n) ->
                Row(Modifier.fillMaxWidth().clickable { vm.setIconPack(k) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = p.iconPack == k, onClick = { vm.setIconPack(k) })
                    Text(n)
                }
            }
            Text("Long-press an app and choose Change icon to set a single icon.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(
            enabled = p.iconOverrides.isNotEmpty(),
            onClick = { p.replaceIconOverrides(emptyMap()); vm.refresh() }
        ) { Text("Reset ${p.iconOverrides.size} custom icon(s)") }
    }

    val listenerOn = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    var askDots by remember { mutableStateOf(false) }
    Section("Notification dots") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show dots on apps with notifications", Modifier.weight(1f))
            Switch(checked = p.notificationDots, onCheckedChange = { on ->
                p.setNotificationDots(on)
                if (on && !listenerOn) askDots = true
            })
        }
        if (p.notificationDots) {
            Text(if (listenerOn) "Notification access granted. Dots are active." else "Waiting for notification access. Dots appear once you grant it.")
        }
        Text("Nova only counts notifications per app. It never reads, stores or sends their content.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (askDots) {
        AlertDialog(
            onDismissRequest = { askDots = false; p.setNotificationDots(false) },
            title = { Text("Allow notification access?") },
            text = { Text("Android requires this special access so Nova can see which apps have notifications. Nova only counts them to draw a dot. You can turn it off any time in Android settings.") },
            confirmButton = {
                TextButton(onClick = { askDots = false; vm.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { askDots = false; p.setNotificationDots(false) }) { Text("Not now") } }
        )
    }

    var pendingRestore by remember { mutableStateOf<String?>(null) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(p, ver).toByteArray()) }
                vm.toast("Backup saved")
            } catch (_: Exception) { vm.toast("Couldn't save the backup") }
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val t = readCapped(ctx, uri, Backup.MAX_BYTES)
            if (t == null) vm.toast("Couldn't read that file, or it's too large") else pendingRestore = t
        }
    }
    Section("Backup & restore") {
        Text("Saves your layout, folders, dock, hidden apps, icon choices and settings to a file you pick. No usage or notification data is included.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportBackup.launch("nova-backup.json") }) { Text("Export backup") }
            OutlinedButton(onClick = { importBackup.launch(arrayOf("*/*")) }) { Text("Restore backup") }
        }
    }
    pendingRestore?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Your current layout and settings will be replaced. Apps that aren't installed on this phone are skipped.") },
            confirmButton = {
                TextButton(onClick = {
                    val r = Backup.restore(text, p, vm.apps.map { it.pkg }.toSet())
                    vm.toast(r.message)
                    pendingRestore = null
                    if (r.ok) vm.refresh()
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
        )
    }

    val exportTheme = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(ThemeIO.toJson(themeById(p.themeId, p.customThemes)).toByteArray())
                }
                vm.toast("Theme saved")
            } catch (_: Exception) { vm.toast("Couldn't save the theme") }
        }
    }
    val importTheme = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val t = readCapped(ctx, uri, 16 * 1024)
            if (t == null) { vm.toast("Couldn't read that file, or it's too large") } else {
                val r = ThemeIO.parse(t)
                val th = r.theme
                when {
                    th == null -> vm.toast(r.error ?: "Invalid theme")
                    allThemes(p).any { it.id == th.id } -> vm.toast("That theme is already added")
                    p.customThemes.size >= 10 -> vm.toast("You can keep up to 10 custom themes")
                    else -> { p.addCustomTheme(t); vm.toast("Added ${th.name}") }
                }
            }
        }
    }
    Section("Share themes") {
        Text("Theme files are plain data. Nova checks size, colours and text contrast, and never runs anything from them.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportTheme.launch("nova-theme.json") }) { Text("Export current") }
            OutlinedButton(onClick = { importTheme.launch(arrayOf("*/*")) }) { Text("Import theme") }
        }
    }
}
