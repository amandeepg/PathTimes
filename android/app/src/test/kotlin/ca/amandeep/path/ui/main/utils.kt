@file:Suppress("ktlint:standard:filename")

package ca.amandeep.path.ui.main

/**
 * Creates a cartesian product from the given lists.
 *
 * Example: ((1, 2), (a, b))
 * Result: ((1, a), (1, b), (2, a), (2,b))
 */
fun <T> List<List<T>>.cartesianProduct(): List<List<T>> {
    fun <T> cartesianProductInternal(index: Int, lists: List<List<T>>): List<MutableList<T>> {
        val result = mutableListOf<MutableList<T>>()
        if (index == -1) {
            result.add(mutableListOf())
        } else {
            for (obj in lists[index]) {
                for (set in cartesianProductInternal(index - 1, lists)) {
                    set.add(obj)
                    result.add(set)
                }
            }
        }
        return result
    }

    return cartesianProductInternal(this.size - 1, this)
}
