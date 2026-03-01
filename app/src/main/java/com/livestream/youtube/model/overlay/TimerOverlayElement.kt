package com.livestream.youtube.model.overlay

import android.graphics.Color
import java.util.UUID

data class TimerOverlayElement(
    override val id: String = UUID.randomUUID().toString(),
    override var x: Float = 0.1f,
    override var y: Float = 0.1f,
    override var width: Float = 0.2f,
    override var height: Float = 0.1f,
    override var rotation: Float = 0f,
    override var opacity: Float = 1f,
    override var isVisible: Boolean = true,
    override var zIndex: Int = 0,
    var format: TimerFormat = TimerFormat.HH_MM_SS,
    var direction: TimerDirection = TimerDirection.UP,
    var startTime: Long = System.currentTimeMillis(),
    var fontFamily: String = "monospace",
    var fontSize: Int = 32,
    var textColor: Int = Color.WHITE,
    var backgroundColor: Int = Color.TRANSPARENT,
    var hasShadow: Boolean = true,
    var showLabels: Boolean = false,
    var labelText: String = "LIVE"
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
        get() = TYPE_TIMER

    override fun copy(): TimerOverlayElement {
        // Create a new instance directly to avoid recursive call to this method
        return TimerOverlayElement(
            id = this.id,
            x = this.x,
            y = this.y,
            width = this.width,
            height = this.height,
            rotation = this.rotation,
            opacity = this.opacity,
            isVisible = this.isVisible,
            zIndex = this.zIndex,
            format = this.format,
            direction = this.direction,
            startTime = this.startTime,
            fontFamily = this.fontFamily,
            fontSize = this.fontSize,
            textColor = this.textColor,
            backgroundColor = this.backgroundColor,
            hasShadow = this.hasShadow,
            showLabels = this.showLabels,
            labelText = this.labelText
        )
    }

    enum class TimerFormat(val pattern: String, val displayName: String) {
        HH_MM_SS("HH:MM:SS", "Hours:Minutes:Seconds"),
        MM_SS("MM:SS", "Minutes:Seconds"),
        HH_MM("HH:MM", "Hours:Minutes"),
        SS("SS", "Seconds Only"),
        CUSTOM("%1\$tH:%1\$tM:%1\$tS", "Custom Format")
    }

    enum class TimerDirection {
        UP,
        DOWN
    }
}
