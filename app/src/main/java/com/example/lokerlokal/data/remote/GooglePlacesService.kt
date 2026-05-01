package com.example.lokerlokal.data.remote

import android.util.Log
import com.example.lokerlokal.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val PLACES_TAG = "GooglePlacesService"

data class BusinessPlaceDetails(
    val placeId: String,
    val displayName: String,
    val formattedAddress: String,
    val photoUrl: String?,
    val photoUrls: List<String>,
)

object GooglePlacesService {

    suspend fun getPlaceDetails(placeId: String): BusinessPlaceDetails? = withContext(Dispatchers.IO) {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank() || placeId.isBlank()) return@withContext null

        val endpoint = URL("$supabaseUrl/functions/v1/google-place-details")
        val payload = JSONObject().put("placeId", placeId).toString()
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

            if (BuildConfig.DEBUG) {
                Log.d(PLACES_TAG, "Proxy details response ($code) for placeId=$placeId body=${body.take(800)}")
            }

            if (code !in 200..299 || body.isBlank()) {
                if (code !in 200..299) {
                    Log.e(PLACES_TAG, "Failed to fetch place details from proxy. code=$code placeId=$placeId")
                }
                return@withContext null
            }

            val json = JSONObject(body)
            val resolvedPlaceId = json.optString("placeId").ifBlank { placeId }
            val displayName = json.optString("displayName").orEmpty()
            val formattedAddress = json.optString("formattedAddress").orEmpty()
            val photoNames = mutableListOf<String>()

            val photoNamesArray = json.optJSONArray("photoNames")
            if (photoNamesArray != null) {
                for (index in 0 until photoNamesArray.length()) {
                    val name = photoNamesArray.optString(index).trim()
                    if (name.isNotEmpty()) photoNames += name
                }
            }

            val firstPhotoName = json.optString("photoName").trim()
            if (photoNames.isEmpty() && firstPhotoName.isNotEmpty()) {
                photoNames += firstPhotoName
            }

            val photoUrls = photoNames.map { photoName ->
                val encodedPhotoName = URLEncoder.encode(photoName, Charsets.UTF_8.name())
                "$supabaseUrl/functions/v1/google-place-details?photoName=$encodedPhotoName"
            }
            val photoUrl = photoUrls.firstOrNull()

            if (BuildConfig.DEBUG) {
                Log.d(
                    PLACES_TAG,
                    "Parsed proxy details placeId=$resolvedPlaceId displayName='${displayName.take(40)}' photoCount=${photoUrls.size}"
                )
            }

            return@withContext BusinessPlaceDetails(
                placeId = resolvedPlaceId,
                displayName = displayName,
                formattedAddress = formattedAddress,
                photoUrl = photoUrl,
                photoUrls = photoUrls,
            )
        } finally {
            connection.disconnect()
        }
    }
}
