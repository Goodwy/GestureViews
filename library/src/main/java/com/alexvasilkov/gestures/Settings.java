package com.alexvasilkov.gestures;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import com.alexvasilkov.gestures.views.interfaces.GestureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Various settings needed for {@link GestureController} and for {@link StateController}.
 * <p>
 * Required settings are viewport size ({@link #setViewport(int, int)})
 * and image size {@link #setImage(int, int)}
 */
public class Settings {

    public static final float MAX_ZOOM = 2f;
    public static final float OVERZOOM_FACTOR = 2f;
    public static final long ANIMATIONS_DURATION = 300L;

    /*
     * Viewport area.
     */
    private int viewportW;
    private int viewportH;

    /*
     * Movement area.
     */
    private int movementAreaW;
    private int movementAreaH;

    private boolean isMovementAreaSpecified;

    /*
     * Image size.
     */
    private int imageW;
    private int imageH;

    /*
     * Min zoom level, default value is 0f, meaning min zoom will be adjusted to fit viewport.
     */
    private float minZoom = 0f;

    /*
     * Max zoom level, default value is {@link #MAX_ZOOM}.
     */
    private float maxZoom = MAX_ZOOM;

    /*
     * Double tap zoom level, default value is -1. Defaults to {@link #maxZoom} if <= 0.
     */
    private float doubleTapZoom = -1f;

    /*
     * Overzoom factor.
     */
    private float overzoomFactor = OVERZOOM_FACTOR;

    /*
     * Whether zooming is enabled or not.
     */
    private boolean isZoomEnabled = true;

    /*
     * Whether rotation gesture is enabled or not.
     */
    private boolean isRotationEnabled = false;

    /*
     * Whether image rotation should stick to 90 degrees or can be free.
     */
    private boolean isRestrictRotation = false;

    /*
     * Counter for gestures disabling calls.
     */
    private int gesturesDisableCount;

    Settings() {
        // Package private constructor
    }

    public void initFromAttributes(@NonNull Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.GestureView);

        movementAreaW = arr.getDimensionPixelSize(
                R.styleable.GestureView_gest_movementAreaWidth, movementAreaW);
        movementAreaH = arr.getDimensionPixelSize(
                R.styleable.GestureView_gest_movementAreaHeight, movementAreaH);
        isMovementAreaSpecified = movementAreaW > 0 && movementAreaH > 0;

        minZoom = arr.getFloat(
                R.styleable.GestureView_gest_minZoom, minZoom);
        maxZoom = arr.getFloat(
                R.styleable.GestureView_gest_maxZoom, maxZoom);
        doubleTapZoom = arr.getFloat(
                R.styleable.GestureView_gest_doubleTapZoom, doubleTapZoom);
        overzoomFactor = arr.getFloat(
                R.styleable.GestureView_gest_overzoomFactor, overzoomFactor);

        isZoomEnabled = arr.getBoolean(
                R.styleable.GestureView_gest_zoomEnabled, isZoomEnabled);
        isRotationEnabled = arr.getBoolean(
                R.styleable.GestureView_gest_rotationEnabled, isRotationEnabled);
        isRestrictRotation = arr.getBoolean(
                R.styleable.GestureView_gest_restrictRotation, isRestrictRotation);

        boolean disableGestures = arr.getBoolean(
                R.styleable.GestureView_gest_disableGestures, false);
        if (disableGestures) {
            disableGestures();
        }

        arr.recycle();
    }

    /**
     * Setting viewport size.
     * <p>
     * Should only be used when implementing custom {@link GestureView}.
     *
     * @param width  Viewport width
     * @param height Viewport height
     * @return Current settings object for calls chaining
     */
    public Settings setViewport(int width, int height) {
        viewportW = width;
        viewportH = height;
        return this;
    }

    /**
     * Setting movement area size. Viewport area will be used instead if no movement area is
     * specified.
     *
     * @param width  Movement area width
     * @param height Movement area height
     * @return Current settings object for calls chaining
     */
    public Settings setMovementArea(int width, int height) {
        isMovementAreaSpecified = true;
        movementAreaW = width;
        movementAreaH = height;
        return this;
    }

    /**
     * Setting full image size.
     * <p>
     * Should only be used when implementing custom {@link GestureView}.
     *
     * @param width  Image width
     * @param height Image height
     * @return Current settings object for calls chaining
     */
    public Settings setImage(int width, int height) {
        imageW = width;
        imageH = height;
        return this;
    }

    /**
     * Setting max zoom level.
     * <p>
     * Default value is {@link #MAX_ZOOM}.
     *
     * @param maxZoom Max zoom level, or 0 to use zoom level which fits the image into the viewport.
     * @return Current settings object for calls chaining
     */
    public Settings setMaxZoom(float maxZoom) {
        this.maxZoom = maxZoom;
        return this;
    }

    /**
     * Setting double tap zoom level, should not be greater than {@link #getMaxZoom()}.
     * Defaults to {@link #getMaxZoom()} if &lt;= 0.
     * <p>
     * Default value is -1.
     *
     * @param doubleTapZoom Double tap zoom level
     * @return Current settings object for calls chaining
     */
    public Settings setDoubleTapZoom(float doubleTapZoom) {
        this.doubleTapZoom = doubleTapZoom;
        return this;
    }

    /**
     * Setting overzoom factor. User will be able to "over zoom" up to this factor.
     * Cannot be &lt; 1.
     * <p>
     * Default value is {@link #OVERZOOM_FACTOR}.
     *
     * @param factor Overzoom factor
     * @return Current settings object for calls chaining
     */
    public Settings setOverzoomFactor(float factor) {
        if (factor < 1f) {
            throw new IllegalArgumentException("Overzoom factor cannot be < 1");
        }
        overzoomFactor = factor;
        return this;
    }

    /**
     * Sets whether zooming is enabled or not.
     * <p>
     * Default value is true.
     *
     * @param enabled Whether zooming should be enabled or not
     * @return Current settings object for calls chaining
     */
    public Settings setZoomEnabled(boolean enabled) {
        isZoomEnabled = enabled;
        return this;
    }

    /**
     * Sets whether rotation gesture is enabled or not.
     * <p>
     * Default value is false.
     *
     * @param enabled Whether rotation should be enabled or not
     * @return Current settings object for calls chaining
     */
    public Settings setRotationEnabled(boolean enabled) {
        isRotationEnabled = enabled;
        return this;
    }

    /**
     * Sets whether image rotation should stick to 90 degrees intervals or can be free.
     * <p>
     * Default value is false.
     *
     * @param restrict Whether rotation should be restricted or not
     * @return Current settings object for calls chaining
     */
    public Settings setRestrictRotation(boolean restrict) {
        isRestrictRotation = restrict;
        return this;
    }

    /**
     * Disable all gestures.<br>
     * Calls to this method are counted, so if you called it N times
     * you should call {@link #enableGestures()} N times to re-enable all gestures.
     * <p>
     * Useful when you need to temporary disable touch gestures during animation or image loading.
     * <p>
     * See also {@link #enableGestures()}
     *
     * @return Current settings object for calls chaining
     */
    public Settings disableGestures() {
        gesturesDisableCount++;
        return this;
    }

    /**
     * Re-enable all gestures disabled by {@link #disableGestures()} method.<br>
     * Calls to this method are counted, so if you called {@link #disableGestures()} N times
     * you should call this method N times to re-enable all gestures.
     * <p>
     * See also {@link #disableGestures()}
     *
     * @return Current settings object for calls chaining
     */
    public Settings enableGestures() {
        gesturesDisableCount--;
        return this;
    }

    // --------------
    //  Getters
    // --------------

    public int getViewportW() {
        return viewportW;
    }

    public int getViewportH() {
        return viewportH;
    }

    public int getMovementAreaW() {
        return isMovementAreaSpecified ? movementAreaW : viewportW;
    }

    public int getMovementAreaH() {
        return isMovementAreaSpecified ? movementAreaH : viewportH;
    }

    public int getImageW() {
        return imageW;
    }

    public int getImageH() {
        return imageH;
    }

    public float getMinZoom() {
        return minZoom;
    }

    public float getMaxZoom() {
        return maxZoom;
    }

    public float getDoubleTapZoom() {
        return doubleTapZoom;
    }

    public float getOverzoomFactor() {
        return overzoomFactor;
    }

    public boolean isZoomEnabled() {
        return isGesturesEnabled() && isZoomEnabled;
    }

    public boolean isRotationEnabled() {
        return isGesturesEnabled() && isRotationEnabled;
    }

    public boolean isRestrictRotation() {
        return isRestrictRotation;
    }

    public boolean isDoubleTapEnabled() {
        return isGesturesEnabled() && isZoomEnabled;
    }

    public boolean isGesturesEnabled() {
        return gesturesDisableCount <= 0;
    }

    /**
     * @return Whether at least one of pan, zoom, rotation or double tap are enabled or not
     */
    public boolean isEnabled() {
        return isGesturesEnabled() && (isZoomEnabled || isRotationEnabled);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted") // Public API
    public boolean hasImageSize() {
        return imageW != 0 && imageH != 0;
    }

    public boolean hasViewportSize() {
        return viewportW != 0 && viewportH != 0;
    }
}
