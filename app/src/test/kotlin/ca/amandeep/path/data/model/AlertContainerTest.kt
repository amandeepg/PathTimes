package ca.amandeep.path.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Date

class AlertContainerTest {

    @Test
    fun alertDatas_fail() {
        val alertDatas = AlertContainer(
            contentKey = "contentKey",
            content = "nothing",
        ).alertDatas
        assertThat(alertDatas.hasError).isTrue()
        assertThat(alertDatas.alerts).isEmpty()
    }

    @Test
    fun alertDatas_singleAlert() {
        val alertDatas = AlertContainer(
            contentKey = "contentKey",
            content = Fakes.SINGLE_ALERT,
        ).alertDatas
        assertThat(alertDatas.hasError).isFalse()

        assertThat(alertDatas.alerts).containsExactly(
            AlertData(
                text = "9 St and 23 St stations closed nightly from approximately 11:59pm until 5am the following morning for maintenance-related activity and periodic cleaning. Christopher St, 14 St, and 33 St entrances remain open.",
                date = Date(1_686_016_800_000L),
            ),
        )
    }
}
