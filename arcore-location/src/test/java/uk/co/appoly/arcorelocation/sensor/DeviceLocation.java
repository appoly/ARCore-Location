package uk.co.appoly.arcorelocation.sensor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.widget.Toast;

import uk.co.appoly.arcorelocation.LocationScene;

/**
 * Created by John on 02/03/2018.
 */

public class DeviceLocation implements LocationListener {

    private static final int TWO_MINUTES = 1000 * 60 * 2;
    public Location currentBestLocation;
    private LocationManager locationManager ;
    private String provider;

    public DeviceLocation() {

        try {
            // Getting LocationManager object
            locationManager = (LocationManager) LocationScene.mContext.getSystemService(Context.LOCATION_SERVICE);
            // Creating an empty criteria object
            Criteria criteria = new Criteria();

            // Getting the name of the provider that meets the criteria
            provider = locationManager.getBestProvider(criteria, false);

            if (provider != null && !provider.equals("")) {

                // Get the location from the given provider
                Location location = locationManager.getLastKnownLocation(provider);
                locationManager.requestLocationUpdates(provider, 1000, 1, this);

                if (location != null)
                    onLocationChanged(location);
                else
                    Toast.makeText(LocationScene.mContext, "Location can't be retrieved", Toast.LENGTH_SHORT).show();

            } else {
                Toast.makeText(LocationScene.mContext, "No Provider Found", Toast.LENGTH_SHORT).show();
            }

        } catch(SecurityException e) {
            Toast.makeText(LocationScene.mContext, "Enable location permissions from settings", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Only replaces current location if this reading is
     * more likely to be accurate
     * @param location
     * @return
     */
    protected boolean isBetterLocation(Location location) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    @Override
    public void onLocationChanged(Location location) {
        if(isBetterLocation(location)) {
            currentBestLocation = location;
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        permissionsCheck();
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    public void permissionsCheck() {
        if (ActivityCompat.checkSelfPermission(
                LocationScene.mContext,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                ) {

            // Check Permissions Now
            ActivityCompat.requestPermissions(
                    LocationScene.mActivity,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    0);
        }
    }
}
