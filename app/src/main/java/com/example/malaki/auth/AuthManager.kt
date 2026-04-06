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

    suspend fun parentLogin(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                // Verify user is a parent in Firestore
                val userDoc = firestore.collection("users").document(user.uid).get().await()
                val userType = userDoc.getString("userType")

                if (userType == "PARENT") {
                    saveSession(UserType.PARENT, user.uid)
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

    suspend fun childLogin(pin: String): AuthResult {
        return try {
            // Find child by PIN code
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

            // Sign in anonymously (children don't need email)
            val result = auth.signInAnonymously().await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                saveSession(UserType.CHILD, childId)
                AuthResult.Success(childId)
            } else {
                AuthResult.Error("Login failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Login failed")
        }
    }

    fun logout() {
        auth.signOut()
        prefs.edit().clear().apply()
    }

    private fun saveSession(userType: UserType, userId: String) {
        prefs.edit()
            .putString(KEY_USER_TYPE, userType.name)
            .putString(KEY_USER_ID, userId)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    fun setLinkedChild(childId: String) {
        prefs.edit().putString(KEY_CHILD_ID, childId).apply()
    }

    fun setLinkedParent(parentId: String) {
        prefs.edit().putString(KEY_PARENT_ID, parentId).apply()
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