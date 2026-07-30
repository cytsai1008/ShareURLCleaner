package com.cytsai.urlclean

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.cytsai.urlclean.core.RedirectResolver
import com.cytsai.urlclean.core.ShareTextCleaner
import com.cytsai.urlclean.core.UrlCleaner
import com.cytsai.urlclean.data.AggressiveMode
import com.cytsai.urlclean.data.FilterRepository
import com.cytsai.urlclean.data.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Manifest alias that forces aggressive mode on. Must match `AndroidManifest.xml`. */
private const val AGGRESSIVE_ALIAS = "com.cytsai.urlclean.ShareAggressiveActivity"

class ShareActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourceIntent = intent
        val sourceType = sourceIntent.type
        val sourceStream = IntentCompat.getParcelableExtra(
            sourceIntent,
            Intent.EXTRA_STREAM,
            Uri::class.java,
        )
        val sharedText = sourceIntent.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedText.isNullOrBlank() && sourceStream == null) {
            finish()
            return
        }

        val sharedTextOrEmpty = sharedText.orEmpty()
        val hasUrl = ShareTextCleaner.hasUrl(sharedTextOrEmpty)

        // Shared via the "Aggressive Mode" entry: aggressive mode for every domain,
        // whatever the settings say.
        val forceAggressive = sourceIntent.component?.className == AGGRESSIVE_ALIAS

        lifecycleScope.launch {
            // Written from several fetch threads at once now that URLs resolve in parallel.
            val fetchFailed = AtomicBoolean(false)
            val fetchAnnounced = AtomicBoolean(false)
            val (cleanedText, toast) = withContext(Dispatchers.IO) {
                if (hasUrl) {
                    val rules = FilterRepository(applicationContext).loadRules()
                    val settings = SettingsDataStore(applicationContext)
                    val resolver = RedirectResolver.forMode(
                        if (forceAggressive) AggressiveMode.ALL else settings.aggressiveMode.first(),
                        if (forceAggressive) {
                            emptySet()
                        } else {
                            SettingsDataStore.parseDomains(settings.aggressiveDomains.first())
                        },
                        onFetch = {
                            // Fires on the IO thread right before the network call, so the user
                            // gets an explanation for the pause before the chooser appears.
                            // Once per share, not once per link.
                            if (fetchAnnounced.compareAndSet(false, true)) {
                                mainHandler.post {
                                    Toast.makeText(
                                        applicationContext,
                                        getString(R.string.toast_fetching_url),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        onFailure = { fetchFailed.set(true) },
                        clean = { UrlCleaner.clean(it, rules) },
                    )
                    val result = ShareTextCleaner.cleanUrls(sharedTextOrEmpty, rules, resolver)
                    val toast = when {
                        // Ranked first: the user was told to wait, so they are owed the outcome.
                        // The link still goes out, just without its redirect followed.
                        fetchFailed.get() -> R.string.toast_fetch_failed
                        rules.isEmpty() -> R.string.toast_no_rules
                        result.cleaned -> R.string.toast_url_cleaned
                        else -> null
                    }
                    result.text to toast
                } else {
                    sharedTextOrEmpty to null
                }
            }

            if (toast != null) {
                val length = if (toast == R.string.toast_no_rules) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                Toast.makeText(applicationContext, getString(toast), length).show()
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (sourceStream != null && !sourceType.isNullOrBlank()) sourceType else "text/plain"
                if (cleanedText.isNotBlank()) {
                    putExtra(Intent.EXTRA_TEXT, cleanedText)
                }
                if (sourceStream != null) {
                    putExtra(Intent.EXTRA_STREAM, sourceStream)
                    clipData = ClipData.newRawUri(null, sourceStream)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            val chooser = Intent.createChooser(sendIntent, null).apply {
                putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(
                        ComponentName(applicationContext, ShareActivity::class.java),
                        ComponentName(packageName, AGGRESSIVE_ALIAS),
                    ),
                )
            }
            startActivity(chooser)
            finish()
        }
    }
}
