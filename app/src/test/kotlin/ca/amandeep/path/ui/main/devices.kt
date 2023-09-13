@file:Suppress("ktlint:standard:filename")

package ca.amandeep.path.ui.main

import app.cash.paparazzi.DeviceConfig
import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.Navigation
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen

// TODO: Pixel Tablet, Tab S9, Tab S9+, Tab S9 Ultra

val PIXEL_FOLD = DeviceConfig(
    screenHeight = 1840,
    screenWidth = 2208,
    xdpi = 378,
    ydpi = 378,
    orientation = ScreenOrientation.LANDSCAPE,
    density = Density.DPI_360,
    ratio = ScreenRatio.NOTLONG,
    size = ScreenSize.XLARGE,
    keyboard = Keyboard.QWERTY,
    touchScreen = TouchScreen.FINGER,
    keyboardState = KeyboardState.SOFT,
    softButtons = true,
    navigation = Navigation.NONAV,
    released = "May 10, 2023",
)

val PIXEL_FOLD_OUTER = PIXEL_FOLD.copy(
    screenHeight = 2092,
    screenWidth = 1080,
    xdpi = 408,
    ydpi = 408,
    orientation = ScreenOrientation.PORTRAIT,
    density = Density.DPI_400,
    ratio = ScreenRatio.LONG,
    size = ScreenSize.LARGE,
)

val GALAXY_Z_FOLD_5 = DeviceConfig(
    screenHeight = 1812,
    screenWidth = 2176,
    xdpi = 373,
    ydpi = 373,
    orientation = ScreenOrientation.LANDSCAPE,
    density = Density.DPI_360,
    ratio = ScreenRatio.NOTLONG,
    size = ScreenSize.XLARGE,
    keyboard = Keyboard.QWERTY,
    touchScreen = TouchScreen.FINGER,
    keyboardState = KeyboardState.SOFT,
    softButtons = true,
    navigation = Navigation.NONAV,
    released = "July 26, 2023",
)

val GALAXY_Z_FOLD_5_OUTER = GALAXY_Z_FOLD_5.copy(
    screenHeight = 2316,
    screenWidth = 904,
    xdpi = 401,
    ydpi = 401,
    orientation = ScreenOrientation.PORTRAIT,
    density = Density.DPI_400,
    ratio = ScreenRatio.LONG,
    size = ScreenSize.LARGE,
)

val GALAXY_Z_FLIP_5 = DeviceConfig(
    screenHeight = 2640,
    screenWidth = 1080,
    xdpi = 425,
    ydpi = 425,
    orientation = ScreenOrientation.PORTRAIT,
    density = Density.DPI_420,
    ratio = ScreenRatio.LONG,
    size = ScreenSize.LARGE,
    keyboard = Keyboard.QWERTY,
    touchScreen = TouchScreen.FINGER,
    keyboardState = KeyboardState.SOFT,
    softButtons = true,
    navigation = Navigation.NONAV,
    released = "July 26, 2023",
)

val GALAXY_Z_FLIP_5_OUTER = GALAXY_Z_FLIP_5.copy(
    screenHeight = 748,
    screenWidth = 720,
    xdpi = 306,
    ydpi = 306,
    orientation = ScreenOrientation.PORTRAIT,
    density = Density.DPI_300,
    ratio = ScreenRatio.NOTLONG,
    size = ScreenSize.SMALL,
)

val PIXEL_7 = DeviceConfig(
    screenHeight = 2400,
    screenWidth = 1080,
    xdpi = 416,
    ydpi = 416,
    orientation = ScreenOrientation.PORTRAIT,
    density = Density.DPI_420,
    ratio = ScreenRatio.LONG,
    size = ScreenSize.LARGE,
    keyboard = Keyboard.NOKEY,
    touchScreen = TouchScreen.FINGER,
    keyboardState = KeyboardState.SOFT,
    softButtons = true,
    navigation = Navigation.NONAV,
    released = "October 6, 2022",
)

val PIXEL_7_PRO = PIXEL_7.copy(
    screenHeight = 3120,
    screenWidth = 1440,
    xdpi = 512,
    ydpi = 512,
    density = Density.XXHIGH,
)

val PIXEL_7A = PIXEL_7.copy(
    screenHeight = 2400,
    screenWidth = 1080,
    xdpi = 429,
    ydpi = 429,
    density = Density.DPI_420,
    released = "May 10, 2023",
)

val GALAXY_S_23 = DeviceConfig(
    screenHeight = 2340,
    screenWidth = 1080,
    xdpi = 425,
    ydpi = 425,
    orientation = ScreenOrientation.PORTRAIT,
    density = Density.DPI_420,
    ratio = ScreenRatio.LONG,
    size = ScreenSize.LARGE,
    keyboard = Keyboard.NOKEY,
    touchScreen = TouchScreen.FINGER,
    keyboardState = KeyboardState.SOFT,
    softButtons = true,
    navigation = Navigation.NONAV,
    released = "February 1, 2023",
)

val GALAXY_S_23_PLUS = GALAXY_S_23.copy(
    xdpi = 393,
    ydpi = 393,
    density = Density.DPI_400,
)

val GALAXY_S_23_ULTRA = GALAXY_S_23.copy(
    screenHeight = 3088,
    screenWidth = 1440,
    xdpi = 500,
    ydpi = 500,
    density = Density.XXHIGH,
)

fun List<Any>.toLandscape(): List<Any>? = run {
    val deviceConfig = this[1] as DeviceConfig
    if (deviceConfig.orientation == ScreenOrientation.LANDSCAPE) {
        listOf(
            (this[0] as String) + " Landscape",
            deviceConfig.copy(
                orientation = ScreenOrientation.LANDSCAPE,
                screenWidth = deviceConfig.screenHeight,
                screenHeight = deviceConfig.screenWidth,
            ),
        )
    } else {
        null
    }
}

fun List<List<Any>>.andLandscape(): List<List<Any>> = run {
    this + mapNotNull { it.toLandscape() }
}
