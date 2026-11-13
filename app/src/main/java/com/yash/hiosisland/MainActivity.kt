package com.yash.hiosisland

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val permissionRefresh = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IslandSetupScreen(
                refreshKey = permissionRefresh.intValue,
                overlayGranted = { Settings.canDrawOverlays(this) },
                listenerGranted = { isNotificationAccessEnabled() },
                requestOverlay = {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                },
                requestListener = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                startIsland = {
                    val intent = Intent(this, IslandService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                },
                stopIsland = { stopService(Intent(this, IslandService::class.java)) }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        permissionRefresh.intValue++
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(ComponentName(this, IslandNotificationListener::class.java).flattenToString())
    }
}

@Composable
private fun IslandSetupScreen(
    refreshKey: Int,
    overlayGranted: () -> Boolean,
    listenerGranted: () -> Boolean,
    requestOverlay: () -> Unit,
    requestListener: () -> Unit,
    startIsland: () -> Unit,
    stopIsland: () -> Unit
) {
    val overlay = remember(refreshKey) { overlayGranted() }
    val listener = remember(refreshKey) { listenerGranted() }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFB8C8FF),
        background = Color(0xFF07090E),
        surface = Color(0xFF171B25),
        onSurface = Color(0xFFF3F5FA)
    )) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF07090E)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            Text("HiOS Island", fontSize = 31.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("A smarter live island for your TECNO display", color = Color(0xFFADB7CC), fontSize = 14.sp)
            IslandPreview()
            Text("SYSTEM INTEGRATION", color = Color(0xFF9BA9C5), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            PermissionRow("Display over other apps", "Required for the island overlay", overlay)
            Button(onClick = requestOverlay, modifier = Modifier.fillMaxWidth()) {
                Text(if (overlay) "Overlay permission ready" else "Grant overlay permission")
            }
            PermissionRow("Notification access", "Allows notification previews and live media controls", listener)
            OutlinedButton(onClick = requestListener, modifier = Modifier.fillMaxWidth()) {
                Text(if (listener) "Notification access ready" else "Enable notification access")
            }
            Text("Notification access is optional; media and notification integration need it.", color = Color(0xFF8996AC), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = startIsland,
                enabled = overlay,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text("Start HiOS Island", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = stopIsland, modifier = Modifier.fillMaxWidth()) { Text("Stop Island") }
            Text("Android 11+ • Spark 8C tuned • Data stays on-device", color = Color(0xFF7F8AA0), fontSize = 10.sp)
        }
    }
}

@Composable
private fun IslandPreview() {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF111722), RoundedCornerShape(24.dp)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.background(Color.Black, RoundedCornerShape(30.dp)).padding(horizontal = 19.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).background(Color(0xFF7CDEB2), RoundedCornerShape(50)))
                Spacer(Modifier.width(10.dp))
                Text("Island ready", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(14.dp))
                Text("●", color = Color(0xFF8BAEFF), fontSize = 10.sp)
            }
        }
        Text("Notifications  •  Music  •  Charging  •  Timers", color = Color(0xFFB9C4D8), fontSize = 12.sp)
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFF8D99AE), fontSize = 11.sp)
        }
        Text(if (granted) "READY" else "NEEDED", color = if (granted) Color(0xFF70D6A5) else Color(0xFFFFC078), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
