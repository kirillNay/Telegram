package org.telegram;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;

public final class DebugUtils {

    public static Paint debugPaint1 = new Paint();
    public static Paint debugPaint2 = new Paint();

    static {
        debugPaint1.setColor(Color.argb(255, 255, 0, 0));
        debugPaint1.setColor(Color.argb(255, 0, 255, 255));
    }

    public static void logCanvasInfo(Canvas canvas) {
        Matrix matrix = new Matrix();
        canvas.getMatrix(matrix);

        float[] values = new float[9];
        matrix.getValues(values);

        float translateX = values[Matrix.MTRANS_X];
        float translateY = values[Matrix.MTRANS_Y];

        Log.d(TAG, "translateX: " + translateX + ", translateY: " + translateY);
    }

    public static void logValue(String name, Object value) {
        Log.d(TAG, name + " = " + value);
    }

    public static void logValue(Object value) {
        Log.d(TAG, value.getClass().getSimpleName() + " = " + value);
    }

    public static void logValue(Object ...values) {
        for (Object value : values) {
            Log.d(TAG, value.getClass().getSimpleName() + " = " + value);
        }
    }

    public static void log(String message) {
        Log.d(TAG, message);
    }

    public static void error(Throwable exception) {
        Log.e(TAG, "Exception", exception);
    }

    public static void warn(String message) {
        Log.w(TAG, message);
    }

    private static final String TAG = "kirillNay";

}
