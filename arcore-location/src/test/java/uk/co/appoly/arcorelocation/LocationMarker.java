package uk.co.appoly.arcorelocation;

import com.google.ar.core.Anchor;
import uk.co.appoly.arcorelocation.rendering.Renderer;

/**
 * Created by John on 02/03/2018.
 */

public class LocationMarker {

    // Location in real-world terms
    public double longitude;
    public double latitude;

    // Location in AR terms
    public Anchor anchor;

    //Renderer
    public Renderer renderer;

    public LocationMarker(double longitude, double latitude, Renderer renderer) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.renderer = renderer;
    }
}
