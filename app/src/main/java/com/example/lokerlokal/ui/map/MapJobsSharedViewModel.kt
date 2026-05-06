package com.example.lokerlokal.ui.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class MapJobItem(
    val id: String,
    val title: String,
    val businessName: String,
    val description: String,
    val jobType: String,
    val payText: String,
    val distanceText: String,
    val addressText: String,
    val whatsapp: String,
    val phone: String,
    val expiresAt: String,
    val createdAt: String,
    val latitude: Double,
    val longitude: Double,
    val placeId: String,
)

class MapJobsSharedViewModel : ViewModel() {

    enum class LocalJobsSource {
        NEARBY,
        PLACE,
    }

    private val _jobs = MutableLiveData<List<MapJobItem>>(emptyList())
    val jobs: LiveData<List<MapJobItem>> = _jobs

    private val _localJobs = MutableLiveData<List<MapJobItem>>(emptyList())
    val localJobs: LiveData<List<MapJobItem>> = _localJobs

    private val _isMapLoading = MutableLiveData(false)
    val isMapLoading: LiveData<Boolean> = _isMapLoading

    private val _isLocalJobsLoading = MutableLiveData(false)
    val isLocalJobsLoading: LiveData<Boolean> = _isLocalJobsLoading

    private val _isLoadingMoreLocalJobs = MutableLiveData(false)
    val isLoadingMoreLocalJobs: LiveData<Boolean> = _isLoadingMoreLocalJobs

    private val _localJobsSource = MutableLiveData(LocalJobsSource.NEARBY)
    val localJobsSource: LiveData<LocalJobsSource> = _localJobsSource

    private val _selectedPlaceName = MutableLiveData("")
    val selectedPlaceName: LiveData<String> = _selectedPlaceName

    /** True when there are more pages to load for local jobs. */
    var hasMoreLocalJobs: Boolean = true
        private set

    /** Current pagination offset for local jobs. */
    var localJobsOffset: Int = 0
        private set

    private val _selectedJob = MutableLiveData<MapJobItem?>(null)
    val selectedJob: LiveData<MapJobItem?> = _selectedJob

    fun setJobs(jobs: List<MapJobItem>) {
        _jobs.value = jobs
    }

    fun setLocalJobs(jobs: List<MapJobItem>) {
        _localJobs.value = jobs
    }

    fun appendLocalJobs(newJobs: List<MapJobItem>, pageSize: Int) {
        val combined = (_localJobs.value.orEmpty() + newJobs).distinctBy { it.id }
        _localJobs.value = combined
        localJobsOffset = combined.size
        hasMoreLocalJobs = newJobs.size >= pageSize
    }

    fun resetLocalJobsPagination() {
        localJobsOffset = 0
        hasMoreLocalJobs = true
    }

    fun setLoadingMoreLocalJobs(loading: Boolean) {
        _isLoadingMoreLocalJobs.value = loading
    }

    fun setLocalJobsSourceNearby() {
        _localJobsSource.value = LocalJobsSource.NEARBY
        _selectedPlaceName.value = ""
    }

    fun setLocalJobsSourcePlace(placeName: String) {
        _localJobsSource.value = LocalJobsSource.PLACE
        _selectedPlaceName.value = placeName
    }

    fun setMapLoading(loading: Boolean) {
        _isMapLoading.value = loading
    }

    fun setLocalJobsLoading(loading: Boolean) {
        _isLocalJobsLoading.value = loading
    }

    fun selectJob(job: MapJobItem) {
        _selectedJob.value = job
    }

    fun clearSelectedJob() {
        _selectedJob.value = null
    }
}

