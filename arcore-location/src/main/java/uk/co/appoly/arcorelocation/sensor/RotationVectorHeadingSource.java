package uk.co.appoly.arcorelocation.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import uk.co.appoly.arcorelocation.geo.HeadingMath;

/**
 * Compass heading from Android's rotation vector sensor, which fuses the magnetometer, accelerometer
 * and gyroscope. Headings are relative to magnetic north; {@code GeoTracker} corrects them to true
 * north using the local magnetic declination.
 */
public final class RotationVectorHeadingSource implements HeadingSource, SensorEventListener {

    /** Accuracy assumed when the sensor doesn't report one, by Android accuracy status. */
    private static final double ACCURACY_HIGH_DEGREES = 10;
    private static final double ACCURACY_MEDIUM_DEGREES = 20;
    private static final double ACCURACY_LOW_DEGREES = 35;
    /** Phone compasses are rarely better than this, whatever the sensor claims. */
    private static final double BEST_REALISTIC_ACCURACY_DEGREES = 5;

    private final SensorManager sensorManager;
    @Nullable
    private final Sensor sensor;
    private final float[] rotationMatrix = new float[9];

    @Nullable
    private Listener listener;
    private boolean started;
    private int accuracyStatus = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM;

    public RotationVectorHeadingSource(@NonNull Context context) {
        sensorManager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    @Override
    public void start(@NonNull Listener listener) {
        this.listener = listener;
        if (started) {
            return;
        }
        if (sensor == null) {
            listener.onHeadingUnavailable("This device has no rotation vector sensor");
            return;
        }
        started = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        if (!started) {
            listener.onHeadingUnavailable("Could not register the rotation vector sensor");
        }
    }

    @Override
    public void stop() {
        if (started) {
            sensorManager.unregisterListener(this);
            started = false;
        }
        listener = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Listener current = listener;
        if (current == null || event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
            return;
        }
        if (accuracyStatus == SensorManager.SENSOR_STATUS_UNRELIABLE
                || accuracyStatus == SensorManager.SENSOR_STATUS_NO_CONTACT) {
            return;
        }
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        double heading = HeadingMath.backCameraHeadingDegrees(rotationMatrix);
        if (Double.isNaN(heading)) {
            return;
        }
        // Event timestamps are on the elapsedRealtimeNanos clock.
        long eventMillis = event.timestamp > 0 ? event.timestamp / 1_000_000L : SystemClock.elapsedRealtime();
        current.onHeading(new HeadingSample(heading, accuracyDegrees(event), true, eventMillis));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        accuracyStatus = accuracy;
    }

    private double accuracyDegrees(SensorEvent event) {
        // values[4] is the estimated heading accuracy in radians, or -1 if unavailable.
        if (event.values.length > 4 && event.values[4] > 0) {
            return Math.max(Math.toDegrees(event.values[4]), BEST_REALISTIC_ACCURACY_DEGREES);
        }
        switch (accuracyStatus) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                return ACCURACY_HIGH_DEGREES;
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                return ACCURACY_LOW_DEGREES;
            default:
                return ACCURACY_MEDIUM_DEGREES;
        }
    }
}
