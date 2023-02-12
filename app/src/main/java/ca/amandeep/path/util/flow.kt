package ca.amandeep.path.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Duration

/**
 * Combines this flow with a flow that emits a value every [uiUpdateInterval].
 * The resulting flow will emit the value of this flow every [uiUpdateInterval].
 */
fun <T> Flow<T>.repeat(uiUpdateInterval: Duration): Flow<T> =
    tickFlow(uiUpdateInterval).combine(this, takeSecond())
