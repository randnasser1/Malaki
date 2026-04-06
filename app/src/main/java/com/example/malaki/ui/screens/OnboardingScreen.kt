package com.example.malaki.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.malaki.ui.components.angel.Angel
import com.example.malaki.ui.components.angel.AngelVariant

data class OnboardingStep(
    val title: String,
    val description: String
)

val onboardingSteps = listOf(
    OnboardingStep(
        title = "Welcome to your safe space",
        description = "This is a place where you can track your feelings and express yourself freely."
    ),
    OnboardingStep(
        title = "Track your moods",
        description = "Every day, you can share how you're feeling. No one will judge you here."
    ),
    OnboardingStep(
        title = "Write in your journal",
        description = "Express your thoughts and feelings in your private journal whenever you want."
    ),
    OnboardingStep(
        title = "You're protected",
        description = "Your guardian angel is always watching over you, keeping you safe."
    )
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }
    val step = onboardingSteps[currentStep]
    val isLastStep = currentStep == onboardingSteps.size - 1

    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFF8E7),
            Color(0xFFFFFBE6),
            Color(0xFFFFF0E0)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Angel
            val infiniteTransition = rememberInfiniteTransition()
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                )
            )

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(600)) +
                            scaleIn(initialScale = 0.8f, animationSpec = tween(600)) togetherWith
                            fadeOut(animationSpec = tween(300)) +
                            scaleOut(targetScale = 1.2f, animationSpec = tween(300))
                }
            ) { stepIndex ->
                Angel(
                    variant = AngelVariant.SPLASH,
                    modifier = Modifier.scale(scale)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Content with animation
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) +
                            slideInVertically(initialOffsetY = { 20 }, animationSpec = tween(400)) togetherWith
                            fadeOut(animationSpec = tween(300)) +
                            slideOutVertically(targetOffsetY = { -20 }, animationSpec = tween(300))
                }
            ) { stepIndex ->
                val current = onboardingSteps[stepIndex]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color(0xFF111827),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = current.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF4B5563),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Progress dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                onboardingSteps.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .width(if (index == currentStep) 24.dp else 8.dp)
                            .height(8.dp)
                            .background(
                                color = if (index == currentStep)
                                    Color(0xFFF3D97F)
                                else
                                    Color(0xFFD4D4D8),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onComplete,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF4B5563)
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Skip")
                }

                Button(
                    onClick = {
                        if (isLastStep) {
                            onComplete()
                        } else {
                            currentStep++
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(if (isLastStep) "Get Started" else "Next")
                }
            }
        }
    }
}