package ca.amandeep.path.data.model

import androidx.compose.runtime.Stable
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
            alerts = alerts,
        )
    }

    companion object {
        private val DATE_FORMATTER = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US)
        private const val NO_ALERTS_HTML_TEXT = "There are no active PATHAlerts at this time"

        private val TIME_PREFIX_REGEX = Regex("\\d{2}:\\d{2} [AP]M: ")

        private fun Document.toAlertDatas(): List<AlertData> =
            select(".alertText").map { it.text() }
                .zip(select(".stationName").map { it.text() })
                .map {
                    DATE_FORMATTER.timeZone = TimeZone.getDefault()
                    val date = try {
                        DATE_FORMATTER.parse(it.second.trim())
                    } catch (_: Exception) {
                        null
                    }
                    AlertData(it.first.removeUnnecessaryText(), date)
                }.sortedBy { it.date }.distinctBy { it.text }

        private fun String.removeUnnecessaryText(): String {
            return replaceFirst(TIME_PREFIX_REGEX, "")
        }
    }
}

data class AlertDatas(
    val alerts: List<AlertData> = emptyList(),
    val hasError: Boolean = false,
)

data class AlertData(
    val text: String,
    val date: Date?,
)
