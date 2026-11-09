package uk.co.appoly.arcorelocation.sceneform;

import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.co.appoly.arcorelocation.GeoPlacement;
import uk.co.appoly.arcorelocation.GeoTracker;

/**
 * Shows {@link LocationMarker}s in a Sceneform {@link ArSceneView}.
 *
 * <pre>{@code
 * LocationScene locationScene = new LocationScene(arSceneView);
 * getLifecycle().addObserver(locationScene);
 * locationScene.addMarker(new LocationMarker(GeoCoordinate.of(51.5033, -0.1196), node));
 * }</pre>
 *
 * <p>The scene updates itself every frame; there is no need to forward frames to it. If you don't
 * register it with a lifecycle, call {@link #resume()}, {@link #pause()} and {@link #destroy()}
 * yourself.</p>
 */
@MainThread
public final class LocationScene implements DefaultLifecycleObserver {

    private static final String TAG = "LocationScene";

    private final ArSceneView sceneView;
    private final GeoTracker tracker;
    private final List<LocationMarker> markers = new ArrayList<>();
    private final Scene.OnUpdateListener updateListener = this::onSceneUpdate;

    private float maxRenderDistanceMetres = 25f;
    private OverlapMode overlapMode = OverlapMode.NONE;
    private double overlapAngleDegrees = 6;
    private float stackSpacingMetres = 1.2f;
    private int maxVisibleMarkers = Integer.MAX_VALUE;
    private float repositionToleranceMetres = 0.3f;
    private boolean destroyed;

    /** Creates a scene with its own {@link GeoTracker} using the default location and heading sources. */
    public LocationScene(@NonNull ArSceneView sceneView) {
        this(sceneView, new GeoTracker(sceneView.getContext()));
    }

    /**
     * Creates a scene using {@code tracker}. The scene calls {@link GeoTracker#update} every frame,
     * so don't also call it yourself.
     */
    public LocationScene(@NonNull ArSceneView sceneView, @NonNull GeoTracker tracker) {
        this.sceneView = sceneView;
        this.tracker = tracker;
        sceneView.getScene().addOnUpdateListener(updateListener);
    }

    @NonNull
    public GeoTracker getTracker() {
        return tracker;
    }

    public void addMarker(@NonNull LocationMarker marker) {
        if (!markers.contains(marker)) {
            markers.add(marker);
        }
    }

    public void removeMarker(@NonNull LocationMarker marker) {
        if (markers.remove(marker)) {
            marker.detach();
        }
    }

    public void clearMarkers() {
        for (LocationMarker marker : markers) {
            marker.detach();
        }
        markers.clear();
    }

    /** The markers in this scene, in the order they were added. */
    @NonNull
    public List<LocationMarker> getMarkers() {
        return Collections.unmodifiableList(markers);
    }

    /** Starts location and heading updates. */
    public void resume() {
        if (!destroyed) {
            tracker.start();
        }
    }

    /** Stops location and heading updates. Markers stay where they are. */
    public void pause() {
        tracker.stop();
    }

    /** Stops updates, removes all markers from the scene and detaches their anchors. */
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        pause();
        sceneView.getScene().removeOnUpdateListener(updateListener);
        clearMarkers();
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        resume();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        pause();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        destroy();
        owner.getLifecycle().removeObserver(this);
    }

    /**
     * Markers further away than this are drawn at this distance in the right direction. Sceneform's
     * camera clips at 30 metres, so keep it below that. Default 25 metres.
     */
    public void setMaxRenderDistanceMetres(float metres) {
        this.maxRenderDistanceMetres = Math.max(1f, metres);
    }

    public float getMaxRenderDistanceMetres() {
        return maxRenderDistanceMetres;
    }

    /** What to do with markers that line up behind each other. Default {@link OverlapMode#NONE}. */
    public void setOverlapMode(@NonNull OverlapMode mode) {
        this.overlapMode = mode;
    }

    @NonNull
    public OverlapMode getOverlapMode() {
        return overlapMode;
    }

    /** Markers closer together than this angle, seen from the camera, overlap. Default 6 degrees. */
    public void setOverlapAngleDegrees(double degrees) {
        this.overlapAngleDegrees = degrees;
    }

    /** Vertical gap between stacked markers in {@link OverlapMode#STACK}. Default 1.2 metres. */
    public void setStackSpacingMetres(float metres) {
        this.stackSpacingMetres = metres;
    }

    /** Show at most this many markers, nearest first. Default unlimited. */
    public void setMaxVisibleMarkers(int count) {
        this.maxVisibleMarkers = Math.max(0, count);
    }

    /**
     * A marker is re-anchored when its ideal position moves more than this from its anchor.
     * Default 0.3 metres.
     */
    public void setRepositionToleranceMetres(float metres) {
        this.repositionToleranceMetres = Math.max(0.01f, metres);
    }

    private void onSceneUpdate(FrameTime frameTime) {
        Session session = sceneView.getSession();
        Frame frame = sceneView.getArFrame();
        if (session == null || frame == null) {
            return;
        }
        tracker.update(session, frame);
        if (frame.getCamera().getTrackingState() != TrackingState.TRACKING || !tracker.isReady()) {
            return;
        }
        Pose cameraPose = frame.getCamera().getPose();

        List<LocationMarker> placed = new ArrayList<>(markers.size());
        for (LocationMarker marker : markers) {
            GeoPlacement placement = tracker.place(marker.getCoordinate(), cameraPose,
                    maxRenderDistanceMetres, marker.getHeight());
            marker.placement = placement;
            if (placement == null) {
                continue;
            }
            // Hysteresis stops markers flickering at the edge of their range.
            double range = marker.getOnlyRenderWhenWithinMetres();
            if (marker.withinRange && placement.getDistanceMetres() > range * 1.05) {
                marker.withinRange = false;
            } else if (!marker.withinRange && placement.getDistanceMetres() <= range) {
                marker.withinRange = true;
            }
            if (!marker.withinRange) {
                marker.detach();
                continue;
            }
            if (ensureAnchor(session, marker, placement)) {
                placed.add(marker);
            }
        }

        double[] bearings = new double[placed.size()];
        double[] distances = new double[placed.size()];
        for (int i = 0; i < placed.size(); i++) {
            GeoPlacement p = placed.get(i).placement;
            bearings[i] = p.getBearingDegrees();
            distances[i] = p.getDistanceMetres();
        }
        MarkerLayout.Result layout = MarkerLayout.compute(bearings, distances, overlapMode,
                overlapAngleDegrees, maxVisibleMarkers);

        Vector3 cameraPosition = sceneView.getScene().getCamera().getWorldPosition();
        for (int i = 0; i < placed.size(); i++) {
            LocationMarker marker = placed.get(i);
            Node layoutNode = marker.getLayoutNode();
            marker.visible = layout.visible[i];
            layoutNode.setEnabled(marker.visible);
            if (!marker.visible) {
                continue;
            }
            layoutNode.setLocalPosition(new Vector3(0, layout.stackIndex[i] * stackSpacingMetres, 0));
            Vector3 toCamera = Vector3.subtract(cameraPosition, layoutNode.getWorldPosition());
            if (toCamera.length() > 0.001f) {
                layoutNode.setWorldRotation(Quaternion.lookRotation(toCamera, Vector3.up()));
            }
            float scale = scaleFor(marker, toCamera.length());
            layoutNode.setLocalScale(new Vector3(scale, scale, scale));
            LocationMarker.OnRenderListener listener = marker.getOnRenderListener();
            if (listener != null) {
                listener.onRender(marker);
            }
        }
    }

    /** Creates or moves the marker's anchor. Returns false if no anchor could be created. */
    private boolean ensureAnchor(Session session, LocationMarker marker, GeoPlacement placement) {
        AnchorNode anchorNode = marker.anchorNode;
        Anchor current = anchorNode == null ? null : anchorNode.getAnchor();
        if (current != null && current.getTrackingState() != TrackingState.STOPPED) {
            Pose pose = current.getPose();
            float dx = pose.tx() - placement.getWorldX();
            float dy = pose.ty() - placement.getWorldY();
            float dz = pose.tz() - placement.getWorldZ();
            float tolerance = repositionToleranceMetres + 0.01f * placement.getRenderDistanceMetres();
            if (dx * dx + dy * dy + dz * dz <= tolerance * tolerance) {
                return true;
            }
        }
        Anchor replacement;
        try {
            replacement = session.createAnchor(
                    Pose.makeTranslation(placement.getWorldX(), placement.getWorldY(), placement.getWorldZ()));
        } catch (RuntimeException e) {
            // Usually NotTrackingException or ResourceExhaustedException; try again next frame.
            Log.w(TAG, "Could not create anchor", e);
            return current != null;
        }
        if (anchorNode == null) {
            anchorNode = new AnchorNode(replacement);
            anchorNode.setParent(sceneView.getScene());
            marker.getLayoutNode().setParent(anchorNode);
            marker.anchorNode = anchorNode;
        } else {
            anchorNode.setAnchor(replacement);
        }
        if (current != null) {
            current.detach();
        }
        return true;
    }

    private float scaleFor(LocationMarker marker, float cameraDistance) {
        GeoPlacement placement = marker.placement;
        float fraction = placement == null ? 0f
                : Math.min(placement.getRenderDistanceMetres() / maxRenderDistanceMetres, 1f);
        float gradual = marker.getGradualScalingMaxScale()
                - (marker.getGradualScalingMaxScale() - marker.getGradualScalingMinScale()) * fraction;
        float scale;
        switch (marker.getScalingMode()) {
            case NO_SCALING:
                scale = 1f;
                break;
            case GRADUAL_TO_MAX_RENDER_DISTANCE:
                scale = gradual * (placement == null ? 1f : placement.getRenderDistanceMetres());
                break;
            case GRADUAL_FIXED_SIZE:
                scale = gradual * cameraDistance;
                break;
            case FIXED_SIZE_ON_SCREEN:
            default:
                scale = cameraDistance;
                break;
        }
        return Math.max(scale * marker.getScaleModifier(), 0.001f);
    }
}
