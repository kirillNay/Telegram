package org.telegram.ui.ActionBar;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
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
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CubicBezierInterpolator;
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
import static org.telegram.messenger.AndroidUtilities.doOnLayout;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.ActionBar.Theme.hasGradientService;

public class QuickShareLayout extends FrameLayout {

    private final GLSurfaceView shareSelectorView;

    private Runnable onDetach;

    private final RectF initialButtonRect = new RectF();

    private final RectF currentButtonRect = new RectF();

    private final RectF finalShareSelectorRect = new RectF();

    private final Theme.ResourcesProvider resourcesProvider;

    private final static int SELECTOR_WIDTH_PX = dp(276);

    private final static int SELECTOR_HEIGHT_PX = dp(56);

    private final static int SELECTOR_BUTTON_PADDING_PX = dp(16);

    private @Direction int direction;

    @IntDef({UP, DOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Direction {
    }

    public static final int UP = 0;
    public static final int DOWN = 1;

    private final ValueAnimator animator = ValueAnimator.ofFloat(0F, 1F);
    private final ValueAnimator animatorF = ValueAnimator.ofFloat(0F, 1F);
    private final ValueAnimator buttonInAnimator = ValueAnimator.ofFloat(0F, 1F, 0F);

    private final AnimatorSet animatorSet = new AnimatorSet();

    public QuickShareLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setLayoutParams(new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setBackgroundColor(Color.argb(0, 0, 0, 0));

        animatorSet.playTogether(animator, animatorF, buttonInAnimator);
        animatorSet.setDuration(1_000L);

        animator.setInterpolator(new CubicBezierInterpolator(.34, 1.2, .5, 1));
        animatorF.setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK);
        buttonInAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK);

        animator.addUpdateListener((animation) -> invalidate());

        shareSelectorView = new GLSurfaceView(context);
        shareSelectorView.setLayoutParams(new LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        shareSelectorView.setEGLContextClientVersion(2);
        shareSelectorView.setZOrderOnTop(true);
        shareSelectorView.getHolder().setFormat(PixelFormat.TRANSPARENT);
        shareSelectorView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        shareSelectorView.setRenderer(new QuickShareLayout.ShareBalloonRenderer(getContext()));
        shareSelectorView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        addView(shareSelectorView);
    }

    @Override
    public void invalidate() {
        shareSelectorView.requestRender();
        super.invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        currentButtonRect.set(
                initialButtonRect.left,
                lerp(
                        initialButtonRect.top,
                        direction == UP ? initialButtonRect.top - dp(16) : initialButtonRect.top + dp(16),
                        (float) buttonInAnimator.getAnimatedValue()
                ),
                initialButtonRect.right,
                lerp(
                        initialButtonRect.bottom,
                        direction == UP ? initialButtonRect.bottom - dp(16) : initialButtonRect.bottom + dp(16),
                        (float) buttonInAnimator.getAnimatedValue()
                )
        );

        drawButton(canvas, currentButtonRect);
    }

    private void drawButton(Canvas canvas, RectF rect) {
        canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), getThemedPaint(Theme.key_paint_chatActionBackground));

        if (hasGradientService()) {
            canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), Theme.chat_actionBackgroundGradientDarkenPaint);
        }

        final int scx = ((int) rect.left + AndroidUtilities.dp(16)), scy = ((int) rect.top + AndroidUtilities.dp(16));
        Drawable drawable = Theme.getThemeDrawable(Theme.key_drawable_shareIcon);
        final int shw = drawable.getIntrinsicWidth() / 2, shh = drawable.getIntrinsicHeight() / 2;
        drawable.setBounds(scx - shw, scy - shh, scx + shw, scy + shh);

        canvas.save();
        canvas.rotate(
                lerp(
                        0F,
                        direction == UP ? -60F : 60F,
                        (float) buttonInAnimator.getAnimatedValue()
                ),
                rect.centerX(),
                rect.centerY()
        );
        drawable.draw(canvas);
        canvas.restore();
    }

    private Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    public void doOnDetach(Runnable onDetach) {
        this.onDetach = onDetach;
    }

    private void setupPositions(int shareButtonCenterX, int shareButtonCenterY) {
        doOnLayout(this, () -> {
            int rightX = Math.min(getWidth() - dp(12), shareButtonCenterX + SELECTOR_WIDTH_PX / 5);
            int leftX = rightX - SELECTOR_WIDTH_PX;
            if (leftX < dp(12)) {
                leftX = dp(12);
                rightX = leftX + SELECTOR_WIDTH_PX;
            }

            int topY, preferableTopY;
            topY = preferableTopY = shareButtonCenterY - dp(16) - SELECTOR_BUTTON_PADDING_PX - SELECTOR_HEIGHT_PX;
            if (preferableTopY >= dp(12)) {
                direction = UP;
            } else {
                direction = DOWN;
                topY = shareButtonCenterY + dp(16) + SELECTOR_BUTTON_PADDING_PX;
            }
            int bottomY = topY + SELECTOR_HEIGHT_PX;

            finalShareSelectorRect.set(leftX, topY, rightX, bottomY);
            initialButtonRect.set(shareButtonCenterX - dp(16), shareButtonCenterY - dp(16), shareButtonCenterX + dp(16), shareButtonCenterY + dp(16));
        });
    }

    private void start() {
        animatorSet.start();
    }

    private void detach() {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            parent.removeView(this);
        }
    }

    public boolean isVisible() {
        return getParent() != null;
    }

    @Override
    protected void onDetachedFromWindow() {
        animatorSet.cancel();

        if (onDetach != null) {
            onDetach.run();
        }

        super.onDetachedFromWindow();
    }

    public static QuickShareLayout showLayout(
            @NonNull ViewGroup parent,
            @NonNull Theme.ResourcesProvider resourcesProvider,
            int shareButtonCenterX,
            int shareButtonCenterY
    ) {
        QuickShareLayout layout = new QuickShareLayout(parent.getContext(), resourcesProvider);
        parent.addView(layout);
        layout.setupPositions(shareButtonCenterX, shareButtonCenterY);
        layout.start();

        return layout;
    }

    public static void detach(QuickShareLayout layout) {
        layout.detach();
    }

    private class ShareBalloonRenderer implements GLSurfaceView.Renderer {

        private int program;

        private int uResolutionLocation;
        private int uShareButtonInitCordLocation;
        private int uShareButtonCurrentCordLocation;
        private int uShareButtonRadiusLocation;
        private int uSelectorFinalCenterCordLocation;
        private int uSelectorFinalSizeLocation;

        private int uProgressLocation;
        private int uProgressFLocation;

        private int screenWidth;
        private int screenHeight;

        private final Context context;

        ShareBalloonRenderer(Context context) {
            super();
            this.context = context;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            int vertexShaderId = createShader(GL_VERTEX_SHADER, R.raw.aa);
            int fragmentShaderId = createShader(GL_FRAGMENT_SHADER, R.raw.aaa);
            program = createProgram(vertexShaderId, fragmentShaderId);

            uResolutionLocation = glGetUniformLocation(program, "u_Resolution");
            uProgressLocation = glGetUniformLocation(program, "u_Progress");
            uShareButtonInitCordLocation = glGetUniformLocation(program, "u_ShareButtonInitCord");
            uShareButtonCurrentCordLocation = glGetUniformLocation(program, "u_ShareButtonCurrentCord");
            uShareButtonRadiusLocation = glGetUniformLocation(program, "u_ShareButtonRadius");
            uSelectorFinalCenterCordLocation = glGetUniformLocation(program, "u_SelectorFinalCenterCord");
            uSelectorFinalSizeLocation = glGetUniformLocation(program, "u_SelectorFinalSize");
            uProgressFLocation = glGetUniformLocation(program, "u_ProgressF");
        }

        private void setUniforms() {
            GLES20.glUniform3f(uResolutionLocation, (float) screenWidth, (float) screenHeight, 1.0f);
            GLES20.glUniform2f(uShareButtonInitCordLocation, initialButtonRect.centerX(), initialButtonRect.centerY());
            GLES20.glUniform2f(uShareButtonCurrentCordLocation, currentButtonRect.centerX(), currentButtonRect.centerY());
            GLES20.glUniform1f(uShareButtonRadiusLocation, (float) dp(16));
            GLES20.glUniform2f(uSelectorFinalCenterCordLocation, finalShareSelectorRect.centerX(), finalShareSelectorRect.centerY());
            GLES20.glUniform2f(uSelectorFinalSizeLocation, (float) SELECTOR_WIDTH_PX, (float) SELECTOR_HEIGHT_PX);
            GLES20.glUniform1f(uProgressLocation, (float) animator.getAnimatedValue());
            GLES20.glUniform1f(uProgressFLocation, (float) animatorF.getAnimatedValue());
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
                    -1f, -1f,
                    1f, -1f,
                    -1f, 1f,
                    1f, 1f
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
