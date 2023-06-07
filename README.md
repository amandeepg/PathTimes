# Arrivals for PATH [![Build with Gradle](https://github.com/amandeepg/PathTimes/actions/workflows/gradle.yml/badge.svg)](https://github.com/amandeepg/PathTimes/actions/workflows/gradle.yml)

An Android app for riding [the PATH](https://en.wikipedia.org/wiki/PATH_(rail_system)) that shows the next arrivals for trains at every station.



Some features of the app:
* Much nicer looking than the official app.
* Shows next trains at every PATH station.
* Color-coded and styled using the same styles as on PATH signage and trains, so it should look familiar to new and old riders.
* Ordered by your closest station so you don't need to scroll.
* Optionally only show you the trains headed into NYC if you're in NJ, or into NJ if you're in NYC, so you only see the trains you actually care about.
* Optionally use short names like "WTC" or longer names like "World Trade Center". If you've lived here for a while the abbreviations are faster to read.
* When you lose WiFi/data signal, the ETAs will continue to count down based on the last information the app knows.
* Uses [Matt Razza's API](https://github.com/mrazza/path-data), the same source of data used in Citymapper and Transit.

## Development
Built with:
* 100% Kotlin
* UI in 100% [Jetpack Compose](https://developer.android.com/jetpack/compose)
* [Kotlin Flow](https://kotlinlang.org/docs/flow.html)
* [Android ViewModels](https://developer.android.com/topic/libraries/architecture/viewmodel)

## Screenshots
