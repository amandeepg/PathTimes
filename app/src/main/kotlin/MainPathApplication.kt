package ca.amandeep.path

import android.app.Application
import ca.amandeep.path.data.PathDataSyncWorker

open class MainPathApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Schedule the background worker
        PathDataSyncWorker.schedule(this)
    }
}
