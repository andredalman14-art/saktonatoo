package com.example.saktonato

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private fun Float.show(): String = if (this > 0f) toString().removeSuffix(".0") else ""

@Composable
fun ProfileScreen(onSaved: () -> Unit, onCancel: (() -> Unit)? = null) {
    val saved = ProfileStore.current
    var height by remember { mutableStateOf(saved.heightCm.show()) }
    var weight by remember { mutableStateOf(saved.weightKg.show()) }
    var chest by remember { mutableStateOf(saved.chestCm.show()) }
    var waist by remember { mutableStateOf(saved.waistCm.show()) }
    var shoulder by remember { mutableStateOf(saved.shoulderCm.show()) }
    var size by remember { mutableStateOf(saved.usualSize) }
    var error by remember { mutableStateOf<String?>(null) }

    val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("My measurements")
        Text("Saved on this phone only. Measure over thin clothing with a soft tape.")

        OutlinedTextField(
            value = height, onValueChange = { height = it },
            label = { Text("Height (cm), required") },
            keyboardOptions = numberKeys, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = weight, onValueChange = { weight = it },
            label = { Text("Weight (kg)") },
            keyboardOptions = numberKeys, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = chest, onValueChange = { chest = it },
            label = { Text("Chest / bust (cm), around the fullest part") },
            keyboardOptions = numberKeys, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = waist, onValueChange = { waist = it },
            label = { Text("Waist (cm), around the natural waist") },
            keyboardOptions = numberKeys, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = shoulder, onValueChange = { shoulder = it },
            label = { Text("Shoulder width (cm), optional but more accurate") },
            keyboardOptions = numberKeys, singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Text("Usual size")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("XS", "S", "M", "L", "XL").forEach { s ->
                if (s == size) Button(onClick = { size = s }) { Text(s) }
                else OutlinedButton(onClick = { size = s }) { Text(s) }
            }
        }

        error?.let { Text(it, color = Color.Red) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                val h = height.toFloatOrNull()
                if (h == null || h !in 100f..220f) {
                    error = "Enter a height between 100 and 220 cm."
                    return@Button
                }
                val sh = shoulder.toFloatOrNull() ?: 0f
                if (sh != 0f && sh !in 25f..65f) {
                    error = "Shoulder width should be between 25 and 65 cm, or leave it blank."
                    return@Button
                }
                ProfileStore.save(
                    SavedProfile(
                        heightCm = h,
                        weightKg = weight.toFloatOrNull() ?: 0f,
                        chestCm = chest.toFloatOrNull() ?: 0f,
                        waistCm = waist.toFloatOrNull() ?: 0f,
                        shoulderCm = sh,
                        usualSize = size
                    )
                )
                error = null
                onSaved()
            }) { Text("Save") }

            if (onCancel != null) OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}