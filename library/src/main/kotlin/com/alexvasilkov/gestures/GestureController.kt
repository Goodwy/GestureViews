package com.alexvasilkov.gestures

import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.view.*
import android.widget.OverScroller
import java.util.*

class GestureController(private val targetView: View) : View.OnTouchListener {
    companion object {
        private const val STATE_CHANGE_IDLE_DURATION = 200L     // refocus on the image at dragging too, if the finger hasnt moved for this long
        private const val FLING_COEFFICIENT = 0.9f
        private val tmpPointF = PointF()
        private val tmpRectF = RectF()
        private val tmpPointArr = FloatArray(2)
    }

    private val touchSlop: Int
    private val minVelocity: Int
    private val maxVelocity: Int

    private var gestureListener: OnGestureListener? = null
    private val stateListeners = ArrayList<OnStateChangeListener>()

    private val animationEngine: AnimationEngine

    private val gestureDetector: GestureDetector
    private val scaleDetector: ScaleGestureDetector
    private val rotateDetector: RotationGestureDetector

    private var isInterceptTouchCalled = false
    private var isInterceptTouchDisallowed = false
    private var isScrollDetected = false
    private var isScaleDetected = false
    private var isRotationDetected = false

    private var pivotX = java.lang.Float.NaN
    private var pivotY = java.lang.Float.NaN
    private var endPivotX = java.lang.Float.NaN
    private var endPivotY = java.lang.Float.NaN

    private var isStateChangedDuringTouch = false
    private var isRestrictZoomRequested = false
    private var isRestrictRotationRequested = false
    private var isAnimatingInBounds = false

    private val flingScroller: OverScroller
    private val stateScroller: FloatScroller

    private val flingBounds: MovementBounds
    private val stateStart = State()
    private val stateEnd = State()
    private val prevState = State()
    private val stateChangeHandler = Handler()
    val settings: Settings
    val state = State()
    val stateController: StateController

    init {
        val context = targetView.context
        settings = Settings()
        stateController = StateController(settings)

        animationEngine = AnimationEngine(targetView)
        val internalListener = InternalGesturesListener()
        gestureDetector = GestureDetector(context, internalListener)
        scaleDetector = ScaleGestureDetectorFixed(context, internalListener)
        rotateDetector = RotationGestureDetector(internalListener)

        flingScroller = OverScroller(context)
        stateScroller = FloatScroller()

        flingBounds = MovementBounds(settings)

        val configuration = ViewConfiguration.get(context)
        touchSlop = configuration.scaledTouchSlop
        minVelocity = configuration.scaledMinimumFlingVelocity
        maxVelocity = configuration.scaledMaximumFlingVelocity
    }

    fun setOnGesturesListener(listener: OnGestureListener?) {
        gestureListener = listener
    }

    fun addOnStateChangeListener(listener: OnStateChangeListener) {
        stateListeners.add(listener)
    }

    fun removeOnStateChangeListener(listener: OnStateChangeListener) {
        stateListeners.remove(listener)
    }

    fun updateState() {
        stateController.applyZoomPatch(state)
        stateController.applyZoomPatch(prevState)
        stateController.applyZoomPatch(stateStart)
        stateController.applyZoomPatch(stateEnd)

        val reset = stateController.updateState(state)
        if (reset) {
            notifyStateReset()
        } else {
            notifyStateUpdated()
        }
    }

    fun resetState() {
        stopAllAnimations()
        val reset = stateController.resetState(state)
        if (reset) {
            notifyStateReset()
        } else {
            notifyStateUpdated()
        }
    }

    private fun animateKeepInBounds() {
        animateStateTo(state, true)
    }

    private fun animateStateTo(endState: State?, keepInBounds: Boolean = true) {
        if (endState == null) {
            return
        }

        var endStateRestricted: State? = null
        if (keepInBounds) {
            endStateRestricted = stateController.restrictStateBoundsCopy(endState, prevState, pivotX, pivotY)
        }

        if (endStateRestricted == null) {
            endStateRestricted = endState
        }

        if (endStateRestricted == state) {
            return
        }

        stopAllAnimations()

        isAnimatingInBounds = keepInBounds
        stateStart.set(state)
        stateEnd.set(endStateRestricted)

        if (!java.lang.Float.isNaN(pivotX) && !java.lang.Float.isNaN(pivotY)) {
            tmpPointArr[0] = pivotX
            tmpPointArr[1] = pivotY
            MathUtils.computeNewPosition(tmpPointArr, stateStart, stateEnd)
            endPivotX = tmpPointArr[0]
            endPivotY = tmpPointArr[1]
        }

        stateScroller.startScroll(0f, 1f)
        animationEngine.start()
    }

    private fun stopStateAnimation() {
        if (!stateScroller.isFinished) {
            stateScroller.forceFinished()
            onStateAnimationFinished()
        }
    }

    private fun stopFlingAnimation() {
        if (!flingScroller.isFinished) {
            flingScroller.forceFinished(true)
            onFlingAnimationFinished(true)
        }
    }

    private fun stopAllAnimations() {
        stopStateAnimation()
        stopFlingAnimation()
    }

    private fun onStateAnimationFinished() {
        isAnimatingInBounds = false
        pivotX = java.lang.Float.NaN
        pivotY = java.lang.Float.NaN
    }

    private fun onFlingAnimationFinished(forced: Boolean) {
        if (!forced) {
            animateKeepInBounds()
        }
        stateChanged()
    }

    private fun notifyStateUpdated() {
        prevState.set(state)
        stateListeners.forEach {
            it.onStateChanged(state)
        }
    }

    private fun notifyStateReset() {
        stateListeners.forEach {
            it.onStateChanged(state)
        }
        notifyStateUpdated()
    }

    private fun stateChanged() {
        stateChangeHandler.removeCallbacksAndMessages(null)
    }

    fun onInterceptTouch(view: View, event: MotionEvent): Boolean {
        isInterceptTouchCalled = true
        return onTouchInternal(view, event)
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (!isInterceptTouchCalled) {
            onTouchInternal(view, event)
        }

        isInterceptTouchCalled = false
        return settings.getIsEnabled()
    }

    private fun onTouchInternal(view: View, event: MotionEvent): Boolean {
        val viewportEvent = MotionEvent.obtain(event)
        viewportEvent.offsetLocation((-view.paddingLeft).toFloat(), (-view.paddingTop).toFloat())

        gestureDetector.setIsLongpressEnabled(view.isLongClickable)
        var result = gestureDetector.onTouchEvent(viewportEvent)
        scaleDetector.onTouchEvent(viewportEvent)
        rotateDetector.onTouchEvent(viewportEvent)
        result = result || isScaleDetected || isRotationDetected

        stateChangeHandler.removeCallbacksAndMessages(null)
        stateChangeHandler.postDelayed({
            if (flingScroller.isFinished) {
                stateChanged()
            }
        }, STATE_CHANGE_IDLE_DURATION)

        if (isStateChangedDuringTouch) {
            isStateChangedDuringTouch = false

            stateController.restrictStateBounds(state, prevState, pivotX, pivotY, true, false)
            if (state != prevState) {
                notifyStateUpdated()
            }
        }

        if (isRestrictZoomRequested || isRestrictRotationRequested) {
            isRestrictZoomRequested = false
            isRestrictRotationRequested = false

            val restrictedState = stateController.restrictStateBoundsCopy(state, prevState, pivotX, pivotY)
            animateStateTo(restrictedState, false)
        }

        if (viewportEvent.actionMasked == MotionEvent.ACTION_UP || viewportEvent.actionMasked == MotionEvent.ACTION_CANCEL) {
            onUpOrCancel(viewportEvent)
            if (flingScroller.isFinished) {
                stateChanged()
            }
        }

        if (!isInterceptTouchDisallowed && shouldDisallowInterceptTouch(viewportEvent)) {
            isInterceptTouchDisallowed = true

            val parent = view.parent
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        viewportEvent.recycle()
        return result
    }

    private fun shouldDisallowInterceptTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                stateController.getMovementArea(state, tmpRectF)
                val isPannable = State.compare(tmpRectF.width()) > 0 || State.compare(tmpRectF.height()) > 0
                if (isPannable) {
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                return settings.isZoomEnabled || settings.isRotationEnabled
            }
        }

        return false
    }

    private fun onDown(event: MotionEvent): Boolean {
        isInterceptTouchDisallowed = false

        stopFlingAnimation()
        gestureListener?.onDown(event)
        return false
    }

    private fun onUpOrCancel(event: MotionEvent) {
        isScrollDetected = false
        isScaleDetected = false
        isRotationDetected = false

        if (flingScroller.isFinished && !isAnimatingInBounds) {
            animateKeepInBounds()
        }

        gestureListener?.onUpOrCancel(event)
    }

    private fun onSingleTapUp(event: MotionEvent): Boolean {
        if (!settings.isDoubleTapEnabled()) {
            targetView.performClick()
        }

        return gestureListener?.onSingleTapUp(event) ?: false
    }

    private fun onLongPress(event: MotionEvent) {
        if (settings.getIsEnabled()) {
            targetView.performLongClick()
            gestureListener?.onLongPress(event)
        }
    }

    private fun onScroll(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float): Boolean {
        if (!stateScroller.isFinished) {
            return false
        }

        if (!isScrollDetected) {
            isScrollDetected = Math.abs(e2.x - e1.x) > touchSlop || Math.abs(e2.y - e1.y) > touchSlop

            if (isScrollDetected) {
                return false
            }
        }

        if (isScrollDetected) {
            state.translateBy(-dx, -dy)
            isStateChangedDuringTouch = true
        }

        return isScrollDetected
    }

    private fun onFling(vx: Float, vy: Float): Boolean {
        if (!stateScroller.isFinished) {
            return false
        }

        stopFlingAnimation()
        flingBounds.set(state).extend(state.x, state.y)
        flingScroller.fling(
                Math.round(state.x), Math.round(state.y),
                limitFlingVelocity(vx * FLING_COEFFICIENT),
                limitFlingVelocity(vy * FLING_COEFFICIENT),
                Integer.MIN_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE)

        animationEngine.start()
        return true
    }

    private fun limitFlingVelocity(velocity: Float): Int {
        return when {
            Math.abs(velocity) < minVelocity -> 0
            Math.abs(velocity) >= maxVelocity -> Math.signum(velocity).toInt() * maxVelocity
            else -> Math.round(velocity)
        }
    }

    private fun onFlingScroll(dx: Int, dy: Int): Boolean {
        val prevX = state.x
        val prevY = state.y
        var toX = prevX + dx
        var toY = prevY + dy

        flingBounds.restrict(toX, toY, tmpPointF)
        toX = tmpPointF.x
        toY = tmpPointF.y

        state.translateTo(toX, toY)
        return !State.equals(prevX, toX) || !State.equals(prevY, toY)
    }

    private fun onSingleTapConfirmed(event: MotionEvent): Boolean {
        if (settings.isDoubleTapEnabled()) {
            targetView.performClick()
        }

        return gestureListener?.onSingleTapConfirmed(event) ?: false
    }

    private fun onDoubleTapEvent(event: MotionEvent): Boolean {
        if (!settings.isDoubleTapEnabled()) {
            return false
        }

        if (event.actionMasked != MotionEvent.ACTION_UP) {
            return false
        }

        if (isScaleDetected) {
            return false
        }

        if (gestureListener?.onDoubleTap(event) == true) {
            return true
        }

        if (!settings.swallowDoubleTaps) {
            animateStateTo(stateController.toggleMinMaxZoom(state, event.x, event.y))
        }
        return true
    }

    private fun onScaleBegin(): Boolean {
        isScaleDetected = settings.isZoomEnabled
        return isScaleDetected
    }

    private fun onScale(detector: ScaleGestureDetector): Boolean {
        if (!settings.isZoomEnabled || !stateScroller.isFinished) {
            return false
        }

        val scaleFactor = detector.scaleFactor
        pivotX = detector.focusX
        pivotY = detector.focusY
        state.zoomBy(scaleFactor, pivotX, pivotY)
        isStateChangedDuringTouch = true
        return true
    }

    private fun onScaleEnd() {
        isScaleDetected = false
        isRestrictZoomRequested = true
    }

    private fun onRotationBegin(): Boolean {
        isRotationDetected = settings.isRotationEnabled
        return isRotationDetected
    }

    private fun onRotate(detector: RotationGestureDetector): Boolean {
        if (!settings.isRotationEnabled || !stateScroller.isFinished) {
            return false
        }

        pivotX = detector.focusX
        pivotY = detector.focusY
        state.rotateBy(detector.getRotationDelta(), pivotX, pivotY)
        isStateChangedDuringTouch = true
        return true
    }

    private fun onRotationEnd() {
        isRotationDetected = false
        isRestrictRotationRequested = true
    }

    private inner class AnimationEngine(val view: View) : Runnable {
        private val FRAME_TIME = 10L

        fun onStep(): Boolean {
            var shouldProceed = false

            if (!flingScroller.isFinished) {
                val prevX = flingScroller.currX
                val prevY = flingScroller.currY

                if (flingScroller.computeScrollOffset()) {
                    val dx = flingScroller.currX - prevX
                    val dy = flingScroller.currY - prevY

                    if (!onFlingScroll(dx, dy)) {
                        stopFlingAnimation()
                    }

                    shouldProceed = true
                }

                if (flingScroller.isFinished) {
                    onFlingAnimationFinished(false)
                }
            }

            if (!stateScroller.isFinished) {
                stateScroller.computeScroll()
                val factor = stateScroller.curr

                if (java.lang.Float.isNaN(pivotX) || java.lang.Float.isNaN(pivotY) || java.lang.Float.isNaN(endPivotX) || java.lang.Float.isNaN(endPivotY)) {
                    MathUtils.interpolate(state, stateStart, stateEnd, factor)
                } else {
                    MathUtils.interpolate(state, stateStart, pivotX, pivotY, stateEnd, endPivotX, endPivotY, factor)
                }

                shouldProceed = true

                if (stateScroller.isFinished) {
                    onStateAnimationFinished()
                }
            }

            if (shouldProceed) {
                notifyStateUpdated()
            }

            return shouldProceed
        }

        override fun run() {
            if (onStep()) {
                scheduleNextStep()
            }
        }

        private fun scheduleNextStep() {
            view.removeCallbacks(this)
            view.postOnAnimationDelayed(this, FRAME_TIME)
        }

        fun start() {
            scheduleNextStep()
        }
    }

    interface OnStateChangeListener {
        fun onStateChanged(state: State)
    }

    interface OnGestureListener {
        fun onDown(event: MotionEvent)

        fun onUpOrCancel(event: MotionEvent)

        fun onSingleTapUp(event: MotionEvent): Boolean

        fun onSingleTapConfirmed(event: MotionEvent): Boolean

        fun onLongPress(event: MotionEvent)

        fun onDoubleTap(event: MotionEvent): Boolean
    }

    private inner class InternalGesturesListener : GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, ScaleGestureDetector.OnScaleGestureListener, RotationGestureDetector.OnRotationGestureListener {

        override fun onSingleTapConfirmed(event: MotionEvent) = this@GestureController.onSingleTapConfirmed(event)

        override fun onDoubleTap(event: MotionEvent) = false

        override fun onDoubleTapEvent(event: MotionEvent) = this@GestureController.onDoubleTapEvent(event)

        override fun onDown(event: MotionEvent) = this@GestureController.onDown(event)

        override fun onShowPress(event: MotionEvent) {}

        override fun onSingleTapUp(event: MotionEvent) = this@GestureController.onSingleTapUp(event)

        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float) = this@GestureController.onScroll(e1, e2, distanceX, distanceY)

        override fun onLongPress(event: MotionEvent) {
            this@GestureController.onLongPress(event)
        }

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float) = this@GestureController.onFling(velocityX, velocityY)

        override fun onRotate(detector: RotationGestureDetector) = this@GestureController.onRotate(detector)

        override fun onRotationBegin(detector: RotationGestureDetector) = this@GestureController.onRotationBegin()

        override fun onRotationEnd(detector: RotationGestureDetector) {
            this@GestureController.onRotationEnd()
        }

        override fun onScale(detector: ScaleGestureDetector) = this@GestureController.onScale(detector)

        override fun onScaleBegin(detector: ScaleGestureDetector) = this@GestureController.onScaleBegin()

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            this@GestureController.onScaleEnd()
        }
    }
}
