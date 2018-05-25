package uk.co.appoly.arcorelocation;

import com.google.ar.sceneform.Node;

import uk.co.appoly.arcorelocation.rendering.LocationNode;
import uk.co.appoly.arcorelocation.rendering.LocationNodeRender;

/**
 * Created by John on 02/03/2018.
 */

public class LocationMarker {

    // Location in real-world terms
    public double longitude;
    public double latitude;

    // Location in AR terms
    public LocationNode anchorNode;

    // Node to render
    public Node node;

    // Called on each frame if not null
    private LocationNodeRender renderEvent;
    private float scaleModifier = 1F;
    private float height = 0F;
    private boolean scaleAtDistance = true;
    private int onlyRenderWhenWithin = Integer.MAX_VALUE;

    public LocationMarker(double longitude, double latitude, Node node) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.node = node;
    }

    /**
     * Only render this marker when within [onlyRenderWhenWithin] metres
     *
     * @return - metres or -1
     */
    public int getOnlyRenderWhenWithin() {
        return onlyRenderWhenWithin;
    }

    /**
     * Only render this marker when within [onlyRenderWhenWithin] metres
     *
     * @param onlyRenderWhenWithin - metres
     */
    public void setOnlyRenderWhenWithin(int onlyRenderWhenWithin) {
        this.onlyRenderWhenWithin = onlyRenderWhenWithin;
    }

    /**
     * Height based on camera height
     *
     * @return - height in metres
     */
    public float getHeight() {
        return height;
    }

    /**
     * Height based on camera height
     *
     * @param height - height in metres
     */
    public void setHeight(float height) {
        this.height = height;
    }

    /**
     * Whether the marker should stay at the same size on screen, regardless of distance.
     *
     * @return - true/false
     */
    public boolean shouldScaleAtDistance() {
        return scaleAtDistance;
    }

    /**
     * Whether the marker should stay at the same size on screen, regardless of distance.
     *
     * @param scaleAtDistance - true/false
     */
    public void setScaleAtDistance(boolean scaleAtDistance) {
        this.scaleAtDistance = scaleAtDistance;
    }

    /**
     * Scale multiplier
     *
     * @return - multiplier
     */
    public float getScaleModifier() {
        return scaleModifier;
    }

    /**
     * Scale multiplier
     *
     * @param scaleModifier - multiplier
     */
    public void setScaleModifier(float scaleModifier) {
        this.scaleModifier = scaleModifier;
    }


    /**
     * Called on each frame
     *
     * @return - LocationNodeRender (event)
     */
    public LocationNodeRender getRenderEvent() {
        return renderEvent;
    }

    /**
     * Called on each frame.
     */
    public void setRenderEvent(LocationNodeRender renderEvent) {
        this.renderEvent = renderEvent;
    }

}
