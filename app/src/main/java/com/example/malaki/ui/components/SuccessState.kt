package com.example.malaki.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.malaki.ui.components.angel.Angel
import com.example.malaki.ui.components.angel.AngelVariant
import androidx.compose.material3.MaterialTheme
@Composable
fun SuccessState(
    message: String,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Backdrop overlay
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .clickable(enabled = onClose != null) { onClose?.invoke() }
    ) {
        // Animated card
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(
                animationSpec = tween(300, delayMillis = 100),
                initialScale = 0.9f
            ) + fadeIn(animationSpec = tween(300, delayMillis = 100))
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth()
                    .widthIn(max = 400.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 16.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Success Angel
                    Angel(
                        variant = AngelVariant.SUCCESS,
                        moodColor = Color(0xFFE8F5A8)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Message with fade-in
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300, delayMillis = 300))
                    ) {
                        Text(
                            text = message,
                            color = Color(0xFF1F2937),  // gray-800
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300, delayMillis = 400))
                    ) {
                        Text(
                            text = "You're safe here",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Continue button
                    if (onClose != null) {
                        Spacer(modifier = Modifier.height(24.dp))

                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(300, delayMillis = 500))
                        ) {
                            Button(
                                onClick = onClose,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),  // green-500
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Continue")
                            }
                        }
                    }
                }
            }
        }
    }
}