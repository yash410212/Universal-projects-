package com.yash.hiosisland

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaController
import android.os.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class IslandService : Service() {
    companion object {
        const val ACTION_NOTIFICATION = "com.yash.hiosisland.NOTIFICATION"
        const val ACTION_NOTIFICATION_REMOVED = "com.yash.hiosisland.NOTIFICATION_REMOVED"
        const val ACTION_MEDIA_SESSION = "com.yash.hiosisland.MEDIA_SESSION"
        private const val CHANNEL = "hios_island_service"
    }

    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var expanded = false
    private var notificationTitle = "HiOS Island"
    private var notificationText = "Tap to explore live activities"
    private var batteryLevel = 0
    private var charging = false
    private var mediaToggle: PendingIntent? = null
    private var mediaNext: PendingIntent? = null
    private var mediaPrevious: PendingIntent? = null
    private var mediaController: MediaController? = null
    private var mediaTitle = ""
    private var mediaArtist = ""
    private var mediaPlaying = false
    private var timer: CountDownTimer? = null
    private var timerEnd = 0L
    private var timerText = ""
    private val handler = Handler(Looper.getMainLooper())
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_NOTIFICATION -> {
                    notificationTitle = intent.getStringExtra("title") ?: "Notification"
                    notificationText = intent.getStringExtra("text") ?: ""
                    mediaToggle = getPending(intent, "media_toggle")
                    mediaNext = getPending(intent, "media_next")
                    mediaPrevious = getPending(intent, "media_previous")
                    render()
                }
                ACTION_NOTIFICATION_REMOVED -> {
                    notificationTitle = "HiOS Island"
                    notificationText = "You're all caught up"
                    render()
                }
                ACTION_MEDIA_SESSION -> {
                    mediaTitle = intent.getStringExtra("media_title").orEmpty()
                    mediaArtist = intent.getStringExtra("media_artist").orEmpty()
                    mediaPlaying = intent.getBooleanExtra("media_playing", false)
                    val token = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra("media_token", android.media.session.MediaSession.Token::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra("media_token")
                    mediaController = try { token?.let { MediaController(this@IslandService, it) } } catch (_: Exception) { null }
                    render()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    if (level >= 0 && scale > 0) batteryLevel = level * 100 / scale
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    render()
                }
            }
        }
    }

    private fun getPending(intent: Intent, key: String): PendingIntent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(key, PendingIntent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(key)

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(71, serviceNotification())
        registerReceiver(eventReceiver, IntentFilter().apply {
            addAction(ACTION_NOTIFICATION)
            addAction(ACTION_NOTIFICATION_REMOVED)
            addAction(ACTION_MEDIA_SESSION)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        })
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        battery?.let { eventReceiver.onReceive(this, it) }
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (root == null) showOverlay()
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "HiOS Island", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun serviceNotification(): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("HiOS Island is active")
            .setContentText("Tap to manage your live island").setContentIntent(open).setOngoing(true).build()
        else @Suppress("DEPRECATION") Notification.Builder(this)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("HiOS Island is active")
            .setContentText("Tap to manage your live island").setContentIntent(open).setOngoing(true).build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun showOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this) || root != null) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.BLACK, 28, 0xFF30343D.toInt())
            setPadding(dp(10), dp(7), dp(10), dp(8))
            elevation = dp(12).toFloat()
        }
        root = container
        render()
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            dp(204), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(2)
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try { wm.addView(container, params) } catch (_: Exception) { root = null }
    }

    private fun render() {
        val box = root ?: return
        box.removeAllViews()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(2), dp(5), dp(2))
        }
        val dot = TextView(this).apply {
            text = if (charging) "ϟ" else "●"
            textSize = 14f
            setTextColor(if (charging) 0xFF8DE6B5.toInt() else 0xFF9EB9FF.toInt())
        }
        val label = TextView(this).apply {
            text = if (timerEnd > 0L) timerText else if (mediaTitle.isNotBlank()) mediaTitle.take(18) else if (notificationTitle != "HiOS Island") notificationTitle.take(18) else "HiOS Island"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
        }
        val spacer = View(this)
        val status = TextView(this).apply {
            text = if (charging) "$batteryLevel% ϟ" else if (batteryLevel > 0) "$batteryLevel%" else "LIVE"
            textSize = 10f
            setTextColor(0xFFB5C0D3.toInt())
        }
        header.addView(dot, LinearLayout.LayoutParams(dp(20), dp(25)))
        header.addView(label, LinearLayout.LayoutParams(0, dp(25), 1f))
        header.addView(spacer, LinearLayout.LayoutParams(dp(5), 1))
        header.addView(status)
        header.setOnClickListener { expanded = !expanded; resize(); render() }
        box.addView(header)
        if (expanded) {
            val divider = View(this).apply { setBackgroundColor(0xFF30343D.toInt()) }
            box.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(5); bottomMargin = dp(8) })
            val title = TextView(this).apply {
                text = if (mediaTitle.isNotBlank()) mediaTitle else notificationTitle
                textSize = 14f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            val body = TextView(this).apply {
                text = if (timerEnd > 0L) "Timer running • $timerText" else if (mediaTitle.isNotBlank()) mediaArtist.ifBlank { "Now playing" } else notificationText.ifBlank { "No new notifications" }
                textSize = 12f
                setTextColor(0xFFB9C2D2.toInt())
                maxLines = 3
            }
            box.addView(title)
            box.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            }
            if (mediaController != null) {
                actions.addView(actionButton("‹") { mediaController?.transportControls?.skipToPrevious() })
                actions.addView(actionButton(if (mediaPlaying) "Ⅱ" else "▶") {
                    try { if (mediaPlaying) mediaController?.transportControls?.pause() else mediaController?.transportControls?.play() } catch (_: Exception) {}
                })
                actions.addView(actionButton("›") { mediaController?.transportControls?.skipToNext() })
            } else {
                if (mediaPrevious != null) actions.addView(actionButton("‹") { send(mediaPrevious) })
                if (mediaToggle != null) actions.addView(actionButton("▶ / Ⅱ") { send(mediaToggle) })
                if (mediaNext != null) actions.addView(actionButton("›") { send(mediaNext) })
            }
            actions.addView(actionButton("5 min") { startTimer(5 * 60_000L) })
            actions.addView(actionButton("15 min") { startTimer(15 * 60_000L) })
            actions.addView(actionButton("×") { cancelTimer(); expanded = false; resize(); render() })
            box.addView(actions)
            val hint = TextView(this).apply {
                text = "Tap the island to collapse  •  Swipe up to dismiss"
                textSize = 9f
                setTextColor(0xFF758096.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            }
            box.addView(hint)
        }
        box.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastTouchX = event.rawX; lastTouchY = event.rawY; true }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - lastTouchX
                    val dy = event.rawY - lastTouchY
                    if (abs(dy) > dp(65) && dy < 0) { stopSelf(); true }
                    else if (abs(dx) > dp(70)) { expanded = false; resize(); render(); true }
                    else false
                }
                else -> true
            }
        }
    }

    private fun actionButton(text: String, action: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = shape(0xFF252B38.toInt(), 18)
            setPadding(dp(9), dp(8), dp(9), dp(8))
            val lp = LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(5) }
            layoutParams = lp
            setOnClickListener { action() }
        }

    private fun resize() {
        val view = root ?: return
        try {
            val p = view.layoutParams as WindowManager.LayoutParams
            p.width = dp(if (expanded) 286 else 204)
            wm.updateViewLayout(view, p)
        } catch (_: Exception) {}
    }

    private fun send(pending: PendingIntent?) {
        try { pending?.send() } catch (_: Exception) {}
    }

    private fun startTimer(duration: Long) {
        timer?.cancel()
        timerEnd = System.currentTimeMillis() + duration
        timer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                timerText = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
                render()
            }
            override fun onFinish() {
                timerEnd = 0L
                timerText = "Timer finished"
                notificationTitle = "Timer finished"
                notificationText = "Your countdown is complete"
                render()
            }
        }.start()
        render()
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
        timerEnd = 0L
        timerText = ""
    }

    override fun onDestroy() {
        try { unregisterReceiver(eventReceiver) } catch (_: Exception) {}
        timer?.cancel()
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
