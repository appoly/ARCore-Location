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

public class DeviceLocation implements LocationManager.LocationManagerInterface{

    private final String LOGTAG = DeviceLocation.class.getSimpleName();

    private LocationScene mLocationScene;
    public LocationManager locationManager;
    public Location currentBestLocation;

    public DeviceLocation(LocationScene locationScene) {
        Log.d(LOGTAG, "in DeviceLocation constructor");
        this.mLocationScene = locationScene;
        locationManager = LocationManager.getInstance(locationScene.mContext);
        locationManager.setLocationUpdateListener(this);
    }

    public void resume(){
        Log.d(LOGTAG, "in resume");
        if(locationManager != null) {
            Log.d(LOGTAG, "locationManager != null");
            locationManager.setLocationUpdateListener(this);
            locationManager.startUpdatingLocation();
        }
        else
            Log.d(LOGTAG, "locationManager == null");
    }

    public void pause(){
        Log.d(LOGTAG, "in pause");
        if(locationManager != null) {
            Log.d(LOGTAG, "locationManager != null");
            locationManager.removeLocationUpdateListener(this);
            locationManager.stopUpdatingLocation();
        }
        else
            Log.d(LOGTAG, "locationManager == null");
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

    /***********************************************************************/
    @Override
    public void onLocationUpdate(Location location) {
        currentBestLocation = location;
        locationEvents();
    }
    /***********************************************************************/
}
