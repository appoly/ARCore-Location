package uk.co.appoly.arcorelocation;

import android.app.Activity;
import android.content.Context;
import android.opengl.Matrix;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;

import java.util.ArrayList;

import uk.co.appoly.arcorelocation.sensor.DeviceLocation;
import uk.co.appoly.arcorelocation.sensor.DeviceOrientation;
import uk.co.appoly.arcorelocation.utils.LocationUtils;

/**
 * Created by John on 02/03/2018.
 */

public class LocationScene {

    // Anchors are currently re-drawn on an interval. There are likely better
    // ways of doing this, however it's sufficient for now.
    private final static int ANCHOR_REFRESH_INTERVAL = 1000 * 8; // 8 seconds
    public static Context mContext;
    public static Activity mActivity;

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] mAnchorMatrix = new float[16];
    public ArrayList<LocationMarker> mLocationMarkers = new ArrayList<>();

    public DeviceLocation deviceLocation;
    public DeviceOrientation deviceOrientation;

    // Limit of where to draw markers within AR scene.
    // They will auto scale, but this helps prevents rendering issues
    private int distanceLimit = 50;

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

    public LocationScene(Context mContext, Activity mActivity, Session mSession) {
        this.mContext = mContext;
        this.mActivity = mActivity;
        this.mSession = mSession;

        startCalculationTask();

        deviceLocation = new DeviceLocation();
        deviceOrientation = new DeviceOrientation();
    }



    public void draw(Frame frame) {

        // Refresh the anchors in the scene.
        // Needs to occur in the draw method, as we need details about the camera
        refreshAnchorsIfRequired(frame);

        // Draw each anchor with it's individual renderer.
        drawMarkers(frame);

        for(LocationMarker lm : mLocationMarkers) {
            if(lm.anchor == null) {
                anchorsNeedRefresh = true;
            }
        }

        if(frame.getCamera().getTrackingState() != TrackingState.TRACKING)
            anchorsNeedRefresh = true;
    }

    public void drawMarkers(Frame frame) {
        for (LocationMarker locationMarker : mLocationMarkers) {

            try {
                // Get the current pose of an Anchor in world space. The Anchor pose is updated
                // during calls to session.update() as ARCore refines its estimate of the world.

                float translation[] = new float[3];
                float rotation[] = new float[4];
                locationMarker.anchor.getPose().getTranslation(translation, 0);
                frame.getCamera().getPose().getRotationQuaternion(rotation, 0);

                Pose rotatedPose = new Pose(translation, rotation);
                rotatedPose.toMatrix(mAnchorMatrix, 0);

                int markerDistance = (int) Math.ceil(
                        LocationUtils.distance(
                                locationMarker.latitude,
                                deviceLocation.currentBestLocation.getLatitude(),
                                locationMarker.longitude,
                                deviceLocation.currentBestLocation.getLongitude(),
                                0,
                                0)
                );

                // Limit the distance of the Anchor within the scene.
                // Prevents uk.co.appoly.arcorelocation.rendering issues.
                int renderDistance = markerDistance;
                if (renderDistance > distanceLimit)
                    renderDistance = distanceLimit;


                float[] projectionMatrix = new float[16];
                frame.getCamera().getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

                // Get camera matrix and draw.
                float[] viewMatrix = new float[16];
                frame.getCamera().getViewMatrix(viewMatrix, 0);

                // Make sure marker stays the same size on screen, no matter the distance
                float scale = 3.0F / 10.0F * (float) renderDistance;

                // Distant markers a little smaller
                if (markerDistance > 3000)
                    scale *= 0.75F;

                // Compute lighting from average intensity of the image.
                final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

                locationMarker.renderer.updateModelMatrix(mAnchorMatrix, scale);
                locationMarker.renderer.draw(viewMatrix, projectionMatrix, lightIntensity);


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
                    double heightAdjustment = Math.round(renderDistance * (Math.tan(Math.toRadians(deviceOrientation.pitch))));

                    // Raise distant markers for better illusion of distance
                    // Hacky - but it works as a temporary measure
                    int cappedRealDistance = markerDistance > 500 ? 500 : markerDistance;
                    if (renderDistance != markerDistance)
                        heightAdjustment += 0.01F * (cappedRealDistance - renderDistance);

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

                    mLocationMarkers.get(i).anchor = newAnchor;

                    mLocationMarkers.get(i).renderer.createOnGlThread(mContext, markerDistance);

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
