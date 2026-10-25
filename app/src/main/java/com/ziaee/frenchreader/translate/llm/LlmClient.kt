package com.ziaee.frenchreader.translate.llm

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** [detail] is the provider's own error message (never the key), shown when testing the connection. */
class LlmError(val kind: Kind, val detail: String? = null) : Exception(kind.name) {
    enum class Kind { MISSING_KEY, UNAUTHORIZED, PAYMENT, FORBIDDEN, NOT_FOUND, RATE_LIMIT, UNREACHABLE, BAD_RESPONSE }
}

/**
 * The "error.message" of an OpenAI-compatible error body, shortened for a snackbar. A provider may echo
 * the key it rejected, so the key and anything key-shaped are masked before the text goes anywhere.
 */
internal fun providerErrorMessage(errorBody: String?, secret: String? = null): String? = runCatching {
    val error = JSONObject(errorBody.orEmpty()).opt("error")
    var message = ((error as? JSONObject)?.optString("message") ?: error as? String)?.trim() ?: return@runCatching null
    secret?.trim()?.takeIf { it.length >= 4 }?.let { message = message.replace(it, "••••") }
    message = keyShaped.replace(message, "••••")
    message.take(200).takeIf { it.isNotEmpty() }
}.getOrNull()

private val keyShaped = Regex("\\b(sk|pk|or|key)[-_][A-Za-z0-9_-]{8,}|\\b[A-Za-z0-9_-]{32,}")

internal fun completionUrl(baseUrl: String, computer: Boolean): String {
    val base = baseUrl.trim().trimEnd('/')
    val path = URI(base).path.orEmpty()
    return base + (if (computer && !path.endsWith("/v1")) "/v1" else "") + "/chat/completions"
}

/** A 400 that names the JSON-mode parameter, as opposed to any other bad request. */
internal fun rejectsJsonMode(errorBody: String?): Boolean =
    errorBody != null && Regex("response_format|json_object|json mode", RegexOption.IGNORE_CASE).containsMatchIn(errorBody)

internal fun parseLlmContent(content: String): JSONObject {
    try {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end < start) throw LlmError(LlmError.Kind.BAD_RESPONSE)
        return JSONObject(content.substring(start, end + 1))
    } catch (_: Exception) {
        throw LlmError(LlmError.Kind.BAD_RESPONSE)
    }
}

class LlmClient(
    baseUrl: String,
    private val model: String,
    private val token: String?,
    private val computer: Boolean,
    private val provider: LlmProvider? = null,
    private val refindBaseUrl: (() -> String?)? = null
) {
    @Volatile private var activeBaseUrl = baseUrl

    internal fun complete(prompt: LlmPrompt): JSONObject {
        fun request(url: String): JSONObject = try {
            complete(prompt, jsonMode = true, url)
        } catch (_: JsonModeUnsupported) {
            complete(prompt, jsonMode = false, url)
        }
        val requestUrl = activeBaseUrl
        return try { request(requestUrl) } catch (error: LlmError) {
            if (error.kind != LlmError.Kind.UNREACHABLE) throw error
            val newUrl = refindBaseUrl?.invoke() ?: throw error
            // Discovery may confirm the same address after a transient cold-start failure.
            // Retry it once, just as we would a changed address.
            activeBaseUrl = newUrl
            request(newUrl)
        }
    }

    private class JsonModeUnsupported : Exception()

    private fun complete(prompt: LlmPrompt, jsonMode: Boolean, requestBaseUrl: String): JSONObject {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(completionUrl(requestBaseUrl, computer)).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = if (computer) 4_000 else 10_000
            connection.readTimeout = 90_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            token?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (provider == LlmProvider.OPENROUTER) {
                connection.setRequestProperty("HTTP-Referer", "https://github.com/Ziaeemehr/FrenchReaderSpike")
                connection.setRequestProperty("X-Title", "French Reader")
            }
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", prompt.system))
                .put(JSONObject().put("role", "user").put("content", prompt.user))
            val body = JSONObject().put("model", model).put("temperature", 0.2).put("messages", messages)
            if (jsonMode) body.put("response_format", JSONObject().put("type", "json_object"))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                if (jsonMode && status == 400 && rejectsJsonMode(errorBody)) throw JsonModeUnsupported()
                throw LlmError(when (status) {
                    401 -> LlmError.Kind.UNAUTHORIZED
                    402 -> LlmError.Kind.PAYMENT
                    403 -> LlmError.Kind.FORBIDDEN
                    404 -> LlmError.Kind.NOT_FOUND
                    429 -> LlmError.Kind.RATE_LIMIT
                    else -> LlmError.Kind.BAD_RESPONSE
                }, providerErrorMessage(errorBody, token))
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val content = JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").get("content") as? String ?: throw LlmError(LlmError.Kind.BAD_RESPONSE)
            return parseLlmContent(content)
        } catch (error: LlmError) {
            throw error
        } catch (error: JsonModeUnsupported) {
            throw error
        } catch (_: IOException) {
            throw LlmError(LlmError.Kind.UNREACHABLE)
        } catch (_: Exception) {
            throw LlmError(LlmError.Kind.BAD_RESPONSE)
        } finally {
            connection?.disconnect()
        }
    }
}
