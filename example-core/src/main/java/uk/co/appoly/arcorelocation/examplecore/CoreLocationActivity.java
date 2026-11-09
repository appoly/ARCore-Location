package uk.co.appoly.arcorelocation.examplecore;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import uk.co.appoly.arcorelocation.ArLocationPermissions;
import uk.co.appoly.arcorelocation.GeoCoordinate;
import uk.co.appoly.arcorelocation.GeoPlacement;
import uk.co.appoly.arcorelocation.GeoTracker;
import uk.co.appoly.arcorelocation.geo.GeoMath;

/**
 * Uses the core module with ARCore directly, without Sceneform or any 3D engine.
 *
 * <p>A {@link GLSurfaceView} draws the camera image. Every frame, on the GL thread, the activity
 * feeds the frame to {@link GeoTracker}, keeps one ARCore anchor per target (replacing it only
 * when the target's placement moves), and projects each anchor onto the screen. Labels are
 * ordinary Android views moved to those screen positions on the main thread.</p>
 */
public class CoreLocationActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private static final float MAX_RENDER_DISTANCE_METRES = 25f;
    private static final float REPOSITION_TOLERANCE_METRES = 0.3f;

    /** A place to label. Fields written on the GL thread are read on the main thread. */
    private static final class Target {
        final String name;
        final GeoCoordinate coordinate;
        final float heightMetres;

        // GL thread only.
        Anchor anchor;

        // Written on the GL thread, read on the main thread.
        volatile boolean onScreen;
        volatile float screenX;
        volatile float screenY;
        volatile double distanceMetres = Double.NaN;

        // Main thread only.
        View label;
        TextView distanceView;

        Target(String name, GeoCoordinate coordinate, float heightMetres) {
            this.name = name;
            this.coordinate = coordinate;
            this.heightMetres = heightMetres;
        }
    }

    private final List<Target> targets = new CopyOnWriteArrayList<>();
    private final BackgroundRenderer background = new BackgroundRenderer();
    private final AtomicBoolean labelUpdatePending = new AtomicBoolean();
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewProjection = new float[16];
    private final float[] point = new float[4];
    private final float[] clip = new float[4];

    private GLSurfaceView surfaceView;
    private FrameLayout labelsLayout;
    private TextView statusView;
    private DisplayRotationHelper displayRotationHelper;
    private GeoTracker tracker;

    private volatile Session session;
    private boolean installRequested;
    private boolean permissionsGranted;
    private volatile int viewWidth;
    private volatile int viewHeight;
    private volatile boolean nearbyTargetsAdded;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (ArLocationPermissions.hasRequiredPermissions(this)) {
                    onPermissionsGranted();
                } else if (ArLocationPermissions.hasApproximateLocationOnly(this)) {
                    Toast.makeText(this, R.string.approximate_location, Toast.LENGTH_LONG).show();
                    onPermissionsGranted();
                } else {
                    statusView.setText(R.string.permissions_needed);
                    if (!ArLocationPermissions.shouldShowRationale(this)) {
                        ArLocationPermissions.openAppSettings(this);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_core);
        surfaceView = findViewById(R.id.surface);
        labelsLayout = findViewById(R.id.labels);
        statusView = findViewById(R.id.status);
        displayRotationHelper = new DisplayRotationHelper(this);
        keepStatusClearOfSystemBars();

        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        tracker = new GeoTracker(this);

        targets.add(new Target("Big Ben", GeoCoordinate.of(51.50073, -0.12463), 0f));
        targets.add(new Target("Eiffel Tower", GeoCoordinate.of(48.85837, 2.29448), 0f));
        targets.add(new Target("Statue of Liberty", GeoCoordinate.of(40.68925, -74.04450), 0f));
        targets.add(new Target("Sydney Opera House", GeoCoordinate.of(-33.85678, 151.21530), 0f));

        if (ArLocationPermissions.hasRequiredPermissions(this)) {
            onPermissionsGranted();
        } else {
            permissionLauncher.launch(ArLocationPermissions.REQUIRED_PERMISSIONS);
        }
    }

    private void onPermissionsGranted() {
        if (permissionsGranted) {
            return;
        }
        permissionsGranted = true;
        // Start the tracker only once permission is granted, so location updates can begin.
        getLifecycle().addObserver(tracker);
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            resumeAr();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionsGranted) {
            resumeAr();
        }
    }

    private void resumeAr() {
        if (session == null) {
            session = createSession();
            if (session == null) {
                return;
            }
        }
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            statusView.setText(getString(R.string.ar_unavailable, "camera not available"));
            session = null;
            return;
        }
        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) {
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }

    /** Installs ARCore if needed and creates a configured session, or returns null. */
    private Session createSession() {
        try {
            if (ArCoreApk.getInstance().requestInstall(this, !installRequested)
                    == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true;
                return null;
            }
            Session created = new Session(this);
            Config config = created.getConfig();
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            config.setFocusMode(Config.FocusMode.AUTO);
            config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
            // Use the Geospatial API where the device supports it. It needs an API key (see
            // README); without one, ARCore reports an authorisation error and the tracker uses
            // GPS and compass instead.
            if (ArLocationPermissions.hasRequiredPermissions(this)
                    && created.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
                config.setGeospatialMode(Config.GeospatialMode.ENABLED);
            }
            try {
                created.configure(config);
            } catch (RuntimeException e) {
                config.setGeospatialMode(Config.GeospatialMode.DISABLED);
                created.configure(config);
            }
            return created;
        } catch (UnavailableUserDeclinedInstallationException e) {
            statusView.setText(R.string.arcore_declined);
            return null;
        } catch (UnavailableDeviceNotCompatibleException e) {
            statusView.setText(R.string.arcore_not_supported);
            return null;
        } catch (UnavailableException e) {
            statusView.setText(getString(R.string.ar_unavailable, e.getMessage()));
            return null;
        }
    }

    // GL thread.

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        background.createOnGlThread();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
        viewWidth = width;
        viewHeight = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Session current = session;
        if (current == null) {
            return;
        }
        displayRotationHelper.updateSessionIfNeeded(current);
        current.setCameraTextureName(background.getTextureId());
        Frame frame;
        try {
            frame = current.update();
        } catch (CameraNotAvailableException e) {
            return;
        }
        background.draw(frame);

        // GeoTracker is thread safe, so it can be updated here on the GL thread.
        tracker.update(current, frame);
        Camera camera = frame.getCamera();
        boolean tracking = camera.getTrackingState() == TrackingState.TRACKING;
        if (tracking && tracker.isReady()) {
            Pose cameraPose = camera.getPose();
            addNearbyTargetsOnce(cameraPose);
            for (Target target : targets) {
                updateAnchor(current, target, cameraPose);
            }
            projectTargets(camera);
        } else {
            for (Target target : targets) {
                target.onScreen = false;
            }
        }
        requestLabelUpdate();
    }

    /** Adds four targets 20 m north, east, south and west of the first known position. */
    private void addNearbyTargetsOnce(Pose cameraPose) {
        if (nearbyTargetsAdded) {
            return;
        }
        GeoCoordinate here = tracker.getCameraLocation(cameraPose);
        if (here == null) {
            return;
        }
        nearbyTargetsAdded = true;
        String[] names = {"North", "East", "South", "West"};
        for (int i = 0; i < names.length; i++) {
            targets.add(new Target(names[i], GeoMath.destination(here, i * 90, 20), 0f));
        }
    }

    /** Keeps one anchor per target, replacing it only when the target's placement has moved. */
    private void updateAnchor(Session session, Target target, Pose cameraPose) {
        GeoPlacement placement = tracker.place(target.coordinate, cameraPose,
                MAX_RENDER_DISTANCE_METRES, target.heightMetres);
        if (placement == null) {
            return;
        }
        target.distanceMetres = placement.getDistanceMetres();
        Anchor current = target.anchor;
        if (current != null && current.getTrackingState() != TrackingState.STOPPED) {
            Pose pose = current.getPose();
            float dx = pose.tx() - placement.getWorldX();
            float dy = pose.ty() - placement.getWorldY();
            float dz = pose.tz() - placement.getWorldZ();
            float tolerance = REPOSITION_TOLERANCE_METRES + 0.01f * placement.getRenderDistanceMetres();
            if (dx * dx + dy * dy + dz * dz <= tolerance * tolerance) {
                return;
            }
        }
        try {
            target.anchor = session.createAnchor(Pose.makeTranslation(
                    placement.getWorldX(), placement.getWorldY(), placement.getWorldZ()));
        } catch (RuntimeException e) {
            // Usually NotTrackingException or ResourceExhaustedException; try again next frame.
            return;
        }
        if (current != null) {
            current.detach();
        }
    }

    /** Works out where each anchor appears on screen. */
    private void projectTargets(Camera camera) {
        camera.getViewMatrix(viewMatrix, 0);
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f);
        Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0);
        int width = viewWidth;
        int height = viewHeight;
        for (Target target : targets) {
            Anchor anchor = target.anchor;
            if (anchor == null || anchor.getTrackingState() != TrackingState.TRACKING) {
                target.onScreen = false;
                continue;
            }
            Pose pose = anchor.getPose();
            point[0] = pose.tx();
            point[1] = pose.ty();
            point[2] = pose.tz();
            point[3] = 1f;
            Matrix.multiplyMV(clip, 0, viewProjection, 0, point, 0);
            float w = clip[3];
            if (w <= 0) {
                // Behind the camera.
                target.onScreen = false;
                continue;
            }
            float ndcX = clip[0] / w;
            float ndcY = clip[1] / w;
            target.screenX = (ndcX + 1f) / 2f * width;
            target.screenY = (1f - ndcY) / 2f * height;
            target.onScreen = Math.abs(ndcX) <= 1.2f && Math.abs(ndcY) <= 1.2f;
        }
    }

    // Main thread.

    /** Posts one label update at a time to the main thread. */
    private void requestLabelUpdate() {
        if (labelUpdatePending.compareAndSet(false, true)) {
            runOnUiThread(() -> {
                labelUpdatePending.set(false);
                updateLabels();
                updateStatus();
            });
        }
    }

    private void updateLabels() {
        LayoutInflater inflater = getLayoutInflater();
        for (Target target : targets) {
            if (target.label == null) {
                target.label = inflater.inflate(R.layout.marker_label, labelsLayout, false);
                ((TextView) target.label.findViewById(R.id.marker_name)).setText(target.name);
                target.distanceView = target.label.findViewById(R.id.marker_distance);
                labelsLayout.addView(target.label, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            View label = target.label;
            if (!target.onScreen) {
                label.setVisibility(View.INVISIBLE);
                continue;
            }
            label.setVisibility(View.VISIBLE);
            label.setTranslationX(target.screenX - label.getWidth() / 2f);
            label.setTranslationY(target.screenY - label.getHeight() / 2f);
            // Nearer labels draw on top of further ones.
            label.setTranslationZ((float) (-target.distanceMetres / 1000.0));
            String distance = formatDistance(target.distanceMetres);
            if (!distance.contentEquals(target.distanceView.getText())) {
                target.distanceView.setText(distance);
            }
        }
    }

    private void updateStatus() {
        String text;
        switch (tracker.getStatus()) {
            case TRACKING:
                text = String.format(Locale.US, "%s · ±%.0f m · ±%.0f°",
                        tracker.getSource() == GeoTracker.Source.GEOSPATIAL ? "Geospatial" : "GPS + compass",
                        tracker.getHorizontalAccuracyMetres(), tracker.getHeadingAccuracyDegrees());
                break;
            case WAITING_FOR_LOCATION:
                text = "Waiting for location…";
                break;
            case WAITING_FOR_HEADING:
                text = "Waiting for compass. Point the camera at the horizon.";
                break;
            case LOCATION_STALE:
                text = "Location is out of date";
                break;
            case LOCATION_UNAVAILABLE:
                text = "Location unavailable: " + tracker.getLocationUnavailableReason();
                break;
            case HEADING_UNAVAILABLE:
                text = "Compass unavailable: " + tracker.getHeadingUnavailableReason();
                break;
            case STOPPED:
            default:
                text = "Starting…";
                break;
        }
        if (!text.contentEquals(statusView.getText())) {
            statusView.setText(text);
        }
    }

    private void keepStatusClearOfSystemBars() {
        int margin = Math.round(16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(statusView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.setMargins(bars.left + margin, bars.top + margin, bars.right + margin, params.bottomMargin);
            v.setLayoutParams(params);
            return insets;
        });
    }

    private static String formatDistance(double metres) {
        if (Double.isNaN(metres)) {
            return "";
        }
        if (metres < 1000) {
            return String.format(Locale.getDefault(), "%.0f m", metres);
        }
        return String.format(Locale.getDefault(), "%.1f km", metres / 1000);
    }
}
