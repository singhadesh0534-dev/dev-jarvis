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
        // Silero VAD for Android (github.com/gkonovalov/android-vad, MIT): speech vs noise, on the phone.
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.gkonovalov.*") } }
    }
}
rootProject.name = "DevJarvis"
include(":app")
