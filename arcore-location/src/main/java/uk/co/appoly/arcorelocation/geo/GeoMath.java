package uk.co.appoly.arcorelocation.geo;

import androidx.annotation.NonNull;

import uk.co.appoly.arcorelocation.GeoCoordinate;

/**
 * Spherical-earth geometry helpers. Accurate to well under 0.5% for the distances AR content is
 * placed at, which is far better than consumer GPS.
 */
public final class GeoMath {

    /** Mean earth radius in metres. */
    public static final double EARTH_RADIUS_METRES = 6_371_008.8;

    private GeoMath() {
    }

    /** Great-circle distance in metres, ignoring altitude. */
    public static double distanceMetres(@NonNull GeoCoordinate from, @NonNull GeoCoordinate to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(wrapDegrees(to.getLongitude() - from.getLongitude()));
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double a = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        // Floating point error can push a fractionally outside [0, 1] near antipodal points.
        a = Math.min(1.0, Math.max(0.0, a));
        return 2 * EARTH_RADIUS_METRES * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Initial great-circle bearing in degrees clockwise from true north, in [0, 360). */
    public static double bearingDegrees(@NonNull GeoCoordinate from, @NonNull GeoCoordinate to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double dLon = Math.toRadians(wrapDegrees(to.getLongitude() - from.getLongitude()));
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return normalizeDegrees(Math.toDegrees(Math.atan2(y, x)));
    }

    /**
     * Offset from {@code from} to {@code to} in metres as {east, north}, using the great-circle
     * bearing and distance so that distant targets keep the correct direction.
     */
    @NonNull
    public static double[] eastNorth(@NonNull GeoCoordinate from, @NonNull GeoCoordinate to) {
        double distance = distanceMetres(from, to);
        double bearing = Math.toRadians(bearingDegrees(from, to));
        return new double[]{distance * Math.sin(bearing), distance * Math.cos(bearing)};
    }

    /**
     * The coordinate reached by travelling {@code distanceMetres} from {@code start} along
     * {@code bearingDegrees}. The start altitude is kept.
     */
    @NonNull
    public static GeoCoordinate destination(@NonNull GeoCoordinate start, double bearingDegrees,
                                            double distanceMetres) {
        double angular = distanceMetres / EARTH_RADIUS_METRES;
        double bearing = Math.toRadians(bearingDegrees);
        double lat1 = Math.toRadians(start.getLatitude());
        double lon1 = Math.toRadians(start.getLongitude());
        double sinLat2 = Math.sin(lat1) * Math.cos(angular)
                + Math.cos(lat1) * Math.sin(angular) * Math.cos(bearing);
        double lat2 = Math.asin(Math.max(-1.0, Math.min(1.0, sinLat2)));
        double lon2 = lon1 + Math.atan2(
                Math.sin(bearing) * Math.sin(angular) * Math.cos(lat1),
                Math.cos(angular) - Math.sin(lat1) * sinLat2);
        GeoCoordinate result = GeoCoordinate.of(Math.toDegrees(lat2), wrapDegrees(Math.toDegrees(lon2)));
        return start.hasAltitude() ? result.withAltitude(start.getAltitude()) : result;
    }

    /** The coordinate offset from {@code origin} by the given east and north distances in metres. */
    @NonNull
    public static GeoCoordinate offset(@NonNull GeoCoordinate origin, double eastMetres, double northMetres) {
        double distance = Math.hypot(eastMetres, northMetres);
        if (distance == 0) {
            return origin;
        }
        double bearing = Math.toDegrees(Math.atan2(eastMetres, northMetres));
        return destination(origin, bearing, distance);
    }

    /** Normalises an angle to [0, 360). */
    public static double normalizeDegrees(double degrees) {
        double result = degrees % 360.0;
        if (result < 0) {
            result += 360.0;
        }
        // -0.0 % 360 and tiny negatives can round up to exactly 360.
        return result >= 360.0 ? 0.0 : result;
    }

    /** Wraps an angle to [-180, 180). */
    public static double wrapDegrees(double degrees) {
        double result = normalizeDegrees(degrees);
        return result >= 180.0 ? result - 360.0 : result;
    }

    /** Signed smallest difference {@code to - from} in degrees, in [-180, 180). */
    public static double angleDifference(double fromDegrees, double toDegrees) {
        return wrapDegrees(toDegrees - fromDegrees);
    }
}
