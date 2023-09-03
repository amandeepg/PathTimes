package ca.amandeep.path.data.model

import androidx.compose.runtime.Immutable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.text.Typography.nbsp

@Immutable
@JsonClass(generateAdapter = true)
data class Stations(
    @field:Json(name = "stations") val stations: List<Station>?,
)

@Immutable
@JsonClass(generateAdapter = true)
data class Station(
    @field:Json(name = "station") val station: String,
    @field:Json(name = "name") val name: String,
    @field:Json(name = "coordinates") val coordinates: Coordinates,
)

@Immutable
@JsonClass(generateAdapter = true)
data class Coordinates(
    @field:Json(name = "latitude") val latitude: Double,
    @field:Json(name = "longitude") val longitude: Double,
)

@Immutable
@JsonClass(generateAdapter = true)
data class UpcomingTrains(
    @field:Json(name = "upcomingTrains") val upcomingTrains: List<UpcomingTrain>?,
)

@Immutable
@JsonClass(generateAdapter = true)
data class UpcomingTrain(
    @field:Json(name = "route") val _route: Route,
    @field:Json(name = "direction") val direction: Direction,
    @field:Json(name = "projectedArrival") val projectedArrival: Date,
    @field:Json(name = "lineColors") val lineColors: List<String> = listOf("", ""),
) {
    val route = when (_route) {
        Route.JSQ_33_HOB ->
            if (lineColors.size > 1) {
                _route
            } else if ("#FF9900" in lineColors) {
                Route.JSQ_33
            } else if ("#4D92FB" in lineColors) {
                Route.HOB_33
            } else {
                Route.HOB_33
            }

        else -> _route
    }
}

fun UpcomingTrain.relativeArrivalMins(now: Long): Double {
    val diff = projectedArrival.time - now
    val seconds = diff / 1000
    // minutes
    return seconds / 60.0
}

@Immutable
enum class Direction(
    val stateName: String,
    val stateNameShort: String,
) {
    TO_NJ("New${nbsp}Jersey", "NJ"),
    TO_NY("New${nbsp}York", "NY"),
}

@Immutable
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

val Route.displayName
    get() = when (this) {
        Route.JSQ_33 -> "JSQ-33"
        Route.HOB_33 -> "HOB-33"
        Route.HOB_WTC -> "HOB-WTC"
        Route.NWK_WTC -> "NWK-WTC"
        Route.JSQ_33_HOB -> "JSQ-33 via HOB"
    }

@Immutable
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
                    sin(deltaLon / 2).pow(2.0),
            ),
        )
        return radius * angle
    }
}
