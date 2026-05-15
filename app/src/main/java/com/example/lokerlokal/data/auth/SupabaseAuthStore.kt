package com.example.lokerlokal.data.auth

import android.content.Context
import org.json.JSONObject

object SupabaseAuthStore {

    private const val PREFS_NAME = "supabase_auth_session"
    private const val KEY_SESSION_JSON = "session_json"

    fun saveSession(context: Context, session: SupabaseSession) {
        prefs(context).edit().putString(KEY_SESSION_JSON, session.toJson().toString()).apply()
    }

    fun loadSession(context: Context): SupabaseSession? {
        val raw = prefs(context).getString(KEY_SESSION_JSON, null)?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching { SupabaseSession.fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun clearSession(context: Context) {
        prefs(context).edit().remove(KEY_SESSION_JSON).apply()
    }

    fun currentUserId(context: Context): String? = loadSession(context)?.user?.id

    fun currentAccessToken(context: Context): String? = loadSession(context)?.accessToken

    fun isSignedIn(context: Context): Boolean = loadSession(context) != null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

