package ca.amandeep.path

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import java.util.Locale

private fun Coordinates.geoAdminArea(context: Context): String =
    address(context)?.adminArea.orEmpty()

private fun Coordinates.address(context: Context): Address? =
    try {
        Log.d("agrewal", "address")
        Geocoder(context, Locale.US)
            .getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
    } catch (e: Exception) {
        null
    }

fun Coordinates.isInNJ(context: Context): Boolean {
    val geoAdminArea = geoAdminArea(context)
    return geoAdminArea.equals("NJ", ignoreCase = true) ||
            geoAdminArea.equals("New Jersey", ignoreCase = true)
}
