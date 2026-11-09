package uk.co.appoly.arcorelocation.example;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import java.util.Locale;

import uk.co.appoly.arcorelocation.ArLocationPermissions;
import uk.co.appoly.arcorelocation.GeoCoordinate;
import uk.co.appoly.arcorelocation.GeoTracker;
import uk.co.appoly.arcorelocation.geo.GeoMath;
import uk.co.appoly.arcorelocation.sceneform.LocationMarker;
import uk.co.appoly.arcorelocation.sceneform.LocationScene;
import uk.co.appoly.arcorelocation.sceneform.OverlapMode;
import uk.co.appoly.arcorelocation.sceneform.ScalingMode;

/**
 * Shows labels at a few well-known landmarks, plus four markers 20 metres north, east, south and
 * west of wherever you start, so there is something to see nearby.
 */
public class LocationActivity extends AppCompatActivity {

    private static final String AR_FRAGMENT_TAG = "ar";

    private TextView statusView;
    private LocationScene locationScene;
    private boolean nearbyMarkersAdded;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (ArLocationPermissions.hasRequiredPermissions(this)) {
                    showArFragment();
                } else if (ArLocationPermissions.hasApproximateLocationOnly(this)) {
                    Toast.makeText(this, R.string.approximate_location, Toast.LENGTH_LONG).show();
                    showArFragment();
                } else {
                    statusView.setText(R.string.permissions_needed);
                    if (!ArLocationPermissions.shouldShowRationale(this)) {
                        // The user chose "Don't ask again"; send them to settings.
                        ArLocationPermissions.openAppSettings(this);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        statusView = findViewById(R.id.status);
        // Apps targeting Android 15+ draw edge to edge; keep the status label clear of system bars.
        int margin = Math.round(16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(statusView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            android.view.ViewGroup.MarginLayoutParams params =
                    (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.setMargins(bars.left + margin, bars.top + margin, bars.right + margin, params.bottomMargin);
            v.setLayoutParams(params);
            return insets;
        });

        if (ArLocationPermissions.hasRequiredPermissions(this)) {
            showArFragment();
        } else {
            permissionLauncher.launch(ArLocationPermissions.REQUIRED_PERMISSIONS);
        }
    }

    private void showArFragment() {
        ArFragment fragment = (ArFragment) getSupportFragmentManager().findFragmentByTag(AR_FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new ArFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.ar_container, fragment, AR_FRAGMENT_TAG)
                    .commitNow();
        }
        fragment.setOnSessionConfigurationListener(this::configureSession);
        fragment.setOnViewCreatedListener(this::onArSceneViewCreated);
        fragment.setOnArUnavailableListener(e ->
                statusView.setText(getString(R.string.ar_unavailable, e.getMessage())));
        if (fragment.getArSceneView() != null) {
            onArSceneViewCreated(fragment.getArSceneView());
        }
    }

    private void configureSession(Session session, Config config) {
        // Use the Geospatial API where the device supports it. It needs an API key (see README);
        // without one, ARCore reports an authorisation error and the library falls back to GPS
        // and compass.
        if (ArLocationPermissions.hasRequiredPermissions(this)
                && session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
            config.setGeospatialMode(Config.GeospatialMode.ENABLED);
        }
        config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
    }

    private void onArSceneViewCreated(ArSceneView sceneView) {
        if (locationScene != null) {
            return;
        }
        ArFragment fragment = (ArFragment) getSupportFragmentManager().findFragmentByTag(AR_FRAGMENT_TAG);
        if (fragment != null) {
            fragment.getInstructionsController().setEnabled(false);
        }
        sceneView.getPlaneRenderer().setVisible(false);

        locationScene = new LocationScene(sceneView);
        locationScene.setOverlapMode(OverlapMode.STACK);
        getLifecycle().addObserver(locationScene);

        GeoTracker tracker = locationScene.getTracker();
        tracker.addListener(new GeoTracker.Listener() {
            @Override
            public void onStatusChanged(@NonNull GeoTracker t, @NonNull GeoTracker.Status status,
                                        @NonNull GeoTracker.Source source) {
                updateStatus(t);
            }
        });
        sceneView.getScene().addOnUpdateListener(frameTime -> {
            updateStatus(tracker);
            addNearbyMarkersOnce(sceneView, tracker);
        });

        addMarker("Big Ben", GeoCoordinate.of(51.50073, -0.12463));
        addMarker("Eiffel Tower", GeoCoordinate.of(48.85837, 2.29448));
        addMarker("Statue of Liberty", GeoCoordinate.of(40.68925, -74.04450));
        addMarker("Sydney Opera House", GeoCoordinate.of(-33.85678, 151.21530));
    }

    private void addNearbyMarkersOnce(ArSceneView sceneView, GeoTracker tracker) {
        Frame frame = sceneView.getArFrame();
        if (nearbyMarkersAdded || frame == null || !tracker.isReady()) {
            return;
        }
        GeoCoordinate here = tracker.getCameraLocation(frame.getCamera().getPose());
        if (here == null) {
            return;
        }
        nearbyMarkersAdded = true;
        String[] names = {"North", "East", "South", "West"};
        Color[] colors = {new Color(0.9f, 0.2f, 0.2f), new Color(0.2f, 0.7f, 0.3f),
                new Color(0.2f, 0.4f, 0.9f), new Color(0.9f, 0.7f, 0.1f)};
        for (int i = 0; i < names.length; i++) {
            GeoCoordinate target = GeoMath.destination(here, i * 90, 20);
            addMarker(getString(R.string.nearby_marker, names[i]), target);
            addSphere(target, colors[i]);
        }
    }

    /** A floating label showing the place name and live distance. */
    private void addMarker(String name, GeoCoordinate coordinate) {
        ViewRenderable.builder()
                .setView(this, R.layout.marker_label)
                .build()
                .thenAccept(renderable -> {
                    if (locationScene == null) {
                        return;
                    }
                    View view = renderable.getView();
                    ((TextView) view.findViewById(R.id.marker_name)).setText(name);
                    TextView distanceView = view.findViewById(R.id.marker_distance);

                    Node node = new Node();
                    node.setRenderable(renderable);
                    // Scale the label so it is a readable size whatever its distance.
                    node.setLocalScale(new Vector3(0.12f, 0.12f, 0.12f));

                    LocationMarker marker = new LocationMarker(coordinate, node)
                            .setHeight(1.5f)
                            .setScalingMode(ScalingMode.GRADUAL_FIXED_SIZE)
                            .setOnRenderListener(m -> {
                                String text = formatDistance(m.getDistanceMetres());
                                if (!text.contentEquals(distanceView.getText())) {
                                    distanceView.setText(text);
                                }
                            });
                    locationScene.addMarker(marker);
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Could not create marker: " + throwable.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return null;
                });
    }

    /** A coloured sphere on the ground-ish plane, 1.5 m below the camera. */
    private void addSphere(GeoCoordinate coordinate, Color color) {
        MaterialFactory.makeOpaqueWithColor(this, color).thenAccept(material -> {
            if (locationScene == null) {
                return;
            }
            Node node = new Node();
            node.setRenderable(ShapeFactory.makeSphere(0.3f, Vector3.zero(), material));
            locationScene.addMarker(new LocationMarker(coordinate, node)
                    .setHeight(-1.2f)
                    .setScalingMode(ScalingMode.NO_SCALING));
        });
    }

    private void updateStatus(GeoTracker tracker) {
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
