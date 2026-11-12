import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Release signing comes from <root>/keystore.properties (never committed: storeFile, storePassword, keyAlias,
// keyPassword). Without that file the release APK is built unsigned.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }
// Contest file name: <team>_CarLog_release.apk  (override with -PcarlogTeam=팀명; path-unsafe characters become "_")
val carlogTeam: String = (project.findProperty("carlogTeam")?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "TEAM")
    .replace(Regex("""[\\/:*?"<>|\s]+"""), "_")

android {
    namespace = "com.carlog.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.carlog.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 5
        versionName = "0.3.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Pleos emulator is x86_64, vehicles are arm64: ship only those to keep the APK small.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    applicationVariants.all {
        if (buildType.name == "release") {
            outputs.all {
                (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = "${carlogTeam}_CarLog_release.apk"
            }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Core icons only (the UI draws category badges as text): material-icons-extended added megabytes to the APK.
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
