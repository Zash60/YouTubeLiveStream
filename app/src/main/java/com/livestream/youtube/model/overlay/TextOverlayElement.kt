package com.livestream.youtube.model.overlay

import android.graphics.Color
import java.util.UUID

/**
 * Text overlay element with customizable font, color, and styling.
 */
data class TextOverlayElement(
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
) : OverlayElement() {

    override val type: String
        get() = TYPE_TEXT

    override fun copy(): TextOverlayElement {
        return TextOverlayElement(
            id = id,
            x = x,
            y = y,
            width = width,
            height = height,
            rotation = rotation,
            opacity = opacity,
            isVisible = isVisible,
            zIndex = zIndex,
            text = text,
            fontFamily = fontFamily,
            fontSize = fontSize,
            textColor = textColor,
            backgroundColor = backgroundColor,
            hasShadow = hasShadow,
            shadowColor = shadowColor,
            shadowRadius = shadowRadius,
            shadowDx = shadowDx,
            shadowDy = shadowDy,
            isBold = isBold,
            isItalic = isItalic,
            alignment = alignment
        )
    }

    enum class TextAlignment {
        LEFT, CENTER, RIGHT
    }
}
