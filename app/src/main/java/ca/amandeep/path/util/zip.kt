package ca.amandeep.path.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.zip

/**
 * Combines the given flows into a single flow that emits a list of values.
 */
fun <T, R> zip(
    vararg flows: Flow<T>,
    transform: suspend (List<T>) -> R
): Flow<R> = zip(flows.toList(), transform)

/**
 * Combines the given flows into a single flow that emits a list of values.
 */
fun <T, R> zip(
    flows: List<Flow<T>>,
    transform: suspend (List<T>) -> R
): Flow<R> = when (flows.size) {
    0 -> emptyFlow()
    1 -> flows[0].map { transform(listOf(it)) }
    2 -> flows[0].zip(flows[1]) { a, b -> transform(listOf(a, b)) }
    else -> {
        var accFlow: Flow<List<T>> = flows[0].zip(flows[1]) { a, b -> listOf(a, b) }
        for (i in 2 until flows.size) {
            accFlow = accFlow.zip(flows[i]) { list, it ->
                list + it
            }
        }
        accFlow.map(transform)
    }
}

/**
 * Combines the given flows into a single flow that emits a list of values.
 */
@JvmName("zipExtension")
fun <T, R> List<Flow<T>>.zip(
    transform: suspend (List<T>) -> R
): Flow<R> = zip(this, transform)
