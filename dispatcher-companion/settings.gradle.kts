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
rootProject.name = "dispatcher-companion"
include(":app")
include(":core:model")
include(":core:db")
include(":audio")
include(":asr")
include(":ai")
include(":broker")
include(":engine:extraction")
include(":engine:negotiation")
include(":engine:calculator")
