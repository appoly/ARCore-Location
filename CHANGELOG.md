# Changelog

## 2.0.0

A rewrite. See the [migration guide](https://github.com/appoly/ARCore-Location/wiki/Migrating-from-1.x).

### Added

- A renderer-independent core module, `arcore-location`, built around `GeoTracker` and `GeoPlacement`.
- Support for ARCore's Geospatial API. The tracker uses Geospatial poses when the session has them and falls back to GPS and compass otherwise.
- `GeoCoordinate`, with latitude first and optional altitude.
- Marker altitude. With the Geospatial API, or a GPS fix with vertical accuracy, markers can be placed at a real altitude.
- Automatic magnetic declination correction.
- `GeoTracker` status and accuracy reporting, and listeners for status changes and accepted locations.
- `OverlapMode.STACK` and `OverlapMode.HIDE`, and `LocationScene.setMaxVisibleMarkers`.
- Lifecycle support: `LocationScene` and `GeoTracker` are `DefaultLifecycleObserver`s.
- Injectable `LocationSource` and `HeadingSource`, for custom providers and tests.
- Unit tests and GitHub Actions CI.

### Changed

- Placement no longer depends on how the phone is tilted. Markers are placed in ARCore's gravity-aligned world frame using an estimate of north and of the world origin's location that improves over time.
- Markers are re-anchored only when their ideal position moves, instead of every five seconds.
- Location comes from the fused location provider. The hand-written Kalman filter has been removed.
- One render distance is used for position, height and scaling. Distant markers keep their angle above or below the horizon.
- `ArLocationPermissions` requests fine and coarse location together, as Android 12 and later require.
- The build uses Android Gradle Plugin 9, Gradle 9, AndroidX and `maven-publish`.
- The Sceneform module uses the community Sceneform fork, 1.23.0.

### Removed

- `LocationScene.processFrame`, `setAnchorRefreshInterval`, `setMinimalRefreshing`, `setRefreshAnchorsAsLocationChanges`, `setBearingAdjustment` and `setDebugEnabled`.
- `DeviceLocation`, `DeviceOrientation`, `KalmanLatLong`, `LocationUtils` and `LocationNode`.
- The `app` and `examples/sceneform` projects, replaced by the `example` module.

### Fixed

- Anchor nodes are removed from the scene when replaced, instead of accumulating.
- Markers outside `onlyRenderWhenWithin` are hidden again when you walk away.
- Hidden overlapping markers reappear when they stop overlapping.
- Overlap stacking no longer raises markers further on every frame.

### Known issues

- Sceneform's Filament libraries are not 16 KB page aligned, so apps that include `arcore-location-sceneform` and target Android 15 or later can't be published to Google Play. Use the core module for new apps.

## 1.3.1

- Fixed the JitPack build by removing the Bintray plugin and replacing jcenter with Maven Central.

## 1.3

This release can't be installed from JitPack. Use 1.3.1.

- Fixed a crash when a location provider was re-enabled.
- Fixed the anchor refresh timer leaking the activity.
- Fixed negative and NaN scales in the gradual scaling modes.
