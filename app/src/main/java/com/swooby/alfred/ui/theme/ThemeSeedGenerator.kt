package com.swooby.alfred.ui.theme

import android.graphics.Color
import kotlin.random.Random

object ThemeSeedGenerator {
    fun randomSeed(): Long {
        val hue = Random.nextInt(0, 360)
        val saturation = Random.nextDouble(0.45, 0.85).toFloat()
        val value = Random.nextDouble(0.55, 0.9).toFloat()
        val hsv = floatArrayOf(hue.toFloat(), saturation, value)
        val colorInt = Color.HSVToColor(hsv)
        return colorInt.toLong() and 0xFFFFFFFFL
    }
}
