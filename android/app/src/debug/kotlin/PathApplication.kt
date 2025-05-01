package ca.amandeep.path

import timber.log.Timber
import timber.log.Timber.DebugTree

class PathApplication : MainPathApplication() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(DebugTree())
    }
}
