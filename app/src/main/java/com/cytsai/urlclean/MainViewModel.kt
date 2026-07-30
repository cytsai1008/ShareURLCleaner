package com.cytsai.urlclean

import android.annotation.SuppressLint
import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cytsai.urlclean.data.AggressiveMode
import com.cytsai.urlclean.data.FilterRepository
import com.cytsai.urlclean.data.FilterSource
import com.cytsai.urlclean.data.SettingsDataStore
import com.cytsai.urlclean.worker.FilterUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class SettingsUiState(
    val sources: List<FilterSource> = emptyList(),
    val autoUpdate: Boolean = false,
    val lastUpdated: Long = 0L,
    val ruleCount: Int = 0,
    val aggressiveMode: AggressiveMode = AggressiveMode.OFF,
    val aggressiveDomains: String = "",
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

    // combine() tops out at five flows, so the filter-related ones are folded first.
    private val filterState = combine(
        dataStore.filterSources,
        dataStore.autoUpdate,
        dataStore.lastUpdated,
        dataStore.ruleCount,
    ) { sources, auto, ts, count ->
        SettingsUiState(sources = sources, autoUpdate = auto, lastUpdated = ts, ruleCount = count)
    }

    val uiState = combine(
        filterState,
        dataStore.aggressiveMode,
        dataStore.aggressiveDomains,
        _updateStatus,
    ) { base, mode, domains, (updating, error) ->
        base.copy(
            aggressiveMode = mode,
            aggressiveDomains = domains,
            isUpdating = updating,
            updateError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setSourceEnabled(url: String, enabled: Boolean) {
        viewModelScope.launch {
            val updated = dataStore.filterSources.first()
                .map { if (it.url == url) it.copy(enabled = enabled) else it }
            dataStore.setFilterSources(updated)
        }
    }

    fun addSource(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isEmpty()) return
        viewModelScope.launch {
            val current = dataStore.filterSources.first()
            if (current.any { it.url == url }) return@launch
            dataStore.setFilterSources(current + FilterSource(url, enabled = true))
        }
    }

    fun removeSource(url: String) {
        if (SettingsDataStore.isBuiltIn(url)) return
        viewModelScope.launch {
            dataStore.setFilterSources(dataStore.filterSources.first().filterNot { it.url == url })
        }
    }

    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setAutoUpdate(enabled)
            val ctx = getApplication<Application>()
            if (enabled) FilterUpdateWorker.schedule(ctx) else FilterUpdateWorker.cancel(ctx)
        }
    }

    fun setAggressiveMode(mode: AggressiveMode) {
        viewModelScope.launch { dataStore.setAggressiveMode(mode) }
    }

    fun updateAggressiveDomains(raw: String) {
        viewModelScope.launch { dataStore.setAggressiveDomains(raw) }
    }

    @SuppressLint("StringFormatInvalid")
    fun triggerManualUpdate() {
        if (_updateStatus.value.first) return
        viewModelScope.launch {
            _updateStatus.update { Pair(true, null) }
            val urls = dataStore.filterSources.first().filter { it.enabled }.map { it.url }
            val result = repo.downloadAndUpdate(urls)
            result.fold(
                onSuccess = { count ->
                    dataStore.setLastUpdated(System.currentTimeMillis())
                    dataStore.setRuleCount(count)
                    _updateStatus.update { Pair(false, null) }
                    val app = getApplication<Application>()
                    val msg = app.getString(R.string.toast_update_success, count)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { e ->
                    // Only the prefix is ours to translate; the rest is the exception's own text.
                    val msg = if (e is IOException) {
                        getApplication<Application>().getString(
                            R.string.error_network,
                            e.message.orEmpty()
                        )
                    } else {
                        e.message
                    }
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
