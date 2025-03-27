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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Background worker that synchronizes PATH data every 30 minutes
 */
class PathDataSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            d { "Starting background data sync" }

            // TODO consolidate this with MainViewModel
            val pathRepository = PathRepository(
                pathRemoteDataSource = PathRemoteDataSource(
                    pathRestApi = PathOfficialRestApiService.INSTANCE,
                    alertsApi = PathAlertsApiService.INSTANCE,
                    ioDispatcher = Dispatchers.IO,
                    alertParser = AlertParser(),
                ),
                summarizerApi = PathAlertsSummarizerApiService.create(context),
                arrivalsUpdateInterval = 99.days,
                alertsUpdateInterval = 99.days,
            )

            withTimeoutOrNull(1.minutes.toJavaDuration()) {
                pathRepository.alerts.combine(pathRepository.arrivals) { alerts, arrivals ->
                    d { "Background sync: ${arrivals.arrivals.size} arrivals, ${alerts.alerts.size} alerts" }
                }.collect()
            }

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
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                PeriodicWorkRequestBuilder<PathDataSyncWorker>(
                    repeatInterval = 15.minutes.toJavaDuration(),
                    flexTimeInterval = 15.minutes.toJavaDuration(),
                )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresBatteryNotLow(true)
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build(),
            )

            d { "Scheduled PATH data sync to run every 15 minutes" }
        }
    }
}
