# ARCore-Location

Place AR content at real-world coordinates on Android. Give the library a latitude and longitude and it works out where that point is in your ARCore scene, using ARCore's Geospatial API when it's available and GPS plus compass when it isn't.

[![CI](https://github.com/appoly/ARCore-Location/actions/workflows/ci.yml/badge.svg)](https://github.com/appoly/ARCore-Location/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/appoly/ARCore-Location.svg)](https://jitpack.io/#appoly/ARCore-Location)

Version 2.0 is a rewrite. If you're upgrading from 1.x, read the [migration guide](https://github.com/appoly/ARCore-Location/wiki/Migrating-from-1.x) first; most method names have changed and `LocationMarker` now takes latitude before longitude.

## Modules

| Module | What it is |
| --- | --- |
| `arcore-location` | The core. Turns coordinates into ARCore world positions. Depends on ARCore and Google Play services only, so you can use it with any renderer. |
| `arcore-location-sceneform` | `LocationScene` and `LocationMarker` for [Sceneform](https://github.com/sceneview/sceneform-android). Handles anchors, scaling, billboarding and overlapping markers for you. |

### A note on Sceneform

Google archived Sceneform in 2020, and the community fork this module uses was archived in 2024. Its bundled Filament libraries aren't aligned for 16 KB memory pages, so Google Play won't accept new apps or updates that target Android 15 or later and include them.

The Sceneform module is there so existing 1.x apps can upgrade without rewriting their rendering. For a new app, use the core module with a maintained renderer such as [SceneView](https://github.com/SceneView/sceneview-android); see [Using another renderer](https://github.com/appoly/ARCore-Location/wiki/Using-another-renderer).

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

Then add one of the modules:

```kotlin
dependencies {
    // With Sceneform:
    implementation("com.github.appoly.ARCore-Location:arcore-location-sceneform:2.0.0")

    // Or the core only, for use with another renderer:
    implementation("com.github.appoly.ARCore-Location:arcore-location:2.0.0")
}
```

The library needs minSdk 24 and Java 17, and uses AndroidX.

## Quick start with Sceneform

```java
// Once you have an ArSceneView, for example from ArFragment.setOnViewCreatedListener:
LocationScene locationScene = new LocationScene(arSceneView);
getLifecycle().addObserver(locationScene);

Node node = new Node();
node.setRenderable(renderable);

LocationMarker marker = new LocationMarker(GeoCoordinate.of(51.50073, -0.12463), node)
        .setHeight(2f)
        .setOnRenderListener(m -> label.setText(Math.round(m.getDistanceMetres()) + " m"));
locationScene.addMarker(marker);
```

`LocationScene` updates itself every frame. Registering it as a lifecycle observer starts and stops location and compass updates with your activity and cleans up when it's destroyed.

A few settings worth knowing about:

```java
locationScene.setMaxRenderDistanceMetres(25f);       // further markers are drawn at this distance
locationScene.setOverlapMode(OverlapMode.STACK);     // or HIDE, or NONE
locationScene.setMaxVisibleMarkers(20);              // nearest first
marker.setOnlyRenderWhenWithinMetres(500);           // hide this marker when far away
marker.setScalingMode(ScalingMode.GRADUAL_FIXED_SIZE);
```

## Using the core directly

`GeoTracker` is independent of any renderer. Give it each ARCore frame, then ask where a coordinate is:

```java
GeoTracker tracker = new GeoTracker(context);
getLifecycle().addObserver(tracker);

// Every frame, after session.update():
tracker.update(session, frame);
GeoPlacement placement = tracker.place(target, frame.getCamera().getPose(), 25f, 0f);
if (placement != null) {
    Anchor anchor = session.createAnchor(Pose.makeTranslation(
            placement.getWorldX(), placement.getWorldY(), placement.getWorldZ()));
    // Attach your content to the anchor.
}
```

The wiki page [Using another renderer](https://github.com/appoly/ARCore-Location/wiki/Using-another-renderer) covers when to re-anchor and how to read the tracker's status.

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

## Example app

The `example` module shows labels at a few landmarks around the world, plus coloured spheres 20 metres north, east, south and west of where you start. To try the Geospatial API, add `arcore.apiKey=YOUR_KEY` to `local.properties`.

```sh
./gradlew :example:installDebug
```

## Using a coding assistant

AI assistants often suggest 1.x code, which no longer compiles. Point yours at [`llms.txt`](llms.txt), a short summary of the current API. If you're working on the library itself, [`AGENTS.md`](AGENTS.md) has the build commands and project rules.

## Building

Use JDK 17 or later. `./gradlew test` runs the unit tests, and `./gradlew build` builds and lints everything.

## Support

[Open an issue](https://github.com/appoly/ARCore-Location/issues) if you find a bug or something is unclear. The [wiki](https://github.com/appoly/ARCore-Location/wiki) has more detail on every part of the library.

## License

MIT. See [LICENSE](LICENSE).
