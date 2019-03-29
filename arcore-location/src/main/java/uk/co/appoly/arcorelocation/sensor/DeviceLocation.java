package uk.co.appoly.arcorelocation.sensor;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

import uk.co.appoly.arcorelocation.LocationScene;
import uk.co.appoly.arcorelocation.utils.KalmanLatLong;

/**
 * Created by John on 02/03/2018.
 */

public class DeviceLocation implements LocationListener {

    private static final String TAG = DeviceLocation.class.getSimpleName();
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    public Location currentBestLocation;
    private boolean isLocationManagerUpdatingLocation;
    private ArrayList<Location> locationList;
    private ArrayList<Location> oldLocationList;
    private ArrayList<Location> noAccuracyLocationList;
    private ArrayList<Location> inaccurateLocationList;
    private ArrayList<Location> kalmanNGLocationList;
    private float currentSpeed = 0.0f; // meters/second
    private KalmanLatLong kalmanFilter;
    private int gpsCount = 0;
    private long runStartTimeInMillis;
    private LocationManager locationManager;
    private LocationScene locationScene;
    private int minimumAccuracy = 25;
    private Context context;

    public DeviceLocation(Context context, LocationScene locationScene) {
        this.context = context.getApplicationContext();
        this.locationScene = locationScene;
        isLocationManagerUpdatingLocation = false;
        locationList = new ArrayList<>();
        noAccuracyLocationList = new ArrayList<>();
        oldLocationList = new ArrayList<>();
        inaccurateLocationList = new ArrayList<>();
        kalmanNGLocationList = new ArrayList<>();
        kalmanFilter = new KalmanLatLong(3);

        startUpdatingLocation();
    }

    public int getMinimumAccuracy() {
        return minimumAccuracy;
    }

    public void setMinimumAccuracy(int minimumAccuracy) {
        this.minimumAccuracy = minimumAccuracy;
    }


    @Override
    public void onLocationChanged(final Location newLocation) {
        Log.d(TAG, "(" + newLocation.getLatitude() + "," + newLocation.getLongitude() + ")");

        gpsCount++;

        filterAndAddLocation(newLocation);
    }

    @Override
    public void onProviderDisabled(String provider) {
        startUpdatingLocation();
    }

    @Override
    public void onProviderEnabled(String provider) {
        try {
            locationManager.requestLocationUpdates(provider, 0, 0, this);
        } catch (SecurityException e) {

        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }


    public void startUpdatingLocation() {
        if (this.isLocationManagerUpdatingLocation == false) {
            isLocationManagerUpdatingLocation = true;
            runStartTimeInMillis = (long) (SystemClock.elapsedRealtimeNanos() / 1000000);


            locationList.clear();

            oldLocationList.clear();
            noAccuracyLocationList.clear();
            inaccurateLocationList.clear();
            kalmanNGLocationList.clear();

            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            //Exception thrown when GPS or Network provider were not available on the user's device.
            try {
                Criteria criteria = new Criteria();
                criteria.setAccuracy(Criteria.ACCURACY_FINE); //setAccuracyは内部では、https://stackoverflow.com/a/17874592/1709287の用にHorizontalAccuracyの設定に変換されている。
                criteria.setPowerRequirement(Criteria.POWER_HIGH);
                criteria.setAltitudeRequired(false);
                criteria.setSpeedRequired(true);
                criteria.setCostAllowed(true);
                criteria.setBearingRequired(false);

                //API level 9 and up
                criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
                criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);
                //criteria.setBearingAccuracy(Criteria.ACCURACY_HIGH);
                //criteria.setSpeedAccuracy(Criteria.ACCURACY_HIGH);

                Integer gpsFreqInMillis = 5000;
                Integer gpsFreqInDistance = 1;  // in meters

                //locationManager.addGpsStatusListener(this);

                locationManager.requestLocationUpdates(gpsFreqInMillis, gpsFreqInDistance, criteria, this, null);

                /* Battery Consumption Measurement */
                gpsCount = 0;

            } catch (IllegalArgumentException e) {
                Log.e(TAG, e.getLocalizedMessage());
            } catch (SecurityException e) {
                Log.e(TAG, e.getLocalizedMessage());
            } catch (RuntimeException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }
    }

    private void stopUpdatingLocation() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(this);
        isLocationManagerUpdatingLocation = false;
    }


    private long getLocationAge(Location newLocation) {
        long locationAge;
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            long currentTimeInMilli = (long) (SystemClock.elapsedRealtimeNanos() / 1000000);
            long locationTimeInMilli = (long) (newLocation.getElapsedRealtimeNanos() / 1000000);
            locationAge = currentTimeInMilli - locationTimeInMilli;
        } else {
            locationAge = System.currentTimeMillis() - newLocation.getTime();
        }
        return locationAge;
    }


    private boolean filterAndAddLocation(Location location) {

        if (currentBestLocation == null) {
            currentBestLocation = location;

            locationEvents();
        }

        long age = getLocationAge(location);

        if (age > 5 * 1000) { //more than 5 seconds
            Log.d(TAG, "Location is old");
            oldLocationList.add(location);

            if (locationScene.isDebugEnabled())
                Toast.makeText(context, "Rejected: old", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (location.getAccuracy() <= 0) {
            Log.d(TAG, "Latitidue and longitude values are invalid.");
            if (locationScene.isDebugEnabled())
                Toast.makeText(context, "Rejected: invalid", Toast.LENGTH_SHORT).show();
            noAccuracyLocationList.add(location);
            return false;
        }

        //setAccuracy(newLocation.getAccuracy());
        float horizontalAccuracy = location.getAccuracy();
        if (horizontalAccuracy > getMinimumAccuracy()) { //10meter filter
            Log.d(TAG, "Accuracy is too low.");
            inaccurateLocationList.add(location);
            if (locationScene.isDebugEnabled())
                Toast.makeText(context, "Rejected: innacurate", Toast.LENGTH_SHORT).show();
            return false;
        }


        /* Kalman Filter */
        float Qvalue;

        long locationTimeInMillis = (long) (location.getElapsedRealtimeNanos() / 1000000);
        long elapsedTimeInMillis = locationTimeInMillis - runStartTimeInMillis;

        if (currentSpeed == 0.0f) {
            Qvalue = 3.0f; //3 meters per second
        } else {
            Qvalue = currentSpeed; // meters per second
        }

        kalmanFilter.Process(location.getLatitude(), location.getLongitude(), location.getAccuracy(), elapsedTimeInMillis, Qvalue);
        double predictedLat = kalmanFilter.get_lat();
        double predictedLng = kalmanFilter.get_lng();

        Location predictedLocation = new Location("");//provider name is unecessary
        predictedLocation.setLatitude(predictedLat);//your coords of course
        predictedLocation.setLongitude(predictedLng);
        float predictedDeltaInMeters = predictedLocation.distanceTo(location);

        if (predictedDeltaInMeters > 60) {
            Log.d(TAG, "Kalman Filter detects mal GPS, we should probably remove this from track");
            kalmanFilter.consecutiveRejectCount += 1;

            if (kalmanFilter.consecutiveRejectCount > 3) {
                kalmanFilter = new KalmanLatLong(3); //reset Kalman Filter if it rejects more than 3 times in raw.
            }

            kalmanNGLocationList.add(location);
            if (locationScene.isDebugEnabled())
                Toast.makeText(context, "Rejected: kalman filter", Toast.LENGTH_SHORT).show();
            return false;
        } else {
            kalmanFilter.consecutiveRejectCount = 0;
        }


        Log.d(TAG, "Location quality is good enough.");
        currentBestLocation = predictedLocation;
        currentSpeed = location.getSpeed();
        locationList.add(location);

        locationEvents();


        return true;
    }

    public void locationEvents() {
        if (locationScene.getLocationChangedEvent() != null) {
            locationScene.getLocationChangedEvent().onChange(currentBestLocation);
        }

        if (locationScene.refreshAnchorsAsLocationChanges()) {
            locationScene.refreshAnchors();
        }
    }


    public void pause() {
        stopUpdatingLocation();
    }

    public void resume() {
        startUpdatingLocation();
    }
}