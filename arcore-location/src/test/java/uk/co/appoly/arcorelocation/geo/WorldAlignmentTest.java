package uk.co.appoly.arcorelocation.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

import uk.co.appoly.arcorelocation.GeoCoordinate;

public class WorldAlignmentTest {

    private static final GeoCoordinate ORIGIN = GeoCoordinate.of(40.7580, -73.9855);
    private static final double NORTH_YAW = 30;

    /** Geographic location of a world position under the true alignment. */
    private static GeoCoordinate truthAt(double worldX, double worldZ) {
        double[] en = WorldAlignment.worldToEastNorth(worldX, worldZ, NORTH_YAW);
        return GeoMath.offset(ORIGIN, en[0], en[1]);
    }

    @Test
    public void rotationsAreInverse() {
        double[] en = WorldAlignment.worldToEastNorth(3, -7, 123);
        double[] xz = WorldAlignment.eastNorthToWorld(en[0], en[1], 123);
        assertEquals(3, xz[0], 1e-9);
        assertEquals(-7, xz[1], 1e-9);
    }

    @Test
    public void northYawZeroMeansNorthIsMinusZ() {
        double[] en = WorldAlignment.worldToEastNorth(0, -1, 0);
        assertEquals(0, en[0], 1e-9);
        assertEquals(1, en[1], 1e-9);
    }

    @Test
    public void notReadyUntilBothKindsOfSample() {
        WorldAlignment a = new WorldAlignment();
        assertFalse(a.isReady());
        a.addHeadingSample(0, 0, 10, 0);
        assertFalse(a.isReady());
        a.addPositionSample(ORIGIN, 5, Double.NaN, 0, 0, 0, 0);
        assertTrue(a.isReady());
    }

    @Test
    public void convergesFromNoisyGpsAndCompass() {
        Random random = new Random(42);
        WorldAlignment a = new WorldAlignment();
        for (int i = 0; i < 60; i++) {
            // Walk around a 20 m loop while turning the camera.
            double angle = i * 0.3;
            double x = 10 * Math.cos(angle);
            double z = 10 * Math.sin(angle);
            GeoCoordinate truth = truthAt(x, z);
            GeoCoordinate gps = GeoMath.offset(truth, random.nextGaussian() * 5, random.nextGaussian() * 5);
            a.addPositionSample(gps, 5, Double.NaN, x, 1.5, z, i * 1000L);

            double cameraYaw = random.nextDouble() * 360;
            double trueHeading = cameraYaw - NORTH_YAW + random.nextGaussian() * 15;
            a.addHeadingSample(cameraYaw, trueHeading, 15, i * 1000L);
        }
        assertEquals(NORTH_YAW, a.getNorthYawDegrees(), 5);
        GeoCoordinate originEstimate = a.worldToGeo(0, Double.NaN, 0);
        assertNotNull(originEstimate);
        assertEquals(0, GeoMath.distanceMetres(ORIGIN, originEstimate), 2.5);
        assertTrue(a.getHorizontalAccuracyMetres() < 2);
    }

    @Test
    public void singleGpsJumpIsRejected() {
        WorldAlignment a = new WorldAlignment();
        a.addHeadingSample(NORTH_YAW, 0, 5, 0);
        for (int i = 0; i < 20; i++) {
            a.addPositionSample(truthAt(i, 0), 5, Double.NaN, i, 0, 0, i);
        }
        // A 300 m jump, reported with a confident 5 m accuracy.
        a.addPositionSample(GeoMath.offset(truthAt(20, 0), 300, 0), 5, Double.NaN, 20, 0, 0, 20);
        GeoCoordinate originEstimate = a.worldToGeo(0, Double.NaN, 0);
        assertNotNull(originEstimate);
        assertEquals(0, GeoMath.distanceMetres(ORIGIN, originEstimate), 0.5);
    }

    @Test
    public void accurateSamplesOutweighPoorOnes() {
        WorldAlignment a = new WorldAlignment();
        // Twenty poor compass readings pointing 20 degrees off, then a few Geospatial-quality ones.
        for (int i = 0; i < 20; i++) {
            a.addHeadingSample(0, -(NORTH_YAW + 20), 30, i);
        }
        for (int i = 0; i < 5; i++) {
            a.addHeadingSample(0, -NORTH_YAW, 2, 100 + i);
        }
        assertEquals(NORTH_YAW, a.getNorthYawDegrees(), 1);
    }

    @Test
    public void headingAveragesAcrossNorth() {
        WorldAlignment a = new WorldAlignment();
        a.addHeadingSample(0, -358, 5, 0); // north yaw 358
        a.addHeadingSample(0, -2, 5, 1);   // north yaw 2
        assertEquals(0, GeoMath.wrapDegrees(a.getNorthYawDegrees()), 1e-6);
    }

    @Test
    public void altitudeIsEstimatedWhenAvailable() {
        WorldAlignment a = new WorldAlignment();
        a.addHeadingSample(0, 0, 5, 0);
        a.addPositionSample(ORIGIN.withAltitude(101.5), 3, 2, 0, 1.5, 0, 0);
        assertTrue(a.hasAltitude());
        assertEquals(0, a.altitudeToWorldY(100), 1e-9);
    }

    @Test
    public void sampleCountsAreBounded() {
        WorldAlignment a = new WorldAlignment();
        a.setMaxHeadingSamples(10);
        a.setMaxPositionSamples(5);
        for (int i = 0; i < 100; i++) {
            a.addHeadingSample(0, 0, 5, i);
            a.addPositionSample(ORIGIN, 5, Double.NaN, 0, 0, 0, i);
        }
        assertEquals(10, a.headingSampleCount());
        assertEquals(5, a.positionSampleCount());
    }

    @Test
    public void oldSamplesExpireButTheNewestIsKept() {
        WorldAlignment a = new WorldAlignment();
        a.setMaxSampleAgeMillis(1000);
        a.addHeadingSample(0, 0, 5, 0);
        a.addHeadingSample(0, 0, 5, 500);
        a.expireSamples(10_000);
        assertEquals(1, a.headingSampleCount());
        assertTrue(a.hasHeading());
    }
}
