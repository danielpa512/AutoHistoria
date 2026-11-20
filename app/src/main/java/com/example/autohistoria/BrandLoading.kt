package com.example.autohistoria

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Only composed while an operation is pending; no idle animation. */
@Composable
fun BrandLoading(label: String = "Cargando…") {
    val transition = rememberInfiniteTransition(label = "Carga AutoHistoria")
    val angle = transition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "Giro")
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.autohistoria_logo), contentDescription = null,
            modifier = Modifier.size(44.dp).rotate(angle.value))
        Text(label)
    }
}
