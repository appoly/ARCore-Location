package uk.co.appoly.arcorelocation.sensor;

import androidx.annotation.NonNull;

/**
 * Supplies device location readings. Implement this to use your own location provider or to
 * replay recorded data in tests.
 *
 * <p>Callbacks must arrive on the main thread.</p>
 */
public interface LocationSource {

    interface Listener {
        void onLocation(@NonNull LocationSample sample);

        /** Called when updates cannot start or have stopped, for example without permission. */
        void onLocationUnavailable(@NonNull String reason);
    }

    /** Starts delivering readings to {@code listener}. Calling start while started is a no-op. */
    void start(@NonNull Listener listener);

    void stop();
}
