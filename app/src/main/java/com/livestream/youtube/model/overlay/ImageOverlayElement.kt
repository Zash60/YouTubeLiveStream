package com.livestream.youtube.model.overlay

import java.util.UUID

/**
 * Image overlay element with customizable scaling and positioning.
 */
data class ImageOverlayElement(
    val id: String = UUID.randomUUID().toString(),
    var x: Float = 0.1f,
    var y: Float = 0.1f,
    var width: Float = 0.2f,
    var height: Float = 0.1f,
    var rotation: Float = 0f,
    var opacity: Float = 1f,
    var isVisible: Boolean = true,
    var zIndex: Int = 0,
    var imagePath: String = "",
    var scaleType: ScaleType = ScaleType.FIT_CENTER,
    var cornerRadius: Float = 0f,
    var borderWidth: Int = 0,
    var borderColor: Int = 0
) : OverlayElement(
    id = id,
    x = x,
    y = y,
    width = width,
    height = height,
    rotation = rotation,
    opacity = opacity,
    isVisible = isVisible,
    zIndex = zIndex
) {

    override val type: String
        get() = TYPE_IMAGE

    enum class ScaleType {
        FIT_CENTER,    // Scale uniformly to fit within bounds
        CENTER_CROP,   // Scale uniformly to fill, crop excess
        STRETCH,       // Stretch to fill bounds
        CENTER         // No scaling, centered
    }
}
