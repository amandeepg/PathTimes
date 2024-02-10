package ca.amandeep.path.data.model

import androidx.compose.runtime.Immutable
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
@JsonClass(generateAdapter = true)
data class ResultsContainer(
    @Json(name = "results")
    val stations: List<Station>,
)

@Immutable
@JsonClass(generateAdapter = true)
data class Station(
    @Json(name = "consideredStation")
    val name: StationName,
    @Json(name = "destinations")
    val upcomingTrains: List<UpcomingTrains>,
) {
    val state = name.state
}

@Immutable
enum class StationName(
    val longName: String,
    val shortName: String,
    val state: State,
    val coordinates: Coordinates,
) {
    NWK(
        longName = "Newark".displayStr(),
        shortName = "NWK",
        state = State.NJ,
        coordinates = Coordinates(
            latitude = 40.73454,
            longitude = -74.16375,
        ),
    ),
    HAR(
        longName = "Harrison".displayStr(),
        shortName = "HAR",
        state = State.NJ,
        coordinates = Coordinates(
            latitude = 40.73942,
            longitude = -74.15587,
        ),
    ),
    JSQ(
        longName = "Journal Square".displayStr(),
        shortName = "JSQ",
        state = State.NJ,
        coordinates = Coordinates(
            latitude = 40.73301,
            longitude = -74.06289,
        ),
    ),
    GRV(
        longName = "Grove Street".displayStr(),
        shortName = "GRV",
        state = State.NJ,
        coordinates = Coordinates(
            latitude = 40.71966,
            longitude = -74.04245,
        ),
    ),
    NEW(
        longName = "Newport".displayStr(),
        shortName = "NPT",
        state = State.NJ,
        coordinates = Coordinates(
            latitude = 40.72699,
            longitude = -74.03383,
        ),
    ),
    EXP(
        longName = "Exchange Place".displayStr(),
        shortName = "EXP",
        state = State.NJ,
        coordinates = Coordinates(
            latitude = 40.71676,
            longitude = -74.03238,
        ),
    ),
    HOB(
        longName = "Hoboken".displayStr(),
        shortName = "HOB",
        state = State.NJ,
        coordinates = Coordinates(
            latitude = 40.73586,
            longitude = -74.02922,
        ),
    ),
    WTC(
        longName = "World Trade Center".displayStr(),
        shortName = "WTC",
        state = State.NY,
        coordinates = Coordinates(
            latitude = 40.71271,
            longitude = -74.01193,
        ),
    ),
    CHR(
        longName = "Christopher Street".displayStr(),
        shortName = "CHR",
        state = State.NY,
        coordinates = Coordinates(
            latitude = 40.73295,
            longitude = -74.00707,
        ),
    ),
    S9(
        longName = "9th Street".displayStr(),
        shortName = "9th",
        state = State.NY,
        coordinates = Coordinates(
            latitude = 40.73424,
            longitude = -73.9991,
        ),
    ),
    S14(
        longName = "14th Street".displayStr(),
        shortName = "14th",
        state = State.NY,
        coordinates = Coordinates(
            latitude = 40.73735,
            longitude = -73.99684,
        ),
    ),
    S23(
        longName = "23rd Street".displayStr(),
        shortName = "23rd",
        state = State.NY,
        coordinates = Coordinates(
            latitude = 40.7429,
            longitude = -73.99278,
        ),
    ),
    S33(
        longName = "33rd Street".displayStr(),
        shortName = "33rd",
        state = State.NY,
        coordinates = Coordinates(
            latitude = 40.7486,
            longitude = -73.9886,
        ),
    ),
    ;

    class Adapter {
        @FromJson
        fun fromJson(stationName: String): StationName = when (stationName) {
            "NWK" -> NWK
            "HAR" -> HAR
            "JSQ" -> JSQ
            "GRV" -> GRV
            "NEW" -> NEW
            "EXP" -> EXP
            "HOB" -> HOB
            "WTC" -> WTC
            "CHR" -> CHR
            "09S" -> S9
            "14S" -> S14
            "23S" -> S23
            "33S" -> S33
            else -> throw IllegalArgumentException("Station name not found")
        }
    }
}

data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

private fun String.displayStr(): String = replace(' ', Typography.nbsp)

enum class State {
    NY,
    NJ,
}

@JsonClass(generateAdapter = true)
data class UpcomingTrains(
    @Json(name = "label")
    val direction: Direction,
    @Json(name = "messages")
    val trains: List<UpcomingTrain>,
)

@Immutable
enum class Direction(
    val stateName: String,
    val stateNameShort: String,
) {
    ToNJ("New Jersey".displayStr(), "NJ"),
    ToNY("New York".displayStr(), "NY"),
}

@Immutable
enum class Route(
    val njTerminus: StationName,
    val nyTerminus: StationName,
    val via: StationName? = null,
) {
    JSQ_33(
        njTerminus = StationName.JSQ,
        nyTerminus = StationName.S33,
    ),
    HOB_33(
        njTerminus = StationName.HOB,
        nyTerminus = StationName.S33,
    ),
    HOB_WTC(
        njTerminus = StationName.HOB,
        nyTerminus = StationName.WTC,
    ),
    NWK_WTC(
        njTerminus = StationName.NWK,
        nyTerminus = StationName.WTC,
    ),
    JSQ_33_HOB(
        njTerminus = StationName.JSQ,
        nyTerminus = StationName.S33,
        via = StationName.HOB,
    ),
}

val Route.displayName
    get() = when (this) {
        Route.JSQ_33 -> "JSQ-33"
        Route.HOB_33 -> "HOB-33"
        Route.HOB_WTC -> "HOB-WTC"
        Route.NWK_WTC -> "NWK-WTC"
        Route.JSQ_33_HOB -> "JSQ-33 via HOB".displayStr()
    }

@Immutable
@JsonClass(generateAdapter = true)
data class UpcomingTrain(
    @Json(name = "lineColor")
    val lineColor: String,
    @Json(name = "secondsToArrival")
    val secondsToArrival: Int,
    @Json(name = "target")
    val target: StationName,
    @Json(name = "lastUpdated")
    val lastUpdated: Date,
) {
    constructor(
        route: Route,
        direction: Direction,
        minsToArrival: Int,
    ) : this(
        lineColor = when (route) {
            Route.JSQ_33 -> "FF9900"
            Route.HOB_33 -> "4D92FB"
            Route.HOB_WTC -> "65C100"
            Route.NWK_WTC -> "D93A30"
            Route.JSQ_33_HOB -> "4D92FB,FF9900"
        },
        secondsToArrival = minsToArrival * 60,
        target = when (direction) {
            Direction.ToNJ -> route.njTerminus
            Direction.ToNY -> route.nyTerminus
        },
        lastUpdated = Date(System.currentTimeMillis()),
    )

    val arrivalDate = Date(lastUpdated.time + secondsToArrival * 1000)

    val direction: Direction = when (target.state) {
        State.NJ -> Direction.ToNJ
        State.NY -> Direction.ToNY
    }

    val route: Route = when (lineColor) {
        "FF9900" -> Route.JSQ_33
        "4D92FB" -> Route.HOB_33
        "65C100" -> Route.HOB_WTC
        "D93A30" -> Route.NWK_WTC
        "4D92FB,FF9900", "FF9900,4D92FB" -> Route.JSQ_33_HOB
        else -> throw IllegalArgumentException("Route not found")
    }
}

fun UpcomingTrain.relativeArrivalMins(now: Long): Double {
    val diff = arrivalDate.time - now
    val seconds = diff / 1000
    // minutes
    return seconds / 60.0
}

class SortPlaces(private val currentLocation: Coordinates) : Comparator<StationName> {
    override fun compare(station1: StationName, station2: StationName): Int {
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
