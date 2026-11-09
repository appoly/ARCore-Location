package uk.co.appoly.arcorelocation.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HeadingMathTest {

    /** Quaternion for a rotation of {@code degrees} about +Y (counter-clockwise seen from above). */
    private static float[] yawQuaternion(double degrees) {
        double half = Math.toRadians(degrees) / 2;
        return new float[]{0, (float) Math.sin(half), 0, (float) Math.cos(half)};
    }

    @Test
    public void identityCameraFacesYawZero() {
        assertEquals(0, HeadingMath.cameraYawDegrees(0, 0, 0, 1), 1e-4);
    }

    @Test
    public void yawIsClockwiseFromAbove() {
        // Rotating counter-clockwise about +Y by 90 degrees turns the camera to face -X: yaw 270.
        float[] q = yawQuaternion(90);
        assertEquals(270, HeadingMath.cameraYawDegrees(q[0], q[1], q[2], q[3]), 1e-3);
        q = yawQuaternion(-90);
        assertEquals(90, HeadingMath.cameraYawDegrees(q[0], q[1], q[2], q[3]), 1e-3);
    }

    @Test
    public void cameraPointingStraightDownHasNoYaw() {
        // Pitch -90 degrees about +X.
        double half = Math.toRadians(-90) / 2;
        assertTrue(Double.isNaN(HeadingMath.cameraYawDegrees((float) Math.sin(half), 0, 0, (float) Math.cos(half))));
    }

    @Test
    public void backCameraHeadingFromRotationMatrix() {
        // Device upright, screen facing south, back camera facing north:
        // device X -> east, device Y -> up, device Z -> south.
        float[] facingNorth = {
                1, 0, 0,
                0, 0, -1,
                0, 1, 0,
        };
        assertEquals(0, HeadingMath.backCameraHeadingDegrees(facingNorth), 1e-4);

        // Rotated so the back camera faces east: device X -> south, device Z -> west.
        float[] facingEast = {
                0, 0, -1,
                -1, 0, 0,
                0, 1, 0,
        };
        assertEquals(90, HeadingMath.backCameraHeadingDegrees(facingEast), 1e-4);
    }

    @Test
    public void phoneLyingFlatHasNoHeading() {
        float[] identity = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        assertTrue(Double.isNaN(HeadingMath.backCameraHeadingDegrees(identity)));
    }

    @Test
    public void eastUpSouthHeading() {
        // Identity in EUS: camera looks along -Z, which is north.
        assertEquals(0, HeadingMath.headingFromEastUpSouth(0, 0, 0, 1), 1e-4);
        // Rotate -90 degrees about Up: camera turns to face east.
        float[] q = yawQuaternion(-90);
        assertEquals(90, HeadingMath.headingFromEastUpSouth(q[0], q[1], q[2], q[3]), 1e-3);
    }
}
