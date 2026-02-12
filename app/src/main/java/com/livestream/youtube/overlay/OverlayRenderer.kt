package com.livestream.youtube.overlay

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.SizeF
import android.view.View
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.livestream.youtube.R
import com.livestream.youtube.model.overlay.*
import kotlinx.coroutines.*
import java.io.File

/**
 * Renders overlay elements to a canvas.
 * Supports text, image, and timer elements with various styling options.
 * Optimized for smooth performance during editing.
 */
open class OverlayRenderer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val elements = mutableListOf<OverlayElement>()
    private var sortedElements: List<OverlayElement> = emptyList()
    private var selectedElementId: String? = null
    private var isEditMode: Boolean = false
    private var needsSorting = false

    // Reusable paint objects to avoid GC
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    // Reusable RectF for drawing
    private val tempRectF = RectF()
    private val tempRect = Rect()
    private val tempMatrix = Matrix()

    private val imageCache = mutableMapOf<String, Bitmap>()
    private val scope: CoroutineScope by lazy { CoroutineScope(Dispatchers.Main + SupervisorJob()) }
    private var pendingImages = mutableSetOf<String>()

    init {
        // Enable hardware acceleration for better performance
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Sets the elements to render.
     */
    fun setElements(newElements: List<OverlayElement>) {
        elements.clear()
        elements.addAll(newElements.filter { it.isVisible })
        needsSorting = true
        preloadImages()
        invalidate()
    }

    /**
     * Sets the selected element ID.
     */
    fun setSelectedElement(elementId: String?) {
        selectedElementId = elementId
        invalidate()
    }

    /**
     * Enables or disables edit mode (shows selection handles).
     */
    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        invalidate()
    }

    /**
     * Gets the current edit mode state.
     */
    fun isEditMode(): Boolean = isEditMode

    /**
     * Gets the selected element ID.
     */
    fun getSelectedElementId(): String? = selectedElementId

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Sort only when needed (not every frame)
        if (needsSorting) {
            sortedElements = elements.sortedBy { it.zIndex }
            needsSorting = false
        }

        for (element in sortedElements) {
            when (element) {
                is TextOverlayElement -> drawTextElement(canvas, element)
                is ImageOverlayElement -> drawImageElement(canvas, element)
                is TimerOverlayElement -> drawTimerElement(canvas, element)
                is ViewerCountOverlayElement -> drawViewerCountElement(canvas, element)
            }

            // Draw selection overlay in edit mode
            if (isEditMode && element.id == selectedElementId) {
                drawSelectionOverlay(canvas, element)
            }
        }
    }

    private fun drawTextElement(canvas: Canvas, element: TextOverlayElement) {
        val bounds = getElementBounds(element, tempRectF)

        // Draw background if set
        if (element.backgroundColor != Color.TRANSPARENT) {
            backgroundPaint.color = element.backgroundColor
            canvas.drawRoundRect(bounds, 8f, 8f, backgroundPaint)
        }

        // Setup text paint
        textPaint.color = element.textColor
        textPaint.textSize = element.fontSize * resources.displayMetrics.density
        textPaint.isFakeBoldText = element.isBold
        textPaint.textSkewX = if (element.isItalic) -0.2f else 0f

        // Draw shadow if enabled
        if (element.hasShadow) {
            textPaint.setShadowLayer(element.shadowRadius, element.shadowDx, element.shadowDy, element.shadowColor)
        }

        // Draw text
        val text = element.text
        val x = bounds.left + when (element.alignment) {
            TextOverlayElement.TextAlignment.LEFT -> 0f
            TextOverlayElement.TextAlignment.CENTER -> (bounds.width() - textPaint.measureText(text)) / 2
            TextOverlayElement.TextAlignment.RIGHT -> bounds.width() - textPaint.measureText(text)
        }
        val y = bounds.bottom - textPaint.descent()

        canvas.save()
        canvas.rotate(element.rotation, bounds.centerX(), bounds.centerY())
        canvas.drawText(text, x, y, textPaint)
        canvas.restore()

        // Clear shadow for other elements
        textPaint.clearShadowLayer()
    }

    private fun drawImageElement(canvas: Canvas, element: ImageOverlayElement) {
        val bounds = getElementBounds(element, tempRectF)
        val bitmap = imageCache[element.imagePath]

        if (bitmap != null && !bitmap.isRecycled) {
            canvas.save()
            canvas.rotate(element.rotation, bounds.centerX(), bounds.centerY())

            // Apply opacity
            imagePaint.alpha = (element.opacity * 255).toInt()

            val srcRect = tempRect.apply {
                set(0, 0, bitmap.width, bitmap.height)
            }

            when (element.scaleType) {
                ImageOverlayElement.ScaleType.FIT_CENTER -> {
                    val scale = minOf(
                        bounds.width() / bitmap.width,
                        bounds.height() / bitmap.height
                    )
                    val scaledWidth = bitmap.width * scale
                    val scaledHeight = bitmap.height * scale
                    tempMatrix.reset()
                    tempMatrix.setScale(scale, scale)
                    tempMatrix.postTranslate(
                        bounds.left + (bounds.width() - scaledWidth) / 2,
                        bounds.top + (bounds.height() - scaledHeight) / 2
                    )
                }
                ImageOverlayElement.ScaleType.CENTER_CROP -> {
                    val scale = maxOf(
                        bounds.width() / bitmap.width,
                        bounds.height() / bitmap.height
                    )
                    tempMatrix.reset()
                    tempMatrix.setScale(scale, scale)
                    tempMatrix.postTranslate(
                        bounds.left - (bitmap.width * scale - bounds.width()) / 2,
                        bounds.top - (bitmap.height * scale - bounds.height()) / 2
                    )
                }
                ImageOverlayElement.ScaleType.STRETCH -> {
                    tempMatrix.reset()
                    tempMatrix.setScale(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
                    tempMatrix.postTranslate(bounds.left, bounds.top)
                }
                ImageOverlayElement.ScaleType.CENTER -> {
                    tempMatrix.reset()
                    tempMatrix.postTranslate(
                        bounds.left + (bounds.width() - bitmap.width) / 2,
                        bounds.top + (bounds.height() - bitmap.height) / 2
                    )
                }
            }

            canvas.drawBitmap(bitmap, tempMatrix, imagePaint)
            canvas.restore()
        }
    }

    private fun drawTimerElement(canvas: Canvas, element: TimerOverlayElement) {
        val bounds = getElementBounds(element, tempRectF)

        // Draw background if set
        if (element.backgroundColor != Color.TRANSPARENT) {
            backgroundPaint.color = element.backgroundColor
            canvas.drawRoundRect(bounds, 8f, 8f, backgroundPaint)
        }

        // Calculate timer value
        val elapsed = System.currentTimeMillis() - element.startTime
        val displayTime = when (element.direction) {
            TimerOverlayElement.TimerDirection.UP -> elapsed
            TimerOverlayElement.TimerDirection.DOWN -> {
                // For countdown, calculate remaining time from a duration
                // If startTime is in the future, use it as end time
                // Otherwise, use absolute value for display
                val remaining = element.startTime - System.currentTimeMillis()
                maxOf(0L, remaining)
            }
        }

        val timeText = formatTime(displayTime, element.format)

        // Setup text paint
        textPaint.color = element.textColor
        textPaint.textSize = element.fontSize * resources.displayMetrics.density
        textPaint.typeface = Typeface.MONOSPACE

        // Draw shadow if enabled
        if (element.hasShadow) {
            textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        canvas.save()
        canvas.rotate(element.rotation, bounds.centerX(), bounds.centerY())
        val x = bounds.left + (bounds.width() - textPaint.measureText(timeText)) / 2
        val y = bounds.bottom - textPaint.descent()
        canvas.drawText(timeText, x, y, textPaint)
        canvas.restore()

        textPaint.clearShadowLayer()
    }

    private fun drawViewerCountElement(canvas: Canvas, element: ViewerCountOverlayElement) {
        val bounds = getElementBounds(element, tempRectF)

        // Draw background if set
        if (element.backgroundColor != Color.TRANSPARENT) {
            backgroundPaint.color = element.backgroundColor
            canvas.drawRoundRect(bounds, 8f, 8f, backgroundPaint)
        }

        // Setup text paint
        textPaint.color = element.textColor
        textPaint.textSize = element.fontSize * resources.displayMetrics.density
        textPaint.typeface = Typeface.MONOSPACE

        // Draw shadow if enabled
        if (element.hasShadow) {
            textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        // Format text with icon
        val iconText = if (element.showIcon) element.iconType.emoji else ""
        val countText = element.viewerValue
        val fullText = "$iconText $countText"

        canvas.save()
        canvas.rotate(element.rotation, bounds.centerX(), bounds.centerY())
        val x = bounds.left + (bounds.width() - textPaint.measureText(fullText)) / 2
        val y = bounds.bottom - textPaint.descent()
        canvas.drawText(fullText, x, y, textPaint)
        canvas.restore()

        textPaint.clearShadowLayer()
    }

    private fun drawSelectionOverlay(canvas: Canvas, element: OverlayElement) {
        val bounds = getElementBounds(element, tempRectF)

        // Draw selection border
        canvas.drawRoundRect(bounds, 4f, 4f, selectionPaint)

        // Draw resize handles at corners
        val handleSize = 16f * resources.displayMetrics.density
        val halfHandle = handleSize / 2

        val handles = listOf(
            PointF(bounds.left - halfHandle, bounds.top - halfHandle),  // Top-left
            PointF(bounds.right - halfHandle, bounds.top - halfHandle), // Top-right
            PointF(bounds.left - halfHandle, bounds.bottom - halfHandle), // Bottom-left
            PointF(bounds.right - halfHandle, bounds.bottom - halfHandle) // Bottom-right
        )

        for (handle in handles) {
            canvas.drawOval(handle.x, handle.y, handle.x + handleSize, handle.y + handleSize, handlePaint)
        }
    }

    protected fun getElementBounds(element: OverlayElement, rect: RectF = tempRectF): RectF {
        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()

        val left = element.x * screenWidth
        val top = element.y * screenHeight
        val right = left + (element.width * screenWidth)
        val bottom = top + (element.height * screenHeight)

        rect.set(left, top, right, bottom)
        return rect
    }

    private fun formatTime(millis: Long, format: TimerOverlayElement.TimerFormat): String {
        val totalSeconds = millis / 1000
        val hours = (totalSeconds / 3600) % 24
        val minutes = (totalSeconds / 60) % 60
        val seconds = totalSeconds % 60

        return when (format) {
            TimerOverlayElement.TimerFormat.HH_MM_SS ->
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            TimerOverlayElement.TimerFormat.MM_SS ->
                String.format("%02d:%02d", minutes, seconds)
            TimerOverlayElement.TimerFormat.HH_MM ->
                String.format("%02d:%02d", hours, minutes)
            TimerOverlayElement.TimerFormat.SS ->
                String.format("%02d", seconds)
            TimerOverlayElement.TimerFormat.CUSTOM ->
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    private fun preloadImages() {
        for (element in elements) {
            if (element is ImageOverlayElement && element.imagePath.isNotEmpty()) {
                if (!imageCache.containsKey(element.imagePath) && !pendingImages.contains(element.imagePath)) {
                    pendingImages.add(element.imagePath)
                    loadImage(element.imagePath)
                }
            }
        }
    }

    private fun loadImage(path: String) {
        scope.launch {
            try {
                val uri = if (path.startsWith("content://") || path.startsWith("file://")) {
                    Uri.parse(path)
                } else {
                    Uri.fromFile(File(path))
                }

                Glide.with(context)
                    .asBitmap()
                    .load(uri)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            imageCache[path] = resource
                            pendingImages.remove(path)
                            invalidate()
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            // Handle cleanup if needed
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            pendingImages.remove(path)
                        }

                        override fun onLoadStarted(placeholder: Drawable?) {
                            // Loading started
                        }
                    })
            } catch (e: Exception) {
                pendingImages.remove(path)
            }
        }
    }

    /**
     * Clears the image cache.
     */
    fun clearImageCache() {
        imageCache.clear()
        pendingImages.clear()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
        clearImageCache()
    }

    /**
     * Gets the dimensions required for a text element.
     */
    fun measureTextElement(element: TextOverlayElement, maxWidth: Float): SizeF {
        textPaint.textSize = element.fontSize * resources.displayMetrics.density
        textPaint.isFakeBoldText = element.isBold
        textPaint.textSkewX = if (element.isItalic) -0.2f else 0f

        val text = element.text
        val textWidth = textPaint.measureText(text)
        val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }

        return SizeF(
            minOf(textWidth + 16, maxWidth),
            textHeight + 16
        )
    }

    /**
     * Gets all elements currently being rendered.
     * Used by subclasses for hit testing.
     */
    protected fun getAllElements(): List<OverlayElement> {
        return elements.toList()
    }
}
