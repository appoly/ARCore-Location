# ARCore-Location

Place AR content at real-world coordinates on Android. Give the library a latitude and longitude and it works out where that point is in your ARCore scene, using ARCore's Geospatial API when it's available and GPS plus compass when it isn't.

[![CI](https://github.com/appoly/ARCore-Location/actions/workflows/ci.yml/badge.svg)](https://github.com/appoly/ARCore-Location/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/appoly/ARCore-Location.svg)](https://jitpack.io/#appoly/ARCore-Location)

Version 2.0 is a rewrite. If you're upgrading from 1.x, read the [migration guide](https://github.com/appoly/ARCore-Location/wiki/Migrating-from-1.x) first; most method names have changed and coordinates now take latitude before longitude.

## Installation

Add JitPack to your repositories in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then add the library:

```kotlin
dependencies {
    implementation("com.github.appoly.ARCore-Location:arcore-location:2.0.0")
}
```

It works with ARCore directly and with any renderer, such as [SceneView](https://github.com/SceneView/sceneview-android), Filament or your own OpenGL code. It needs minSdk 24 and Java 17, and uses AndroidX.

If your app already uses Sceneform, there is also a Sceneform module; see [Existing Sceneform apps](#existing-sceneform-apps).

## Quick start

`GeoTracker` works out where real-world coordinates are in your ARCore session. Create one and tie it to your activity's lifecycle so location and compass updates start and stop with it:

```java
GeoTracker tracker = new GeoTracker(context);
getLifecycle().addObserver(tracker);
```

Then, every frame, after `session.update()`, give it the frame and ask where your target is:

```java
tracker.update(session, frame);
if (frame.getCamera().getTrackingState() == TrackingState.TRACKING && tracker.isReady()) {
    GeoPlacement placement = tracker.place(
            GeoCoordinate.of(51.50073, -0.12463), // latitude, longitude
            frame.getCamera().getPose(),
            25f,  // targets further away are drawn at this distance
            0f);  // height above the camera, in metres
    if (placement != null) {
        Anchor anchor = session.createAnchor(Pose.makeTranslation(
                placement.getWorldX(), placement.getWorldY(), placement.getWorldZ()));
        // Attach your content to the anchor.
    }
}
```

Don't create a new anchor every frame. Keep the anchor and replace it only when the placement has moved more than about 30 cm from it. `placement.getDistanceMetres()` and `getBearingDegrees()` give the real distance and direction, for labels.

`tracker.getStatus()` tells you whether it is still waiting for a GPS fix or compass reading, and `getSource()` whether it is using the Geospatial API. [Getting started](https://github.com/appoly/ARCore-Location/wiki/Getting-started) on the wiki covers anchors, status and custom sensors in more detail.

## Geospatial API

If the ARCore session has `Config.GeospatialMode.ENABLED` and ARCore can localise, the library switches to Geospatial poses automatically. These are accurate to a few metres and a few degrees, and better than that in areas with Street View coverage. GPS and compass usually manage 5 to 20 metres and 10 to 30 degrees.

To enable it you need an ARCore API key, or keyless authorisation, from Google Cloud. Follow Google's [Geospatial quickstart](https://developers.google.com/ar/develop/java/geospatial/enable), then enable the mode in your session config:

```java
if (session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
    config.setGeospatialMode(Config.GeospatialMode.ENABLED);
}
```

Without a key, ARCore reports an authorisation error and the library carries on with GPS and compass.

## Permissions

The library's manifest declares camera, fine and coarse location, and internet. Request them at runtime with `ArLocationPermissions.REQUIRED_PERMISSIONS`. Android 12 and later let the user grant approximate location only; `ArLocationPermissions.hasApproximateLocationOnly()` tells you when that has happened, because placement can then be off by a kilometre or more.

## How accurate is it?

The library pairs every GPS and compass reading with ARCore's tracked camera position and averages them, weighting each by its reported accuracy. Markers get steadier the longer a session runs, and a single bad GPS fix has little effect. Even so, with GPS and compass alone, expect markers within a few tens of metres of the camera to be noticeably off. Use the Geospatial API if you need better.

The compass is corrected from magnetic to true north automatically. Readings are only used when the camera is within about 60 degrees of horizontal, so ask users to hold the phone up.

[How placement works](https://github.com/appoly/ARCore-Location/wiki/How-placement-works) explains the method in more detail.

## Existing Sceneform apps

`arcore-location-sceneform` adds `LocationScene` and `LocationMarker` on top of the core, for apps already built on [Sceneform](https://github.com/sceneview/sceneform-android). It handles anchors, scaling, billboarding and overlapping markers.

Use it only to upgrade an existing app. Google archived Sceneform in 2020, and the community fork this module uses was archived in 2024. Its bundled Filament libraries aren't aligned for 16 KB memory pages, so Google Play won't accept new apps or updates that target Android 15 or later and include them.

```kotlin
implementation("com.github.appoly.ARCore-Location:arcore-location-sceneform:2.0.0")
```

```java
// Once you have an ArSceneView, for example from ArFragment.setOnViewCreatedListener:
LocationScene locationScene = new LocationScene(arSceneView);
getLifecycle().addObserver(locationScene); // no per-frame call needed

LocationMarker marker = new LocationMarker(GeoCoordinate.of(51.50073, -0.12463), node)
        .setHeight(2f)
        .setOnRenderListener(m -> label.setText(Math.round(m.getDistanceMetres()) + " m"));
locationScene.addMarker(marker);
```

[Sceneform markers](https://github.com/appoly/ARCore-Location/wiki/Sceneform-markers) on the wiki covers render distance, scaling and overlap options.

## Using a coding assistant

AI assistants often suggest 1.x code, which no longer compiles. Point yours at [`llms.txt`](llms.txt), a short summary of the current API. If you're working on the library itself, [`AGENTS.md`](AGENTS.md) has the build commands and project rules.

## Example app

The `example` module uses the Sceneform module. It shows labels at a few landmarks around the world, plus coloured spheres 20 metres north, east, south and west of where you start. To try the Geospatial API, add `arcore.apiKey=YOUR_KEY` to `local.properties`.

```sh
./gradlew :example:installDebug
```

## Building

Use JDK 17 or later. `./gradlew test` runs the unit tests, and `./gradlew build` builds and lints everything.

## Support

[Open an issue](https://github.com/appoly/ARCore-Location/issues) if you find a bug or something is unclear. The [wiki](https://github.com/appoly/ARCore-Location/wiki) has more detail on every part of the library.

## License

MIT. See [LICENSE](LICENSE).
