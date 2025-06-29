package org.telegram;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.Log;

public final class DebugUtils {

    public static void logCanvasInfo(Canvas canvas) {
        Matrix matrix = new Matrix();
        canvas.getMatrix(matrix);

        float[] values = new float[9];
        matrix.getValues(values);

        float translateX = values[Matrix.MTRANS_X];
        float translateY = values[Matrix.MTRANS_Y];

        Log.d(TAG, "translateX: " + translateX + ", translateY: " + translateY);
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

    private static final String TAG = "kirillNay";

}
