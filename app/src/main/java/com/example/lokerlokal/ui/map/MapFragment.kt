package com.example.lokerlokal.ui.map

import android.Manifest
import android.content.res.Configuration
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import android.util.Log
import com.google.android.gms.maps.model.MapStyleOptions
import android.content.res.Resources
import com.example.lokerlokal.BuildConfig
import com.example.lokerlokal.R
import com.example.lokerlokal.data.remote.NearbyJob
import com.example.lokerlokal.data.remote.SupabaseJobsService
import com.example.lokerlokal.util.LocationHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.example.lokerlokal.ui.map.cache.CameraViewport
import com.example.lokerlokal.ui.map.cache.InMemoryTileCacheManager
import com.example.lokerlokal.ui.map.cache.TileBounds

class MapFragment : Fragment(R.layout.fragment_map) {

    companion object {
        private const val TAG = "MapFragment"
        private const val DEFAULT_MAP_ZOOM = 14.5f
        private const val CURRENT_LOCATION_ZOOM = 15f
        const val LOCAL_JOBS_PAGE_SIZE = 5
    }

    private data class Region(
        val latitude: Double,
        val longitude: Double,
        val latitudeDelta: Double,
        val longitudeDelta: Double,
    )

    private enum class PlaceSheetMode {
        NEARBY,
        PLACE,
    }

    private data class SelectedPlaceContext(
        val placeId: String,
        val latitude: Double,
        val longitude: Double,
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
                // Still fetch for default camera position so map isn't empty
                lifecycleScope.launch {
                    fetchJobsForCurrentViewport()
                    fetchLocalJobsForMapCenter()
                }
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

    private var googleMap: GoogleMap? = null
    private var placeSheetBehavior: BottomSheetBehavior<View>? = null
    private var applySheetBehavior: BottomSheetBehavior<View>? = null
    private var applySheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private var sheetNavController: NavController? = null
    private var mapNavView: BottomNavigationView? = null
    private var mapControlsContainer: View? = null
    private var mapLoadingIndicator: View? = null
    private var allowApplySheetHide = false
    private var cameraIdleFetchJob: Job? = null
    private var lastCameraMoveWasGesture = false
    private var placeSheetMode: PlaceSheetMode = PlaceSheetMode.NEARBY
    private var selectedPlaceContext: SelectedPlaceContext? = null
    private var forcePlaceSheetHalfExpanded = false

    private val mapTileCache = InMemoryTileCacheManager<MapJobItem>(
        ttlMs = 60_000L,
        itemKey = { it.id },
        remoteFetcher = { _, bounds -> fetchJobsForTile(bounds) },
    )

    private val backPressCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            hideApplySheet()
        }
    }

    private val placeSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            updateMapControlsPosition()
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            updateMapControlsPosition()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapControlsContainer = view.findViewById(R.id.map_controls_container)
        mapLoadingIndicator = view.findViewById(R.id.map_loading_indicator)

        sharedJobsViewModel.isMapLoading.observe(viewLifecycleOwner) { loading ->
            mapLoadingIndicator?.visibility = if (loading) View.VISIBLE else View.GONE
        }
        view.findViewById<ImageButton>(R.id.button_current_location).setOnClickListener {
            onMapInteractionCollapseApplySheet()
            initLocation()
        }
        view.findViewById<ImageButton>(R.id.button_zoom_in).setOnClickListener {
            onMapInteractionCollapseApplySheet()
            googleMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        view.findViewById<ImageButton>(R.id.button_zoom_out).setOnClickListener {
            onMapInteractionCollapseApplySheet()
            googleMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }

        val mapNavView = view.findViewById<BottomNavigationView>(R.id.map_nav_view)
        this.mapNavView = mapNavView
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            mapNavView.setPadding(
                mapNavView.paddingLeft,
                mapNavView.paddingTop,
                mapNavView.paddingRight,
                systemBarsInsets.bottom,
            )
            placeSheetBehavior?.expandedOffset = systemBarsInsets.top + expandedTopMargin()
            val applySheetView = view.findViewById<View>(R.id.apply_sheet)
            applySheetView.setPadding(
                applySheetView.paddingLeft,
                applySheetView.paddingTop,
                applySheetView.paddingRight,
                systemBarsInsets.bottom,
            )
            applySheetBehavior?.expandedOffset = systemBarsInsets.top + expandedTopMargin()
            updateMapControlsPosition()
            insets
        }

        // Wire up the activity-level bottom sheet
        val placeSheetView = view.findViewById<View>(R.id.place_bottom_sheet)
        placeSheetBehavior = BottomSheetBehavior.from(placeSheetView).also { behavior ->
            behavior.isFitToContents = false
            behavior.expandedOffset = 0
            behavior.halfExpandedRatio = 0.6f
            behavior.skipCollapsed = false
            behavior.isHideable = true
            behavior.peekHeight = homePeekHeight()
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
            behavior.addBottomSheetCallback(placeSheetCallback)
        }
        val currentInsets = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        if (currentInsets != null) {
            placeSheetBehavior?.expandedOffset = currentInsets.top + expandedTopMargin()
        }

        // Wire up the apply (second) bottom sheet
        val applySheetView = view.findViewById<View>(R.id.apply_sheet)
        applySheetView.bringToFront()
        applySheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        if (allowApplySheetHide) {
                            allowApplySheetHide = false
                            backPressCallback.isEnabled = false
                        }

                        if (selectedJobOrNull() == null) {
                            backPressCallback.isEnabled = false
                        }
                    }

                    BottomSheetBehavior.STATE_COLLAPSED,
                    BottomSheetBehavior.STATE_HALF_EXPANDED,
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.STATE_SETTLING,
                    BottomSheetBehavior.STATE_DRAGGING -> {
                        if (selectedJobOrNull() == null) {
                            bottomSheet.post {
                                applySheetBehavior?.isHideable = true
                                applySheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
                            }
                            backPressCallback.isEnabled = false
                            return
                        }
                        applySheetBehavior?.isHideable = false
                        backPressCallback.isEnabled = true
                    }
                }
                updateMapControlsPosition()
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                updateMapControlsPosition()
            }
        }
        applySheetBehavior = BottomSheetBehavior.from(applySheetView).also { behavior ->
            behavior.isFitToContents = false
            behavior.expandedOffset = 0
            behavior.halfExpandedRatio = 0.6f
            behavior.skipCollapsed = false
            behavior.isHideable = true
            behavior.peekHeight = applyPeekHeight()
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
            behavior.addBottomSheetCallback(applySheetCallback!!)
        }
        if (currentInsets != null) {
            applySheetBehavior?.expandedOffset = currentInsets.top + expandedTopMargin()
            applySheetView.setPadding(
                applySheetView.paddingLeft,
                applySheetView.paddingTop,
                applySheetView.paddingRight,
                currentInsets.bottom,
            )
        }

        // Register back press: enabled only when apply sheet is visible
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressCallback)

        val sheetNavHost =
            childFragmentManager.findFragmentById(R.id.place_sheet_nav_host_fragment) as NavHostFragment
        sheetNavController = sheetNavHost.navController
        mapNavView.setupWithNavController(sheetNavController!!)
        mapNavView.setOnItemReselectedListener { item ->
            applyPlaceSheetStateForDestination(item.itemId)
        }
        sheetNavController?.addOnDestinationChangedListener { _, destination, _ ->
            applyPlaceSheetStateForDestination(destination.id)
        }
        placeSheetView.post {
            applyPlaceSheetStateForDestination(sheetNavController?.currentDestination?.id ?: R.id.navigation_local)
            updateMapControlsPosition()
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            applyMapStyleSafely(map)
            updateMyLocationLayer()
            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isZoomGesturesEnabled = true
            map.setOnCameraMoveStartedListener { reason ->
                lastCameraMoveWasGesture = reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    onMapInteractionCollapseApplySheet()
                    collapsePlaceSheetToPeek()
                }
            }
            map.setOnMapClickListener {
                onMapInteractionCollapseApplySheet()
            }
            map.setOnCameraIdleListener {
                cameraIdleFetchJob?.cancel()
                cameraIdleFetchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300L)
                    val shouldFetch = lastCameraMoveWasGesture
                    logDebug("cameraIdle zoom=${map.cameraPosition.zoom} gesture=$shouldFetch")
                    if (shouldFetch) {
                        fetchJobsForCurrentViewport()
                        fetchLocalJobsForMapCenter()
                    } else {
                        logDebug("cameraIdle skip all refetch because move was programmatic")
                    }
                    lastCameraMoveWasGesture = false
                }
            }
            map.setOnMarkerClickListener { marker ->
                val markerPlaceId = marker.tag as? String
                val tappedPlace = jobs.firstOrNull { it.id == markerPlaceId }
                tappedPlace?.let { handlePlaceMarkerPress(it) }
                true
            }

            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(region.latitude, region.longitude),
                    DEFAULT_MAP_ZOOM,
                )
            )

            // Fetch only after location resolves (acquired or denied) so first results are relevant
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

                lastCameraMoveWasGesture = false
                mapTileCache.clearAll()

                val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                    LatLng(coords.latitude, coords.longitude), CURRENT_LOCATION_ZOOM
                )
                googleMap?.animateCamera(cameraUpdate, object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        viewLifecycleOwner.lifecycleScope.launch {
                            fetchJobsForCurrentViewport()
                            fetchLocalJobsForMapCenter()
                        }
                    }
                    override fun onCancel() {
                        // Camera animation cancelled (e.g. user panned); cameraIdle will handle refetch if gesture
                    }
                })
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
                    placeId = it.placeId,
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

    private suspend fun fetchJobsForCurrentViewport() {
        val map = googleMap ?: return
        val bounds = map.projection.visibleRegion.latLngBounds
        val viewport = bounds.toCameraViewport(map.cameraPosition.zoom)
        setMapLoading(true)
        try {
            val diff = mapTileCache.diff(viewport)
            logDebug(
                "viewportFetch start zoom=${viewport.zoom} visibleTiles=${diff.visibleTiles.size} freshTiles=${diff.freshTiles.size} missTiles=${diff.staleOrMissingTiles.size}"
            )

            val fresh = mapTileCache.getFreshVisibleItems(viewport)
            if (fresh.isNotEmpty()) {
                logDebug("viewportFetch cacheRender items=${fresh.size}")
                onJobsChanged(fresh)
            }

            val fetched = runCatching { mapTileCache.fetchMissingTiles(viewport) }
                .getOrElse {
                    logDebug("viewportFetch networkError message=${it.message}")
                    emptyList()
                }
            val merged = (fresh + fetched).distinctBy { it.id }
            logDebug("viewportFetch fetched=${fetched.size} merged=${merged.size}")

            if (merged.isNotEmpty()) {
                onJobsChanged(merged)
            }
        } finally {
            setMapLoading(false)
        }
    }

    private fun currentViewportNearbyRadius(): Int {
        val map = googleMap ?: return 2_000
        val bounds = map.projection.visibleRegion.latLngBounds
        val center = map.cameraPosition.target

        val edgeMidpoints = listOf(
            LatLng(bounds.northeast.latitude, center.longitude),
            LatLng(bounds.southwest.latitude, center.longitude),
            LatLng(center.latitude, bounds.northeast.longitude),
            LatLng(center.latitude, bounds.southwest.longitude),
        )

        val distances = edgeMidpoints.map { edge ->
            val meters = FloatArray(1)
            Location.distanceBetween(
                center.latitude,
                center.longitude,
                edge.latitude,
                edge.longitude,
                meters,
            )
            meters.firstOrNull()?.toDouble() ?: Double.MAX_VALUE
        }

        val radiusMeters = distances.minOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 2_000.0
        val finalRadius = radiusMeters.roundToInt().coerceAtLeast(100)
        logDebug("viewportNearbyRadius center=${center.latitude},${center.longitude} radius=$finalRadius bounds=(${bounds.southwest.latitude},${bounds.southwest.longitude})..(${bounds.northeast.latitude},${bounds.northeast.longitude})")
        return finalRadius
    }

    private suspend fun fetchLocalJobsForMapCenter() {
        val map = googleMap ?: return
        val target = map.cameraPosition.target
        val radius = currentViewportNearbyRadius()

        placeSheetMode = PlaceSheetMode.NEARBY
        selectedPlaceContext = null
        sharedJobsViewModel.setLocalJobsSourceNearby()
        sharedJobsViewModel.resetLocalJobsPagination()
        sharedJobsViewModel.setLocalJobsLoading(true)
        try {
            val fetched = SupabaseJobsService.getNearbyJobs(
                lat = target.latitude,
                lng = target.longitude,
                radius = radius,
                limit = LOCAL_JOBS_PAGE_SIZE,
                offset = 0,
            ).map { it.toMapJobItem(target.latitude, target.longitude) }

            sharedJobsViewModel.setLocalJobs(fetched)
            sharedJobsViewModel.appendLocalJobs(fetched, LOCAL_JOBS_PAGE_SIZE)
            // Re-apply so UI list stays at exactly 'fetched' (appendLocalJobs dedups, no double)
        } catch (error: Throwable) {
            logDebug("localJobsFetch error=${error.message}")
            sharedJobsViewModel.setLocalJobs(emptyList())
        } finally {
            sharedJobsViewModel.setLocalJobsLoading(false)
        }
    }

    private suspend fun fetchLocalJobsForPlace(placeMarker: MapJobItem) {
        val placeId = placeMarker.placeId.trim()
        if (placeId.isBlank()) {
            sharedJobsViewModel.setLocalJobs(emptyList())
            return
        }

        val map = googleMap
        val target = map?.cameraPosition?.target
        val originLat = target?.latitude ?: region.latitude
        val originLng = target?.longitude ?: region.longitude

        placeSheetMode = PlaceSheetMode.PLACE
        selectedPlaceContext = SelectedPlaceContext(
            placeId = placeId,
            latitude = placeMarker.latitude,
            longitude = placeMarker.longitude,
        )
        sharedJobsViewModel.setLocalJobsSourcePlace(placeMarker.businessName)
        sharedJobsViewModel.resetLocalJobsPagination()
        sharedJobsViewModel.setLocalJobsLoading(true)
        try {
            val fetched = SupabaseJobsService.getJobsByPlace(
                placeId = placeId,
                limit = LOCAL_JOBS_PAGE_SIZE,
                offset = 0,
            ).map { job ->
                val resolvedLat = job.latitude.takeUnless { it == 0.0 } ?: placeMarker.latitude
                val resolvedLng = job.longitude.takeUnless { it == 0.0 } ?: placeMarker.longitude
                MapJobItem(
                    id = job.id,
                    title = job.title,
                    businessName = job.businessName,
                    description = job.description,
                    jobType = job.jobType,
                    payText = job.payText,
                    distanceText = formatDistance(originLat, originLng, resolvedLat, resolvedLng),
                    addressText = job.addressText,
                    whatsapp = job.whatsapp,
                    phone = job.phone,
                    expiresAt = job.expiresAt,
                    createdAt = job.createdAt,
                    latitude = resolvedLat,
                    longitude = resolvedLng,
                    placeId = job.placeId.ifBlank { placeId },
                )
            }

            sharedJobsViewModel.setLocalJobs(fetched)
            sharedJobsViewModel.appendLocalJobs(fetched, LOCAL_JOBS_PAGE_SIZE)
        } catch (error: Throwable) {
            logDebug("localJobsByPlaceFetch error=${error.message} placeId=$placeId")
            sharedJobsViewModel.setLocalJobs(emptyList())
        } finally {
            sharedJobsViewModel.setLocalJobsLoading(false)
        }
    }

    fun loadMoreLocalJobs() {
        if (!sharedJobsViewModel.hasMoreLocalJobs) return
        if (sharedJobsViewModel.isLoadingMoreLocalJobs.value == true) return
        val offset = sharedJobsViewModel.localJobsOffset

        viewLifecycleOwner.lifecycleScope.launch {
            sharedJobsViewModel.setLoadingMoreLocalJobs(true)
            try {
                val fetched = when (placeSheetMode) {
                    PlaceSheetMode.NEARBY -> {
                        val map = googleMap ?: return@launch
                        val target = map.cameraPosition.target
                        val radius = currentViewportNearbyRadius()
                        SupabaseJobsService.getNearbyJobs(
                            lat = target.latitude,
                            lng = target.longitude,
                            radius = radius,
                            limit = LOCAL_JOBS_PAGE_SIZE,
                            offset = offset,
                        ).map { it.toMapJobItem(target.latitude, target.longitude) }
                    }

                    PlaceSheetMode.PLACE -> {
                        val placeContext = selectedPlaceContext ?: return@launch
                        val map = googleMap
                        val target = map?.cameraPosition?.target
                        val originLat = target?.latitude ?: region.latitude
                        val originLng = target?.longitude ?: region.longitude
                        SupabaseJobsService.getJobsByPlace(
                            placeId = placeContext.placeId,
                            limit = LOCAL_JOBS_PAGE_SIZE,
                            offset = offset,
                        ).map { job ->
                            val resolvedLat = job.latitude.takeUnless { it == 0.0 } ?: placeContext.latitude
                            val resolvedLng = job.longitude.takeUnless { it == 0.0 } ?: placeContext.longitude
                            MapJobItem(
                                id = job.id,
                                title = job.title,
                                businessName = job.businessName,
                                description = job.description,
                                jobType = job.jobType,
                                payText = job.payText,
                                distanceText = formatDistance(originLat, originLng, resolvedLat, resolvedLng),
                                addressText = job.addressText,
                                whatsapp = job.whatsapp,
                                phone = job.phone,
                                expiresAt = job.expiresAt,
                                createdAt = job.createdAt,
                                latitude = resolvedLat,
                                longitude = resolvedLng,
                                placeId = job.placeId.ifBlank { placeContext.placeId },
                            )
                        }
                    }
                }

                sharedJobsViewModel.appendLocalJobs(fetched, LOCAL_JOBS_PAGE_SIZE)
            } catch (error: Throwable) {
                logDebug("loadMoreLocalJobs error=${error.message}")
            } finally {
                sharedJobsViewModel.setLoadingMoreLocalJobs(false)
            }
        }
    }

    private suspend fun fetchJobsForTile(bounds: TileBounds): List<MapJobItem> {
        logDebug(
            "tileFetch bounds=(${bounds.minLat},${bounds.minLng})..(${bounds.maxLat},${bounds.maxLng})"
        )

        val nearbyJobs = SupabaseJobsService.getPlaceMapMarkersInBounds(
            minLat = bounds.minLat,
            minLng = bounds.minLng,
            maxLat = bounds.maxLat,
            maxLng = bounds.maxLng,
        )
        val result = nearbyJobs
            .filter { it.latitude in bounds.minLat..bounds.maxLat && it.longitude in bounds.minLng..bounds.maxLng }
            .map { it.toMapJobItem(region.latitude, region.longitude) }
        logDebug("tileFetch response nearby=${nearbyJobs.size} inTile=${result.size}")
        return result
    }


    private fun NearbyJob.toMapJobItem(distanceOriginLat: Double, distanceOriginLng: Double): MapJobItem {
        return MapJobItem(
            id = id,
            title = title,
            businessName = businessName,
            description = description,
            jobType = jobType,
            payText = payText,
            distanceText = formatDistance(distanceOriginLat, distanceOriginLng, latitude, longitude),
            addressText = addressText,
            whatsapp = whatsapp,
            phone = phone,
            expiresAt = expiresAt,
            createdAt = createdAt,
            latitude = latitude,
            longitude = longitude,
            placeId = placeId,
        )
    }

    private fun LatLngBounds.toCameraViewport(zoom: Float): CameraViewport {
        return CameraViewport(
            zoom = zoom,
            minLat = southwest.latitude,
            minLng = southwest.longitude,
            maxLat = northeast.latitude,
            maxLng = northeast.longitude,
        )
    }

    // ===================== UI FUNCTIONS =====================

    private fun openPlaceSheet() {
        view?.post {
            applyPlaceSheetStateForDestination(sheetNavController?.currentDestination?.id ?: R.id.navigation_local)
        }
    }

    private fun handlePlaceMarkerPress(placeMarker: MapJobItem) {
        hideApplySheet()
        placeSheetMode = PlaceSheetMode.PLACE
        forcePlaceSheetHalfExpanded = true
        mapNavView?.selectedItemId = R.id.navigation_local
        viewLifecycleOwner.lifecycleScope.launch {
            fetchLocalJobsForPlace(placeMarker)
        }
        view?.post {
            openPlaceSheet()
            placeSheetBehavior?.peekHeight = homePeekHeight()
            placeSheetBehavior?.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            forcePlaceSheetHalfExpanded = false
        }
    }

    private fun openJobApplySheet(job: MapJobItem) {
        sharedJobsViewModel.selectJob(job)
        if (childFragmentManager.findFragmentByTag(JobApplyBottomSheetFragment.TAG) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.apply_sheet_container, JobApplyBottomSheetFragment(), JobApplyBottomSheetFragment.TAG)
                .commit()
        }
        val insetsTop = ViewCompat.getRootWindowInsets(requireView())
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
            ?.top
        allowApplySheetHide = false
        applySheetBehavior?.peekHeight = applyPeekHeight()
        if (insetsTop != null) {
            applySheetBehavior?.expandedOffset = insetsTop + expandedTopMargin()
        }
        applySheetBehavior?.isHideable = false
        applySheetBehavior?.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        backPressCallback.isEnabled = true
        view?.post { openPlaceSheet() }
    }

    fun openApplySheetForJob(job: MapJobItem) {
        lastCameraMoveWasGesture = false
        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(job.latitude, job.longitude), CURRENT_LOCATION_ZOOM)
        )
        openJobApplySheet(job)
    }

    fun focusMapOnJob(job: MapJobItem) {
        val target = LatLng(job.latitude, job.longitude)
        lastCameraMoveWasGesture = false
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, CURRENT_LOCATION_ZOOM))

        placeSheetBehavior?.let { behavior ->
            behavior.peekHeight = homePeekHeight()
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        collapseApplySheetToPeek(showHint = false)
    }

    fun hideApplySheet() {
        val behavior = applySheetBehavior ?: return
        sharedJobsViewModel.clearSelectedJob()
        backPressCallback.isEnabled = false
        if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) return
        allowApplySheetHide = true
        behavior.isHideable = true
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun onMapInteractionCollapseApplySheet() {
        collapseApplySheetToPeek(showHint = true)
    }

    private fun collapsePlaceSheetToPeek() {
        val behavior = placeSheetBehavior ?: return
        if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) return
        if (behavior.state == BottomSheetBehavior.STATE_COLLAPSED) return
        behavior.peekHeight = homePeekHeight()
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun collapseApplySheetToPeek(showHint: Boolean = false) {
        val behavior = applySheetBehavior ?: return
        if (selectedJobOrNull() == null) return
        if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) return
        if (behavior.state == BottomSheetBehavior.STATE_COLLAPSED) return
        if (showHint) showApplySheetAutoCollapseHint()
        allowApplySheetHide = false
        behavior.isHideable = false
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun showApplySheetAutoCollapseHint() {
        val applyFragment = childFragmentManager.findFragmentByTag(JobApplyBottomSheetFragment.TAG) ?: return
        val handle = applyFragment.view?.findViewById<View>(R.id.apply_drag_handle) ?: return
        handle.animate().cancel()
        handle.animate()
            .scaleX(1.18f)
            .scaleY(1.18f)
            .alpha(0.7f)
            .setDuration(90L)
            .withEndAction {
                handle.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(120L)
                    .start()
            }
            .start()
    }

    internal fun selectJobForTest(job: MapJobItem) {
        openJobApplySheet(job)
    }

    internal fun simulateMapInteractionForTest() {
        onMapInteractionCollapseApplySheet()
    }

    internal fun expandApplySheetForTest() {
        applySheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    internal fun applySheetStateForTest(): Int =
        applySheetBehavior?.state ?: BottomSheetBehavior.STATE_HIDDEN

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

        // Keep user camera position while panning; recenter only from explicit actions.
    }

    private fun setRefreshing(@Suppress("UNUSED_PARAMETER") value: Boolean) {
        // No pull-to-refresh UI on the map screen.
    }

    private fun applyPlaceSheetStateForDestination(destinationId: Int) {
        val behavior = placeSheetBehavior ?: return
        when (destinationId) {
            R.id.navigation_local -> {
                behavior.peekHeight = homePeekHeight()
                behavior.state = if (forcePlaceSheetHalfExpanded || placeSheetMode == PlaceSheetMode.PLACE) {
                    forcePlaceSheetHalfExpanded = false
                    BottomSheetBehavior.STATE_HALF_EXPANDED
                } else {
                    BottomSheetBehavior.STATE_COLLAPSED
                }
            }

            R.id.navigation_dashboard,
            R.id.navigation_notifications -> {
                behavior.peekHeight = 0
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun homePeekHeight(): Int = (220f * resources.displayMetrics.density).roundToInt()

    private fun applyPeekHeight(): Int = homePeekHeight()

    private fun selectedJobOrNull(): MapJobItem? = sharedJobsViewModel.selectedJob.value

    private fun formatDistance(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): String {
        val meters = FloatArray(1)
        Location.distanceBetween(fromLat, fromLng, toLat, toLng, meters)
        val value = meters.firstOrNull() ?: return "-"
        return if (value < 1000f) {
            "${value.roundToInt()} m"
        } else {
            String.format(Locale.US, "%.1f km", value / 1000f)
        }
    }

    private fun expandedTopMargin(): Int = (16f * resources.displayMetrics.density).roundToInt()

    private fun updateMapControlsPosition() {
        val rootView = view ?: return
        val applyView = rootView.findViewById<View>(R.id.apply_sheet)
        val applyBehavior = applySheetBehavior
        if (applyBehavior != null && applyBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            updateMapControlsPosition(applyView.top, applyBehavior)
            return
        }

        val mainSheetView = rootView.findViewById<View>(R.id.place_bottom_sheet)
        val mainBehavior = placeSheetBehavior ?: return
        updateMapControlsPosition(mainSheetView.top, mainBehavior)
    }

    private fun updateMapControlsPosition(sheetTop: Int, behavior: BottomSheetBehavior<View>) {
        val rootView = view ?: return
        val controls = mapControlsContainer ?: return
        val navView = mapNavView ?: return
        controls.post {
            val controlsHeight = controls.height
            if (controlsHeight == 0) return@post

            val margin = (16f * resources.displayMetrics.density).roundToInt()
            val rootHeight = rootView.height
            if (rootHeight == 0) return@post

            val navTop = navView.top.takeIf { it > 0 } ?: (rootHeight - navView.height)
            val hiddenAnchorTop = navTop - controlsHeight - margin

            val halfExpandedTop = (rootHeight * (1f - behavior.halfExpandedRatio)).roundToInt()
            val desiredTop = sheetTop - controlsHeight - margin
            val lockedTop = halfExpandedTop - controlsHeight - margin
            val minTop = min(lockedTop, hiddenAnchorTop)
            val maxTop = max(lockedTop, hiddenAnchorTop)
            controls.y = desiredTop.coerceIn(minTop, maxTop).toFloat()
        }
    }

    private fun updateMyLocationLayer() {
        val map = googleMap ?: return
        val hasPermission = locationHelper.isLocationPermissionGranted()
        try {
            map.isMyLocationEnabled = hasPermission
            map.uiSettings.isMyLocationButtonEnabled = false
        } catch (_: SecurityException) {
            map.isMyLocationEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = false
        }
    }

    private fun setMapLoading(loading: Boolean) {
        sharedJobsViewModel.setMapLoading(loading)
    }

    private fun applyMapStyleSafely(map: GoogleMap) {
        val uiModeMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val preferredStyle = if (uiModeMask == Configuration.UI_MODE_NIGHT_YES) {
            R.raw.map_style_tint_night
        } else {
            R.raw.map_style_tint_day
        }

        try {
            val applied = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(requireContext(), preferredStyle)
            )
            if (!applied) {
                logDebug("mapStyle apply failed for preferred style=$preferredStyle; trying fallback")
                applyFallbackMapStyle(map)
            }
        } catch (_: Resources.NotFoundException) {
            logDebug("mapStyle preferred resource not found style=$preferredStyle; trying fallback")
            applyFallbackMapStyle(map)
        } catch (error: Exception) {
            logDebug("mapStyle preferred parse/apply error=${error.message}; trying fallback")
            applyFallbackMapStyle(map)
        }
    }

    private fun applyFallbackMapStyle(map: GoogleMap) {
        try {
            val fallbackApplied = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_tint)
            )
            if (!fallbackApplied) {
                logDebug("mapStyle fallback apply failed; using Google default style")
            }
        } catch (_: Resources.NotFoundException) {
            logDebug("mapStyle fallback resource not found; using Google default style")
        } catch (error: Exception) {
            logDebug("mapStyle fallback parse/apply error=${error.message}; using Google default style")
        }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    override fun onDestroyView() {
        cameraIdleFetchJob?.cancel()
        cameraIdleFetchJob = null
        mapTileCache.clearAll()
        readyDelayRunnable?.let(mainHandler::removeCallbacks)
        readyDelayRunnable = null
        placeSheetBehavior?.removeBottomSheetCallback(placeSheetCallback)
        placeSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        placeSheetBehavior = null
        applySheetCallback?.let { callback ->
            applySheetBehavior?.removeBottomSheetCallback(callback)
        }
        applySheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        applySheetBehavior = null
        applySheetCallback = null
        allowApplySheetHide = false
        sheetNavController = null
        mapNavView = null
        mapControlsContainer = null
        mapLoadingIndicator = null
        googleMap = null
        super.onDestroyView()
    }
}
