plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "uk.co.appoly.arcorelocation.sceneform"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":arcore-location"))
    // Sceneform types (Node, ArSceneView) are part of this module's public API.
    api(libs.sceneform.core)

    testImplementation(libs.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = providers.gradleProperty("GROUP").get()
            artifactId = "arcore-location-sceneform"
            version = providers.gradleProperty("VERSION_NAME").get()
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("ARCore-Location Sceneform")
                description.set("Sceneform integration for ARCore-Location: show Sceneform nodes at real-world coordinates.")
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
