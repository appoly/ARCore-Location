package uk.co.appoly.arcorelocation.sensor;

/** One heading reading: the direction the back camera faces. */
public final class HeadingSample {

    private final double headingDegrees;
    private final double accuracyDegrees;
    private final boolean magnetic;
    private final long elapsedRealtimeMillis;

    /**
     * @param headingDegrees        degrees clockwise from north
     * @param accuracyDegrees       approximate 1-sigma error
     * @param magnetic              true if relative to magnetic north, false if true north
     * @param elapsedRealtimeMillis when the reading was taken, on the
     *                              {@code SystemClock.elapsedRealtime()} clock
     */
    public HeadingSample(double headingDegrees, double accuracyDegrees, boolean magnetic,
                         long elapsedRealtimeMillis) {
        this.headingDegrees = headingDegrees;
        this.accuracyDegrees = accuracyDegrees;
        this.magnetic = magnetic;
        this.elapsedRealtimeMillis = elapsedRealtimeMillis;
    }

    public double getHeadingDegrees() {
        return headingDegrees;
    }

    public double getAccuracyDegrees() {
        return accuracyDegrees;
    }

    public boolean isMagnetic() {
        return magnetic;
    }

    public long getElapsedRealtimeMillis() {
        return elapsedRealtimeMillis;
    }
}
