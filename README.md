![Example Annotation](http://smegaupload.co.uk/up/uploads/arcorelocationbanner1524843962.png "ARCore location gps")

# ARCoreLocation

[Allows items to be placed within the AR world using real-world coordinates.](https://www.appoly.co.uk/arcore-location/)

Built for the ARCore Android SDK.

Version 1.x is now for SceneForm projects, and will not be compatible with old versions of ARCore.
If you are still using the older version of ARCore, change branch to "legacy". Note that legacy will not recieve frequent updates.


## Example usage
It's first required to set-up a basic ARCore sceneform project, or you can use our example.

### Importing the library
Add the JitPack repository to your build file

#### Step 1. 
Add JitPack in your root build.gradle at the end of repositories:
```
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```

#### Step 2. 
Add the ARCore-Location dependency. Replace `1.0.6` with the latest release from the [releases tab on Github](https://github.com/appoly/ARCore-Location/releases)
```
dependencies {
    compile 'com.github.appoly:ARCore-Location:1.0.6'
}
```

### Using the library

It's highly reccomended to use the example project as a reference.
To implement this library into one of your AR projects, do the following.

#### Step 1. 
Inside your AR Activity, you should create a new variable called `locationScene`, which will be the instance of this library.
```
private LocationScene locationScene;
```

#### Step 2.

Set up your renderables in onCreate
```
CompletableFuture<ModelRenderable> andy = ModelRenderable.builder()
        .setSource(this, R.raw.andy)
        .build();


CompletableFuture.allOf(andy)
        .handle(
                (notUsed, throwable) -> 
                {
                    if (throwable != null) {
                        DemoUtils.displayError(this, "Unable to load renderables", throwable);
                        return null;
                    }

                    try {
                        andyRenderable = andy.get();

                    } catch (InterruptedException | ExecutionException ex) {
                        DemoUtils.displayError(this, "Unable to load renderables", ex);
                    }
                    return null;
                });
```

#### Step 3.
Both your LocationScene, and various location markers should be instantiated in your onCreate method. Assuming you already have a arSceneView - the LocationScene and LocationMarkers can be set-up in the update listener.
```
arSceneView
.getScene()
.setOnUpdateListener(
frameTime -> {

    if (locationScene == null) {
        locationScene = new LocationScene(this, this, arSceneView);
        locationScene.mLocationMarkers.add(
                new LocationMarker(
                        -0.119677,
                        51.478494,
                        getAndy()));
    }

    ....

    if (locationScene != null) {
        locationScene.processFrame(frame);
    }

});
```

Where `getAndy()` is the following (also showing a onTapListener):

```
private Node getAndy() {
    Node base = new Node();
    base.setRenderable(andyRenderable);
    Context c = this;
    base.setOnTapListener((v, event) -> {
        Toast.makeText(
                c, "Andy touched.", Toast.LENGTH_LONG)
                .show();
    });
    return base;
}
```


#### Permissions
This library requires permission to use the device Camera and Fine Location. You should set this up in `AndroidManifest.xml`. If you're unfamiliar with requesting permissions, have a look at HelloArActivity in our example project.
```
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

The library provides `ARLocationPermissionHelper` - which is similar to the PermissionHelper used in Google's example projects, and provides a range of functions to correctly aquire the necessary permissions. Please see the example project for guiadance.


### Support
If you're having problems with the library, please [open a new issue](https://github.com/appoly/ARCore-Location/issues), and we'll aim to address it quickly.

#### Known issues
Mobile phone compasses only have an accuracy of about 15 degrees, even when calibrated. For most applications this is adequate, but when trying to superimpose markers over the real world it can be very problematic. This is a problem we haven’t resolved, and welcome your ideas on how best to do so!

### Contributing
We'd love your help in making this library better. Pull requests with new features and bug fixes are welcome.

### Apps built with ARCore-Location
[Where's my cAR?](https://play.google.com/store/apps/details?id=uk.co.appoly.wheres_my_car) - Appoly