pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "FAndroidTerminal"
include(":app", ":core:designsystem", ":core:terminal", ":core:runtime", ":core:sessions")

include(":termlib")
project(":termlib").projectDir = file("third_party/termlib/lib")
project(":termlib").buildFileName = "../../termlib.gradle.kts"
