package uk.co.appoly.arcorelocation.sensor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Location from Google Play services' fused location provider. ARCore already requires Google Play
 * services for AR, so this adds no new requirement.
 */
public final class FusedLocationSource implements LocationSource {

    private final Context context;
    private final FusedLocationProviderClient client;
    private final long intervalMillis;

    @Nullable
    private Listener listener;
    private boolean started;

    private final LocationCallback callback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult result) {
            Listener current = listener;
            if (current == null) {
                return;
            }
            for (Location location : result.getLocations()) {
                current.onLocation(LocationSample.fromLocation(location));
            }
        }
    };

    public FusedLocationSource(@NonNull Context context) {
        this(context, 1000);
    }

    public FusedLocationSource(@NonNull Context context, long intervalMillis) {
        this.context = context.getApplicationContext();
        this.client = LocationServices.getFusedLocationProviderClient(this.context);
        this.intervalMillis = intervalMillis;
    }

    @Override
    public void start(@NonNull Listener listener) {
        this.listener = listener;
        if (started) {
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onLocationUnavailable("Location permission has not been granted");
            return;
        }
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
                .setMinUpdateIntervalMillis(intervalMillis / 2)
                .build();
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnFailureListener(e -> {
                        started = false;
                        Listener current = this.listener;
                        if (current != null) {
                            current.onLocationUnavailable("Location updates failed: " + e.getMessage());
                        }
                    });
            started = true;
        } catch (SecurityException e) {
            listener.onLocationUnavailable("Location permission has not been granted");
        }
    }

    @Override
    public void stop() {
        if (started) {
            client.removeLocationUpdates(callback);
            started = false;
        }
        listener = null;
    }
}
