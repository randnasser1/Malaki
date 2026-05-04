package com.example.malaki.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.malaki.auth.AuthManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Context

internal suspend fun generateUniquePin(): String {
    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    while (true) {
        val pin = String.format("%06d", (100000..999999).random())
        val existing = firestore.collection("users")
            .whereEqualTo("pinCode", pin)
            .limit(1)
            .get()
            .await()
        if (existing.isEmpty) return pin
    }
}

@Composable
fun AddChildScreen(
    authManager: AuthManager,
    onChildAdded: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var childName by remember { mutableStateOf("") }
    var childAge by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var generatedPin by remember { mutableStateOf<String?>(null) }

    fun validateForm(): Boolean {
        when {
            childName.isBlank() -> {
                errorMessage = "Please enter your child's name"
                return false
            }
            childAge.isBlank() -> {
                errorMessage = "Please enter your child's age"
                return false
            }
            childAge.toIntOrNull() == null || childAge.toInt() !in 4..17 -> {
                errorMessage = "Child age must be between 4 and 17"
                return false
            }
        }
        return true
    }

    generatedPin?.let { pin ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Child Account Created!") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Share this PIN with ${childName} so they can log in:")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = pin,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp,
                        color = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You can view this PIN anytime from your dashboard.",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(onClick = { generatedPin = null; onChildAdded() }) {
                    Text("Done")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) {
                Text("← Back", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Add Child",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Add a child to monitor", fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = childName,
            onValueChange = { childName = it },
            label = { Text("Child's Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = childAge,
            onValueChange = { childAge = it },
            label = { Text("Child's Age") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        errorMessage?.let {
            Text(
                text = it,
                color = Color(0xFFEF4444),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                scope.launch {
                    if (validateForm()) {
                        isLoading = true
                        errorMessage = null

                        val parentId = authManager.currentUser?.uid
                        val parentEmail = authManager.currentUser?.email

                        if (parentId == null || parentEmail == null) {
                            errorMessage = "Parent session expired. Please log in again."
                            isLoading = false
                            return@launch
                        }

                        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                        val parentPassword = prefs.getString("parent_password", null)

                        if (parentPassword == null) {
                            errorMessage = "Please log in again to add a child"
                            isLoading = false
                            return@launch
                        }

                        val pin = generateUniquePin()

                        val result = authManager.createChildAccount(
                            parentId = parentId,
                            parentEmail = parentEmail,
                            parentPassword = parentPassword,
                            childName = childName,
                            childAge = childAge.toInt(),
                            childPin = pin
                        )

                        isLoading = false

                        when (result) {
                            is com.example.malaki.auth.ChildCreationResult.Success -> {
                                generatedPin = pin
                            }
                            is com.example.malaki.auth.ChildCreationResult.Error -> {
                                errorMessage = result.message
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("Add Child", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "A unique 6-digit PIN will be generated for your child to log in.",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}
