package ca.amandeep.path.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.ajalt.timberkt.Timber.d
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background worker that synchronizes PATH data every 30 minutes
 */
class PathDataSyncWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            d { "Starting background data sync" }

            // Create PathRemoteDataSource and fetch data
            val pathRemoteDataSource = PathRemoteDataSource(
                pathRestApi = PathOfficialRestApiService.INSTANCE,
                alertsApi = PathAlertsApiService.INSTANCE,
                ioDispatcher = Dispatchers.IO,
                alertParser = AlertParser(),
            )

            // Fetch arrivals and alerts
            val arrivals = pathRemoteDataSource.getArrivals()
            val alerts = pathRemoteDataSource.getAlerts()

            d { "Background sync completed successfully: ${arrivals.size} stations, ${alerts.size} alerts" }
            Result.success()
        } catch (e: Exception) {
            d { "Background sync failed: ${e.message}" }
            // Retry on failure
            Result.retry()
        }
    }

    companion object {
        private const val SYNC_WORK_NAME = "path_data_sync_work"

        /**
         * Schedules the background worker to run every 30 minutes
         */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PathDataSyncWorker>(
                    30,
                    TimeUnit.MINUTES,
                    15,
                    TimeUnit.MINUTES,
                )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build(),
            )

            d { "Scheduled PATH data sync to run every 30 minutes" }
        }
    }
}
