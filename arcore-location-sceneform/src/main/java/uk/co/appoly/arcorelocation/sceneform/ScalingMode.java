package uk.co.appoly.arcorelocation.sceneform;

/** How a marker's content is scaled as the camera's distance to it changes. */
public enum ScalingMode {
    /** Content keeps the same size on screen at any distance. */
    FIXED_SIZE_ON_SCREEN,
    /** Content is never scaled, so it shrinks with distance like a real object. */
    NO_SCALING,
    /**
     * Content is scaled with its drawn distance, between the marker's maximum gradual scale when
     * close and its minimum gradual scale at the maximum render distance.
     */
    GRADUAL_TO_MAX_RENDER_DISTANCE,
    /**
     * Like {@link #FIXED_SIZE_ON_SCREEN}, but multiplied by a factor that falls from the maximum
     * gradual scale when close to the minimum at the maximum render distance, so nearer markers
     * look slightly larger.
     */
    GRADUAL_FIXED_SIZE
}
