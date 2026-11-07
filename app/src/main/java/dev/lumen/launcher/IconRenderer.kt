package dev.lumen.launcher

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// Superellipse exponent. Higher = squarer. ~4.6 gives the soft "squircle" look.
private const val SQUIRCLE_N = 4.6

private fun squirclePath(size: Float): Path {
    val path = Path()
    val r = size / 2.0
    val exponent = 2.0 / SQUIRCLE_N
    val steps = 160
    for (i in 0..steps) {
        val t = 2.0 * PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = r + r * sign(c) * abs(c).pow(exponent)
        val y = r + r * sign(s) * abs(s).pow(exponent)
        if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
    }
    path.close()
    return path
}

/**
 * Old-style (non-adaptive) icons are usually a bare logo on transparency. Instead of leaving
 * them tiny on a grey square, put the logo on a clean tile: white for coloured logos,
 * dark for mostly-white logos so they stay visible.
 */
private fun legacyTile(drawable: Drawable, size: Int): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)

    val logoSize = (size * 0.64f).toInt()
    val logo = Bitmap.createBitmap(logoSize, logoSize, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, logoSize, logoSize)
    drawable.draw(Canvas(logo))

    val tiny = Bitmap.createScaledBitmap(logo, 8, 8, true)
    var r = 0
    var g = 0
    var b = 0
    var n = 0
    for (y in 0 until 8) {
        for (x in 0 until 8) {
            val p = tiny.getPixel(x, y)
            if (Color.alpha(p) > 128) {
                r += Color.red(p)
                g += Color.green(p)
                b += Color.blue(p)
                n++
            }
        }
    }
    tiny.recycle()
    val luminance = if (n == 0) 0.5f else (0.299f * r + 0.587f * g + 0.114f * b) / n / 255f

    val top: Int
    val bottom: Int
    if (luminance > 0.82f) {
        top = 0xFF4A5060.toInt()
        bottom = 0xFF262A33.toInt()
    } else {
        top = 0xFFFFFFFF.toInt()
        bottom = 0xFFE6E9EF.toInt()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), top, bottom, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

    val offset = (size - logoSize) / 2f
    canvas.drawBitmap(logo, offset, offset, null)
    logo.recycle()
    return out
}

/**
 * Renders any app icon as a uniform squircle with a soft baked-in drop shadow.
 * [icon] is the icon size in px, [pad] is extra transparent space on every side for the shadow.
 * Everything is pre-rendered once, so scrolling just blits bitmaps.
 */
internal fun Drawable.toIconBitmap(icon: Int, pad: Int): Bitmap {
    val src: Bitmap
    val adaptive = this as? AdaptiveIconDrawable
    if (adaptive != null) {
        src = Bitmap.createBitmap(icon, icon, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        canvas.drawColor(0xFFF4F5F8.toInt())
        // Layers are 108dp with a 72dp visible window: extend bounds so the visible part fills the tile.
        val extra = (icon * 0.25f).toInt()
        adaptive.background?.let {
            it.setBounds(-extra, -extra, icon + extra, icon + extra)
            it.draw(canvas)
        }
        adaptive.foreground?.let {
            it.setBounds(-extra, -extra, icon + extra, icon + extra)
            it.draw(canvas)
        }
    } else {
        src = legacyTile(this, icon)
    }

    // Squircle mask
    val masked = Bitmap.createBitmap(icon, icon, Bitmap.Config.ARGB_8888)
    val maskCanvas = Canvas(masked)
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    maskCanvas.drawPath(squirclePath(icon.toFloat()), maskPaint)
    maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    maskCanvas.drawBitmap(src, 0f, 0f, maskPaint)
    src.recycle()

    // Soft drop shadow, then the icon on top
    val out = Bitmap.createBitmap(icon + pad * 2, icon + pad * 2, Bitmap.Config.ARGB_8888)
    val outCanvas = Canvas(out)
    val blurPaint = Paint()
    blurPaint.maskFilter = BlurMaskFilter(pad * 0.6f, BlurMaskFilter.Blur.NORMAL)
    val offset = IntArray(2)
    val shadow = masked.extractAlpha(blurPaint, offset)
    val shadowPaint = Paint()
    shadowPaint.color = 0x4D000000
    outCanvas.drawBitmap(shadow, pad + offset[0].toFloat(), pad + offset[1] + pad * 0.25f, shadowPaint)
    outCanvas.drawBitmap(masked, pad.toFloat(), pad.toFloat(), null)
    shadow.recycle()
    masked.recycle()
    return out
}
