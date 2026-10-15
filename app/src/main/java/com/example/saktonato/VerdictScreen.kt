package com.example.saktonato

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val SELLER_MESSAGE =
    "Hi po! Interested po ako sa item na ito. Pwede po ba pakisabi ang measurements?\n\n" +
            "Pakilatag po ang item sa flat na surface at sukatin (ruler o bond paper po ay pwede):\n" +
            "1) Pit-to-pit: pahalang, mula kilikili hanggang kilikili\n" +
            "2) Haba: mula balikat pababa hanggang laylayan\n\n" +
            "Gusto ko lang po sigurado na sakto sa akin bago bumili. Salamat po!"

@Composable
fun VerdictScreen(onTryOn: () -> Unit, onBack: () -> Unit, onEditProfile: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    var refresh by remember { mutableIntStateOf(0) }
    val verdict = remember(refresh) {
        FitEngine.evaluate(ProfileStore.current, ProfileStore.favoritePitCm)
    }
    var sellerText by remember { mutableStateOf("") }
    var favText by remember {
        mutableStateOf(ProfileStore.favoritePitCm.let { if (it > 0f) it.toString().removeSuffix(".0") else "" })
    }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            verdict.tier.label,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(tierColor(verdict.tier))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        Text(verdict.headline, style = MaterialTheme.typography.titleLarge)
        verdict.details.forEach { Text(it) }

        if (verdict.level == FitLevel.NEED_PROFILE) {
            OutlinedButton(onClick = onEditProfile) { Text("Edit my measurements") }
        }

        // Ask seller (only when the fit is not already verified)
        if (verdict.tier != Tier.VERIFIED) {
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, SELLER_MESSAGE)
                }
                context.startActivity(Intent.createChooser(send, "Send to seller"))
            }) { Text("Ask seller for measurements") }
        }

        // Seller replied -> upgrade to Verified Fit
        OutlinedTextField(
            value = sellerText,
            onValueChange = { sellerText = it },
            label = { Text("Seller replied? Pit-to-pit (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        Button(onClick = {
            val v = sellerText.toFloatOrNull()
            if (v == null || v !in 30f..80f) {
                message = "Enter a pit-to-pit width between 30 and 80 cm."
            } else {
                GarmentStore.source = MeasureSource.SELLER
                GarmentStore.pitToPitCm = v
                message = "Updated. This is now a Verified Fit."
                refresh++
            }
        }) { Text("Apply seller's measurement") }

        // Favorite shirt comparison
        OutlinedTextField(
            value = favText,
            onValueChange = { favText = it },
            label = { Text("My favorite shirt, pit-to-pit (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        OutlinedButton(onClick = {
            val v = favText.toFloatOrNull()
            if (v == null || v !in 30f..80f) {
                message = "Enter a pit-to-pit width between 30 and 80 cm."
            } else {
                ProfileStore.saveFavorite(v)
                message = "Favorite shirt saved."
                refresh++
            }
        }) { Text("Save favorite shirt") }

        message?.let { Text(it) }

        Button(onClick = onTryOn, modifier = Modifier.fillMaxWidth()) { Text("Try on live") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}