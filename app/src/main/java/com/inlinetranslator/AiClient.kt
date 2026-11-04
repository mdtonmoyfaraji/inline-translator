package com.inlinetranslator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiClient(private val prefs: PreferencesManager) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("model", prefs.modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are an inline translator. Translate the given text into ${prefs.targetLang}. Output ONLY the translated text without quotes or notes.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                })
            })
            put("temperature", 0.3)
        }

        val request = Request.Builder()
            .url(prefs.apiUrl)
            .addHeader("Content-Type", "application/json")
            .apply {
                if (prefs.apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${prefs.apiKey}")
                }
            }
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val rawResponse = response.body?.string() ?: throw Exception("Empty response from AI server")

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: $rawResponse")
        }

        val json = JSONObject(rawResponse)
        val choices = json.getJSONArray("choices")
        val message = choices.getJSONObject(0).getJSONObject("message")
        return@withContext message.getString("content").trim()
    }
}
