package com.livestream.youtube.model.overlay

/**
 * Container class that holds a complete overlay configuration
 * including all elements and metadata.
 */
data class OverlayConfiguration(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New Overlay",
    val elements: List<OverlayElement> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false
) {
    /**
     * Returns elements sorted by z-index.
     */
    fun getSortedElements(): List<OverlayElement> {
        return elements.sortedBy { it.zIndex }
    }

    /**
     * Finds an element by ID.
     */
    fun findElementById(id: String): OverlayElement? {
        return elements.find { it.id == id }
    }

    /**
     * Creates a copy with updated element.
     */
    fun withUpdatedElement(element: OverlayElement): OverlayConfiguration {
        return copy(
            elements = elements.map { if (it.id == element.id) element else it },
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Creates a copy with added element.
     */
    fun withAddedElement(element: OverlayElement): OverlayConfiguration {
        return copy(
            elements = elements + element,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Creates a copy with removed element.
     */
    fun withRemovedElement(elementId: String): OverlayConfiguration {
        return copy(
            elements = elements.filter { it.id != elementId },
            updatedAt = System.currentTimeMillis()
        )
    }
}
