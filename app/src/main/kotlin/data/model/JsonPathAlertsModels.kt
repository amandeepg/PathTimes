package ca.amandeep.path.data.model

import androidx.compose.runtime.Immutable
import ca.amandeep.path.data.model.AlertData.Grouped.Title.Companion.extractRoutesTitle
import ca.amandeep.path.data.model.AlertData.Grouped.Title.Companion.toTitle
import ca.amandeep.path.data.model.AlertDatas.Companion.getGroupedAlerts
import ca.amandeep.path.data.model.AlertDatas.Companion.toAlertDatas
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Immutable
@JsonClass(generateAdapter = true)
data class AlertContainer(
    @Json(name = "ContentKey") val contentKey: String?,
    @Json(name = "Content") val content: String?,
) {
    val alertDatas by lazy {
        val noAlerts = NO_ALERTS_HTML_TEXT in content.orEmpty()
        val alerts = try {
            val document = Jsoup.parse(content?.replace("&quot", "").orEmpty())
            document.toAlertDatas()
        } catch (_: Exception) {
            emptyList()
        }
        AlertDatas(
            hasError = !noAlerts && alerts.isEmpty(),
            alerts = alerts.getGroupedAlerts(),
        )
    }

    companion object {
        private const val NO_ALERTS_HTML_TEXT = "There are no active PATHAlerts at this time"

        private fun Document.toAlertDatas(): ImmutableList<AlertData.Single> =
            select(".stationName").map { it.text() }
                .zip(select(".alertText").map { it.text() })
                .toAlertDatas()
    }
}

data class AlertDatas(
    val alerts: ImmutableList<AlertData> = persistentListOf(),
    val hasError: Boolean = false,
) {
    val size = alerts.size

    fun isEmpty(): Boolean = alerts.isEmpty()

    companion object {
        private val DATE_FORMATTER = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/New_York")
        }
        private val TIME_PREFIX_REGEX = Regex("^\\d{1,2}[: ]\\d{1,2} ?[apAP][mM][: ]?", RegexOption.IGNORE_CASE)
        private val UPDATE_IN_MINS_REGEX = Regex("Update in \\d{1,2} mins\\.")
        private val APOLOGIZE_REGEX =
            Regex("We (apologize|regret) (for )?(the|this|any)?( )?(inconvenience)( )?(this )?(may )?(have|has)?( )?(caused)?(.*\\.)")

        fun Iterable<Pair<String, String>>.toAlertDatas(): ImmutableList<AlertData.Single> = this
            .map {
                val date = try {
                    DATE_FORMATTER.parse(it.first.trim())
                } catch (_: Exception) {
                    null
                }
                AlertData.Single(it.second.removeUnnecessaryText().trim(), date)
            }
            .sortedBy { it.date }
            .reversed()
            .distinctBy { it.text }
            .toImmutableList()

        fun Iterable<AlertData.Single>.getGroupedAlerts(): ImmutableList<AlertData> = this
            .groupBy {
                if ("Service Advisory" in it.text) {
                    it.text
                } else {
                    it.text.extractRoutesTitle()?.routes?.joinToString()
                        ?: it.text.split(".").first()
                }
            }
            .map { group ->
                val title = group.value.first().toTitle()
                if (group.value.size == 1 && title !is AlertData.Grouped.Title.RouteTitle) {
                    group.value.single()
                } else {
                    val alertsWithBlanks = group.value.mapIndexed { index, alert ->
                        val newText = alert.text
                            .dropBefore(".")
                            .let {
                                when (index) {
                                    0 -> it
                                    else -> it.replace(UPDATE_IN_MINS_REGEX, "")
                                }
                            }
                            .trim()
                        alert.copy(text = newText)
                    }
                    val alerts = alertsWithBlanks.mapIndexed { index, alert ->
                        if (alert.text.isBlank() && index != 0) {
                            alert.copy(
                                text = group.value[index].toTitle().text.capitalize().addPeriod(),
                            )
                        } else {
                            alert.copy(text = alert.text.capitalize().addPeriod())
                        }
                    }
                    AlertData.Grouped(
                        title = title,
                        main = alerts.first(),
                        history = alerts.drop(1).toImmutableList(),
                    )
                }
            }
            .toImmutableList()

        private fun AlertData.Single.toTitle(): AlertData.Grouped.Title =
            text.getBefore(".").toTitle()

        private fun String.removeUnnecessaryText(): String = this
            .remove("  ").remove("  ").remove("  ").remove("  ").remove("  ")
            .replaceFirst(TIME_PREFIX_REGEX, "")
            .replace("An update will be issued in approx.", "Update in")
            .replace("An update will be issued in approx", "Update in")
            .replace("An update will be issued w/in approx", "Update in")
            .replace("Next update will be issued w/in approx", "Update in")
            .replace("Next update w/in", "Update in")
            .replace("mins.", "  mins.")
            .replace(APOLOGIZE_REGEX, "")
            .remove("PATHAlert:")
            .remove("PATHAlert Update:")
            .remove("PATHAlert Final Update:")
            .remove("Final Update:")
            .remove("Update:")
            .remove("Real-Time Train Departures on: - RidePATH app: - PATH website:")
            .remove("  ").remove("  ").remove("  ").remove("  ").remove("  ")
            .replace("..", ".").replace("..", ".")

        private fun String.remove(str: String): String = replace(str, "")
    }
}

@Immutable
sealed interface AlertData {
    @Immutable
    data class Single(
        val text: String,
        val date: Date?,
    ) : AlertData {
        val isElevator: Boolean = text.contains("elevator", ignoreCase = true)
    }

    @Immutable
    data class Grouped(
        val title: Title,
        val main: Single,
        val history: ImmutableList<Single> = persistentListOf(),
    ) : AlertData {
        @Immutable
        sealed interface Title {
            val text: String

            @Immutable
            data class RouteTitle(
                val routes: ImmutableList<Route>,
                override val text: String,
            ) : Title

            @Immutable
            data class FreeformTitle(override val text: String) : Title

            companion object {
                val ROUTE_STRINGS = listOf(
                    "JSQ-33 via HOB" to Route.JSQ_33_HOB,
                    "33-JSQ via HOB" to Route.JSQ_33_HOB,
                    "HOB-33" to Route.HOB_33,
                    "33-HOB" to Route.HOB_33,
                    "JSQ-33" to Route.JSQ_33,
                    "33-JSQ" to Route.JSQ_33,
                    "NWK-WTC" to Route.NWK_WTC,
                    "WTC-NWK" to Route.NWK_WTC,
                    "HOB-WTC" to Route.HOB_WTC,
                    "WTC-HOB" to Route.HOB_WTC,
                )

                fun String.toTitle(): Title =
                    extractRoutesTitle() ?: FreeformTitle(this)

                fun String.extractRoutesTitle(): RouteTitle? {
                    val routes = split(", ").mapNotNull { candidateStr ->
                        ROUTE_STRINGS.firstOrNull { candidateStr.trim().startsWith(it.first) }
                    }
                    return if (routes.isNotEmpty()) {
                        RouteTitle(
                            routes = routes
                                .sortedBy { it.first }
                                .map { it.second }
                                .toImmutableList(),
                            text = routes.fold(this) { str, route ->
                                str.removePrefix(route.first).removePrefix(", ").trim()
                            },
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }
}

private fun String.dropBefore(delimiter: String): String =
    split(delimiter).drop(1).joinToString(delimiter).trim()

private fun String.getBefore(delimiter: String): String =
    split(delimiter).firstOrNull().orEmpty()

private fun String.capitalize(): String = replaceFirstChar {
    if (it.isLowerCase()) {
        it.titlecase(Locale.US)
    } else {
        it.toString()
    }
}

private fun String.addPeriod(): String = trim().let {
    if (it.endsWith(".")) it else "$it."
}
