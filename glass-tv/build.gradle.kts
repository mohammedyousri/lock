plugins {
    alias(libs.plugins.android.application) apply false
    // Declaring the Compose compiler plugin here also pins the Kotlin version
    // used by AGP's built-in Kotlin support.
    alias(libs.plugins.kotlin.compose) apply false
}
