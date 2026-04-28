package com.cytsai.urlclean.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cytsai.urlclean.data.FilterRepository
import com.cytsai.urlclean.data.SettingsDataStore
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

class FilterUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "filter_update_periodic"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<FilterUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val dataStore = SettingsDataStore(applicationContext)
        val repo = FilterRepository(applicationContext)

        return try {
            val url = dataStore.filterUrl.first()
            val result = repo.downloadAndUpdate(url)
            result.fold(
                onSuccess = { count ->
                    dataStore.setLastUpdated(System.currentTimeMillis())
                    dataStore.setRuleCount(count)
                    Result.success()
                },
                onFailure = { e ->
                    if (e is IOException) Result.retry() else Result.failure()
                },
            )
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}
