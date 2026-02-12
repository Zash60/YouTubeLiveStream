package com.livestream.youtube.model.overlay

import android.graphics.Color
import java.util.UUID

data class ViewerCountOverlayElement(
    override val id: String = UUID.randomUUID().toString(),
    override var x: Float = 0.1f,
    override var y: Float = 0.1f,
    override var width: Float = 0.2f,
    override var height: Float = 0.1f,
    override var rotation: Float = 0f,
    override var opacity: Float = 1f,
    override var isVisible: Boolean = true,
    override var zIndex: Int = 0,
    var fontFamily: String = "monospace",
    var fontSize: Int = 24,
    var textColor: Int = Color.WHITE,
    var backgroundColor: Int = Color.TRANSPARENT,
    var hasShadow: Boolean = true,
    var showIcon: Boolean = true,
    var iconType: IconType = IconType.EYE,
    var viewerValue: String = "0"
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

    override fun copy(): ViewerCountOverlayElement {
        // Create a new instance directly to avoid recursive call to this method
        return ViewerCountOverlayElement(
            id = this.id,
            x = this.x,
            y = this.y,
            width = this.width,
            height = this.height,
            rotation = this.rotation,
            opacity = this.opacity,
            isVisible = this.isVisible,
            zIndex = this.zIndex,
            fontFamily = this.fontFamily,
            fontSize = this.fontSize,
            textColor = this.textColor,
            backgroundColor = this.backgroundColor,
            hasShadow = this.hasShadow,
            showIcon = this.showIcon,
            iconType = this.iconType,
            viewerValue = this.viewerValue
        )
    }

    enum class IconType(val emoji: String) {
        EYE("👁️"),
        USERS("👥"),
        LIVE("🔴"),
        NONE("")
    }

    companion object {
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
