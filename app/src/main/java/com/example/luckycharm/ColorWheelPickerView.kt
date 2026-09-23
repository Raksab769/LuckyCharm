package com.example.luckycharm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ColorWheelPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var color: Int
        get() = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        set(value) {
            val hsv = FloatArray(3)
            Color.colorToHSV(value, hsv)
            hue = hsv[0]
            saturation = hsv[1]
            this.value = hsv[2]
            invalidate()
        }

    var hue: Float = 0f // 0..360
    var saturation: Float = 1f // 0..1
    var value: Float = 1f // 0..1

    var onColorChanged: ((Int) -> Unit)? = null

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 6f
    }

    private val selectorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 10f
    }

    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var innerRadius = 0f
    private var wheelThickness = 0f

    private val boxRect = RectF()

    private enum class TouchTarget { NONE, WHEEL, BOX }
    private var activeTouch = TouchTarget.NONE

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f

        val minSize = minOf(w, h).toFloat()
        outerRadius = minSize * 0.46f
        wheelThickness = minSize * 0.12f
        innerRadius = outerRadius - wheelThickness

        wheelPaint.strokeWidth = wheelThickness

        val colors = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
            Color.BLUE, Color.MAGENTA, Color.RED
        )
        val sweepGradient = SweepGradient(centerX, centerY, colors, null)
        wheelPaint.shader = sweepGradient

        val boxHalfSize = innerRadius * 0.62f
        boxRect.set(
            centerX - boxHalfSize,
            centerY - boxHalfSize,
            centerX + boxHalfSize,
            centerY + boxHalfSize
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Outer Hue Wheel
        val radiusForWheel = outerRadius - wheelThickness / 2f
        canvas.drawCircle(centerX, centerY, radiusForWheel, wheelPaint)

        // Hue selector ring
        val angleRad = Math.toRadians(hue.toDouble())
        val hueSelX = centerX + radiusForWheel * cos(angleRad).toFloat()
        val hueSelY = centerY + radiusForWheel * sin(angleRad).toFloat()
        val selRadius = wheelThickness * 0.42f
        canvas.drawCircle(hueSelX, hueSelY, selRadius, selectorShadowPaint)
        canvas.drawCircle(hueSelX, hueSelY, selRadius, selectorPaint)

        // 2. Inner Saturation / Value Box
        val pureHueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))

        val satShader = LinearGradient(
            boxRect.left, boxRect.top, boxRect.right, boxRect.top,
            Color.WHITE, pureHueColor, Shader.TileMode.CLAMP
        )
        val valShader = LinearGradient(
            boxRect.left, boxRect.top, boxRect.left, boxRect.bottom,
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP
        )

        boxPaint.shader = ComposeShader(satShader, valShader, PorterDuff.Mode.DARKEN)
        canvas.drawRect(boxRect, boxPaint)

        // SV selector ring inside box
        val satX = boxRect.left + saturation * boxRect.width()
        val valY = boxRect.top + (1f - value) * boxRect.height()
        canvas.drawCircle(satX, valY, 12f, selectorShadowPaint)
        canvas.drawCircle(satX, valY, 10f, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.x - centerX
        val dy = event.y - centerY
        val dist = hypot(dx, dy)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val radiusForWheel = outerRadius - wheelThickness / 2f
                if (abs(dist - radiusForWheel) <= wheelThickness * 1.2f) {
                    activeTouch = TouchTarget.WHEEL
                    updateHue(dx, dy)
                } else if (boxRect.contains(event.x, event.y)) {
                    activeTouch = TouchTarget.BOX
                    updateSV(event.x, event.y)
                } else {
                    activeTouch = TouchTarget.NONE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTouch == TouchTarget.WHEEL) {
                    updateHue(dx, dy)
                } else if (activeTouch == TouchTarget.BOX) {
                    updateSV(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeTouch != TouchTarget.NONE) {
                    performClick()
                }
                activeTouch = TouchTarget.NONE
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateHue(dx: Float, dy: Float) {
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f
        hue = angle
        invalidate()
        onColorChanged?.invoke(color)
    }

    private fun updateSV(x: Float, y: Float) {
        saturation = ((x - boxRect.left) / boxRect.width()).coerceIn(0f, 1f)
        value = (1f - (y - boxRect.top) / boxRect.height()).coerceIn(0f, 1f)
        invalidate()
        onColorChanged?.invoke(color)
    }
}
