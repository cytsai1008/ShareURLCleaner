package com.cytsai.urlclean

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cytsai.urlclean.data.SettingsDataStore
import com.cytsai.urlclean.ui.theme.ShareURLCleanerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(application) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ShareURLCleaner)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareURLCleanerTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.title_settings)) })
                    },
                ) { innerPadding ->
                    SettingsScreen(
                        uiState = uiState,
                        onSaveUrl = viewModel::updateFilterUrl,
                        onUpdateNow = viewModel::triggerManualUpdate,
                        onAutoUpdateToggle = viewModel::setAutoUpdate,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    onSaveUrl: (String) -> Unit,
    onUpdateNow: () -> Unit,
    onAutoUpdateToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var localUrl by rememberSaveable { mutableStateOf("") }
    var urlInitialized by remember { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.filterUrl) {
        if (!urlInitialized && uiState.filterUrl.isNotEmpty()) {
            localUrl = uiState.filterUrl
            urlInitialized = true
        }
    }

    val urlDirty = localUrl != uiState.filterUrl

    fun saveUrl() {
        if (urlDirty) {
            onSaveUrl(localUrl)
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = urlDirty) { saveUrl() }

    val neverLabel = stringResource(R.string.status_never)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = localUrl,
                onValueChange = { localUrl = it },
                label = { Text(stringResource(R.string.label_filter_url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveUrl() }),
                trailingIcon = {
                    if (urlDirty) {
                        IconButton(onClick = { saveUrl() }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.cd_save_url),
                            )
                        }
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        localUrl = SettingsDataStore.DEFAULT_FILTER_URL
                        onSaveUrl(SettingsDataStore.DEFAULT_FILTER_URL)
                        focusManager.clearFocus()
                    },
                    enabled = localUrl != SettingsDataStore.DEFAULT_FILTER_URL,
                ) {
                    Text(stringResource(R.string.btn_reset_filter_url))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = onUpdateNow,
                enabled = !uiState.isUpdating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_updating))
                } else {
                    Text(stringResource(R.string.btn_update_now))
                }
            }
            if (uiState.updateError != null) {
                Text(
                    text = uiState.updateError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_auto_update),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.desc_auto_update),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = uiState.autoUpdate,
                onCheckedChange = onAutoUpdateToggle,
            )
        }

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(
                    R.string.status_last_updated,
                    formatTimestamp(uiState.lastUpdated, neverLabel),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.status_rules_loaded, uiState.ruleCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        HorizontalDivider()

        TextButton(
            onClick = { showLicenses = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_third_party_licenses))
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showLicenses) {
        ThirdPartyLicensesDialog(onDismiss = { showLicenses = false })
    }
}

@Composable
private fun ThirdPartyLicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        },
        title = { Text(stringResource(R.string.title_third_party_licenses)) },
        text = {
            Text(
                text = thirdPartyLicensesText(),
                modifier = Modifier.verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}

private fun thirdPartyLicensesText(): String = listOf(
    "AndroidX Core KTX - Apache License 2.0",
    "AndroidX Activity Compose - Apache License 2.0",
    "AndroidX Lifecycle - Apache License 2.0",
    "AndroidX DataStore - Apache License 2.0",
    "AndroidX WorkManager - Apache License 2.0",
    "Jetpack Compose UI - Apache License 2.0",
    "Jetpack Compose Material 3 - Apache License 2.0",
    "Jetpack Compose Material Icons - Apache License 2.0",
    "Kotlin - Apache License 2.0",
    "OkHttp - Apache License 2.0",
    "Phosphor Icons - MIT License",
).joinToString(separator = "\n\n")

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatTimestamp(ts: Long, never: String): String {
    if (ts == 0L) return never
    return Instant.ofEpochMilli(ts)
        .atZone(ZoneId.systemDefault())
        .format(timestampFormatter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Settings — idle")
@Composable
private fun PreviewSettingsIdle() {
    ShareURLCleanerTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.title_settings)) }) },
        ) { innerPadding ->
            SettingsScreen(
                uiState = SettingsUiState(
                    filterUrl = "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_17_TrackParam/filter.txt",
                    autoUpdate = false,
                    lastUpdated = 0L,
                    ruleCount = 0,
                ),
                onSaveUrl = {},
                onUpdateNow = {},
                onAutoUpdateToggle = {},
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Settings — with data")
@Composable
private fun PreviewSettingsWithData() {
    ShareURLCleanerTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.title_settings)) }) },
        ) { innerPadding ->
            SettingsScreen(
                uiState = SettingsUiState(
                    filterUrl = "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_17_TrackParam/filter.txt",
                    autoUpdate = true,
                    lastUpdated = 1_700_000_000_000L,
                    ruleCount = 3_241,
                ),
                onSaveUrl = {},
                onUpdateNow = {},
                onAutoUpdateToggle = {},
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Settings — updating")
@Composable
private fun PreviewSettingsUpdating() {
    ShareURLCleanerTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.title_settings)) }) },
        ) { innerPadding ->
            SettingsScreen(
                uiState = SettingsUiState(
                    filterUrl = "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_17_TrackParam/filter.txt",
                    autoUpdate = true,
                    lastUpdated = 1_700_000_000_000L,
                    ruleCount = 3_241,
                    isUpdating = true,
                ),
                onSaveUrl = {},
                onUpdateNow = {},
                onAutoUpdateToggle = {},
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Settings — error")
@Composable
private fun PreviewSettingsError() {
    ShareURLCleanerTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.title_settings)) }) },
        ) { innerPadding ->
            SettingsScreen(
                uiState = SettingsUiState(
                    filterUrl = "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_17_TrackParam/filter.txt",
                    autoUpdate = false,
                    lastUpdated = 0L,
                    ruleCount = 0,
                    updateError = "Network error: Unable to resolve host",
                ),
                onSaveUrl = {},
                onUpdateNow = {},
                onAutoUpdateToggle = {},
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
