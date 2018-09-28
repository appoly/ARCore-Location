package uk.co.appoly.arcorelocation.sensor;

import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;

public class LocationManager {

    private static final String LOGTAG = LocationManager.class.getSimpleName();

    private static volatile LocationManager mLocationManager;

    private long UPDATE_INTERVAL = 3000; //milliseconds
    private float SMALLEST_DISPLACEMENT = 5000; //meters
    private int ACCURACY_THRESHOLD = 150; //meters
    private volatile boolean locationUpdateStarted = false;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    //private Location lastLocation;
    public Location currentBestLocation;

    private FusedLocationProviderClient locationProviderClient;

    private ArrayList<LocationManagerInterface> observers = new ArrayList<>();

    public interface LocationManagerInterface{
        void onLocationUpdate(Location location);
    }

    private LocationManager(Context context) {
        Log.d(LOGTAG, "in LocationManager constructor");
        locationProviderClient = LocationServices.getFusedLocationProviderClient(context);
        startUpdatingLocation();
    }

    public synchronized static LocationManager getInstance(Context context){
        Log.d(LOGTAG, "in getInstance");
        if(mLocationManager == null){
            Log.d(LOGTAG, "mLocationManager == null");
            if(mLocationManager == null){
                Log.d(LOGTAG, "mLocationManager == null");
                mLocationManager = new LocationManager(context);
            }
        }
        Log.d(LOGTAG, "returning mLocationManager");
        return mLocationManager;
    }

    public void setLocationUpdateListener(LocationManagerInterface locationUpdateListener){
        Log.d(LOGTAG,"in setLocationUpdateListener");
        Log.d(LOGTAG,"observers.indexOf(locationUpdateListener) = " + observers.indexOf(locationUpdateListener));
        if(observers.indexOf(locationUpdateListener) == -1) {
            Log.d(LOGTAG, "adding to observers list");
            if(currentBestLocation != null) { //dispacht latest best location
                Log.d(LOGTAG, "dispatch latest currentBestLocation");
                locationUpdateListener.onLocationUpdate(currentBestLocation);
            }
            observers.add(locationUpdateListener); //add for future location updates
        }
        else
            Log.d(LOGTAG, "will not add to observers list");
    }

    public void removeLocationUpdateListener(LocationManagerInterface locationUpdateListener){
        Log.d(LOGTAG,"in removeLocationUpdateListener");
        Log.d(LOGTAG,"observers.indexOf(locationUpdateListener) = " + observers.indexOf(locationUpdateListener));
        boolean removed = observers.remove(locationUpdateListener);
        Log.d(LOGTAG, "removed = " + removed);
    }

    /****************************************************************************************************/
    /** Location update helper methods */
    private LocationRequest getLocationRequest() {
        if(mLocationRequest == null) {
            mLocationRequest = new LocationRequest();
            mLocationRequest.setInterval(UPDATE_INTERVAL);
            mLocationRequest.setFastestInterval(UPDATE_INTERVAL);
            //mLocationRequest.setSmallestDisplacement(SMALLEST_DISPLACEMENT);
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }
        return mLocationRequest;
    }

    private LocationCallback getLocationCallback() {
        if(mLocationCallback == null) {
            mLocationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    super.onLocationResult(locationResult);
                    Location location = locationResult.getLastLocation();
                    Log.d(LOGTAG, "location.getAccuracy() = " + location.getAccuracy());
                    if(location.getAccuracy() <= ACCURACY_THRESHOLD) {
                        if(currentBestLocation != null){
                            if(location.distanceTo(currentBestLocation) >= SMALLEST_DISPLACEMENT) {
                                Log.d(LOGTAG, "location.distanceTo(currentBestLocation) > SMALLEST_DISPLACEMENT = " + (location.distanceTo(currentBestLocation) > SMALLEST_DISPLACEMENT));
                                currentBestLocation = location;
                                dispatchLocation();
                            }
                        }
                        else{
                            currentBestLocation = location;
                            Log.d(LOGTAG, "currentBestLocation == null, setting current location");
                            dispatchLocation();
                        }
                    }
                }
            };
        }
        return mLocationCallback;
    }

    public synchronized void startUpdatingLocation(){
        Log.d(LOGTAG, "in startLocationUpdate");
        Log.d(LOGTAG, "start requesting location updates");
        Log.d(LOGTAG, "locationUpdateStarted = " + locationUpdateStarted);
        if(!locationUpdateStarted) {
            locationUpdateStarted = true;
            //noinspection MissingPermission
            locationProviderClient.requestLocationUpdates(getLocationRequest(), getLocationCallback(), Looper.myLooper());
        }
    }

    public synchronized void stopUpdatingLocation(){
        Log.d(LOGTAG, "in stopLocationUpdate");
        Log.d(LOGTAG, "stop requesting location updates");
        Log.d(LOGTAG, "locationUpdateStarted = " + locationUpdateStarted);
        try {
            if(locationUpdateStarted)
                locationProviderClient.removeLocationUpdates(getLocationCallback());
            locationUpdateStarted = false;
            currentBestLocation = null;
        }
        catch (Exception e){
            Log.d(LOGTAG, "error " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void dispatchLocation(){
        for(LocationManagerInterface locationManagerInterface : observers)
            locationManagerInterface.onLocationUpdate(currentBestLocation);
    }
    /****************************************************************************************************/

    /****************************************************************************************************/
    /** Update settings methods */
    private void setUpdateInterval(long updateInterval){
        this.UPDATE_INTERVAL = updateInterval;
    }

    private void setSmallestDisplacement(float smallestDisplacement){
        this.SMALLEST_DISPLACEMENT = smallestDisplacement;
    }

    private void setAccuracyThreshold(int accuracyThreshold){
        this.ACCURACY_THRESHOLD = accuracyThreshold;
    }

    public synchronized void updateLocationValues(long updateInterval, float smallesDisplacement, int accuracyThreshold){
        //-1 SKIP VALUE
        boolean restartLocationRequest = false;
        if(updateInterval != -1 && updateInterval != UPDATE_INTERVAL) {
            restartLocationRequest = true;
            setUpdateInterval(updateInterval);
        }
        if(smallesDisplacement != -1 && smallesDisplacement != SMALLEST_DISPLACEMENT){
            restartLocationRequest = true;
            setSmallestDisplacement(smallesDisplacement);
        }
        if(accuracyThreshold != -1 && accuracyThreshold != ACCURACY_THRESHOLD){
            restartLocationRequest = true;
            setAccuracyThreshold(ACCURACY_THRESHOLD);
        }

        if(restartLocationRequest){
            stopUpdatingLocation();
            mLocationRequest = null;
            startUpdatingLocation();
        }
    }

    /****************************************************************************************************/

}
