import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val signingFile = rootProject.file("../host/toolchain/signing.properties")
val labSigning = Properties().apply {
    if (!signingFile.exists()) error("Run scripts/bootstrap-signing.sh before building")
    signingFile.inputStream().use { load(it) }
}
android {
    namespace = "org.cattail.m30lab"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "org.cattail.m30lab"
        minSdk = 23
        targetSdk = 29
        versionCode = 1
        versionName = "0.1"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("../host/toolchain/m30-lab-debug.jks")
            storePassword = labSigning.getProperty("storePassword")
            keyAlias = "m30-lab-debug"
            keyPassword = labSigning.getProperty("keyPassword")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
