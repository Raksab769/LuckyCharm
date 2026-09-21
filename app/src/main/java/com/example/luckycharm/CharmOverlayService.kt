package com.example.luckycharm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class CharmOverlayService : Service() {

    companion object {
        const val EXTRA_CHARM = "extra_charm"
        const val EXTRA_SENSITIVITY = "extra_sensitivity"
        const val EXTRA_COLOR = "extra_color"
        const val EXTRA_POSITION_RATIO = "extra_position_ratio"
        const val ACTION_HIDE = "com.example.luckycharm.action.HIDE"
        private const val CHANNEL_ID = "lucky_charm_channel"
        private const val NOTIF_ID = 1001
        private const val CHARM_SIZE_DP = 96
    }

    private lateinit var windowManager: WindowManager
    private var charmView: CharmView? = null
    private var pegTouchView: View? = null
    private var charmTouchView: View? = null
    
    private var drawParams: WindowManager.LayoutParams? = null
    private var pegParams: WindowManager.LayoutParams? = null
    private var charmTouchParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())

        val charmName = intent?.getStringExtra(EXTRA_CHARM)
        val charm = CharmType.entries.firstOrNull { it.name == charmName } ?: CharmType.CLOVER
        val sensitivity = intent?.getFloatExtra(EXTRA_SENSITIVITY, 1.0f) ?: 1.0f
        val positionRatio = intent?.getFloatExtra(EXTRA_POSITION_RATIO, 0.5f) ?: 0.5f
        val color = if (intent?.hasExtra(EXTRA_COLOR) == true) intent.getIntExtra(EXTRA_COLOR, 0) else null

        // Recreate overlay on orientation change or config change since bounds are different
        if (charmView != null) {
            val oldView = charmView
            oldView?.cleanup()
            windowManager.removeView(oldView)
            
            pegTouchView?.let { windowManager.removeView(it) }
            charmTouchView?.let { windowManager.removeView(it) }
            
            charmView = null
            pegTouchView = null
            charmTouchView = null
        }
        
        addOverlay(charm, sensitivity, color, positionRatio)

        return START_STICKY
    }

    private fun addOverlay(charm: CharmType, sensitivity: Float, color: Int?, positionRatio: Float) {
        val metrics = resources.displayMetrics
        val density = metrics.density
        val charmSizePx = (CHARM_SIZE_DP * density).toInt()
        
        // Use exact screen bounds so coordinate math perfectly aligns
        val windowWidthPx = metrics.widthPixels
        val windowHeightPx = metrics.heightPixels

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Drawing window (larger to allow swinging, NOT touchable so it doesn't block background apps)
        val drawParams = WindowManager.LayoutParams(
            windowWidthPx,
            windowHeightPx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0 // Drawing window spans the entire width now, so we never clip when swinging hard
            y = 0
        }

        val view = CharmView(this).apply {
            this.charm = charm
            this.gyroSensitivity = sensitivity
            this.customColor = color
            this.anchorRatio = positionRatio
        }
        windowManager.addView(view, drawParams)
        charmView = view
        this.drawParams = drawParams

        // 2. The Peg Hitbox (Small invisible window at the top to reposition the string)
        val pegTouchSize = (80f * density).toInt()
        pegParams = WindowManager.LayoutParams(
            pegTouchSize,
            pegTouchSize,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels / 2f - pegTouchSize / 2f).toInt()
            y = 0 // Top edge
        }
        pegTouchView = View(this).apply {
            setOnTouchListener { _, event ->
                // Adjust coordinates so the view receives them relative to the full-width drawing window
                event.offsetLocation(
                    (pegParams!!.x).toFloat(),
                    (pegParams!!.y).toFloat()
                )
                view.dispatchTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                true
            }
        }
        windowManager.addView(pegTouchView, pegParams)

        // 3. The Charm Hitbox (Small invisible window at the bottom to drag the charm)
        val charmRadiusApproxPx = (windowWidthPx * 0.08f).coerceIn(20f * density, 32f * density)
        val charmTouchSize = (charmRadiusApproxPx * 2.5f).toInt()
        charmTouchParams = WindowManager.LayoutParams(
            charmTouchSize,
            charmTouchSize,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (windowWidthPx / 2f - charmTouchSize / 2f).toInt()
            y = (windowHeightPx * 0.23f).toInt() // Updated to match new 0.22f short string ratio
        }

        charmTouchView = View(this).apply {
            setOnTouchListener { _, event ->
                event.offsetLocation(
                    (charmTouchParams!!.x).toFloat(),
                    (charmTouchParams!!.y).toFloat()
                )
                view.dispatchTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                true
            }
        }
        windowManager.addView(charmTouchView, charmTouchParams)

        // Keep the peg touch window aligned when the user moves it horizontally
        view.onRepositioned = { screenX ->
            pegParams?.x = (screenX - pegTouchSize / 2f).toInt()
            windowManager.updateViewLayout(pegTouchView, pegParams)
        }

        // Keep the charm touch window locked exactly onto the charm as it swings
        view.onCharmMoved = { cx, cy ->
            charmTouchParams?.let { tp ->
                // Account for the fact that the draw canvas now uses MATCH_PARENT width
                val targetX = (cx - charmTouchSize / 2f).toInt()
                val targetY = (cy - charmTouchSize / 2f).toInt()
                if (tp.x != targetX || tp.y != targetY) {
                    tp.x = targetX
                    tp.y = targetY
                    windowManager.updateViewLayout(charmTouchView, tp)
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Lucky Charm", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps your lucky charm hanging on screen" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val hideIntent = Intent(this, CharmOverlayService::class.java).apply { action = ACTION_HIDE }
        val hidePending = PendingIntent.getService(
            this, 0, hideIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("Lucky Charm is hanging around")
            .setContentText("Tap it for a little ritual, drag it to move it.")
            .setContentIntent(openPending)
            .addAction(0, "Hide", hidePending)
            .setOngoing(true)
            .build()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Refresh the overlay when the screen rotates (portrait/landscape)
        val currentCharm = charmView?.charm ?: CharmType.CLOVER
        val currentSensitivity = charmView?.gyroSensitivity ?: 1.0f
        val currentColor = charmView?.customColor

        charmView?.cleanup()
        charmView?.let { windowManager.removeView(it) }
        pegTouchView?.let { windowManager.removeView(it) }
        charmTouchView?.let { windowManager.removeView(it) }

        charmView = null
        pegTouchView = null
        charmTouchView = null

        addOverlay(currentCharm, currentSensitivity, currentColor, charmView?.anchorRatio ?: 0.5f)
    }

    override fun onDestroy() {
        super.onDestroy()
        charmView?.let {
            it.cleanup()
            windowManager.removeView(it)
        }
        charmView = null
        pegTouchView?.let {
            windowManager.removeView(it)
        }
        pegTouchView = null
        charmTouchView?.let {
            windowManager.removeView(it)
        }
        charmTouchView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
