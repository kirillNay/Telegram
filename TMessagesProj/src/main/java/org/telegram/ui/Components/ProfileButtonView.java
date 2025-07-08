package org.telegram.ui.Components;

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
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import static org.telegram.messenger.AndroidUtilities.dp;

@SuppressLint("ViewConstructor")
public class ProfileButtonView extends View {

    private final View backgroundSibling;

    private ViewTreeObserver viewTreeObserver;
    private RenderNode blurRenderNode;

    private final Paint textPaint;
    private final Drawable drawable;
    private final String buttonText;

    private float cornerRadius;

    private final ViewTreeObserver.OnDrawListener onDrawListener = this::invalidate;

    private final int[] backgroundSiblingLoc = new int[2];
    private final int[] viewLoc = new int[2];

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
        setCornerRadius(cornerRadius);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Theme.getColor(Theme.key_profile_title, resourcesProvider));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(dp(10));
        textPaint.setTextAlign(Paint.Align.CENTER);

        drawable = ContextCompat.getDrawable(context, iconRes);
        if (drawable != null) {
            drawable.setBounds(0, 0, dp(30), dp(30));
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_actionIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
        }

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(62));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        canvas.save();
        canvas.clipPath(clipPath);

        // Drawing background
        if (canvas.isHardwareAccelerated() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            drawBackgroundBlur(canvas);
        }
        canvas.drawColor(Color.argb(40, 0, 0, 0));

        // Drawing icon
        canvas.save();
        canvas.translate(getWidth() / 2f - dp( 15), dp(8));
        drawable.draw(canvas);
        canvas.restore();

        // Drawing text
        canvas.drawText(buttonText, getWidth() / 2f, dp(54), textPaint);

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
}
