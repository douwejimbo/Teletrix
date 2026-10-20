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
    }
}

rootProject.name = "NextGenMatrix"

include(
    ":app",
    ":core:common",
    ":core:model",
    ":core:ui",
    ":core:network",
    ":matrix",
    ":data:db",
    ":data:media",
    ":data:push",
    ":feature:auth",
    ":feature:chat",
    ":feature:media",
    ":feature:settings",
)
