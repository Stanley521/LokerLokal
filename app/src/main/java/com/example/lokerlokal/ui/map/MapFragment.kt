package com.example.lokerlokal.ui.map

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.lokerlokal.R
import com.example.lokerlokal.data.remote.SupabaseJobsService
import com.example.lokerlokal.util.LocationHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch

class MapFragment : Fragment(R.layout.fragment_map) {

    private data class Region(
        val latitude: Double,
        val longitude: Double,
        val latitudeDelta: Double,
        val longitudeDelta: Double,
    )

    private data class Job(
        val id: Long,
        val title: String,
        val company: String,
        val latitude: Double,
        val longitude: Double,
    )

    private val locationHelper by lazy(LazyThreadSafetyMode.NONE) {
        LocationHelper(requireContext())
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (hasPermission) {
                // User granted permission; retry location + fetch
                initLocation()
            } else {
                setRefreshing(false)
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var readyDelayRunnable: Runnable? = null
    private var markersNotReady = true

    private var region = Region(
        latitude = -6.1751,
        longitude = 106.8273,
        latitudeDelta = 0.05,
        longitudeDelta = 0.05,
    )
    private var jobs: List<Job> = emptyList()
    private var selectedJob: Job? = null
    private var refreshing = false

    private var googleMap: GoogleMap? = null
    private var sheetBehavior: BottomSheetBehavior<View>? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout).apply {
            setOnRefreshListener { onRefresh() }
        }

        // Wire up the activity-level bottom sheet
        val sheetView = requireActivity().findViewById<View>(R.id.bottom_sheet)
        sheetBehavior = BottomSheetBehavior.from(sheetView).also { behavior ->
            behavior.isHideable = true
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            map.setOnMarkerClickListener { marker ->
                val markerJobId = marker.tag as? Long
                val tappedJob = jobs.firstOrNull { it.id == markerJobId }
                tappedJob?.let { handleMarkerPress(it) }
                true
            }

            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(region.latitude, region.longitude),
                    11f,
                )
            )
            // Immediately try to fetch jobs from default region while waiting for location
            lifecycleScope.launch {
                try {
                    val nearbyJobs = SupabaseJobsService.getNearbyJobs(region.latitude, region.longitude)
                    if (nearbyJobs.isNotEmpty()) {
                        onJobsChanged(nearbyJobs.map { Job(it.id, it.title, it.company, it.latitude, it.longitude) })
                    }
                } catch (e: Exception) {
                    // Silently skip initial fetch; user location will be more accurate
                }
            }
            // Then init location to get user's actual position
            initLocation()
        }
    }

    // ===================== LOCATION FUNCTIONS =====================

    private fun initLocation() {
        lifecycleScope.launch {
            try {
                setRefreshing(true)
                if (!locationHelper.isLocationPermissionGranted()) {
                    locationHelper.requestLocationPermission(permissionLauncher)
                    return@launch
                }

                val coords = locationHelper.fetchCurrentLocation()
                region = region.copy(
                    latitude = coords.latitude,
                    longitude = coords.longitude,
                    latitudeDelta = 0.01,
                    longitudeDelta = 0.01,
                )

                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(coords.latitude, coords.longitude), 13f)
                )

                fetchJobs(coords.latitude, coords.longitude)
            } catch (error: Throwable) {
                locationHelper.showLocationError(error) { initLocation() }
                setRefreshing(false)
            }
        }
    }

    // ===================== JOBS FUNCTIONS =====================

    private suspend fun fetchJobs(lat: Double, lng: Double) {
        setRefreshing(true)
        try {
            val fetched = SupabaseJobsService.getNearbyJobs(lat, lng).map {
                Job(
                    id = it.id,
                    title = it.title,
                    company = it.company,
                    latitude = it.latitude,
                    longitude = it.longitude,
                )
            }
            jobs = fetched
            onJobsChanged(fetched)

            if (fetched.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Tidak ada lowongan di sekitar Anda. Silahkan coba lagi nanti.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        } catch (error: Throwable) {
            Toast.makeText(
                requireContext(),
                error.message ?: "Ada kesalahan saat mengambil data pekerjaan.",
                Toast.LENGTH_LONG,
            ).show()
        } finally {
            setRefreshing(false)
        }
    }

    private fun onRefresh() {
        lifecycleScope.launch {
            setRefreshing(true)
            if (region.latitude == 0.0 && region.longitude == 0.0) {
                initLocation()
                return@launch
            }
            fetchJobs(region.latitude, region.longitude)
            setRefreshing(false)
        }
    }

    // ===================== UI FUNCTIONS =====================

    private fun openSheet() {
        val job = selectedJob ?: return
        val activity = requireActivity()
        val sheetView = activity.findViewById<View>(R.id.bottom_sheet)
        sheetView.findViewById<TextView>(R.id.job_title)?.text = job.title
        sheetView.findViewById<TextView>(R.id.job_company)?.text = getString(
            R.string.map_job_company_coordinates,
            job.company,
            job.latitude,
            job.longitude,
        )
        sheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun closeSheet() {
        sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        selectedJob = null
    }

    private fun handleMarkerPress(job: Job) {
        selectedJob = job
        view?.post { openSheet() }
    }

    // ===================== MARKER RENDERING =====================

    private fun onJobsChanged(newJobs: List<Job>) {
        jobs = newJobs
        if (jobs.isEmpty()) {
            renderMarkers()
            return
        }

        markersNotReady = true
        readyDelayRunnable?.let(mainHandler::removeCallbacks)
        readyDelayRunnable = Runnable {
            markersNotReady = false
            renderMarkers()
        }
        mainHandler.postDelayed(readyDelayRunnable!!, 1_000L)
    }

    private fun renderMarkers() {
        val map = googleMap ?: return
        if (markersNotReady) return

        map.clear()
        jobs.forEach { job ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(job.latitude, job.longitude))
                    .title(job.title)
            )
            marker?.tag = job.id
        }

        jobs.firstOrNull()?.let { first ->
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 12f))
        }
    }

    private fun setRefreshing(value: Boolean) {
        refreshing = value
        swipeRefreshLayout?.isRefreshing = value
    }

    override fun onDestroyView() {
        readyDelayRunnable?.let(mainHandler::removeCallbacks)
        readyDelayRunnable = null
        sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        sheetBehavior = null
        swipeRefreshLayout?.setOnRefreshListener(null)
        swipeRefreshLayout = null
        googleMap = null
        super.onDestroyView()
    }
}
