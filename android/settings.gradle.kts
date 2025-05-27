import de.fayard.refreshVersions.core.StabilityLevel

include(":app")
include(":data")
include(":json")
include(":main-core")
include(":strings")
include(":ui-alerts")
include(":ui-core")
include(":ui-stations")
include(":util")
include(":prefs")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    // See https://splitties.github.io/refreshVersions
    id("de.fayard.refreshVersions") version "0.60.5"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
////                                                   # available:"0.10.0"
////                                                   # available:"1.0.0-rc-1"
////                                                   # available:"1.0.0"
}

refreshVersions {
    rejectVersionIf {
        candidate.stabilityLevel != StabilityLevel.Stable
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "PATH"

gradle.beforeProject {
    buildDir = file("${project.rootProject.rootDir}/build/${project.name}")
}