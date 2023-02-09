package ca.amandeep.path.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/**
 * Creates a flow that emits a value every [updateInterval].
 */
fun tickFlow(updateInterval: Duration) = flow {
    while (true) {
        emit(Unit)
        delay(updateInterval)
    }
}