package com.livestream.youtube.model.overlay

import android.graphics.Color
import java.util.UUID

/**
 * Timer overlay element with customizable format and styling.
 */
data class TimerOverlayElement(
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
) : OverlayElement() {

    override val type: String
        get() = TYPE_TIMER

    override fun copy(): TimerOverlayElement {
        return TimerOverlayElement(
            id = id,
            x = x,
            y = y,
            width = width,
            height = height,
            rotation = rotation,
            opacity = opacity,
            isVisible = isVisible,
            zIndex = zIndex,
            format = format,
            direction = direction,
            startTime = startTime,
            fontFamily = fontFamily,
            fontSize = fontSize,
            textColor = textColor,
            backgroundColor = backgroundColor,
            hasShadow = hasShadow,
            showLabels = showLabels,
            labelText = labelText
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
        UP,     // Counts up from start time
        DOWN    // Counts down to zero
    }
}
