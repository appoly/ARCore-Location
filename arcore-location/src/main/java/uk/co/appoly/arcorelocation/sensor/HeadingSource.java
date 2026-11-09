package uk.co.appoly.arcorelocation.sensor;

import androidx.annotation.NonNull;

/**
 * Supplies the heading of the device's back camera. Callbacks must arrive on the main thread.
 */
public interface HeadingSource {

    interface Listener {
        void onHeading(@NonNull HeadingSample sample);

        /** Called when no heading sensor is available or it could not be registered. */
        void onHeadingUnavailable(@NonNull String reason);
    }

    void start(@NonNull Listener listener);

    void stop();
}
