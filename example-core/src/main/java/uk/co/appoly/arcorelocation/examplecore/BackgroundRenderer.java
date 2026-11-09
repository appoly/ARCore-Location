package uk.co.appoly.arcorelocation.examplecore;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Draws the ARCore camera image as a full-screen background. Use only on the GL thread. */
final class BackgroundRenderer {

    private static final String VERTEX_SHADER =
            "attribute vec4 a_Position;\n"
                    + "attribute vec2 a_TexCoord;\n"
                    + "varying vec2 v_TexCoord;\n"
                    + "void main() {\n"
                    + "  gl_Position = a_Position;\n"
                    + "  v_TexCoord = a_TexCoord;\n"
                    + "}\n";

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float;\n"
                    + "varying vec2 v_TexCoord;\n"
                    + "uniform samplerExternalOES u_Texture;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = texture2D(u_Texture, v_TexCoord);\n"
                    + "}\n";

    /** A full-screen quad in normalised device coordinates, as a triangle strip. */
    private static final float[] QUAD_COORDS = {-1f, -1f, +1f, -1f, -1f, +1f, +1f, +1f};

    private final FloatBuffer quadCoords = floatBuffer(QUAD_COORDS.length);
    private final FloatBuffer quadTexCoords = floatBuffer(QUAD_COORDS.length);

    private int textureId = -1;
    private int program;
    private int positionAttrib;
    private int texCoordAttrib;

    /** Creates the camera texture and shader program. Call from {@code onSurfaceCreated}. */
    void createOnGlThread() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];
        int target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
        GLES20.glBindTexture(target, textureId);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        quadCoords.put(QUAD_COORDS).position(0);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER));
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER));
        GLES20.glLinkProgram(program);
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position");
        texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord");
    }

    int getTextureId() {
        return textureId;
    }

    /** Draws the camera image for {@code frame}. */
    void draw(Frame frame) {
        if (frame.hasDisplayGeometryChanged()) {
            quadCoords.position(0);
            quadTexCoords.position(0);
            frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quadCoords,
                    Coordinates2d.TEXTURE_NORMALIZED, quadTexCoords);
        }
        if (frame.getTimestamp() == 0) {
            // ARCore hasn't produced a camera image yet.
            return;
        }
        quadCoords.position(0);
        quadTexCoords.position(0);

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUseProgram(program);
        GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadCoords);
        GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords);
        GLES20.glEnableVertexAttribArray(positionAttrib);
        GLES20.glEnableVertexAttribArray(texCoordAttrib);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(positionAttrib);
        GLES20.glDisableVertexAttribArray(texCoordAttrib);
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Shader compile failed: " + log);
        }
        return shader;
    }

    private static FloatBuffer floatBuffer(int floats) {
        return ByteBuffer.allocateDirect(floats * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }
}
