package com.example.malaki.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.example.malaki.ui.components.angel.Angel
import com.example.malaki.ui.components.angel.AngelVariant
import androidx.compose.material3.MaterialTheme
@Composable
fun SplashScreen(
    onComplete: () -> Unit
) {
    // Animate the splash screen then call onComplete after 2.5 seconds
    LaunchedEffect(Unit) {
        delay(2500)
        onComplete()
    }

    // Gradient background
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFF8E7),  // amber-50
            Color(0xFFFFFBE6),  // yellow-50
            Color(0xFFFFF0E0)   // orange-50
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        // Animated Angel - scales up and fades in
        val scale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(800, delayMillis = 200),
            label = "scale"
        )

        val alpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(800, delayMillis = 200),
            label = "alpha"
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Angel(
                variant = AngelVariant.SPLASH,
                modifier = Modifier.scale(scale)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Animated text
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 800)) +
                        slideInVertically(initialOffsetY = { 20 })
            ) {
                Text(
                    text = "Child Guardian App",
                    color = Color(0xFF4B5563),  // gray-600
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}