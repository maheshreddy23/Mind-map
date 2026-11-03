import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

// The shipped app is arm64-v8a only. CI passes -PwithX86_64 to add an x86_64 build for the emulator.
val emulatorAbi = providers.gradleProperty("withX86_64").isPresent

android {
    namespace = "com.maheshreddy.mym"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maheshreddy.mym"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
            if (emulatorAbi) abiFilters += "x86_64"
        }
    }

    // Fixed signing key so consecutive builds install as updates. Each CI runner would otherwise generate a
    // random ~/.android/debug.keystore. The key is never committed: CI writes it from the DEBUG_KEYSTORE_B64
    // secret (see .github/workflows/android.yml); without it, the default debug key is used.
    val fixedKeystore = rootProject.file("keystore/debug.keystore")
    if (fixedKeystore.isFile) {
        signingConfigs {
            getByName("debug") {
                storeFile = fixedKeystore
                storePassword = providers.environmentVariable("DEBUG_KEYSTORE_PASSWORD").orElse("android").get()
                keyAlias = providers.environmentVariable("DEBUG_KEY_ALIAS").orElse("androiddebugkey").get()
                keyPassword = providers.environmentVariable("DEBUG_KEYSTORE_PASSWORD").orElse("android").get()
            }
        }
    }

    // Full: can download models (INTERNET, used only for model downloads). Sealed: no INTERNET permission at all;
    // models are imported from files. Same applicationId and signing key, so either installs over the other.
    flavorDimensions += "network"
    productFlavors {
        create("full") {
            dimension = "network"
            buildConfigField("boolean", "MODEL_DOWNLOADS", "true")
        }
        create("sealed") {
            dimension = "network"
            versionNameSuffix = "-sealed"
            buildConfigField("boolean", "MODEL_DOWNLOADS", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the release APK is installable for personal use.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Screenshot tests (Robolectric + Roborazzi) render real resources: the bundled fonts and themes.
        unitTests.isIncludeAndroidResources = true
    }

    // Exported Room schemas (app/schemas), so MigrationTestHelper can build every past database version.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":whisper"))
    implementation(project(":llama"))
    implementation(project(":tts"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.work.runtime)
    implementation(libs.biometric)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// Screenshot goldens live in src/test/screenshots (committed). Record: ./gradlew recordRoborazziFullDebug.
// CI runs testFullDebugUnitTest with -Proborazzi.test.verify=true and fails on any visual change.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}
