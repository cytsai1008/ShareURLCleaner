package com.cytsai.urlclean

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cytsai.urlclean.data.AggressiveMode
import com.cytsai.urlclean.data.FilterSource
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
                    // Bottom inset is handled inside SettingsScreen so it can collapse into
                    // the IME inset instead of stacking with it.
                    contentWindowInsets = WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ) { innerPadding ->
                    SettingsScreen(
                        uiState = uiState,
                        onUpdateNow = viewModel::triggerManualUpdate,
                        onAutoUpdateToggle = viewModel::setAutoUpdate,
                        onSourceToggle = viewModel::setSourceEnabled,
                        onAddSource = viewModel::addSource,
                        onRemoveSource = viewModel::removeSource,
                        onAggressiveModeChange = viewModel::setAggressiveMode,
                        onSaveAggressiveDomains = viewModel::updateAggressiveDomains,
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
    onUpdateNow: () -> Unit,
    onAutoUpdateToggle: (Boolean) -> Unit,
    onSourceToggle: (String, Boolean) -> Unit = { _, _ -> },
    onAddSource: (String) -> Unit = {},
    onRemoveSource: (String) -> Unit = {},
    onAggressiveModeChange: (AggressiveMode) -> Unit = {},
    onSaveAggressiveDomains: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var localDomains by rememberSaveable { mutableStateOf("") }
    var domainsInitialized by remember { mutableStateOf(false) }
    var domainsExpanded by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showAddFilter by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.aggressiveDomains) {
        if (!domainsInitialized && uiState.aggressiveDomains.isNotEmpty()) {
            localDomains = uiState.aggressiveDomains
            domainsInitialized = true
        }
    }

    val domainsDirty = localDomains != uiState.aggressiveDomains

    fun saveDomains() {
        if (domainsDirty) {
            onSaveAggressiveDomains(localDomains)
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = domainsDirty) { saveDomains() }

    val neverLabel = stringResource(R.string.status_never)

    Column(
        modifier = modifier
            .fillMaxSize()
            // navigationBarsPadding consumes its inset, so imePadding only adds what the
            // keyboard needs beyond it — total is max(navbar, ime), not the sum. Both go
            // before verticalScroll so the viewport shrinks and the focused field is
            // auto-scrolled into view.
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.label_filter_lists),
                style = MaterialTheme.typography.bodyLarge,
            )
            uiState.sources.forEach { source ->
                FilterSourceRow(
                    source = source,
                    onToggle = { onSourceToggle(source.url, it) },
                    onRemove = { onRemoveSource(source.url) },
                )
            }
            TextButton(onClick = { showAddFilter = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_add_filter))
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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.label_aggressive),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.desc_aggressive),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AggressiveMode.entries.forEach { mode ->
                AggressiveModeCard(
                    mode = mode,
                    selected = uiState.aggressiveMode == mode,
                    onClick = { onAggressiveModeChange(mode) },
                )
            }

            AnimatedVisibility(visible = uiState.aggressiveMode == AggressiveMode.SELECTED) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { domainsExpanded = !domainsExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.label_aggressive_domains_count,
                                localDomains.lines().count { it.isNotBlank() },
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (domainsExpanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = null,
                        )
                    }

                    AnimatedVisibility(visible = domainsExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // No inner scroll: the field grows to its full content height and
                            // scrolls with the rest of the page.
                            OutlinedTextField(
                                value = localDomains,
                                onValueChange = { localDomains = it },
                                label = { Text(stringResource(R.string.label_aggressive_domains)) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    if (domainsDirty) {
                                        IconButton(onClick = { saveDomains() }) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = stringResource(R.string.cd_save_domains),
                                            )
                                        }
                                    }
                                },
                            )
                            Text(
                                text = stringResource(R.string.hint_aggressive_domains),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        localDomains = SettingsDataStore.DEFAULT_AGGRESSIVE_DOMAINS
                                        onSaveAggressiveDomains(SettingsDataStore.DEFAULT_AGGRESSIVE_DOMAINS)
                                        focusManager.clearFocus()
                                    },
                                    enabled = localDomains != SettingsDataStore.DEFAULT_AGGRESSIVE_DOMAINS,
                                ) {
                                    Text(stringResource(R.string.btn_reset_filter_url))
                                }
                            }
                        }
                    }
                }
            }
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

    if (showAddFilter) {
        AddFilterDialog(
            onDismiss = { showAddFilter = false },
            onConfirm = {
                onAddSource(it)
                showAddFilter = false
            },
        )
    }
}

@Composable
private fun FilterSourceRow(
    source: FilterSource,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val builtIn = SettingsDataStore.isBuiltIn(source.url)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = source.enabled, role = Role.Checkbox, onValueChange = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = source.enabled, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name.ifEmpty { source.url.substringAfterLast('/') },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        if (!builtIn) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_remove_filter),
                )
            }
        }
    }
}

@Composable
private fun AddFilterDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    // ponytail: scheme check only. A bad list URL surfaces as a download error, which is
    // already handled — no point validating reachability here.
    val valid = url.startsWith("https://") || url.startsWith("http://")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_add_filter)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.label_filter_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(url) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url) }, enabled = valid) {
                Text(stringResource(R.string.btn_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
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

/**
 * Consumes leftover scroll and fling instead of letting it bubble to the enclosing page,
 * so a scrollable section can be panned without dragging the whole settings screen with it.
 */
@Composable
private fun AggressiveModeCard(
    mode: AggressiveMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(mode.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(mode.descRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

private fun AggressiveMode.labelRes(): Int = when (this) {
    AggressiveMode.OFF -> R.string.aggressive_off
    AggressiveMode.SELECTED -> R.string.aggressive_selected
    AggressiveMode.ALL -> R.string.aggressive_all
}

private fun AggressiveMode.descRes(): Int = when (this) {
    AggressiveMode.OFF -> R.string.aggressive_off_desc
    AggressiveMode.SELECTED -> R.string.aggressive_selected_desc
    AggressiveMode.ALL -> R.string.aggressive_all_desc
}

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
                    sources = SettingsDataStore.BUILT_IN_FILTERS,
                    autoUpdate = false,
                    lastUpdated = 0L,
                    ruleCount = 0,
                ),
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
                    sources = SettingsDataStore.BUILT_IN_FILTERS,
                    autoUpdate = true,
                    lastUpdated = 1_700_000_000_000L,
                    ruleCount = 3_241,
                ),
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
                    sources = SettingsDataStore.BUILT_IN_FILTERS,
                    autoUpdate = true,
                    lastUpdated = 1_700_000_000_000L,
                    ruleCount = 3_241,
                    isUpdating = true,
                ),
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
                    sources = SettingsDataStore.BUILT_IN_FILTERS,
                    autoUpdate = false,
                    lastUpdated = 0L,
                    ruleCount = 0,
                    updateError = "Network error: Unable to resolve host",
                ),
                onUpdateNow = {},
                onAutoUpdateToggle = {},
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
