package com.example.saktonato

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    if (selected) Button(onClick = onClick, modifier = modifier, contentPadding = contentPadding) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = contentPadding) { Text(label) }
}

@Composable
fun CutoutScreen(onNext: () -> Unit, onEditProfile: () -> Unit) {
    val context = LocalContext.current
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var cutout by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("Pick a screenshot of a garment.") }
    var busy by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(MeasureSource.NONE) }
    var tag by remember { mutableStateOf("M") }
    var pitText by remember { mutableStateOf("50") }
    var fracText by remember { mutableStateOf("0.75") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            original = loadBitmap(context, uri)
            cutout = null
            status = if (original != null) "Image loaded. Tap Cut out garment." else "Could not load that image."
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedButton(onClick = onEditProfile) {
            Text("Edit my measurements (${ProfileStore.current.usualSize}, ${ProfileStore.current.heightCm.toInt()} cm)")
        }
        Text("Sakto Na 'To: Garment Scan")
        Text(status)

        Button(onClick = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text("1. Pick screenshot") }

        original?.let { orig ->
            Image(
                bitmap = orig.asImageBitmap(),
                contentDescription = "Original",
                modifier = Modifier.height(220.dp),
                contentScale = ContentScale.Fit
            )
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    status = "Cutting out... (first time needs internet)"
                    cutOutGarment(
                        orig,
                        onDone = { out ->
                            busy = false
                            cutout = out
                            status = if (out != null) "Cutout ready." else "No garment found. Try another image."
                        },
                        onError = { msg -> busy = false; status = "Error: $msg" }
                    )
                }
            ) { Text("2. Cut out garment") }
        }

        cutout?.let { cut ->
            Box(Modifier.background(Color.Gray)) {
                Image(
                    bitmap = cut.asImageBitmap(),
                    contentDescription = "Cutout",
                    modifier = Modifier.height(260.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Text("What do you know about this garment?")
            Choice("Nothing, screenshot only", source == MeasureSource.NONE,
                { source = MeasureSource.NONE }, Modifier.fillMaxWidth())
            Choice("It has a size tag", source == MeasureSource.SIZE_TAG,
                { source = MeasureSource.SIZE_TAG }, Modifier.fillMaxWidth())
            Choice("The seller gave measurements", source == MeasureSource.SELLER,
                { source = MeasureSource.SELLER }, Modifier.fillMaxWidth())

            if (source == MeasureSource.SIZE_TAG) {
                Text("Size on the tag")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("XS", "S", "M", "L", "XL").forEach { s ->
                        Choice(s, tag == s, { tag = s }, Modifier.weight(1f), PaddingValues(0.dp))
                    }
                }
            }
            if (source == MeasureSource.SELLER) {
                OutlinedTextField(
                    value = pitText,
                    onValueChange = { pitText = it },
                    label = { Text("Pit-to-pit width (cm), laid flat") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = fracText,
                onValueChange = { fracText = it },
                label = { Text("Body width fraction (0.6 to 0.9)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Button(onClick = {
                if (source == MeasureSource.SELLER) {
                    val v = pitText.toFloatOrNull()
                    if (v == null || v !in 30f..80f) {
                        status = "Enter a pit-to-pit width between 30 and 80 cm."
                        return@Button
                    }
                    GarmentStore.pitToPitCm = v
                }
                GarmentStore.bitmap = cut
                GarmentStore.source = source
                GarmentStore.sizeTag = tag
                GarmentStore.widthFraction = (fracText.toFloatOrNull() ?: 0.75f).coerceIn(0.5f, 0.95f)
                onNext()
            }) { Text("3. Check fit") }
        }
    }
}