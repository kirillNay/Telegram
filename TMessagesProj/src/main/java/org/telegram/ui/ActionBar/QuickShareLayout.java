package org.telegram.ui.ActionBar;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES10.glViewport;
import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_COMPILE_STATUS;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_LINK_STATUS;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static android.opengl.GLES20.GL_SRC_ALPHA;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static android.opengl.GLES20.glAttachShader;
import static android.opengl.GLES20.glBlendFunc;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glCompileShader;
import static android.opengl.GLES20.glCreateProgram;
import static android.opengl.GLES20.glCreateShader;
import static android.opengl.GLES20.glDeleteProgram;
import static android.opengl.GLES20.glDeleteShader;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glGetProgramiv;
import static android.opengl.GLES20.glGetShaderInfoLog;
import static android.opengl.GLES20.glGetShaderiv;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glLinkProgram;
import static android.opengl.GLES20.glShaderSource;
import static android.opengl.GLES20.glUseProgram;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.ActionBar.Theme.hasGradientService;

public class QuickShareLayout extends FrameLayout {

    private final GLSurfaceView balloonView;

    private Runnable onHide;

    private final RectF shareButtonRect = new RectF(0F, 0F, 32F, 32F);

    private final Theme.ResourcesProvider resourcesProvider;

    private @Direction int direction;

    @IntDef({UP, DOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Direction {}
    public static final int UP = 0;
    public static final int DOWN = 1;

    public QuickShareLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setLayoutParams(new ViewGroup.LayoutParams(dp(280), dp(120)));
        setBackgroundColor(Color.argb(20, 255, 0, 0));
        setVisibility(View.GONE);

        balloonView = new GLSurfaceView(context);
        balloonView.setLayoutParams(new LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        balloonView.setBackgroundColor(Color.argb(0, 0, 0, 0));
        balloonView.setEGLContextClientVersion(2);
        balloonView.setZOrderOnTop(true);
        balloonView.getHolder().setFormat(PixelFormat.TRANSPARENT);
        balloonView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        balloonView.setRenderer(new QuickShareLayout.ShareBalloonRenderer(getContext()));
        //balloonView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        addView(balloonView);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        drawButton(canvas, shareButtonRect);
    }

    // Сюда просто передаю rect, где будет находится button в текущем canvas'е
    private void drawButton(Canvas canvas, RectF rect) {
        canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), getThemedPaint(Theme.key_paint_chatActionBackground));

        if (hasGradientService()) {
            canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), Theme.chat_actionBackgroundGradientDarkenPaint);
        }

        final int scx = ((int) rect.left + AndroidUtilities.dp(16)), scy = ((int) rect.top + AndroidUtilities.dp(16));
        Drawable drawable = Theme.getThemeDrawable(Theme.key_drawable_shareIcon);
        final int shw = drawable.getIntrinsicWidth() / 2, shh = drawable.getIntrinsicHeight() / 2;
        drawable.setBounds(scx - shw, scy - shh, scx + shw, scy + shh);
        drawable.draw(canvas);
    }

    private Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    public void onHide(Runnable onHide) {
        this.onHide = onHide;
    }

    private int shareButtonCenterX;

    public void show(int shareButtonCenterX, @Direction int direction) {
        this.direction = direction;
        if (direction == UP) {
            shareButtonRect.set(shareButtonCenterX - dp(16), getHeight() - dp(32), shareButtonCenterX + dp(16), getHeight());
        } else {
            shareButtonRect.set(shareButtonCenterX - dp(16), 0, shareButtonCenterX + dp(16), dp(32));
        }
        this.shareButtonCenterX = shareButtonCenterX;

        setVisibility(View.VISIBLE);
    }

    public void hide() {
        setVisibility(View.GONE);
        if (onHide != null) {
            onHide.run();
        }
    }

    private class ShareBalloonRenderer implements GLSurfaceView.Renderer {

        private int program;

        private int uResolutionLocation;
        private int uTimeLocation;
        private int uShareButtonInitCordLocation;
        private int uShareButtonCurrentCordLocation;
        private int uShareButtonRadiusLocation;

        private int screenWidth;
        private int screenHeight;

        private final Context context;

        ShareBalloonRenderer(Context context){
            super();
            this.context = context;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            count = 0;

            int vertexShaderId = createShader(GL_VERTEX_SHADER, R.raw.aa);
            int fragmentShaderId = createShader(GL_FRAGMENT_SHADER, R.raw.aaa);
            program = createProgram(vertexShaderId, fragmentShaderId);

            uResolutionLocation = glGetUniformLocation(program, "u_Resolution");
            uTimeLocation = glGetUniformLocation(program, "u_Time");
            uShareButtonInitCordLocation = glGetUniformLocation(program, "u_ShareButtonInitCord");
            uShareButtonCurrentCordLocation = glGetUniformLocation(program, "u_ShareButtonCurrentCord");
            uShareButtonRadiusLocation = glGetUniformLocation(program, "u_ShareButtonRadius");
        }

        private int count = 0;

        private void setUniforms() {
            GLES20.glUniform3f(uResolutionLocation, (float) screenWidth, (float) screenHeight, 1.0f);
            GLES20.glUniform1f(uTimeLocation, (count++) / 100F);
            GLES20.glUniform2f(uShareButtonInitCordLocation, shareButtonRect.left + shareButtonRect.width() / 2F, shareButtonRect.top + shareButtonRect.height() / 2f);
            GLES20.glUniform2f(uShareButtonCurrentCordLocation, shareButtonRect.left + shareButtonRect.width() / 2F, shareButtonRect.top + shareButtonRect.height() / 2f);
            GLES20.glUniform1f(uShareButtonRadiusLocation, (float) dp(32));
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            glViewport(0, 0, width, height);

            screenWidth = width;
            screenHeight = height;
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            glClear(GL_COLOR_BUFFER_BIT);

            glUseProgram(program);
            setUniforms();

            drawRectangle();

            glUseProgram(0);
        }

        private void drawRectangle() {
            float[] vertices = {
                    -1f, -1f, // нижний левый
                    1f, -1f, // нижний правый
                    -1f,  1f, // верхний левый
                    1f,  1f  // верхний правый
            };

            FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(vertices);
            vertexBuffer.position(0);

            int positionLocation = GLES20.glGetAttribLocation(program, "vPosition");
            GLES20.glEnableVertexAttribArray(positionLocation);
            GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(positionLocation);

            // Отвязываем буфер вершин
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }

        // TODO не забудь удалить, будем заранее иметь String
        private int createShader(int type, int shaderRawId) {
            String shaderText = readTextFromRaw(shaderRawId);
            return createShader(type, shaderText);
        }

        private String readTextFromRaw(int resourceId) {
            StringBuilder stringBuilder = new StringBuilder();
            try {
                BufferedReader bufferedReader = null;
                try {
                    InputStream inputStream = context.getResources().openRawResource(resourceId);
                    bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                        stringBuilder.append("\r\n");
                    }
                } finally {
                    if (bufferedReader != null) {
                        bufferedReader.close();
                    }
                }
            } catch (IOException | Resources.NotFoundException ex) {
                ex.printStackTrace();
            }
            return stringBuilder.toString();
        }

        private int createShader(int type, String shaderText) {
            final int shaderId = glCreateShader(type);
            if (shaderId == 0) {
                return 0;
            }
            glShaderSource(shaderId, shaderText);
            glCompileShader(shaderId);
            final int[] compileStatus = new int[1];
            glGetShaderiv(shaderId, GL_COMPILE_STATUS, compileStatus, 0);
            if (compileStatus[0] == 0) {
                Log.e("kirillNay", "Shader compilation failure. Reason: " + glGetShaderInfoLog(shaderId));
                glDeleteShader(shaderId);
                return 0;
            }
            return shaderId;
        }

        private int createProgram(int vertexShaderId, int fragmentShaderId) {
            final int programId = glCreateProgram();
            if (programId == 0) {
                return 0;
            }
            glAttachShader(programId, vertexShaderId);
            glAttachShader(programId, fragmentShaderId);
            glLinkProgram(programId);
            final int[] linkStatus = new int[1];
            glGetProgramiv(programId, GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                Log.e("kirillNay", "Shader compilation failure. Reason: " + glGetShaderInfoLog(programId));
                glDeleteProgram(programId);
                return 0;
            }
            return programId;
        }
    }

}
