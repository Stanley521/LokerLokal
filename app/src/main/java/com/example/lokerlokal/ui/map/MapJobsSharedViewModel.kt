package com.example.lokerlokal.ui.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class MapJobItem(
    val id: Long,
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
    val businessPlaceId: String,
)

class MapJobsSharedViewModel : ViewModel() {

    private val _jobs = MutableLiveData<List<MapJobItem>>(emptyList())
    val jobs: LiveData<List<MapJobItem>> = _jobs

    fun setJobs(jobs: List<MapJobItem>) {
        _jobs.value = jobs
    }
}

