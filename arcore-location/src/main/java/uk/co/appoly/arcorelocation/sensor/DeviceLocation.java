package uk.co.appoly.arcorelocation.sensor;

import android.location.Location;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import uk.co.appoly.arcorelocation.LocationScene;

public class DeviceLocation {

    private final String LOGTAG = DeviceLocation.class.getSimpleName();

    private final long UPDATE_INTERVAL = 3000; //milliseconds
    private final float SMALLEST_DISPLACEMENT = 5000; //meters
    private final int ACCURACY_THRESHOLD = 150; //meters
    private boolean locationUpdateStarted = false;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    //private Location lastLocation;
    public Location currentBestLocation;

    private FusedLocationProviderClient locationProviderClient;

    private LocationScene mLocationScene;

    public DeviceLocation(LocationScene locationScene) {
        Log.d(LOGTAG, "in DeviceLocation constructor");
        this.mLocationScene = locationScene;
        locationProviderClient = LocationServices.getFusedLocationProviderClient(locationScene.mContext);
        startUpdatingLocation();
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
                    if(location.getAccuracy() <= ACCURACY_THRESHOLD) {
                        if(currentBestLocation != null){
                            if(location.distanceTo(currentBestLocation) > SMALLEST_DISPLACEMENT) {
                                Log.d(LOGTAG, "location.distanceTo(currentBestLocation) > SMALLEST_DISPLACEMENT = " + (location.distanceTo(currentBestLocation) > SMALLEST_DISPLACEMENT));
                                currentBestLocation = location;
                                locationEvents();
                            }
                        }
                        else{
                            currentBestLocation = location;
                            Log.d(LOGTAG, "currentBestLocation == null, setting current location");
                            locationEvents();
                        }
                    }
                }
            };
        }
        return mLocationCallback;
    }

    public void startUpdatingLocation(){
        Log.d(LOGTAG, "in startLocationUpdate");
        Log.d(LOGTAG, "start requesting location updates");
        Log.d(LOGTAG, "locationUpdateStarted = " + locationUpdateStarted);
        if(!locationUpdateStarted) {
            locationUpdateStarted = true;
            //noinspection MissingPermission
            locationProviderClient.requestLocationUpdates(getLocationRequest(), getLocationCallback(), Looper.myLooper());
        }
    }

    public void stopUpdatingLocation(){
        Log.d(LOGTAG, "in stopLocationUpdate");
        Log.d(LOGTAG, "stop requesting location updates");
        Log.d(LOGTAG, "locationUpdateStarted = " + locationUpdateStarted);
        try {
            if(locationUpdateStarted)
                locationProviderClient.removeLocationUpdates(getLocationCallback());
            locationUpdateStarted = false;
        }
        catch (Exception e){
            Log.d(LOGTAG, "error " + e.getMessage());
            e.printStackTrace();
        }
    }

    /****************************************************************************************************/

    public void locationEvents() {
        if (mLocationScene.getLocationChangedEvent() != null) {
            mLocationScene.getLocationChangedEvent().onChange(currentBestLocation);
        }

        if (mLocationScene.refreshAnchorsAsLocationChanges()) {
            mLocationScene.refreshAnchors();
        }
    }

}
