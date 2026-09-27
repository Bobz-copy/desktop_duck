/**
 * @Author
 * @AIDE AIDE+
 */
package com.cfks.goosedroid;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.util.Log;
import android.view.*;

import com.cfks.goosedroid.GooseDesktop.*;
import com.cfks.goosedroid.SamEngine.*;

import java.lang.ref.WeakReference;

/**
 * Custom View that renders the goose animation.
 * Uses a WeakReference-based Handler to prevent memory leaks.
 */
public class GooseView extends View {
    private static final String TAG = "GooseView";

    private Context ctx;
    private ConfigureActivity ca;
    private boolean canvasInit = false;
    private RenderHandler handler;
    private Runnable renderRunnable;
    private int frameRefreshRate;
    private boolean isRunning = false;
    private static final int LOOP_ERROR_LOG_INTERVAL = 300;
    private int loopErrorCount = 0;

    public static float DrawSize = 2.5f;  // Default size for better visibility

    // Touch interaction state
    private boolean isTouchEnabled = true;

    /**
     * Static handler with WeakReference to prevent memory leaks.
     */
    private static class RenderHandler extends Handler {
        private final WeakReference<GooseView> viewRef;

        RenderHandler(GooseView view) {
            super(Looper.getMainLooper());
            this.viewRef = new WeakReference<>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            GooseView view = viewRef.get();
            if (view != null && view.isRunning) {
                view.postInvalidate();
                sendEmptyMessageDelayed(0, view.frameRefreshRate);
            }
        }
    }

    public GooseView(Context context, ConfigureActivity ca, int frameRefreshRate) {
        super(context);
        this.ctx = context;
        this.ca = ca;
        this.frameRefreshRate = Math.max(8, frameRefreshRate); // Minimum 8ms (~120 FPS max)
        this.handler = new RenderHandler(this);
        startRendering();
    }

    public GooseView(Context context, ConfigureActivity ca) {
        this(context, ca, 16);
    }

    /**
     * Start the rendering loop.
     */
    private void startRendering() {
        if (isRunning) return;

        isRunning = true;
        handler.sendEmptyMessageDelayed(0, frameRefreshRate);
        Log.i(TAG, "Rendering started with refresh rate: " + frameRefreshRate + "ms");
    }

    /**
     * Stop the rendering loop.
     */
    public void stopRendering() {
        isRunning = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        Log.i(TAG, "Rendering stopped");
    }

    /**
     * Pause rendering (e.g., when app goes to background).
     */
    public void pauseRendering() {
        stopRendering();
    }

    /**
     * Resume rendering (e.g., when app comes to foreground).
     */
    public void resumeRendering() {
        if (!isRunning) {
            startRendering();
        }
    }

    /**
     * Check if rendering is active.
     */
    public boolean isRendering() {
        return isRunning;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Validate DrawSize
        float safeDrawSize = Math.max(0.1f, Math.min(DrawSize, 10.0f));
        canvas.scale(safeDrawSize, safeDrawSize, (float) getWidth() / 2, (float) getHeight() / 2);

        if (!canvasInit) {
            try {
                TheGoose.Init(ctx, canvas, ca);
                canvasInit = true;
                Log.i(TAG, "Canvas initialized");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing TheGoose", e);
                return;
            }
        }

        // Tick y Render por separado: un fallo de lógica no debe dejar el frame en blanco.
        try {
            Time.TickTime();
            TheGoose.Tick();
        } catch (Exception e) {
            logLoopError("Error during tick", e);
        }

        int saveCount = canvas.save();
        try {
            TheGoose.Render(canvas);
        } catch (Exception e) {
            logLoopError("Error during render", e);
        } finally {
            canvas.restoreToCount(saveCount);
        }
    }

    /**
     * Loguea errores del loop sin inundar logcat: el primero completo y luego
     * uno cada LOOP_ERROR_LOG_INTERVAL.
     */
    private void logLoopError(String message, Exception e) {
        loopErrorCount++;
        if (loopErrorCount == 1 || loopErrorCount % LOOP_ERROR_LOG_INTERVAL == 0) {
            Log.e(TAG, message + " (x" + loopErrorCount + ")", e);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopRendering();
        Log.i(TAG, "View detached from window");
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isRunning && canvasInit) {
            startRendering();
        }
        Log.i(TAG, "View attached to window");
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        if (visibility == View.VISIBLE) {
            if (!isRunning && canvasInit) {
                resumeRendering();
            }
        } else {
            pauseRendering();
        }
    }

    // Track if we're currently interacting with the goose
    private boolean isInteractingWithGoose = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isTouchEnabled) {
            return false;
        }

        // Validate DrawSize for touch coordinate conversion
        float safeDrawSize = Math.max(0.1f, Math.min(DrawSize, 10.0f));

        // Convert touch coordinates to scaled canvas coordinates
        float x = event.getX() / safeDrawSize;
        float y = event.getY() / safeDrawSize;

        try {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Check if touch is near the goose (within 150 pixels)
                    float gooseX = TheGoose.getGoosePos().x;
                    float gooseY = TheGoose.getGoosePos().y;
                    float touchRadius = 150f * TheGoose.DrawScale; // Scale touch radius with goose size
                    float dist = (float) Math.sqrt(Math.pow(x - gooseX, 2) + Math.pow(y - gooseY, 2));

                    if (dist <= touchRadius) {
                        // Touch is on the goose - handle it
                        isInteractingWithGoose = true;
                        TheGoose.onTouchStart(x, y);
                        return true;
                    } else {
                        // Touch is not on goose - let it pass through
                        isInteractingWithGoose = false;
                        return false;
                    }

                case MotionEvent.ACTION_MOVE:
                    if (isInteractingWithGoose) {
                        TheGoose.onTouchMove(x, y);
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isInteractingWithGoose) {
                        TheGoose.onTouchEnd(x, y);
                        isInteractingWithGoose = false;
                        return true;
                    }
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling touch event", e);
        }

        return super.onTouchEvent(event);
    }

    /**
     * Enable or disable touch interaction.
     */
    public void setTouchEnabled(boolean enabled) {
        this.isTouchEnabled = enabled;
    }

    /**
     * Check if touch interaction is enabled.
     */
    public boolean isTouchEnabled() {
        return isTouchEnabled;
    }

    /**
     * Set the frame refresh rate.
     * @param rate Refresh rate in milliseconds (minimum 1ms)
     */
    public void setFrameRefreshRate(int rate) {
        this.frameRefreshRate = Math.max(8, rate);
    }

    /**
     * Clean up resources when view is being destroyed.
     */
    public void cleanup() {
        stopRendering();
        handler = null;
        ctx = null;
        ca = null;
        Log.i(TAG, "GooseView cleanup complete");
    }
}
