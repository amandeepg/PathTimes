package ca.amandeep.path.data

import ca.amandeep.path.data.model.StationName
import ca.amandeep.path.data.model.SummarizeApiResponse
import ca.amandeep.path.data.model.UpcomingTrains
import ca.amandeep.path.util.tickFlow
import com.github.ajalt.timberkt.d
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Repository for getting stations and arrivals information. Stations are cached,
 * but arrivals are periodically polled from the API.
 */
class PathRepository(
    private val pathRemoteDataSource: PathRemoteDataSource,
    private val summarizerApi: PathAlertsSummarizerApiService,
    private val arrivalsUpdateInterval: Duration,
    private val alertsUpdateInterval: Duration,
) {
    private var refreshFlow = MutableSharedFlow<Unit>()

    /**
     * Get the list of arrivals for a station, periodically polling the API for updates.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val arrivals: Flow<ArrivalsResult>
        get() =
            // Merge tick flow to periodically poll the API, and the refresh flow to force a refresh
            merge(tickFlow(arrivalsUpdateInterval), refreshFlow).map {
                pathRemoteDataSource.getArrivals()
                    .let {
                        ArrivalsResult(
                            metadata = Metadata(System.currentTimeMillis()),
                            arrivals = it.mapValues { it.value.toImmutableList() }.toImmutableMap(),
                        ).also {
                            d { "new arrivals wallTime: ${it.metadata.lastUpdated}" }
                        }
                    }
            }

    @OptIn(FlowPreview::class)
    val alerts: Flow<AlertsResult>
        get() =
            // Merge tick flow to periodically poll the API, and the refresh flow to force a refresh
            merge(tickFlow(alertsUpdateInterval), refreshFlow).transform {
                val alertsResult = AlertsResult(
                    Metadata(System.currentTimeMillis()),
                    pathRemoteDataSource.getAlerts(),
                )
                emit(
                    alertsResult.also {
                        d { "new alerts wallTime: ${it.metadata.lastUpdated}" }
                    },
                )
                coroutineScope {
                    val originalAlerts = alertsResult.alerts.alerts
                    val processedAlerts = originalAlerts.toMutableList()
                    val channel = Channel<Pair<Int, AlertData>>()

                    // Launch async processing for each alert
                    val jobs = originalAlerts.mapIndexed { index, alert ->
                        launch {
                            channel.send(index to alert.maybeSummarizeAlertData())
                        }
                    }

                    // Close the channel once all processing is done
                    launch {
                        jobs.joinAll()
                        channel.close()
                    }

                    // Emit updates as each alert is processed
                    channel.consumeAsFlow().collect { (index, processedAlert) ->
                        processedAlerts[index] = processedAlert
                        emit(
                            alertsResult.copy(
                                alerts = alertsResult.alerts.copy(
                                    alerts = processedAlerts.toImmutableList(),
                                ),
                            ),
                        )
                    }
                }
            }.debounce(100.milliseconds)

    private suspend fun AlertData.maybeSummarizeAlertData(): AlertData =
        if (this is AlertData.Single && !text.isNullOrBlank()) {
            d { "starting summary... of $text" }
            val summarizeApiResponse = summarizerApi.summarize(text)
            val summarizedText = summarizeApiResponse.response.text
            d { "summarized alert: $summarizedText" }
            if (summarizedText.isNotBlank()) {
                createSummarizedAlertData(summarizedText, summarizeApiResponse)
            } else {
                this
            }
        } else {
            this
        }

    private fun AlertData.Single.createSummarizedAlertData(
        summarizedText: String,
        summarizeApiResponse: SummarizeApiResponse,
    ): AlertData {
        val newAlert = copy(text = summarizedText)
        val routes = summarizeApiResponse.response.affectedArea.affectedRoutes
        val stations = summarizeApiResponse.response.affectedArea.affectedStations
        return if (routes?.isNotEmpty() == true) {
            AlertData.GroupedWithLlm(
                title = AlertData.Grouped.Title.RouteTitle(
                    routes = routes.toImmutableList(),
                    text = "",
                ),
                main = newAlert,
                modelName = summarizeApiResponse.model,
                original = this,
            )
        } else if (stations?.isNotEmpty() == true) {
            AlertData.GroupedWithLlm(
                title = AlertData.Grouped.Title.StationTitle(
                    stations = stations.toImmutableList(),
                    text = "",
                ),
                main = newAlert,
                modelName = summarizeApiResponse.model,
                original = this,
            )
        } else {
            newAlert
        }
    }

    /**
     * Refreshes the data from the remote data source.
     */
    suspend fun refresh() = refreshFlow.emit(Unit)

    data class ArrivalsResult(
        val metadata: Metadata = Metadata(),
        val arrivals: ImmutableMap<StationName, ImmutableList<UpcomingTrains>> =
            persistentMapOf(),
    )

    data class AlertsResult(
        val metadata: Metadata = Metadata(),
        val alerts: AlertDatas = AlertDatas(),
    )

    data class Metadata(
        val lastUpdated: Long = -1,
    )
}
