package ca.amandeep.path.data.model

import ca.amandeep.path.data.model.AlertDatas.Companion.getGroupedAlerts
import ca.amandeep.path.data.model.AlertDatas.Companion.toAlertDatas
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test
import java.util.Date

class AlertDatasTest {
    @Test
    fun singleAlertWithTime() {
        val alertDatas = listOf(
            "6/5/2023 04:37 PM" to "04:37 PM: NWK-WTC delayed. Crew reported a bird. An update will be issued in approx. 15 mins.",
        ).toAlertDatas()
        assertThat(alertDatas.map { it.text }).containsExactly(
            "NWK-WTC delayed. Crew reported a bird. Update in 15 mins.",
        )
    }

    @Test
    fun singleAlertWithDuplicate() {
        val alertDatas = listOf(
            "6/5/2023 04:37 PM" to "04:37 PM: NWK-WTC delayed. Crew reported a bird. An update will be issued in approx. 15 mins.",
            "6/5/2023 04:39 PM" to "04:39 PM: NWK-WTC delayed. Crew reported a bird. An update will be issued in approx. 15 mins.",
        ).toAlertDatas()
        assertThat(alertDatas.map { it.text }).containsExactly(
            "NWK-WTC delayed. Crew reported a bird. Update in 15 mins.",
        )
    }

    @Test
    fun sortMultipleItems() {
        val alertDatas = listOf(
            "6/5/2023 04:37 PM" to "04:37 PM: NWK-WTC delayed. Bird has been saved. An update will be issued in approx. 15 mins.",
            "6/5/2023 01:02 PM" to "01:02 PM: NWK-WTC delayed. Crew reported a bird. An update will be issued in approx. 10 mins.",
            "6/5/2023 10:08 PM" to "10:08 PM: NWK-WTC delayed. Trains moving again.",
        ).toAlertDatas()
        assertThat(alertDatas.map { it.text }).containsExactly(
            "NWK-WTC delayed. Trains moving again.",
            "NWK-WTC delayed. Bird has been saved. Update in 15 mins.",
            "NWK-WTC delayed. Crew reported a bird. Update in 10 mins.",
        )
    }

    @Test
    fun groupRouteAlerts() {
        val alertDatas = listOf(
            "6/5/2023 04:37 PM" to "04:37 PM: NWK-WTC delayed. Bird has been saved. An update will be issued in approx. 15 mins.",
            "6/5/2023 01:01 PM" to "01:01 PM: NWK-WTC delayed. Crew reported a bird. An update will be issued in approx. 10 mins.",
            "6/5/2023 01:02 PM" to "01:02 PM: NWK-WTC delays continue. Crew reported a bird. An update will be issued in approx. 12 mins.",
            "6/5/2023 10:08 PM" to "10:08 PM: NWK-WTC almost done. Trains almost moving. An update will be issued in approx. 11 mins.",
        ).toAlertDatas().getGroupedAlerts()
        assertThat(alertDatas).containsExactly(
            AlertData.Grouped(
                title = AlertData.Grouped.Title.RouteTitle(persistentListOf(Route.NWK_WTC), "almost done"),
                main = AlertData.Single(
                    "Trains almost moving. Update in 11 mins.",
                    date = Date(1686017280000L),
                ),
                history = persistentListOf(
                    AlertData.Single(
                        "Bird has been saved.",
                        date = Date(1685997420000L),
                    ),
                    AlertData.Single(
                        "Crew reported a bird.",
                        date = Date(1685984520000L),
                    ),
                    AlertData.Single(
                        "Crew reported a bird.",
                        date = Date(1685984520000L - 60000L),
                    ),
                ),
            ),
        )
    }

    @Test
    fun groupRouteAlertsWithBlankItems() {
        val alertDatas = listOf(
            "6/5/2023 04:37 PM" to "04:37 PM: NWK-WTC delayed. Bird has been saved. An update will be issued in approx. 15 mins.",
            "6/5/2023 01:01 PM" to "01:01 PM: NWK-WTC delayed. Crew reported a bird. An update will be issued in approx. 10 mins.",
            "6/5/2023 01:02 PM" to "01:02 PM: NWK-WTC delays continue. We apologize for the inconvenience.",
            "6/5/2023 10:08 PM" to "10:08 PM: NWK-WTC almost done. Trains almost moving. An update will be issued in approx. 11 mins.",
        ).toAlertDatas().getGroupedAlerts()
        assertThat(alertDatas).containsExactly(
            AlertData.Grouped(
                title = AlertData.Grouped.Title.RouteTitle(persistentListOf(Route.NWK_WTC), "almost done"),
                main = AlertData.Single(
                    "Trains almost moving. Update in 11 mins.",
                    date = Date(1686017280000L),
                ),
                history = persistentListOf(
                    AlertData.Single(
                        "Bird has been saved.",
                        date = Date(1685997420000L),
                    ),
                    AlertData.Single(
                        "Delays continue.",
                        date = Date(1685984520000L),
                    ),
                    AlertData.Single(
                        "Crew reported a bird.",
                        date = Date(1685984520000L - 60000L),
                    ),
                ),
            ),
        )
    }

    @Test
    fun groupMultiRoutesAlerts() {
        val alertDatas = listOf(
            "6/5/2023 04:37 PM" to "04:37 PM: NWK-WTC, HOB-33 delayed. Bird has been saved. An update will be issued in approx. 15 mins.",
            "6/5/2023 01:01 PM" to "01:01 PM: NWK-WTC, HOB-33 delayed. Crew saw bird. An update will be issued in approx. 10 mins.",
            "6/5/2023 01:02 PM" to "01:02 PM: NWK-WTC, HOB-33 delays continue. Crew saw bird. An update will be issued in approx. 12 mins.",
            "6/5/2023 10:08 PM" to "10:08 PM: NWK-WTC, HOB-33 almost done. Trains movingish. An update will be issued in approx. 11 mins.",
        ).toAlertDatas().getGroupedAlerts()
        assertThat(alertDatas).containsExactly(
            AlertData.Grouped(
                title = AlertData.Grouped.Title.RouteTitle(
                    persistentListOf(Route.HOB_33, Route.NWK_WTC),
                    "almost done",
                ),
                main = AlertData.Single(
                    "Trains movingish. Update in 11 mins.",
                    date = Date(1686017280000L),
                ),
                history = persistentListOf(
                    AlertData.Single(
                        "Bird has been saved.",
                        date = Date(1685997420000L),
                    ),
                    AlertData.Single(
                        "Crew saw bird.",
                        date = Date(1685984520000L),
                    ),
                    AlertData.Single(
                        "Crew saw bird.",
                        date = Date(1685984520000L - 60000L),
                    ),
                ),
            ),
        )
    }

    @Test
    fun groupFreeformAlerts() {
        val alertDatas = listOf(
            "6/5/2023 04:37 PM" to "04:37 PM: Bird incident. Bird has been saved. An update will be issued in approx. 15 mins.",
            "6/5/2023 01:02 PM" to "01:02 PM: Bird incident. Crew reported a bird. An update will be issued in approx. 10 mins.",
            "6/5/2023 10:08 PM" to "10:08 PM: Bird incident. Trains moving again.",
        ).toAlertDatas().getGroupedAlerts()
        assertThat(alertDatas).containsExactly(
            AlertData.Grouped(
                title = AlertData.Grouped.Title.FreeformTitle("Bird incident"),
                main = AlertData.Single(
                    "Trains moving again.",
                    date = Date(1686017280000L),
                ),
                history = persistentListOf(
                    AlertData.Single(
                        "Bird has been saved.",
                        date = Date(1685997420000L),
                    ),
                    AlertData.Single(
                        "Crew reported a bird.",
                        date = Date(1685984520000L),
                    ),
                ),
            ),
        )
    }
}
