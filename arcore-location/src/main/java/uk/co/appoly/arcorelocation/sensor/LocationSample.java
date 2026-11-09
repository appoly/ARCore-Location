package uk.co.appoly.arcorelocation.sensor;

import android.location.Location;
import android.os.Build;

import androidx.annotation.NonNull;

import uk.co.appoly.arcorelocation.GeoCoordinate;

/** One location reading with its accuracy and timestamp. */
public final class LocationSample {

    private final GeoCoordinate coordinate;
    private final double horizontalAccuracyMetres;
    private final double verticalAccuracyMetres;
    private final long elapsedRealtimeMillis;

    /**
     * @param verticalAccuracyMetres  {@code NaN} if unknown
     * @param elapsedRealtimeMillis  when the reading was taken, on the
     *                               {@code SystemClock.elapsedRealtime()} clock
     */
    public LocationSample(@NonNull GeoCoordinate coordinate, double horizontalAccuracyMetres,
                          double verticalAccuracyMetres, long elapsedRealtimeMillis) {
        this.coordinate = coordinate;
        this.horizontalAccuracyMetres = horizontalAccuracyMetres;
        this.verticalAccuracyMetres = verticalAccuracyMetres;
        this.elapsedRealtimeMillis = elapsedRealtimeMillis;
    }

    /** Converts an Android {@link Location}. Altitude is kept only if it has a vertical accuracy. */
    @NonNull
    public static LocationSample fromLocation(@NonNull Location location) {
        double verticalAccuracy = Double.NaN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasAltitude()
                && location.hasVerticalAccuracy()) {
            verticalAccuracy = location.getVerticalAccuracyMeters();
        }
        GeoCoordinate coordinate = Double.isNaN(verticalAccuracy)
                ? GeoCoordinate.of(location.getLatitude(), location.getLongitude())
                : GeoCoordinate.of(location.getLatitude(), location.getLongitude(), location.getAltitude());
        double accuracy = location.hasAccuracy() ? location.getAccuracy() : Double.NaN;
        return new LocationSample(coordinate, accuracy, verticalAccuracy,
                location.getElapsedRealtimeNanos() / 1_000_000L);
    }

    @NonNull
    public GeoCoordinate getCoordinate() {
        return coordinate;
    }

    /** 68% confidence radius in metres, or {@code NaN} if unknown. */
    public double getHorizontalAccuracyMetres() {
        return horizontalAccuracyMetres;
    }

    /** 68% confidence vertical accuracy in metres, or {@code NaN} if unknown. */
    public double getVerticalAccuracyMetres() {
        return verticalAccuracyMetres;
    }

    public long getElapsedRealtimeMillis() {
        return elapsedRealtimeMillis;
    }
}
