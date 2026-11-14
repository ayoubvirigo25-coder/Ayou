package eu.kanade.tachiyomi.data.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class TranslationProvider(val displayName: String, val baseUrl: String, val defaultModel: String) {
    GOOGLE_FREE("Google Translate (Online)", "", ""),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-2.5-flash"),
    GROQ("Groq (Fastest AI)", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"),
    NVIDIA_NIM("NVIDIA NIM", "https://integrate.api.nvidia.com/v1/chat/completions", "meta/llama-3.3-70b-instruct"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "google/gemini-2.5-flash-preview"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1/chat/completions", "deepseek-chat"),
    DEEPL("DeepL API", "https://api-free.deepl.com/v2/translate", "")
}

class AiPageTranslator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /**
     * ترجمة جميع فقاعات الصفحة دفعة واحدة للحفاظ على السياق والسرعة
     */
    suspend fun translateBubbles(
        bubbles: List<BubbleRegion>,
        targetLang: String = "ar",
        provider: TranslationProvider = TranslationProvider.GOOGLE_FREE,
        apiKey: String = "",
        customModel: String = ""
    ) = withContext(Dispatchers.IO) {
        if (bubbles.isEmpty()) return@withContext

        try {
            when {
                provider == TranslationProvider.GOOGLE_FREE || apiKey.isBlank() -> {
                    translateWithGoogleFree(bubbles, targetLang)
                }
                provider == TranslationProvider.DEEPL -> {
                    translateWithDeepL(bubbles, targetLang, apiKey)
                }
                else -> {
                    translateWithOpenAiCompatible(
                        bubbles = bubbles,
                        targetLang = targetLang,
                        endpoint = provider.baseUrl,
                        apiKey = apiKey,
                        model = customModel.ifBlank { provider.defaultModel }
                    )
                }
            }
        } catch (e: Exception) {
            // في حال فشل الـ API أو انتهاء الرصيد، يتحول تلقائياً إلى Google المجاني حتى لا تتوقف القراءة
            translateWithGoogleFree(bubbles, targetLang)
        }
    }

    private fun translateWithGoogleFree(bubbles: List<BubbleRegion>, targetLang: String) {
        // دمج الفقاعات بفاصل رقمي واضح لترجمتها في طلب واحد سريع
        val separator = "\n ||| \n"
        val combined = bubbles.joinToString(separator) { it.originalText }
        val encoded = URLEncoder.encode(combined, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded"

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return
            val jsonArray = JSONArray(body).getJSONArray(0)
            val fullTranslated = StringBuilder()
            for (i in 0 until jsonArray.length()) {
                fullTranslated.append(jsonArray.getJSONArray(i).getString(0))
            }
            val splitParts = fullTranslated.toString().split("|||")
            for (i in bubbles.indices) {
                bubbles[i].translatedText = splitParts.getOrNull(i)?.trim() ?: bubbles[i].originalText
            }
        }
    }

    private fun translateWithOpenAiCompatible(
        bubbles: List<BubbleRegion>,
        targetLang: String,
        endpoint: String,
        apiKey: String,
        model: String
    ) {
        val numberedInput = bubbles.mapIndexed { idx, b -> "[${idx + 1}] ${b.originalText}" }.joinToString("\n")
        val systemPrompt = """
            You are an expert manga/manhwa translator into Arabic ($targetLang).
            Translate each numbered line naturally, matching character tone and context.
            Keep the exact numbering format [1], [2], etc. Output ONLY the translated lines.
        """.trimIndent()

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", numberedInput))
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.3)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response")
            val content = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            val regex = Regex("""\[(\d+)\]\s*(.*)""")
            for (line in content.lines()) {
                val match = regex.find(line.trim())
                if (match != null) {
                    val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: continue
                    val trans = match.groupValues[2].trim()
                    if (index in bubbles.indices && trans.isNotEmpty()) {
                        bubbles[index].translatedText = trans
                    }
                }
            }
        }
    }

    private fun translateWithDeepL(bubbles: List<BubbleRegion>, targetLang: String, apiKey: String) {
        val textArray = JSONArray()
        bubbles.forEach { textArray.put(it.originalText) }

        val payload = JSONObject().apply {
            put("text", textArray)
            put("target_lang", targetLang.uppercase())
        }

        val request = Request.Builder()
            .url("https://api-free.deepl.com/v2/translate")
            .addHeader("Authorization", "DeepL-Auth-Key $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty DeepL response")
            val translations = JSONObject(body).getJSONArray("translations")
            for (i in 0 until minOf(bubbles.size, translations.length())) {
                bubbles[i].translatedText = translations.getJSONObject(i).getString("text")
            }
        }
    }
}
