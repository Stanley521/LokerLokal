package com.example.lokerlokal.data.auth

import com.example.lokerlokal.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object SupabaseAuthService {

    suspend fun signInWithPassword(email: String, password: String): SupabaseSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)

        val response = executeAuthRequest(
            path = "/auth/v1/token?grant_type=password",
            payload = payload,
        )

        parseSessionOrThrow(response, "Login failed")
    }

    suspend fun signUpWithPassword(
        email: String,
        password: String,
        fullName: String,
    ): SupabaseSession = withContext(Dispatchers.IO) {
        val data = JSONObject().apply {
            if (fullName.trim().isNotBlank()) put("full_name", fullName.trim())
        }
        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", data)

        val response = executeAuthRequest(
            path = "/auth/v1/signup",
            payload = payload,
        )

        parseSessionOrNull(response) ?: runCatching {
            signInWithPassword(email, password)
        }.getOrElse { signupError ->
            val message = response.optString("msg").trim().ifBlank {
                response.optString("error_description").trim().ifBlank {
                    response.optString("error").trim().ifBlank {
                        signupError.message ?: "Signup failed"
                    }
                }
            }
            throw IOException(message)
        }
    }

    private fun executeAuthRequest(path: String, payload: JSONObject): JSONObject {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            throw IllegalStateException("Missing Supabase configuration")
        }

        val endpoint = URL("$supabaseUrl$path")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = if (path.contains("signup", ignoreCase = true)) "POST" else "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doInput = true
            doOutput = true
            setRequestProperty("apikey", supabaseAnonKey)
            setRequestProperty("Authorization", "Bearer $supabaseAnonKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) {
                throw IOException(extractErrorMessage(body, code))
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSessionOrThrow(json: JSONObject, fallbackError: String): SupabaseSession {
        val session = parseSessionOrNull(json)
        if (session != null) return session

        val errorMessage = json.optString("msg").trim().ifBlank {
            json.optString("error_description").trim().ifBlank {
                json.optString("error").trim().ifBlank { fallbackError }
            }
        }
        throw IOException(errorMessage)
    }

    private fun parseSessionOrNull(json: JSONObject): SupabaseSession? {
        val sessionJson = json.optJSONObject("session")
        return SupabaseSession.fromJson(sessionJson ?: json)
    }

    private fun extractErrorMessage(body: String, code: Int): String {
        if (body.isBlank()) return "Auth request failed ($code)"
        return runCatching {
            val json = JSONObject(body)
            json.optString("msg").trim().ifBlank {
                json.optString("error_description").trim().ifBlank {
                    json.optString("error").trim().ifBlank { body }
                }
            }
        }.getOrElse { body }
    }
}


