package com.example.lokerlokal.ui.dashboard

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lokerlokal.data.remote.ResumeMeta
import com.example.lokerlokal.data.remote.SupabaseResumeService
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {

    private val _resumeMeta = MutableLiveData<ResumeMeta?>(null)
    val resumeMeta: LiveData<ResumeMeta?> = _resumeMeta

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadResume(context: Context) {
        val userKey = resolveUserKey(context)
        viewModelScope.launch {
            _isLoading.value = true
            _resumeMeta.value = runCatching { SupabaseResumeService.getLatestResume(userKey) }.getOrNull()
            _isLoading.value = false
        }
    }

    fun onResumeUploaded(meta: ResumeMeta) {
        _resumeMeta.value = meta
    }

    private fun resolveUserKey(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId?.takeIf { it.isNotBlank() } ?: "anonymous-user"
    }
}