package com.example.saktonato

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

enum class MeasureSource { NONE, SIZE_TAG, SELLER }

enum class Tier(val label: String) {
    STYLE_PREVIEW("Style Preview"),
    ESTIMATED("Estimated Fit"),
    VERIFIED("Verified Fit")
}

enum class FitLevel { NO_CLAIM, NEED_PROFILE, TOO_SMALL, SNUG, REGULAR, RELAXED, OVERSIZED }

data class FitVerdict(
    val tier: Tier,
    val level: FitLevel,
    val headline: String,
    val details: List<String>
)

fun tierColor(t: Tier): Color = when (t) {
    Tier.STYLE_PREVIEW -> Color(0xFF757575)
    Tier.ESTIMATED -> Color(0xFFB26A00)
    Tier.VERIFIED -> Color(0xFF2E7D32)
}

/** PLACEHOLDER generic tee chart (flat pit-to-pit, cm). Replace with real brand charts later. */
object SizeChart {
    val pitToPitBySize = mapOf("XS" to 46f, "S" to 49f, "M" to 52f, "L" to 55f, "XL" to 58f)
}

object FitEngine {
    /** Size tags are unreliable, so allow +-3 cm pit-to-pit (= +-6 cm of chest circumference). */
    private const val TAG_TOLERANCE_CM = 6f

    private fun f(x: Float) = String.format(java.util.Locale.US, "%.1f", x).removeSuffix(".0")

    fun garmentPitCm(): Float? = when (GarmentStore.source) {
        MeasureSource.SELLER -> GarmentStore.pitToPitCm
        MeasureSource.SIZE_TAG -> SizeChart.pitToPitBySize[GarmentStore.sizeTag]
        MeasureSource.NONE -> null
    }

    /** Width used to DRAW the garment. With no measurement, a neutral width so we never imply a fit. */
    fun displayPitCm(p: SavedProfile): Float =
        garmentPitCm() ?: (if (p.chestCm > 0f) p.chestCm / 2f + 5f else 50f)

    /** ease = garment chest circumference minus body chest circumference (cm). Rules of thumb. */
    fun classify(ease: Float): FitLevel = when {
        ease < -2f -> FitLevel.TOO_SMALL
        ease < 4f -> FitLevel.SNUG
        ease < 12f -> FitLevel.REGULAR
        ease < 20f -> FitLevel.RELAXED
        else -> FitLevel.OVERSIZED
    }

    private fun levelText(l: FitLevel) = when (l) {
        FitLevel.TOO_SMALL -> "too small"
        FitLevel.SNUG -> "snug"
        FitLevel.REGULAR -> "a regular fit"
        FitLevel.RELAXED -> "relaxed"
        FitLevel.OVERSIZED -> "oversized"
        else -> ""
    }

    private fun favoriteLine(pit: Float, fav: Float): String? {
        if (fav <= 0f) return null
        val d = pit - fav
        return when {
            abs(d) < 1f -> "About the same width as your favorite shirt."
            d > 0 -> "${f(d)} cm wider than your favorite shirt (pit-to-pit)."
            else -> "${f(-d)} cm narrower than your favorite shirt (pit-to-pit)."
        }
    }

    fun evaluate(p: SavedProfile, favoritePit: Float): FitVerdict {
        val pit = garmentPitCm()
        if (pit == null) {
            return FitVerdict(
                Tier.STYLE_PREVIEW, FitLevel.NO_CLAIM,
                "Style Preview: this shows the look, not the fit.",
                listOf(
                    "A screenshot has no scale, so we can't say if it fits.",
                    "Ask the seller for the pit-to-pit width, or pick a size tag if the item has one."
                )
            )
        }

        val tier = if (GarmentStore.source == MeasureSource.SELLER) Tier.VERIFIED else Tier.ESTIMATED
        val details = mutableListOf<String>()
        val garmentChest = pit * 2f
        details += "Garment chest (pit-to-pit x 2): ${f(garmentChest)} cm"

        if (p.chestCm <= 0f) {
            favoriteLine(pit, favoritePit)?.let { details += it }
            details += "Add your chest measurement in 'Edit my measurements' to get a verdict."
            return FitVerdict(tier, FitLevel.NEED_PROFILE, "We need your chest measurement.", details)
        }

        val ease = garmentChest - p.chestCm
        details += "Your chest: ${f(p.chestCm)} cm"
        details += "Room in the garment: ${if (ease >= 0) "+" else ""}${f(ease)} cm"

        val headline: String
        val level: FitLevel
        if (tier == Tier.VERIFIED) {
            level = classify(ease)
            headline = "Expect it to be ${levelText(level)}."
            details += "Based on the seller's measurement."
        } else {
            val lo = classify(ease - TAG_TOLERANCE_CM)
            val hi = classify(ease + TAG_TOLERANCE_CM)
            level = classify(ease)
            headline = if (lo == hi) "Likely ${levelText(lo)}."
            else "Likely between ${levelText(lo)} and ${levelText(hi)}."
            details += "Based on size tag ${GarmentStore.sizeTag} and a generic size chart. Real garments vary, so ask the seller to be sure."
        }

        favoriteLine(pit, favoritePit)?.let { details += it }
        details += "This checks chest width only. Length and sleeves are not checked yet."
        return FitVerdict(tier, level, headline, details)
    }
}