package ca.amandeep.path

import android.app.Application
import ca.amandeep.path.data.PathDataSyncWorker
import timber.log.Timber
import timber.log.Timber.DebugTree

class PathApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(DebugTree())

        // Schedule the background worker
        PathDataSyncWorker.schedule(this)
    }
}
