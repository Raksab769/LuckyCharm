package com.example.luckycharm

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every charm here is an original vector illustration drawn with Canvas Path
 * primitives — simple, generic motifs (clover, star, horseshoe, moon,
 * ladybird, heart) rather than reproductions of any specific existing
 * product or cultural artifact's exact artwork.
 */
enum class CharmType(
    val displayName: String,
    val baseColor: Int,
    @DrawableRes val drawableRes: Int? = null
) {
    CLOVER("Four-Leaf Clover", Color.parseColor("#3FA34D")),
    STAR("Wishing Star", Color.parseColor("#F5B301")),
    HORSESHOE("Horseshoe", Color.parseColor("#C9962C")),
    MOONBEAM("Crescent Moon", Color.parseColor("#6C63FF")),
    LADYBIRD("Ladybird", Color.parseColor("#E63946")),
    LOVE("Love Dangle", Color.parseColor("#E0457B")),
    PUBG_HELMET("Level 3 Helmet", Color.parseColor("#4B5320")),
    PUBG_PAN("Winner Pan", Color.parseColor("#2C2C2C")),
    ALPHABET("Alphabet Letter", Color.parseColor("#E9C46A")),
    PHOTO_1("Photo Charm 1", Color.parseColor("#8E44AD"), R.drawable.istockphoto_1005374612_612x612),
    PHOTO_2("Photo Charm 2", Color.parseColor("#2980B9"), R.drawable.istockphoto_1339851357_612x612);
    /** Draw the charm centered at (cx, cy) with the given radius. [ritual] is 0..1 progress of the tap animation. */
    fun draw(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        radius: Float,
        ritual: Float,
        customColor: Int? = null,
        letter: String? = null,
        customDrawable: Drawable? = null
    ) {
        paint.style = Paint.Style.FILL
        paint.color = customColor ?: baseColor

        if (customDrawable != null) {
            drawPhoto(canvas, paint, cx, cy, radius, ritual, customDrawable)
            return
        }

        when (this) {
            CLOVER -> drawClover(canvas, paint, cx, cy, radius, ritual, customColor)
            STAR -> drawStar(canvas, paint, cx, cy, radius, ritual, customColor)
            HORSESHOE -> drawHorseshoe(canvas, paint, cx, cy, radius, ritual, customColor)
            MOONBEAM -> drawMoon(canvas, paint, cx, cy, radius, ritual, customColor)
            LADYBIRD -> drawLadybird(canvas, paint, cx, cy, radius, ritual, customColor)
            LOVE -> drawLove(canvas, paint, cx, cy, radius, ritual, customColor)
            PUBG_HELMET -> drawPubgHelmet(canvas, paint, cx, cy, radius, ritual, customColor)
            PUBG_PAN -> drawPubgPan(canvas, paint, cx, cy, radius, ritual, customColor)
            ALPHABET -> drawAlphabet(canvas, paint, cx, cy, radius, ritual, customColor, letter)
            else -> canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawPhoto(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        radius: Float,
        ritual: Float,
        drawable: Drawable
    ) {
        val spin = ritual * 2f * Math.PI.toFloat()
        canvas.save()
        canvas.rotate(Math.toDegrees(spin.toDouble()).toFloat(), cx, cy)

        // Top bail ring to attach to the string
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.12f
        paint.color = Color.parseColor("#C9962C")
        canvas.drawCircle(cx, cy - radius * 0.85f, radius * 0.2f, paint)

        // Clip path to round the photo into a smooth circular amulet
        canvas.save()
        val clipPath = Path().apply {
            addCircle(cx, cy, radius * 0.95f, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        val size = (radius * 2.2f).toInt()
        drawable.setBounds(
            (cx - size / 2).toInt(),
            (cy - size / 2).toInt(),
            (cx + size / 2).toInt(),
            (cy + size / 2).toInt()
        )
        drawable.draw(canvas)
        canvas.restore() // restore clip

        // Outer Gold Rim Frame
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.1f
        paint.color = Color.parseColor("#C9962C")
        canvas.drawCircle(cx, cy, radius * 0.95f, paint)

        canvas.restore() // restore rotation
    }

    private fun drawClover(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, ritual: Float, customColor: Int?) {
        val leafR = r * 0.55f
        val offset = r * 0.5f
        val glowScale = 1f + ritual * 0.25f
        val positions = listOf(
            -offset to -offset, offset to -offset,
            -offset to offset, offset to offset
        )
        for ((dx, dy) in positions) {
            canvas.drawCircle(cx + dx * glowScale, cy + dy * glowScale, leafR * glowScale, paint)
        }
        val stemColor = customColor?.let { darkenColor(it) } ?: Color.parseColor("#2C7A3B")
        val stemPaint = Paint(paint).apply { color = stemColor }
        canvas.drawRect(cx - r * 0.06f, cy, cx + r * 0.06f, cy + r * 0.9f, stemPaint)
    }

    private fun drawStar(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, ritual: Float, customColor: Int?) {
        val path = Path()
        val spin = ritual * 2f * Math.PI.toFloat()
        val points = 5
        val outerR = r * (1f + ritual * 0.15f)
        val innerR = outerR * 0.45f
        for (i in 0 until points * 2) {
            val angle = spin + (Math.PI.toFloat() / points) * i - Math.PI.toFloat() / 2
            val rad = if (i % 2 == 0) outerR else innerR
            val x = cx + rad * cos(angle)
            val y = cy + rad * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawHorseshoe(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, ritual: Float, customColor: Int?) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.35f
        paint.strokeCap = Paint.Cap.ROUND
        val sweep = 220f + ritual * 40f
        val rect = RectF(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f)
        canvas.drawArc(rect, 160f, sweep, false, paint)
        paint.style = Paint.Style.FILL
        val studColor = customColor?.let { darkenColor(it) } ?: Color.parseColor("#8A6A1E")
        val studPaint = Paint(paint).apply { color = studColor }
        for (t in listOf(0.05f, 0.95f)) {
            val angle = Math.toRadians((160f + sweep * t).toDouble())
            val x = cx + (r * 0.7f) * cos(angle).toFloat()
            val y = cy + (r * 0.7f) * sin(angle).toFloat()
            canvas.drawCircle(x, y, r * 0.08f, studPaint)
        }
    }

    private fun drawMoon(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, ritual: Float, customColor: Int?) {
        canvas.drawCircle(cx, cy, r * 0.75f, paint)
        // Background cutout circle to make a crescent
        val bgPaint = Paint(paint).apply { color = Color.parseColor("#1B1B2F") }
        canvas.drawCircle(cx + r * (0.35f - ritual * 0.15f), cy - r * 0.05f, r * 0.65f, bgPaint)
        if (ritual > 0.3f) {
            val sparklePaint = Paint(paint).apply { color = Color.parseColor("#FFF3B0") }
            canvas.drawCircle(cx - r * 0.9f, cy - r * 0.9f, r * 0.08f * ritual, sparklePaint)
            canvas.drawCircle(cx + r * 1.0f, cy - r * 0.5f, r * 0.06f * ritual, sparklePaint)
        }
    }

    private fun drawLadybird(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, ritual: Float, customColor: Int?) {
        val wingSpread = ritual * r * 0.4f
        canvas.drawCircle(cx - wingSpread, cy, r * 0.8f, paint)
        canvas.drawCircle(cx + wingSpread, cy, r * 0.8f, paint)
        val headPaint = Paint(paint).apply { color = Color.BLACK }
        canvas.drawCircle(cx, cy - r * 0.75f, r * 0.35f, headPaint)
        val spotPaint = Paint(paint).apply { color = Color.BLACK }
        canvas.drawCircle(cx - wingSpread - r * 0.25f, cy - r * 0.1f, r * 0.14f, spotPaint)
        canvas.drawCircle(cx + wingSpread + r * 0.25f, cy + r * 0.2f, r * 0.14f, spotPaint)
        canvas.drawLine(cx - wingSpread, cy - r * 0.75f, cx + wingSpread, cy - r * 0.75f, headPaint.apply { strokeWidth = r * 0.06f })
    }

    private fun drawLove(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, ritual: Float, customColor: Int?) {
        val beat = if (ritual < 0.5f) {
            (sin(ritual * 4f * Math.PI.toFloat()).coerceAtLeast(0f)) * 0.18f
        } else 0f
        val scale = 1f + beat

        val path = Path()
        val topLift = r * 0.35f * scale
        val lobeR = r * 0.5f * scale
        path.moveTo(cx, cy + r * 0.75f * scale)
        path.cubicTo(
            cx - r * 1.1f * scale, cy - r * 0.1f * scale,
            cx - r * 0.9f * scale, cy - topLift - lobeR,
            cx, cy - topLift + lobeR * 0.15f
        )
        path.cubicTo(
            cx + r * 0.9f * scale, cy - topLift - lobeR,
            cx + r * 1.1f * scale, cy - r * 0.1f * scale,
            cx, cy + r * 0.75f * scale
        )
        path.close()
        canvas.drawPath(path, paint)

        val highlightPaint = Paint(paint).apply { color = Color.parseColor("#88FFFFFF") }
        canvas.drawCircle(cx - r * 0.32f, cy - topLift * 0.4f, r * 0.16f * scale, highlightPaint)

        if (ritual in 0.15f..0.85f) {
            val sparklePaint = Paint(paint).apply { color = Color.parseColor("#FFE1EC") }
            canvas.drawCircle(cx - r * 1.3f, cy - r * 0.4f, r * 0.07f, sparklePaint)
            canvas.drawCircle(cx + r * 1.25f, cy - r * 0.1f, r * 0.05f, sparklePaint)
        }
    }

    private fun drawPubgHelmet(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, ritual: Float, customColor: Int?) {
        val shake = sin(ritual * 8f * Math.PI.toFloat()) * (r * 0.15f)
        val rx = cx + shake
        val ry = cy

        // If custom color is passed, use it for the helmet, else default to dark green/grey
        paint.color = customColor ?: Color.parseColor("#3B3B3B")
        val domePath = Path()
        domePath.moveTo(rx - r * 0.8f, ry + r * 0.2f)
        domePath.cubicTo(rx - r * 0.8f, ry - r * 1.2f, rx + r * 0.8f, ry - r * 1.2f, rx + r * 0.8f, ry + r * 0.2f)
        domePath.close()
        canvas.drawPath(domePath, paint)

        paint.color = Color.parseColor("#1C1C1C")
        val visorRect = RectF(rx - r * 0.85f, ry - r * 0.3f, rx + r * 0.85f, ry + r * 0.5f)
        canvas.drawRoundRect(visorRect, r * 0.15f, r * 0.15f, paint)

        paint.color = Color.parseColor("#050505")
        val slitRect = RectF(rx - r * 0.7f, ry - r * 0.1f, rx + r * 0.7f, ry + r * 0.15f)
        canvas.drawRoundRect(slitRect, r * 0.05f, r * 0.05f, paint)
        
        paint.color = Color.parseColor("#7A7A7A")
        canvas.drawCircle(rx - r * 0.7f, ry - r * 0.2f, r * 0.08f, paint)
        canvas.drawCircle(rx + r * 0.7f, ry - r * 0.2f, r * 0.08f, paint)
        canvas.drawCircle(rx - r * 0.7f, ry + r * 0.4f, r * 0.08f, paint)
        canvas.drawCircle(rx + r * 0.7f, ry + r * 0.4f, r * 0.08f, paint)
    }

    private fun drawPubgPan(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, ritual: Float, customColor: Int?) {
        val spin = ritual * 4f * Math.PI.toFloat()
        
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(Math.toDegrees(spin.toDouble()).toFloat())

        paint.color = Color.parseColor("#1A1A1A")
        val handleRect = RectF(-r * 0.1f, r * 0.6f, r * 0.1f, r * 1.6f)
        canvas.drawRoundRect(handleRect, r * 0.05f, r * 0.05f, paint)
        
        paint.color = customColor ?: Color.parseColor("#2C2C2C")
        canvas.drawCircle(0f, -r * 0.2f, r * 0.8f, paint)
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.06f
        paint.color = Color.parseColor("#111111")
        canvas.drawCircle(0f, -r * 0.2f, r * 0.7f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#5A5A5A")
        canvas.drawCircle(-r * 0.3f, -r * 0.1f, r * 0.15f, paint)
        paint.color = Color.parseColor("#111111")
        canvas.drawCircle(-r * 0.32f, -r * 0.12f, r * 0.08f, paint)

        canvas.restore()
        paint.style = Paint.Style.FILL
    }

    private fun drawAlphabet(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, ritual: Float, customColor: Int?, letter: String?) {
        val displayLetter = (letter?.take(1) ?: "A").uppercase()
        val spin = ritual * 2f * Math.PI.toFloat()
        
        canvas.save()
        canvas.rotate(Math.toDegrees(spin.toDouble()).toFloat(), cx, cy)

        // Draw coin base
        paint.color = customColor ?: Color.parseColor("#E9C46A")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r * 0.9f, paint)
        
        // Draw rim
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.1f
        paint.color = darkenColor(paint.color)
        canvas.drawCircle(cx, cy, r * 0.85f, paint)
        
        // Draw text
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B263B") // dark navy
        paint.textSize = r * 1.2f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        // adjust cy to center text vertically
        val textOffset = (paint.descent() + paint.ascent()) / 2
        canvas.drawText(displayLetter, cx, cy - textOffset, paint)

        canvas.restore()
        paint.isFakeBoldText = false
    }

    private fun drawPuppy(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, ritual: Float, customColor: Int?) {
        val earFlap = sin(ritual * 4f * Math.PI.toFloat()) * 15f // degrees
        
        // Base color
        val basePuppyColor = customColor ?: Color.parseColor("#F5F5DC") // Cream/Beige instead of Tan
        val headR = r * 0.8f
        
        // Ears (Darker brown/black for contrast, very floppy)
        paint.color = Color.parseColor("#5C4033") // Dark Brown
        
        // Left Ear
        canvas.save()
        canvas.translate(cx - headR * 0.6f, cy - headR * 0.3f)
        canvas.rotate(earFlap)
        val leftEar = RectF(-r*0.35f, -r*0.1f, r*0.25f, r*1.1f)
        canvas.drawRoundRect(leftEar, r*0.3f, r*0.3f, paint)
        canvas.restore()

        // Right Ear
        canvas.save()
        canvas.translate(cx + headR * 0.6f, cy - headR * 0.3f)
        canvas.rotate(-earFlap)
        val rightEar = RectF(-r*0.25f, -r*0.1f, r*0.35f, r*1.1f)
        canvas.drawRoundRect(rightEar, r*0.3f, r*0.3f, paint)
        canvas.restore()

        // Head (chubby round shape)
        paint.color = basePuppyColor
        canvas.drawCircle(cx, cy, headR, paint)
        // Cheeks
        canvas.drawCircle(cx - headR*0.3f, cy + headR*0.2f, headR*0.6f, paint)
        canvas.drawCircle(cx + headR*0.3f, cy + headR*0.2f, headR*0.6f, paint)

        // Snout area (white)
        paint.color = Color.parseColor("#FFFFFF")
        canvas.drawCircle(cx, cy + headR * 0.25f, headR * 0.55f, paint)

        // Cute big Nose
        paint.color = Color.parseColor("#1A1A1A")
        val noseRect = RectF(cx - r*0.25f, cy + headR*0.1f, cx + r*0.25f, cy + headR*0.3f)
        canvas.drawRoundRect(noseRect, r*0.15f, r*0.15f, paint)
        // Nose glint
        paint.color = Color.WHITE
        canvas.drawCircle(cx - r*0.1f, cy + headR*0.15f, r*0.05f, paint)

        // Big Anime Eyes
        paint.color = Color.parseColor("#1A1A1A")
        val eyeOffsetX = headR * 0.4f
        val eyeOffsetY = -headR * 0.15f
        canvas.drawCircle(cx - eyeOffsetX, cy + eyeOffsetY, r * 0.2f, paint)
        canvas.drawCircle(cx + eyeOffsetX, cy + eyeOffsetY, r * 0.2f, paint)
        
        // Eye glints (multiple for cuteness)
        paint.color = Color.WHITE
        canvas.drawCircle(cx - eyeOffsetX + r*0.05f, cy + eyeOffsetY - r*0.08f, r * 0.08f, paint)
        canvas.drawCircle(cx - eyeOffsetX - r*0.08f, cy + eyeOffsetY + r*0.05f, r * 0.04f, paint)
        
        canvas.drawCircle(cx + eyeOffsetX + r*0.05f, cy + eyeOffsetY - r*0.08f, r * 0.08f, paint)
        canvas.drawCircle(cx + eyeOffsetX - r*0.08f, cy + eyeOffsetY + r*0.05f, r * 0.04f, paint)
        
        // Happy open mouth / Tongue
        val tongueExtension = if (ritual > 0.1f) sin(ritual * Math.PI.toFloat()) * r * 0.4f else r * 0.1f
        paint.color = Color.parseColor("#FF99C8")
        val tongueRect = RectF(cx - r*0.2f, cy + headR*0.4f, cx + r*0.2f, cy + headR*0.45f + tongueExtension)
        canvas.drawRoundRect(tongueRect, r*0.15f, r*0.15f, paint)
        
        // tongue line
        paint.color = Color.parseColor("#E07A9F")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.02f
        canvas.drawLine(cx, cy + headR*0.45f, cx, cy + headR*0.45f + tongueExtension - r*0.05f, paint)
        paint.style = Paint.Style.FILL
    }
    
    private fun darkenColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] *= 0.8f // Darken value
        return Color.HSVToColor(hsv)
    }
}
