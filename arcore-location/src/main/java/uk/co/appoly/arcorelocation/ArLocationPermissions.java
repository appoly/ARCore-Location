package uk.co.appoly.arcorelocation;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Permission helpers for location-based AR.
 *
 * <p>Pass {@link #REQUIRED_PERMISSIONS} to an {@code ActivityResultContracts.RequestMultiplePermissions}
 * launcher. Fine and coarse location are requested together, as Android 12 and later require; the
 * user may still grant only approximate location, which {@link #hasApproximateLocationOnly} detects.</p>
 */
public final class ArLocationPermissions {

    /** Camera plus fine and coarse location. */
    public static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };

    private ArLocationPermissions() {
    }

    /** True if camera and precise location are granted. */
    public static boolean hasRequiredPermissions(@NonNull Context context) {
        return granted(context, Manifest.permission.CAMERA)
                && granted(context, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /**
     * True if the user granted approximate but not precise location. Placement will work but can be
     * off by a kilometre or more, and the Geospatial API is unavailable.
     */
    public static boolean hasApproximateLocationOnly(@NonNull Context context) {
        return granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                && !granted(context, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /** True if the system recommends explaining why any required permission is needed. */
    public static boolean shouldShowRationale(@NonNull Activity activity) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return true;
            }
        }
        return false;
    }

    /** Opens this app's page in system settings, for when the user chose "Don't ask again". */
    public static void openAppSettings(@NonNull Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }

    private static boolean granted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
