package ca.amandeep.path.util

fun <T, U> takeSecond(): (a: T, b: U) -> U = { _, b -> b }

fun <T, U> takeFirst(): (a: T, b: U) -> T = { a, _ -> a }
