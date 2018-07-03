package uk.co.appoly.arcorelocation.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;

import uk.co.appoly.arcorelocation.LocationScene;

/**
 * Created by John on 02/03/2018.
 */

public class DeviceOrientation implements SensorEventListener {

    private SensorManager mSensorManager;

    // Gravity rotational data
    private float gravity[];

    // Magnetic rotational data
    private float magnetic[];
    private float accels[] = new float[3];
    private float mags[] = new float[3];
    private float[] values = new float[3];
    private float azimuth;
    public float pitch;
    public float roll;
    private LocationScene locationScene;

    /********************************************/
    private float rots[] = new float[3];
    private float[] mRotationMatrix = new float[16];
    private double mapsCameraBearing;
    /********************************************/

    // North
    public float currentDegree = 0f;

    public DeviceOrientation(LocationScene locationScene) {
        this.locationScene = locationScene;
        mSensorManager = (SensorManager) locationScene.mContext.getSystemService(Context.SENSOR_SERVICE);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        // Get the device heading
        float degree = -Math.round( event.values[0] );

        // Temporary fix until we can work out what's causing the anomalies
        if(degree != 1.0 && degree != 0 && degree != 2.0 && degree != -1.0)
            currentDegree = degree;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_MAGNETIC_FIELD:
                mags = event.values.clone();
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accels = event.values.clone();
                break;
            /*********************************************************************************/
            case Sensor.TYPE_ROTATION_VECTOR:
                rots = event.values.clone();

                SensorManager.getRotationMatrixFromVector(mRotationMatrix , rots);
                float[] orientation = new float[3];
                SensorManager.getOrientation(mRotationMatrix, orientation);
                mapsCameraBearing = orientation[0]* 57.2957795f;

                break;
            /*********************************************************************************/
        }

        if (mags != null && accels != null) {
            gravity = new float[9];
            magnetic = new float[9];
            SensorManager.getRotationMatrix(gravity, magnetic, accels, mags);
            float[] outGravity = new float[9];
            SensorManager.remapCoordinateSystem(gravity, SensorManager.AXIS_X,SensorManager.AXIS_Z, outGravity);
            SensorManager.getOrientation(outGravity, values);

            azimuth = values[0] * 57.2957795f;
            pitch = values[1] * 57.2957795f;
            roll = values[2] * 57.2957795f;
            mags = null;
            accels = null;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void resume() {
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void pause() {
        mSensorManager.unregisterListener(this);
    }

    /*******************************************************************************************/
    public double getMapsCameraBearing() {
        return mapsCameraBearing;
    }
    /*******************************************************************************************/

}
