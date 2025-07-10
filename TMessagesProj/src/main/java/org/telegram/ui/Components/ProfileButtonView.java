package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import kotlin.jvm.functions.Function0;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

@SuppressLint("ViewConstructor")
public class ProfileButtonView extends View {

    private final View backgroundSibling;

    private final Theme.ResourcesProvider resourcesProvider;

    private ViewTreeObserver viewTreeObserver;
    private RenderNode blurRenderNode;

    private final Paint textPaint;
    private Drawable drawable;
    private String buttonText;

    private float scale = 1f;
    private boolean backgroundBlur = false;

    private float cornerRadius;

    private Runnable onClickCallback;

    private OnLongPressedCallback onLongPressedCallback;

    private final ViewTreeObserver.OnDrawListener onDrawListener = this::invalidate;

    private final int[] backgroundSiblingLoc = new int[2];
    private final int[] viewLoc = new int[2];

    private final ValueAnimator pressedAnimator;
    private float pressedProgress = 0f;

    public ProfileButtonView(
            Context context,
            View backgroundSibling,
            Theme.ResourcesProvider resourcesProvider,
            int iconRes,
            String buttonText,
            float cornerRadius
    ) {
        super(context);
        this.backgroundSibling = backgroundSibling;
        this.buttonText = buttonText;
        this.resourcesProvider = resourcesProvider;
        setCornerRadius(cornerRadius);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Theme.getColor(Theme.key_profile_title, resourcesProvider));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextAlign(Paint.Align.CENTER);

        drawable = ContextCompat.getDrawable(context, iconRes);
        if (drawable != null) {
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_actionIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
        }

        setScale(1f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        pressedAnimator = ValueAnimator.ofFloat(0f, 1f);
        pressedAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        pressedAnimator.addUpdateListener(animation -> {
            pressedProgress = (float) animation.getAnimatedValue();
            setScaleX(lerp(1f, 0.95f, pressedProgress));
            setScaleY(lerp(1f, 0.95f, pressedProgress));
            invalidate();
        });
    }

    public void setClickListener(Runnable callback) {
        onClickCallback = callback;
    }

    public void setOnLongPressed(OnLongPressedCallback callback) {
        onLongPressedCallback = callback;
    }

    public void setIconRes(int iconRes) {
        drawable = ContextCompat.getDrawable(getContext(), iconRes);
        if (drawable != null) {
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_actionIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
        }
        setScale(1f);
        invalidate();
    }

    public void setText(String text) {
        if (!buttonText.equals(text)) {
            buttonText = text;
            invalidate();
        }
    }

    public void setScale(float scale) {
        this.scale = scale;

        textPaint.setTextSize(lerp(dp(10), dp(6), 1 - scale));
        float iconSize = dp(62) * scale - 2 * dp(8) - textPaint.getTextSize() - dp(8);
        drawable.setBounds(0, 0, (int) (iconSize), (int) (iconSize));
    }

    public void enableBackgroundBlur(boolean enable) {
        backgroundBlur = enable;
    }

    public void setCornerRadius(float radius) {
        if (this.cornerRadius == radius) {
            return;
        }

        cornerRadius = radius;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        addDrawListener();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeDrawListener();
        super.onDetachedFromWindow();
    }

    private boolean isPressed = false;

    private final float[] pressedLoc = new float[2];

    private final Runnable longPressed = () -> {
        if (isPressed && onLongPressedCallback.onLongPressed(pressedLoc[0], pressedLoc[1])) {
            AndroidUtilities.vibrateCursor(this);
            pressed(false);
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && !isPressed) {
            pressed(true);
            postDelayed(longPressed, 300);
            pressedLoc[0] = event.getX();
            pressedLoc[1] = event.getY();

            return true;
        }

        boolean isX = event.getX() >= 0 && event.getX() <= getWidth();
        boolean isY = event.getY() >= 0 && event.getY() <= getHeight();

        if (event.getAction() == MotionEvent.ACTION_UP && isPressed) {
            pressed(false);

            if (isX && isY && onClickCallback != null) {
                onClickCallback.run();
                return true;
            }

            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            pressedLoc[0] = event.getX();
            pressedLoc[1] = event.getY();
            if ((!isX || !isY) && isPressed) {
                pressed(false);
            }

            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_CANCEL && isPressed) {
            pressed(false);
            return false;
        }

        return false;
    }

    private void pressed(boolean isPressed) {
        this.isPressed = isPressed;

        if (pressedAnimator.isRunning()) {
            pressedAnimator.cancel();
        }

        if (isPressed) {
            pressedAnimator.setDuration((long) (120L * (1f - pressedProgress)));
            pressedAnimator.setFloatValues(pressedProgress, 1f);
        } else {
            pressedAnimator.setDuration((long) (120L * pressedProgress));
            pressedAnimator.setFloatValues(pressedProgress, 0f);
        }

        pressedAnimator.start();
    }

    private void addDrawListener() {
        viewTreeObserver = backgroundSibling.getViewTreeObserver();
        viewTreeObserver.addOnDrawListener(onDrawListener);
    }

    private void removeDrawListener() {
        if (viewTreeObserver != null && viewTreeObserver.isAlive()) {
            viewTreeObserver.removeOnDrawListener(onDrawListener);
            viewTreeObserver = null;
        }
    }

    private final Path clipPath = new Path();
    private final RectF clipRect = new RectF();

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        clipRect.set(0f, 0f, w, h);
        clipPath.reset();
        clipPath.addRoundRect(clipRect, cornerRadius, cornerRadius, Path.Direction.CCW);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (int) (dp(62) * scale));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        canvas.save();
        canvas.clipPath(clipPath);

        if (canvas.isHardwareAccelerated() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backgroundBlur) {
            drawBackgroundBlur(canvas);
        }
        canvas.drawColor(Color.argb(40 + lerp(0, 20, pressedProgress), 0, 0, 0));

        canvas.save();
        canvas.translate(getWidth() / 2f - drawable.getBounds().width() / 2f, dp(8));
        drawable.draw(canvas);
        canvas.restore();

        canvas.drawText(buttonText, getWidth() / 2f, getHeight() - dp(8) - textPaint.descent(), textPaint);

        canvas.restore();
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void drawBackgroundBlur(Canvas canvas) {
        backgroundSibling.getLocationOnScreen(backgroundSiblingLoc);
        getLocationOnScreen(viewLoc);

        createRenderNode();

        captureSiblingRenderNode();

        canvas.drawRenderNode(blurRenderNode);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void captureSiblingRenderNode() {
        try {
            blurRenderNode.setPosition(0, 0, backgroundSibling.getWidth(), backgroundSibling.getHeight());
            blurRenderNode.setTranslationX(backgroundSiblingLoc[0] - viewLoc[0]);
            blurRenderNode.setTranslationY(backgroundSiblingLoc[1] - viewLoc[1]);
            RecordingCanvas recordingCanvas = blurRenderNode.beginRecording();
            backgroundSibling.draw(recordingCanvas);
        } finally {
            blurRenderNode.endRecording();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void createRenderNode() {
        if (blurRenderNode == null) {
            blurRenderNode = new RenderNode("ProfileButtonViewRenderNode");
            blurRenderNode.setRenderEffect(RenderEffect.createBlurEffect(dp(16), dp(16), Shader.TileMode.DECAL));
        }
    }

    public interface OnLongPressedCallback {

        public boolean onLongPressed(float x, float y);

    }
}
