package com.livestream.youtube.overlay

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.livestream.youtube.model.overlay.*

/**
 * Canvas view for editing overlay elements with touch support.
 * Handles drag, resize, and selection of elements.
 */
class OverlayCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : OverlayRenderer(context) {

    interface OnElementInteractionListener {
        fun onElementSelected(element: OverlayElement?)
        fun onElementMoved(elementId: String, newX: Float, newY: Float)
        fun onElementResized(elementId: String, newWidth: Float, newHeight: Float)
        fun onEditModeChanged(enabled: Boolean)
    }

    private var listener: OnElementInteractionListener? = null
    private var selectedElement: OverlayElement? = null
    private var isDragging = false
    private var isResizing = false
    private var resizeHandleIndex = -1

    private val touchStart = PointF()
    private val elementStart = PointF()
    private val elementStartSize = PointF()

    private val handleSize = 48f // Hit area for resize handles

    /**
     * Sets the interaction listener.
     */
    fun setOnElementInteractionListener(listener: OnElementInteractionListener) {
        this.listener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditMode()) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStart.set(event.x, event.y)

                // Check if touching a resize handle
                if (selectedElement != null) {
                    val handleIndex = getTouchedHandleIndex(event.x, event.y)
                    if (handleIndex >= 0) {
                        isResizing = true
                        resizeHandleIndex = handleIndex
                        elementStartSize.set(selectedElement!!.width, selectedElement!!.height)
                        return true
                    }
                }

                // Check if touching an element
                val touchedElement = findElementAtPoint(event.x, event.y)
                if (touchedElement != null) {
                    selectedElement = touchedElement
                    setSelectedElement(touchedElement.id)
                    listener?.onElementSelected(touchedElement)
                    isDragging = true
                    elementStart.set(touchedElement.x, touchedElement.y)
                    return true
                } else {
                    selectedElement = null
                    setSelectedElement(null)
                    listener?.onElementSelected(null)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging && selectedElement != null) {
                    val dx = (event.x - touchStart.x) / width
                    val dy = (event.y - touchStart.y) / height

                    val newX = (elementStart.x + dx).coerceIn(0f, 1f - selectedElement!!.width)
                    val newY = (elementStart.y + dy).coerceIn(0f, 1f - selectedElement!!.height)

                    listener?.onElementMoved(selectedElement!!.id, newX, newY)
                }

                if (isResizing && selectedElement != null) {
                    val dx = (event.x - touchStart.x) / width
                    val dy = (event.y - touchStart.y) / height

                    val (newWidth, newHeight) = when (resizeHandleIndex) {
                        0 -> Pair( // Top-left
                            (elementStartSize.x - dx).coerceIn(0.05f, 1f),
                            (elementStartSize.y - dy).coerceIn(0.05f, 1f)
                        )
                        1 -> Pair( // Top-right
                            (elementStartSize.x + dx).coerceIn(0.05f, 1f),
                            (elementStartSize.y - dy).coerceIn(0.05f, 1f)
                        )
                        2 -> Pair( // Bottom-left
                            (elementStartSize.x - dx).coerceIn(0.05f, 1f),
                            (elementStartSize.y + dy).coerceIn(0.05f, 1f)
                        )
                        3 -> Pair( // Bottom-right
                            (elementStartSize.x + dx).coerceIn(0.05f, 1f),
                            (elementStartSize.y + dy).coerceIn(0.05f, 1f)
                        )
                        else -> Pair(elementStartSize.x, elementStartSize.y)
                    }

                    listener?.onElementResized(selectedElement!!.id, newWidth, newHeight)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isResizing = false
                resizeHandleIndex = -1
            }
        }

        return true
    }

    /**
     * Finds the element at the given point.
     */
    private fun findElementAtPoint(x: Float, y: Float): OverlayElement? {
        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()

        // Search in reverse z-index order (top elements first)
        return getAllElements().sortedByDescending { it.zIndex }.firstOrNull { element ->
            val left = element.x * screenWidth
            val top = element.y * screenHeight
            val right = left + (element.width * screenWidth)
            val bottom = top + (element.height * screenHeight)

            x >= left && x <= right && y >= top && y <= bottom
        }
    }

    /**
     * Gets the index of the resize handle at the given point.
     * Returns -1 if not touching any handle.
     */
    private fun getTouchedHandleIndex(x: Float, y: Float): Int {
        if (selectedElement == null) return -1

        val bounds = getElementBounds(selectedElement!!)
        val handleHalfSize = handleSize / 2

        val handles = listOf(
            PointF(bounds.left, bounds.top),      // Top-left
            PointF(bounds.right, bounds.top),     // Top-right
            PointF(bounds.left, bounds.bottom),   // Bottom-left
            PointF(bounds.right, bounds.bottom)   // Bottom-right
        )

        handles.forEachIndexed { index, handle ->
            if (x >= handle.x - handleHalfSize && x <= handle.x + handleHalfSize &&
                y >= handle.y - handleHalfSize && y <= handle.y + handleHalfSize) {
                return index
            }
        }

        return -1
    }

    /**
     * Gets all elements from the manager.
     */
    private fun getAllElements(): List<OverlayElement> {
        // Elements are stored in the parent class
        return emptyList() // Will be overridden by manager
    }

    /**
     * Updates the selected element.
     */
    fun updateSelectedElement(element: OverlayElement?) {
        selectedElement = element
        setSelectedElement(element?.id)
    }

    /**
     * Clears selection.
     */
    fun clearSelection() {
        selectedElement = null
        setSelectedElement(null)
        listener?.onElementSelected(null)
    }
}
