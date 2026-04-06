package com.example.malaki.auth

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class ConnectionCodeManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val authManager = AuthManager(context)

    companion object {
        private const val CODE_LENGTH = 6
        private const val CODE_EXPIRY_HOURS = 24
    }

    suspend fun generateConnectionCode(parentId: String): ConnectionCodeResult {
        return try {
            val code = generateUniqueCode()
            val expiresAt = System.currentTimeMillis() + (CODE_EXPIRY_HOURS * 60 * 60 * 1000)

            val codeData = mapOf(
                "code" to code,
                "parentId" to parentId,
                "createdAt" to System.currentTimeMillis(),
                "expiresAt" to expiresAt,
                "used" to false,
                "usedBy" to null
            )

            firestore.collection("connection_codes")
                .document(code)
                .set(codeData)
                .await()

            ConnectionCodeResult.Success(code, expiresAt)
        } catch (e: Exception) {
            ConnectionCodeResult.Error(e.message ?: "Failed to generate code")
        }
    }

    suspend fun connectChildToParent(childId: String, code: String): ConnectionResult {
        return try {
            val codeDoc = firestore.collection("connection_codes")
                .document(code)
                .get()
                .await()

            if (!codeDoc.exists()) {
                return ConnectionResult.Error("Invalid connection code")
            }

            val codeData = codeDoc.data ?: return ConnectionResult.Error("Invalid code data")
            val isUsed = codeData["used"] as? Boolean ?: true
            val expiresAt = codeData["expiresAt"] as? Long ?: 0
            val parentId = codeData["parentId"] as? String ?: return ConnectionResult.Error("Invalid code")

            if (isUsed) {
                return ConnectionResult.Error("This code has already been used")
            }

            if (expiresAt < System.currentTimeMillis()) {
                return ConnectionResult.Error("This code has expired")
            }

            firestore.collection("connection_codes")
                .document(code)
                .update(mapOf(
                    "used" to true,
                    "usedBy" to childId,
                    "usedAt" to System.currentTimeMillis()
                ))
                .await()

            firestore.collection("users")
                .document(childId)
                .update("parentId", parentId)
                .await()

            firestore.collection("users")
                .document(parentId)
                .update("childId", childId)
                .await()

            authManager.setLinkedParent(parentId)

            ConnectionResult.Success(parentId)
        } catch (e: Exception) {
            ConnectionResult.Error(e.message ?: "Connection failed")
        }
    }

    private suspend fun generateUniqueCode(): String {
        var code: String
        var isUnique = false

        while (!isUnique) {
            code = Random.nextInt(100000, 999999).toString()
            val existing = firestore.collection("connection_codes")
                .document(code)
                .get()
                .await()

            if (!existing.exists()) {
                return code
            }
        }

        return Random.nextInt(100000, 999999).toString()
    }
}

sealed class ConnectionCodeResult {
    data class Success(val code: String, val expiresAt: Long) : ConnectionCodeResult()
    data class Error(val message: String) : ConnectionCodeResult()
}

sealed class ConnectionResult {
    data class Success(val parentId: String) : ConnectionResult()
    data class Error(val message: String) : ConnectionResult()
}