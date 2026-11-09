package uk.co.appoly.arcorelocation.geo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.co.appoly.arcorelocation.GeoCoordinate;

public class GeoMathTest {

    @Test
    public void distanceLondonToParis() {
        GeoCoordinate london = GeoCoordinate.of(51.5074, -0.1278);
        GeoCoordinate paris = GeoCoordinate.of(48.8566, 2.3522);
        assertEquals(343_500, GeoMath.distanceMetres(london, paris), 1_000);
    }

    @Test
    public void distanceAcrossTheAntimeridianIsShort() {
        GeoCoordinate west = GeoCoordinate.of(0, 179.9999);
        GeoCoordinate east = GeoCoordinate.of(0, -179.9999);
        assertEquals(22.2, GeoMath.distanceMetres(west, east), 0.2);
        assertEquals(90, GeoMath.bearingDegrees(west, east), 0.01);
    }

    @Test
    public void antipodalDistanceIsFinite() {
        double d = GeoMath.distanceMetres(GeoCoordinate.of(0, 0), GeoCoordinate.of(0, 180));
        assertEquals(Math.PI * GeoMath.EARTH_RADIUS_METRES, d, 1);
    }

    @Test
    public void cardinalBearings() {
        GeoCoordinate o = GeoCoordinate.of(10, 10);
        assertEquals(0, GeoMath.bearingDegrees(o, GeoCoordinate.of(10.001, 10)), 0.01);
        assertEquals(90, GeoMath.bearingDegrees(o, GeoCoordinate.of(10, 10.001)), 0.01);
        assertEquals(180, GeoMath.bearingDegrees(o, GeoCoordinate.of(9.999, 10)), 0.01);
        assertEquals(270, GeoMath.bearingDegrees(o, GeoCoordinate.of(10, 9.999)), 0.01);
    }

    @Test
    public void destinationRoundTrips() {
        GeoCoordinate start = GeoCoordinate.of(51.4778, -0.0015, 12);
        GeoCoordinate end = GeoMath.destination(start, 37, 250);
        assertEquals(250, GeoMath.distanceMetres(start, end), 0.01);
        assertEquals(37, GeoMath.bearingDegrees(start, end), 0.01);
        assertEquals(12, end.getAltitude(), 0);
    }

    @Test
    public void eastNorthMatchesOffset() {
        GeoCoordinate o = GeoCoordinate.of(-33.8568, 151.2153);
        GeoCoordinate t = GeoMath.offset(o, 30, -40);
        double[] en = GeoMath.eastNorth(o, t);
        assertEquals(30, en[0], 0.01);
        assertEquals(-40, en[1], 0.01);
    }

    @Test
    public void angleHelpers() {
        assertEquals(350, GeoMath.normalizeDegrees(-10), 1e-9);
        assertEquals(0, GeoMath.normalizeDegrees(720), 1e-9);
        assertEquals(-170, GeoMath.wrapDegrees(190), 1e-9);
        assertEquals(20, GeoMath.angleDifference(350, 10), 1e-9);
        assertEquals(-20, GeoMath.angleDifference(10, 350), 1e-9);
    }
}
