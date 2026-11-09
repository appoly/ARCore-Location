package uk.co.appoly.arcorelocation.examplecore;

import android.app.Activity;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

import com.google.ar.core.Session;

/** Tells ARCore the display's size and rotation whenever they change. */
final class DisplayRotationHelper implements DisplayManager.DisplayListener {

    private final Activity activity;
    private final DisplayManager displayManager;
    private volatile boolean viewportChanged;
    private volatile int width;
    private volatile int height;

    DisplayRotationHelper(Activity activity) {
        this.activity = activity;
        this.displayManager = activity.getSystemService(DisplayManager.class);
    }

    void onResume() {
        displayManager.registerDisplayListener(this, null);
    }

    void onPause() {
        displayManager.unregisterDisplayListener(this);
    }

    /** Call from {@code onSurfaceChanged}. */
    void onSurfaceChanged(int width, int height) {
        this.width = width;
        this.height = height;
        viewportChanged = true;
    }

    /** Call before {@code session.update()} on the GL thread. */
    void updateSessionIfNeeded(Session session) {
        if (viewportChanged) {
            session.setDisplayGeometry(display().getRotation(), width, height);
            viewportChanged = false;
        }
    }

    @SuppressWarnings("deprecation")
    private Display display() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return activity.getDisplay();
        }
        return ((WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE)).getDefaultDisplay();
    }

    @Override
    public void onDisplayAdded(int displayId) {
    }

    @Override
    public void onDisplayRemoved(int displayId) {
    }

    @Override
    public void onDisplayChanged(int displayId) {
        viewportChanged = true;
    }
}
