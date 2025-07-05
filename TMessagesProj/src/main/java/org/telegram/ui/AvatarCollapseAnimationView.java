package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLES31;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import org.telegram.DebugUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;

import javax.microedition.khronos.opengles.GL10;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_COMPILE_STATUS;
import static android.opengl.GLES20.GL_LINK_STATUS;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glUseProgram;

@SuppressLint("ViewConstructor")
public class AvatarCollapseAnimationView extends TextureView implements TextureView.SurfaceTextureListener {

    private static final class RenderThread extends DispatchQueue {

        private final SurfaceTexture surfaceTexture;
        private final ImageReceiver imageReceiver;
        private final FrameLayout avatarContainer;

        // EGL
        private EGLContext eglContext;
        private EGLDisplay eglDisplay;
        private EGLSurface eglSurface;

        private FloatBuffer vertexBuffer;
        private ShortBuffer indexBuffer;
        private final float[] vertexData = {
                -1f,  1f,   0f, 0f,
                -1f, -1f,   0f, 1f,
                1f, -1f,   1f, 1f,
                1f,  1f,   1f, 0f
        };
        private final short[] indices = new short[] {
            0, 1, 2,
            0, 2, 3
        };
        private int eglProgram;
        private int avatarTexture;

        private int width;
        private int height;

        private final float[] avatarModelMatrix = new float[16];

        // Uniforms
        private int uResolutionLoc;
        private int uAvatarRadiusLoc;
        private int uAvatarTextureLoc;
        private int uAvatarModelLoc;

        // Attributes
        private int aTexCordsLoc;
        private int aPositionLoc;

        // Render state
        private volatile boolean isInitialized = false;
        private volatile boolean isReleased = false;
        private volatile boolean isRunning = false;

        private volatile boolean invalid = true;

        private RenderThread(
                SurfaceTexture surfaceTexture,
                ImageReceiver imageReceiver,
                FrameLayout avatarContainer,
                int width,
                int height
        ) {
            super("AvatarCollapseAnimation thread", false);
            this.surfaceTexture = surfaceTexture;
            this.width = width;
            this.height = height;
            this.imageReceiver = imageReceiver;
            this.avatarContainer = avatarContainer;
        }

        public void setSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public void run() {
            try {
                initGLContext();
                initImageTexture();
                isInitialized = true;
            } catch (Exception e) {
                DebugUtils.error(e);
                release();
            }
            super.run();
        }

        private void startRendering() {
            postRunnable(() -> {
                final long maxdt = (long) (1000L / Math.max(30, AndroidUtilities.screenRefreshRate));
                while(isRunning && !isReleased) {
                    long start = System.currentTimeMillis();
                    dispatchDraw();
                    long dt = System.currentTimeMillis() - start;
                    if (dt < maxdt - 1) {
                        try {
                            Thread.sleep(maxdt - 1 - dt);
                        } catch (Exception ignore) {}
                    }
                }
            });
        }

        private void invalidate() {
            invalid = true;
        }

        private void initGLContext() throws InternalRenderException {
            // Preparing OpenGL scene
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw new InternalRenderException("EglDisplay does not exists");
            }

            int[] version = new int[2];
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw new InternalRenderException("Unable to initialize EGL14");
            }

            int[] configAttrs = {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 16,
                    EGL14.EGL_STENCIL_SIZE, 8,
                    EGL14.EGL_NONE
            };

            EGLConfig[] eglConfig = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(eglDisplay, configAttrs, 0, eglConfig, 0, 1, numConfigs, 0)) {
                throw new InternalRenderException("Unable to choose EGL14 config");
            }

            int[] eglContextAttrs = new int[] {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
            };
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig[0], EGL14.EGL_NO_CONTEXT, eglContextAttrs, 0);
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                throw new InternalRenderException("Unable to create EGL14 context");
            }

            int[] surfaceAttrs = new int[] {
                EGL14.EGL_NONE
            };
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig[0], surfaceTexture, surfaceAttrs, 0);
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw new InternalRenderException("Unable to create EGL14 surface");
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)){
                throw new InternalRenderException("Unable to bind EGL14 surface");
            }

            // Compiling shaders
            int vertexShader = createShader(GLES31.GL_VERTEX_SHADER, AndroidUtilities.readRes(R.raw.avatar_collapse_vertex));
            int fragmentShader = createShader(GLES31.GL_FRAGMENT_SHADER, AndroidUtilities.readRes(R.raw.avatar_collapse_fragment));
            eglProgram = createProgram(vertexShader, fragmentShader);

            // Linking uniform vars
            uResolutionLoc = GLES20.glGetUniformLocation(eglProgram, "u_Resolution");
            uAvatarRadiusLoc = GLES20.glGetUniformLocation(eglProgram, "u_AvatarRadius");
            uAvatarTextureLoc = GLES20.glGetUniformLocation(eglProgram, "u_Texture");
            uAvatarModelLoc = GLES20.glGetUniformLocation(eglProgram, "u_Model");

            vertexBuffer = ByteBuffer
                    .allocateDirect(vertexData.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(vertexData);

            indexBuffer = ByteBuffer
                    .allocateDirect(indices.length * 2)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
                    .put(indices);

            indexBuffer.position(0);
            vertexBuffer.position(0);

            // Linking attributes
            vertexBuffer.position(0);
            aPositionLoc = GLES20.glGetAttribLocation(eglProgram, "a_Position");
            GLES20.glEnableVertexAttribArray(aPositionLoc);
            GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, vertexBuffer);

            aTexCordsLoc = GLES20.glGetAttribLocation(eglProgram, "a_TexCoord");
            GLES20.glEnableVertexAttribArray(aTexCordsLoc);
            vertexBuffer.position(2);
            GLES20.glVertexAttribPointer(
                    aTexCordsLoc, 2, GLES20.GL_FLOAT, false,
                    4 * 4, vertexBuffer
            );

            GLES31.glViewport(0, 0, width, height);
            GLES31.glEnable(GLES31.GL_BLEND);
            GLES31.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES31.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            GLES31.glUseProgram(eglProgram);
        }

        private void initImageTexture() throws InternalRenderException {
            Bitmap avatarBitmap = imageReceiver.getBitmap();
            if (avatarBitmap != null) {
                try {
                    int[] textures = new int[1];
                    GLES20.glGenTextures(1, textures, 0);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
                    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, avatarBitmap, 0);
                    GLES20.glBindTexture(GL10.GL_TEXTURE_2D, 0);
                    avatarTexture = textures[0];
                } catch (Exception e) {
                    throw new InternalRenderException("Failed to create avatar bitmap texture", e);
                }

            } else {
                throw new InternalRenderException("There is no avatar bitmap");
            }
        }

        private int createShader(int type, String shaderText) throws InternalRenderException {
            final int shaderId = GLES20.glCreateShader(type);
            if (shaderId == 0) {
                throw new InternalRenderException("Unable to create shader");
            }
            GLES20.glShaderSource(shaderId, shaderText);
            GLES20.glCompileShader(shaderId);
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shaderId, GL_COMPILE_STATUS, compileStatus, 0);
            if (compileStatus[0] == 0) {
                String failReason = GLES20.glGetShaderInfoLog(shaderId);
                GLES20.glDeleteShader(shaderId);
                throw new InternalRenderException("Shader compilation failure. Reason: " + failReason);
            }
            return shaderId;
        }

        private int createProgram(int vertexShaderId, int fragmentShaderId) throws InternalRenderException {
            final int programId = GLES20.glCreateProgram();
            if (programId == 0) {
                throw new InternalRenderException("Unable to create program");
            }
            GLES20.glAttachShader(programId, vertexShaderId);
            GLES20.glAttachShader(programId, fragmentShaderId);
            GLES20.glLinkProgram(programId);
            final int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(programId, GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(programId);
                throw new InternalRenderException("Shader compilation failure. Reason: " + GLES20.glGetShaderInfoLog(programId));
            }
            return programId;
        }

        private void dispatchDraw() {
            if (!isInitialized) return;
            if (!invalid) return;

            if (isReleased) throw new IllegalStateException("Render thread can't draw after release");

            GLES20.glClearColor(0f, 0f, 0f, 0f);
            glClear(GL_COLOR_BUFFER_BIT);
            glUseProgram(eglProgram);
            setUniforms();
            draw();

            glUseProgram(0);
        }

        private final float[] avatarCords = new float[2];
        private void setUniforms() {
            float textureScaleY = (avatarContainer.getHeight() * avatarContainer.getScaleY()) / (float) height;
            float textureScaleX = (avatarContainer.getWidth() * avatarContainer.getScaleX()) / (float) width;
            float textureWidth = height * textureScaleY;

            GLES20.glUniform2f(uResolutionLoc, width, height);

            GLES20.glUniform1f(uAvatarRadiusLoc, (avatarContainer.getHeight() * avatarContainer.getScaleY() / 2f) / textureWidth);

            Matrix.setIdentityM(avatarModelMatrix, 0);

            avatarCords[0] = avatarContainer.getX() + avatarContainer.getWidth() / 2f;
            avatarCords[1] = avatarContainer.getY() + avatarContainer.getHeight() / 2f * avatarContainer.getScaleY();
            normalizeCords(avatarCords, width, height);

            Matrix.translateM(avatarModelMatrix, 0, avatarCords[0], avatarCords[1], 0.0f);
            Matrix.scaleM(avatarModelMatrix, 0, textureScaleX, textureScaleY, 1.0f);

            GLES20.glUniformMatrix4fv(uAvatarModelLoc, 1, false, avatarModelMatrix, 0);
        }

        private void draw() {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, avatarTexture);
            GLES20.glUniform1i(uAvatarTextureLoc, 0);

            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, indexBuffer);

            try {
                EGL14.eglSwapBuffers(eglDisplay, eglSurface);
            } catch (Exception e) {
                DebugUtils.error(e);
                release();
            }
        }

        private void release() {
            isRunning = false;

            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                GLES20.glDisableVertexAttribArray(aTexCordsLoc);
                GLES20.glDisableVertexAttribArray(aPositionLoc);

                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(eglDisplay, eglSurface);
                EGL14.eglDestroyContext(eglDisplay, eglContext);
                EGL14.eglTerminate(eglDisplay);
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY;
            eglContext = EGL14.EGL_NO_CONTEXT;
            eglSurface = EGL14.EGL_NO_SURFACE;

            isReleased = true;
        }

        private static void normalizeCords(float[] cords, int width, int height) {
            cords[0] = (cords[0] / width) * 2f - 1f;
            cords[1] = 1f - (cords[1] / height) * 2f;
        }

        private static class InternalRenderException extends Exception {

            private InternalRenderException(String message) {
                super(message);
            }

            private InternalRenderException(String message, Throwable cause) {
                super(message, cause);
            }

        }
    }

    private RenderThread renderThread;

    private final FrameLayout avatarContainer;

    private final ImageReceiver imageReceiver;

    private float collapseProgress;

    private boolean invalid = true;

    public AvatarCollapseAnimationView(Context context, FrameLayout avatarContainer, ImageReceiver imageReceiver) {
        super(context);
        this.avatarContainer = avatarContainer;
        this.imageReceiver = imageReceiver;
        setSurfaceTextureListener(this);

        setOpaque(false);
    }

    public void setCollapseProgress(float collapseProgress) {
        if (this.collapseProgress == collapseProgress) {
            return;
        }

        this.collapseProgress = collapseProgress;
        if (renderThread != null) {
            renderThread.invalidate();
        }
    }

    @Override
    public void setVisibility(int visibility) {
        if (getVisibility() == visibility) return;

        super.setVisibility(visibility);

        if (visibility == View.VISIBLE && renderThread != null) {
            startRender();
        } else if (renderThread != null) {
            stopRender();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        renderThread = new RenderThread(surface, imageReceiver, avatarContainer, width, height);
        renderThread.start();

        if (getVisibility() == View.VISIBLE) {
            startRender();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        if (renderThread != null) {
            renderThread.setSize(width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        if (renderThread != null) {
            renderThread.release();
        }

        renderThread = null;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        renderThread.release();
        renderThread = null;
    }

    private void startRender() {
        if (renderThread != null) {
            renderThread.isRunning = true;
            renderThread.startRendering();
            renderThread.invalidate();
        }
    }

    private void stopRender() {
        if (renderThread != null) {
            renderThread.isRunning = false;
        }
    }
}
