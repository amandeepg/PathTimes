package ca.amandeep.path.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Launches the app with the given package name, or opens the Play Store page if the app is not installed.
 */
fun launchPackageOrMarketPage(ctx: Context, appPackageName: String) {
    val launchIntent = ctx.packageManager.getLaunchIntentForPackage(appPackageName)
    val launchedApp = if (launchIntent != null) {
        try {
            ctx.startActivity(launchIntent, null)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    } else {
        false
    }

    if (!launchedApp) {
        try {
            ctx.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$appPackageName".toUri(),
                ),
                null,
            )
        } catch (e: ActivityNotFoundException) {
            ctx.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "market://details?id=$appPackageName".toUri(),
                ),
                null,
            )
        }
    }
}
