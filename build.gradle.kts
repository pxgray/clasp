buildscript {
    // Pins R8 (see gradle/libs.versions.toml) so release builds are reproducible.
    repositories {
        google()
    }
    dependencies {
        classpath(libs.r8)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
}
