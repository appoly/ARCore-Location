package uk.co.appoly.arcorelocation;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * An immutable WGS84 coordinate: latitude and longitude in degrees, with an optional altitude in
 * metres above the WGS84 ellipsoid.
 *
 * <p>Arguments are always latitude first, matching Android's {@code Location}, Google Maps and
 * ARCore's Geospatial API.</p>
 */
public final class GeoCoordinate {

    private final double latitude;
    private final double longitude;
    private final double altitude;

    private GeoCoordinate(double latitude, double longitude, double altitude) {
        if (!(latitude >= -90.0 && latitude <= 90.0)) {
            throw new IllegalArgumentException("latitude must be between -90 and 90, was " + latitude);
        }
        if (!(longitude >= -180.0 && longitude <= 180.0)) {
            throw new IllegalArgumentException("longitude must be between -180 and 180, was " + longitude);
        }
        if (Double.isInfinite(altitude)) {
            throw new IllegalArgumentException("altitude must be finite or NaN, was " + altitude);
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }

    /** A coordinate without altitude. */
    @NonNull
    public static GeoCoordinate of(double latitude, double longitude) {
        return new GeoCoordinate(latitude, longitude, Double.NaN);
    }

    /** A coordinate with altitude in metres above the WGS84 ellipsoid. */
    @NonNull
    public static GeoCoordinate of(double latitude, double longitude, double altitudeMetres) {
        return new GeoCoordinate(latitude, longitude, altitudeMetres);
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    /** Altitude in metres above the WGS84 ellipsoid, or {@code NaN} when unknown. */
    public double getAltitude() {
        return altitude;
    }

    public boolean hasAltitude() {
        return !Double.isNaN(altitude);
    }

    /** A copy of this coordinate with the given altitude. */
    @NonNull
    public GeoCoordinate withAltitude(double altitudeMetres) {
        return new GeoCoordinate(latitude, longitude, altitudeMetres);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoCoordinate)) return false;
        GeoCoordinate that = (GeoCoordinate) o;
        return Double.compare(latitude, that.latitude) == 0
                && Double.compare(longitude, that.longitude) == 0
                && Double.compare(altitude, that.altitude) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(latitude);
        result = 31 * result + Double.hashCode(longitude);
        result = 31 * result + Double.hashCode(altitude);
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        if (hasAltitude()) {
            return String.format(Locale.US, "GeoCoordinate(%.7f, %.7f, %.1fm)", latitude, longitude, altitude);
        }
        return String.format(Locale.US, "GeoCoordinate(%.7f, %.7f)", latitude, longitude);
    }
}
