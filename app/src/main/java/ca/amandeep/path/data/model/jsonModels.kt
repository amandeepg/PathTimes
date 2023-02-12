package ca.amandeep.path.data.model

import androidx.compose.runtime.Stable
import com.squareup.moshi.Json
import java.util.Date
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Stable
data class Stations(
    @field:Json(name = "stations") val stations: List<Station>?,
)

@Stable
data class Station(
    @field:Json(name = "station") val station: String,
    @field:Json(name = "name") val name: String,
    @field:Json(name = "coordinates") val coordinates: Coordinates,
)

@Stable
data class Coordinates(
    @field:Json(name = "latitude") val latitude: Double,
    @field:Json(name = "longitude") val longitude: Double,
)

@Stable
data class UpcomingTrains(
    @field:Json(name = "upcomingTrains") val upcomingTrains: List<UpcomingTrain>?,
)

@Stable
data class UpcomingTrain(
    @field:Json(name = "route") val route: Route,
    @field:Json(name = "direction") val direction: Direction,
    @field:Json(name = "projectedArrival") val projectedArrival: Date,
)

fun UpcomingTrain.relativeArrivalMins(now: Long): Double {
    val diff = projectedArrival.time - now
    val seconds = diff / 1000
    // minutes
    return seconds / 60.0
}

@Stable
enum class Direction(
    val stateName: String,
) {
    TO_NJ("New Jersey"),
    TO_NY("New York")
}

@Stable
enum class Route(
    val njTerminus: RouteStation,
    val nyTerminus: RouteStation,
    val via: RouteStation? = null,
) {
    JSQ_33(
        njTerminus = RouteStation.JSQ,
        nyTerminus = RouteStation.THIRTY_THIRD,
    ),
    HOB_33(
        njTerminus = RouteStation.HOB,
        nyTerminus = RouteStation.THIRTY_THIRD,
    ),
    HOB_WTC(
        njTerminus = RouteStation.HOB,
        nyTerminus = RouteStation.WTC,
    ),
    NWK_WTC(
        njTerminus = RouteStation.NWK,
        nyTerminus = RouteStation.WTC,
    ),
    JSQ_33_HOB(
        njTerminus = RouteStation.JSQ,
        nyTerminus = RouteStation.THIRTY_THIRD,
        via = RouteStation.HOB,
    ),
}

@Stable
enum class RouteStation {
    JSQ, NWK, WTC, HOB, THIRTY_THIRD,
}

class SortPlaces(private val currentLocation: Coordinates) : Comparator<Station> {
    override fun compare(station1: Station, station2: Station): Int {
        val lat1 = station1.coordinates.latitude
        val lon1 = station1.coordinates.longitude
        val lat2 = station2.coordinates.latitude
        val lon2 = station2.coordinates.longitude
        val distanceToPlace1 =
            distance(currentLocation.latitude, currentLocation.longitude, lat1, lon1)
        val distanceToPlace2 =
            distance(currentLocation.latitude, currentLocation.longitude, lat2, lon2)
        return (distanceToPlace1 - distanceToPlace2).toInt()
    }

    private fun distance(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val radius = 6378137.0 // approximate Earth radius, *in meters*
        val deltaLat = toLat - fromLat
        val deltaLon = toLon - fromLon
        val angle = 2 * asin(
            sqrt(
                sin(deltaLat / 2).pow(2.0) +
                    cos(fromLat) * cos(toLat) *
                    sin(deltaLon / 2).pow(2.0)
            )
        )
        return radius * angle
    }
}
