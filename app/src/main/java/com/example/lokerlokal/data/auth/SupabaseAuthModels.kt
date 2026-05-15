package com.example.lokerlokal.data.auth

import org.json.JSONObject

data class SupabaseAuthUser(
    val id: String,
    val email: String? = null,
    val role: String? = null,
)

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val tokenType: String,
    val user: SupabaseAuthUser,
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("access_token", accessToken)
            .put("refresh_token", refreshToken)
            .put("expires_in_seconds", expiresInSeconds)
            .put("token_type", tokenType)
            .put("user", JSONObject()
                .put("id", user.id)
                .put("email", user.email ?: JSONObject.NULL)
                .put("role", user.role ?: JSONObject.NULL)
            )
    }

    companion object {
        fun fromJson(json: JSONObject): SupabaseSession? {
            val accessToken = json.optString("access_token").trim()
            val refreshToken = json.optString("refresh_token").trim()
            val tokenType = json.optString("token_type", "bearer").trim()
            val expiresInSeconds = json.optLong("expires_in", 0L)
            val userJson = json.optJSONObject("user")
            val userId = userJson?.optString("id").orEmpty().trim()
            if (accessToken.isBlank() || refreshToken.isBlank() || userId.isBlank()) return null

            fun JSONObject.optCleanString(key: String): String? {
                return optString(key)
                    .trim()
                    .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            }

            return SupabaseSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresInSeconds = expiresInSeconds,
                tokenType = tokenType,
                user = SupabaseAuthUser(
                    id = userId,
                    email = userJson?.optCleanString("email"),
                    role = userJson?.optCleanString("role"),
                ),
            )
        }
    }
}


