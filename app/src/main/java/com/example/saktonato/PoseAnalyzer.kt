package com.example.saktonato

import android.graphics.PointF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.max

/** Landmark positions in *upright image* coordinates (after rotation). */
data class PoseFrame(
    val points: Map<Int, PointF>,
    val w: Int,
    val h: Int
)

class PoseAnalyzer(
    private val onResult: (PoseFrame?) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private val wanted = listOf(
        PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) { imageProxy.close(); return }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val upright = rotation % 180 == 0
        val w = if (upright) imageProxy.width else imageProxy.height
        val h = if (upright) imageProxy.height else imageProxy.width

        detector.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { pose ->
                val pts = HashMap<Int, PointF>()
                for (t in wanted) {
                    val lm = pose.getPoseLandmark(t) ?: continue
                    if (lm.inFrameLikelihood >= 0.5f) {
                        pts[t] = PointF(lm.position.x, lm.position.y)
                    }
                }
                // shoulders are required; hips are optional
                val ok = pts.containsKey(PoseLandmark.LEFT_SHOULDER) &&
                        pts.containsKey(PoseLandmark.RIGHT_SHOULDER)
                onResult(if (ok) PoseFrame(pts, w, h) else null)
            }
            .addOnFailureListener { onResult(null) }
            .addOnCompleteListener { imageProxy.close() }  // ALWAYS close, or the stream freezes
    }
}

/** Convert an image-space landmark to screen-space, matching PreviewView FILL_CENTER. */
fun PoseFrame.toView(type: Int, vw: Float, vh: Float, mirror: Boolean = true): Offset? {
    val p = points[type] ?: return null
    val scale = max(vw / w, vh / h)
    val dx = (vw - w * scale) / 2f
    val dy = (vh - h * scale) / 2f
    var x = p.x * scale + dx
    val y = p.y * scale + dy
    if (mirror) x = vw - x          // front camera preview is mirrored
    return Offset(x, y)
}

class Smoother(private val alpha: Float = 0.5f) {   // lower = smoother but laggier
    private val last = HashMap<Int, PointF>()
    fun apply(f: PoseFrame): PoseFrame {
        val out = f.points.mapValues { (k, p) ->
            val o = last[k]
            val s = if (o == null) PointF(p.x, p.y)
            else PointF(o.x + alpha * (p.x - o.x), o.y + alpha * (p.y - o.y))
            last[k] = s
            s
        }
        return f.copy(points = out)
    }
    fun reset() = last.clear()
}