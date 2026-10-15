package com.example.saktonato

import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.concurrent.Executors
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.atan2
import androidx.compose.material3.Button
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background


class FpsMeter {
    private var count = 0
    private var start = System.nanoTime()
    private var last = 0
    fun tick(): Int {
        count++
        val now = System.nanoTime()
        if (now - start >= 1_000_000_000L) { last = count; count = 0; start = now }
        return last
    }
}


@Composable
fun CameraScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var frame by remember { mutableStateOf<PoseFrame?>(null) }
    var fps by remember { mutableIntStateOf(0) }
    val meter = remember { FpsMeter() }
    val smoother = remember { Smoother(0.35f) }   // <-- ADDED
    val fallback = ImageBitmap.imageResource(R.drawable.demo_shirt)
    val shirt = remember { GarmentStore.bitmap?.asImageBitmap() ?: fallback }
    val profile = remember { ProfileStore.current.toBuyerProfile() }
    val verdict = remember { FitEngine.evaluate(ProfileStore.current, ProfileStore.favoritePitCm) }
    val baseCm = remember { FitEngine.displayPitCm(ProfileStore.current) }
    var pitToPit by remember { mutableFloatStateOf(baseCm) }
    val garment = Garment(shirt, pitToPit, GarmentStore.widthFraction)


    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            // same aspect ratio for both use cases, so the overlay math stays correct
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

            val preview = Preview.Builder().setResolutionSelector(selector).build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { a ->
                    a.setAnalyzer(executor, PoseAnalyzer { f ->      // <-- CHANGED
                        frame = if (f == null) { smoother.reset(); null } else smoother.apply(f)
                        fps = meter.tick()
                    })
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            future.get().unbindAll()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Canvas(Modifier.fillMaxSize()) {
            val f = frame ?: return@Canvas
            // (dots loop is optional now)
            drawGarmentScaled(f, garment, profile)
        }

        Text(
            "FPS: $fps",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        )

        // Size comparison buttons: hidden for Style Preview
        if (verdict.tier != Tier.STYLE_PREVIEW) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { pitToPit = baseCm * 0.85f }) { Text("Smaller") }
                Button(onClick = { pitToPit = baseCm }) { Text("Actual") }
                Button(onClick = { pitToPit = baseCm * 1.15f }) { Text("Larger") }
            }
        }
    }   // closes Box
}       // closes CameraScreen()

fun DrawScope.drawGarmentSimple(f: PoseFrame, bmp: ImageBitmap) {
    val ls = f.toView(PoseLandmark.LEFT_SHOULDER, size.width, size.height) ?: return
    val rs = f.toView(PoseLandmark.RIGHT_SHOULDER, size.width, size.height) ?: return

    val mid = Offset((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f)
    val shoulderPx = (rs - ls).getDistance()
    val angle = Math.toDegrees(atan2((rs.y - ls.y).toDouble(), (rs.x - ls.x).toDouble())).toFloat()

    val w = shoulderPx * 1.6f                       // tune: 1.4 to 1.9
    val h = w * bmp.height / bmp.width

    withTransform({
        translate(mid.x, mid.y)
        rotate(angle, Offset.Zero)
    }) {
        drawImage(
            bmp,
            dstOffset = IntOffset((-w / 2f).toInt(), (-h * 0.12f).toInt()), // collar sits slightly above shoulder line
            dstSize = IntSize(w.toInt(), h.toInt())
        )
    }
}

data class Garment(
    val image: ImageBitmap,
    val pitToPitCm: Float,        // measured flat, armpit to armpit
    val bodyWidthFraction: Float  // pit-to-pit width / total PNG width (~0.7 for tees)
)

data class BuyerProfile(val heightCm: Float, val shoulderCm: Float? = null)

fun DrawScope.drawGarmentScaled(f: PoseFrame, g: Garment, p: BuyerProfile) {
    val ls = f.toView(PoseLandmark.LEFT_SHOULDER, size.width, size.height) ?: return
    val rs = f.toView(PoseLandmark.RIGHT_SHOULDER, size.width, size.height) ?: return

    val mid = Offset((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f)
    val shoulderPx = (rs - ls).getDistance()
    val angle = Math.toDegrees(atan2((rs.y - ls.y).toDouble(), (rs.x - ls.x).toDouble())).toFloat()

    val shoulderCm = p.shoulderCm ?: (0.259f * p.heightCm)
    val pxPerCm = shoulderPx / shoulderCm

    val w = g.pitToPitCm * pxPerCm / g.bodyWidthFraction
    val h = w * g.image.height / g.image.width

    withTransform({ translate(mid.x, mid.y); rotate(angle, Offset.Zero) }) {
        drawImage(
            g.image,
            dstOffset = IntOffset((-w / 2f).toInt(), (-h * 0.12f).toInt()),
            dstSize = IntSize(w.toInt(), h.toInt())
        )
    }
}