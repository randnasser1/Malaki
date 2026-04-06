package com.example.malaki

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.malaki.auth.AuthManager
import com.example.malaki.ui.auth.AppAuthFlow
import com.example.malaki.ui.theme.MalakiTheme

class MainActivity : AppCompatActivity() {

    // Background services (run silently)
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var dataCollector: DataCollector
    private lateinit var authManager: AuthManager
    private val handler = Handler(Looper.getMainLooper())

    // Firebase instances
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize services (run in background)
        permissionHelper = PermissionHelper(this)
        dataCollector = DataCollector(this)
        authManager = AuthManager(this)

        // Initialize Firebase
        initializeFirebase()

        // Start background services if permissions are granted
        startBackgroundServicesIfPermitted()

        // Set up the Compose UI with Authentication Flow
        setContent {
            MalakiTheme {
                AppAuthFlow(authManager = authManager)
            }
        }
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startBackgroundServicesIfPermitted() {
        // Check if permissions are granted, then start background services
        if (permissionHelper.checkUsageStatsPermission()) {
            val serviceIntent = Intent(this, BackgroundService::class.java)
            startService(serviceIntent)
        }

        // Start collecting data periodically
        handler.postDelayed({
            collectDataIfPermitted()
        }, 5000)
    }

    private fun collectDataIfPermitted() {
        if (permissionHelper.checkUsageStatsPermission()) {
            Thread {
                try {
                    dataCollector.saveAppUsageData()
                    dataCollector.saveMessages()
                    dataCollector.saveMusicNotificationData()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
        // Repeat every 5 minutes
        handler.postDelayed({
            collectDataIfPermitted()
        }, 300000)
    }

    // Public methods that might be needed by other components
    fun getAuth(): FirebaseAuth = auth
    fun getFirestore(): FirebaseFirestore = firestore

    override fun onResume() {
        super.onResume()
        // Check and prompt for permissions if needed (but don't show debug UI)
        checkPermissionsSilently()
    }

    private fun checkPermissionsSilently() {
        if (!permissionHelper.checkUsageStatsPermission()) {
            // Show dialog to request permission
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Malaki needs usage access to monitor app usage and keep your child safe.")
                .setPositiveButton("Grant") { _, _ ->
                    permissionHelper.requestUsageStatsPermission()
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }
}