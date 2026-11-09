package uk.co.appoly.arcorelocation.sceneform;

/** What to do when markers line up in the same direction from the camera. */
public enum OverlapMode {
    /** Draw every marker. */
    NONE,
    /** Hide markers that are behind a nearer marker. */
    HIDE,
    /** Raise markers that are behind a nearer marker so they stack vertically. */
    STACK
}
