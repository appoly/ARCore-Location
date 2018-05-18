package uk.co.appoly.arcorelocation;

import com.google.ar.sceneform.AnchorNode;
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

    public LocationNodeRender getRenderEvent() {
        return renderEvent;
    }

    public void setRenderEvent(LocationNodeRender renderEvent) {
        this.renderEvent = renderEvent;
    }

    private LocationNodeRender renderEvent;

    public LocationMarker(double longitude, double latitude, Node node) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.node = node;
    }

}
