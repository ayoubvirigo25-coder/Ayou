package eu.kanade.tachiyomi.data.translation

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ExtraProvider(
    val id: String,
    val displayName: String,
    val chatUrl: String,
    val defaultModel: String,
    val requiresKey: Boolean = true
)

object ExtraProviders {
    val LIST = listOf(
        ExtraProvider("google_free", "Google Translate (Online - \u0645\u062c\u0627\u0646\u064a \u0628\u062f\u0648\u0646 \u0645\u0641\u062a\u0627\u062d)", "", "google-gtx", requiresKey = false),
        ExtraProvider("groq", "Groq (Fastest LPU)", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"),
        ExtraProvider("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1/chat/completions", "meta/llama-3.3-70b-instruct"),
        ExtraProvider("cerebras", "Cerebras Cloud", "https://api.cerebras.ai/v1/chat/completions", "llama-3.3-70b"),
        ExtraProvider("mistral", "Mistral AI", "https://api.mistral.ai/v1/chat/completions", "mistral-small-latest"),
        ExtraProvider("zai", "Z.ai (GLM)", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash"),
        ExtraProvider("siliconflow", "SiliconFlow", "https://api.siliconflow.cn/v1/chat/completions", "Qwen/Qwen2.5-72B-Instruct"),
        ExtraProvider("together", "Together AI", "https://api.together.xyz/v1/chat/completions", "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
        ExtraProvider("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference/v1/chat/completions", "accounts/fireworks/models/llama-v3p3-70b-instruct"),
        ExtraProvider("xai", "xAI (Grok)", "https://api.x.ai/v1/chat/completions", "grok-2-latest"),
        ExtraProvider("moonshot", "Moonshot (Kimi)", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k"),
        ExtraProvider("deepl", "DeepL API", "https://api-free.deepl.com/v2/translate", "deepl")
    )

    fun byId(id: String): ExtraProvider? = LIST.firstOrNull { it.id == id }
}

object LocalTranslationBridge {

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var port: Int = 0
    @Volatile var currentProvider: String = "google_free"
    @Volatile var currentTargetLang: String = "Arabic"
    @Volatile var currentApiKey: String = ""

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun ensureStarted(): String {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            return "http://127.0.0.1:$port/v1/chat/completions"
        }
        val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        port = ss.localPort
        thread(isDaemon = true, name = "LocalTranslationBridge") {
            while (!ss.isClosed) {
                try {
                    val client = ss.accept()
                    thread(isDaemon = true) { handleClient(client) }
                } catch (_: Throwable) {
                    break
                }
            }
        }
        return "http://127.0.0.1:$port/v1/chat/completions"
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 25000
            val input = s.getInputStream()
            val output = s.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))

            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val bodyChars = CharArray(contentLength)
            var readTotal = 0
            while (readTotal < contentLength) {
                val r = reader.read(bodyChars, readTotal, contentLength - readTotal)
                if (r == -1) break
                readTotal += r
            }
            val requestBody = String(bodyChars, 0, readTotal)

            val translatedContent = try {
                val json = JSONObject(requestBody)
                val messages = json.getJSONArray("messages")
                var userText = ""
                for (i in 0 until messages.length()) {
                    val msg = messages.getJSONObject(i)
                    if (msg.optString("role") == "user") {
                        userText = msg.optString("content")
                    }
                }
                translateStructuredText(userText)
            } catch (e: Throwable) {
                ""
            }

            val respJson = JSONObject().apply {
                put("id", "chatcmpl-local")
                put("object", "chat.completion")
                put("choices", JSONArray().put(
                    JSONObject().apply {
                        put("index", 0)
                        put("message", JSONObject().apply {
                            put("role", "assistant")
                            put("content", translatedContent)
                        })
                        put("finish_reason", "stop")
                    }
                ))
                put("usage", JSONObject().apply {
                    put("prompt_tokens", 10)
                    put("completion_tokens", 10)
                    put("total_tokens", 20)
                })
            }.toString()

            val respBytes = respJson.toByteArray(Charsets.UTF_8)
            val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${respBytes.size}\r\n" +
                "Connection: close\r\n\r\n"
            output.write(headers.toByteArray(Charsets.UTF_8))
            output.write(respBytes)
            output.flush()
        }
    }

    private fun mapLangCode(langName: String): String = when (langName.lowercase()) {
        "arabic" -> "ar"
        "english" -> "en"
        "japanese" -> "ja"
        "korean" -> "ko"
        "simplified chinese" -> "zh-CN"
        "traditional chinese" -> "zh-TW"
        "spanish" -> "es"
        "french" -> "fr"
        "german" -> "de"
        "portuguese" -> "pt"
        "russian" -> "ru"
        else -> "ar"
    }

    private fun translateStructuredText(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val langCode = mapLangCode(currentTargetLang)

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return runCatching {
                val arr = JSONArray(trimmed)
                val list = (0 until arr.length()).map { arr.getString(it) }
                val trans = batchTranslate(list, langCode)
                JSONArray(trans).toString()
            }.getOrElse { translateLinesPreservingPrefixes(input, langCode) }
        }

        return translateLinesPreservingPrefixes(input, langCode)
    }

    private fun translateLinesPreservingPrefixes(input: String, langCode: String): String {
        val lines = input.split("\n")
        val prefixRegex = Regex("""^(\s*(?:<\|?\d+\|>|\[\d+\]|\(\d+\)|\d+[\.\):\-])\s*)(.*)$""")
        val prefixes = ArrayList<String>(lines.size)
        val bodies = ArrayList<String>(lines.size)

        for (line in lines) {
            val match = prefixRegex.find(line)
            if (match != null) {
                prefixes.add(match.groupValues[1])
                bodies.add(match.groupValues[2])
            } else {
                prefixes.add("")
                bodies.add(line)
            }
        }

        val nonBlankIndices = bodies.indices.filter { bodies[it].isNotBlank() }
        if (nonBlankIndices.isEmpty()) return input

        val toTranslate = nonBlankIndices.map { bodies[it] }
        val translatedList = batchTranslate(toTranslate, langCode)

        val resultLines = bodies.toMutableList()
        for ((idx, origIndex) in nonBlankIndices.withIndex()) {
            val tr = translatedList.getOrNull(idx)?.trim()
            if (!tr.isNullOrEmpty()) {
                resultLines[origIndex] = tr
            }
        }

        return resultLines.indices.joinToString("\n") { i -> prefixes[i] + resultLines[i] }
    }

    private fun batchTranslate(texts: List<String>, targetLang: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        if (currentProvider == "deepl" && currentApiKey.isNotBlank()) {
            runCatching { return translateDeepL(texts, targetLang, currentApiKey) }
        }
        return runCatching { translateGoogleFree(texts, targetLang) }.getOrDefault(texts)
    }

    private fun translateGoogleFree(texts: List<String>, targetLang: String): List<String> {
        val sep = "\n ||| \n"
        val combined = texts.joinToString(sep)
        val encoded = URLEncoder.encode(combined, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded"

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return texts
            val outer = JSONArray(body).getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until outer.length()) {
                sb.append(outer.getJSONArray(i).getString(0))
            }
            val parts = sb.toString().split("|||").map { it.trim() }
            return texts.indices.map { i -> parts.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: texts[i] }
        }
    }

    private fun translateDeepL(texts: List<String>, targetLang: String, apiKey: String): List<String> {
        val arr = JSONArray()
        texts.forEach { arr.put(it) }
        val payload = JSONObject().apply {
            put("text", arr)
            put("target_lang", targetLang.uppercase().substringBefore("-"))
        }
        val endpoint = if (apiKey.endsWith(":fx")) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }
        val req = Request.Builder()
            .url(endpoint)
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return texts
            val trans = JSONObject(body).getJSONArray("translations")
            return texts.indices.map { i ->
                if (i < trans.length()) trans.getJSONObject(i).getString("text") else texts[i]
            }
        }
    }
}