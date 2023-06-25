package ca.amandeep.path.data.model

import androidx.compose.runtime.Stable
import ca.amandeep.path.data.model.AlertData.Grouped.Title.Companion.toTitle
import ca.amandeep.path.data.model.AlertDatas.Companion.getGroupedAlerts
import ca.amandeep.path.data.model.AlertDatas.Companion.toAlertDatas
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Stable
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

        private fun Document.toAlertDatas(): List<AlertData.Single> =
            select(".stationName").map { it.text() }
                .zip(select(".alertText").map { it.text() })
                .toAlertDatas()
    }
}

data class AlertDatas(
    val alerts: List<AlertData> = emptyList(),
    val hasError: Boolean = false,
) {
    val size = alerts.size

    fun isEmpty(): Boolean = alerts.isEmpty()

    companion object {
        private val DATE_FORMATTER = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/New_York")
        }
        private val TIME_PREFIX_REGEX = Regex("\\d{1,2}:\\d{2} [ap]m:", RegexOption.IGNORE_CASE)
        private val UPDATE_IN_MINS_REGEX = Regex("Update in \\d{1,2} mins\\.")
        private val APOLOGIZE_REGEX = Regex("We (apologize|regret) (for )?(the|this|any)?( )?(inconvenience)( )?(this )?(may )?(have|has)?( )?(caused)?(.*\\.)")
        fun List<Pair<String, String>>.toAlertDatas(): List<AlertData.Single> = this
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

        fun List<AlertData.Single>.getGroupedAlerts(): List<AlertData> = this
            .groupBy {
                if ("Service Advisory" in it.text) {
                    it.text
                } else {
                    when {
                        it.text.startsWith("JSQ-33 via HOB") -> "JSQ-33 via HOB"
                        it.text.startsWith("JSQ-33") -> "JSQ-33"
                        it.text.startsWith("HOB-33") -> "HOB-33"
                        it.text.startsWith("HOB-WTC") -> "HOB-WTC"
                        it.text.startsWith("NWK-WTC") -> "NWK-WTC"
                        else -> it.text.split(".").first()
                    }
                }
            }
            .map { group ->
                if (group.value.size == 1) {
                    group.value.single()
                } else {
                    val alerts = group.value
                        .mapIndexed { index, alert ->
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
                        .distinctBy { it.text }
                    AlertData.Grouped(
                        title = group.value.first().text.split(".").first().toTitle(),
                        main = alerts.first(),
                        history = alerts.drop(1),
                    )
                }
            }

        private fun String.removeUnnecessaryText(): String = this
            .replaceFirst(TIME_PREFIX_REGEX, "")
            .replace("An update will be issued in approx.", "Update in")
            .replace("An update will be issued in approx", "Update in")
            .replace(APOLOGIZE_REGEX, "")
            .replace("PATHAlert:", "")
            .replace("PATHAlert Update:", "")
            .replace("PATHAlert Final Update:", "")
            .replace("  ", "")
            .replace("  ", "")
            .replace("  ", "")
            .replace("..", ".")
            .replace("..", ".")
    }
}

sealed interface AlertData {
    data class Single(
        val text: String,
        val date: Date?,
    ) : AlertData

    data class Grouped(
        val title: Title,
        val main: Single,
        val history: List<Single> = emptyList(),
    ) : AlertData {
        sealed interface Title {
            data class RouteTitle(
                val route: Route,
                val text: String,
            ) : Title

            data class FreeformTitle(val text: String) : Title

            companion object {
                val ROUTE_STRINGS = listOf(
                    "JSQ-33 via HOB" to Route.JSQ_33_HOB,
                    "HOB-33" to Route.HOB_33,
                    "JSQ-33" to Route.JSQ_33,
                    "NWK-WTC" to Route.NWK_WTC,
                    "WTC-NWK" to Route.NWK_WTC,
                    "HOB-WTC" to Route.HOB_WTC,
                    "WTC-HOB" to Route.HOB_WTC,
                )

                fun String.toTitle(): Title {
                    val routeInText = ROUTE_STRINGS.firstOrNull { startsWith(it.first) }?.first
                    val route = ROUTE_STRINGS.firstOrNull { startsWith(it.first) }?.second
                    return if (route != null) {
                        RouteTitle(route, replaceFirst(routeInText!!, "").trim())
                    } else {
                        FreeformTitle(this)
                    }
                }
            }
        }
    }
}

private fun String.dropBefore(delimiter: String): String =
    split(delimiter).drop(1).joinToString(delimiter).trim()
