package com.example.lokerlokal.data.remote

import com.example.lokerlokal.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ResumeMeta(
    val userKey: String,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val updatedAt: String,
)

object SupabaseResumeService {

    private const val RESUME_TABLE = "resume_files"
    private const val RESUME_BUCKET = "resumes"

    suspend fun getLatestResume(userKey: String): ResumeMeta? = withContext(Dispatchers.IO) {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank() || userKey.isBlank()) return@withContext null

        val encodedUserKey = URLEncoder.encode(userKey, Charsets.UTF_8.name())
        val endpoint = URL(
            "$supabaseUrl/rest/v1/$RESUME_TABLE?user_key=eq.$encodedUserKey" +
                "&select=user_key,file_name,file_path,size_bytes,updated_at&order=updated_at.desc&limit=1"
        )
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            doInput = true
            setRequestProperty("apikey", supabaseAnonKey)
            setRequestProperty("Authorization", "Bearer $supabaseAnonKey")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299 || body.isBlank()) return@withContext null

            val array = JSONArray(body)
            if (array.length() == 0) return@withContext null

            return@withContext array.optJSONObject(0)?.let { json ->
                ResumeMeta(
                    userKey = json.optString("user_key"),
                    fileName = json.optString("file_name"),
                    filePath = json.optString("file_path"),
                    sizeBytes = json.optLong("size_bytes", 0L),
                    updatedAt = json.optString("updated_at"),
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun uploadResume(
        userKey: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ResumeMeta = withContext(Dispatchers.IO) {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank() || userKey.isBlank()) {
            throw IllegalStateException("Missing Supabase configuration or user key")
        }

        val filePath = "${sanitizePathPart(userKey)}/resume_${System.currentTimeMillis()}.pdf"
        val encodedPath = filePath
            .split('/')
            .joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8.name()) }

        val uploadEndpoint = URL("$supabaseUrl/storage/v1/object/$RESUME_BUCKET/$encodedPath")
        val uploadConnection = (uploadEndpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doInput = true
            doOutput = true
            setRequestProperty("apikey", supabaseAnonKey)
            setRequestProperty("Authorization", "Bearer $supabaseAnonKey")
            setRequestProperty("Content-Type", mimeType)
            setRequestProperty("x-upsert", "true")
        }

        try {
            uploadConnection.outputStream.use { it.write(bytes) }
            val uploadCode = uploadConnection.responseCode
            val uploadBody = (if (uploadCode in 200..299) uploadConnection.inputStream else uploadConnection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            if (uploadCode !in 200..299) {
                throw IOException("Upload failed ($uploadCode): $uploadBody")
            }
        } finally {
            uploadConnection.disconnect()
        }

        upsertResumeMeta(userKey, fileName, filePath, bytes.size.toLong())
        return@withContext getLatestResume(userKey)
            ?: throw IOException("Resume uploaded but metadata was not found")
    }

    suspend fun downloadResume(filePath: String): ByteArray = withContext(Dispatchers.IO) {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank() || filePath.isBlank()) {
            throw IllegalStateException("Missing Supabase configuration or file path")
        }

        val encodedPath = filePath
            .split('/')
            .joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8.name()) }
        val endpoint = URL("$supabaseUrl/storage/v1/object/$RESUME_BUCKET/$encodedPath")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            doInput = true
            setRequestProperty("apikey", supabaseAnonKey)
            setRequestProperty("Authorization", "Bearer $supabaseAnonKey")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Download failed ($code): $body")
            }
            return@withContext connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun upsertResumeMeta(
        userKey: String,
        fileName: String,
        filePath: String,
        sizeBytes: Long,
    ) {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()

        val endpoint = URL("$supabaseUrl/rest/v1/$RESUME_TABLE")
        val payload = JSONObject()
            .put("user_key", userKey)
            .put("file_name", fileName)
            .put("file_path", filePath)
            .put("size_bytes", sizeBytes)
            .toString()

        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", supabaseAnonKey)
            setRequestProperty("Authorization", "Bearer $supabaseAnonKey")
            setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
        }

        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Metadata upsert failed ($code): $body")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sanitizePathPart(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}

