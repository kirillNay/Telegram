package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import org.telegram.ui.Components.LayoutHelper;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES10.glClearColor;
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
import static android.opengl.GLES20.glUniform1f;
import static android.opengl.GLES20.glUniform2f;
import static android.opengl.GLES20.glUniform4f;
import static android.opengl.GLES20.glUseProgram;
import static org.telegram.messenger.AndroidUtilities.dp;

public class QuickShareLayout extends FrameLayout {

    private final GLSurfaceView balloonView;

    private Runnable onHide;

    public QuickShareLayout(@NonNull Context context) {
        super(context);

        setLayoutParams(new ViewGroup.LayoutParams(dp(64), dp(64)));
        setBackgroundColor(Color.argb(20, 255, 0, 0));
        setVisibility(View.GONE);

        balloonView = new GLSurfaceView(context);
        balloonView.setLayoutParams(new LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        balloonView.setEGLContextClientVersion(2);
        balloonView.setZOrderOnTop(true);
        balloonView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        balloonView.getHolder().setFormat(PixelFormat.RGBA_8888);
        balloonView.setRenderer(new QuickShareLayout.ShareBalloonRenderer());
        balloonView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        addView(balloonView);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.i("kirillNay", "QuickSharePopupWindow.onTouchEvent: " + event.getX() + "; " + event.getY());
        return super.onTouchEvent(event);
    }

    public void onHide(Runnable onHide) {
        this.onHide = onHide;
    }

    public void show() {
        setVisibility(View.VISIBLE);
    }



    public void hide() {
        setVisibility(View.GONE);
        if (onHide != null) {
            onHide.run();
        }
    }

    private static class ShareBalloonRenderer implements GLSurfaceView.Renderer {

        private final static String VERTEX_SHADER = "#version 300 es\n"
                + "precision mediump float;\n"
                + "\n"
                + "in vec4 aPosition;\n"
                + "out vec2 vTexCoord;\n"
                + "\n"
                + "void main() {\n"
                + "    vTexCoord = aPosition.xy * 0.5 + 0.5;\n"
                + "    gl_Position = aPosition;\n"
                + "}\n";

        private final static String FRAGMENT_SHADER = "#version 300 es\n"
                + "precision mediump float;\n"
                + "\n"
                + "in vec2 vTexCoord;   // Входные координаты текстуры (от 0.0 до 1.0)\n"
                + "out vec4 fragColor;  // Выходной цвет фрагмента\n"
                + "\n"
                + "uniform vec2 resolution;   // Размер экрана или текстуры\n"
                + "uniform vec4 rectColor;    // Цвет прямоугольника (RGBA)\n"
                + "uniform float radius;      // Радиус закругления\n"
                + "\n"
                + "void main() {\n"
                + "    // Преобразуем координаты в диапазон от 0 до размера экрана или прямоугольника\n"
                + "    vec2 uv = vTexCoord * resolution;\n"
                + "\n"
                + "    // Определяем размеры прямоугольника\n"
                + "    vec2 rectSize = resolution * 0.5; // Используем половину размера экрана для прямоугольника\n"
                + "    vec2 center = resolution * 0.5;   // Центр прямоугольника\n"
                + "\n"
                + "    // Вычисляем расстояние до ближайшего угла прямоугольника\n"
                + "    vec2 dist = abs(uv - center) - rectSize + vec2(radius);\n"
                + "\n"
                + "    // Если расстояние по обеим осям меньше нуля, значит пиксель находится внутри прямоугольника\n"
                + "    float outside = length(max(dist, 0.0)) - radius;\n"
                + "\n"
                + "    // Устанавливаем цвет пикселя, если он внутри закругленного прямоугольника, иначе делаем его прозрачным\n"
                + "    if (outside < 0.0) {\n"
                + "        fragColor = rectColor;\n"
                + "    } else {\n"
                + "        discard; // Пропускаем пиксели, которые выходят за границы\n"
                + "    }\n"
                + "}\n";

        private int program;

        private int uResolutionLocation;
        private int uRectColorLocation;
        private int uRadiusLocation;

        private int screenWidth;
        private int screenHeight;

        private final float[] mvpMatrix = new float[16];
        private final float[] scaleMatrix = new float[16];

        private float scaleFactor = 1.0f;
        private final float maxScaleFactor = 1.5f;
        private final float scaleSpeed = 0.01f;
        private boolean isScalingUp = true;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            int vertexShaderId = createShader(GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShaderId = createShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            program = createProgram(vertexShaderId, fragmentShaderId);

            //uMVPMatrixLocation = glGetUniformLocation(program, "uMVPMatrix");
            uResolutionLocation = glGetUniformLocation(program, "resolution");
            uRectColorLocation = glGetUniformLocation(program, "rectColor");
            uRadiusLocation = glGetUniformLocation(program, "radius");

            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            glViewport(0, 0, width, height);

            screenWidth = width;
            screenHeight = height;

            // Устанавливаем начальную матрицу проекции
            Matrix.setIdentityM(mvpMatrix, 0);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            glClear(GL_COLOR_BUFFER_BIT);

            // Обновляем scaleFactor для анимации надувания/сдувания
            if (isScalingUp) {
                scaleFactor += scaleSpeed;
                if (scaleFactor >= maxScaleFactor) {
                    isScalingUp = false;
                }
            } else {
                scaleFactor -= scaleSpeed;
                if (scaleFactor <= 1.0f) {
                    isScalingUp = true;
                }
            }

            // Обновляем матрицу масштабирования
            Matrix.setIdentityM(scaleMatrix, 0);
            Matrix.scaleM(scaleMatrix, 0, scaleFactor, scaleFactor, 1.0f);
            Matrix.multiplyMM(mvpMatrix, 0, scaleMatrix, 0, mvpMatrix, 0);

            glUseProgram(program);

            //glUniformMatrix4fv(uMVPMatrixLocation, 1, false, mvpMatrix, 0);
            glUniform2f(uResolutionLocation, (float) screenWidth, (float) screenHeight);
            glUniform4f(uRectColorLocation, 1.0f, 0.0f, 0.0f, 0.0f); // Красный цвет
            glUniform1f(uRadiusLocation, 40.0f);

            drawRectangle();

            glUseProgram(0);
        }

        private void drawRectangle() {
            float[] vertices = {
                    -1.0f, -1.0f, 0.0f,
                    1.0f, -1.0f, 0.0f,
                    -1.0f,  1.0f, 0.0f,
                    1.0f,  1.0f, 0.0f
            };

            // Загрузка координат вершин в буфер
            FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(vertices);
            vertexBuffer.position(0);

            // Создание буфера вершин и привязка его в OpenGL
            int[] vertexBufferId = new int[1];
            GLES20.glGenBuffers(1, vertexBufferId, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId[0]);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.length * 4, vertexBuffer, GLES20.GL_STATIC_DRAW);

            // Получаем атрибут позиции в шейдере и включаем его
            int aPositionLocation = GLES20.glGetAttribLocation(program, "aPosition");
            GLES20.glEnableVertexAttribArray(aPositionLocation);
            GLES20.glVertexAttribPointer(aPositionLocation, 3, GLES20.GL_FLOAT, false, 3 * 4, 0);

            // Рисуем треугольники (создающие прямоугольник)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertices.length / 3);

            // Отключаем атрибут позиции
            GLES20.glDisableVertexAttribArray(aPositionLocation);

            // Отвязываем буфер вершин
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
