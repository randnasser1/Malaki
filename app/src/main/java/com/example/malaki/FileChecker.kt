package com.example.malaki

import android.content.Context
import android.util.Log

class FileChecker(private val context: Context) {

    fun checkFiles() {
        try {
            val filesDir = context.filesDir
            val files = filesDir.listFiles()

            Log.d("FileChecker", "📁 Checking files in: ${filesDir.absolutePath}")

            if (files == null || files.isEmpty()) {
                Log.d("FileChecker", "📭 No files found!")
                return
            }

            Log.d("FileChecker", "📊 Found ${files.size} files:")

            files.sortedByDescending { it.lastModified() }.forEach { file ->
                val sizeKB = file.length() / 1024
                val lastModified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(file.lastModified())
                Log.d("FileChecker", "  • ${file.name} (${sizeKB} KB) - $lastModified")

                // Show first few lines of each file
                if (file.name.endsWith(".json") && file.length() < 10000) {
                    try {
                        val content = file.readText()
                        Log.d("FileChecker", "    Content preview: ${content.take(200)}...")
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("FileChecker", "Error checking files: ${e.message}")
        }
    }
}