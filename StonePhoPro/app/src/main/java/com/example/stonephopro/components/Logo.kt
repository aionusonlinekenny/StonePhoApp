// Logo.kt (trong package com.example.stonephopro.components)
package com.example.stonephopro.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.stonephopro.R

@Composable
fun AppLogo(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(200.dp)
            .height(50.dp)
            .background(Color.Transparent) // ✅ Nền trong suốt

    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "App Logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}
