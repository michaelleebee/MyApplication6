import org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(FAIL_ON_PROJECT_REPOS)
    versionCatalogs {
        create("libs") {

    }
    // Add the repositories you need
    // For example:
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MyApplication6"

include(/* ...projectPaths = */ ":app")}