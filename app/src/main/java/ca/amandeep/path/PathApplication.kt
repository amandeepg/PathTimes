package ca.amandeep.path

import android.app.Application
import timber.log.Timber
import timber.log.Timber.DebugTree

class PathApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }
}
