package ca.amandeep.path.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat

/**
 * Launches the app with the given package name, or opens the Play Store page if the app is not installed.
 */
fun launchPackageOrMarketPage(ctx: Context, appPackageName: String) {
    val launchIntent = ctx.packageManager.getLaunchIntentForPackage(appPackageName)
    val launchedApp = if (launchIntent != null) {
        try {
            ContextCompat.startActivity(ctx, launchIntent, null)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    } else {
        false
    }

    if (!launchedApp) {
        try {
            ContextCompat.startActivity(
                ctx,
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName"),
                ),
                null,
            )
        } catch (e: ActivityNotFoundException) {
            ContextCompat.startActivity(
                ctx,
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$appPackageName"),
                ),
                null,
            )
        }
    }
}
