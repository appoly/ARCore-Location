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
    private float height = 0F;

    public LocationMarker(double longitude, double latitude, Node node) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.node = node;
    }

    /**
     * Height based on camera height
     */
    public float getHeight() {
        return height;
    }

    /**
     * Height based on camera height
     *
     * @param height
     */
    public void setHeight(float height) {
        this.height = height;
    }

    /**
     * Called on each frame
     *
     * @return
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
