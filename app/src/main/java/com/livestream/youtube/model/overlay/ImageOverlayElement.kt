package com.livestream.youtube.model.overlay

import java.util.UUID

data class ImageOverlayElement(
    override val id: String = UUID.randomUUID().toString(),
    override var x: Float = 0.1f,
    override var y: Float = 0.1f,
    override var width: Float = 0.2f,
    override var height: Float = 0.1f,
    override var rotation: Float = 0f,
    override var opacity: Float = 1f,
    override var isVisible: Boolean = true,
    override var zIndex: Int = 0,
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

    override fun copy(): ImageOverlayElement {
        // Create a new instance directly to avoid recursive call to this method
        return ImageOverlayElement(
            id = this.id,
            x = this.x,
            y = this.y,
            width = this.width,
            height = this.height,
            rotation = this.rotation,
            opacity = this.opacity,
            isVisible = this.isVisible,
            zIndex = this.zIndex,
            imagePath = this.imagePath,
            scaleType = this.scaleType,
            cornerRadius = this.cornerRadius,
            borderWidth = this.borderWidth,
            borderColor = this.borderColor
        )
    }

    enum class ScaleType {
        FIT_CENTER,
        CENTER_CROP,
        STRETCH,
        CENTER
    }
}
