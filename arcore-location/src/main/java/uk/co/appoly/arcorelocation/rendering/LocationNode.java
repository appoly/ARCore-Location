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

    private String TAG = "LocationNode";

    private LocationMarker locationMarker;
    private LocationNodeRender renderEvent;
    private int distance;
    private float scaleModifier = 1F;
    private float height = 0F;
    private boolean scaleAtDistance = true;
    private LocationScene locationScene;

    public LocationNode(Anchor anchor, LocationMarker locationMarker, LocationScene locationScene) {
        super(anchor);
        this.locationMarker = locationMarker;
        this.locationScene = locationScene;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getScaleModifier() {
        return scaleModifier;
    }

    public void setScaleModifier(float scaleModifier) {
        this.scaleModifier = scaleModifier;
    }

    public LocationNodeRender getRenderEvent() {
        return renderEvent;
    }

    public void setRenderEvent(LocationNodeRender renderEvent) {
        this.renderEvent = renderEvent;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public boolean shouldScaleAtDistance() {
        return scaleAtDistance;
    }

    public void setScaleAtDistance(boolean scaleAtDistance) {
        this.scaleAtDistance = scaleAtDistance;
    }

    @Override
    public void onUpdate(FrameTime frameTime) {

        // Typically, getScene() will never return null because onUpdate() is only called when the node
        // is in the scene.
        // However, if onUpdate is called explicitly or if the node is removed from the scene on a
        // different thread during onUpdate, then getScene may be null.


        for (Node n : getChildren()) {
            if (getScene() == null) {
                return;
            }

            int markerDistance = (int) Math.ceil(
                    LocationUtils.distance(
                            locationMarker.latitude,
                            locationScene.deviceLocation.currentBestLocation.getLatitude(),
                            locationMarker.longitude,
                            locationScene.deviceLocation.currentBestLocation.getLongitude(),
                            0,
                            0)
            );

            setDistance(markerDistance);

            // Limit the distance of the Anchor within the scene.
            // Prevents uk.co.appoly.arcorelocation.rendering issues.
            int renderDistance = markerDistance;
            if (renderDistance > locationScene.getDistanceLimit())
                renderDistance = locationScene.getDistanceLimit();

            float scale = 1F;

            // Make sure marker stays the same size on screen, no matter the distance
            if (shouldScaleAtDistance())
                scale = 0.5F * (float) renderDistance;

            // Distant markers a little smaller
            if (markerDistance > 3000)
                scale *= 0.75F;

            scale *= scaleModifier;

            Vector3 cameraPosition = getScene().getCamera().getWorldPosition();
            Vector3 nodePosition = n.getWorldPosition();
            n.setWorldPosition(new Vector3(n.getWorldPosition().x, getHeight(), n.getWorldPosition().z));
            Vector3 direction = Vector3.subtract(cameraPosition, nodePosition);
            Quaternion lookRotation = Quaternion.lookRotation(direction, Vector3.up());

            n.setWorldRotation(lookRotation);
            //locationMarker.node.setWorldScale(new Vector3(scale, scale, scale));
            n.setWorldScale(new Vector3(scale, scale, scale));

            if (locationScene.shouldOffsetOverlapping()) {
                if (locationScene.mArSceneView.getScene().overlapTestAll(n).size() > 0) {
                    setHeight(getHeight() + 1.2F);
                }
            }

            if (renderEvent != null) {
                renderEvent.render(this);
            }

        }

    }
}
