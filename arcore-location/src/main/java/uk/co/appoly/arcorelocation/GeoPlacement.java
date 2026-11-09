package uk.co.appoly.arcorelocation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import uk.co.appoly.arcorelocation.geo.GeoMath;
import uk.co.appoly.arcorelocation.geo.WorldAlignment;

/**
 * Where a geographic target should be drawn in ARCore's world frame.
 *
 * <p>Targets further away than the maximum render distance are pulled in along the same direction
 * so they stay inside the renderer's far clipping plane. Their height above or below the camera is
 * scaled by the same ratio, so they still appear in the right place on the horizon.</p>
 */
public final class GeoPlacement {

    private final float worldX;
    private final float worldY;
    private final float worldZ;
    private final double distanceMetres;
    private final double bearingDegrees;
    private final float renderDistanceMetres;

    GeoPlacement(float worldX, float worldY, float worldZ, double distanceMetres,
                 double bearingDegrees, float renderDistanceMetres) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldZ = worldZ;
        this.distanceMetres = distanceMetres;
        this.bearingDegrees = bearingDegrees;
        this.renderDistanceMetres = renderDistanceMetres;
    }

    /**
     * Computes a placement.
     *
     * @param alignment          the current world alignment; must be ready
     * @param target             where the content is in the real world
     * @param cameraX            camera world position
     * @param cameraY            camera world position
     * @param cameraZ            camera world position
     * @param maxRenderDistance  targets further than this are drawn at this distance
     * @param heightOffsetMetres added to the target's height. When the target has an altitude and
     *                           the alignment knows the world's altitude, height is measured from
     *                           that altitude; otherwise it is measured from the camera.
     * @return the placement, or {@code null} if the alignment is not ready
     */
    @Nullable
    public static GeoPlacement compute(@NonNull WorldAlignment alignment, @NonNull GeoCoordinate target,
                                       float cameraX, float cameraY, float cameraZ,
                                       float maxRenderDistance, float heightOffsetMetres) {
        GeoCoordinate camera = alignment.worldToGeo(cameraX, Double.NaN, cameraZ);
        double[] offset = alignment.worldOffsetTo(target, cameraX, cameraZ);
        if (camera == null || offset == null) {
            return null;
        }
        double distance = GeoMath.distanceMetres(camera, target);
        double bearing = GeoMath.bearingDegrees(camera, target);

        double dy = heightOffsetMetres;
        if (target.hasAltitude()) {
            double targetY = alignment.altitudeToWorldY(target.getAltitude());
            if (!Double.isNaN(targetY)) {
                dy += targetY - cameraY;
            }
        }

        double horizontal = Math.hypot(offset[0], offset[1]);
        double ratio = 1.0;
        float limit = Math.max(maxRenderDistance, 1f);
        if (horizontal > limit) {
            ratio = limit / horizontal;
        }
        return new GeoPlacement(
                (float) (cameraX + offset[0] * ratio),
                (float) (cameraY + dy * ratio),
                (float) (cameraZ + offset[1] * ratio),
                distance,
                bearing,
                (float) (horizontal * ratio));
    }

    public float getWorldX() {
        return worldX;
    }

    public float getWorldY() {
        return worldY;
    }

    public float getWorldZ() {
        return worldZ;
    }

    /** Real-world horizontal distance from the camera to the target in metres. */
    public double getDistanceMetres() {
        return distanceMetres;
    }

    /** Bearing from the camera to the target, in degrees clockwise from true north. */
    public double getBearingDegrees() {
        return bearingDegrees;
    }

    /** Horizontal distance at which the target is drawn, at most the maximum render distance. */
    public float getRenderDistanceMetres() {
        return renderDistanceMetres;
    }

    /** True if the target was pulled in to the maximum render distance. */
    public boolean isCompressed() {
        return renderDistanceMetres + 0.01 < distanceMetres;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US, "GeoPlacement(world=%.2f,%.2f,%.2f distance=%.1fm bearing=%.1f°)",
                worldX, worldY, worldZ, distanceMetres, bearingDegrees);
    }
}
