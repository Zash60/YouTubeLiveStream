package com.livestream.youtube.model.overlay

import java.util.UUID

/**
 * Base class for all overlay elements.
 * Provides common properties for positioning, sizing, and visibility.
 */
abstract class OverlayElement(
    open val id: String = UUID.randomUUID().toString(),
    open var x: Float = 0.1f,
    open var y: Float = 0.1f,
    open var width: Float = 0.2f,
    open var height: Float = 0.1f,
    open var rotation: Float = 0f,
    open var opacity: Float = 1f,
    open var isVisible: Boolean = true,
    open var zIndex: Int = 0
) {
    /**
     * Returns the type identifier for serialization.
     */
    abstract val type: String

    /**
     * Creates a deep copy of this element.
     */
    abstract fun copy(): OverlayElement

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_TIMER = "timer"
        const val TYPE_VIEWER_COUNT = "viewer_count"
        const val TYPE_CHAT = "chat"
    }
}
