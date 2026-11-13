package com.yash.depthstudio

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DepthStudioScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepthStudioScreen() {
    val context = LocalContext.current
    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var foregroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var clockSize by remember { mutableFloatStateOf(64f) }
    var clockY by remember { mutableFloatStateOf(0.28f) }
    var clockColor by remember { mutableStateOf(Color.White) }

    fun decode(uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }

    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) backgroundBitmap = decode(uri)
    }
    val foregroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) foregroundBitmap = decode(uri)
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB8C8FF), background = Color(0xFF090B12), surface = Color(0xFF151925), onSurface = Color(0xFFF3F4FA))) {
        Scaffold(containerColor = Color(0xFF090B12), topBar = {
            TopAppBar(title = { Column {
                Text("DEPTH STUDIO", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Lock-screen wallpaper creator", fontSize = 11.sp, color = Color.LightGray)
            } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF090B12)))
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.49f).background(Color(0xFF202637), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                    if (backgroundBitmap != null) Image(backgroundBitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your wallpaper preview", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("Choose a background photo", color = Color.LightGray, fontSize = 12.sp)
                    }
                    if (backgroundBitmap != null) {
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), Modifier.align(Alignment.TopCenter).padding(top = (clockY * 1000).dp), color = clockColor, fontSize = clockSize.sp, fontWeight = FontWeight.Light)
                        Text(SimpleDateFormat("EEE, d MMMM", Locale.getDefault()).format(Date()), Modifier.align(Alignment.TopCenter).padding(top = (clockY * 1000 + 78).dp), color = clockColor.copy(alpha = 0.9f), fontSize = 15.sp)
                        if (foregroundBitmap != null) Image(foregroundBitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { backgroundPicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text(if (backgroundBitmap == null) "Choose photo" else "Change photo") }
                    OutlinedButton(onClick = { foregroundPicker.launch("image/png") }, modifier = Modifier.weight(1f)) { Text(if (foregroundBitmap == null) "Add cutout PNG" else "Change cutout") }
                }
                Text("Clock size", color = Color.White, fontWeight = FontWeight.SemiBold)
                Slider(value = clockSize, onValueChange = { clockSize = it }, valueRange = 36f..90f)
                Text("Clock vertical position", color = Color.White, fontWeight = FontWeight.SemiBold)
                Slider(value = clockY, onValueChange = { clockY = it }, valueRange = 0.08f..0.55f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Color.White, Color(0xFFFFDCA8), Color(0xFFB8D9FF)).forEach { tint ->
                        Button(onClick = { clockColor = tint }, colors = ButtonDefaults.buttonColors(containerColor = tint), modifier = Modifier.weight(1f)) { Text(" ") }
                    }
                }
                Button(onClick = {
                    val bg = backgroundBitmap
                    if (bg == null) Toast.makeText(context, "Choose a background photo first", Toast.LENGTH_SHORT).show()
                    else try {
                        val out = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(out)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        canvas.drawBitmap(bg, null, Rect(0, 0, 1080, 2400), paint)
                        val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE; textSize = clockSize * 1.55f; textAlign = Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                        }
                        clockPaint.color = android.graphics.Color.argb((clockColor.alpha * 255).toInt(), (clockColor.red * 255).toInt(), (clockColor.green * 255).toInt(), (clockColor.blue * 255).toInt())
                        val y = 2400f * clockY + clockSize * 1.55f
                        canvas.drawText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 540f, y, clockPaint)
                        clockPaint.textSize = 34f
                        canvas.drawText(SimpleDateFormat("EEE, d MMMM", Locale.getDefault()).format(Date()), 540f, y + 54f, clockPaint)
                        foregroundBitmap?.let { canvas.drawBitmap(it, null, Rect(0, 0, 1080, 2400), paint) }
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "DepthStudio_${System.currentTimeMillis()}.png")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DepthStudio")
                        }
                        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        Toast.makeText(context, "Wallpaper saved to Pictures/DepthStudio", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) { Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                }, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = backgroundBitmap != null) { Text("Export wallpaper") }
                Text("Depth tip: add a transparent PNG cutout of your subject. The clock is layered between the background and cutout.", color = Color.LightGray, fontSize = 12.sp)
                Text("Offline • Android 11+ • Made for Tecno", color = Color(0xFF9BA6C4), fontSize = 11.sp)
            }
        }
    }
}
