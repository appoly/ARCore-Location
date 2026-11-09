import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Optional ARCore API key for the Geospatial API. Put `arcore.apiKey=...` in local.properties.
// Without a key the example still works, using GPS and compass instead.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val arcoreApiKey: String = localProperties.getProperty("arcore.apiKey")
    ?: providers.gradleProperty("ARCORE_API_KEY").orNull
    ?: ""

android {
    namespace = "uk.co.appoly.arcorelocation.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "uk.co.appoly.arcorelocation.example"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = providers.gradleProperty("VERSION_NAME").get()
        manifestPlaceholders["arcoreApiKey"] = arcoreApiKey
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":arcore-location-sceneform"))
    implementation(libs.sceneform.ux)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
