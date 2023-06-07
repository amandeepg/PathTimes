package ca.amandeep.path.data.model

import androidx.compose.runtime.Stable
import ca.amandeep.path.data.model.AlertDatas.Companion.getGroupedAlerts
import ca.amandeep.path.data.model.AlertDatas.Companion.toAlertDatas
import ca.amandeep.path.data.model.GroupedAlertData.Title.Companion.toTitle
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
        val (regularAlerts, groupedAlerts) = alerts.getGroupedAlerts()
        AlertDatas(
            hasError = !noAlerts && alerts.isEmpty(),
            alerts = regularAlerts,
            groupedAlerts = groupedAlerts,
        )
    }

    companion object {
        private const val NO_ALERTS_HTML_TEXT = "There are no active PATHAlerts at this time"

        private fun Document.toAlertDatas(): List<AlertData> =
            select(".stationName").map { it.text() }
                .zip(select(".alertText").map { it.text() })
                .toAlertDatas()
    }
}

data class AlertDatas(
    val groupedAlerts: List<GroupedAlertData> = emptyList(),
    val alerts: List<AlertData> = emptyList(),
    val hasError: Boolean = false,
) {
    val size = alerts.size + groupedAlerts.size

    fun isEmpty(): Boolean = alerts.isEmpty() && groupedAlerts.isEmpty()

    companion object {
        private val DATE_FORMATTER = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/New_York")
        }
        private val TIME_PREFIX_REGEX = Regex("\\d{2}:\\d{2} [AP]M: ")
        private val UPDATE_IN_MINS_REGEX = Regex("Update in \\d{1,2} mins\\.")
        fun List<Pair<String, String>>.toAlertDatas(): List<AlertData> = this
            .map {
                val date = try {
                    DATE_FORMATTER.parse(it.first.trim())
                } catch (_: Exception) {
                    null
                }
                AlertData(it.second.removeUnnecessaryText().trim(), date)
            }
            .sortedBy { it.date }
            .reversed()
            .distinctBy { it.text }

        fun List<AlertData>.getGroupedAlerts(): Pair<List<AlertData>, List<GroupedAlertData>> {
            val groups = groupBy {
                when {
                    it.text.startsWith("JSQ-33") -> "JSQ-33"
                    it.text.startsWith("HOB-33") -> "HOB-33"
                    it.text.startsWith("HOB-WTC") -> "HOB-WTC"
                    it.text.startsWith("NWK-WTC") -> "NWK-WTC"
                    it.text.startsWith("JSQ-33 via HOB") -> "JSQ-33 via HOB"
                    else -> it.text.split(".").first()
                }
            }
            val groupedAlerts = groups.filter { it.value.size > 1 }
                .map { group ->
                    GroupedAlertData(
                        group.value.first().text.split(".").first().toTitle(),
                        group.value
                            .mapIndexed { index, alert ->
                                alert.copy(
                                    text = alert.text
                                        .dropBefore(".")
                                        .let {
                                            when (index) {
                                                0 -> it
                                                else -> it.replace(UPDATE_IN_MINS_REGEX, "")
                                            }
                                        }
                                        .trim(),
                                )
                            }
                            .distinctBy { it.text },
                    )
                }
            val regularAlerts = groups.filter { it.value.size == 1 }.values.map { it.first() }

            return regularAlerts to groupedAlerts
        }

        private fun String.removeUnnecessaryText(): String {
            return replaceFirst(TIME_PREFIX_REGEX, "")
                .replace("An update will be issued in approx.", "Update in")
                .replace("An update will be issued in approx", "Update in")
                .replace("We regret this inconvenience.", "")
                .replace("We apologize for the inconvenience this may have caused.", "")
        }
    }
}

data class AlertData(
    val text: String,
    val date: Date?,
)

data class GroupedAlertData(
    val title: Title,
    val alerts: List<AlertData> = emptyList(),
) {
    sealed interface Title {
        data class RouteTitle(
            val route: Route,
            val text: String,
        ) : Title

        data class FreeformTitle(val text: String) : Title

        companion object {
            fun String.toTitle(): Title {
                val route = when {
                    startsWith("HOB-33") -> Route.HOB_33
                    startsWith("JSQ-33") -> Route.JSQ_33
                    startsWith("NWK-WTC") -> Route.NWK_WTC
                    startsWith("WTC-NWK") -> Route.NWK_WTC
                    startsWith("HOB-NWK") -> Route.HOB_WTC
                    startsWith("NWK-HOB") -> Route.HOB_WTC
                    startsWith("JSQ-33 via HOB") -> Route.JSQ_33_HOB
                    else -> null
                }
                return if (route != null) {
                    RouteTitle(route, dropBefore(" "))
                } else {
                    FreeformTitle(this)
                }
            }
        }
    }
}

private fun String.dropBefore(delimiter: String): String =
    split(delimiter).drop(1).joinToString(delimiter).trim()
