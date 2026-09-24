package com.example.luckycharm

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class StringType(val displayName: String, val defaultColor: Int) {
    BASIC("Basic Thread", Color.parseColor("#AAAAAA")),
    RED_KNOT("Red Knot", Color.parseColor("#D32F2F")),
    STAR_CHAIN("Star Chain", Color.parseColor("#B0BEC5")),
    GEM_BEADS("Gem Beads", Color.parseColor("#555555")),
    LEATHER_BELL("Leather & Bell", Color.parseColor("#795548")),
    GOLD_CHAIN("Gold Chain", Color.parseColor("#F5B041")),
    PEARL("Pearl Necklace", Color.parseColor("#FDFAF5")),
    NEON_WIRE("Neon Wire", Color.parseColor("#00E5FF")),
    VINE("Nature Vine", Color.parseColor("#43A047")),
    DIAMOND("Diamond", Color.parseColor("#E0E0E0")),
    TINY_HEARTS("Tiny Hearts", Color.parseColor("#E91E63")),
    LINE_PATTERN("Line Pattern", Color.parseColor("#FF9800"));

    fun draw(
        canvas: Canvas,
        paint: Paint,
        path: Path,
        pointsX: FloatArray,
        pointsY: FloatArray,
        segments: Int,
        customColor: Int?,
        density: Float
    ) {
        val colorToUse = customColor ?: defaultColor
        paint.color = colorToUse
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        when (this) {
            BASIC -> {
                paint.strokeWidth = 2.5f * density
                canvas.drawPath(path, paint)
            }
            RED_KNOT -> {
                paint.strokeWidth = 3.5f * density
                canvas.drawPath(path, paint)
                
                // Draw gold knots at specific points
                paint.style = Paint.Style.FILL
                val knotColor = Color.parseColor("#FBC02D")
                val knotPaint = Paint(paint).apply { color = knotColor }
                
                val knotPositions = listOf(segments / 3, segments * 2 / 3)
                for (i in knotPositions) {
                    if (i < segments) {
                        // A simple stylized knot: a central circle with overlapping rings
                        canvas.drawCircle(pointsX[i], pointsY[i], 4f * density, knotPaint)
                        knotPaint.style = Paint.Style.STROKE
                        knotPaint.strokeWidth = 1.5f * density
                        canvas.drawCircle(pointsX[i], pointsY[i], 6f * density, knotPaint)
                        knotPaint.style = Paint.Style.FILL
                    }
                }
            }
            STAR_CHAIN -> {
                paint.strokeWidth = 1.5f * density
                canvas.drawPath(path, paint)
                
                paint.style = Paint.Style.FILL
                for (i in 1 until segments step 2) {
                    drawStar(canvas, paint, pointsX[i], pointsY[i], 4f * density)
                }
            }
            GEM_BEADS -> {
                paint.strokeWidth = 1.5f * density
                canvas.drawPath(path, paint)
                
                paint.style = Paint.Style.FILL
                val gemColors = intArrayOf(
                    Color.parseColor("#00BCD4"), // Turquoise
                    Color.parseColor("#9C27B0"), // Amethyst
                    Color.parseColor("#8BC34A"), // Jade
                    Color.parseColor("#FDFAF5"), // Pearl
                    Color.parseColor("#424242")  // Onyx
                )
                
                for (i in 1..segments) {
                    // Draw one bead at the point
                    paint.color = gemColors[i % gemColors.size]
                    canvas.drawCircle(pointsX[i], pointsY[i], 4.5f * density, paint)
                    
                    // Draw another bead midway
                    val midX = (pointsX[i] + pointsX[i-1]) / 2f
                    val midY = (pointsY[i] + pointsY[i-1]) / 2f
                    paint.color = gemColors[(i + 2) % gemColors.size]
                    canvas.drawCircle(midX, midY, 4.5f * density, paint)
                }
            }
            LEATHER_BELL -> {
                paint.strokeWidth = 3.5f * density
                canvas.drawPath(path, paint)
                
                // Draw a small bell near the bottom
                val bellIdx = segments - 2
                if (bellIdx > 0) {
                    val bx = pointsX[bellIdx]
                    val by = pointsY[bellIdx]
                    
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#FBC02D") // Brass
                    canvas.drawCircle(bx, by, 6f * density, paint)
                    
                    paint.color = Color.parseColor("#3E2723") // Dark slot
                    canvas.drawCircle(bx, by + 2.5f * density, 1.5f * density, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1f * density
                    canvas.drawLine(bx, by + 2.5f * density, bx, by + 6f * density, paint)
                }
            }
            GOLD_CHAIN -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f * density
                
                for (i in 1..segments) {
                    val cx = (pointsX[i] + pointsX[i-1]) / 2f
                    val cy = (pointsY[i] + pointsY[i-1]) / 2f
                    val dx = pointsX[i] - pointsX[i-1]
                    val dy = pointsY[i] - pointsY[i-1]
                    val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    
                    canvas.save()
                    canvas.translate(cx, cy)
                    canvas.rotate(angle)
                    
                    val linkLength = 8f * density
                    val linkWidth = 3.5f * density
                    val rect = RectF(-linkLength/2, -linkWidth/2, linkLength/2, linkWidth/2)
                    canvas.drawRoundRect(rect, linkWidth/2, linkWidth/2, paint)
                    
                    // Cross link
                    canvas.drawOval(RectF(-1.5f*density, -linkWidth, 1.5f*density, linkWidth), paint)
                    canvas.restore()
                }
            }
            PEARL -> {
                paint.style = Paint.Style.FILL
                
                for (i in 1..segments) {
                    val cx = (pointsX[i] + pointsX[i-1]) / 2f
                    val cy = (pointsY[i] + pointsY[i-1]) / 2f
                    
                    canvas.drawCircle(pointsX[i], pointsY[i], 4f * density, paint)
                    canvas.drawCircle(cx, cy, 4f * density, paint)
                }
            }
            NEON_WIRE -> {
                // Glow
                paint.style = Paint.Style.STROKE
                paint.color = Color.argb(100, Color.red(colorToUse), Color.green(colorToUse), Color.blue(colorToUse))
                paint.strokeWidth = 7f * density
                canvas.drawPath(path, paint)
                
                // Core
                paint.color = Color.WHITE
                paint.strokeWidth = 2f * density
                canvas.drawPath(path, paint)
            }
            VINE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                canvas.drawPath(path, paint)
                
                paint.style = Paint.Style.FILL
                for (i in 1 until segments) {
                    val isRight = i % 2 == 0
                    val dx = pointsX[i] - pointsX[i-1]
                    val dy = pointsY[i] - pointsY[i-1]
                    val angle = atan2(dy.toDouble(), dx.toDouble())
                    
                    val offsetAngle = angle + (if (isRight) Math.PI/2 else -Math.PI/2)
                    val leafX = pointsX[i] + (4f * density * cos(offsetAngle)).toFloat()
                    val leafY = pointsY[i] + (4f * density * sin(offsetAngle)).toFloat()
                    
                    canvas.save()
                    canvas.translate(leafX, leafY)
                    canvas.rotate(Math.toDegrees(angle).toFloat() + (if(isRight) 45f else -45f))
                    // Simple leaf shape using an oval
                    canvas.drawOval(RectF(-3f*density, -1.5f*density, 3f*density, 1.5f*density), paint)
                    canvas.restore()
                }
            }
            DIAMOND -> {
                paint.style = Paint.Style.FILL
                for (i in 1..segments) {
                    val cx = (pointsX[i] + pointsX[i-1]) / 2f
                    val cy = (pointsY[i] + pointsY[i-1]) / 2f
                    
                    val s = 2.5f * density
                    canvas.drawRect(pointsX[i] - s, pointsY[i] - s, pointsX[i] + s, pointsY[i] + s, paint)
                    canvas.drawRect(cx - s, cy - s, cx + s, cy + s, paint)
                    
                    val highlight = Paint(paint).apply { color = Color.WHITE }
                    canvas.drawCircle(pointsX[i], pointsY[i], 1f * density, highlight)
                    canvas.drawCircle(cx, cy, 1f * density, highlight)
                }
            }
            TINY_HEARTS -> {
                paint.strokeWidth = 1.0f * density
                canvas.drawPath(path, paint)
                
                paint.style = Paint.Style.FILL
                for (i in 1..segments) {
                    drawHeart(canvas, paint, pointsX[i], pointsY[i], 4.5f * density)
                }
            }
            LINE_PATTERN -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * density
                
                for (i in 1..segments) {
                    val cx = (pointsX[i] + pointsX[i-1]) / 2f
                    val cy = (pointsY[i] + pointsY[i-1]) / 2f
                    val dx = pointsX[i] - pointsX[i-1]
                    val dy = pointsY[i] - pointsY[i-1]
                    val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    
                    canvas.save()
                    canvas.translate(cx, cy)
                    canvas.rotate(angle)
                    
                    // Draw dashed segments
                    canvas.drawLine(-6f * density, 0f, -2f * density, 0f, paint)
                    canvas.drawLine(2f * density, 0f, 6f * density, 0f, paint)
                    
                    // Draw a dot in the middle
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(0f, 0f, 1.5f * density, paint)
                    paint.style = Paint.Style.STROKE
                    
                    canvas.restore()
                }
            }
        }
    }

    private fun drawHeart(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float) {
        val path = Path()
        path.moveTo(cx, cy + r * 0.8f)
        path.cubicTo(
            cx - r * 1.5f, cy,
            cx - r * 1.0f, cy - r * 1.0f,
            cx, cy - r * 0.2f
        )
        path.cubicTo(
            cx + r * 1.0f, cy - r * 1.0f,
            cx + r * 1.5f, cy,
            cx, cy + r * 0.8f
        )
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawStar(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float) {
        val path = Path()
        val points = 5
        val innerR = r * 0.45f
        for (i in 0 until points * 2) {
            val angle = (Math.PI / points) * i - Math.PI / 2
            val rad = if (i % 2 == 0) r else innerR
            val x = cx + rad * cos(angle).toFloat()
            val y = cy + rad * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}
