@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package ca.amandeep.path.ui.main

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DropdownMenu
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import ca.amandeep.path.ui.getCurrentLocation
import ca.amandeep.path.ui.stations.StationsAndTrains
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import dev.burnoo.compose.rememberpreference.rememberBooleanPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private const val TAG = "MainScreen"

@Composable
fun MainScreen(mainViewModel: MainViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val currentLocation by getCurrentLocation()
    LaunchedEffect(currentLocation) {
        mainViewModel.setCurrentLocation(context, currentLocation)
    }

    val (refreshing, setRefreshing) = remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            val jobs = mutableListOf<Job>()
            jobs += async { delay(300) }
            jobs += mainViewModel.refreshTrainsFromNetwork()

            jobs.forEach { it.join() }
            setRefreshing(false)
        }
    }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                Log.d(TAG, "update server auto")
                mainViewModel.refreshTrainsFromNetwork()
                delay(30.seconds)
            }
        }
    }

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                Log.d(TAG, "update UI")
                mainViewModel.refreshTrainsTimes()
                delay(5.seconds)
            }
        }
    }
    val shortenNames = rememberBooleanPreference(
        keyName = "shortenNames",
        initialValue = false,
        defaultValue = false
    )
    val showOppositeDirection = rememberBooleanPreference(
        keyName = "showOppositeDirection",
        initialValue = true,
        defaultValue = true
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PATH Arrivals") },
                actions = {
                    IconButton(
                        onClick = { setRefreshing(true) }
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh"
                        )
                    }

                    var expanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More"
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                shortenNames.value = !shortenNames.value
                            }
                        ) {
                            Checkbox(
                                checked = shortenNames.value,
                                onCheckedChange = { shortenNames.value = it },
                            )
                            Text(
                                "Shorten station names",
                                modifier = Modifier.padding(end = 10.dp),
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                showOppositeDirection.value = !showOppositeDirection.value
                            }
                        ) {
                            Checkbox(
                                checked = showOppositeDirection.value,
                                onCheckedChange = { showOppositeDirection.value = it },
                            )
                            Text(
                                "Show opposite direction",
                                modifier = Modifier.padding(end = 10.dp),
                            )
                        }
                    }

                }
            )
        }
    ) { innerPadding ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = refreshing),
            onRefresh = { setRefreshing(true) },
        ) {
            StationsAndTrains(
                innerPadding = innerPadding,
                mainViewModel = mainViewModel,
                userState = UserState(
                    shortenNames = shortenNames,
                    showOppositeDirection = showOppositeDirection,
                    isInNJ = mainViewModel.isInNJ
                )
            )
        }
    }
}

class UserState(
    shortenNames: State<Boolean>,
    showOppositeDirection: State<Boolean>,
    isInNJ: State<Boolean>
) {
    val shortenNames by shortenNames
    val showOppositeDirection by showOppositeDirection
    val isInNJ by isInNJ
}
