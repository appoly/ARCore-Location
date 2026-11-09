package uk.co.appoly.arcorelocation.sceneform;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Comparator;

import uk.co.appoly.arcorelocation.geo.GeoMath;

/**
 * Decides which markers to show and how high to stack them. Kept free of Sceneform types so it can
 * be unit tested.
 */
final class MarkerLayout {

    static final class Result {
        final boolean[] visible;
        final int[] stackIndex;

        Result(int size) {
            visible = new boolean[size];
            stackIndex = new int[size];
        }
    }

    private MarkerLayout() {
    }

    /**
     * @param bearings   direction from the camera to each marker, in degrees
     * @param distances  real distance to each marker; nearer markers take priority
     * @param mode       overlap handling
     * @param overlapAngleDegrees markers closer together than this, seen from the camera, overlap
     * @param maxVisible at most this many markers are shown, nearest first
     */
    @NonNull
    static Result compute(@NonNull double[] bearings, @NonNull double[] distances, @NonNull OverlapMode mode,
                          double overlapAngleDegrees, int maxVisible) {
        int n = bearings.length;
        Result result = new Result(n);
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble(i -> distances[i]));

        int[] shown = new int[n];
        int shownCount = 0;
        for (Integer index : order) {
            int i = index;
            if (shownCount >= maxVisible) {
                continue;
            }
            int overlaps = 0;
            if (mode != OverlapMode.NONE) {
                for (int s = 0; s < shownCount; s++) {
                    if (Math.abs(GeoMath.angleDifference(bearings[shown[s]], bearings[i])) < overlapAngleDegrees) {
                        overlaps++;
                    }
                }
            }
            if (mode == OverlapMode.HIDE && overlaps > 0) {
                continue;
            }
            result.visible[i] = true;
            result.stackIndex[i] = mode == OverlapMode.STACK ? overlaps : 0;
            shown[shownCount++] = i;
        }
        return result;
    }
}
