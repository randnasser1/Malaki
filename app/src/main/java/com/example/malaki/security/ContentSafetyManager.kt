package com.example.malaki.security

import android.content.Context
import android.util.Log
import com.example.malaki.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        val confidenceScore: Float,
        val categoryScores: Map<String, Float> = emptyMap()
    )

    enum class RiskLevel { SAFE, LOW, MEDIUM, HIGH, CRITICAL }

    // ── Public entry point ──────────────────────────────────────────────────────
    // Throws on any failure — callers must catch and handle.
    suspend fun analyzeUrl(url: String): SafetyResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "▶️ analyzeUrl START: $url")
        val result = analyzeWebpageUrl(url)
        Log.d(TAG, "◀️ analyzeUrl DONE: isSafe=${result.isSafe} level=${result.riskLevel} reasons=${result.blockReasons}")
        result
    }

    // ── Step 1: extract text via Jina Reader ───────────────────────────────────
    private suspend fun analyzeWebpageUrl(url: String): SafetyResult {
        Log.d(TAG, "📖 STEP 1: fetching page text via Jina for $url")
        val pageText = try {
            extractTextWithJina(url)
        } catch (e: Exception) {
            // Jina failed — send the URL itself to the API so it can still classify
            Log.w(TAG, "⚠️ Jina failed: ${e.message} — falling back to URL-only analysis")
            return checkWithRapidApi("URL: $url")
        }
        Log.d(TAG, "📖 STEP 1 DONE: ${pageText.length} chars. Preview: ${pageText.take(200)}")

        // Prepend the URL so the API has full context (path like /r/cutting matters)
        val textToAnalyze = "URL: $url\n\n${pageText}"
        Log.d(TAG, "🔍 STEP 2: sending ${textToAnalyze.length.coerceAtMost(2000)} chars to RapidAPI…")
        return checkWithRapidApi(textToAnalyze)
    }

    private fun extractTextWithJina(url: String): String {
        val jinaUrl = "https://r.jina.ai/$url"
        Log.d(TAG, "📡 Jina → $jinaUrl")

        val request = Request.Builder()
            .url(jinaUrl)
            .header("User-Agent", "Mozilla/5.0 (compatible; Malaki/1.0)")
            .header("Accept", "text/plain")
            .build()

        val response = client.newCall(request).execute()
        Log.d(TAG, "📩 Jina HTTP ${response.code}")

        if (!response.isSuccessful) {
            val body = response.body?.string()?.take(200) ?: ""
            throw Exception("Jina HTTP ${response.code} for $url — $body")
        }

        val text = response.body?.string()
        if (text.isNullOrBlank()) throw Exception("Jina returned empty body for $url")

        Log.d(TAG, "✅ Jina OK: ${text.length} chars")
        return text
    }

    // ── Step 2: call RapidAPI text moderation ─────────────────────────────────
    private fun checkWithRapidApi(text: String): SafetyResult {
        val key = BuildConfig.RAPIDAPI_KEY
        if (key.isBlank()) throw Exception("RAPIDAPI_KEY is empty — set it in secrets.properties")

        Log.d(TAG, "📡 RapidAPI key prefix: ${key.take(6)}***  text_len=${text.length.coerceAtMost(2000)}")

        val body = JSONObject().apply {
            put("text", text.take(2000))
            put("detail_level", "light")
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://ai-text-moderation-toxicity-aspects-sentiment-analyzer.p.rapidapi.com/analyze.php")
            .post(body)
            .header("x-rapidapi-key", key)
            .header("x-rapidapi-host", "ai-text-moderation-toxicity-aspects-sentiment-analyzer.p.rapidapi.com")
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        Log.d(TAG, "📩 RapidAPI HTTP ${response.code}")

        if (!response.isSuccessful) {
            val errBody = response.body?.string()?.take(300) ?: ""
            throw Exception("RapidAPI HTTP ${response.code} — $errBody")
        }

        val responseBody = response.body?.string()
        Log.d(TAG, "📩 RapidAPI body (first 800): ${responseBody?.take(800)}")

        if (responseBody.isNullOrBlank()) throw Exception("RapidAPI returned empty body")

        return parseRapidApiResponse(responseBody)
    }

    // ── Step 3: parse response ─────────────────────────────────────────────────
    private fun parseRapidApiResponse(responseBody: String): SafetyResult {
        val json = JSONObject(responseBody)

        if (!json.optBoolean("ok", false))
            throw Exception("RapidAPI ok=false. Body: ${responseBody.take(300)}")

        val data = json.optJSONObject("data")
            ?: throw Exception("RapidAPI missing 'data'. Body: ${responseBody.take(300)}")
        val items = data.optJSONArray("items")
            ?: throw Exception("RapidAPI missing 'data.items'. Body: ${responseBody.take(300)}")

 val DIMENSION_LABELS = mapOf(
    "self_harm" to "⚠️ Self-harm content detected - This content discusses self-harm methods or suicidal ideation",
    "threats_or_violence" to "🔪 Violent or threatening content - Contains threats, violence, or harm to others",
    "sexual_content" to "🔞 Adult/sexual content - Contains sexually explicit material",
    "hate_speech" to "🏳️ Hate speech - Contains discriminatory or hateful content"
)

        val blockReasons = mutableListOf<String>()
        val categoryScores = mutableMapOf<String, Float>()
        var maxScore = 0f

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val dims = item.optJSONObject("toxicity")?.optJSONObject("dimensions") ?: continue

            DIMENSION_LABELS.forEach { (key, label) ->
                val score = dims.optDouble(key, -1.0).toFloat()
                if (score >= 0f) {
                    categoryScores[key] = score
                    Log.d(TAG, "   $key = ${"%.4f".format(score)}")
                    if (score > 0.5f) {
                        blockReasons.add(label)
                        maxScore = maxOf(maxScore, score)
                    }
                }
            }
        }

        Log.d(TAG, "📊 categoryScores=$categoryScores  flagged=$blockReasons  maxScore=${"%.4f".format(maxScore)}")

        val riskLevel = when {
            blockReasons.isEmpty() -> RiskLevel.SAFE
            maxScore > 0.9f        -> RiskLevel.CRITICAL
            maxScore > 0.7f        -> RiskLevel.HIGH
            maxScore > 0.5f        -> RiskLevel.MEDIUM
            else                   -> RiskLevel.LOW
        }

        return SafetyResult(
            isSafe          = blockReasons.isEmpty(),
            riskLevel       = riskLevel,
            blockReasons    = blockReasons,
            confidenceScore = maxScore,
            categoryScores  = categoryScores
        )
    }
}
