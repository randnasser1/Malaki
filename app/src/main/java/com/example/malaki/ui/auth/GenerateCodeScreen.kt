package com.example.malaki.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GenerateCodeScreen(
    onCodeGenerated: (String) -> Unit = {},
    onBack: () -> Unit,
    authManager: com.example.malaki.auth.AuthManager
) {
    val context = LocalContext.current
    val codeManager = com.example.malaki.auth.ConnectionCodeManager(context)
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableStateOf<Long?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCopied by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Color(0xFF6B7280))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = "Connect Your Child",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F2937),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Generate a one-time code for your child to enter",
            fontSize = 14.sp,
            color = Color(0xFF6B7280),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Code Display
        if (generatedCode != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFEF3C7)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your Connection Code",
                        fontSize = 14.sp,
                        color = Color(0xFF92400E)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = generatedCode!!,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 8.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    expiresAt?.let {
                        val expiryTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it))
                        Text(
                            text = "Expires at $expiryTime",
                            fontSize = 12.sp,
                            color = Color(0xFF92400E)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                // Copy to clipboard
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Connection Code", generatedCode)
                                clipboard.setPrimaryClip(clip)
                                showCopied = true
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD97706)
                            )
                        ) {
                            Text("Copy Code")
                        }

                        OutlinedButton(
                            onClick = {
                                generatedCode = null
                                expiresAt = null
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Generate New")
                        }
                    }

                    AnimatedVisibility(visible = showCopied) {
                        Text(
                            text = "Copied!",
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Give this code to your child. They will enter it in their app to connect to you.",
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        } else {
            // Generate Button
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        val userId = authManager.currentUser?.uid
                        if (userId != null) {
                            val result = codeManager.generateConnectionCode(userId)
                            when (result) {
                                is com.example.malaki.auth.ConnectionCodeResult.Success -> {
                                    generatedCode = result.code
                                    expiresAt = result.expiresAt
                                    onCodeGenerated(result.code)
                                }
                                is com.example.malaki.auth.ConnectionCodeResult.Error -> {
                                    errorMessage = result.message
                                }
                            }
                        } else {
                            errorMessage = "Please log in first"
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Generate Connection Code", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            errorMessage?.let {
                Text(
                    text = it,
                    color = Color(0xFFEF4444),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Info box
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFEFF6FF)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ℹ️ How it works",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E40AF),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Generate a unique 6-digit code\n2. Give this code to your child\n3. Your child enters the code in their app\n4. You'll be connected and can monitor their wellbeing",
                    fontSize = 12.sp,
                    color = Color(0xFF1E40AF),
                    lineHeight = 18.sp
                )
            }
        }
    }
}