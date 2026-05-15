package com.example.lokerlokal.ui.dashboard

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lokerlokal.data.auth.SupabaseAuthStore
import com.example.lokerlokal.data.remote.ResumeMeta
import com.example.lokerlokal.data.remote.SupabaseResumeService
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {

    private val _resumeMeta = MutableLiveData<ResumeMeta?>(null)
    val resumeMeta: LiveData<ResumeMeta?> = _resumeMeta

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadResume(context: Context) {
        val session = SupabaseAuthStore.loadSession(context)
        viewModelScope.launch {
            _isLoading.value = true
            _resumeMeta.value = session?.let {
                runCatching { SupabaseResumeService.getLatestResume(it) }.getOrNull()
            }
            _isLoading.value = false
        }
    }

    fun onResumeUploaded(meta: ResumeMeta) {
        _resumeMeta.value = meta
    }

    fun clearResume() {
        _resumeMeta.value = null
    }
}