package uk.co.appoly.arcorelocation;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

import java.util.ArrayList;

import uk.co.appoly.arcorelocation.rendering.LocationNode;
import uk.co.appoly.arcorelocation.sensor.DeviceLocation;
import uk.co.appoly.arcorelocation.sensor.DeviceOrientation;
import uk.co.appoly.arcorelocation.utils.LocationUtils;

/**
 * Created by John on 02/03/2018.
 */

public class LocationScene {

    // Anchors are currently re-drawn on an interval. There are likely better
    // ways of doing this, however it's sufficient for now.
    private final static int ANCHOR_REFRESH_INTERVAL = 1000 * 5; // 5 seconds
    public static Context mContext;
    public static Activity mActivity;
    public static ArSceneView mArSceneView;

    public ArrayList<LocationMarker> mLocationMarkers = new ArrayList<>();

    public static DeviceLocation deviceLocation;
    public static DeviceOrientation deviceOrientation;

    // Limit of where to draw markers within AR scene.
    // They will auto scale, but this helps prevents rendering issues
    public static int distanceLimit = 20;

    // Bearing adjustment. Can be set to calibrate with true north
    private int bearingAdjustment = 0;

    private String TAG = "LocationScene";
    private boolean anchorsNeedRefresh = true;
    private Handler mHandler = new Handler();

    Runnable anchorRefreshTask = new Runnable() {
        @Override
        public void run() {
            anchorsNeedRefresh = true;
            mHandler.postDelayed(anchorRefreshTask, ANCHOR_REFRESH_INTERVAL);
        }
    };
    private Session mSession;

    public LocationScene(Context mContext, Activity mActivity, ArSceneView mArSceneView) {
        Log.i(TAG, "Location Scene initiated.");
        this.mContext = mContext;
        this.mActivity = mActivity;
        this.mSession = mArSceneView.getSession();
        this.mArSceneView = mArSceneView;

        startCalculationTask();

        deviceLocation = new DeviceLocation();
        deviceOrientation = new DeviceOrientation();
        deviceOrientation.resume();
    }

    public void processFrame(Frame frame) {
        refreshAnchorsIfRequired(frame);
    }

    public void refreshAnchorsIfRequired(Frame frame) {
        if (anchorsNeedRefresh) {
            anchorsNeedRefresh = false;

            for (int i = 0; i < mLocationMarkers.size(); i++) {
                try {

                    int markerDistance = (int) Math.round(
                            LocationUtils.distance(
                                    mLocationMarkers.get(i).latitude,
                                    deviceLocation.currentBestLocation.getLatitude(),
                                    mLocationMarkers.get(i).longitude,
                                    deviceLocation.currentBestLocation.getLongitude(),
                                    0,
                                    0)
                    );

                    float markerBearing = deviceOrientation.currentDegree + (float) LocationUtils.bearing(
                            deviceLocation.currentBestLocation.getLatitude(),
                            deviceLocation.currentBestLocation.getLongitude(),
                            mLocationMarkers.get(i).latitude,
                            mLocationMarkers.get(i).longitude);

                    // Bearing adjustment can be set if you are trying to
                    // correct the heading of north - setBearingAdjustment(10)
                    markerBearing = markerBearing + bearingAdjustment;
                    markerBearing = markerBearing % 360;

                    double rotation = Math.floor(markerBearing);

                    // When pointing device upwards (camera towards sky)
                    // the compass bearing can flip.
                    // In experiments this seems to happen at pitch~=-25
                    if (deviceOrientation.pitch > -25)
                        rotation = rotation * Math.PI / 180;

                    int renderDistance = markerDistance;

                    // Limit the distance of the Anchor within the scene.
                    // Prevents rendering issues.
                    if (renderDistance > distanceLimit)
                        renderDistance = distanceLimit;

                    // Adjustment to add markers on horizon, instead of just directly in front of camera
                    double heightAdjustment = 0;
                    // Math.round(renderDistance * (Math.tan(Math.toRadians(deviceOrientation.pitch)))) - 1.5F;

                    // Raise distant markers for better illusion of distance
                    // Hacky - but it works as a temporary measure
                    int cappedRealDistance = markerDistance > 500 ? 500 : markerDistance;
                    if (renderDistance != markerDistance)
                        heightAdjustment += 0.005F * (cappedRealDistance - renderDistance);

                    float x = 0;
                    float z = -renderDistance;

                    float zRotated = (float) (z * Math.cos(rotation) - x * Math.sin(rotation));
                    float xRotated = (float) -(z * Math.sin(rotation) + x * Math.cos(rotation));

                    // Current camera height
                    float y = frame.getCamera().getDisplayOrientedPose().ty();

                    // Don't immediately assign newly created anchor in-case of exceptions
                    Anchor newAnchor = mSession.createAnchor(
                            frame.getCamera().getPose()
                                    .compose(Pose.makeTranslation(xRotated, y + (float) heightAdjustment, zRotated)));

                    mLocationMarkers.get(i).anchorNode = new LocationNode(newAnchor, mLocationMarkers.get(i));
                    mLocationMarkers.get(i).anchorNode.setParent(mArSceneView.getScene());
                    mLocationMarkers.get(i).anchorNode.addChild(mLocationMarkers.get(i).node);

                    if(mLocationMarkers.get(i).getRenderEvent() != null) {
                        mLocationMarkers.get(i).anchorNode.setRenderEvent(mLocationMarkers.get(i).getRenderEvent());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
    }


    public int getBearingAdjustment() {
        return bearingAdjustment;
    }

    public void setBearingAdjustment(int i) {
        bearingAdjustment = i;
        anchorsNeedRefresh = true;
    }

    public void resume() {
        deviceOrientation.resume();
    }

    public void pause() {
        deviceOrientation.pause();
    }

    void startCalculationTask() {
        anchorRefreshTask.run();
    }

    void stopCalculationTask() {
        mHandler.removeCallbacks(anchorRefreshTask);
    }
}
