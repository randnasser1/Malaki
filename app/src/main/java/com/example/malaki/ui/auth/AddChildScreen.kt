package com.example.malaki.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

@Composable
fun AddChildScreen(
    parentId: String,
    onChildAdded: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var childName by remember { mutableStateOf("") }
    var childAge by remember { mutableStateOf("") }
    var childPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

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
            childPin.isBlank() -> {
                errorMessage = "Please create a PIN for your child (4-6 digits)"
                return false
            }
            childPin.length < 4 -> {
                errorMessage = "PIN must be at least 4 digits"
                return false
            }
            childPin != confirmPin -> {
                errorMessage = "PINs do not match"
                return false
            }
        }
        return true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) {
                Text("← Back", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = "Add Child",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add another child to monitor",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Child Name
        OutlinedTextField(
            value = childName,
            onValueChange = { childName = it },
            label = { Text("Child's Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Child Age
        OutlinedTextField(
            value = childAge,
            onValueChange = { childAge = it },
            label = { Text("Child's Age") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Child PIN
        OutlinedTextField(
            value = childPin,
            onValueChange = { childPin = it.take(6) },
            label = { Text("Child's PIN (4-6 digits)") },
            placeholder = { Text("e.g., 1234") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Confirm PIN
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { confirmPin = it.take(6) },
            label = { Text("Confirm PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        errorMessage?.let {
            Text(
                text = it,
                color = Color(0xFFEF4444),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Add Child Button
        Button(
            onClick = {
                scope.launch {
                    if (validateForm()) {
                        isLoading = true
                        errorMessage = null

                        try {
                            // Create child account (anonymous)
                            val childAuth = auth.signInAnonymously().await()
                            val childId = childAuth.user?.uid

                            if (childId != null) {
                                // Save child data
                                val childData = hashMapOf(
                                    "userId" to childId,
                                    "name" to childName,
                                    "age" to childAge.toInt(),
                                    "pinCode" to childPin,
                                    "userType" to "CHILD",
                                    "parentId" to parentId,
                                    "createdAt" to System.currentTimeMillis()
                                )

                                firestore.collection("users")
                                    .document(childId)
                                    .set(childData)
                                    .await()

                                // Update parent document with childId
                                firestore.collection("users")
                                    .document(parentId)
                                    .update("childId", childId)
                                    .await()

                                // Generate connection code
                                val connectionCode = String.format("%06d", (100000..999999).random())
                                val codeData = hashMapOf(
                                    "code" to connectionCode,
                                    "parentId" to parentId,
                                    "childId" to childId,
                                    "createdAt" to System.currentTimeMillis(),
                                    "expiresAt" to System.currentTimeMillis() + (24 * 60 * 60 * 1000),
                                    "used" to false
                                )

                                firestore.collection("connection_codes")
                                    .document(connectionCode)
                                    .set(codeData)
                                    .await()

                                // Sign back in as parent
                                val currentUser = auth.currentUser
                                if (currentUser != null && currentUser.isAnonymous) {
                                    // Re-authenticate as parent if needed
                                    // For now, just show success
                                }

                                Toast.makeText(
                                    context,
                                    "Child added! Share code: $connectionCode",
                                    Toast.LENGTH_LONG
                                ).show()

                                onChildAdded()
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to add child"
                        } finally {
                            isLoading = false
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

        // Info note
        Text(
            text = "Your child will get a connection code to log in",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

// Add missing imports
