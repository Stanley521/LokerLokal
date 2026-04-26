package com.example.lokerlokal.data.remote

import android.util.Log
import com.example.lokerlokal.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class NearbyJob(
    val id: Long,
    val title: String,
    val businessName: String,
    val description: String,
    val jobType: String,
    val payText: String,
    val addressText: String,
    val whatsapp: String,
    val phone: String,
    val expiresAt: String,
    val createdAt: String,
    val latitude: Double,
    val longitude: Double,
)

object SupabaseJobsService {

    private const val TAG = "SupabaseJobsService"
    private const val BODY_PREVIEW_MAX_LENGTH = 500

    suspend fun getNearbyJobs(lat: Double, lng: Double, radius: Int = 2000): List<NearbyJob> =
        withContext(Dispatchers.IO) {
            val supabaseUrl = BuildConfig.SUPABASE_URL.trim()
            val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()

            if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
                logDebug("Missing Supabase configuration in BuildConfig")
                throw IllegalStateException(
                    "SUPABASE_URL / SUPABASE_ANON_KEY is empty. Add them in local.properties"
                )
            }

            val endpoint = URL("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/get_nearby_jobs")
            val payload = JSONObject()
                .put("lat", lat)
                .put("lng", lng)
                .put("radius", radius)
                .toString()

            logDebug("Request -> POST $endpoint payload=$payload")

            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 20_000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", supabaseAnonKey)
                setRequestProperty("Authorization", "Bearer $supabaseAnonKey")
            }

            try {
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                logDebug(
                    "Response <- code=$code success=${code in 200..299} body=${body.toBodyPreview()}"
                )

                if (code !in 200..299) {
                    Log.e(
                        TAG,
                        "Supabase RPC failed. endpoint=$endpoint code=$code body=${body.toBodyPreview()}"
                    )
                    throw IOException("Supabase RPC failed ($code): $body")
                }

                if (body.isBlank()) {
                    logDebug("Response body is empty; returning no jobs")
                    return@withContext emptyList()
                }

                val jsonArray = JSONArray(body)
                logDebug("Parsed ${jsonArray.length()} jobs from Supabase")
                return@withContext List(jsonArray.length()) { index ->
                    jsonArray.optJSONObject(index)?.toNearbyJob(index)
                        ?: NearbyJob(
                            id = index.toLong(),
                            title = "Unknown",
                            businessName = "Unknown",
                            description = "",
                            jobType = "",
                            payText = "",
                            addressText = "",
                            whatsapp = "",
                            phone = "",
                            expiresAt = "",
                            createdAt = "",
                            latitude = lat,
                            longitude = lng,
                        )
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    private fun String.toBodyPreview(maxLength: Int = BODY_PREVIEW_MAX_LENGTH): String {
        val normalized = replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return "<empty>"
        return if (normalized.length <= maxLength) {
            normalized
        } else {
            normalized.take(maxLength) + "…"
        }
    }

    private fun JSONObject.toNearbyJob(index: Int): NearbyJob {
        val parsedLat = optDoubleAny("latitude", "lat", "job_latitude")
        val parsedLng = optDoubleAny("longitude", "lng", "job_longitude")

        return NearbyJob(
            id = optLongAny("id", "job_id") ?: index.toLong(),
            title = optStringAny("title", "job_title", "position", "nama_pekerjaan") ?: "Untitled Job",
            businessName =
                optStringAny("business_name", "company", "company_name", "nama_perusahaan")
                    ?: "Unknown Business",
            description = optStringAny("description", "job_description", "deskripsi") ?: "",
            jobType = optStringAny("job_type", "type", "tipe_pekerjaan") ?: "",
            payText = optStringAny("pay_text", "salary_text", "gaji_text") ?: "",
            addressText = optStringAny("address_text", "address", "alamat") ?: "",
            whatsapp = optStringAny("whatsapp", "whatsapp_number") ?: "",
            phone = optStringAny("phone", "phone_number") ?: "",
            expiresAt = optStringAny("expires_at", "expired_at") ?: "",
            createdAt = optStringAny("created_at") ?: "",
            latitude = parsedLat ?: 0.0,
            longitude = parsedLng ?: 0.0,
        )
    }

    private fun JSONObject.optStringAny(vararg keys: String): String? {
        for (key in keys) {
            if (has(key) && !isNull(key)) {
                val value = optString(key).trim()
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }

    private fun JSONObject.optLongAny(vararg keys: String): Long? {
        for (key in keys) {
            if (has(key) && !isNull(key)) {
                val asLong = optLong(key, Long.MIN_VALUE)
                if (asLong != Long.MIN_VALUE) return asLong
                val asString = optString(key).trim()
                asString.toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun JSONObject.optDoubleAny(vararg keys: String): Double? {
        for (key in keys) {
            if (has(key) && !isNull(key)) {
                val asDouble = optDouble(key, Double.NaN)
                if (!asDouble.isNaN()) return asDouble
                val asString = optString(key).trim()
                asString.toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }
}

