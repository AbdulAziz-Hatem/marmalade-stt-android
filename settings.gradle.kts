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

rootProject.name = "MarmaladeSTT"
include(":app")
include(":textproc")
// The shared Marmalade design library (#87), a pinned git submodule.
include(":marmalade-ui")
project(":marmalade-ui").projectDir = file("third_party/marmalade-ui-android/marmalade-ui")
