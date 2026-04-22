package com.example.malaki.ui.auth

import android.widget.Toast
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.malaki.auth.AuthManager
import kotlinx.coroutines.launch
import android.content.Context

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
    var childPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                errorMessage = "Please create a PIN for your child"
                return false
            }
            childPin.length != 6 -> {
                errorMessage = "PIN must be exactly 6 digits"
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

        Text(
            text = "Add Child",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add a child to monitor",
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

        // Child PIN (6 digits exactly)
        OutlinedTextField(
            value = childPin,
            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) childPin = it },
            label = { Text("Child's PIN (6 digits)") },
            placeholder = { Text("e.g., 123456") },
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
            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) confirmPin = it },
            label = { Text("Confirm PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
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

        // Add Child Button
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

                        val result = authManager.createChildAccount(
                            parentId = parentId,
                            parentEmail = parentEmail,
                            parentPassword = parentPassword,
                            childName = childName,
                            childAge = childAge.toInt(),
                            childPin = childPin
                        )

                        isLoading = false

                        when (result) {
                            is com.example.malaki.auth.ChildCreationResult.Success -> {
                                Toast.makeText(context, "Child added successfully!", Toast.LENGTH_LONG).show()
                                onChildAdded()
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
            text = "Your child will use this 6-digit PIN to log in",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}