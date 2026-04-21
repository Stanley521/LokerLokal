package com.example.lokerlokal.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationHelper(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Returns true if ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION is granted.
     * Use this to check permission status before fetching location.
     */
    fun isLocationPermissionGranted(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        Log.d("LocationHelper", "Location permission granted: ${fine || coarse}")
        return fine || coarse
    }

    /**
     * Launch the system location permission dialog.
     * Pass the launcher obtained via registerForActivityResult in your Fragment/Activity.
     */
    fun requestLocationPermission(launcher: ActivityResultLauncher<Array<String>>) {
        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    /**
     * Fetch the device's current location with a 20-second timeout.
     * Falls back to the last known location if the current-location request fails.
     * Throws an exception if neither is available.
     */
    suspend fun fetchCurrentLocation(): Location {
        return try {
            withTimeout(20_000L) {
                getCurrentLocation()
            }
        } catch (error: Exception) {
            // Fallback: try last known location
            Log.d("LocationHelper", "getCurrentLocation failed, trying last known location...")
            val lastLocation = getLastKnownLocation()
            if (lastLocation != null) {
                lastLocation
            } else {
                throw error
            }
        }
    }

    /**
     * Show a Toast with the error message.
     * @param error  The exception that was thrown.
     * @param onRetry  Optional callback — attach to a Snackbar/dialog action if needed.
     */
    fun showLocationError(error: Throwable, onRetry: (() -> Unit)? = null) {
        val message = error.message?.takeIf { it.isNotBlank() }
            ?: "Failed to get current location"
        Log.e("LocationHelper", "Location error: $message", error)

        Toast.makeText(context, "Warning: $message", Toast.LENGTH_LONG).show()

        // If you want a retry button, show a Snackbar instead and pass onRetry.
        // Example (requires a View reference):
        // Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE)
        //     .setAction("Retry") { onRetry?.invoke() }
        //     .show()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private suspend fun getCurrentLocation(): Location =
        suspendCancellableCoroutine { cont ->
            if (!isLocationPermissionGranted()) {
                cont.resumeWithException(SecurityException("Location permission not granted"))
                return@suspendCancellableCoroutine
            }
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .build()

            try {
                getCurrentLocationTask(request)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            cont.resume(location)
                        } else {
                            cont.resumeWithException(Exception("Location unavailable"))
                        }
                    }
                    .addOnFailureListener { e ->
                        cont.resumeWithException(e)
                    }
            } catch (se: SecurityException) {
                cont.resumeWithException(se)
            }
        }

    private suspend fun getLastKnownLocation(): Location? =
        suspendCancellableCoroutine { cont ->
            if (!isLocationPermissionGranted()) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            try {
                getLastLocationTask()
                    .addOnSuccessListener { location -> cont.resume(location) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (_: SecurityException) {
                cont.resume(null)
            }
        }

    // These wrappers are called only after permission checks in the suspend functions above.
    @SuppressLint("MissingPermission")
    private fun getCurrentLocationTask(request: CurrentLocationRequest): Task<Location> =
        fusedClient.getCurrentLocation(request, null)

    @SuppressLint("MissingPermission")
    private fun getLastLocationTask(): Task<Location> =
        fusedClient.lastLocation
}
