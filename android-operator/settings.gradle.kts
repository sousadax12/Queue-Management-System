pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "queue-operator"

// The operator app is a THIRD app on the same contract, so it reuses the TV
// build's `:domain` (models, mappers' contract, pure logic) and `:data`
// (Firestore mappers, repositories, auth, RSS client) by pointing at their
// directories. One source of truth for the contract, no duplicated models.
// They keep their names because android-tv/data declares `project(":domain")`.
include(":app", ":ui", ":domain", ":data")
project(":domain").projectDir = file("../android-tv/domain")
project(":data").projectDir = file("../android-tv/data")
