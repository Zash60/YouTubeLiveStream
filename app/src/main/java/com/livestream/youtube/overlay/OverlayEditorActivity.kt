package com.livestream.youtube.overlay

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.livestream.youtube.R
import com.livestream.youtube.databinding.ActivityOverlayEditorBinding
import com.livestream.youtube.model.overlay.*

/**
 * Activity for editing overlay configurations.
 * Provides a visual editor for creating and modifying overlay elements.
 */
class OverlayEditorActivity : AppCompatActivity(), OverlayCanvasView.OnElementInteractionListener {

    private lateinit var binding: ActivityOverlayEditorBinding
    private lateinit var elementManager: OverlayElementManager
    private lateinit var storageManager: OverlayStorageManager
    private var selectedElement: OverlayElement? = null
    private var isPreviewMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOverlayEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        elementManager = OverlayElementManager()
        storageManager = OverlayStorageManager(this)

        // Load existing configuration or create new
        loadConfiguration()

        // Setup canvas
        setupCanvas()

        // Setup toolbar buttons
        setupToolbar()

        // Setup property panel
        setupPropertyPanel()

        // Load overlay if specified
        val configId = intent.getStringExtra(EXTRA_CONFIG_ID)
        if (configId != null) {
            loadSpecificConfiguration(configId)
        }
    }

    private fun loadConfiguration() {
        val config = storageManager.loadActiveConfiguration()
        if (config != null) {
            elementManager.setConfiguration(config)
        } else {
            // Create default configuration
            val defaultConfig = storageManager.createDefaultConfiguration()
            elementManager.setConfiguration(defaultConfig)
        }
    }

    private fun loadSpecificConfiguration(configId: String) {
        val config = storageManager.loadConfiguration(configId)
        if (config != null) {
            elementManager.setConfiguration(config)
            updateCanvas()
        }
    }

    private fun setupCanvas() {
        binding.overlayCanvas.setOnElementInteractionListener(this)
        updateCanvas()
    }

    private fun setupToolbar() {
        // Add Text Element
        binding.btnAddText.setOnClickListener {
            val textElement = TextOverlayElement(
                text = getString(R.string.text_element),
                x = 0.1f,
                y = 0.1f,
                width = 0.3f,
                height = 0.05f
            )
            elementManager.addElement(textElement)
            updateCanvas()
            selectElement(textElement)
        }

        // Add Image Element
        binding.btnAddImage.setOnClickListener {
            // In a real app, open image picker here
            val imageElement = ImageOverlayElement(
                imagePath = "",
                x = 0.1f,
                y = 0.2f,
                width = 0.2f,
                height = 0.15f
            )
            elementManager.addElement(imageElement)
            updateCanvas()
            selectElement(imageElement)
        }

        // Add Timer Element
        binding.btnAddTimer.setOnClickListener {
            val timerElement = TimerOverlayElement(
                x = 0.1f,
                y = 0.4f,
                width = 0.2f,
                height = 0.05f
            )
            elementManager.addElement(timerElement)
            updateCanvas()
            selectElement(timerElement)
        }

        // Add Viewer Count Element
        binding.btnAddViewerCount.setOnClickListener {
            val viewerElement = TextOverlayElement(
                text = "👁️ 0",
                x = 0.1f,
                y = 0.5f,
                width = 0.15f,
                height = 0.04f,
                fontSize = 20
            )
            elementManager.addElement(viewerElement)
            updateCanvas()
            selectElement(viewerElement)
        }

        // Add Chat Element
        binding.btnAddChat.setOnClickListener {
            val chatElement = TextOverlayElement(
                text = "Chat: Hello!",
                x = 0.1f,
                y = 0.6f,
                width = 0.25f,
                height = 0.1f,
                fontSize = 14,
                backgroundColor = 0x80000000.toInt()
            )
            elementManager.addElement(chatElement)
            updateCanvas()
            selectElement(chatElement)
        }

        // Undo/Redo
        binding.btnUndo.isEnabled = elementManager.canUndo()
        binding.btnRedo.isEnabled = elementManager.canRedo()

        binding.btnUndo.setOnClickListener {
            if (elementManager.undo()) {
                updateCanvas()
                updateUndoRedoButtons()
            }
        }

        binding.btnRedo.setOnClickListener {
            if (elementManager.redo()) {
                updateCanvas()
                updateUndoRedoButtons()
            }
        }

        // Preview Toggle
        binding.fabPreviewToggle.setOnClickListener {
            isPreviewMode = !isPreviewMode
            binding.overlayCanvas.setEditMode(!isPreviewMode)
            binding.fabPreviewToggle.setImageResource(
                if (isPreviewMode) android.R.drawable.ic_menu_edit
                else android.R.drawable.ic_menu_view
            )
            updateCanvas()
        }

        // Save Button
        binding.btnSave.setOnClickListener {
            saveConfiguration()
        }

        // Delete Button
        binding.btnDelete.setOnClickListener {
            selectedElement?.let { element ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete_element)
                    .setMessage(R.string.confirm_clear_all)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        elementManager.removeElement(element.id)
                        updateCanvas()
                        clearSelection()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }
        }

        // Duplicate Button
        binding.btnDuplicate.setOnClickListener {
            selectedElement?.let { element ->
                val newElement = elementManager.duplicateElement(element.id)
                if (newElement != null) {
                    updateCanvas()
                    selectElement(newElement)
                }
            }
        }
    }

    private fun setupPropertyPanel() {
        // Position SeekBars
        binding.seekbarX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && selectedElement != null) {
                    elementManager.moveElement(selectedElement!!.id, progress / 100f, selectedElement!!.y)
                    updateCanvas()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekbarY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && selectedElement != null) {
                    elementManager.moveElement(selectedElement!!.id, selectedElement!!.x, progress / 100f)
                    updateCanvas()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekbarWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && selectedElement != null) {
                    elementManager.resizeElement(selectedElement!!.id, progress / 100f, selectedElement!!.height)
                    updateCanvas()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekbarHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && selectedElement != null) {
                    elementManager.resizeElement(selectedElement!!.id, selectedElement!!.width, progress / 100f)
                    updateCanvas()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekbarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && selectedElement != null) {
                    selectedElement!!.opacity = progress / 100f
                    elementManager.updateElement(selectedElement!!.id) { it }
                    updateCanvas()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.switchVisible.setOnCheckedChangeListener { _, isChecked ->
            selectedElement?.let { element ->
                elementManager.setElementVisibility(element.id, isChecked)
                updateCanvas()
            }
        }
    }

    private fun updateCanvas() {
        binding.overlayCanvas.setElements(elementManager.getVisibleElements())
    }

    private fun selectElement(element: OverlayElement?) {
        selectedElement = element
        binding.overlayCanvas.setSelectedElement(element?.id)
        updateSelectionUI()
    }

    private fun clearSelection() {
        selectedElement = null
        binding.overlayCanvas.clearSelection()
        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        if (selectedElement != null) {
            binding.tvSelectedElement.text = getElementTypeName(selectedElement!!)
            binding.propertiesContainer.visibility = View.VISIBLE
            binding.btnDelete.visibility = View.VISIBLE
            binding.btnDuplicate.visibility = View.VISIBLE

            // Update SeekBars
            binding.seekbarX.progress = (selectedElement!!.x * 100).toInt()
            binding.seekbarY.progress = (selectedElement!!.y * 100).toInt()
            binding.seekbarWidth.progress = (selectedElement!!.width * 100).toInt()
            binding.seekbarHeight.progress = (selectedElement!!.height * 100).toInt()
            binding.seekbarOpacity.progress = (selectedElement!!.opacity * 100).toInt()
            binding.switchVisible.isChecked = selectedElement!!.isVisible
        } else {
            binding.tvSelectedElement.text = getString(R.string.no_overlay_selected)
            binding.propertiesContainer.visibility = View.GONE
            binding.btnDelete.visibility = View.GONE
            binding.btnDuplicate.visibility = View.GONE
        }
    }

    private fun getElementTypeName(element: OverlayElement): String {
        return when (element) {
            is TextOverlayElement -> getString(R.string.text_element)
            is ImageOverlayElement -> getString(R.string.image_element)
            is TimerOverlayElement -> getString(R.string.timer_element)
            else -> getString(R.string.overlay_editor)
        }
    }

    private fun updateUndoRedoButtons() {
        binding.btnUndo.isEnabled = elementManager.canUndo()
        binding.btnRedo.isEnabled = elementManager.canRedo()
    }

    private fun saveConfiguration() {
        val config = elementManager.getConfiguration()
        if (storageManager.saveConfiguration(config)) {
            storageManager.setActiveConfiguration(config.id)
            Toast.makeText(this, R.string.overlay_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onElementSelected(element: OverlayElement?) {
        selectElement(element)
    }

    override fun onElementMoved(elementId: String, newX: Float, newY: Float) {
        elementManager.moveElement(elementId, newX, newY, saveUndo = false)
        if (selectedElement?.id == elementId) {
            binding.seekbarX.progress = (newX * 100).toInt()
            binding.seekbarY.progress = (newY * 100).toInt()
        }
    }

    override fun onElementResized(elementId: String, newWidth: Float, newHeight: Float) {
        elementManager.resizeElement(elementId, newWidth, newHeight, saveUndo = false)
        if (selectedElement?.id == elementId) {
            binding.seekbarWidth.progress = (newWidth * 100).toInt()
            binding.seekbarHeight.progress = (newHeight * 100).toInt()
        }
    }

    override fun onEditModeChanged(enabled: Boolean) {
        isPreviewMode = !enabled
        binding.fabPreviewToggle.setImageResource(
            if (isPreviewMode) android.R.drawable.ic_menu_edit
            else android.R.drawable.ic_menu_view
        )
    }

    companion object {
        const val EXTRA_CONFIG_ID = "config_id"
    }
}
