package ca.amandeep.path.util

fun <T, V> Iterable<Pair<T?, V?>>.filterNotNullPairs(): List<Pair<T, V>> = this
    .filterNot { it.first == null }
    .filterNot { it.second == null }
    .map { it.first!! to it.second!! }

inline fun <T, U, V> Iterable<T>.mapToNotNullPairs(transform: (T) -> Pair<U?, V?>): List<Pair<U, V>> =
    this.map(transform).filterNotNullPairs()
