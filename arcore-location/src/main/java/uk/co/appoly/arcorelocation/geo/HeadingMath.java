package uk.co.appoly.arcorelocation.geo;

/**
 * Converts device and camera orientation into headings.
 *
 * <p>ARCore's world frame is gravity aligned with +Y up, so a horizontal direction in that frame is
 * described by a yaw angle. This library measures yaw in degrees clockwise (seen from above)
 * starting at world -Z, which is where the camera faces when a session starts.</p>
 */
public final class HeadingMath {

    /**
     * Headings are unreliable when the camera points nearly straight up or down. Samples are only
     * used when the horizontal part of the camera's forward vector is at least this long, which
     * corresponds to the camera being within about 60 degrees of horizontal.
     */
    public static final double MIN_HORIZONTAL_COMPONENT = 0.5;

    private HeadingMath() {
    }

    /**
     * Yaw of a world-space direction vector, in degrees clockwise from world -Z, in [0, 360).
     * Returns {@code NaN} if the vector is too close to vertical.
     */
    public static double yawOfWorldDirection(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length == 0) {
            return Double.NaN;
        }
        double horizontal = Math.hypot(x, z) / length;
        if (horizontal < MIN_HORIZONTAL_COMPONENT) {
            return Double.NaN;
        }
        return GeoMath.normalizeDegrees(Math.toDegrees(Math.atan2(x, -z)));
    }

    /**
     * Yaw of the direction a camera faces, given the camera pose's rotation quaternion. Cameras
     * look along their local -Z axis. Returns {@code NaN} when pointing near vertical.
     */
    public static double cameraYawDegrees(float qx, float qy, float qz, float qw) {
        // Rotate (0, 0, -1) by the quaternion.
        double x = -2 * (qx * qz + qw * qy);
        double y = -2 * (qy * qz - qw * qx);
        double z = -(1 - 2 * (qx * qx + qy * qy));
        return yawOfWorldDirection(x, y, z);
    }

    /**
     * Heading of the device's back camera in degrees clockwise from magnetic north, from a rotation
     * matrix produced by {@code SensorManager.getRotationMatrixFromVector}. That matrix maps device
     * coordinates to an East-North-Up world frame. Returns {@code NaN} when pointing near vertical.
     *
     * <p>Unlike {@code SensorManager.getOrientation}, this does not depend on display rotation.</p>
     */
    public static double backCameraHeadingDegrees(float[] rotationMatrix) {
        if (rotationMatrix == null || rotationMatrix.length < 9) {
            return Double.NaN;
        }
        // The back camera looks along device -Z, i.e. world vector -(third column).
        double east = -rotationMatrix[2];
        double north = -rotationMatrix[5];
        double up = -rotationMatrix[8];
        double length = Math.sqrt(east * east + north * north + up * up);
        if (length == 0 || Math.hypot(east, north) / length < MIN_HORIZONTAL_COMPONENT) {
            return Double.NaN;
        }
        return GeoMath.normalizeDegrees(Math.toDegrees(Math.atan2(east, north)));
    }

    /**
     * Heading of a camera in degrees clockwise from true north, from the East-Up-South quaternion
     * returned by ARCore's {@code GeospatialPose.getEastUpSouthQuaternion()}. Returns {@code NaN}
     * when pointing near vertical.
     */
    public static double headingFromEastUpSouth(float qx, float qy, float qz, float qw) {
        // Rotate the camera's forward vector (0, 0, -1) into the EUS frame.
        double east = -2 * (qx * qz + qw * qy);
        double up = -2 * (qy * qz - qw * qx);
        double south = -(1 - 2 * (qx * qx + qy * qy));
        double north = -south;
        double length = Math.sqrt(east * east + up * up + south * south);
        if (length == 0 || Math.hypot(east, north) / length < MIN_HORIZONTAL_COMPONENT) {
            return Double.NaN;
        }
        return GeoMath.normalizeDegrees(Math.toDegrees(Math.atan2(east, north)));
    }
}
