package com.example.saktonato

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlin.math.max

/** Shared state between the cutout screen and the camera screen (fine for a prototype). */
object GarmentStore {
    var bitmap: Bitmap? = null
    var source: MeasureSource = MeasureSource.NONE
    var sizeTag: String = "M"
    var pitToPitCm: Float = 50f       // only meaningful when source == SELLER
    var widthFraction: Float = 0.75f  // pit-to-pit width / cutout width
}

/** Load an image from a Uri, downscaled so the longest side is at most maxSide. */
fun loadBitmap(context: Context, uri: Uri, maxSide: Int = 1280): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

/** Crop away transparent borders so the garment fills the bitmap. */
fun Bitmap.cropToContent(alphaThreshold: Int = 16): Bitmap {
    val w = width
    val h = height
    val px = IntArray(w * h)
    getPixels(px, 0, w, 0, 0, w, h)
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (y in 0 until h) {
        for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > alphaThreshold) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < 0) return this
    return Bitmap.createBitmap(this, minX, minY, maxX - minX + 1, maxY - minY + 1)
}

/** Run ML Kit subject segmentation and return the cropped, transparent-background cutout. */
fun cutOutGarment(
    source: Bitmap,
    onDone: (Bitmap?) -> Unit,
    onError: (String) -> Unit
) {
    val segmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
    )
    segmenter.process(InputImage.fromBitmap(source, 0))
        .addOnSuccessListener { result ->
            onDone(result.foregroundBitmap?.cropToContent())
        }
        .addOnFailureListener { onError(it.message ?: "Segmentation failed") }
        .addOnCompleteListener { segmenter.close() }
}