package com.livestream.youtube.model.overlay

/**
 * Image overlay element with customizable scaling and positioning.
 */
data class ImageOverlayElement(
    var imagePath: String = "",
    var scaleType: ScaleType = ScaleType.FIT_CENTER,
    var cornerRadius: Float = 0f,
    var borderWidth: Int = 0,
    var borderColor: Int = 0
) : OverlayElement() {

    override val type: String
        get() = TYPE_IMAGE

    override fun copy(): ImageOverlayElement {
        return ImageOverlayElement(
            id = id,
            x = x,
            y = y,
            width = width,
            height = height,
            rotation = rotation,
            opacity = opacity,
            isVisible = isVisible,
            zIndex = zIndex,
            imagePath = imagePath,
            scaleType = scaleType,
            cornerRadius = cornerRadius,
            borderWidth = borderWidth,
            borderColor = borderColor
        )
    }

    enum class ScaleType {
        FIT_CENTER,    // Scale uniformly to fit within bounds
        CENTER_CROP,   // Scale uniformly to fill, crop excess
        STRETCH,       // Stretch to fill bounds
        CENTER         // No scaling, centered
    }
}
