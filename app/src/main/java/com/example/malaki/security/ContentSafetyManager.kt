package com.example.malaki.security

import android.content.Context
import android.util.Log
import com.example.malaki.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ContentSafetyManager(private val context: Context) {

    companion object {
        private const val TAG = "ContentSafetyManager"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class SafetyResult(
        val isSafe: Boolean,
        val riskLevel: RiskLevel,
        val blockReasons: List<String>,
        val confidenceScore: Float
    )

    enum class RiskLevel { SAFE, LOW, MEDIUM, HIGH, CRITICAL }

    /**
     * Analyze a URL (image or webpage) for unsafe content
     */
    suspend fun analyzeUrl(url: String): SafetyResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Analyzing URL: $url")

        // First, check if it's an image URL or webpage
        val isImage = url.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|bmp)(\\?.*)?$", RegexOption.IGNORE_CASE))

        return@withContext if (isImage) {
            analyzeImageUrl(url)
        } else {
            analyzeWebpageUrl(url)
        }
    }

    private suspend fun analyzeImageUrl(url: String): SafetyResult {
        // Use Google Vision API for images
        return checkWithGoogleVision(url)
    }

    private suspend fun analyzeWebpageUrl(url: String): SafetyResult {
        // Use Jina Reader + RapidAPI for webpages
        val webpageText = extractTextWithJina(url)

        if (webpageText.isNullOrBlank()) {
            return SafetyResult(
                isSafe = true,
                riskLevel = RiskLevel.SAFE,
                blockReasons = emptyList(),
                confidenceScore = 0.5f
            )
        }

        return checkWithRapidApi(webpageText)
    }

    private suspend fun extractTextWithJina(url: String): String? {
        val jinaUrl = "https://r.jina.ai/${url}"

        val request = Request.Builder()
            .url(jinaUrl)
            .header("User-Agent", "Mozilla/5.0 (compatible; Malaki/1.0)")
            .header("Accept", "text/plain")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Jina extraction failed: ${e.message}")
            null
        }
    }

    private suspend fun checkWithGoogleVision(url: String): SafetyResult {
        // TODO: Implement Google Vision API call
        // For now, return safe as placeholder
        return SafetyResult(
            isSafe = true,
            riskLevel = RiskLevel.SAFE,
            blockReasons = emptyList(),
            confidenceScore = 0.8f
        )
    }

    private suspend fun checkWithRapidApi(text: String): SafetyResult {
        val jsonBody = JSONObject(mapOf(
            "text" to text.take(5000),
            "detail_level" to "light"
        )).toString()

        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://ai-text-moderation-toxicity-aspects-sentiment-analyzer.p.rapidapi.com/analyze.php")
            .post(requestBody)
            .header("x-rapidapi-key", BuildConfig.RAPIDAPI_KEY)
            .header("x-rapidapi-host", "ai-text-moderation-toxicity-aspects-sentiment-analyzer.p.rapidapi.com")
            .header("Content-Type", "application/json")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                parseRapidApiResponse(responseBody)
            } else {
                SafetyResult(true, RiskLevel.SAFE, emptyList(), 0.5f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "RapidAPI error: ${e.message}")
            SafetyResult(true, RiskLevel.SAFE, emptyList(), 0.5f)
        }
    }

    private fun parseRapidApiResponse(responseBody: String?): SafetyResult {
        if (responseBody.isNullOrBlank()) {
            return SafetyResult(true, RiskLevel.SAFE, emptyList(), 0.5f)
        }

        val blockReasons = mutableListOf<String>()
        var maxScore = 0f

        try {
            val json = JSONObject(responseBody)
            if (json.optBoolean("ok", false)) {
                val data = json.optJSONObject("data")
                val items = data?.optJSONArray("items")

                items?.let {
                    for (i in 0 until it.length()) {
                        val item = it.getJSONObject(i)
                        val toxicity = item.optJSONObject("toxicity")
                        val dimensions = toxicity?.optJSONObject("dimensions")

                        dimensions?.let { dims ->
                            // Check self-harm
                            if (dims.has("self_harm")) {
                                val score = dims.getDouble("self_harm").toFloat()
                                if (score > 0.5) {
                                    blockReasons.add("Self-harm content detected")
                                    maxScore = maxOf(maxScore, score)
                                }
                            }
                            // Check violence
                            if (dims.has("threats_or_violence")) {
                                val score = dims.getDouble("threats_or_violence").toFloat()
                                if (score > 0.5) {
                                    blockReasons.add("Violent content detected")
                                    maxScore = maxOf(maxScore, score)
                                }
                            }
                            // Check sexual content
                            if (dims.has("sexual_content")) {
                                val score = dims.getDouble("sexual_content").toFloat()
                                if (score > 0.5) {
                                    blockReasons.add("Adult content detected")
                                    maxScore = maxOf(maxScore, score)
                                }
                            }
                            // Check hate speech
                            if (dims.has("hate_speech")) {
                                val score = dims.getDouble("hate_speech").toFloat()
                                if (score > 0.5) {
                                    blockReasons.add("Hate speech detected")
                                    maxScore = maxOf(maxScore, score)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response: ${e.message}")
        }

        val riskLevel = when {
            blockReasons.isEmpty() -> RiskLevel.SAFE
            maxScore > 0.9 -> RiskLevel.CRITICAL
            maxScore > 0.7 -> RiskLevel.HIGH
            maxScore > 0.5 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        return SafetyResult(
            isSafe = blockReasons.isEmpty(),
            riskLevel = riskLevel,
            blockReasons = blockReasons,
            confidenceScore = maxScore
        )
    }
}