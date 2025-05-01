@file:Suppress("TestFunctionName")

package ca.amandeep.path.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.amandeep.path.R

@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    includeNotifs: Boolean = false,
) {
    val height = 36
    val dpTpSp = 0.55f
    val paddingRatio = 0.3f

    Box(
        modifier = modifier
            .height(height.dp)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 25.dp, vertical = (height * paddingRatio).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier
                    .wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                    .padding(end = 5.dp),
                text = "2:28",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = (height * (1 - paddingRatio) * dpTpSp).sp,
            )
            if (includeNotifs) {
                Icon(
                    painter = painterResource(R.drawable.ic_twitter),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(end = 5.dp, top = 1.dp, bottom = 1.dp),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_snapchat),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(end = 5.dp, top = 1.dp, bottom = 1.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.SignalWifi4Bar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = 5.dp),
            )
            Icon(
                imageVector = Icons.Default.SignalCellular4Bar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = 5.dp),
            )
            Icon(
                imageVector = Icons.Default.BatteryFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically, unbounded = true),
                text = "82%",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = (height * (1 - paddingRatio) * dpTpSp).sp,
            )
        }
    }
}
