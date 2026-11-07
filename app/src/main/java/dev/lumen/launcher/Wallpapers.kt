package dev.lumen.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/** A soft glowing colour blob. Position and radius are fractions of the wallpaper size. */
internal class Blob(val cx: Float, val cy: Float, val radius: Float, val color: Int)

internal class WallpaperStyle(
    val name: String,
    val top: Int,
    val bottom: Int,
    val blobs: List<Blob>,
)

private fun c(hex: Long): Int = hex.toInt()

internal object WallpaperStyles {
    val all: List<WallpaperStyle> = listOf(
        WallpaperStyle(
            "Sunrise", c(0xFFFF8A71), c(0xFF7C5CFF),
            listOf(
                Blob(0.15f, 0.10f, 0.75f, c(0xAAFFD27A)),
                Blob(0.90f, 0.50f, 0.70f, c(0x99FF5FA2)),
                Blob(0.40f, 1.00f, 0.90f, c(0xAA4C6DFF)),
            ),
        ),
        WallpaperStyle(
            "Ocean", c(0xFF0E5A8A), c(0xFF18B7B0),
            listOf(
                Blob(0.80f, 0.10f, 0.70f, c(0x8861E3FF)),
                Blob(0.10f, 0.60f, 0.80f, c(0x88006D9E)),
                Blob(0.60f, 1.00f, 0.80f, c(0x8800E0B8)),
            ),
        ),
        WallpaperStyle(
            "Aurora", c(0xFF0B1F3A), c(0xFF1B6F6A),
            listOf(
                Blob(0.20f, 0.30f, 0.70f, c(0x8800E5A8)),
                Blob(0.90f, 0.65f, 0.75f, c(0x887B5CFF)),
                Blob(0.50f, 0.00f, 0.60f, c(0x6638C8FF)),
            ),
        ),
        WallpaperStyle(
            "Blossom", c(0xFFB03A8C), c(0xFF5B3CC4),
            listOf(
                Blob(0.10f, 0.20f, 0.80f, c(0x99FF8FC7)),
                Blob(0.90f, 0.80f, 0.80f, c(0x998A6BFF)),
                Blob(0.60f, 0.45f, 0.50f, c(0x66FFC0E0)),
            ),
        ),
        WallpaperStyle(
            "Meadow", c(0xFF1E7B5A), c(0xFF0B3D3A),
            listOf(
                Blob(0.80f, 0.10f, 0.80f, c(0x88C6F06B)),
                Blob(0.10f, 0.80f, 0.80f, c(0x8826C6A0)),
            ),
        ),
        WallpaperStyle(
            "Midnight", c(0xFF12142B), c(0xFF2B2F6B),
            listOf(
                Blob(0.70f, 0.20f, 0.70f, c(0x884D6BFF)),
                Blob(0.20f, 0.85f, 0.80f, c(0x77B05CFF)),
            ),
        ),
    )
}

internal fun renderWallpaper(style: WallpaperStyle, w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    val wf = w.toFloat()
    val hf = h.toFloat()

    paint.shader = LinearGradient(0f, 0f, 0f, hf, style.top, style.bottom, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, wf, hf, paint)

    val m = maxOf(wf, hf)
    for (b in style.blobs) {
        paint.shader = RadialGradient(
            b.cx * wf,
            b.cy * hf,
            b.radius * m,
            b.color,
            b.color and 0x00FFFFFF,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, wf, hf, paint)
    }
    return bmp
}

/** Renders the wallpaper (a little wider than the screen, so home-page parallax has room) and sets it. */
internal fun applyWallpaper(context: Context, style: WallpaperStyle): Boolean {
    val dm = context.resources.displayMetrics
    val bmp = renderWallpaper(style, (dm.widthPixels * 1.3f).toInt(), dm.heightPixels)
    return try {
        WallpaperManager.getInstance(context).setBitmap(bmp)
        true
    } catch (e: Throwable) {
        false
    } finally {
        bmp.recycle()
    }
}
