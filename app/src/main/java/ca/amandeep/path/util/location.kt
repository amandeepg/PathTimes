package ca.amandeep.path.util

import ca.amandeep.path.Coordinates
import kotlin.math.abs

// NJ: 40.71670935568646, -74.03240619573852
// NY: 40.71599747828071, -74.01354955895476
private const val NJ_LONGITUDE: Double = -74.03240619573852
private const val NY_LONGITUDE: Double = -74.01354955895476

/**
 * Returns true if the coordinates are in New Jersey, false if they are in New York.
 * Designed solely for the PATH station coordinates, which are very close to the border between
 * Jersey City and Battery Park City.
 */
val Coordinates.isInNJ: Boolean
    get() {
        val distanceToNJ = abs(longitude - NJ_LONGITUDE)
        val distanceToNY = abs(longitude - NY_LONGITUDE)

        return distanceToNJ < distanceToNY
    }
