package com.livestream.youtube.overlay

import com.livestream.youtube.model.overlay.OverlayConfiguration
import com.livestream.youtube.model.overlay.OverlayElement
import com.livestream.youtube.model.overlay.TextOverlayElement
import kotlin.math.roundToInt

/**
 * Manages overlay elements with CRUD operations and undo/redo support.
 */
class OverlayElementManager {

    private var configuration: OverlayConfiguration = OverlayConfiguration()
    private val undoStack = mutableListOf<OverlayConfiguration>()
    private val redoStack = mutableListOf<OverlayConfiguration>()
    private val maxUndoSteps = 50

    /**
     * Gets the current configuration.
     */
    fun getConfiguration(): OverlayConfiguration = configuration

    /**
     * Sets the configuration.
     */
    fun setConfiguration(config: OverlayConfiguration, saveUndo: Boolean = true) {
        if (saveUndo) {
            pushUndoState()
        }
        configuration = config
    }

    /**
     * Adds a new element.
     */
    fun addElement(element: OverlayElement, saveUndo: Boolean = true): Boolean {
        if (saveUndo) {
            pushUndoState()
        }
        configuration = configuration.withAddedElement(element)
        return true
    }

    /**
     * Removes an element by ID.
     */
    fun removeElement(elementId: String, saveUndo: Boolean = true): Boolean {
        if (configuration.findElementById(elementId) == null) {
            return false
        }
        if (saveUndo) {
            pushUndoState()
        }
        configuration = configuration.withRemovedElement(elementId)
        return true
    }

    /**
     * Updates an element.
     */
    fun updateElement(
        elementId: String,
        update: (OverlayElement) -> OverlayElement,
        saveUndo: Boolean = true
    ): Boolean {
        val element = configuration.findElementById(elementId) ?: return false
        if (saveUndo) {
            pushUndoState()
        }
        configuration = configuration.withUpdatedElement(update(element))
        return true
    }

    /**
     * Moves an element to a new position.
     */
    fun moveElement(elementId: String, newX: Float, newY: Float, saveUndo: Boolean = true): Boolean {
        val element = configuration.findElementById(elementId) ?: return false
        if (saveUndo) {
            pushUndoState()
        }
        // Directly modify the element (it's a data class with var properties)
        element.x = newX
        element.y = newY
        // Only recreate configuration if we need to save undo
        if (saveUndo) {
            configuration = configuration.withUpdatedElement(element)
        }
        return true
    }

    /**
     * Resizes an element.
     */
    fun resizeElement(
        elementId: String,
        newWidth: Float,
        newHeight: Float,
        saveUndo: Boolean = true
    ): Boolean {
        val element = configuration.findElementById(elementId) ?: return false
        if (saveUndo) {
            pushUndoState()
        }
        // Directly modify the element (it's a data class with var properties)
        element.width = newWidth
        element.height = newHeight
        // Only recreate configuration if we need to save undo
        if (saveUndo) {
            configuration = configuration.withUpdatedElement(element)
        }
        return true
    }

    /**
     * Sets element visibility.
     */
    fun setElementVisibility(elementId: String, visible: Boolean, saveUndo: Boolean = true): Boolean {
        val element = configuration.findElementById(elementId) ?: return false
        if (saveUndo) {
            pushUndoState()
        }
        element.isVisible = visible
        configuration = configuration.withUpdatedElement(element)
        return true
    }

    /**
     * Brings element to front (highest z-index).
     */
    fun bringToFront(elementId: String, saveUndo: Boolean = true): Boolean {
        val element = configuration.findElementById(elementId) ?: return false
        if (saveUndo) {
            pushUndoState()
        }
        val maxZ = configuration.elements.maxOfOrNull { it.zIndex } ?: 0
        element.zIndex = maxZ + 1
        configuration = configuration.withUpdatedElement(element)
        return true
    }

    /**
     * Sends element to back (lowest z-index).
     */
    fun sendToBack(elementId: String, saveUndo: Boolean = true): Boolean {
        val element = configuration.findElementById(elementId) ?: return false
        if (saveUndo) {
            pushUndoState()
        }
        val minZ = configuration.elements.minOfOrNull { it.zIndex } ?: 0
        element.zIndex = minZ - 1
        configuration = configuration.withUpdatedElement(element)
        return true
    }

    /**
     * Gets element by ID.
     */
    fun getElement(elementId: String): OverlayElement? {
        return configuration.findElementById(elementId)
    }

    /**
     * Gets all elements.
     */
    fun getAllElements(): List<OverlayElement> {
        return configuration.elements
    }

    /**
     * Gets visible elements sorted by z-index.
     */
    fun getVisibleElements(): List<OverlayElement> {
        return configuration.getSortedElements().filter { it.isVisible }
    }

    /**
     * Performs undo.
     */
    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        redoStack.add(configuration)
        configuration = undoStack.removeLast()
        return true
    }

    /**
     * Performs redo.
     */
    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        undoStack.add(configuration)
        configuration = redoStack.removeLast()
        return true
    }

    /**
     * Checks if undo is available.
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()

    /**
     * Checks if redo is available.
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Clears undo/redo history.
     */
    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    /**
     * Creates a copy of current state for undo.
     */
    private fun pushUndoState() {
        undoStack.add(configuration.copy())
        if (undoStack.size > maxUndoSteps) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    /**
     * Gets the count of elements.
     */
    fun getElementCount(): Int = configuration.elements.size

    /**
     * Removes all elements.
     */
    fun clearAllElements(saveUndo: Boolean = true) {
        if (saveUndo) {
            pushUndoState()
        }
        configuration = configuration.copy(elements = emptyList())
    }

    /**
     * Duplicates an element.
     */
    fun duplicateElement(elementId: String, saveUndo: Boolean = true): OverlayElement? {
        val element = configuration.findElementById(elementId) ?: return null
        if (saveUndo) {
            pushUndoState()
        }
        val newElement = element.copy()
        // Offset position slightly
        newElement.x += 0.05f
        newElement.y += 0.05f
        newElement.zIndex += 1
        configuration = configuration.withAddedElement(newElement)
        return newElement
    }

    // ==================== Snap-to-Grid ====================

    private var snapToGridEnabled = false
    private var gridSize = 0.05f // 5% grid

    /**
     * Enables or disables snap-to-grid.
     */
    fun setSnapToGridEnabled(enabled: Boolean) {
        snapToGridEnabled = enabled
    }

    /**
     * Sets the grid size (as percentage of screen, e.g., 0.05f for 5%).
     */
    fun setGridSize(size: Float) {
        gridSize = size.coerceIn(0.01f, 0.2f)
    }

    /**
     * Snaps a position to the nearest grid point.
     */
    fun snapToGrid(x: Float, y: Float): Pair<Float, Float> {
        if (!snapToGridEnabled) return Pair(x, y)
        return Pair(
            (x / gridSize).roundToInt() * gridSize,
            (y / gridSize).roundToInt() * gridSize
        )
    }

    /**
     * Moves element with snap-to-grid if enabled.
     */
    fun moveElementWithSnap(elementId: String, newX: Float, newY: Float, saveUndo: Boolean = true): Boolean {
        val (snappedX, snappedY) = snapToGrid(newX, newY)
        return moveElement(elementId, snappedX, snappedY, saveUndo)
    }

    /**
     * Gets current grid settings.
     */
    fun isSnapToGridEnabled(): Boolean = snapToGridEnabled
    fun getGridSize(): Float = gridSize
}
