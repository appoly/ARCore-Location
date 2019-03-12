package uk.co.appoly.arcorelocation.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

/**
 * Created by John on 02/03/2018.
 */

public class DeviceOrientation implements SensorEventListener {

    public float pitch;
    public float roll;
    private WindowManager windowManager;
    private SensorManager mSensorManager;
    private float orientation = 0f;

    public DeviceOrientation(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * Gets the device orientation in degrees from the azimuth (clockwise)
     *
     * @return orientation [0-360] in degrees
     */
    public float getOrientation() {
        return orientation;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            processSensorOrientation(event.values);
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void processSensorOrientation(float[] rotation) {
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotation);
        final int worldAxisX;
        final int worldAxisY;

        switch (windowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                worldAxisX = SensorManager.AXIS_Z;
                worldAxisY = SensorManager.AXIS_MINUS_X;
                break;
            case Surface.ROTATION_180:
                worldAxisX = SensorManager.AXIS_MINUS_X;
                worldAxisY = SensorManager.AXIS_MINUS_Z;
                break;
            case Surface.ROTATION_270:
                worldAxisX = SensorManager.AXIS_MINUS_Z;
                worldAxisY = SensorManager.AXIS_X;
                break;
            case Surface.ROTATION_0:
            default:
                worldAxisX = SensorManager.AXIS_X;
                worldAxisY = SensorManager.AXIS_Z;
                break;
        }
        float[] adjustedRotationMatrix = new float[9];
        SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisX,
                worldAxisY, adjustedRotationMatrix);

        // azimuth/pitch/roll
        float[] orientation = new float[3];
        SensorManager.getOrientation(adjustedRotationMatrix, orientation);

        this.orientation = ((float) Math.toDegrees(orientation[0]) + 360f) % 360f;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.d("brako", "unreliable compass");
            return;
        }
    }

    public void resume() {
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void pause() {
        mSensorManager.unregisterListener(this);
    }
}