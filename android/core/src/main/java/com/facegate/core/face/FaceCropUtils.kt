package com.facegate.core.face

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Crop wajah berbentuk KOTAK (square) yang berpusat di bounding box wajah.
 *
 * InsightFace (w600k_mbf) menerima input 112×112. Kalau crop-nya persegi
 * panjang, `Bitmap.createScaledBitmap(..., 112, 112)` akan men-distorsi wajah
 * (stretch/squish) dan embedding jadi rusak → cosine similarity turun drastis
 * → selalu "Wajah tidak dikenal". Crop kotak menjamin resize 112×112 hanya
 * skala seragam (tanpa distorsi).
 *
 * Sisi kotak = max(lebar, tinggi) bbox + margin kiri/kanan (default 30% tiap sisi).
 * Jika kotak keluar dari bitmap, sisi digeser ke dalam bounds (bukan di-clip
 * per sisi) supaya tetap kotak.
 */
object FaceCropUtils {

    fun cropSquare(bitmap: Bitmap, boundingBox: Rect, marginRatio: Float = 0.3f): Bitmap {
        val base = max(boundingBox.width(), boundingBox.height())
        val side = min(
            (base * (1f + 2f * marginRatio)).toInt().coerceAtLeast(1),
            min(bitmap.width, bitmap.height)
        )
        if (side <= 0) {
            return Bitmap.createBitmap(bitmap)
        }

        val cx = boundingBox.exactCenterX()
        val cy = boundingBox.exactCenterY()

        var x = (cx - side / 2f).toInt()
        var y = (cy - side / 2f).toInt()

        // Geser ke dalam bounds supaya kotak penuh muat di bitmap.
        x = x.coerceIn(0, maxOf(0, bitmap.width - side))
        y = y.coerceIn(0, maxOf(0, bitmap.height - side))

        return Bitmap.createBitmap(bitmap, x, y, side, side)
    }
}
