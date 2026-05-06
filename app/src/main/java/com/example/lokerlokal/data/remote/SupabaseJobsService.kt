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
import java.net.URLEncoder
import java.time.OffsetDateTime

data class NearbyJob(
    val id: String,
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
    val placeId: String,
)

object SupabaseJobsService {

    private const val TAG = "SupabaseJobsService"
    private const val BODY_PREVIEW_MAX_LENGTH = 500

    suspend fun getPlaceMapMarkersInBounds(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
    ): List<NearbyJob> =
        withContext(Dispatchers.IO) {
            val supabaseUrl = BuildConfig.SUPABASE_URL.trim()
            val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()

            if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
                logDebug("Missing Supabase configuration in BuildConfig")
                throw IllegalStateException(
                    "SUPABASE_URL / SUPABASE_ANON_KEY is empty. Add them in local.properties"
                )
            }

            val endpoint = URL("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/get_place_w_jobs_map_markers_in_bounds")
            val payload = JSONObject()
                .put("min_lat", minLat)
                .put("min_lng", minLng)
                .put("max_lat", maxLat)
                .put("max_lng", maxLng)
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
                    logDebug("Response body is empty; returning no marker places")
                    return@withContext emptyList()
                }

                val jsonArray = JSONArray(body)
                logDebug("Parsed ${jsonArray.length()} marker places from Supabase")
                return@withContext List(jsonArray.length()) { index ->
                    val rowObject = jsonArray.optJSONObject(index)
                    rowObject?.toNearbyJob(index) ?: NearbyJob(
                        id = index.toString(),
                        title = "",
                        businessName = "Unknown Business",
                        description = "",
                        jobType = "",
                        payText = "",
                        addressText = "",
                        whatsapp = "",
                        phone = "",
                        expiresAt = "",
                        createdAt = "",
                        latitude = 0.0,
                        longitude = 0.0,
                        placeId = "",
                    )
                }
            } finally {
                connection.disconnect()
            }
        }

    suspend fun getNearbyJobs(
        lat: Double,
        lng: Double,
        radius: Int = 2000,
        limit: Int = 20,
        offset: Int = 0,
    ): List<NearbyJob> =
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
                .put("p_limit", limit)
                .put("p_offset", offset)
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
                    val rawEntry = jsonArray.opt(index)
                    val rowObject = rawEntry as? JSONObject
                    if (rowObject == null) {
                        logDebug(
                            "Row[$index] is not a JSON object. type=${rawEntry?.javaClass?.simpleName ?: "null"} value=${rawEntry?.toString()?.take(220)}"
                        )
                        logDebug("Row[$index] fallback job generated (id=$index, placeId='')")
                        NearbyJob(
                            id = index.toString(),
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
                            placeId = "",
                        )
                    } else {
                        rowObject.toNearbyJob(index)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

    suspend fun getJobsByPlace(
        placeId: String,
        limit: Int = 20,
        offset: Int = 0,
    ): List<NearbyJob> =
        withContext(Dispatchers.IO) {
            val supabaseUrl = BuildConfig.SUPABASE_URL.trim()
            val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()

            if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
                logDebug("Missing Supabase configuration in BuildConfig")
                throw IllegalStateException(
                    "SUPABASE_URL / SUPABASE_ANON_KEY is empty. Add them in local.properties"
                )
            }

            val rpcEndpoint = URL("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/get_jobs_by_place")
            val rpcPayload = JSONObject()
                .put("p_place_id", placeId)
                .put("p_limit", limit)
                .put("p_offset", offset)
                .toString()

            runCatching {
                logDebug("Request -> POST $rpcEndpoint payload=$rpcPayload")
                val connection = (rpcEndpoint.openConnection() as HttpURLConnection).apply {
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
                    connection.outputStream.use { it.write(rpcPayload.toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()

                    logDebug("Response <- code=$code success=${code in 200..299} body=${body.toBodyPreview()}")

                    if (code !in 200..299) {
                        throw IOException("Supabase RPC get_jobs_by_place failed ($code): ${body.toBodyPreview()}")
                    }

                    if (body.isBlank()) {
                        return@withContext emptyList()
                    }

                    val jsonArray = JSONArray(body)
                    return@withContext List(jsonArray.length()) { index ->
                        val rowObject = jsonArray.optJSONObject(index)
                        rowObject?.toNearbyJob(index) ?: NearbyJob(
                            id = index.toString(),
                            title = "",
                            businessName = "Unknown Business",
                            description = "",
                            jobType = "",
                            payText = "",
                            addressText = "",
                            whatsapp = "",
                            phone = "",
                            expiresAt = "",
                            createdAt = "",
                            latitude = 0.0,
                            longitude = 0.0,
                            placeId = placeId,
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { rpcError ->
                logDebug("get_jobs_by_place RPC unavailable, fallback to table query. reason=${rpcError.message}")

                val encodedPlaceId = URLEncoder.encode(placeId, Charsets.UTF_8.name())
                val nowIso = URLEncoder.encode(OffsetDateTime.now().toString(), Charsets.UTF_8.name())
                val endpoint = URL(
                    "${supabaseUrl.trimEnd('/')}/rest/v1/jobs" +
                        "?select=id,title,business_name,description,job_type,pay_text,address_text,whatsapp,phone,created_at,expires_at,place_id" +
                        "&place_id=eq.$encodedPlaceId" +
                        "&is_active=eq.true" +
                        "&expires_at=gt.$nowIso" +
                        "&order=expires_at.asc" +
                        "&limit=$limit" +
                        "&offset=$offset"
                )

                logDebug("Request -> GET $endpoint")

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
                    val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()

                    logDebug("Response <- code=$code success=${code in 200..299} body=${body.toBodyPreview()}")

                    if (code !in 200..299) {
                        Log.e(TAG, "Supabase table fallback failed. endpoint=$endpoint code=$code body=${body.toBodyPreview()}")
                        throw IOException("Supabase place jobs fetch failed ($code): $body")
                    }

                    if (body.isBlank()) return@withContext emptyList()
                    val jsonArray = JSONArray(body)
                    return@withContext List(jsonArray.length()) { index ->
                        val rowObject = jsonArray.optJSONObject(index)
                        rowObject?.toNearbyJob(index) ?: NearbyJob(
                            id = index.toString(),
                            title = "",
                            businessName = "Unknown Business",
                            description = "",
                            jobType = "",
                            payText = "",
                            addressText = "",
                            whatsapp = "",
                            phone = "",
                            expiresAt = "",
                            createdAt = "",
                            latitude = 0.0,
                            longitude = 0.0,
                            placeId = placeId,
                        )
                    }
                } finally {
                    connection.disconnect()
                }
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
        val parsedId =
            optStringAny("id", "job_id")
                ?: optStringAny("place_id", "placeId")
                ?: optLongAny("id", "job_id")?.toString()
                ?: index.toString()
        val parsedTitle = optStringAny("title", "job_title", "position", "nama_pekerjaan") ?: "Untitled Job"
        val parsedBusinessName =
            optStringAny("business_name", "name", "company", "company_name", "nama_perusahaan") ?: "Unknown Business"
        val parsedPlaceId =
            optStringAny(
                "place_id",
                "placeId",
                "business_place_id",
                "businessPlaceId",
                "business_placeid",
                "google_place_id",
                "googlePlaceId",
            ) ?: ""

        if (parsedPlaceId.isBlank()) {
            logDebug("Row[$index] placeId missing. availableKeys=${keysPreview()}")
        }

        logDebug(
            "Row[$index] parsed id=$parsedId title='${parsedTitle.take(40)}' business='${parsedBusinessName.take(40)}' placeId='${parsedPlaceId.ifBlank { "<empty>" }}'"
        )

        if (parsedBusinessName.isRanchMarketPesanggrahan()) {
            logDebug(
                "RanchMarketPesanggrahan row[$index] id=$parsedId title='${parsedTitle.take(80)}' placeId='${parsedPlaceId.ifBlank { "<empty>" }}' lat=${parsedLat ?: 0.0} lng=${parsedLng ?: 0.0} address='${(optStringAny("address_text", "address", "alamat") ?: "").take(120)}' keys=${keysPreview()} raw=${toString().toBodyPreview(900)}"
            )
        }

        return NearbyJob(
            id = parsedId,
            title = parsedTitle,
            businessName = parsedBusinessName,
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
            placeId = parsedPlaceId,
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

    private fun JSONObject.keysPreview(): String {
        val keys = mutableListOf<String>()
        val iterator = keys()
        while (iterator.hasNext()) {
            keys += iterator.next()
        }
        return keys.sorted().joinToString(prefix = "[", postfix = "]")
    }

    private fun String.isRanchMarketPesanggrahan(): Boolean {
        val normalized = lowercase()
        return normalized.contains("ranch") && normalized.contains("market") && normalized.contains("pesanggrahan")
    }
}

