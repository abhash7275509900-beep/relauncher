package com.zenith.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.zenith.launcher.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun IconPickerDialog(vm: LauncherViewModel) {
    val app = vm.iconPickerFor ?: return
    var pack by remember(app) { mutableStateOf(vm.prefs.iconPack.ifBlank { vm.iconPacks.firstOrNull()?.pkg ?: "" }) }
    val names by produceState<List<String>>(emptyList(), pack) {
        value = withContext(Dispatchers.Default) { if (pack.isBlank()) emptyList() else vm.packs.drawableNames(pack) }
    }
    AlertDialog(
        onDismissRequest = { vm.iconPickerFor = null },
        title = { Text("Icon for ${app.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.iconPacks.forEach {
                        FilterChip(selected = pack == it.pkg, onClick = { pack = it.pkg }, label = { Text(it.label) })
                    }
                }
                if (names.isEmpty()) Text("This pack has no icons Nova can read.")
                LazyVerticalGrid(GridCells.Adaptive(56.dp), Modifier.height(280.dp)) {
                    items(names, key = { it }) { n ->
                        PackIcon(vm, pack, n) {
                            vm.setIconOverride(app.pkg, "$pack|$n")
                            vm.iconPickerFor = null
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.iconPickerFor = null }) { Text("Close") } },
        dismissButton = {
            if (vm.prefs.iconOverrides.containsKey(app.pkg)) {
                TextButton(onClick = { vm.setIconOverride(app.pkg, null); vm.iconPickerFor = null }) { Text("Reset icon") }
            }
        }
    )
}

@Composable
private fun PackIcon(vm: LauncherViewModel, pack: String, name: String, onClick: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, pack, name) {
        value = withContext(Dispatchers.Default) { vm.packs.drawable(pack, name, 96) }
    }
    Box(Modifier.size(56.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, name, Modifier.size(44.dp)) }
    }
}
