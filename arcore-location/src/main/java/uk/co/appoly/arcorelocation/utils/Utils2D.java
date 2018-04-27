package uk.co.appoly.arcorelocation.utils;

import android.app.Activity;
import android.opengl.Matrix;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;

import com.google.ar.core.Frame;
import com.google.ar.core.Pose;

import uk.co.appoly.arcorelocation.LocationMarker;
import uk.co.appoly.arcorelocation.LocationScene;

public class Utils2D {

    public static void handleTap(Activity activity, LocationScene locationScene, Frame frame, MotionEvent tap) {
        Log.i("Utils2D", "Handling tap...");
        for (LocationMarker marker : locationScene.mLocationMarkers) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int height = displayMetrics.heightPixels;
            int width = displayMetrics.widthPixels;

            float[] viewMatrix = new float[16];
            frame.getCamera().getViewMatrix(viewMatrix, 0);

            float[] projectionMatrix = new float[16];
            frame.getCamera().getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

            float[] anchorMatrix = new float[16];
            marker.anchor.getPose().toMatrix(anchorMatrix, 0);

            float[] world2screenMatrix = Utils2D.calculateWorld2CameraMatrix(anchorMatrix, viewMatrix, projectionMatrix);
            double[] anchor_2d = Utils2D.world2Screen(marker.anchor.getPose(), width, height, world2screenMatrix);


            if (marker.getTouchableSize() <= 0)
                marker.setTouchableSize(800);

           /* if (anchor_2d[0] < tap.getX() + (marker.getTouchableSize() / 2) && anchor_2d[1] < tap.getY() + (marker.getTouchableSize() / 2) &&
                    anchor_2d[0] + (marker.getTouchableSize() / 2) > tap.getX() && anchor_2d[1] + (marker.getTouchableSize() / 2) > tap.getY()) {*/

           /*if(tap.getX() > anchor_2d[0] - (marker.getTouchableSize() / 2) && tap.getY() >  anchor_2d[1] - (marker.getTouchableSize() / 2) &&
                   tap.getX() < anchor_2d[0] + (marker.getTouchableSize() / 2) && tap.getY() <  anchor_2d[1] + (marker.getTouchableSize() / 2) ) {*/
            if(Math.abs(tap.getX() - anchor_2d[0]) < (marker.getTouchableSize() / 2) &&
                    Math.abs(tap.getY() - anchor_2d[1]) < (marker.getTouchableSize() / 2)) {
                // Tapped anchor
                if (marker.getTouchEvent() != null) {
                    Log.i("Utils2D", "Running tap event");
                    activity.runOnUiThread(marker.getTouchEvent());
                } else {
                    Log.i("Utils2D", "Tap event isnt set");
                }
            }

            System.out.println("TAPPED X/ANCHOR X: " + tap.getX() + " " + anchor_2d[0]);
            System.out.println("TAPPED Y/ANCHOR Y: " + tap.getY() + " " + anchor_2d[1]);


            //System.out.println("DIFFERENCE X/DIFFERENCE Y: " + Math.abs(tap.getX() - anchor_2d[0]) + " " + Math.abs(tap.getY() - anchor_2d[1]));
            System.out.println("MARKER TOUCHABLE SIZE: " + marker.getTouchableSize() + " " + (marker.getTouchableSize() / 2));

        }

    }

    public static float[] calculateWorld2CameraMatrix(float[] modelmtx, float[] viewmtx, float[] prjmtx) {

        float scaleFactor = 1.0f;
        float[] scaleMatrix = new float[16];
        float[] modelXscale = new float[16];
        float[] viewXmodelXscale = new float[16];
        float[] world2screenMatrix = new float[16];

        Matrix.setIdentityM(scaleMatrix, 0);
        scaleMatrix[0] = scaleFactor;
        scaleMatrix[5] = scaleFactor;
        scaleMatrix[10] = scaleFactor;

        Matrix.multiplyMM(modelXscale, 0, modelmtx, 0, scaleMatrix, 0);
        Matrix.multiplyMM(viewXmodelXscale, 0, viewmtx, 0, modelXscale, 0);
        Matrix.multiplyMM(world2screenMatrix, 0, prjmtx, 0, viewXmodelXscale, 0);

        return world2screenMatrix;
    }

    public static double[] world2Screen(Pose pos, int screenWidth, int screenHeight, float[] world2cameraMatrix) {
        float[] origin = {0f, 0f, 0f, 1f};
        float[] ndcCoord = new float[4];
        Matrix.multiplyMV(ndcCoord, 0, world2cameraMatrix, 0, origin, 0);

        ndcCoord[0] = ndcCoord[0] / ndcCoord[3];
        ndcCoord[1] = ndcCoord[1] / ndcCoord[3];

        double[] pos_2d = new double[]{0, 0};
        pos_2d[0] = screenWidth * ((ndcCoord[0] + 1.0) / 2.0);
        pos_2d[1] = screenHeight * ((1.0 - ndcCoord[1]) / 2.0);

        return pos_2d;
    }

}
