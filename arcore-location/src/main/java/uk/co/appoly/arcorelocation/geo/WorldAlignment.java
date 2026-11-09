package uk.co.appoly.arcorelocation.geo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import uk.co.appoly.arcorelocation.GeoCoordinate;

/**
 * Estimates how ARCore's world frame lines up with the real world.
 *
 * <p>ARCore tracks the device very accurately relative to where the session started, but it does
 * not know which way north is or where on earth that start point is. GPS and compass readings know
 * both, but are noisy. This class pairs each reading with the camera's ARCore pose at the same
 * moment and combines many pairs, weighted by their reported accuracy, into two estimates:</p>
 *
 * <ul>
 *     <li>the world yaw that points to true north, and</li>
 *     <li>the geographic position (and, when available, altitude) of the world origin.</li>
 * </ul>
 *
 * <p>Because the estimates average over time, placement gets steadier the longer a session runs,
 * and a single bad GPS fix has little effect. Geospatial API samples, which are far more accurate,
 * feed into the same estimates and naturally dominate the weighting.</p>
 *
 * <p>This class is not thread safe; use it from one thread.</p>
 */
public final class WorldAlignment {

    /** Minimum accuracy assumed for any sample, so one very confident reading cannot dominate. */
    private static final double MIN_POSITION_ACCURACY_METRES = 0.5;
    private static final double MIN_HEADING_ACCURACY_DEGREES = 0.5;

    private final ArrayDeque<HeadingSample> headingSamples = new ArrayDeque<>();
    private final ArrayDeque<PositionSample> positionSamples = new ArrayDeque<>();

    private int maxHeadingSamples = 300;
    private int maxPositionSamples = 60;
    private long maxSampleAgeMillis = 120_000;

    @Nullable
    private GeoCoordinate reference;

    // Cached estimates, recomputed when samples change.
    private boolean dirty = true;
    private double northYawDegrees = Double.NaN;
    private double northYawAccuracyDegrees = Double.NaN;
    private double originEast = Double.NaN;
    private double originNorth = Double.NaN;
    private double originAltitude = Double.NaN;
    private double originAccuracyMetres = Double.NaN;

    /** Discards all samples. Call this when the ARCore session or its world frame is reset. */
    public void reset() {
        headingSamples.clear();
        positionSamples.clear();
        reference = null;
        dirty = true;
    }

    /**
     * Adds a heading observation: at a moment when the camera faced {@code cameraYawDegrees} in the
     * ARCore world frame, it was measured to face {@code trueHeadingDegrees} relative to true north.
     */
    public void addHeadingSample(double cameraYawDegrees, double trueHeadingDegrees,
                                 double accuracyDegrees, long timeMillis) {
        if (!isFinite(cameraYawDegrees) || !isFinite(trueHeadingDegrees) || !(accuracyDegrees > 0)) {
            return;
        }
        double northYaw = GeoMath.normalizeDegrees(cameraYawDegrees - trueHeadingDegrees);
        headingSamples.addLast(new HeadingSample(northYaw,
                Math.max(accuracyDegrees, MIN_HEADING_ACCURACY_DEGREES), timeMillis));
        while (headingSamples.size() > maxHeadingSamples) {
            headingSamples.removeFirst();
        }
        dirty = true;
    }

    /**
     * Adds a position observation: the camera was at {@code cameraLocation} (from GPS or the
     * Geospatial API) while at world position ({@code worldX}, {@code worldY}, {@code worldZ}).
     *
     * @param verticalAccuracyMetres use {@code NaN} if the altitude is unknown or unreliable.
     */
    public void addPositionSample(@NonNull GeoCoordinate cameraLocation,
                                  double horizontalAccuracyMetres, double verticalAccuracyMetres,
                                  double worldX, double worldY, double worldZ, long timeMillis) {
        if (!(horizontalAccuracyMetres > 0) || !isFinite(worldX) || !isFinite(worldZ)) {
            return;
        }
        if (reference == null) {
            reference = GeoCoordinate.of(cameraLocation.getLatitude(), cameraLocation.getLongitude());
        }
        double[] en = GeoMath.eastNorth(reference, cameraLocation);
        boolean altitudeUsable = cameraLocation.hasAltitude() && verticalAccuracyMetres > 0
                && isFinite(worldY);
        positionSamples.addLast(new PositionSample(en[0], en[1],
                altitudeUsable ? cameraLocation.getAltitude() - worldY : Double.NaN,
                Math.max(horizontalAccuracyMetres, MIN_POSITION_ACCURACY_METRES),
                altitudeUsable ? Math.max(verticalAccuracyMetres, MIN_POSITION_ACCURACY_METRES) : Double.NaN,
                worldX, worldZ, timeMillis));
        while (positionSamples.size() > maxPositionSamples) {
            positionSamples.removeFirst();
        }
        dirty = true;
    }

    /** Drops samples older than the maximum sample age, measured against {@code nowMillis}. */
    public void expireSamples(long nowMillis) {
        long cutoff = nowMillis - maxSampleAgeMillis;
        // Always keep the newest sample of each kind so a quiet sensor doesn't wipe the estimate.
        while (headingSamples.size() > 1 && headingSamples.peekFirst().timeMillis < cutoff) {
            headingSamples.removeFirst();
            dirty = true;
        }
        while (positionSamples.size() > 1 && positionSamples.peekFirst().timeMillis < cutoff) {
            positionSamples.removeFirst();
            dirty = true;
        }
    }

    public boolean hasHeading() {
        return !headingSamples.isEmpty();
    }

    public boolean hasPosition() {
        return !positionSamples.isEmpty();
    }

    /** True when both north and the world origin have been estimated. */
    public boolean isReady() {
        return hasHeading() && hasPosition();
    }

    /** World yaw that points to true north, or {@code NaN} if unknown. */
    public double getNorthYawDegrees() {
        update();
        return northYawDegrees;
    }

    /** Approximate 1-sigma accuracy of {@link #getNorthYawDegrees()}, or {@code NaN}. */
    public double getNorthYawAccuracyDegrees() {
        update();
        return northYawAccuracyDegrees;
    }

    /** Approximate 1-sigma horizontal accuracy of the origin estimate in metres, or {@code NaN}. */
    public double getHorizontalAccuracyMetres() {
        update();
        return originAccuracyMetres;
    }

    public boolean hasAltitude() {
        update();
        return isFinite(originAltitude);
    }

    /** Estimated geographic location of a world-space point, or {@code null} if not ready. */
    @Nullable
    public GeoCoordinate worldToGeo(double worldX, double worldY, double worldZ) {
        update();
        if (!isReady() || reference == null) {
            return null;
        }
        double[] en = worldToEastNorth(worldX, worldZ, northYawDegrees);
        GeoCoordinate result = GeoMath.offset(reference, originEast + en[0], originNorth + en[1]);
        if (isFinite(originAltitude) && isFinite(worldY)) {
            result = result.withAltitude(originAltitude + worldY);
        }
        return result;
    }

    /**
     * Horizontal world-space offset {x, z} from the camera at ({@code cameraX}, {@code cameraZ}) to
     * {@code target}, or {@code null} if not ready.
     */
    @Nullable
    public double[] worldOffsetTo(@NonNull GeoCoordinate target, double cameraX, double cameraZ) {
        GeoCoordinate camera = worldToGeo(cameraX, Double.NaN, cameraZ);
        if (camera == null) {
            return null;
        }
        double[] en = GeoMath.eastNorth(camera, target);
        return eastNorthToWorld(en[0], en[1], northYawDegrees);
    }

    /** World Y of a WGS84 altitude, or {@code NaN} if altitude has not been estimated. */
    public double altitudeToWorldY(double altitudeMetres) {
        update();
        if (!isFinite(originAltitude) || !isFinite(altitudeMetres)) {
            return Double.NaN;
        }
        return altitudeMetres - originAltitude;
    }

    public void setMaxHeadingSamples(int maxHeadingSamples) {
        this.maxHeadingSamples = Math.max(1, maxHeadingSamples);
    }

    public void setMaxPositionSamples(int maxPositionSamples) {
        this.maxPositionSamples = Math.max(1, maxPositionSamples);
    }

    /** Samples older than this stop counting, limiting the effect of slow ARCore drift. */
    public void setMaxSampleAgeMillis(long maxSampleAgeMillis) {
        this.maxSampleAgeMillis = Math.max(1, maxSampleAgeMillis);
    }

    /** Converts a world-space horizontal vector to {east, north}. */
    @NonNull
    static double[] worldToEastNorth(double x, double z, double northYawDegrees) {
        double a = Math.toRadians(northYawDegrees);
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        return new double[]{x * cos + z * sin, x * sin - z * cos};
    }

    /** Converts {east, north} to a world-space horizontal vector {x, z}. */
    @NonNull
    static double[] eastNorthToWorld(double east, double north, double northYawDegrees) {
        double a = Math.toRadians(northYawDegrees);
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        return new double[]{east * cos + north * sin, east * sin - north * cos};
    }

    private void update() {
        if (!dirty) {
            return;
        }
        dirty = false;
        updateHeading();
        updateOrigin();
    }

    private void updateHeading() {
        if (headingSamples.isEmpty()) {
            northYawDegrees = Double.NaN;
            northYawAccuracyDegrees = Double.NaN;
            return;
        }
        double mean = weightedCircularMean(headingSamples);
        // Second pass: ignore samples far from the first estimate, such as readings taken next to
        // a magnet or a car, then average again.
        List<HeadingSample> inliers = new ArrayList<>();
        for (HeadingSample s : headingSamples) {
            double limit = Math.max(3 * s.accuracy, 10);
            if (Math.abs(GeoMath.angleDifference(mean, s.northYaw)) <= limit) {
                inliers.add(s);
            }
        }
        if (!inliers.isEmpty()) {
            mean = weightedCircularMean(inliers);
        }
        double weightSum = 0;
        // Typed explicitly: a ternary over ArrayDeque and ArrayList infers SequencedCollection,
        // which only exists on API 35 and later.
        Iterable<HeadingSample> used = inliers.isEmpty() ? headingSamples : inliers;
        for (HeadingSample s : used) {
            weightSum += 1 / (s.accuracy * s.accuracy);
        }
        northYawDegrees = mean;
        northYawAccuracyDegrees = Math.sqrt(1 / weightSum);
    }

    private static double weightedCircularMean(Iterable<HeadingSample> samples) {
        double sin = 0;
        double cos = 0;
        for (HeadingSample s : samples) {
            double w = 1 / (s.accuracy * s.accuracy);
            double r = Math.toRadians(s.northYaw);
            sin += w * Math.sin(r);
            cos += w * Math.cos(r);
        }
        return GeoMath.normalizeDegrees(Math.toDegrees(Math.atan2(sin, cos)));
    }

    private void updateOrigin() {
        if (positionSamples.isEmpty() || !isFinite(northYawDegrees)) {
            originEast = Double.NaN;
            originNorth = Double.NaN;
            originAltitude = Double.NaN;
            originAccuracyMetres = Double.NaN;
            return;
        }
        // Each sample implies an origin position: its own position minus how far the camera had
        // moved from the origin, rotated into east/north using the current north estimate.
        int n = positionSamples.size();
        double[] east = new double[n];
        double[] north = new double[n];
        double[] weight = new double[n];
        int i = 0;
        for (PositionSample s : positionSamples) {
            double[] moved = worldToEastNorth(s.worldX, s.worldZ, northYawDegrees);
            east[i] = s.east - moved[0];
            north[i] = s.north - moved[1];
            weight[i] = 1 / (s.accuracy * s.accuracy);
            i++;
        }
        double[] mean = weightedMean(east, north, weight, null);
        // Second pass: drop samples that disagree badly with the consensus, such as a GPS jump.
        boolean[] keep = new boolean[n];
        i = 0;
        int kept = 0;
        for (PositionSample s : positionSamples) {
            double residual = Math.hypot(east[i] - mean[0], north[i] - mean[1]);
            keep[i] = residual <= Math.max(3 * s.accuracy, 5);
            if (keep[i]) kept++;
            i++;
        }
        if (kept > 0) {
            mean = weightedMean(east, north, weight, keep);
        }
        originEast = mean[0];
        originNorth = mean[1];
        originAccuracyMetres = mean[2];

        double altSum = 0;
        double altWeight = 0;
        i = 0;
        for (PositionSample s : positionSamples) {
            if ((kept == 0 || keep[i]) && isFinite(s.altitudeOrigin)) {
                double w = 1 / (s.verticalAccuracy * s.verticalAccuracy);
                altSum += w * s.altitudeOrigin;
                altWeight += w;
            }
            i++;
        }
        originAltitude = altWeight > 0 ? altSum / altWeight : Double.NaN;
    }

    /** Returns {meanEast, meanNorth, accuracy}. */
    private static double[] weightedMean(double[] east, double[] north, double[] weight, boolean[] keep) {
        double sumE = 0;
        double sumN = 0;
        double sumW = 0;
        for (int i = 0; i < east.length; i++) {
            if (keep != null && !keep[i]) continue;
            sumE += weight[i] * east[i];
            sumN += weight[i] * north[i];
            sumW += weight[i];
        }
        return new double[]{sumE / sumW, sumN / sumW, Math.sqrt(1 / sumW)};
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /** For tests: number of retained samples. */
    int headingSampleCount() {
        return headingSamples.size();
    }

    int positionSampleCount() {
        return positionSamples.size();
    }

    private static final class HeadingSample {
        final double northYaw;
        final double accuracy;
        final long timeMillis;

        HeadingSample(double northYaw, double accuracy, long timeMillis) {
            this.northYaw = northYaw;
            this.accuracy = accuracy;
            this.timeMillis = timeMillis;
        }
    }

    private static final class PositionSample {
        final double east;
        final double north;
        final double altitudeOrigin;
        final double accuracy;
        final double verticalAccuracy;
        final double worldX;
        final double worldZ;
        final long timeMillis;

        PositionSample(double east, double north, double altitudeOrigin, double accuracy,
                       double verticalAccuracy, double worldX, double worldZ, long timeMillis) {
            this.east = east;
            this.north = north;
            this.altitudeOrigin = altitudeOrigin;
            this.accuracy = accuracy;
            this.verticalAccuracy = verticalAccuracy;
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.timeMillis = timeMillis;
        }
    }
}
