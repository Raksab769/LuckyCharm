package com.example.luckycharm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A view that draws a charm hanging from a multi-segment rope, simulated
 * with Verlet integration — the same family of technique used for realistic
 * rope/chain physics in games. This is deliberately NOT a single rigid rod
 * rotating around a pivot: each point along the thread has its own position
 * history and is pulled toward its neighbors by a distance constraint, so a
 * flick travels down the rope as a curve/whip instead of the whole thing
 * snapping around as one stiff piece.
 *
 * Roughly:
 *  - `pointsX/Y` are the current positions of N+1 points along the rope.
 *  - `prevX/Y` are where each point was last frame — the difference between
 *    the two IS the point's velocity, which is the core Verlet trick: we
 *    never store velocity explicitly, we just remember the previous
 *    position and gravity/damping fall out of the update naturally.
 *  - Point 0 is pinned to the anchor (the peg at the top).
 *  - Distance constraints between consecutive points are relaxed a few
 *    times per frame so the rope keeps a roughly constant length.
 *  - Dragging the charm moves the LAST point directly to the finger; when
 *    you let go, the point's last couple of frames of motion are already
 *    baked into `prevX/Y`, so it flies off with real momentum — no manual
 *    "velocity" bookkeeping needed for the flick.
 */
class CharmView(context: Context) : View(context) {

    var charm: CharmType = CharmType.CLOVER
        set(value) {
            field = value
            invalidate()
        }

    /** Called with the current absolute screen x (event.rawX) while the user drags the top peg to re-hang it. */
    var customColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var customLetter: String? = null
        set(value) {
            field = value
            invalidate()
        }

    var gyroSensitivity: Float = 1.0f

    var onRepositioned: ((screenX: Float) -> Unit)? = null

    /** Called every frame with the updated local coordinates of the charm's center. */
    var onCharmMoved: ((cx: Float, cy: Float) -> Unit)? = null

    /** Called when the user taps the charm without dragging it — the "ritual". */
    var onTapped: (() -> Unit)? = null

    companion object {
        private const val SEGMENTS = 10
        private const val GRAVITY = 2600f          // px/s^2, tuned for a satisfying visual weight
        private const val DAMPING = 0.985f          // velocity retained per step (air resistance)
        private const val CONSTRAINT_ITERATIONS = 8
        private const val FIXED_DT = 1f / 60f
    }

    private val pointsX = FloatArray(SEGMENTS + 1)
    private val pointsY = FloatArray(SEGMENTS + 1)
    private val prevX = FloatArray(SEGMENTS + 1)
    private val prevY = FloatArray(SEGMENTS + 1)

    var anchorX = 0f
    var anchorRatio = 0.5f
        set(value) {
            field = value
            if (width > 0) {
                anchorX = width * value
                onRepositioned?.invoke(anchorX)
                invalidate()
            }
        }
    private var anchorY = 0f
    private var segmentLength = 24f
    private var charmRadius = 40f

    private var ritualProgress = 0f

    private val threadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D8D8D8")
    }
    private val charmPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var windAccelX = 0f

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                    if (mode != Mode.DRAGGING_CHARM) {
                        // Apply gravity directly by shifting the wind/accel physics 
                        // x value goes from roughly -9.8 to 9.8. Multiply for strong visual impact.
                        val xGravity = event.values[0]
                        windAccelX = (-xGravity * 400f * gyroSensitivity)
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    if (mode != Mode.DRAGGING_CHARM) {
                        // event.values[1] is rotation rate around Y axis.
                        // Add a sudden burst of momentum when the phone is rotated quickly.
                        val gyroRoll = event.values[1]
                        val gyroImpact = (-gyroRoll * 1500f * gyroSensitivity)
                        if (abs(gyroImpact) > 50f) {
                            for (i in 1..SEGMENTS) {
                                pointsX[i] += gyroImpact * FIXED_DT
                            }
                        }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    
    // Track explicit touch velocities so they can be injected into the Verlet integrator
    private var touchVelocityX = 0f
    private var touchVelocityY = 0f
    private var lastTouchTime = 0L
    private var accumulatorSeconds = 0f
    private var lastFrameTimeNanos = 0L
    private var running = false

    private enum class Mode { NONE, REPOSITIONING, DRAGGING_CHARM }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameTimeNanos == 0L) {
                FIXED_DT
            } else {
                ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            }
            lastFrameTimeNanos = frameTimeNanos

            // Fixed-timestep accumulator: keeps the simulation numerically
            // stable regardless of the device's actual frame rate.
            accumulatorSeconds += dt
            var steps = 0
            while (accumulatorSeconds >= FIXED_DT && steps < 5) {
                simulateStep(FIXED_DT)
                accumulatorSeconds -= FIXED_DT
                steps++
            }

            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val drawableCache = mutableMapOf<Int, Drawable>()

    private fun getDrawableForCharm(charm: CharmType): Drawable? {
        val resId = charm.drawableRes ?: return null
        return drawableCache.getOrPut(resId) {
            ContextCompat.getDrawable(context, resId)!!
        }
    }

    init {
        running = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(frameCallback)
        gravitySensor?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroSensor?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        anchorX = w * anchorRatio
        anchorY = 0f // Pinned exactly to the top pixel of the screen
        // Considerably reduced charm size to make it "cute and comfortable" 
        // (roughly 24dp depending on screen width)
        charmRadius = (w * 0.08f).coerceIn(20f, 32f) 
        
        // Set string length to 0.40f as requested
        val ropeLength = (h * 0.23f).coerceAtLeast(120f)
        segmentLength = ropeLength / SEGMENTS

        // Lay the rope out straight and at rest the first time we know our size.
        for (i in 0..SEGMENTS) {
            pointsX[i] = anchorX
            pointsY[i] = anchorY + segmentLength * i
            prevX[i] = pointsX[i]
            prevY[i] = pointsY[i]
        }
    }

    private fun simulateStep(dt: Float) {
        // --- Integrate every free point (Verlet) ---
        for (i in 0..SEGMENTS) {
            if (i == 0) continue // point 0 is always the pinned anchor
            if (mode == Mode.DRAGGING_CHARM && i == SEGMENTS) continue // finger owns this point right now

            // Heavy damping while dragging prevents the rope from tangling or spinning wildly
            val currentDamping = if (mode == Mode.DRAGGING_CHARM) 0.75f else DAMPING

            val vx = (pointsX[i] - prevX[i]) * currentDamping
            val vy = (pointsY[i] - prevY[i]) * currentDamping
            prevX[i] = pointsX[i]
            prevY[i] = pointsY[i]
            
            // Soften gravity and disable wind while dragging so it hangs neatly
            val appliedGravity = if (mode == Mode.DRAGGING_CHARM) GRAVITY * 0.4f else GRAVITY
            val appliedWind = if (mode == Mode.DRAGGING_CHARM) 0f else windAccelX

            pointsX[i] += vx + appliedWind * dt * dt
            pointsY[i] += vy + appliedGravity * dt * dt
        }

        // Anchor is always pinned to the peg position.
        pointsX[0] = anchorX
        pointsY[0] = anchorY
        prevX[0] = anchorX
        prevY[0] = anchorY

        // --- Relax the distance constraints so segments keep their length ---
        repeat(CONSTRAINT_ITERATIONS) {
            for (i in 0 until SEGMENTS) {
                val dx = pointsX[i + 1] - pointsX[i]
                val dy = pointsY[i + 1] - pointsY[i]
                val dist = hypot(dx, dy).coerceAtLeast(0.0001f)
                val diff = (dist - segmentLength) / dist
                val offsetX = dx * 0.5f * diff
                val offsetY = dy * 0.5f * diff

                if (i != 0) {
                    pointsX[i] += offsetX
                    pointsY[i] += offsetY
                }
                if (!(mode == Mode.DRAGGING_CHARM && i + 1 == SEGMENTS)) {
                    pointsX[i + 1] -= offsetX
                    pointsY[i + 1] -= offsetY
                }
            }
            pointsX[0] = anchorX
            pointsY[0] = anchorY
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Use event.x/y since the service has already offset them into the drawing window's coordinate space
        val x = event.x
        val y = event.y
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                lastTouchTime = System.nanoTime()
                touchVelocityX = 0f
                touchVelocityY = 0f
                
                val distToCharm = hypot(x - pointsX[SEGMENTS], y - pointsY[SEGMENTS])
                
                // Allow a generous grab radius to move the peg at the top
                val distToPeg = hypot(x - anchorX, y - anchorY)
                mode = when {
                    distToPeg < charmRadius * 4.0f -> Mode.REPOSITIONING
                    distToCharm < charmRadius * 2.5f -> Mode.DRAGGING_CHARM
                    else -> Mode.NONE
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val now = System.nanoTime()
                val dt = (now - lastTouchTime) / 1_000_000_000f
                if (dt > 0) {
                    touchVelocityX = (x - pointsX[SEGMENTS]) / dt
                    touchVelocityY = (y - pointsY[SEGMENTS]) / dt
                }
                lastTouchTime = now

                when (mode) {
                    Mode.REPOSITIONING -> {
                        anchorX = x
                        if (width > 0) anchorRatio = anchorX / width
                        onRepositioned?.invoke(x)
                    }
                    Mode.DRAGGING_CHARM -> {
                        // Max out the rope length but allow a tiny bit of "bungee" stretch so pulling down feels tangible
                        val maxLen = segmentLength * SEGMENTS * 1.0f
                        val dx = x - anchorX
                        val dy = y - anchorY
                        val dist = hypot(dx, dy)
                        
                        val targetX = if (dist > maxLen) anchorX + (dx / dist) * maxLen else x
                        val targetY = if (dist > maxLen) anchorY + (dy / dist) * maxLen else y

                        // Ensure that previous position is also locked so no false momentum builds up
                        // while we are simply holding it taut at the bottom.
                        prevX[SEGMENTS] = pointsX[SEGMENTS]
                        prevY[SEGMENTS] = pointsY[SEGMENTS]

                        pointsX[SEGMENTS] = targetX
                        pointsY[SEGMENTS] = targetY
                    }
                    Mode.NONE -> {}
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val moved = hypot(x - downX, y - downY)
                if (mode == Mode.NONE || (mode == Mode.DRAGGING_CHARM && moved < 8f)) {
                    performClick()
                } else if (mode == Mode.DRAGGING_CHARM) {
                    // We just released the charm. 
                    // Calculate how much distance the user moved their finger in the last couple frames
                    // to determine their flick intent versus simply dropping it.
                    val dropDist = hypot(event.x - prevX[SEGMENTS], event.y - prevY[SEGMENTS])
                    
                    if (dropDist < 5f) {
                        // User was holding it still and just let go. 
                        // Simulate a realistic drop by artificially zeroing out the previous position,
                        // forcing the physics engine to pull it back up solely using the rope's tension gravity.
                        prevX[SEGMENTS] = pointsX[SEGMENTS]
                        prevY[SEGMENTS] = pointsY[SEGMENTS]
                    } else {
                        // User flicked it. Inject massive kinetic energy exactly matching the user's flick!
                        val cappedVx = touchVelocityX.coerceIn(-5000f, 5000f)
                        val cappedVy = touchVelocityY.coerceIn(-5000f, 5000f)
                        
                        prevX[SEGMENTS] = pointsX[SEGMENTS] - (cappedVx * FIXED_DT)
                        prevY[SEGMENTS] = pointsY[SEGMENTS] - (cappedVy * FIXED_DT)
                    }
                }
                mode = Mode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        playRitual()
        onTapped?.invoke()
        return true
    }

    private fun playRitual() {
        // A little push on the charm end of the rope, like giving it a real
        // flick, plus a spin/glow overlay handled by the charm's own draw().
        prevX[SEGMENTS] = pointsX[SEGMENTS] + 40f
        prevY[SEGMENTS] = pointsY[SEGMENTS]

        ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 900
            addUpdateListener {
                ritualProgress = it.animatedValue as Float
                invalidate()
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Thread: a smooth line through the simulated rope points.
        val path = Path().apply { moveTo(pointsX[0], pointsY[0]) }
        for (i in 1..SEGMENTS) {
            val midX = (pointsX[i - 1] + pointsX[i]) / 2f
            val midY = (pointsY[i - 1] + pointsY[i]) / 2f
            path.quadTo(pointsX[i - 1], pointsY[i - 1], midX, midY)
        }
        path.lineTo(pointsX[SEGMENTS], pointsY[SEGMENTS])
        canvas.drawPath(path, threadPaint)

        // Top anchor peg to visually show where to drag to reposition the string
        val density = resources.displayMetrics.density
        val topPegPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#997A22") }
        canvas.drawRect(anchorX - 16f * density, anchorY, anchorX + 16f * density, anchorY + 4f * density, topPegPaint)
        canvas.drawCircle(anchorX, anchorY + 4f * density, 6f * density, beadPaint)
        canvas.drawCircle(anchorX, anchorY + 4f * density, 3f * density, topPegPaint)

        // A couple of small decorative beads along the thread
        canvas.drawCircle(pointsX[SEGMENTS - 3], pointsY[SEGMENTS - 3], 5f, beadPaint)
        canvas.drawCircle(pointsX[SEGMENTS - 2], pointsY[SEGMENTS - 2], 4f, beadPaint)

        val customDrawable = getDrawableForCharm(charm)
        charm.draw(canvas, charmPaint, pointsX[SEGMENTS], pointsY[SEGMENTS], charmRadius, ritualProgress, customColor, customLetter, customDrawable)
        
        onCharmMoved?.invoke(pointsX[SEGMENTS], pointsY[SEGMENTS])
    }

    fun cleanup() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        sensorManager.unregisterListener(sensorEventListener)
    }
}
