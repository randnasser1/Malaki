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
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.tasks.await
@Composable
fun ParentRegistrationScreen(
    onRegistrationSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Form fields
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var childName by remember { mutableStateOf("") }
    var childAge by remember { mutableStateOf("") }

    // UI states
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Firebase instances
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    fun validateForm(): Boolean {
        when {
            fullName.isBlank() -> {
                errorMessage = "Please enter your full name"
                return false
            }
            email.isBlank() -> {
                errorMessage = "Please enter your email"
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                errorMessage = "Please enter a valid email address"
                return false
            }
            password.isBlank() -> {
                errorMessage = "Please enter a password"
                return false
            }
            password.length < 6 -> {
                errorMessage = "Password must be at least 6 characters"
                return false
            }
            password != confirmPassword -> {
                errorMessage = "Passwords do not match"
                return false
            }
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
            text = "Create Parent Account",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Set up your account to monitor your child's wellbeing",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Full Name
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") },
            placeholder = { Text("John Doe") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFD1D5DB)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            placeholder = { Text("parent@example.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFD1D5DB)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            placeholder = { Text("••••••••") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFD1D5DB)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Confirm Password
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            placeholder = { Text("••••••••") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFD1D5DB)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Child Name
        OutlinedTextField(
            value = childName,
            onValueChange = { childName = it },
            label = { Text("Child's Name") },
            placeholder = { Text("Child's name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFD1D5DB)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Child Age
        OutlinedTextField(
            value = childAge,
            onValueChange = { childAge = it },
            label = { Text("Child's Age") },
            placeholder = { Text("e.g., 8") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFD1D5DB)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Terms agreement
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = true,
                onCheckedChange = { /* Handle terms acceptance */ },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                text = "I agree to the Terms of Service and Privacy Policy",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

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

        // Register Button
        Button(
            onClick = {
                scope.launch {
                    if (validateForm()) {
                        isLoading = true
                        errorMessage = null

                        try {
                            // Create user in Firebase Auth
                            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                            val userId = authResult.user?.uid

                            if (userId != null) {
                                // Save parent data to Firestore
                                val parentData = hashMapOf(
                                    "userId" to userId,
                                    "fullName" to fullName,
                                    "email" to email,
                                    "userType" to "PARENT",
                                    "childName" to childName,
                                    "childAge" to childAge.toInt(),
                                    "createdAt" to System.currentTimeMillis()
                                )

                                firestore.collection("users")
                                    .document(userId)
                                    .set(parentData)
                                    .await()

                                // Send email verification
                                auth.currentUser?.sendEmailVerification()

                                Toast.makeText(context, "Account created successfully!", Toast.LENGTH_LONG).show()
                                onRegistrationSuccess()
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Registration failed"
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
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// Add missing imports
