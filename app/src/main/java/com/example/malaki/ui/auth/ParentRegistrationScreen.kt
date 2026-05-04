package com.example.malaki.ui.auth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID
// Add missing imports
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.tasks.await
private suspend fun registerUser(
    email: String,
    password: String,
    fullName: String,
    childName: String,
    childAge: String,
    onSuccess: (generatedPin: String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val childPin = generateUniquePin()
        val childEmail = "child_${System.currentTimeMillis()}@malaki.child"
        val childPassword = childPin

        // Create parent account
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val parentId = authResult.user?.uid ?: throw Exception("Failed to create parent account")

        // Sign out parent temporarily to create child account
        auth.signOut()

        // Create child account with email/password (required for child login)
        val childAuth = auth.createUserWithEmailAndPassword(childEmail, childPassword).await()
        val childId = childAuth.user?.uid ?: throw Exception("Failed to create child account")

        // Save parent data
        val parentData = hashMapOf(
            "userId" to parentId,
            "fullName" to fullName,
            "email" to email,
            "userType" to "PARENT",
            "childId" to childId,
            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("users").document(parentId).set(parentData).await()

        // Save child data with email/password so child can log in
        val childData = hashMapOf(
            "userId" to childId,
            "name" to childName,
            "age" to childAge.toInt(),
            "pinCode" to childPin,
            "childEmail" to childEmail,
            "childPassword" to childPassword,
            "userType" to "CHILD",
            "parentId" to parentId,
            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("users").document(childId).set(childData).await()

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

        firestore.collection("connection_codes").document(connectionCode).set(codeData).await()

        // Sign back in as parent
        auth.signOut()
        auth.signInWithEmailAndPassword(email, password).await()

        onSuccess(childPin)
    } catch (e: Exception) {
        onError(e.message ?: "Registration failed")
    }
}
@Composable
fun ParentRegistrationScreen(
    onRegistrationSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Parent form fields
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Child form fields
    var childName by remember { mutableStateOf("") }
    var childAge by remember { mutableStateOf("") }

    // UI states
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var generatedPin by remember { mutableStateOf<String?>(null) }
    var showChildForm by remember { mutableStateOf(true) }

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
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
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
            text = "Set up your account and add your child",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ========== PARENT SECTION ==========
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF0F4F8)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Parent Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ========== CHILD SECTION ==========
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF8E7)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Child Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = childName,
                    onValueChange = { childName = it },
                    label = { Text("Child's Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = childAge,
                    onValueChange = { childAge = it },
                    label = { Text("Child's Age") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )


                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A unique PIN will be generated automatically after registration.",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Terms agreement
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = true,
                onCheckedChange = { },
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

        generatedPin?.let { pin ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Account Created!") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Share this PIN with ${childName} so they can log in:")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = pin,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You can view this PIN anytime from your dashboard.",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { generatedPin = null; onRegistrationSuccess() }) {
                        Text("Done")
                    }
                }
            )
        }

        // Register Button
        Button(
            onClick = {
                scope.launch {
                    if (validateForm()) {
                        isLoading = true
                        errorMessage = null
                        registerUser(
                            email = email,
                            password = password,
                            fullName = fullName,
                            childName = childName,
                            childAge = childAge,
                            onSuccess = { pin ->
                                generatedPin = pin
                                isLoading = false
                            },
                            onError = {
                                errorMessage = it
                                isLoading = false
                            }
                        )
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
                Text("Create Account & Add Child", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

