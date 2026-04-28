package com.cytsai.urlclean

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cytsai.urlclean.data.FilterRepository
import com.cytsai.urlclean.data.SettingsDataStore
import com.cytsai.urlclean.worker.FilterUpdateWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class SettingsUiState(
    val filterUrl: String = "",
    val autoUpdate: Boolean = false,
    val lastUpdated: Long = 0L,
    val ruleCount: Int = 0,
    val isUpdating: Boolean = false,
    val updateError: String? = null,
)

class MainViewModel(
    application: Application,
    private val repo: FilterRepository,
    private val dataStore: SettingsDataStore,
) : AndroidViewModel(application) {

    private val _updateStatus = MutableStateFlow(Pair(false, null as String?))

    init {
        // Ensure WorkManager reflects the stored auto-update preference on first open.
        // Runs only when MainActivity is active, never from ShareActivity.
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            if (dataStore.autoUpdate.first()) FilterUpdateWorker.schedule(ctx)
        }
    }

    val uiState = combine(
        dataStore.filterUrl,
        dataStore.autoUpdate,
        dataStore.lastUpdated,
        dataStore.ruleCount,
        _updateStatus,
    ) { url, auto, ts, count, (updating, error) ->
        SettingsUiState(
            filterUrl = url,
            autoUpdate = auto,
            lastUpdated = ts,
            ruleCount = count,
            isUpdating = updating,
            updateError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateFilterUrl(url: String) {
        viewModelScope.launch { dataStore.setFilterUrl(url) }
    }

    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setAutoUpdate(enabled)
            val ctx = getApplication<Application>()
            if (enabled) FilterUpdateWorker.schedule(ctx) else FilterUpdateWorker.cancel(ctx)
        }
    }

    fun triggerManualUpdate() {
        if (_updateStatus.value.first) return
        viewModelScope.launch {
            _updateStatus.update { Pair(true, null) }
            val url = uiState.value.filterUrl.ifEmpty { dataStore.filterUrl.first() }
            val result = repo.downloadAndUpdate(url)
            result.fold(
                onSuccess = { count ->
                    dataStore.setLastUpdated(System.currentTimeMillis())
                    dataStore.setRuleCount(count)
                    _updateStatus.update { Pair(false, null) }
                    val app = getApplication<Application>()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            app,
                            app.getString(R.string.toast_update_success, count),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onFailure = { e ->
                    val msg = if (e is IOException) "Network error: ${e.message}" else e.message
                    _updateStatus.update { Pair(false, msg) }
                },
            )
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dataStore = SettingsDataStore(application)
            val repo = FilterRepository(application)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repo, dataStore) as T
        }
    }
}
