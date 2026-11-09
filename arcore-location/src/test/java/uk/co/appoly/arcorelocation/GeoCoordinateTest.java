package uk.co.appoly.arcorelocation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GeoCoordinateTest {

    @Test
    public void storesLatitudeFirst() {
        GeoCoordinate c = GeoCoordinate.of(51.5, -0.12);
        assertEquals(51.5, c.getLatitude(), 0);
        assertEquals(-0.12, c.getLongitude(), 0);
        assertFalse(c.hasAltitude());
    }

    @Test
    public void altitudeIsOptional() {
        GeoCoordinate c = GeoCoordinate.of(51.5, -0.12, 35);
        assertTrue(c.hasAltitude());
        assertEquals(35, c.getAltitude(), 0);
        assertEquals(c, GeoCoordinate.of(51.5, -0.12).withAltitude(35));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLatitudeOutOfRange() {
        GeoCoordinate.of(91, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSwappedCoordinates() {
        // A longitude such as -122 passed as latitude is the most common mistake.
        GeoCoordinate.of(-122.4194, 37.7749);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNaNLatitude() {
        GeoCoordinate.of(Double.NaN, 0);
    }
}
