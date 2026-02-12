package com.livestream.youtube.model.overlay

import android.graphics.Color
import java.util.UUID

/**
 * Viewer count overlay element for displaying live viewer count.
 */
data class ViewerCountOverlayElement(
    val id: String = UUID.randomUUID().toString(),
    var x: Float = 0.1f,
    var y: Float = 0.1f,
    var width: Float = 0.2f,
    var height: Float = 0.1f,
    var rotation: Float = 0f,
    var opacity: Float = 1f,
    var isVisible: Boolean = true,
    var zIndex: Int = 0,
    var fontFamily: String = "monospace",
    var fontSize: Int = 24,
    var textColor: Int = Color.WHITE,
    var backgroundColor: Int = Color.TRANSPARENT,
    var hasShadow: Boolean = true,
    var showIcon: Boolean = true,
    var iconType: IconType = IconType.EYE,
    var viewerValue: String = "0" // Dynamic viewer count value
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
        get() = TYPE_VIEWER_COUNT

    enum class IconType(val emoji: String) {
        EYE("👁️"),
        USERS("👥"),
        LIVE("🔴"),
        NONE("")
    }

    companion object {
        /**
         * Creates a default viewer count element positioned at top-right.
         */
        fun createDefault(): ViewerCountOverlayElement {
            return ViewerCountOverlayElement(
                x = 0.85f,
                y = 0.02f,
                width = 0.14f,
                height = 0.05f
            )
        }
    }
}
