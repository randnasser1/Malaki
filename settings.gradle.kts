pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // Recommended for modern projects
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Malaki"
include(":app")
