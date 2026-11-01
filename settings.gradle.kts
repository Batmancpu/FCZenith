pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor is published via JitPack.
        maven("https://jitpack.io")
    }
}

rootProject.name = "BitChord"

// Build speed: reuse task outputs from earlier builds and run modules in parallel.
// Needs ~/.gradle/caches/build-cache-1 persisted between CI runs (see workflow).
gradle.startParameter.isBuildCacheEnabled = true
gradle.startParameter.isParallelProjectExecutionEnabled = true
include(":shared")
include(":sharedUi")

include(":app")
