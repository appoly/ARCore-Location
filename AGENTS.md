# Working on ARCore-Location

Notes for coding agents and contributors. For what the library does and how to use it, read `README.md`; for how placement works, read the wiki.

## Layout

- `arcore-location/` is the core library. `GeoTracker` drives everything; `geo/WorldAlignment` holds the placement estimate; `sensor/` has the location and heading sources.
- `arcore-location-sceneform/` adds `LocationScene` and `LocationMarker` for Sceneform.
- `example-core/` is a demo app using the core module with ARCore directly: OpenGL camera background, one anchor per target, and Android views projected over the camera.
- `example/` is the same demo using the Sceneform module.

## Build and test

Use JDK 17 or later. Android Studio's bundled JDK works:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # macOS
./gradlew test       # unit tests
./gradlew build      # build, test and lint every module
```

CI (`.github/workflows/ci.yml`) runs tests, lint, a build of every module and `publishToMavenLocal`. Run `./gradlew build` before pushing.

## Rules

Coordinates are latitude first everywhere. Use `GeoCoordinate.of(latitude, longitude)` rather than passing raw doubles around.

Keep `geo/` and `GeoPlacement` free of Android imports so they can be unit tested on the JVM. Code that needs Android or ARCore types belongs in `GeoTracker`, the sensor sources or the Sceneform module.

Don't bump library dependencies to the newest AndroidX releases without checking. Some of them require consumers to compile against a newer Android SDK, and that requirement passes to every app using this library. The comment at the top of `gradle/libs.versions.toml` explains this.

Keep `android.builtInKotlin=false` in `gradle.properties`. The project is pure Java, and without this AGP adds `kotlin-stdlib` to the published POMs.

Take lint errors seriously. Lint has already caught a ternary over `ArrayDeque` and `ArrayList` that inferred `SequencedCollection`, a type that only exists on API 35 and later and would have crashed older phones.

Run any public API change past the README, the wiki and `llms.txt`. Their code examples are meant to compile against the current version.

## Releasing

Follow the wiki's Releasing page: check JitPack can build the commit, merge to `main`, tag the merge commit, and create a GitHub release. Never move or re-push a tag, because JitPack caches every build by tag, failed ones included. Release a new patch version instead.

## Writing docs

Write plainly: sentence-case headings, no em dashes, no bold labels on list items, and no claims about accuracy or behaviour that haven't been measured or tested.
