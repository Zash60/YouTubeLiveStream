package com.livestream.youtube.model.overlay

import android.graphics.Color
import java.util.UUID

/**
 * Text overlay element with customizable font, color, and styling.
 */
data class TextOverlayElement(
    val id: String = UUID.randomUUID().toString(),
    var x: Float = 0.1f,
    var y: Float = 0.1f,
    var width: Float = 0.2f,
    var height: Float = 0.1f,
    var rotation: Float = 0f,
    var opacity: Float = 1f,
    var isVisible: Boolean = true,
    var zIndex: Int = 0,
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

    enum class TextAlignment {
        LEFT, CENTER, RIGHT
    }
}
