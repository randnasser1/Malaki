package com.example.malaki.auth

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthManager(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_TYPE = "user_type"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_CHILD_ID = "linked_child_id"
        private const val KEY_PARENT_ID = "linked_parent_id"
        private const val KEY_PARENT_EMAIL = "parent_email"
        private const val KEY_PARENT_PASSWORD = "parent_password"
        private const val KEY_CHILD_NAME = "child_name"
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserType: UserType
        get() {
            val type = prefs.getString(KEY_USER_TYPE, "NONE")
            return when (type) {
                "PARENT" -> UserType.PARENT
                "CHILD" -> UserType.CHILD
                else -> UserType.NONE
            }
        }

    val isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    val linkedChildId: String?
        get() = prefs.getString(KEY_CHILD_ID, null)

    val linkedParentId: String?
        get() = prefs.getString(KEY_PARENT_ID, null)

    fun setChildName(name: String) {
        prefs.edit().putString(KEY_CHILD_NAME, name).apply()
    }

    fun getChildName(): String {
        return prefs.getString(KEY_CHILD_NAME, "Child") ?: "Child"
    }

    // Parent Login - Standard email/password
    suspend fun parentLogin(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                val userDoc = firestore.collection("users").document(user.uid).get().await()
                val userType = userDoc.getString("userType")

                if (userType == "PARENT") {
                    saveParentSession(user.uid, email, password)
                    AuthResult.Success(user.uid)
                } else {
                    auth.signOut()
                    AuthResult.Error("This account is not registered as a parent")
                }
            } else {
                AuthResult.Error("Login failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Login failed")
        }
    }

    // Child Login - Find by PIN, then sign in with email/password
    suspend fun childLogin(pin: String): AuthResult {
        return try {
            val query = firestore.collection("users")
                .whereEqualTo("pinCode", pin)
                .whereEqualTo("userType", "CHILD")
                .get()
                .await()

            if (query.isEmpty) {
                return AuthResult.Error("Invalid PIN code")
            }

            val childDoc = query.documents[0]
            val childId = childDoc.id
            val childEmail = childDoc.getString("childEmail") ?: return AuthResult.Error("Account corrupted")
            val childPassword = childDoc.getString("childPassword") ?: return AuthResult.Error("Account corrupted")
            val childName = childDoc.getString("name") ?: "Child"

            val result = auth.signInWithEmailAndPassword(childEmail, childPassword).await()

            if (result.user != null) {
                saveChildSession(childId, childDoc.getString("parentId"), childName)
                AuthResult.Success(childId)
            } else {
                AuthResult.Error("Login failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Login failed")
        }
    }

    // Create Child Account (called by parent)
    suspend fun createChildAccount(
        parentId: String,
        parentEmail: String,
        parentPassword: String,
        childName: String,
        childAge: Int,
        childPin: String
    ): ChildCreationResult {
        return try {
            val childEmail = "child_${System.currentTimeMillis()}@malaki.child"
            val childPassword = childPin

            val childAuth = auth.createUserWithEmailAndPassword(childEmail, childPassword).await()
            val childId = childAuth.user?.uid ?: return ChildCreationResult.Error("Failed to create child account")

            val childData = hashMapOf(
                "userId" to childId,
                "userType" to "CHILD",
                "name" to childName,
                "age" to childAge,
                "pinCode" to childPin,
                "childEmail" to childEmail,
                "childPassword" to childPassword,
                "parentId" to parentId,
                "createdAt" to System.currentTimeMillis()
            )

            firestore.collection("users").document(childId).set(childData).await()
            firestore.collection("users").document(parentId).update("childId", childId).await()

            // Generate connection code (optional, kept for compatibility)
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
            auth.signInWithEmailAndPassword(parentEmail, parentPassword).await()

            ChildCreationResult.Success(connectionCode)
        } catch (e: Exception) {
            ChildCreationResult.Error(e.message ?: "Failed to create child account")
        }
    }

    private fun saveParentSession(userId: String, email: String, password: String) {
        prefs.edit()
            .putString(KEY_USER_TYPE, UserType.PARENT.name)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_PARENT_EMAIL, email)
            .putString(KEY_PARENT_PASSWORD, password)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    // FIXED: Added childName parameter
    private fun saveChildSession(childId: String, parentId: String?, childName: String) {
        prefs.edit()
            .putString(KEY_USER_TYPE, UserType.CHILD.name)
            .putString(KEY_USER_ID, childId)
            .putString(KEY_PARENT_ID, parentId)
            .putString(KEY_CHILD_NAME, childName)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    fun setLinkedChild(childId: String) {
        prefs.edit().putString(KEY_CHILD_ID, childId).apply()
    }

    fun setLinkedParent(parentId: String) {
        prefs.edit().putString(KEY_PARENT_ID, parentId).apply()
    }

    fun logout() {
        auth.signOut()
        prefs.edit().clear().apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        auth.signOut()
    }
}

sealed class AuthResult {
    data class Success(val userId: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

sealed class ChildCreationResult {
    data class Success(val connectionCode: String) : ChildCreationResult()
    data class Error(val message: String) : ChildCreationResult()
}