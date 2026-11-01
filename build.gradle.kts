plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.roborazzi) apply false
}

// Shared native toolchain versions; CI installs exactly these.
extra["ndkVersion"] = "27.2.12479018"
extra["cmakeVersion"] = "3.22.1"
