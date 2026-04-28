package com.cytsai.urlclean

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.cytsai.urlclean.core.UrlCleaner
import com.cytsai.urlclean.data.FilterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedText.isNullOrBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val (cleanedUrl, toast) = withContext(Dispatchers.IO) {
                if (sharedText.startsWith("http://") || sharedText.startsWith("https://")) {
                    val rules = FilterRepository(applicationContext).loadRules()
                    if (rules.isEmpty()) {
                        sharedText to R.string.toast_no_rules
                    } else {
                        val cleaned = UrlCleaner.clean(sharedText, rules)
                        cleaned to if (cleaned != sharedText) R.string.toast_url_cleaned else null
                    }
                } else {
                    sharedText to null
                }
            }

            if (toast != null) {
                val length = if (toast == R.string.toast_no_rules) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                Toast.makeText(applicationContext, getString(toast), length).show()
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, cleanedUrl)
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
