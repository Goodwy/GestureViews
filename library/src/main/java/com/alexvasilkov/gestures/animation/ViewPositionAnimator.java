package com.alexvasilkov.gestures.animation;

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.alexvasilkov.gestures.GestureController;
import com.alexvasilkov.gestures.GestureControllerForPager;
import com.alexvasilkov.gestures.Settings;
import com.alexvasilkov.gestures.State;
import com.alexvasilkov.gestures.internal.AnimationEngine;
import com.alexvasilkov.gestures.utils.FloatScroller;
import com.alexvasilkov.gestures.utils.GravityUtils;
import com.alexvasilkov.gestures.utils.MathUtils;
import com.alexvasilkov.gestures.views.GestureImageView;
import com.alexvasilkov.gestures.views.interfaces.ClipBounds;
import com.alexvasilkov.gestures.views.interfaces.ClipView;
import com.alexvasilkov.gestures.views.interfaces.GestureView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;

/**
 * Helper class to animate views from one position on screen to another.
 * <p>
 * Animation can be performed from any view (e.g. {@link ImageView}) to any gestures controlled
 * view implementing {@link GestureView} (e.g. {@link GestureImageView}).
 * <p>
 * Note, that initial and final views should have same aspect ratio for correct animation.
 * In case of {@link ImageView} initial and final images should have same aspect, but actual views
 * can have different aspects (e.g. animating from square thumb view with scale type
 * {@link ScaleType#CENTER_CROP} to rectangular full image view).
 * <p>
 * You can listen for position changes using
 * {@link #addPositionUpdateListener(PositionUpdateListener)}.<br>
 * If initial view was changed you should call {@link #update(View)} method to update to new view.
 * You can also manually update initial view position using {@link #update(ViewPosition)} method.
 */
public class ViewPositionAnimator {

    private static final Matrix tmpMatrix = new Matrix();
    private static final float[] tmpPointArr = new float[2];
    private static final Point tmpPoint = new Point();

    private final List<PositionUpdateListener> listeners = new ArrayList<>();
    private final List<PositionUpdateListener> listenersToRemove = new ArrayList<>();
    private boolean iteratingListeners;

    private final FloatScroller positionScroller = new FloatScroller();
    private final AnimationEngine animationEngine;

    private final GestureController toController;
    private final ClipView toClipView;
    private final ClipBounds toClipBounds;

    private final State fromState = new State();
    private final State toState = new State();
    private float fromPivotX;
    private float fromPivotY;
    private float toPivotX;
    private float toPivotY;
    private final RectF fromClip = new RectF();
    private final RectF toClip = new RectF();
    private final RectF fromBoundsClip = new RectF();
    private final RectF toBoundsClip = new RectF();
    private final RectF clipRectTmp = new RectF();
    private ViewPosition fromPos;
    private ViewPosition toPos;
    private boolean fromNonePos;

    private View fromView;

    private boolean isActivated = false;

    private float toPosition = 1f;
    private float position = 0f;

    private boolean isLeaving = true; // Leaving by default
    private boolean isAnimating = false;
    private boolean isApplyingPosition;
    private boolean isApplyingPositionScheduled;

    // Marks that update for 'From' or 'To' is needed
    private boolean isFromUpdated;
    private boolean isToUpdated;

    private final ViewPositionHolder fromPosHolder = new ViewPositionHolder();
    private final ViewPositionHolder toPosHolder = new ViewPositionHolder();

    private final ViewPositionHolder.OnViewPositionChangeListener fromPositionListener =
            new ViewPositionHolder.OnViewPositionChangeListener() {
                @Override
                public void onViewPositionChanged(@NonNull ViewPosition position) {
                    fromPos = position;
                    requestUpdateFromState();
                    applyCurrentPosition();
                }
            };


    public ViewPositionAnimator(@NonNull GestureView to) {
        if (!(to instanceof View)) {
            throw new IllegalArgumentException("Argument 'to' should be an instance of View");
        }

        View toView = (View) to;
        toClipView = to instanceof ClipView ? (ClipView) to : null;
        toClipBounds = to instanceof ClipBounds ? (ClipBounds) to : null;
        animationEngine = new LocalAnimationEngine(toView);

        toController = to.getController();
        toController.addOnStateChangeListener(new GestureController.OnStateChangeListener() {
            @Override
            public void onStateChanged(State state) {
                // Applying zoom patch (needed in case if image size is changed)
                toController.getStateController().applyZoomPatch(fromState);
                toController.getStateController().applyZoomPatch(toState);
            }

            @Override
            public void onStateReset(State oldState, State newState) {
                if (!isActivated) {
                    return;
                }

                setToState(newState, 1f); // We have to reset full state
                applyCurrentPosition();
            }
        });

        toPosHolder.init(toView, new ViewPositionHolder.OnViewPositionChangeListener() {
            @Override
            public void onViewPositionChanged(@NonNull ViewPosition position) {
                toPos = position;
                requestUpdateToState();
                requestUpdateFromState(); // Depends on 'to' position
                applyCurrentPosition();
            }
        });

        // Position updates are paused by default, until animation is started
        fromPosHolder.pause(true);
        toPosHolder.pause(true);
    }

    /**
     * Updates initial view in case it was changed. You should not call this method if view stays
     * the same since animator should automatically detect view position changes.
     *
     * @param from New 'from' view
     */
    public void update(@NonNull View from) {
        updateInternal(from);
    }

    /**
     * Updates position of initial view in case it was changed.
     *
     * @param from New 'from' view position
     */
    public void update(@NonNull ViewPosition from) {
        updateInternal(from);
    }

    /**
     * Updates position of initial view to no specific position, in case 'to' view is not available
     * anymore.
     */
    public void updateToNone() {
        updateInternal();
    }

    private void updateInternal(@NonNull View from) {
        cleanBeforeUpdateInternal();
        fromView = from;
        fromPosHolder.init(from, fromPositionListener);
        from.setVisibility(View.INVISIBLE); // We don't want duplicate view during animation
    }

    private void updateInternal(@NonNull ViewPosition from) {
        cleanBeforeUpdateInternal();
        fromPos = from;
        applyCurrentPosition();
    }

    private void updateInternal() {
        cleanBeforeUpdateInternal();
        fromNonePos = true;
        applyCurrentPosition();
    }

    private void cleanBeforeUpdateInternal() {
        if (!isActivated) {
            throw new IllegalStateException(
                    "You should call enter(...) before calling update(...)");
        }

        cleanup();
        requestUpdateFromState();
    }

    private void cleanup() {
        if (fromView != null) {
            fromView.setVisibility(View.VISIBLE); // Switching back to visible
        }
        if (toClipView != null) {
            toClipView.clipView(null, 0f);
        }

        fromPosHolder.clear();
        fromView = null;
        fromPos = null;
        fromNonePos = false;
        isFromUpdated = isToUpdated = false;
    }

    /**
     * Adds position state changes listener that will be notified during animations.
     *
     * @param listener Position listener
     */
    public void addPositionUpdateListener(@NonNull PositionUpdateListener listener) {
        listeners.add(listener);
        listenersToRemove.remove(listener);
    }

    /**
     * Removes position state changes listener as added by
     * {@link #addPositionUpdateListener(PositionUpdateListener)}.
     * <p>
     * Note, this method may be called inside listener's callback without throwing
     * {@link IndexOutOfBoundsException}.
     *
     * @param listener Position listener to be removed
     */
    public void removePositionUpdateListener(@NonNull PositionUpdateListener listener) {
        if (iteratingListeners) {
            listenersToRemove.add(listener);
        } else {
            listeners.remove(listener);
        }
    }

    private void ensurePositionUpdateListenersRemoved() {
        listeners.removeAll(listenersToRemove);
        listenersToRemove.clear();
    }

    /**
     * @return Current position within range {@code [0, 1]}, where {@code 0} is for
     * initial (from) position and {@code 1} is for final (to) position.
     */
    public float getPosition() {
        return position;
    }

    /**
     * @return Current position
     * @deprecated Use {@link #getPosition()} method instead.
     */
    @Deprecated
    public float getPositionState() {
        return position;
    }

    /**
     * @return Whether animator is in leaving state. Means that animation direction is
     * from final (to) position back to initial (from) position.
     */
    public boolean isLeaving() {
        return isLeaving;
    }


    /**
     * Specifies target ('to') state and it's position which will be used to interpolate
     * current state for intermediate positions (i.e. during animation or exit gesture).<br>
     * This allows you to set up correct state without changing current position
     * ({@link #getPosition()}).
     * <p>
     * Only use this method if you understand what you do.
     *
     * @param state    Target ('to') state
     * @param position Target ('to') position
     */
    public void setToState(State state, @FloatRange(from = 0f, to = 1f) float position) {
        if (position <= 0) {
            throw new IllegalArgumentException("'To' position cannot be <= 0");
        }
        if (position > 1f) {
            throw new IllegalArgumentException("'To' position cannot be > 1");
        }

        toPosition = position;
        toState.set(state);
        requestUpdateToState();
        requestUpdateFromState();
    }

    /**
     * Stops current animation and sets position state to particular values.
     * <p>
     * Note, that once animator reaches {@code state = 0f} and {@code isLeaving = true}
     * it will cleanup all internal stuff.
     *
     * @param pos     Current position
     * @param leaving Whether we we are in exiting direction ({@code true}) or in entering
     *                ({@code false})
     * @param animate Whether we should start animating from given position and in given direction
     */
    public void setState(@FloatRange(from = 0f, to = 1f) float pos,
                         boolean leaving, boolean animate) {
        if (!isActivated) {
            throw new IllegalStateException(
                    "You should call enter(...) before calling setState(...)");
        }

        stopAnimation();
        position = pos < 0f ? 0f : (pos > 1f ? 1f : pos);
        isLeaving = leaving;
        if (animate) {
            startAnimationInternal();
        }
        applyCurrentPosition();
    }

    private void applyCurrentPosition() {
        if (!isActivated) {
            return;
        }

        if (isApplyingPosition) {
            // Excluding possible nested calls, scheduling sequential call instead
            isApplyingPositionScheduled = true;
            return;
        }
        isApplyingPosition = true;

        // We do not need to update while 'to' view is fully visible or fully closed
        boolean paused = isLeaving ? position == 0f : position == 1f;
        fromPosHolder.pause(paused);
        toPosHolder.pause(paused);

        // Perform state updates if needed
        if (!isToUpdated) {
            updateToState();
        }
        if (!isFromUpdated) {
            updateFromState();
        }

        boolean canUpdate = position < toPosition || (isAnimating && position == toPosition);
        if (isToUpdated && isFromUpdated && canUpdate) {
            State state = toController.getState();

            MathUtils.interpolate(state, fromState, fromPivotX, fromPivotY,
                    toState, toPivotX, toPivotY, position / toPosition);

            toController.updateState();

            final boolean skipClip = position >= toPosition || (position == 0f && isLeaving);
            final float clipPosition = position / toPosition;

            if (toClipView != null) {
                MathUtils.interpolate(clipRectTmp, fromClip, toClip, clipPosition);
                toClipView.clipView(skipClip ? null : clipRectTmp, state.getRotation());
            }
            if (toClipBounds != null) {
                // Bounds clipping should stay longer in 'From' state
                final float boundsClipPos = clipPosition * clipPosition;
                MathUtils.interpolate(clipRectTmp, fromBoundsClip, toBoundsClip, boundsClipPos);
                toClipBounds.clipBounds(skipClip ? null : clipRectTmp);
            }
        }

        iteratingListeners = true;
        for (int i = 0, size = listeners.size(); i < size; i++) {
            if (isApplyingPositionScheduled) {
                break; // No need to call listeners anymore
            }
            listeners.get(i).onPositionUpdate(position, isLeaving);
        }
        iteratingListeners = false;
        ensurePositionUpdateListenersRemoved();

        if (position == 0f && isLeaving) {
            cleanup();
            isActivated = false;
            toController.resetState(); // Switching to initial state
        }

        isApplyingPosition = false;

        if (isApplyingPositionScheduled) {
            isApplyingPositionScheduled = false;
            applyCurrentPosition();
        }
    }


    /**
     * @return Whether view position animation is in progress or not.
     */
    public boolean isAnimating() {
        return isAnimating;
    }

    /**
     * Starts animation from current position ({@link #position}) in current
     * direction ({@link #isLeaving}).
     */
    private void startAnimationInternal() {
        positionScroller.startScroll(position, isLeaving ? 0f : 1f);
        animationEngine.start();
        onAnimationStarted();
    }

    /**
     * Stops current animation, if any.
     */
    @SuppressWarnings("WeakerAccess") // Public API
    public void stopAnimation() {
        positionScroller.forceFinished();
        onAnimationStopped();
    }

    private void onAnimationStarted() {
        if (isAnimating) {
            return;
        }
        isAnimating = true;

        // Disabling bounds restrictions & any gestures
        toController.getSettings().disableBounds().disableGestures();
        // Stopping all currently playing state animations
        toController.stopAllAnimations();

        // Disabling ViewPager scroll
        if (toController instanceof GestureControllerForPager) {
            ((GestureControllerForPager) toController).disableViewPager(true);
        }
    }

    private void onAnimationStopped() {
        if (!isAnimating) {
            return;
        }
        isAnimating = false;

        // Restoring original settings
        toController.getSettings().enableBounds().enableGestures();

        // Enabling ViewPager scroll
        if (toController instanceof GestureControllerForPager) {
            ((GestureControllerForPager) toController).disableViewPager(false);
        }

        toController.animateKeepInBounds();
    }

    private void requestUpdateToState() {
        isToUpdated = false;
    }

    private void requestUpdateFromState() {
        isFromUpdated = false;
    }

    private void updateToState() {
        if (isToUpdated) {
            return;
        }

        Settings settings = toController == null ? null : toController.getSettings();

        if (toPos == null || settings == null || !settings.hasImageSize()) {
            return;
        }

        toState.get(tmpMatrix);

        // 'To' clip is a 'To' image rect in 'To' view coordinates
        toClip.set(0, 0, settings.getImageW(), settings.getImageH());

        // Computing pivot point as center of the image after transformation
        tmpPointArr[0] = toClip.centerX();
        tmpPointArr[1] = toClip.centerY();
        tmpMatrix.mapPoints(tmpPointArr);

        toPivotX = tmpPointArr[0];
        toPivotY = tmpPointArr[1];

        // Computing clip rect in 'To' view coordinates without rotation
        tmpMatrix.postRotate(-toState.getRotation(), toPivotX, toPivotY);
        tmpMatrix.mapRect(toClip);
        toClip.offset(toPos.viewport.left - toPos.view.left, toPos.viewport.top - toPos.view.top);

        // 'To' bounds clip is entire 'To' view rect in 'To' view coordinates
        toBoundsClip.set(0f, 0f, toPos.view.width(), toPos.view.height());

        isToUpdated = true;
    }

    private void updateFromState() {
        if (isFromUpdated) {
            return;
        }

        Settings settings = toController == null ? null : toController.getSettings();

        if (fromNonePos && settings != null && toPos != null) {
            fromPos = fromPos == null ? ViewPosition.newInstance() : fromPos;

            GravityUtils.getDefaultPivot(settings, tmpPoint);
            tmpPoint.offset(toPos.view.left, toPos.view.top); // Ensure we're in correct coordinates
            ViewPosition.apply(fromPos, tmpPoint);
        }

        if (toPos == null || fromPos == null || settings == null || !settings.hasImageSize()) {
            return;
        }

        // 'From' pivot point is a center of image in 'To' viewport coordinates
        fromPivotX = fromPos.image.centerX() - toPos.viewport.left;
        fromPivotY = fromPos.image.centerY() - toPos.viewport.top;

        // Computing starting zoom level
        float imageWidth = settings.getImageW();
        float imageHeight = settings.getImageH();
        float zoomW = imageWidth == 0f ? 1f : fromPos.image.width() / imageWidth;
        float zoomH = imageHeight == 0f ? 1f : fromPos.image.height() / imageHeight;
        float zoom = Math.max(zoomW, zoomH);

        // Computing 'From' image in 'To' viewport coordinates.
        // If 'To' image has different aspect ratio it will be centered within the 'From' image.
        float fromX = fromPos.image.centerX() - 0.5f * imageWidth * zoom - toPos.viewport.left;
        float fromY = fromPos.image.centerY() - 0.5f * imageHeight * zoom - toPos.viewport.top;

        fromState.set(fromX, fromY, zoom, 0f);

        // 'From' clip is 'From' view rect in 'To' view coordinates
        fromClip.set(fromPos.viewport);
        fromClip.offset(-toPos.view.left, -toPos.view.top);

        // 'From' bounds clip is a part of 'To' view which considered to be visible.
        // Meaning that if 'From' view is truncated in any direction this clipping should be
        // animated, otherwise it will look like part of 'From' view is instantly becoming visible.
        fromBoundsClip.set(0f, 0f, toPos.view.width(), toPos.view.height());
        fromBoundsClip.left = compareAndSetClipBound(
                fromBoundsClip.left, fromPos.view.left, fromPos.visible.left, toPos.view.left);
        fromBoundsClip.top = compareAndSetClipBound(
                fromBoundsClip.top, fromPos.view.top, fromPos.visible.top, toPos.view.top);
        fromBoundsClip.right = compareAndSetClipBound(
                fromBoundsClip.right, fromPos.view.right, fromPos.visible.right, toPos.view.left);
        fromBoundsClip.bottom = compareAndSetClipBound(
                fromBoundsClip.bottom, fromPos.view.bottom, fromPos.visible.bottom, toPos.view.top);

        isFromUpdated = true;
    }

    private float compareAndSetClipBound(float origBound, int viewPos, int visiblePos, int offset) {
        // Comparing allowing slack of 1 pixel
        if (-1 <= viewPos - visiblePos && viewPos - visiblePos <= 1) {
            return origBound; // View is fully visible in this direction, no extra bounds
        } else {
            return visiblePos - offset; // Returning 'From' view bound in 'To' view coordinates
        }
    }


    private class LocalAnimationEngine extends AnimationEngine {
        LocalAnimationEngine(@NonNull View view) {
            super(view);
        }

        @Override
        public boolean onStep() {
            if (!positionScroller.isFinished()) {
                positionScroller.computeScroll();
                position = positionScroller.getCurr();
                applyCurrentPosition();

                if (positionScroller.isFinished()) {
                    onAnimationStopped();
                }

                return true;
            }
            return false;
        }
    }


    public interface PositionUpdateListener {
        /**
         * @param position  Position within range {@code [0, 1]}, where {@code 0} is for
         *                  initial (from) position and {@code 1} is for final (to) position.
         * @param isLeaving {@code false} if transitioning from initial to final position
         *                  (entering) or {@code true} for reverse transition.
         */
        void onPositionUpdate(float position, boolean isLeaving);
    }

}
