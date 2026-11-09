package uk.co.appoly.arcorelocation.sceneform;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MarkerLayoutTest {

    private static final double[] BEARINGS = {10, 12, 200, 358};
    private static final double[] DISTANCES = {50, 20, 30, 80};

    @Test
    public void noneShowsEverything() {
        MarkerLayout.Result r = MarkerLayout.compute(BEARINGS, DISTANCES, OverlapMode.NONE, 6, Integer.MAX_VALUE);
        assertArrayEquals(new boolean[]{true, true, true, true}, r.visible);
        assertArrayEquals(new int[]{0, 0, 0, 0}, r.stackIndex);
    }

    @Test
    public void hideKeepsTheNearerOfOverlappingMarkers() {
        MarkerLayout.Result r = MarkerLayout.compute(BEARINGS, DISTANCES, OverlapMode.HIDE, 6, Integer.MAX_VALUE);
        assertFalse("10 degrees is behind the nearer marker at 12", r.visible[0]);
        assertTrue(r.visible[1]);
        assertTrue(r.visible[2]);
        assertTrue("358 is 12 degrees from 10 and 14 from 12", r.visible[3]);
    }

    @Test
    public void overlapWrapsAroundNorth() {
        MarkerLayout.Result r = MarkerLayout.compute(new double[]{359, 2}, new double[]{10, 20},
                OverlapMode.HIDE, 6, Integer.MAX_VALUE);
        assertTrue(r.visible[0]);
        assertFalse(r.visible[1]);
    }

    @Test
    public void stackRaisesFurtherMarkers() {
        MarkerLayout.Result r = MarkerLayout.compute(new double[]{0, 1, 2, 90}, new double[]{10, 20, 30, 40},
                OverlapMode.STACK, 6, Integer.MAX_VALUE);
        assertArrayEquals(new boolean[]{true, true, true, true}, r.visible);
        assertArrayEquals(new int[]{0, 1, 2, 0}, r.stackIndex);
    }

    @Test
    public void maxVisibleKeepsTheNearest() {
        MarkerLayout.Result r = MarkerLayout.compute(BEARINGS, DISTANCES, OverlapMode.NONE, 6, 2);
        assertArrayEquals(new boolean[]{false, true, true, false}, r.visible);
    }

    @Test
    public void emptyInput() {
        MarkerLayout.Result r = MarkerLayout.compute(new double[0], new double[0], OverlapMode.STACK, 6, 5);
        assertEquals(0, r.visible.length);
    }
}
