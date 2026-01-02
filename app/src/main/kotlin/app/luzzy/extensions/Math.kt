package app.luzzy.extensions

import kotlin.math.roundToInt

fun Int.roundToClosestMultipleOf(multipleOf: Int = 1): Int {
    return (toDouble() / multipleOf).roundToInt() * multipleOf
}
