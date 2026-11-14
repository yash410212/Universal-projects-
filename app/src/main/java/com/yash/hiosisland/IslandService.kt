package com.yash.hiosisland

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaController
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.*
import kotlin.math.abs
import kotlin.math.min
import java.util.Locale

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
    private var notificationPackage = ""
    private var notificationKey = ""
    private var notificationContentIntent: PendingIntent? = null
    private var notificationIsLiveActivity = false
    private var liveActivityKind = ""
    private var liveProgress = -1
    private var liveProgressMax = 0
    private var batteryLevel = 0
    private var charging = false
    private var mediaToggle: PendingIntent? = null
    private var mediaNext: PendingIntent? = null
    private var mediaPrevious: PendingIntent? = null
    private var mediaController: MediaController? = null
    private var mediaTitle = ""
    private var mediaArtist = ""
    private var mediaPlaying = false
    private var mediaDuration = 0L
    private var mediaPosition = 0L
    private var timer: CountDownTimer? = null
    private var timerEnd = 0L
    private var timerText = ""
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastRenderAt = 0L

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_NOTIFICATION -> {
                    notificationTitle = intent.getStringExtra("title") ?: "Notification"
                    notificationText = intent.getStringExtra("text") ?: ""
                    notificationPackage = intent.getStringExtra("package").orEmpty()
                    notificationKey = intent.getStringExtra("notification_key").orEmpty()
                    notificationContentIntent = getPending(intent, "content_intent")
                    mediaToggle = getPending(intent, "media_toggle")
                    mediaNext = getPending(intent, "media_next")
                    mediaPrevious = getPending(intent, "media_previous")
                    render()
                }
                ACTION_NOTIFICATION_REMOVED -> {
                    val removedKey = intent.getStringExtra("notification_key").orEmpty()
                    if (removedKey.isNotBlank() && removedKey == notificationKey) {
                        notificationTitle = "HiOS Island"
                        notificationText = "You're all caught up"
                        notificationPackage = ""
                        notificationKey = ""
                        notificationContentIntent = null
                        notificationIsLiveActivity = false
                        liveActivityKind = ""
                        liveProgress = -1
                        liveProgressMax = 0
                        render()
                    }
                }
                ACTION_MEDIA_SESSION -> {
                    mediaTitle = intent.getStringExtra("media_title").orEmpty()
                    mediaArtist = intent.getStringExtra("media_artist").orEmpty()
                    mediaPlaying = intent.getBooleanExtra("media_playing", false)
                    mediaPosition = intent.getLongExtra("media_position", 0L).coerceAtLeast(0L)
                    mediaDuration = intent.getLongExtra("media_duration", 0L).coerceAtLeast(0L)
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
        val filter = IntentFilter().apply {
            addAction(ACTION_NOTIFICATION)
            addAction(ACTION_NOTIFICATION_REMOVED)
            addAction(ACTION_MEDIA_SESSION)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(eventReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(eventReceiver, filter)
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { eventReceiver.onReceive(this, it) }
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
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

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }
    private fun glassBackground(radius: Int, expandedCard: Boolean = false): GradientDrawable {
        val colors = if (expandedCard) intArrayOf(0xF51B2230.toInt(), 0xF20D111B.toInt(), 0xF5222938.toInt())
            else intArrayOf(0xF51A202B.toInt(), 0xF70A0D13.toInt(), 0xF51B2230.toInt())
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), 0x667F91B5)
        }
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this) || root != null) return
        val screenWidth = resources.displayMetrics.widthPixels
        val compactWidth = min(dp(224), screenWidth - dp(28))
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassBackground(30)
            setPadding(dp(11), dp(7), dp(11), dp(9))
            elevation = dp(18).toFloat()
            clipToOutline = true
        }
        root = container
        render()
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            compactWidth, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Keep the overlay below the system status bar on devices with display cutouts.
            y = statusBarHeight() + dp(6)
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        try { wm.addView(container, params); container.alpha = 0f; container.scaleX = .92f; container.scaleY = .92f
            container.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
        } catch (_: Exception) { root = null }
    }

    private fun render() {
        val box = root ?: return
        box.removeAllViews()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(2), dp(3), dp(2))
        }
        val indicator = TextView(this).apply {
            text = when { charging -> "ϟ"; mediaPlaying -> "♫"; timerEnd > 0L -> "◷"; else -> "●" }
            textSize = 16f
            setTextColor(if (charging) 0xFF8DE6B5.toInt() else 0xFFB4C8FF.toInt())
            gravity = Gravity.CENTER
        }
        val labelValue = when {
            timerEnd > 0L -> timerText
            mediaTitle.isNotBlank() -> mediaTitle
            notificationTitle != "HiOS Island" -> notificationTitle
            else -> "HiOS Island"
        }
        val label = text(labelValue.take(22), 12f, Color.WHITE, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val status = text(if (charging) "$batteryLevel% ϟ" else if (batteryLevel > 0) "$batteryLevel%" else "LIVE", 10f, 0xFFB9C7DF.toInt())
        header.addView(indicator, LinearLayout.LayoutParams(dp(22), dp(27)))
        header.addView(label, LinearLayout.LayoutParams(0, dp(27), 1f).apply { marginStart = dp(5) })
        header.addView(status)
        header.setOnClickListener { expanded = !expanded; resize(); render() }
        box.addView(header)

        if (expanded) {
            val divider = View(this).apply { setBackgroundColor(0x557E8DA8) }
            box.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(7); bottomMargin = dp(10) })
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = glassBackground(22, true)
                setPadding(dp(12), dp(11), dp(12), dp(11))
            }
            val typeLabel = when {
                timerEnd > 0L -> "TIMER"
                mediaTitle.isNotBlank() -> "NOW PLAYING"
                notificationTitle != "HiOS Island" -> "NOTIFICATION"
                charging -> "POWER"
                else -> "LIVE ISLAND"
            }
            card.addView(text(typeLabel, 9f, 0xFF9EAFCC.toInt(), true))
            val title = text(when {
                timerEnd > 0L -> "Countdown running"
                mediaTitle.isNotBlank() -> mediaTitle
                else -> notificationTitle
            }, 15f, Color.WHITE, true).apply { maxLines = 2 }
            card.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            val subtitle = when {
                timerEnd > 0L -> "Time remaining  •  $timerText"
                mediaTitle.isNotBlank() -> mediaArtist.ifBlank { "Media session active" }
                notificationTitle != "HiOS Island" -> notificationText.ifBlank { notificationPackage }
                charging -> "Battery at $batteryLevel%  •  Charging"
                else -> "Your notifications and activities appear here"
            }
            card.addView(text(subtitle, 11f, 0xFFB6C2D5.toInt()).apply { maxLines = 3 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            if (notificationContentIntent != null && mediaTitle.isBlank() && timerEnd == 0L) {
                card.isClickable = true
                card.setOnClickListener { send(notificationContentIntent); expanded = false; resize(); render() }
            }
            if (mediaTitle.isNotBlank() && mediaDuration > 0L) {
                val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 1000
                    progress = ((mediaPosition.toFloat() / mediaDuration).coerceIn(0f, 1f) * 1000).toInt()
                    progressTintList = android.content.res.ColorStateList.valueOf(0xFFB9C9FF.toInt())
                    progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x557F8CA6)
                }
                card.addView(progress, LinearLayout.LayoutParams(-1, dp(3)).apply { topMargin = dp(10) })
            }
            box.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })

            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            if (mediaController != null) {
                actions.addView(actionButton("⏮") { safeMediaAction { mediaController?.transportControls?.skipToPrevious() } })
                actions.addView(actionButton(if (mediaPlaying) "Ⅱ" else "▶") {
                    safeMediaAction { if (mediaPlaying) mediaController?.transportControls?.pause() else mediaController?.transportControls?.play() }
                }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(5) })
                actions.addView(actionButton("⏭") { safeMediaAction { mediaController?.transportControls?.skipToNext() } })
            } else {
                if (mediaPrevious != null) actions.addView(actionButton("⏮") { send(mediaPrevious) })
                if (mediaToggle != null) actions.addView(actionButton("▶ / Ⅱ") { send(mediaToggle) })
                if (mediaNext != null) actions.addView(actionButton("⏭") { send(mediaNext) })
            }
            actions.addView(actionButton("5m") { startTimer(5 * 60_000L) })
            actions.addView(actionButton("15m") { startTimer(15 * 60_000L) })
            actions.addView(actionButton("×") { cancelTimer(); expanded = false; resize(); render() })
            box.addView(actions)
            box.addView(text("Tap pill to collapse  •  Swipe up to close", 9f, 0xFF7F8CA5.toInt()).apply { gravity = Gravity.CENTER; setPadding(0, dp(9), 0, 0) })
            box.alpha = 0f
            box.animate().alpha(1f).setDuration(170).start()
        }
        box.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastTouchX = event.rawX; lastTouchY = event.rawY; false }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - lastTouchX
                    val dy = event.rawY - lastTouchY
                    if (abs(dy) > dp(65) && dy < 0) { stopSelf(); true }
                    else if (abs(dx) > dp(70)) { expanded = false; resize(); render(); true }
                    else false
                }
                else -> false
            }
        }
    }

    private fun safeMediaAction(action: () -> Unit) { try { action() } catch (_: Exception) {} }

    private fun actionButton(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF394458.toInt(), 0xFF202938.toInt())).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), 0x557E91B5)
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(-2, dp(38)).apply { marginEnd = dp(5) }
            setOnClickListener { action(); animate().scaleX(.94f).scaleY(.94f).setDuration(75).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start() }
        }

    private fun resize() {
        val view = root ?: return
        try {
            val p = view.layoutParams as WindowManager.LayoutParams
            val screenWidth = resources.displayMetrics.widthPixels
            p.width = min(if (expanded) dp(326) else dp(224), screenWidth - dp(24))
            wm.updateViewLayout(view, p)
            view.animate().scaleX(if (expanded) 1f else .98f).scaleY(if (expanded) 1f else .98f)
                .setDuration(180).setInterpolator(DecelerateInterpolator()).start()
        } catch (_: Exception) {}
    }

    private fun send(pending: PendingIntent?) { try { pending?.send() } catch (_: Exception) {} }

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
        expanded = true
        resize()
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
