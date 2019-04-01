package uk.co.appoly.arcorelocation.rendering;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.collision.Ray;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

import java.util.ArrayList;

import uk.co.appoly.arcorelocation.LocationMarker;
import uk.co.appoly.arcorelocation.LocationScene;
import uk.co.appoly.arcorelocation.utils.LocationUtils;

public class LocationNode extends AnchorNode {

    private String TAG = "LocationNode";

    private LocationMarker locationMarker;
    private LocationNodeRender renderEvent;
    private int distance;
    private double distanceInAR;
    private float scaleModifier = 1F;
    private float height = 0F;
    private float gradualScalingMinScale = 0.8F;
    private float gradualScalingMaxScale = 1.4F;

    private LocationMarker.ScalingMode scalingMode = LocationMarker.ScalingMode.FIXED_SIZE_ON_SCREEN;
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

    public LocationMarker getLocationMarker() {
        return locationMarker;
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

    public double getDistanceInAR() {
        return distanceInAR;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public void setDistanceInAR(double distanceInAR) {
        this.distanceInAR = distanceInAR;
    }

    public LocationMarker.ScalingMode getScalingMode() {
        return scalingMode;
    }

    public void setScalingMode(LocationMarker.ScalingMode scalingMode) {
        this.scalingMode = scalingMode;
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

            Vector3 cameraPosition = getScene().getCamera().getWorldPosition();
            Vector3 nodePosition = n.getWorldPosition();

            // Compute the difference vector between the camera and anchor
            float dx = cameraPosition.x - nodePosition.x;
            float dy = cameraPosition.y - nodePosition.y;
            float dz = cameraPosition.z - nodePosition.z;

            // Compute the straight-line distance.
            double distanceInAR = Math.sqrt(dx * dx + dy * dy + dz * dz);
            setDistanceInAR(distanceInAR);

            if (locationScene.shouldOffsetOverlapping()) {
                if (locationScene.mArSceneView.getScene().overlapTestAll(n).size() > 0) {
                    setHeight(getHeight() + 1.2F);
                }
            }

            if (locationScene.shouldRemoveOverlapping()) {
                Ray ray = new Ray();
                ray.setOrigin(cameraPosition);

                float xDelta = (float) (distanceInAR * Math.sin(Math.PI / 15)); //12 degrees
                Vector3 cameraLeft = getScene().getCamera().getLeft().normalized();

                Vector3 left = Vector3.add(nodePosition, cameraLeft.scaled(xDelta));
                Vector3 center = nodePosition;
                Vector3 right = Vector3.add(nodePosition, cameraLeft.scaled(-xDelta));

                boolean isOverlapping = isOverlapping(n, ray, left, cameraPosition)
                        || isOverlapping(n, ray, center, cameraPosition)
                        || isOverlapping(n, ray, right, cameraPosition);

                if (isOverlapping) {
                    setEnabled(false);
                } else {
                    setEnabled(true);
                }
            }
        }

        if (!locationScene.minimalRefreshing())
            scaleAndRotate();


        if (renderEvent != null) {
            if (this.isTracking() && this.isActive() && this.isEnabled())
                renderEvent.render(this);
        }
    }

    private boolean isOverlapping(Node n, Ray ray, Vector3 target, Vector3 cameraPosition) {
        Vector3 nodeDirection = Vector3.subtract(target, cameraPosition);
        ray.setDirection(nodeDirection);

        ArrayList<HitTestResult> hitTestResults = locationScene.mArSceneView.getScene().hitTestAll(ray);
        if (hitTestResults.size() > 0) {

            HitTestResult closestHit = null;
            for (HitTestResult hit : hitTestResults) {
                //Get the closest hit on enabled Node
                if (hit.getNode() != null && hit.getNode().isEnabled()) {
                    closestHit = hit;
                    break;
                }
            }

            // if closest hit is not the current node, it is hidden behind another node that is closer
            return closestHit != null && closestHit.getNode() != n;
        }
        return false;
    }

    public void scaleAndRotate() {
        for (Node n : getChildren()) {
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
            final Vector3 cameraPosition = getScene().getCamera().getWorldPosition();
            Vector3 direction = Vector3.subtract(cameraPosition, n.getWorldPosition());

            switch (scalingMode) {
                case FIXED_SIZE_ON_SCREEN:
                    scale = (float) Math.sqrt(direction.x * direction.x
                            + direction.y * direction.y + direction.z * direction.z);
                    break;
                case GRADUAL_TO_MAX_RENDER_DISTANCE:
                    float scaleDifference = gradualScalingMaxScale - gradualScalingMinScale;
                    scale = (gradualScalingMinScale + ((locationScene.getDistanceLimit() - markerDistance) * (scaleDifference / locationScene.getDistanceLimit()))) * renderDistance;
                    break;
                case GRADUAL_FIXED_SIZE:
                    scale = (float) Math.sqrt(direction.x * direction.x
                            + direction.y * direction.y + direction.z * direction.z);
                    float gradualScale = gradualScalingMaxScale - gradualScalingMinScale;
                    gradualScale = gradualScalingMaxScale - (gradualScale / renderDistance * markerDistance);
                    scale *= Math.max(gradualScale, gradualScalingMinScale);
                    break;
            }

            scale *= scaleModifier;

            //Log.d("LocationScene", "scale " + scale);
            n.setWorldPosition(new Vector3(n.getWorldPosition().x, getHeight(), n.getWorldPosition().z));
            Quaternion lookRotation = Quaternion.lookRotation(direction, Vector3.up());
            n.setWorldRotation(lookRotation);
            n.setWorldScale(new Vector3(scale, scale, scale));
        }
    }

    public float getGradualScalingMinScale() {
        return gradualScalingMinScale;
    }

    public void setGradualScalingMinScale(float gradualScalingMinScale) {
        this.gradualScalingMinScale = gradualScalingMinScale;
    }

    public float getGradualScalingMaxScale() {
        return gradualScalingMaxScale;
    }

    public void setGradualScalingMaxScale(float gradualScalingMaxScale) {
        this.gradualScalingMaxScale = gradualScalingMaxScale;
    }
}
