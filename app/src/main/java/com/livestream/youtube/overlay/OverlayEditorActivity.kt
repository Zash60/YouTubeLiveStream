package com.livestream.youtube.overlay

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
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
    private lateinit var elementListAdapter: ElementListAdapter

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            (selectedElement as? ImageOverlayElement)?.let { element ->
                element.imagePath = selectedUri.toString()
                elementManager.updateElement(element.id, update = { it })
                updateCanvas()
                showSnackbar(getString(R.string.image_selected))
            }
        }
    }

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

        // Setup element list
        setupElementList()

        // Setup layer controls
        setupLayerControls()

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
            updateElementList()
        }
    }

    private fun setupCanvas() {
        binding.overlayCanvas.setOnElementInteractionListener(this)
        updateCanvas()
    }

    private fun setupElementList() {
        elementListAdapter = ElementListAdapter(
            elements = elementManager.getAllElements(),
            onElementClick = { element ->
                selectElement(element)
            },
            onVisibilityToggle = { element ->
                elementManager.setElementVisibility(element.id, !element.isVisible)
                updateCanvas()
                updateElementList()
            }
        )
        binding.recyclerElements.layoutManager = LinearLayoutManager(this)
        binding.recyclerElements.adapter = elementListAdapter
    }

    private fun updateElementList() {
        elementListAdapter.updateElements(elementManager.getAllElements())
    }

    private fun setupLayerControls() {
        binding.btnBringToFront.setOnClickListener {
            selectedElement?.let { element ->
                elementManager.bringToFront(element.id)
                updateCanvas()
                updateElementList()
                showSnackbar(getString(R.string.element_brought_front))
            }
        }

        binding.btnSendToBack.setOnClickListener {
            selectedElement?.let { element ->
                elementManager.sendToBack(element.id)
                updateCanvas()
                updateElementList()
                showSnackbar(getString(R.string.element_sent_back))
            }
        }
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
            updateElementList()
            selectElement(textElement)
            showSnackbar(getString(R.string.element_added, getString(R.string.text_element)))
        }

        // Add Image Element
        binding.btnAddImage.setOnClickListener {
            val imageElement = ImageOverlayElement(
                imagePath = "",
                x = 0.1f,
                y = 0.2f,
                width = 0.2f,
                height = 0.15f
            )
            elementManager.addElement(imageElement)
            updateCanvas()
            updateElementList()
            selectElement(imageElement)
            showSnackbar(getString(R.string.element_added, getString(R.string.image_element)))
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
            updateElementList()
            selectElement(timerElement)
            showSnackbar(getString(R.string.element_added, getString(R.string.timer_element)))
        }

        // Add Viewer Count Element
        binding.btnAddViewerCount.setOnClickListener {
            val viewerElement = ViewerCountOverlayElement(
                x = 0.1f,
                y = 0.5f,
                width = 0.15f,
                height = 0.04f
            )
            elementManager.addElement(viewerElement)
            updateCanvas()
            updateElementList()
            selectElement(viewerElement)
            showSnackbar(getString(R.string.element_added, getString(R.string.viewer_count_element)))
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
            updateElementList()
            selectElement(chatElement)
            showSnackbar(getString(R.string.element_added, getString(R.string.chat_element)))
        }

        // Undo/Redo
        binding.btnUndo.isEnabled = elementManager.canUndo()
        binding.btnRedo.isEnabled = elementManager.canRedo()

        binding.btnUndo.setOnClickListener {
            if (elementManager.undo()) {
                updateCanvas()
                updateElementList()
                updateUndoRedoButtons()
                clearSelection()
            }
        }

        binding.btnRedo.setOnClickListener {
            if (elementManager.redo()) {
                updateCanvas()
                updateElementList()
                updateUndoRedoButtons()
                clearSelection()
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
                    .setMessage(R.string.confirm_delete_element)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        elementManager.removeElement(element.id)
                        updateCanvas()
                        updateElementList()
                        clearSelection()
                        showSnackbar(getString(R.string.element_deleted))
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
                    updateElementList()
                    selectElement(newElement)
                    showSnackbar(getString(R.string.element_duplicated))
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
                    elementManager.updateElement(selectedElement!!.id, { updated -> updated })
                    updateCanvas()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Rotation SeekBar
        binding.seekbarRotation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && selectedElement != null) {
                    selectedElement!!.rotation = progress * 3.6f // 0-360 degrees
                    elementManager.updateElement(selectedElement!!.id, { updated -> updated })
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
                updateElementList()
            }
        }

        // Setup element-specific property buttons
        setupElementSpecificProperties()
    }

    private fun setupElementSpecificProperties() {
        // Edit Text Button
        binding.btnEditText.setOnClickListener {
            (selectedElement as? TextOverlayElement)?.let { element ->
                showTextEditDialog(element)
            }
        }

        // Text Color Button
        binding.btnTextColor.setOnClickListener {
            selectedElement?.let { element ->
                val currentColor = when (element) {
                    is TextOverlayElement -> element.textColor
                    is TimerOverlayElement -> element.textColor
                    is ViewerCountOverlayElement -> element.textColor
                    else -> Color.WHITE
                }
                showColorPickerDialog(currentColor) { color ->
                    updateElementTextColor(element, color)
                }
            }
        }

        // Background Color Button
        binding.btnBackgroundColor.setOnClickListener {
            selectedElement?.let { element ->
                val currentColor = when (element) {
                    is TextOverlayElement -> element.backgroundColor
                    is TimerOverlayElement -> element.backgroundColor
                    is ViewerCountOverlayElement -> element.backgroundColor
                    else -> Color.TRANSPARENT
                }
                showColorPickerDialog(currentColor) { color ->
                    updateElementBackgroundColor(element, color)
                }
            }
        }

        // Font Size SeekBar
        binding.seekbarFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && selectedElement != null) {
                    val fontSize = progress + 8 // 8-80 range
                    when (val element = selectedElement) {
                        is TextOverlayElement -> element.fontSize = fontSize
                        is TimerOverlayElement -> element.fontSize = fontSize
                        is ViewerCountOverlayElement -> element.fontSize = fontSize
                    }
                    elementManager.updateElement(selectedElement!!.id, { updated -> updated })
                    updateCanvas()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Bold Toggle
        binding.switchBold.setOnCheckedChangeListener { _, isChecked ->
            (selectedElement as? TextOverlayElement)?.let { element ->
                element.isBold = isChecked
                elementManager.updateElement(element.id, { updated -> updated })
                updateCanvas()
            }
        }

        // Italic Toggle
        binding.switchItalic.setOnCheckedChangeListener { _, isChecked ->
            (selectedElement as? TextOverlayElement)?.let { element ->
                element.isItalic = isChecked
                elementManager.updateElement(element.id, { updated -> updated })
                updateCanvas()
            }
        }

        // Shadow Toggle
        binding.switchShadow.setOnCheckedChangeListener { _, isChecked ->
            selectedElement?.let { element ->
                when (element) {
                    is TextOverlayElement -> element.hasShadow = isChecked
                    is TimerOverlayElement -> element.hasShadow = isChecked
                    is ViewerCountOverlayElement -> element.hasShadow = isChecked
                }
                elementManager.updateElement(element.id, { updated -> updated })
                updateCanvas()
            }
        }

        // Select Image Button
        binding.btnSelectImage.setOnClickListener {
            (selectedElement as? ImageOverlayElement)?.let {
                imagePickerLauncher.launch("image/*")
            }
        }

        // Scale Type Spinner
        val scaleTypes = listOf("FIT_CENTER", "CENTER_CROP", "STRETCH", "CENTER")
        binding.spinnerScaleType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, scaleTypes)

        // Timer Format Spinner
        val timerFormats = listOf("HH:MM:SS", "MM:SS", "HH:MM", "SS")
        binding.spinnerTimerFormat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timerFormats)

        // Timer Direction Spinner
        val timerDirections = listOf(getString(R.string.timer_up), getString(R.string.timer_down))
        binding.spinnerTimerDirection.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timerDirections)

        // Icon Type Spinner
        val iconTypes = listOf("EYE 👁️", "USERS 👥", "LIVE 🔴", "NONE")
        binding.spinnerIconType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, iconTypes)
    }

    private fun showTextEditDialog(element: TextOverlayElement) {
        val editText = EditText(this).apply {
            setText(element.text)
            setSelection(element.text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.property_text)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                element.text = editText.text.toString()
                elementManager.updateElement(element.id, { updated -> updated })
                updateCanvas()
                showSnackbar(getString(R.string.text_updated))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showColorPickerDialog(currentColor: Int, onColorSelected: (Int) -> Unit) {
        val colors = intArrayOf(
            Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.CYAN, Color.MAGENTA,
            Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
            Color.parseColor("#45B7D1"), Color.parseColor("#96CEB4"),
            Color.parseColor("#FFEAA7"), Color.parseColor("#DDA0DD"),
            Color.parseColor("#98D8C8"), Color.parseColor("#F7DC6F"),
            Color.parseColor("#BB8FCE"), Color.parseColor("#85C1E9"),
            Color.TRANSPARENT
        )

        val colorNames = arrayOf(
            "White", "Black", "Red", "Green", "Blue",
            "Yellow", "Cyan", "Magenta",
            "Coral", "Teal", "Sky Blue", "Mint",
            "Cream", "Pink", "Sea Green", "Gold",
            "Purple", "Light Blue", "Transparent"
        )

        val adapter = ColorAdapter(colors, colorNames, currentColor)

        AlertDialog.Builder(this)
            .setTitle(R.string.select_color)
            .setAdapter(adapter) { _, which ->
                onColorSelected(colors[which])
                updateCanvas()
            }
            .show()
    }

    private fun updateElementTextColor(element: OverlayElement, color: Int) {
        when (element) {
            is TextOverlayElement -> element.textColor = color
            is TimerOverlayElement -> element.textColor = color
            is ViewerCountOverlayElement -> element.textColor = color
        }
        elementManager.updateElement(element.id, { updated -> updated })
        updateCanvas()
    }

    private fun updateElementBackgroundColor(element: OverlayElement, color: Int) {
        when (element) {
            is TextOverlayElement -> element.backgroundColor = color
            is TimerOverlayElement -> element.backgroundColor = color
            is ViewerCountOverlayElement -> element.backgroundColor = color
        }
        elementManager.updateElement(element.id, { updated -> updated })
        updateCanvas()
    }

    private fun updateCanvas() {
        binding.overlayCanvas.setElements(elementManager.getVisibleElements())
    }

    private fun updateCanvasFast() {
        // Fast update without rebuilding list - just invalidate
        binding.overlayCanvas.invalidate()
    }

    private fun selectElement(element: OverlayElement?) {
        selectedElement = element
        binding.overlayCanvas.setSelectedElement(element?.id)
        updateSelectionUI()
        updateElementList()
    }

    private fun clearSelection() {
        selectedElement = null
        binding.overlayCanvas.clearSelection()
        updateSelectionUI()
        updateElementList()
    }

    private fun updateSelectionUI() {
        if (selectedElement != null) {
            binding.tvSelectedElement.text = getElementTypeName(selectedElement!!)
            binding.propertiesContainer.visibility = View.VISIBLE
            binding.btnDelete.visibility = View.VISIBLE
            binding.btnDuplicate.visibility = View.VISIBLE
            binding.layerControls.visibility = View.VISIBLE

            // Update SeekBars
            binding.seekbarX.progress = (selectedElement!!.x * 100).toInt()
            binding.seekbarY.progress = (selectedElement!!.y * 100).toInt()
            binding.seekbarWidth.progress = (selectedElement!!.width * 100).toInt()
            binding.seekbarHeight.progress = (selectedElement!!.height * 100).toInt()
            binding.seekbarOpacity.progress = (selectedElement!!.opacity * 100).toInt()
            binding.seekbarRotation.progress = (selectedElement!!.rotation / 3.6f).toInt()
            binding.switchVisible.isChecked = selectedElement!!.isVisible

            // Update element-specific UI
            updateElementSpecificUI()
        } else {
            binding.tvSelectedElement.text = getString(R.string.no_overlay_selected)
            binding.propertiesContainer.visibility = View.GONE
            binding.btnDelete.visibility = View.GONE
            binding.btnDuplicate.visibility = View.GONE
            binding.layerControls.visibility = View.GONE
            binding.textProperties.visibility = View.GONE
            binding.imageProperties.visibility = View.GONE
            binding.timerProperties.visibility = View.GONE
            binding.viewerCountProperties.visibility = View.GONE
        }
    }

    private fun updateElementSpecificUI() {
        // Hide all specific property panels first
        binding.textProperties.visibility = View.GONE
        binding.imageProperties.visibility = View.GONE
        binding.timerProperties.visibility = View.GONE
        binding.viewerCountProperties.visibility = View.GONE

        when (val element = selectedElement) {
            is TextOverlayElement -> {
                binding.textProperties.visibility = View.VISIBLE
                binding.seekbarFontSize.progress = element.fontSize - 8
                binding.switchBold.isChecked = element.isBold
                binding.switchItalic.isChecked = element.isItalic
                binding.switchShadow.isChecked = element.hasShadow
            }
            is ImageOverlayElement -> {
                binding.imageProperties.visibility = View.VISIBLE
                binding.spinnerScaleType.setSelection(element.scaleType.ordinal)
            }
            is TimerOverlayElement -> {
                binding.timerProperties.visibility = View.VISIBLE
                binding.spinnerTimerFormat.setSelection(element.format.ordinal)
                binding.spinnerTimerDirection.setSelection(element.direction.ordinal)
                binding.switchShadow.isChecked = element.hasShadow
            }
            is ViewerCountOverlayElement -> {
                binding.viewerCountProperties.visibility = View.VISIBLE
                binding.spinnerIconType.setSelection(element.iconType.ordinal)
                binding.switchShadow.isChecked = element.hasShadow
            }
        }
    }

    private fun getElementTypeName(element: OverlayElement): String {
        return when (element) {
            is TextOverlayElement -> getString(R.string.text_element)
            is ImageOverlayElement -> getString(R.string.image_element)
            is TimerOverlayElement -> getString(R.string.timer_element)
            is ViewerCountOverlayElement -> getString(R.string.viewer_count_element)
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

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
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
        // Use fast update during drag
        updateCanvasFast()
    }

    override fun onElementResized(elementId: String, newWidth: Float, newHeight: Float) {
        elementManager.resizeElement(elementId, newWidth, newHeight, saveUndo = false)
        if (selectedElement?.id == elementId) {
            binding.seekbarWidth.progress = (newWidth * 100).toInt()
            binding.seekbarHeight.progress = (newHeight * 100).toInt()
        }
        // Use fast update during resize
        updateCanvasFast()
    }

    override fun onEditModeChanged(enabled: Boolean) {
        isPreviewMode = !enabled
        binding.fabPreviewToggle.setImageResource(
            if (isPreviewMode) android.R.drawable.ic_menu_edit
            else android.R.drawable.ic_menu_view
        )
    }

    // Element List Adapter
    class ElementListAdapter(
        private var elements: List<OverlayElement>,
        private val onElementClick: (OverlayElement) -> Unit,
        private val onVisibilityToggle: (OverlayElement) -> Unit
    ) : RecyclerView.Adapter<ElementListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvElementName: TextView = view.findViewById(R.id.tvElementName)
            val tvElementType: TextView = view.findViewById(R.id.tvElementType)
            val btnVisibility: View = view.findViewById(R.id.btnVisibility)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_element_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val element = elements[position]
            holder.tvElementName.text = when (element) {
                is TextOverlayElement -> element.text.take(20) + if (element.text.length > 20) "..." else ""
                is ImageOverlayElement -> if (element.imagePath.isNotEmpty()) "Image" else "No Image"
                is TimerOverlayElement -> "Timer"
                is ViewerCountOverlayElement -> "Viewers"
                else -> "Element"
            }
            holder.tvElementType.text = when (element) {
                is TextOverlayElement -> "Text"
                is ImageOverlayElement -> "Image"
                is TimerOverlayElement -> "Timer"
                is ViewerCountOverlayElement -> "Viewers"
                else -> "Unknown"
            }
            holder.btnVisibility.alpha = if (element.isVisible) 1f else 0.3f

            holder.itemView.setOnClickListener { onElementClick(element) }
            holder.btnVisibility.setOnClickListener { onVisibilityToggle(element) }
        }

        override fun getItemCount() = elements.size

        fun updateElements(newElements: List<OverlayElement>) {
            elements = newElements
            notifyDataSetChanged()
        }
    }

    // Color Picker Adapter
    inner class ColorAdapter(
        private val colors: IntArray,
        private val names: Array<String>,
        private val currentColor: Int
    ) : ArrayAdapter<String>(this@OverlayEditorActivity, android.R.layout.simple_list_item_1, names) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val textView = view.findViewById<TextView>(android.R.id.text1)
            textView.setTextColor(colors[position])
            if (colors[position] == Color.BLACK) {
                textView.setBackgroundColor(Color.LTGRAY)
            }
            if (colors[position] == currentColor) {
                textView.text = "✓ ${names[position]}"
            }
            return view
        }
    }

    companion object {
        const val EXTRA_CONFIG_ID = "config_id"
    }
}
