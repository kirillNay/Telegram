package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.ActionBar.Theme.hasGradientService;

public class QuickShareViewer {

    private QuickShareLayout quickShareSelector;

    private final List<Dialog> dialogs = new ArrayList<>(5);

    private final RectF finalShareSelectorRect = new RectF();

    private final static int SELECTOR_WIDTH_PX = dp(252);

    private final static int SELECTOR_HEIGHT_PX = dp(56);

    private final static int SELECTOR_BUTTON_PADDING_PX = dp(16);

    private View underlayView;

    private ViewGroup parent;

    private Theme.ResourcesProvider resourcesProvider;

    private final RectF initialButtonRect = new RectF();

    private final RectF currentButtonRect = new RectF();

    private int shareButtonCenterX;

    private int shareButtonCenterY;

    private @Direction int direction;

    @IntDef({UP, DOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Direction {
    }

    public static final int UP = 0;
    public static final int DOWN = 1;

    private final ValueAnimator buttonInAnimator = ValueAnimator.ofFloat(0F, 1F, 0F);

    private final ValueAnimator animator = ValueAnimator.ofFloat(0F, 1F);
    private final ValueAnimator animatorF = ValueAnimator.ofFloat(0F, 1F);

    private final ValueAnimator avatarsAnimator = ValueAnimator.ofFloat(0F, 1F);

    private final ValueAnimator hideAnimator = ValueAnimator.ofFloat(1F, 0F);

    private final AnimatorSet animatorSet = new AnimatorSet();

    private Runnable onDetach;

    public QuickShareViewer() {
        fetchDialogs();

        animatorSet.playTogether(animator, animatorF, buttonInAnimator, avatarsAnimator);
        animatorSet.setDuration(1_100L);

        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK);
        animatorF.setInterpolator(new CubicBezierInterpolator(.15, 1.9, .5, 1.4));
        buttonInAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK);
        avatarsAnimator.setInterpolator(new CubicBezierInterpolator(.34, 1.2, .5, 1));

        animator.addUpdateListener((animation) -> invalidate());

        hideAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        hideAnimator.setDuration(200);
        hideAnimator.addUpdateListener((animation) -> {
            quickShareSelector.setAlpha((float) animation.getAnimatedValue());
        });
        hideAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                detach();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                detach();
            }
        });
    }

    public void show(
            @NonNull ViewGroup parent,
            @NonNull Theme.ResourcesProvider resourcesProvider,
            int shareButtonCenterX,
            int shareButtonCenterY,
            int offsetY
    ) {
        this.parent = parent;
        this.resourcesProvider = resourcesProvider;
        this.shareButtonCenterX = shareButtonCenterX;
        this.shareButtonCenterY = shareButtonCenterY;

        quickShareSelector = new QuickShareLayout(parent.getContext());

        int[] loc = new int[2];
        parent.getLocationInWindow(loc);

        WindowManager windowManager = (WindowManager) parent.getContext().getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = parent.getHeight() - offsetY;
        windowLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;

        windowLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

        AndroidUtilities.setPreferredMaxRefreshRate(windowManager, quickShareSelector, windowLayoutParams);
        windowManager.addView(quickShareSelector, windowLayoutParams);

        underlayView = new UnderlayView(parent.getContext());
        parent.addView(underlayView);

        animatorSet.start();
    }

    public void doOnDetach(Runnable onDetach) {
        this.onDetach = onDetach;
    }

    public void hide(boolean forced) {
        if (onDetach != null) {
            onDetach.run();
        }

        animatorSet.cancel();
        if (forced) {
            detach();
        } else {
            underlayView.setVisibility(View.GONE);
            hideAnimator.start();
        }
    }

    private void detach() {
        for(int i = 0; i < dialogs.size(); i++) {
            dialogs.get(i).release();
        }

        if (parent != null && underlayView != null) {
            parent.removeView(underlayView);
        }

        if (quickShareSelector != null) {
            quickShareSelector.removeAllViews();
            WindowManager windowManager = (WindowManager) quickShareSelector.getContext().getSystemService(Context.WINDOW_SERVICE);
            windowManager.removeView(quickShareSelector);
        }

        quickShareSelector = null;
        underlayView = null;
        parent = null;
    }

    private void invalidate() {
        if (parent == null || quickShareSelector == null || underlayView == null) {
            return;
        }

        quickShareSelector.invalidate();
        underlayView.invalidate();
    }

    private void fetchDialogs() {
        ArrayList<TLRPC.Dialog> allDialogs = MessagesController.getInstance(UserConfig.selectedAccount).getAllDialogs();
        Dialog parentDialog = null;
        Iterator<TLRPC.Dialog> iterator = allDialogs.iterator();
        while(iterator.hasNext() && dialogs.size() < 5) {
            TLRPC.Dialog dialog = iterator.next();

            TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-dialog.id);
            if (
                    !DialogObject.isEncryptedDialog(dialog.id) && dialog.folder_id != 1 && (
                            (DialogObject.isUserDialog(dialog.id) ||
                                    !(chat == null || ChatObject.isNotInChat(chat) || chat.gigagroup && !ChatObject.hasAdminRights(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup))
                    )
            ) {
                Dialog d = new Dialog(
                        dialog,
                        (int) Math.ceil(dialogs.size() / 2f),
                        parentDialog != null ? parentDialog.animator : avatarsAnimator,
                        dialogs.isEmpty()
                );
                dialogs.add(d);
                if (parentDialog == null || parentDialog.row != d.row) {
                    parentDialog = d;
                }
            }
        }
    }

    private static class Dialog {

        TLRPC.Dialog dialog;

        int row;

        AvatarDrawable drawable = new AvatarDrawable();

        ValueAnimator parentAnimator;

        ValueAnimator animator = ValueAnimator.ofFloat(0F, 1F);

        private boolean isStarted = false;

        Dialog(TLRPC.Dialog dialog, int row, ValueAnimator parentAnimator, boolean initial) {
            this.dialog = dialog;
            this.row = row;

            if (DialogObject.isUserDialog(dialog.id)) {
                TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialog.id);
                drawable.setInfo(UserConfig.selectedAccount, user);
                if (UserObject.isUserSelf(user)) {
                    drawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                } else {
                    drawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SHARES);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-dialog.id);
                drawable.setInfo(UserConfig.selectedAccount, chat);
            }

            if (initial) {
                animator = parentAnimator;
            } else {
                this.parentAnimator = parentAnimator;

                animator.setDuration(parentAnimator.getDuration() - parentAnimator.getCurrentPlayTime());
                animator.setInterpolator(parentAnimator.getInterpolator());

                parentAnimator.addUpdateListener((animation) -> {
                    if ((float) animation.getAnimatedValue() >= 0.7 && !isStarted) {
                        isStarted = true;
                        animator.start();
                    }
                });
            }
        }

        void release() {
            animator.cancel();
            isStarted = false;

            animator = ValueAnimator.ofFloat(0F, 1F);
        }

    }

    private class UnderlayView extends View {

        UnderlayView(@NonNull Context context) {
            super(context);
            setLayoutParams(new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);

            if (changed) {
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
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            drawButton(canvas);
        }

        private void drawButton(Canvas canvas) {
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

            canvas.drawRoundRect(currentButtonRect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), getThemedPaint(Theme.key_paint_chatActionBackground));

            if (hasGradientService()) {
                canvas.drawRoundRect(currentButtonRect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), Theme.chat_actionBackgroundGradientDarkenPaint);
            }

            final int scx = ((int) currentButtonRect.left + AndroidUtilities.dp(16)), scy = ((int) currentButtonRect.top + AndroidUtilities.dp(16));
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
                    currentButtonRect.centerX(),
                    currentButtonRect.centerY()
            );
            drawable.draw(canvas);
            canvas.restore();
        }

        private Paint getThemedPaint(String paintKey) {
            Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
            return paint != null ? paint : Theme.getThemePaint(paintKey);
        }
    }

    private class QuickShareLayout extends FrameLayout {

        private final GLSurfaceView shareSelectorView;

        private final View overlayView;

        private float alpha = 1F;

        public QuickShareLayout(@NonNull Context context) {
            super(context);

            setLayoutParams(new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            shareSelectorView = new GLSurfaceView(context);
            shareSelectorView.setLayoutParams(new LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            shareSelectorView.setEGLContextClientVersion(2);
            shareSelectorView.setZOrderOnTop(false);
            shareSelectorView.setZOrderMediaOverlay(false);
            shareSelectorView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            shareSelectorView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
            shareSelectorView.setRenderer(new QuickShareLayout.ShareBalloonRenderer(getContext()));
            shareSelectorView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

            overlayView = new View(context) {

                @Override
                protected void onDraw(@NonNull Canvas canvas) {
                    super.onDraw(canvas);
                    drawOverlays(canvas);
                }
            };
            overlayView.setLayoutParams(new LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            addView(shareSelectorView);
            addView(overlayView);
        }

        @Override
        public void setAlpha(float alpha) {
            this.alpha = alpha;

            overlayView.setAlpha(alpha);
            shareSelectorView.requestRender();
        }

        @Override
        public void invalidate() {
            super.invalidate();
            shareSelectorView.requestRender();
            overlayView.invalidate();
        }

        private void drawOverlays(Canvas canvas) {
            drawAvatars(canvas);
        }

        private void drawAvatars(Canvas canvas) {
            for (int i = 0; i < dialogs.size(); i++) {
                int k = (i % 2 == 0 ? -1 : 1);
                float targetCenterX = finalShareSelectorRect.centerX() + k * (lerp(dp(16), finalShareSelectorRect.width() - dp(64), (float) animator.getAnimatedValue()) / (
                        dialogs.size() - 1)) * (dialogs.get(i).row);
                float actualCenterX = lerp(initialButtonRect.centerX(), targetCenterX, (float) animator.getAnimatedValue());

                float targetCenterY = finalShareSelectorRect.centerY();
                float actualCenterY = lerp(initialButtonRect.centerY(), targetCenterY, (float) animatorF.getAnimatedValue());

                float radius = lerp(0, dp(16), (float) dialogs.get(i).animator.getAnimatedValue());
                AvatarDrawable drawable = dialogs.get(i).drawable;
                drawable.setBounds((int) (actualCenterX - radius), (int) (actualCenterY - radius), (int) (actualCenterX + radius), (int) (actualCenterY + radius));
                drawable.draw(canvas);
            }
        }

        public boolean isVisible() {
            return getParent() != null;
        }

        private class ShareBalloonRenderer implements GLSurfaceView.Renderer {

            private final String VERTEX_SHADER = "attribute vec4 vPosition;\n"
                    + "void main() {\n"
                    + "    gl_Position = vPosition;\n"
                    + "}";

            private final String FRAGMENT_SHADER = "precision mediump float;\n"
                    + "uniform vec3 u_Resolution;\n"
                    + "uniform float u_Progress;\n"
                    + "uniform float u_ProgressF;\n"
                    + "uniform float u_alpha;\n"
                    + "\n"
                    + "// Initial button position\n"
                    + "uniform vec2 u_ShareButtonInitCord;\n"
                    + "uniform float u_ShareButtonRadius;\n"
                    + "\n"
                    + "uniform vec2 u_ShareButtonCurrentCord;\n"
                    + "\n"
                    + "// Quick share selector\n"
                    + "uniform vec2 u_SelectorFinalCenterCord;\n"
                    + "uniform vec2 u_SelectorFinalSize; // x - width; y - height\n"
                    + "uniform vec3 u_selectorColor; // alpha is 1 by default\n"
                    + "uniform vec4 u_shadowColor; // selector shadow\n"
                    + "uniform float u_shadowSize;\n"
                    + "\n"
                    + "float circleWithBlur(vec2 uv, vec2 position, float radius, float blur) {\n"
                    + "    float dist = distance(position, uv);\n"
                    + "    dist = smoothstep(dist - blur, dist, radius);\n"
                    + "    return dist * dist * dist * dist * dist;\n"
                    + "}\n"
                    + "\n"
                    + "float roundedRectangleWithBlur(vec2 uv, vec2 pos, vec2 size, float radius, float blur) {\n"
                    + "    vec2 hSize = size / 2.0 - radius;\n"
                    + "    float d = length(max(abs(uv - pos), hSize) - hSize);\n"
                    + "    float r = smoothstep(-radius - blur, -radius + blur, -d);\n"
                    + "    return r;\n"
                    + "}\n"
                    + "\n"
                    + "vec2 normalizeVec2(vec2 coord) {\n"
                    + "    return 2.0 * (coord - 0.5 * u_Resolution.xy) / u_Resolution.y * vec2(1., -1.);\n"
                    + "}\n"
                    + "\n"
                    + "float interpolateLinear(float start, float end, float factor) {\n"
                    + "    return start + factor * (end - start);\n"
                    + "}\n"
                    + "\n"
                    + "float normalizeX(float x) {\n"
                    + "    return 2.0 * x / u_Resolution.x - 1.0;\n"
                    + "}\n"
                    + "\n"
                    + "float normalizeY(float y) {\n"
                    + "    return 1.0 - 2.0 * y / u_Resolution.y;\n"
                    + "}\n"
                    + "\n"
                    + "void main() {\n"
                    + "    vec2 uv = 2.0 * (gl_FragCoord.xy - 0.5 * u_Resolution.xy) / u_Resolution.y; // normalized fragment coordinate\n"
                    + "    vec2 shareButtonInitCordNorm = normalizeVec2(u_ShareButtonInitCord.xy); // normalized share button coordinate\n"
                    + "    vec2 shareButtonCurrentCordNorm = normalizeVec2(u_ShareButtonCurrentCord.xy);\n"
                    + "    float shareButtonRadiusNorm = u_ShareButtonRadius * 2. / u_Resolution.y;\n"
                    + "    float shadowSize = u_shadowSize * 2. / u_Resolution.y;\n"
                    + "\n"
                    + "    vec2 selectorFinalCordNorm = normalizeVec2(u_SelectorFinalCenterCord);\n"
                    + "\n"
                    + "    float aspectRatio = u_Resolution.y / u_Resolution.x;\n"
                    + "    // TODO come with better solution\n"
                    + "    vec2 selectorFinalSizeNorm = vec2(u_SelectorFinalSize.x / u_Resolution.x * 1.5, u_SelectorFinalSize.y / u_Resolution.y * 3.);\n"
                    + "\n"
                    + "    vec2 rect = vec2(interpolateLinear(shareButtonInitCordNorm.x, selectorFinalCordNorm.x, u_Progress), interpolateLinear(shareButtonInitCordNorm.y, selectorFinalCordNorm.y, u_ProgressF));\n"
                    + "    float rectWidth = interpolateLinear(shareButtonRadiusNorm, selectorFinalSizeNorm.x, u_Progress) / 1.5;\n"
                    + "    float rectHeight = interpolateLinear(shareButtonRadiusNorm, selectorFinalSizeNorm.y, min(1.0, u_ProgressF * 2.)) / 1.5;\n"
                    + "    vec2 rectSize = vec2(rectWidth, rectHeight);\n"
                    + "    float rectRadius = rectHeight * .5;\n"
                    + "\n"
                    + "    float blur = interpolateLinear(0.1, 0., min(u_Progress, 1.));\n"
                    + "    float circleIntensity = circleWithBlur(uv, shareButtonCurrentCordNorm, shareButtonRadiusNorm, blur);\n"
                    + "    float rectIntecity = roundedRectangleWithBlur(uv, rect, rectSize, rectRadius, blur);\n"
                    + "\n"
                    + "    float pixelIntensity = smoothstep(0.99, 1.0, rectIntecity + circleIntensity);\n"
                    + "\n"
                    + "    if (pixelIntensity >= 0.99) {\n"
                    + "        // TODO improve shadow\n"
                    + "//        float rectShadowIntecity = 1. - roundedRectangleWithBlur(uv, rect, rectSize, rectRadius, shadowSize);\n"
                    + "//        vec4 color = mix(u_shadowColor * vec4(1.0, 1.0, 1.0, rectShadowIntecity * u_alpha), vec4(1.0, 1.0, 1.0, rectIntecity * u_alpha), rectShadowIntecity);\n"
                    + "//        gl_FragColor = color;\n"
                    + "\n"
                    + "        gl_FragColor = vec4(1.0, 1.0, 1.0, rectIntecity * u_alpha);\n"
                    + "    } else {\n"
                    + "       vec4(1.0, 1.0, 1.0, 0.0);\n"
                    + "    }\n"
                    + "\n"
                    + "}";

            private int program;

            private int uResolutionLocation;
            private int uShareButtonInitCordLocation;
            private int uShareButtonCurrentCordLocation;
            private int uShareButtonRadiusLocation;
            private int uSelectorFinalCenterCordLocation;
            private int uSelectorFinalSizeLocation;

            private int uSelectorColorLocation;
            private int uSelectorShadowColorLocation;
            private int uSelectorShadowSizeLocation;

            private int uProgressLocation;
            private int uProgressFLocation;

            private int uAlphaLocation;

            private int screenWidth;
            private int screenHeight;

            private final int selectorColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);

            private final int shadowColor = Theme.getColor(Theme.key_windowBackgroundGrayShadow);

            private final Context context;

            ShareBalloonRenderer(Context context) {
                super();
                this.context = context;
            }

            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

                int vertexShaderId = createShader(GL_VERTEX_SHADER, VERTEX_SHADER);
                int fragmentShaderId = createShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
                program = createProgram(vertexShaderId, fragmentShaderId);

                uResolutionLocation = glGetUniformLocation(program, "u_Resolution");
                uProgressLocation = glGetUniformLocation(program, "u_Progress");
                uShareButtonInitCordLocation = glGetUniformLocation(program, "u_ShareButtonInitCord");
                uShareButtonCurrentCordLocation = glGetUniformLocation(program, "u_ShareButtonCurrentCord");
                uShareButtonRadiusLocation = glGetUniformLocation(program, "u_ShareButtonRadius");
                uSelectorFinalCenterCordLocation = glGetUniformLocation(program, "u_SelectorFinalCenterCord");
                uSelectorFinalSizeLocation = glGetUniformLocation(program, "u_SelectorFinalSize");
                uProgressFLocation = glGetUniformLocation(program, "u_ProgressF");
                uAlphaLocation = glGetUniformLocation(program, "u_alpha");
                uSelectorColorLocation = glGetUniformLocation(program, "u_selectorColor");
                uSelectorShadowColorLocation = glGetUniformLocation(program, "u_shadowColor");
                uSelectorShadowSizeLocation = glGetUniformLocation(program, "u_shadowSize");
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
                GLES20.glUniform1f(uAlphaLocation, alpha);

                GLES20.glUniform3f(uSelectorColorLocation, Color.red(selectorColor), Color.green(selectorColor), Color.blue(selectorColor));
                GLES20.glUniform4f(uSelectorShadowColorLocation, Color.alpha(shadowColor), Color.red(shadowColor), Color.green(shadowColor), Color.blue(shadowColor));
                GLES20.glUniform1f(uSelectorShadowSizeLocation, dp(2));
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
                    glDeleteShader(shaderId);
                    FileLog.e("Shader compilation failure. Reason: " + glGetShaderInfoLog(shaderId));
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
                    glDeleteProgram(programId);
                    FileLog.e("Shader compilation failure. Reason: " + glGetShaderInfoLog(programId));
                    return 0;
                }
                return programId;
            }
        }

    }

}
