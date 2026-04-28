package com.cytsai.urlclean

import android.app.Application
import android.util.Log
import androidx.work.Configuration

class App : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
