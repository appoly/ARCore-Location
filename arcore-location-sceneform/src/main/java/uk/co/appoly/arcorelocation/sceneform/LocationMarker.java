package uk.co.appoly.arcorelocation.sceneform;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;

import uk.co.appoly.arcorelocation.GeoCoordinate;
import uk.co.appoly.arcorelocation.GeoPlacement;

/**
 * A Sceneform {@link Node} shown at a real-world coordinate. Add it to a {@link LocationScene}.
 *
 * <p>The scene owns the node hierarchy: an {@link AnchorNode} holds a layout node, which holds
 * your node. Position, rotate and scale your node relative to its parent as you like; the scene
 * only changes the anchor and layout nodes.</p>
 *
 * <pre>{@code
 * LocationMarker marker = new LocationMarker(GeoCoordinate.of(51.5033, -0.1196), node)
 *         .setHeight(2f)
 *         .setScalingMode(ScalingMode.GRADUAL_FIXED_SIZE);
 * locationScene.addMarker(marker);
 * }</pre>
 */
public final class LocationMarker {

    /** Called every frame while a marker is visible, for example to update a distance label. */
    public interface OnRenderListener {
        void onRender(@NonNull LocationMarker marker);
    }

    private GeoCoordinate coordinate;
    private final Node node;
    private final Node layoutNode = new Node();

    private float height;
    private double onlyRenderWhenWithinMetres = Double.POSITIVE_INFINITY;
    private ScalingMode scalingMode = ScalingMode.FIXED_SIZE_ON_SCREEN;
    private float scaleModifier = 1f;
    private float gradualScalingMinScale = 0.8f;
    private float gradualScalingMaxScale = 1.4f;
    @Nullable
    private OnRenderListener onRenderListener;

    // State maintained by LocationScene.
    @Nullable
    AnchorNode anchorNode;
    @Nullable
    GeoPlacement placement;
    boolean withinRange = true;
    boolean visible;

    public LocationMarker(@NonNull GeoCoordinate coordinate, @NonNull Node node) {
        this.coordinate = coordinate;
        this.node = node;
        node.setParent(layoutNode);
    }

    @NonNull
    public GeoCoordinate getCoordinate() {
        return coordinate;
    }

    /** Moves the marker. It is repositioned on the next frame. */
    @NonNull
    public LocationMarker setCoordinate(@NonNull GeoCoordinate coordinate) {
        this.coordinate = coordinate;
        return this;
    }

    /** Your content node. */
    @NonNull
    public Node getNode() {
        return node;
    }

    /**
     * Height in metres above the marker's altitude when the coordinate has one and the scene's
     * altitude is known (for example with the Geospatial API), otherwise above the camera.
     */
    public float getHeight() {
        return height;
    }

    @NonNull
    public LocationMarker setHeight(float height) {
        this.height = height;
        return this;
    }

    /** Hide the marker when it is further away than this. Default: always show. */
    public double getOnlyRenderWhenWithinMetres() {
        return onlyRenderWhenWithinMetres;
    }

    @NonNull
    public LocationMarker setOnlyRenderWhenWithinMetres(double metres) {
        this.onlyRenderWhenWithinMetres = metres;
        return this;
    }

    @NonNull
    public ScalingMode getScalingMode() {
        return scalingMode;
    }

    @NonNull
    public LocationMarker setScalingMode(@NonNull ScalingMode scalingMode) {
        this.scalingMode = scalingMode;
        return this;
    }

    /** Multiplier applied after the scaling mode. Default 1. */
    public float getScaleModifier() {
        return scaleModifier;
    }

    @NonNull
    public LocationMarker setScaleModifier(float scaleModifier) {
        this.scaleModifier = scaleModifier;
        return this;
    }

    public float getGradualScalingMinScale() {
        return gradualScalingMinScale;
    }

    @NonNull
    public LocationMarker setGradualScalingMinScale(float scale) {
        this.gradualScalingMinScale = scale;
        return this;
    }

    public float getGradualScalingMaxScale() {
        return gradualScalingMaxScale;
    }

    @NonNull
    public LocationMarker setGradualScalingMaxScale(float scale) {
        this.gradualScalingMaxScale = scale;
        return this;
    }

    @Nullable
    public OnRenderListener getOnRenderListener() {
        return onRenderListener;
    }

    @NonNull
    public LocationMarker setOnRenderListener(@Nullable OnRenderListener listener) {
        this.onRenderListener = listener;
        return this;
    }

    /** Real-world distance from the camera in metres, or {@code NaN} before the first placement. */
    public double getDistanceMetres() {
        return placement == null ? Double.NaN : placement.getDistanceMetres();
    }

    /** Bearing from the camera in degrees from true north, or {@code NaN} before the first placement. */
    public double getBearingDegrees() {
        return placement == null ? Double.NaN : placement.getBearingDegrees();
    }

    /** The latest placement, or {@code null} before the first. */
    @Nullable
    public GeoPlacement getPlacement() {
        return placement;
    }

    /** True if the marker was drawn on the last frame. */
    public boolean isVisible() {
        return visible;
    }

    /** The anchor node currently holding this marker, or {@code null} while it has no anchor. */
    @Nullable
    public AnchorNode getAnchorNode() {
        return anchorNode;
    }

    Node getLayoutNode() {
        return layoutNode;
    }

    /** Detaches the ARCore anchor and removes the marker's nodes from the scene. */
    void detach() {
        AnchorNode current = anchorNode;
        if (current != null) {
            Anchor anchor = current.getAnchor();
            current.setAnchor(null);
            if (anchor != null) {
                anchor.detach();
            }
            layoutNode.setParent(null);
            current.setParent(null);
            anchorNode = null;
        }
        visible = false;
    }
}
