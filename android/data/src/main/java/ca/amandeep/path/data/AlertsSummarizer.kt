package ca.amandeep.path.data

import android.content.Context
import ca.amandeep.path.data.model.SummarizeApiResponse
import com.github.ajalt.timberkt.d
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class AlertsSummarizer(private val summarizerApi: PathAlertsSummarizerApiService, context: Context) {
    private val alertCache = AlertCache(context)

    fun Iterable<AlertData>.maybeSummarizeAlertDatas(): Flow<List<AlertData>> {
        val alertFlows = map { alertData ->
            alertData
                .maybeSummarizeAlertData()
                .onStart { emit(alertData) }
        }
        return if (alertFlows.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(alertFlows) { it.toList() }
        }
    }

    private fun AlertData.maybeSummarizeAlertData(): Flow<AlertData> =
        if (this is AlertData.Single && !text.isNullOrBlank()) {
            summarizeAlertText(text).map { summarizeApiResponse ->
                val summarizedText = summarizeApiResponse.result.text.ifBlank { summarizeApiResponse.summary }
                d { "summarized alert: $summarizedText" }
                if (summarizedText.isNotBlank()) {
                    createSummarizedAlertData(summarizedText, summarizeApiResponse)
                } else {
                    this
                }
            }
        } else {
            flowOf(this)
        }

    private fun summarizeAlertText(text: String): Flow<SummarizeApiResponse> = flow {
        d { "starting summary... of $text" }

        // Check cache first
        val cachedResult = alertCache.get(text)
        if (cachedResult != null) {
            d { "returning cached summary for: $text (cache hit, will refresh)" }
            emit(cachedResult)
        }

        // If not in cache, make API call
        val result = summarizerApi.summarize(text, "b")
        alertCache.put(text, result)
        emit(result)
    }

    private fun AlertData.Single.createSummarizedAlertData(
        summarizedText: String,
        summarizeApiResponse: SummarizeApiResponse,
    ): AlertData {
        val newAlert = copy(text = summarizedText)
        val response = summarizeApiResponse.result
        val affectedArea = response.affectedArea
        val routes = affectedArea?.affectedRoutes
        val stations = affectedArea?.affectedStations

        val title = when {
            !routes.isNullOrEmpty() -> AlertData.Grouped.Title.RouteTitle(
                routes = routes.toImmutableList(),
                text = "",
            )

            !stations.isNullOrEmpty() -> AlertData.Grouped.Title.StationTitle(
                stations = stations.toImmutableList(),
                text = "",
            )

            else -> null
        }

        return if (title != null) {
            AlertData.GroupedWithLlm(
                title = title,
                main = newAlert,
                modelName = summarizeApiResponse.llmType.orEmpty(),
                original = this,
            )
        } else {
            newAlert
        }
    }
}
