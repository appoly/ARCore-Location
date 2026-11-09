plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "uk.co.appoly.arcorelocation"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // ARCore and lifecycle types are part of this module's public API.
    api(libs.arcore)
    api(libs.androidx.annotation)
    api(libs.androidx.lifecycle.common)
    // Fused location is used internally. ARCore's Geospatial API also requires it to be linked.
    implementation(libs.play.services.location)
    implementation(libs.androidx.core)

    testImplementation(libs.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = providers.gradleProperty("GROUP").get()
            artifactId = "arcore-location"
            version = providers.gradleProperty("VERSION_NAME").get()
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("ARCore-Location")
                description.set("Place ARCore content at real-world coordinates using GPS, compass and the ARCore Geospatial API.")
                url.set("https://github.com/appoly/ARCore-Location")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/appoly/ARCore-Location/blob/main/LICENSE")
                    }
                }
            }
        }
    }
}
