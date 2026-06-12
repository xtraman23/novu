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
include(":core:model")
include(":audio")
include(":asr")
include(":ai")
include(":engine:extraction")
include(":engine:negotiation")
include(":engine:calculator")
