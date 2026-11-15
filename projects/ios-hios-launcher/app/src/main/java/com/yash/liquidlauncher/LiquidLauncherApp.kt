package com.yash.liquidlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class AppEntry(val label: String, val packageName: String)

@Composable
fun LiquidLauncherApp() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var columns by remember { mutableStateOf(4) }
    var dark by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val result = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(queryIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(queryIntent, 0)
            }
            result.mapNotNull { item ->
                val packageName = item.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) null
                else AppEntry(item.loadLabel(pm).toString(), packageName)
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }
        }
    }

    val background = if (dark) Color(0xFF090F1B) else Color(0xFFF0F4FA)
    val foreground = if (dark) Color(0xFFF4F7FF) else Color(0xFF172033)
    val muted = if (dark) Color(0xFFA1AEC3) else Color(0xFF68758A)

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(
                if (dark) Color(0xFF1B3451) else Color(0xFFDCE9F8),
                background,
                if (dark) Color(0xFF101827) else Color(0xFFE9EEF6)
            ))
        )) {
            AnimatedContent(
                targetState = settingsOpen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "main-settings"
            ) { showSettings ->
                if (showSettings) {
                    SettingsScreen(dark, columns, foreground, muted,
                        { dark = !dark }, { columns = it }, { settingsOpen = false })
                } else {
                    Column(
                        Modifier.fillMaxSize()
                            .padding(WindowInsets.statusBars.union(WindowInsets.navigationBars).asPaddingValues())
                            .padding(horizontal = 20.dp)
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date()), color = muted, fontSize = 14.sp)
                                Text(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()), color = foreground, fontSize = 43.sp, fontWeight = FontWeight.Light)
                            }
                            Surface(color = Color.White.copy(alpha = if (dark) .12f else .72f), shape = CircleShape,
                                modifier = Modifier.size(48.dp).clickable { settingsOpen = true }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = foreground)
                                }
                            }
                        }
                        Spacer(Modifier.height(26.dp))
                        Text("Your space", color = foreground, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                        Text("A calmer home for everything.", color = muted, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(apps.take(8), key = { it.packageName }) { app ->
                                AppTile(app, foreground) { launchApp(context, app.packageName) }
                            }
                        }
                        Surface(
                            color = Color.White.copy(alpha = if (dark) .13f else .78f),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Search, "Search apps", tint = foreground, modifier = Modifier.clickable { drawerOpen = true })
                                Text("Search your apps", color = muted, modifier = Modifier.weight(1f).clickable { drawerOpen = true }.padding(start = 12.dp))
                                Icon(Icons.Rounded.Apps, "All apps", tint = foreground, modifier = Modifier.clickable { drawerOpen = true })
                            }
                        }
                        if (drawerOpen) {
                            Surface(color = background.copy(alpha = .98f),
                                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                                modifier = Modifier.fillMaxWidth().weight(1.35f)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("App Library", color = foreground, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Text("Close", color = muted, modifier = Modifier.clickable { drawerOpen = false }.padding(8.dp))
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    TextField(
                                        value = search, onValueChange = { search = it },
                                        placeholder = { Text("Find an app") },
                                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                        singleLine = true, shape = RoundedCornerShape(18.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.White.copy(alpha = .10f),
                                            unfocusedContainerColor = Color.White.copy(alpha = .07f),
                                            focusedTextColor = foreground, unfocusedTextColor = foreground
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    LazyVerticalGrid(columns = GridCells.Fixed(columns),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        items(apps.filter { it.label.contains(search, ignoreCase = true) }, key = { it.packageName }) { app ->
                                            AppTile(app, foreground) { launchApp(context, app.packageName) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun launchApp(context: android.content.Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let { context.startActivity(it) }
}

@Composable
private fun AppTile(app: AppEntry, foreground: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(19.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF80B4E5), Color(0xFF526FAE)))),
            contentAlignment = Alignment.Center) {
            Text(app.label.take(1).uppercase(), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(app.label, color = foreground, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun SettingsScreen(
    dark: Boolean, columns: Int, foreground: Color, muted: Color,
    toggleDark: () -> Unit, changeColumns: (Int) -> Unit, back: () -> Unit
) {
    Column(Modifier.fillMaxSize()
        .padding(WindowInsets.statusBars.union(WindowInsets.navigationBars).asPaddingValues())
        .padding(22.dp)) {
        Text("‹  Back", color = muted, modifier = Modifier.clickable(onClick = back).padding(vertical = 12.dp))
        Text("Personalise", color = foreground, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text("Make the space feel like yours.", color = muted, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
        Surface(color = Color.White.copy(alpha = .10f), shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = toggleDark)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dark appearance", color = foreground, modifier = Modifier.weight(1f))
                Text(if (dark) "On" else "Off", color = muted)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Home grid", color = foreground, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
            listOf(3, 4, 5).forEach { count ->
                Surface(color = if (columns == count) Color(0xFF5B8CCB) else Color.White.copy(alpha = .12f),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.clickable { changeColumns(count) }) {
                    Text("$count columns", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Liquid Launcher · Early access", color = muted, fontSize = 12.sp)
        Text("Workspace editing, real app icons and widgets are in the next build stage.", color = muted, fontSize = 12.sp)
    }
}
