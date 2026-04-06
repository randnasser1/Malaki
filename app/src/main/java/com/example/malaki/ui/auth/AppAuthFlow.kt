package com.example.malaki.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.malaki.auth.AuthManager
import com.example.malaki.auth.UserType
import com.example.malaki.ui.screens.child.ChildHome
import com.example.malaki.ui.screens.parent.ParentDashboard
import com.example.malaki.ui.theme.MalakiTheme

sealed class AuthScreen {
    data object UserType : AuthScreen()
    data object ParentLogin : AuthScreen()
    data object AddChild : AuthScreen()
    data object ChildLogin : AuthScreen()
    data object ParentRegister : AuthScreen()
    data object GenerateCode : AuthScreen()
    data object ConnectCode : AuthScreen()
    data object ParentHome : AuthScreen()
    data object ChildHome : AuthScreen()
}

@Composable
fun AppAuthFlow(
    authManager: AuthManager,
    onNavigateToPermissions: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf<AuthScreen>(AuthScreen.UserType) }
    var isLoggedIn by remember { mutableStateOf(authManager.isLoggedIn) }
    var userType by remember { mutableStateOf(authManager.currentUserType) }

    // Check if already logged in
    LaunchedEffect(isLoggedIn, userType) {
        if (isLoggedIn) {
            when (userType) {
                UserType.PARENT -> currentScreen = AuthScreen.ParentHome
                UserType.CHILD -> currentScreen = AuthScreen.ChildHome
                UserType.NONE -> currentScreen = AuthScreen.UserType
            }
        }
    }

    MalakiTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentScreen) {
                AuthScreen.UserType -> {
                    UserTypeScreen(
                        onParentSelected = { currentScreen = AuthScreen.ParentLogin },
                        onChildSelected = { currentScreen = AuthScreen.ChildLogin }
                    )
                }

                AuthScreen.ParentLogin -> {
                    ParentLoginScreen(
                        onLoginSuccess = {
                            isLoggedIn = authManager.isLoggedIn
                            userType = authManager.currentUserType
                            currentScreen = AuthScreen.ParentHome
                        },
                        onBack = { currentScreen = AuthScreen.UserType },
                        onRegister = { currentScreen = AuthScreen.ParentRegister },
                        authManager = authManager
                    )
                }

                AuthScreen.ChildLogin -> {
                    ChildLoginScreen(
                        onLoginSuccess = {
                            isLoggedIn = authManager.isLoggedIn
                            userType = authManager.currentUserType
                            if (authManager.linkedParentId != null) {
                                currentScreen = AuthScreen.ChildHome
                            } else {
                                currentScreen = AuthScreen.ConnectCode
                            }
                        },
                        onBack = { currentScreen = AuthScreen.UserType },
                        onNeedCode = { currentScreen = AuthScreen.ConnectCode },
                        authManager = authManager
                    )
                }

                AuthScreen.ParentRegister -> {
                    ParentRegistrationScreen(
                        onRegistrationSuccess = {
                            currentScreen = AuthScreen.ParentLogin
                        },
                        onBack = { currentScreen = AuthScreen.ParentLogin }
                    )
                }

                AuthScreen.GenerateCode -> {
                    GenerateCodeScreen(
                        onCodeGenerated = { code ->
                            // Code generated successfully
                        },
                        onBack = { currentScreen = AuthScreen.ParentHome },
                        authManager = authManager
                    )
                }

                AuthScreen.ConnectCode -> {
                    ConnectCodeScreen(
                        onConnectionSuccess = {
                            currentScreen = AuthScreen.ChildHome
                        },
                        onBack = { currentScreen = AuthScreen.ChildLogin },
                        authManager = authManager
                    )
                }

                AuthScreen.ParentHome -> {
                    ParentDashboard(
                        onNavigate = { view ->
                            when (view) {
                                "generateCode" -> currentScreen = AuthScreen.GenerateCode
                                "addChild" -> currentScreen = AuthScreen.AddChild
                                "logout" -> {
                                    authManager.logout()
                                    isLoggedIn = false
                                    currentScreen = AuthScreen.UserType
                                }
                                else -> { /* Handle other navigation */ }
                            }
                        }
                    )
                }

// Add new screen
                AuthScreen.AddChild -> {
                    AddChildScreen(
                        parentId = authManager.currentUser?.uid ?: "",
                        onChildAdded = {
                            currentScreen = AuthScreen.ParentHome
                        },
                        onBack = { currentScreen = AuthScreen.ParentHome }
                    )
                }

                AuthScreen.ChildHome -> {
                    ChildHome(
                        onNavigate = { view ->
                            when (view) {
                                "logout" -> {
                                    authManager.logout()
                                    isLoggedIn = false
                                    currentScreen = AuthScreen.UserType
                                }
                                else -> { /* Handle other navigation */ }
                            }
                        }
                    )
                }
            }
        }
    }
}

