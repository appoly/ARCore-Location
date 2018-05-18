package uk.co.appoly.arcorelocation.rendering;

import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

import uk.co.appoly.arcorelocation.LocationMarker;
import uk.co.appoly.arcorelocation.LocationScene;
import uk.co.appoly.arcorelocation.utils.LocationUtils;

public class LocationNode extends AnchorNode {

    LocationMarker locationMarker;

    public LocationNode(Anchor anchor, LocationMarker locationMarker) {
        super(anchor);
        this.locationMarker = locationMarker;
    }

    public LocationNodeRender getRenderEvent() {
        return renderEvent;
    }

    public void setRenderEvent(LocationNodeRender renderEvent) {
        this.renderEvent = renderEvent;
    }

    private LocationNodeRender renderEvent;

    @Override
    public void onUpdate(FrameTime frameTime) {

        // Typically, getScene() will never return null because onUpdate() is only called when the node
        // is in the scene.
        // However, if onUpdate is called explicitly or if the node is removed from the scene on a
        // different thread during onUpdate, then getScene may be null.

        Log.i("LocationNode", "Marker update called...");

        for (Node n : getChildren()) {
            if (getScene() == null) {
                return;
            }

            int markerDistance = (int) Math.ceil(
                    LocationUtils.distance(
                            locationMarker.latitude,
                            LocationScene.deviceLocation.currentBestLocation.getLatitude(),
                            locationMarker.longitude,
                            LocationScene.deviceLocation.currentBestLocation.getLongitude(),
                            0,
                            0)
            );

            // Limit the distance of the Anchor within the scene.
            // Prevents uk.co.appoly.arcorelocation.rendering issues.
            int renderDistance = markerDistance;
            if (renderDistance > LocationScene.distanceLimit)
                renderDistance = LocationScene.distanceLimit;

            // Make sure marker stays the same size on screen, no matter the distance
            float scale = 0.5F * (float) renderDistance;

            // Distant markers a little smaller
            if (markerDistance > 3000)
                scale *= 0.75F;


            Vector3 cameraPosition = getScene().getCamera().getWorldPosition();
            Vector3 cardPosition = n.getWorldPosition();
            n.setWorldPosition(new Vector3(n.getWorldPosition().x, 0F, n.getWorldPosition().z));
            Vector3 direction = Vector3.subtract(cameraPosition, cardPosition);
            Quaternion lookRotation = Quaternion.lookRotation(direction, Vector3.up());

            n.setWorldRotation(lookRotation);
            //locationMarker.node.setWorldScale(new Vector3(scale, scale, scale));
            n.setWorldScale(new Vector3(scale, scale, scale));

            if(renderEvent != null) {
                renderEvent.render(markerDistance);
            }

        }

    }
}
