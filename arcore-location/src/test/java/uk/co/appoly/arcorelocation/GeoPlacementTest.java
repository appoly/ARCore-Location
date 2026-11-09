package uk.co.appoly.arcorelocation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.co.appoly.arcorelocation.geo.GeoMath;
import uk.co.appoly.arcorelocation.geo.WorldAlignment;

public class GeoPlacementTest {

    private static final GeoCoordinate ORIGIN = GeoCoordinate.of(51.4778, -0.0015);

    /** North is world -Z and the world origin is at ORIGIN, with altitude 10 m at world Y 0. */
    private static WorldAlignment alignedAlignment() {
        WorldAlignment alignment = new WorldAlignment();
        alignment.addHeadingSample(0, 0, 5, 0);
        alignment.addPositionSample(ORIGIN.withAltitude(10), 3, 3, 0, 0, 0, 0);
        return alignment;
    }

    @Test
    public void notReadyReturnsNull() {
        assertNull(GeoPlacement.compute(new WorldAlignment(), ORIGIN, 0, 0, 0, 30, 0));
    }

    @Test
    public void nearbyTargetIsPlacedAtItsRealPosition() {
        GeoCoordinate target = GeoMath.destination(ORIGIN, 90, 10); // 10 m east
        GeoPlacement p = GeoPlacement.compute(alignedAlignment(), target, 0, 1.5f, 0, 30, 0);
        assertNotNull(p);
        assertEquals(10, p.getWorldX(), 0.05);
        assertEquals(0, p.getWorldZ(), 0.05);
        // No altitude on the target: height is relative to the camera.
        assertEquals(1.5, p.getWorldY(), 1e-4);
        assertEquals(10, p.getDistanceMetres(), 0.05);
        assertEquals(90, p.getBearingDegrees(), 0.1);
        assertFalse(p.isCompressed());
    }

    @Test
    public void distantTargetIsPulledInAlongTheSameDirection() {
        GeoCoordinate target = GeoMath.destination(ORIGIN, 0, 1000); // 1 km north
        GeoPlacement p = GeoPlacement.compute(alignedAlignment(), target, 0, 0, 0, 25, 0);
        assertNotNull(p);
        assertEquals(0, p.getWorldX(), 0.05);
        assertEquals(-25, p.getWorldZ(), 0.05);
        assertEquals(25, p.getRenderDistanceMetres(), 0.05);
        assertEquals(1000, p.getDistanceMetres(), 1);
        assertTrue(p.isCompressed());
    }

    @Test
    public void heightIsScaledWithCompression() {
        // 100 m away and 20 m above the camera, drawn at 25 m: the angle above the horizon is kept.
        GeoCoordinate target = GeoMath.destination(ORIGIN, 0, 100);
        GeoPlacement p = GeoPlacement.compute(alignedAlignment(), target, 0, 0, 0, 25, 20);
        assertNotNull(p);
        assertEquals(5, p.getWorldY(), 0.05);
    }

    @Test
    public void altitudeIsUsedWhenKnown() {
        // World Y 0 is 10 m altitude, so a target at 14 m altitude is at world Y 4.
        GeoCoordinate target = GeoMath.destination(ORIGIN, 180, 5).withAltitude(14);
        GeoPlacement p = GeoPlacement.compute(alignedAlignment(), target, 0, 1.5f, 0, 30, 0);
        assertNotNull(p);
        assertEquals(4, p.getWorldY(), 0.05);
        assertEquals(5, p.getWorldZ(), 0.05);
    }

    @Test
    public void placementFollowsCameraPosition() {
        // Walk 4 m north; a target 10 m north of the origin should still be at world Z -10.
        GeoCoordinate target = GeoMath.destination(ORIGIN, 0, 10);
        GeoPlacement p = GeoPlacement.compute(alignedAlignment(), target, 0, 0, -4, 30, 0);
        assertNotNull(p);
        assertEquals(-10, p.getWorldZ(), 0.05);
        assertEquals(6, p.getDistanceMetres(), 0.05);
    }
}
