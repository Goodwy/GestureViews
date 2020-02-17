package com.alexvasilkov.gestures

class Settings {
    companion object {
        private const val MAX_ZOOM = 5f
        private const val DOUBLE_TAP_ZOOM = 3f
        const val OVERZOOM_FACTOR = 2f
        const val ANIMATIONS_DURATION = 300L
    }

    var viewportWidth = 0
    var viewportHeight = 0
    var imageWidth = 0f
    var imageHeight = 0f
    var maxZoom = MAX_ZOOM
    var doubleTapZoom = DOUBLE_TAP_ZOOM
    var isZoomEnabled = true
    var isRotationEnabled = false
    var swallowDoubleTaps = false

    fun getIsEnabled() = isZoomEnabled || isRotationEnabled

    fun setViewport(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    fun setImageSize(width: Float, height: Float) {
        imageWidth = width
        imageHeight = height
    }

    fun isDoubleTapEnabled() = isZoomEnabled

    fun hasImageSize() = imageWidth != 0f && imageHeight != 0f

    fun hasViewportSize() = viewportWidth != 0 && viewportHeight != 0
}
