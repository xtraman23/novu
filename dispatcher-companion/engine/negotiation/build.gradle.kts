plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":core:model"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
