plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dispatcher.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dispatcher.companion"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        // Redmi 14C (Helio G81-Ultra) is arm64; keep v7a for older handsets.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // Personal sideload key so every build installs/updates with one tap.
        // Replace with a private keystore before any store distribution.
        create("release") {
            storeFile = file("dispatcher-release.jks")
            storePassword = "dispatcher14c"
            keyAlias = "dispatcher"
            keyPassword = "dispatcher14c"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "META-INF/{AL2.0,LGPL2.1,INDEX.LIST,DEPENDENCIES}" }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:db"))
    implementation(project(":audio"))
    implementation(project(":asr"))
    implementation(project(":ai"))
    implementation(project(":broker"))
    implementation(project(":engine:extraction"))
    implementation(project(":engine:negotiation"))
    implementation(project(":engine:calculator"))

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
