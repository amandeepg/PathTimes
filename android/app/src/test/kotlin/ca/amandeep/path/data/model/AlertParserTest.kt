package ca.amandeep.path.data.model

import ca.amandeep.path.data.AlertData
import ca.amandeep.path.data.AlertParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Date

class AlertParserTest {
    @Test
    fun alertDatas_fail() {
        val alertDatas = AlertParser().parse(
            AlertContainer(
                contentKey = "contentKey",
                content = "nothing",
            ),
        )
        assertThat(alertDatas.hasError).isTrue()
        assertThat(alertDatas.alerts).isEmpty()
    }

    @Test
    fun alertDatas_singleAlert() {
        val alertDatas = AlertParser().parse(
            AlertContainer(
                contentKey = "contentKey",
                content = Fakes.SINGLE_ALERT,
            ),
        )
        assertThat(alertDatas.hasError).isFalse()

        assertThat(alertDatas.alerts).containsExactly(
            AlertData.Single(
                text = "9 St and 23 St stations closed nightly from approximately 11:59pm until 5am the following morning for " +
                    "maintenance-related activity and periodic cleaning. Christopher St, 14 St, and 33 St entrances remain open.",
                date = Date(1_686_016_800_000L),
            ),
        )
    }
}
