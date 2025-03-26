package ca.amandeep.path.util

import androidx.compose.ui.graphics.Color

fun Color.darken(to: Float): Color = perc(Color.Black, this, 1.0f - to)

fun Color.lighten(to: Float): Color = perc(this, Color.White, to)

fun perc(a: Color, b: Color, percentage: Float): Color {
    require(percentage in 0.0f..1.0f) { "Percentage must be between 0.0 and 1.0" }

    return a.copy(
        red = perc(a.red, b.red, percentage),
        green = perc(a.green, b.green, percentage),
        blue = perc(a.blue, b.blue, percentage),
    )
}

fun perc(a: Int, b: Int, percentage: Float): Int {
    require(percentage in 0.0..1.0) { "Percentage must be between 0.0 and 1.0" }

    return (a * (1 - percentage) + b * percentage).toInt()
}

fun perc(a: Float, b: Float, percentage: Float): Float {
    require(percentage in 0.0..1.0) { "Percentage must be between 0.0 and 1.0" }

    return (a * (1 - percentage) + b * percentage).toFloat()
}
