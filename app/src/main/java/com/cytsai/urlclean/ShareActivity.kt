package com.cytsai.urlclean

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.cytsai.urlclean.core.ShareTextCleaner
import com.cytsai.urlclean.data.FilterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareActivity : ComponentActivity() {

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
        val hasUrl = ShareTextCleaner.cleanFirstUrl(sharedTextOrEmpty, emptyList()).foundUrl

        lifecycleScope.launch {
            val (cleanedText, toast) = withContext(Dispatchers.IO) {
                if (hasUrl) {
                    val rules = FilterRepository(applicationContext).loadRules()
                    val result = ShareTextCleaner.cleanFirstUrl(sharedTextOrEmpty, rules)
                    val toast = when {
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
                    arrayOf(ComponentName(applicationContext, ShareActivity::class.java)),
                )
            }
            startActivity(chooser)
            finish()
        }
    }
}
