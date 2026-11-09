package uk.co.appoly.arcorelocation;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.os.SystemClock;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.ar.core.Camera;
import com.google.ar.core.Earth;
import com.google.ar.core.Frame;
import com.google.ar.core.GeospatialPose;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;

import java.util.concurrent.CopyOnWriteArrayList;

import uk.co.appoly.arcorelocation.geo.GeoMath;
import uk.co.appoly.arcorelocation.geo.HeadingMath;
import uk.co.appoly.arcorelocation.geo.WorldAlignment;
import uk.co.appoly.arcorelocation.sensor.FusedLocationSource;
import uk.co.appoly.arcorelocation.sensor.HeadingSample;
import uk.co.appoly.arcorelocation.sensor.HeadingSource;
import uk.co.appoly.arcorelocation.sensor.LocationSample;
import uk.co.appoly.arcorelocation.sensor.LocationSource;
import uk.co.appoly.arcorelocation.sensor.RotationVectorHeadingSource;

/**
 * Works out where real-world coordinates are in an ARCore session. It is independent of any
 * renderer: give it each ARCore frame with {@link #update(Session, Frame)}, then ask it where to
 * put things with {@link #place}.
 *
 * <p>When the session has the Geospatial API enabled and ARCore is localised, the tracker uses
 * Geospatial poses, which are accurate to a few metres and degrees (better with VPS coverage).
 * Otherwise it combines GPS and compass readings with ARCore's motion tracking.</p>
 *
 * <p>Use from the main thread. Register it with a {@link LifecycleOwner}, or call {@link #start()}
 * and {@link #stop()} from {@code onResume} and {@code onPause}.</p>
 */
@MainThread
public final class GeoTracker implements DefaultLifecycleObserver {

    /** Overall tracking state. */
    public enum Status {
        /** {@link #start()} has not been called, or the tracker was stopped. */
        STOPPED,
        /** No usable location yet. */
        WAITING_FOR_LOCATION,
        /** Location is known but heading is not yet. */
        WAITING_FOR_HEADING,
        /** Location updates could not start, usually because permission was not granted. */
        LOCATION_UNAVAILABLE,
        /** No heading sensor and no Geospatial tracking. */
        HEADING_UNAVAILABLE,
        /** Placement works, but no new location has been accepted recently. */
        LOCATION_STALE,
        /** Placement is working. */
        TRACKING
    }

    /** Which inputs placement is currently based on. */
    public enum Source {
        NONE,
        /** Device GPS and compass, combined with ARCore motion tracking. */
        GPS_AND_COMPASS,
        /** ARCore's Geospatial API (GPS plus visual positioning where available). */
        GEOSPATIAL
    }

    /** Receives status and location changes. All methods have empty defaults. */
    public interface Listener {
        default void onStatusChanged(@NonNull GeoTracker tracker, @NonNull Status status, @NonNull Source source) {
        }

        /** A new location reading was accepted. */
        default void onLocationAccepted(@NonNull GeoTracker tracker, @NonNull LocationSample sample) {
        }
    }

    private static final long GEOSPATIAL_SAMPLE_INTERVAL_MILLIS = 250;
    private static final long GEOSPATIAL_ACTIVE_TIMEOUT_MILLIS = 3000;
    private static final long HEADING_PAIRING_WINDOW_MILLIS = 150;
    private static final long LOCATION_PAIRING_WINDOW_MILLIS = 2000;
    private static final double MAX_TURN_RATE_DEGREES_PER_SECOND = 45;
    private static final double DECLINATION_REFRESH_DISTANCE_METRES = 10_000;

    private final WorldAlignment alignment = new WorldAlignment();
    private final LocationSource locationSource;
    private final HeadingSource headingSource;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private boolean started;
    private boolean useGeospatial = true;
    private double minimumHorizontalAccuracyMetres = 30;
    private double maximumGeospatialAccuracyMetres = 15;
    private double maximumGeospatialYawAccuracyDegrees = 15;
    private long maxLocationAgeMillis = 30_000;

    @Nullable
    private LocationSample pendingLocation;
    @Nullable
    private LocationSample lastAcceptedLocation;
    private long lastAcceptedLocationMillis = Long.MIN_VALUE;
    @Nullable
    private HeadingSample pendingHeading;
    @Nullable
    private String locationUnavailableReason;
    @Nullable
    private String headingUnavailableReason;

    private double declinationDegrees = Double.NaN;
    @Nullable
    private GeoCoordinate declinationLocation;

    private long lastEarthSampleMillis = Long.MIN_VALUE;
    private boolean geospatialActive;
    @Nullable
    private Earth.EarthState lastEarthState;

    private double previousYaw = Double.NaN;
    private long previousYawMillis;

    private Status status = Status.STOPPED;
    private Source source = Source.NONE;

    private final LocationSource.Listener locationListener = new LocationSource.Listener() {
        @Override
        public void onLocation(@NonNull LocationSample sample) {
            locationUnavailableReason = null;
            pendingLocation = sample;
            updateDeclination(sample.getCoordinate());
        }

        @Override
        public void onLocationUnavailable(@NonNull String reason) {
            locationUnavailableReason = reason;
            refreshStatus(SystemClock.elapsedRealtime());
        }
    };

    private final HeadingSource.Listener headingListener = new HeadingSource.Listener() {
        @Override
        public void onHeading(@NonNull HeadingSample sample) {
            headingUnavailableReason = null;
            pendingHeading = sample;
        }

        @Override
        public void onHeadingUnavailable(@NonNull String reason) {
            headingUnavailableReason = reason;
            refreshStatus(SystemClock.elapsedRealtime());
        }
    };

    /** Uses fused location and the rotation vector sensor. */
    public GeoTracker(@NonNull Context context) {
        this(new FusedLocationSource(context), new RotationVectorHeadingSource(context));
    }

    /** Uses the given sources, for example to supply recorded data in tests. */
    public GeoTracker(@NonNull LocationSource locationSource, @NonNull HeadingSource headingSource) {
        this.locationSource = locationSource;
        this.headingSource = headingSource;
    }

    /** Starts location and heading updates. Safe to call repeatedly. */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        locationSource.start(locationListener);
        headingSource.start(headingListener);
        refreshStatus(SystemClock.elapsedRealtime());
    }

    /** Stops location and heading updates. Placement estimates are kept for when it restarts. */
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        locationSource.stop();
        headingSource.stop();
        pendingHeading = null;
        pendingLocation = null;
        refreshStatus(SystemClock.elapsedRealtime());
    }

    /**
     * Discards all placement estimates. Call this if the ARCore session is replaced, because the
     * new session has a different world frame.
     */
    public void reset() {
        alignment.reset();
        pendingHeading = null;
        pendingLocation = null;
        lastAcceptedLocation = null;
        lastAcceptedLocationMillis = Long.MIN_VALUE;
        lastEarthSampleMillis = Long.MIN_VALUE;
        geospatialActive = false;
        previousYaw = Double.NaN;
        refreshStatus(SystemClock.elapsedRealtime());
    }

    /**
     * Feeds one ARCore frame into the tracker. Call this once per frame, after
     * {@code Session.update()}, before placing content.
     */
    public void update(@NonNull Session session, @NonNull Frame frame) {
        long now = SystemClock.elapsedRealtime();
        Camera camera = frame.getCamera();
        if (camera.getTrackingState() != TrackingState.TRACKING) {
            previousYaw = Double.NaN;
            refreshStatus(now);
            return;
        }
        Pose pose = camera.getPose();
        double yaw = HeadingMath.cameraYawDegrees(pose.qx(), pose.qy(), pose.qz(), pose.qw());
        boolean turningFast = isTurningFast(yaw, now);

        if (useGeospatial) {
            sampleGeospatial(session, pose, yaw, now);
        }
        geospatialActive = lastEarthSampleMillis != Long.MIN_VALUE
                && now - lastEarthSampleMillis < GEOSPATIAL_ACTIVE_TIMEOUT_MILLIS;

        if (!geospatialActive) {
            consumeHeading(yaw, turningFast, now);
            consumeLocation(pose, now);
        }
        alignment.expireSamples(now);
        refreshStatus(now);
    }

    /**
     * Where to draw {@code target}, as seen from a camera at {@code cameraPose}.
     *
     * @param maxRenderDistanceMetres targets further away are drawn at this distance, in the right
     *                                direction. Keep it inside your renderer's far clipping plane.
     * @param heightOffsetMetres      added to the target's height; see {@link GeoPlacement#compute}
     * @return the placement, or {@code null} if the tracker is not ready yet
     */
    @Nullable
    public GeoPlacement place(@NonNull GeoCoordinate target, @NonNull Pose cameraPose,
                              float maxRenderDistanceMetres, float heightOffsetMetres) {
        return GeoPlacement.compute(alignment, target, cameraPose.tx(), cameraPose.ty(), cameraPose.tz(),
                maxRenderDistanceMetres, heightOffsetMetres);
    }

    /** Estimated location of a camera at {@code cameraPose}, or {@code null} if not ready. */
    @Nullable
    public GeoCoordinate getCameraLocation(@NonNull Pose cameraPose) {
        return alignment.worldToGeo(cameraPose.tx(), cameraPose.ty(), cameraPose.tz());
    }

    /** True once north and position have been estimated and {@link #place} returns results. */
    public boolean isReady() {
        return alignment.isReady();
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    @NonNull
    public Source getSource() {
        return source;
    }

    /** Why location is unavailable, when the status is {@link Status#LOCATION_UNAVAILABLE}. */
    @Nullable
    public String getLocationUnavailableReason() {
        return locationUnavailableReason;
    }

    /** Why heading is unavailable, when the status is {@link Status#HEADING_UNAVAILABLE}. */
    @Nullable
    public String getHeadingUnavailableReason() {
        return headingUnavailableReason;
    }

    /** The most recent Earth state reported by ARCore, or {@code null} if Geospatial isn't in use. */
    @Nullable
    public Earth.EarthState getEarthState() {
        return lastEarthState;
    }

    /** Approximate 1-sigma accuracy of the current position estimate in metres, or {@code NaN}. */
    public double getHorizontalAccuracyMetres() {
        return alignment.getHorizontalAccuracyMetres();
    }

    /** Approximate 1-sigma accuracy of the current north estimate in degrees, or {@code NaN}. */
    public double getHeadingAccuracyDegrees() {
        return alignment.getNorthYawAccuracyDegrees();
    }

    /** The last location reading accepted from the location source, or {@code null}. */
    @Nullable
    public LocationSample getLastAcceptedLocation() {
        return lastAcceptedLocation;
    }

    /** Whether to use ARCore's Geospatial API when the session has it enabled. Default true. */
    public void setUseGeospatial(boolean useGeospatial) {
        this.useGeospatial = useGeospatial;
    }

    /** Readings less accurate than this are ignored. Default 30 metres. */
    public void setMinimumHorizontalAccuracyMetres(double metres) {
        this.minimumHorizontalAccuracyMetres = metres;
    }

    /** Geospatial poses less accurate than this are ignored. Default 15 metres. */
    public void setMaximumGeospatialAccuracyMetres(double metres) {
        this.maximumGeospatialAccuracyMetres = metres;
    }

    /** Status becomes {@link Status#LOCATION_STALE} after this long without a reading. Default 30s. */
    public void setMaxLocationAgeMillis(long millis) {
        this.maxLocationAgeMillis = millis;
    }

    public void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        start();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        stop();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        stop();
        listeners.clear();
        owner.getLifecycle().removeObserver(this);
    }

    private void sampleGeospatial(Session session, Pose pose, double yaw, long now) {
        if (lastEarthSampleMillis != Long.MIN_VALUE
                && now - lastEarthSampleMillis < GEOSPATIAL_SAMPLE_INTERVAL_MILLIS) {
            return;
        }
        Earth earth;
        try {
            earth = session.getEarth();
        } catch (RuntimeException e) {
            earth = null;
        }
        if (earth == null) {
            lastEarthState = null;
            return;
        }
        lastEarthState = earth.getEarthState();
        if (earth.getTrackingState() != TrackingState.TRACKING
                || lastEarthState != Earth.EarthState.ENABLED) {
            return;
        }
        GeospatialPose geo = earth.getCameraGeospatialPose();
        double horizontalAccuracy = geo.getHorizontalAccuracy();
        if (!(horizontalAccuracy <= maximumGeospatialAccuracyMetres)) {
            return;
        }
        if (!geospatialActive) {
            // Geospatial poses are much better than GPS and compass. Start from a clean slate so
            // older, less accurate samples don't drag the estimate.
            alignment.reset();
        }
        GeoCoordinate coordinate = GeoCoordinate.of(geo.getLatitude(), geo.getLongitude(), geo.getAltitude());
        alignment.addPositionSample(coordinate, horizontalAccuracy, geo.getVerticalAccuracy(),
                pose.tx(), pose.ty(), pose.tz(), now);
        float[] q = geo.getEastUpSouthQuaternion();
        double heading = HeadingMath.headingFromEastUpSouth(q[0], q[1], q[2], q[3]);
        double yawAccuracy = geo.getOrientationYawAccuracy();
        if (!Double.isNaN(heading) && !Double.isNaN(yaw)
                && yawAccuracy <= maximumGeospatialYawAccuracyDegrees) {
            alignment.addHeadingSample(yaw, heading, yawAccuracy, now);
        }
        lastEarthSampleMillis = now;
    }

    private void consumeHeading(double yaw, boolean turningFast, long now) {
        HeadingSample sample = pendingHeading;
        if (sample == null || Double.isNaN(yaw) || turningFast) {
            return;
        }
        if (now - sample.getElapsedRealtimeMillis() > HEADING_PAIRING_WINDOW_MILLIS) {
            pendingHeading = null;
            return;
        }
        double heading = sample.getHeadingDegrees();
        if (sample.isMagnetic()) {
            if (Double.isNaN(declinationDegrees)) {
                // Wait for a location so magnetic north can be corrected to true north.
                return;
            }
            heading += declinationDegrees;
        }
        alignment.addHeadingSample(yaw, heading, sample.getAccuracyDegrees(), now);
        pendingHeading = null;
    }

    private void consumeLocation(Pose pose, long now) {
        LocationSample sample = pendingLocation;
        if (sample == null) {
            return;
        }
        pendingLocation = null;
        double accuracy = sample.getHorizontalAccuracyMetres();
        if (!(accuracy > 0) || accuracy > minimumHorizontalAccuracyMetres) {
            return;
        }
        if (now - sample.getElapsedRealtimeMillis() > LOCATION_PAIRING_WINDOW_MILLIS) {
            return;
        }
        alignment.addPositionSample(sample.getCoordinate(), accuracy, sample.getVerticalAccuracyMetres(),
                pose.tx(), pose.ty(), pose.tz(), now);
        lastAcceptedLocation = sample;
        lastAcceptedLocationMillis = now;
        for (Listener listener : listeners) {
            listener.onLocationAccepted(this, sample);
        }
    }

    private boolean isTurningFast(double yaw, long now) {
        boolean fast = false;
        if (!Double.isNaN(yaw) && !Double.isNaN(previousYaw) && now > previousYawMillis) {
            double rate = Math.abs(GeoMath.angleDifference(previousYaw, yaw)) * 1000.0 / (now - previousYawMillis);
            fast = rate > MAX_TURN_RATE_DEGREES_PER_SECOND;
        }
        previousYaw = yaw;
        previousYawMillis = now;
        return fast;
    }

    private void updateDeclination(GeoCoordinate location) {
        if (declinationLocation != null && !Double.isNaN(declinationDegrees)
                && GeoMath.distanceMetres(declinationLocation, location) < DECLINATION_REFRESH_DISTANCE_METRES) {
            return;
        }
        GeomagneticField field = new GeomagneticField(
                (float) location.getLatitude(),
                (float) location.getLongitude(),
                location.hasAltitude() ? (float) location.getAltitude() : 0f,
                System.currentTimeMillis());
        declinationDegrees = field.getDeclination();
        declinationLocation = location;
    }

    private void refreshStatus(long now) {
        Status newStatus;
        Source newSource = Source.NONE;
        if (!started) {
            newStatus = Status.STOPPED;
        } else if (!alignment.hasPosition()) {
            newStatus = locationUnavailableReason != null && !geospatialActive
                    ? Status.LOCATION_UNAVAILABLE : Status.WAITING_FOR_LOCATION;
        } else if (!alignment.hasHeading()) {
            newStatus = headingUnavailableReason != null && !geospatialActive
                    ? Status.HEADING_UNAVAILABLE : Status.WAITING_FOR_HEADING;
        } else if (!geospatialActive && lastAcceptedLocationMillis != Long.MIN_VALUE
                && now - lastAcceptedLocationMillis > maxLocationAgeMillis) {
            newStatus = Status.LOCATION_STALE;
            newSource = Source.GPS_AND_COMPASS;
        } else {
            newStatus = Status.TRACKING;
            newSource = geospatialActive ? Source.GEOSPATIAL : Source.GPS_AND_COMPASS;
        }
        if (newStatus != status || newSource != source) {
            status = newStatus;
            source = newSource;
            for (Listener listener : listeners) {
                listener.onStatusChanged(this, status, source);
            }
        }
    }
}
