package uk.co.appoly.arcorelocation.utils;

/**
 * Created by johnwedgbury on 01/06/2018.
 */

public class KalmanLatLong {
    private final float MinAccuracy = 1;

    private float Q_metres_per_second;
    private long TimeStamp_milliseconds;
    private double lat;
    private double lng;
    private float variance; // P matrix. Negative means object uninitialised.
    // NB: units irrelevant, as long as same units used
    // throughout
    public int consecutiveRejectCount;

    public KalmanLatLong(float Q_metres_per_second) {
        this.Q_metres_per_second = Q_metres_per_second;
        variance = -1;
        consecutiveRejectCount = 0;
    }

    public long get_TimeStamp() {
        return TimeStamp_milliseconds;
    }

    public double get_lat() {
        return lat;
    }

    public double get_lng() {
        return lng;
    }

    public float get_accuracy() {
        return (float) Math.sqrt(variance);
    }

    public void SetState(double lat, double lng, float accuracy,
                         long TimeStamp_milliseconds) {
        this.lat = lat;
        this.lng = lng;
        variance = accuracy * accuracy;
        this.TimeStamp_milliseconds = TimeStamp_milliseconds;
    }

    // / <summary>
    // / Kalman filter processing for lattitude and longitude
    // / </summary>
    // / <param name="lat_measurement_degrees">new measurement of
    // lattidude</param>
    // / <param name="lng_measurement">new measurement of longitude</param>
    // / <param name="accuracy">measurement of 1 standard deviation error in
    // metres</param>
    // / <param name="TimeStamp_milliseconds">time of measurement</param>
    // / <returns>new state</returns>
    public void Process(double lat_measurement, double lng_measurement,
                        float accuracy, long TimeStamp_milliseconds, float Q_metres_per_second) {
        this.Q_metres_per_second = Q_metres_per_second;

        if (accuracy < MinAccuracy)
            accuracy = MinAccuracy;
        if (variance < 0) {
            // if variance < 0, object is unitialised, so initialise with
            // current values
            this.TimeStamp_milliseconds = TimeStamp_milliseconds;
            lat = lat_measurement;
            lng = lng_measurement;
            variance = accuracy * accuracy;
        } else {
            // else apply Kalman filter methodology

            long TimeInc_milliseconds = TimeStamp_milliseconds
                    - this.TimeStamp_milliseconds;
            if (TimeInc_milliseconds > 0) {
                // time has moved on, so the uncertainty in the current position
                // increases
                variance += TimeInc_milliseconds * Q_metres_per_second
                        * Q_metres_per_second / 1000;
                this.TimeStamp_milliseconds = TimeStamp_milliseconds;
                // TO DO: USE VELOCITY INFORMATION HERE TO GET A BETTER ESTIMATE
                // OF CURRENT POSITION
            }

            // Kalman gain matrix K = Covarariance * Inverse(Covariance +
            // MeasurementVariance)
            // NB: because K is dimensionless, it doesn't matter that variance
            // has different units to lat and lng
            float K = variance / (variance + accuracy * accuracy);
            // apply K
            lat += K * (lat_measurement - lat);
            lng += K * (lng_measurement - lng);
            // new Covarariance matrix is (IdentityMatrix - K) * Covarariance
            variance = (1 - K) * variance;
        }
    }

    public int getConsecutiveRejectCount() {
        return consecutiveRejectCount;
    }

    public void setConsecutiveRejectCount(int consecutiveRejectCount) {
        this.consecutiveRejectCount = consecutiveRejectCount;
    }


}