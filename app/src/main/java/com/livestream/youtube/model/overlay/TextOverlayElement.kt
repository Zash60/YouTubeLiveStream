package com.livestream.youtube.model.overlay

import android.graphics.Color
import java.util.UUID

data class TextOverlayElement(
    override val id: String = UUID.randomUUID().toString(),
    override var x: Float = 0.1f,
    override var y: Float = 0.1f,
    override var width: Float = 0.2f,
    override var height: Float = 0.1f,
    override var rotation: Float = 0f,
    override var opacity: Float = 1f,
    override var isVisible: Boolean = true,
    override var zIndex: Int = 0,
    var text: String = "Sample Text",
    var fontFamily: String = "sans-serif-medium",
    var fontSize: Int = 24,
    var textColor: Int = Color.WHITE,
    var backgroundColor: Int = Color.TRANSPARENT,
    var hasShadow: Boolean = true,
    var shadowColor: Int = Color.BLACK,
    var shadowRadius: Float = 4f,
    var shadowDx: Float = 2f,
    var shadowDy: Float = 2f,
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var alignment: TextAlignment = TextAlignment.LEFT
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
        get() = TYPE_TEXT

    override fun copy(): TextOverlayElement {
        // Create a new instance directly to avoid recursive call to this method
        return TextOverlayElement(
            id = this.id,
            x = this.x,
            y = this.y,
            width = this.width,
            height = this.height,
            rotation = this.rotation,
            opacity = this.opacity,
            isVisible = this.isVisible,
            zIndex = this.zIndex,
            text = this.text,
            fontFamily = this.fontFamily,
            fontSize = this.fontSize,
            textColor = this.textColor,
            backgroundColor = this.backgroundColor,
            hasShadow = this.hasShadow,
            shadowColor = this.shadowColor,
            shadowRadius = this.shadowRadius,
            shadowDx = this.shadowDx,
            shadowDy = this.shadowDy,
            isBold = this.isBold,
            isItalic = this.isItalic,
            alignment = this.alignment
        )
    }

    enum class TextAlignment {
        LEFT, CENTER, RIGHT
    }
}
