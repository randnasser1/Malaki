package com.example.malaki.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.malaki.ui.components.angel.Angel
import com.example.malaki.ui.components.angel.AngelVariant

@Composable
fun EmptyState(
    message: String,
    action: EmptyStateAction? = null,
    modifier: Modifier = Modifier
) {
    // Fixed: Use .value instead of by delegate
    val textAlpha = animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 300, delayMillis = 300),
        label = "textAlpha"
    ).value

    val buttonAlpha = animateFloatAsState(
        targetValue = if (action != null) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 500),
        label = "buttonAlpha"
    ).value

    val buttonY = animateDpAsState(
        targetValue = 0.dp,
        animationSpec = tween(durationMillis = 300, delayMillis = 500),
        label = "buttonY"
    ).value

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 400.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Angel(
            variant = AngelVariant.SMALL,
            moodColor = Color(0xFFD4D4D8)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = message,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (action != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = action.onClick,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6),
                    contentColor = Color.White
                )
            ) {
                Text(action.label)
            }
        }
    }
}

data class EmptyStateAction(
    val label: String,
    val onClick: () -> Unit
)