package uk.co.appoly.arcorelocation.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public final class ARLocationPermissionHelper {
    private static final int PERMISSION_CODE = 0;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final String LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;

    /**
     * Check to see we have the necessary permissions for this app.
     */
    public static boolean hasPermission(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION)
                == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity, LOCATION_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check to see we have the necessary permissions for this app, and ask for them if we don't.
     */
    public static void requestPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity, new String[]{CAMERA_PERMISSION, LOCATION_PERMISSION},
                PERMISSION_CODE);
    }

    /** Check to see if we need to show the rationale for this permission. */
    public static boolean shouldShowRequestPermissionRationale(Activity activity) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA_PERMISSION) || ActivityCompat.shouldShowRequestPermissionRationale(activity, LOCATION_PERMISSION);
    }

    /** Launch Application Setting to grant permission. */
    public static void launchPermissionSettings(Activity activity) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }
}
