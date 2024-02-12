import de.fayard.refreshVersions.core.StabilityLevel

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.github.com/IlyaGulya/paparazzi") {
            name = "github"
            credentials(PasswordCredentials::class.java)
        }
    }
}

plugins {
    // See https://splitties.github.io/refreshVersions
    id("de.fayard.refreshVersions") version "0.60.5"
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
        maven("https://maven.pkg.github.com/IlyaGulya/paparazzi") {
            name = "github"
            credentials(PasswordCredentials::class.java)
        }
    }
}
rootProject.name = "PATH"
include(":app")