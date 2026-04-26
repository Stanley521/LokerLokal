package com.example.lokerlokal.ui.map

import android.Manifest
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.lokerlokal.R
import com.example.lokerlokal.data.remote.SupabaseJobsService
import com.example.lokerlokal.util.LocationHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MapFragment : Fragment(R.layout.fragment_map) {

    private data class Region(
        val latitude: Double,
        val longitude: Double,
        val latitudeDelta: Double,
        val longitudeDelta: Double,
    )

    private val locationHelper by lazy(LazyThreadSafetyMode.NONE) {
        LocationHelper(requireContext())
    }

    private val sharedJobsViewModel: MapJobsSharedViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (hasPermission) {
                // User granted permission; retry location + fetch
                updateMyLocationLayer()
                initLocation()
            } else {
                updateMyLocationLayer()
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
    private var jobs: List<MapJobItem> = emptyList()
    private var refreshing = false

    private var googleMap: GoogleMap? = null
    private var sheetBehavior: BottomSheetBehavior<View>? = null
    private var sheetNavController: NavController? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout).apply {
            setOnRefreshListener { onRefresh() }
        }

        val mapNavView = view.findViewById<BottomNavigationView>(R.id.map_nav_view)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            mapNavView.setPadding(
                mapNavView.paddingLeft,
                mapNavView.paddingTop,
                mapNavView.paddingRight,
                systemBarsInsets.bottom,
            )
            sheetBehavior?.expandedOffset = systemBarsInsets.top + expandedTopMargin()
            insets
        }

        // Wire up the activity-level bottom sheet
        val sheetView = view.findViewById<View>(R.id.bottom_sheet)
        sheetBehavior = BottomSheetBehavior.from(sheetView).also { behavior ->
            behavior.isFitToContents = false
            behavior.expandedOffset = 0
            behavior.halfExpandedRatio = 0.6f
            behavior.skipCollapsed = false
            behavior.isHideable = true
            behavior.peekHeight = homePeekHeight()
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        val currentInsets = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        if (currentInsets != null) {
            sheetBehavior?.expandedOffset = currentInsets.top + expandedTopMargin()
        }

        val sheetNavHost =
            childFragmentManager.findFragmentById(R.id.sheet_nav_host_fragment) as NavHostFragment
        sheetNavController = sheetNavHost.navController
        mapNavView.setupWithNavController(sheetNavController!!)
        mapNavView.setOnItemReselectedListener { item ->
            applySheetStateForDestination(item.itemId)
        }
        sheetNavController?.addOnDestinationChangedListener { _, destination, _ ->
            applySheetStateForDestination(destination.id)
        }
        sheetView.post {
            applySheetStateForDestination(sheetNavController?.currentDestination?.id ?: R.id.navigation_local)
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            updateMyLocationLayer()
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
                        onJobsChanged(
                            nearbyJobs.map {
                                MapJobItem(
                                    id = it.id,
                                    title = it.title,
                                    businessName = it.businessName,
                                    description = it.description,
                                    jobType = it.jobType,
                                    payText = it.payText,
                                    distanceText = formatDistance(
                                        region.latitude,
                                        region.longitude,
                                        it.latitude,
                                        it.longitude,
                                    ),
                                    addressText = it.addressText,
                                    whatsapp = it.whatsapp,
                                    phone = it.phone,
                                    expiresAt = it.expiresAt,
                                    createdAt = it.createdAt,
                                    latitude = it.latitude,
                                    longitude = it.longitude,
                                )
                            }
                        )
                    }
                } catch (_: Exception) {
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
                updateMyLocationLayer()
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
                MapJobItem(
                    id = it.id,
                    title = it.title,
                    businessName = it.businessName,
                    description = it.description,
                    jobType = it.jobType,
                    payText = it.payText,
                    distanceText = formatDistance(lat, lng, it.latitude, it.longitude),
                    addressText = it.addressText,
                    whatsapp = it.whatsapp,
                    phone = it.phone,
                    expiresAt = it.expiresAt,
                    createdAt = it.createdAt,
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
        view?.post {
            applySheetStateForDestination(sheetNavController?.currentDestination?.id ?: R.id.navigation_local)
        }
    }

    private fun handleMarkerPress(@Suppress("UNUSED_PARAMETER") job: MapJobItem) {
        view?.post { openSheet() }
    }

    // ===================== MARKER RENDERING =====================

    private fun onJobsChanged(newJobs: List<MapJobItem>) {
        jobs = newJobs
        sharedJobsViewModel.setJobs(newJobs)
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

    private fun applySheetStateForDestination(destinationId: Int) {
        val behavior = sheetBehavior ?: return
        when (destinationId) {
            R.id.navigation_local -> {
                behavior.peekHeight = homePeekHeight()
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }

            R.id.navigation_dashboard,
            R.id.navigation_notifications -> {
                behavior.peekHeight = 0
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun homePeekHeight(): Int = (220f * resources.displayMetrics.density).roundToInt()

    private fun formatDistance(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): String {
        val meters = FloatArray(1)
        Location.distanceBetween(fromLat, fromLng, toLat, toLng, meters)
        val value = meters.firstOrNull() ?: return "-"
        return if (value < 1000f) {
            "${value.roundToInt()} m"
        } else {
            String.format("%.1f km", value / 1000f)
        }
    }

    private fun expandedTopMargin(): Int = (16f * resources.displayMetrics.density).roundToInt()

    private fun updateMyLocationLayer() {
        val map = googleMap ?: return
        val hasPermission = locationHelper.isLocationPermissionGranted()
        try {
            map.isMyLocationEnabled = hasPermission
            map.uiSettings.isMyLocationButtonEnabled = hasPermission
        } catch (_: SecurityException) {
            map.isMyLocationEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = false
        }
    }

    override fun onDestroyView() {
        readyDelayRunnable?.let(mainHandler::removeCallbacks)
        readyDelayRunnable = null
        sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        sheetBehavior = null
        sheetNavController = null
        swipeRefreshLayout?.setOnRefreshListener(null)
        swipeRefreshLayout = null
        googleMap = null
        super.onDestroyView()
    }
}
