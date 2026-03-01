package com.livestream.youtube.model.overlay

import android.graphics.Color
import java.util.UUID

data class ChatOverlayElement(
    override val id: String = UUID.randomUUID().toString(),
    override var x: Float = 0.02f,
    override var y: Float = 0.5f,
    override var width: Float = 0.3f,
    override var height: Float = 0.45f,
    override var rotation: Float = 0f,
    override var opacity: Float = 1f,
    override var isVisible: Boolean = true,
    override var zIndex: Int = 0,
    var maxMessages: Int = 50,
    var showTimestamps: Boolean = true,
    var fontFamily: String = "sans-serif",
    var fontSize: Int = 14,
    var textColor: Int = Color.WHITE,
    var backgroundColor: Int = Color.argb(128, 0, 0, 0),
    var hasShadow: Boolean = true,
    var showBadges: Boolean = true,
    var messageAnimation: MessageAnimation = MessageAnimation.SCROLL
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
        get() = TYPE_CHAT

    override fun copy(): ChatOverlayElement {
        return ChatOverlayElement(
            id = this.id,
            x = this.x,
            y = this.y,
            width = this.width,
            height = this.height,
            rotation = this.rotation,
            opacity = this.opacity,
            isVisible = this.isVisible,
            zIndex = this.zIndex,
            maxMessages = this.maxMessages,
            showTimestamps = this.showTimestamps,
            fontFamily = this.fontFamily,
            fontSize = this.fontSize,
            textColor = this.textColor,
            backgroundColor = this.backgroundColor,
            hasShadow = this.hasShadow,
            showBadges = this.showBadges,
            messageAnimation = this.messageAnimation
        )
    }

    enum class MessageAnimation {
        SCROLL,
        FADE,
        NONE
    }

    companion object {
        fun createDefault(): ChatOverlayElement {
            return ChatOverlayElement()
        }
    }
}
