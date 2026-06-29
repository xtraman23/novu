plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:report"))
    implementation(project(":engine:extraction"))
    implementation(project(":engine:negotiation"))
    implementation(project(":engine:calculator"))
    implementation(project(":broker"))
    testImplementation(kotlin("test"))
}

application {
    // The PC "brains": reads a labelled transcript, prints live PDWCR + advice,
    // writes transcript.txt + info.txt. The Windows C# capture front-end pipes
    // "BROKER:/DISPATCHER:" lines into this on stdin.
    mainClass.set("com.dispatcher.companion.desktop.DispatchCliKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
