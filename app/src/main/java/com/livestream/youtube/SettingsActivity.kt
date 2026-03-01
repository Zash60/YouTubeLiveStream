package com.livestream.youtube

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import com.livestream.youtube.overlay.OverlayEditorActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.livestream.youtube.databinding.ActivitySettingsBinding
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let { saveImageInternal(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        setupUI()
        loadSettings()
        enableImmersiveMode()
    }

    private fun enableImmersiveMode() {
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false
        
        // Hide system bars
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupUI() {
        binding.btnSelectImage.setOnClickListener {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 101)
            }
        }
        binding.btnRemoveImage.setOnClickListener {
            val file = File(filesDir, "pause_image.png")
            if (file.exists()) file.delete()
            updateImagePreview(null)
            Toast.makeText(this, R.string.image_removed, Toast.LENGTH_SHORT).show()
        }

        // Setup custom spinner dropdown
        val dropdownItemLayout = R.layout.item_spinner_dropdown
        
        val resolutions = arrayOf("1920x1080", "1280x720", "854x480", "640x360")
        binding.spinnerResolution.adapter = ArrayAdapter(this, dropdownItemLayout, resolutions)

        val fpsOptions = arrayOf("60", "30", "24")
        binding.spinnerFps.adapter = ArrayAdapter(this, dropdownItemLayout, fpsOptions)

        val videoBitrateOptions = arrayOf("8000", "6000", "4500", "4000", "3000", "2500", "2000", "1500")
        binding.spinnerVideoBitrate.adapter = ArrayAdapter(this, dropdownItemLayout, videoBitrateOptions)

        val audioBitrateOptions = arrayOf("320", "256", "192", "128", "96")
        binding.spinnerAudioBitrate.adapter = ArrayAdapter(this, dropdownItemLayout, audioBitrateOptions)

        val sampleRateOptions = arrayOf("48000", "44100", "22050")
        binding.spinnerSampleRate.adapter = ArrayAdapter(this, dropdownItemLayout, sampleRateOptions)

        val codecs = arrayOf("H.264 (Padrao)", "H.265 (HEVC - Alta Qualidade)")
        binding.spinnerCodec.adapter = ArrayAdapter(this, dropdownItemLayout, codecs)

        // Recording quality options
        val recordingQualityOptions = arrayOf(
            getString(R.string.recording_quality_high),
            getString(R.string.recording_quality_medium),
            getString(R.string.recording_quality_low)
        )
        binding.spinnerRecordingQuality.adapter = ArrayAdapter(this, dropdownItemLayout, recordingQualityOptions)

        // Recording format options
        val recordingFormatOptions = arrayOf(
            getString(R.string.recording_format_mp4),
            getString(R.string.recording_format_mkv)
        )
        binding.spinnerRecordingFormat.adapter = ArrayAdapter(this, dropdownItemLayout, recordingFormatOptions)

        binding.btnSave.setOnClickListener {
            saveSettings()
            finish()
        }

        binding.btnOpenOverlayEditor.setOnClickListener {
            startActivity(Intent(this, OverlayEditorActivity::class.java))
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)

        @Suppress("UNCHECKED_CAST")
        fun setSpinnerSelection(spinner: android.widget.Spinner, value: String) {
            val adapter = spinner.adapter as ArrayAdapter<String>
            val position = adapter.getPosition(value)
            spinner.setSelection(position.coerceAtLeast(0))
        }

        val width = prefs.getInt("width", 1280)
        val height = prefs.getInt("height", 720)
        setSpinnerSelection(binding.spinnerResolution, "${width}x${height}")

        setSpinnerSelection(binding.spinnerFps, prefs.getInt("fps", 30).toString())
        setSpinnerSelection(binding.spinnerVideoBitrate, prefs.getInt("video_bitrate", 4000).toString())
        setSpinnerSelection(binding.spinnerAudioBitrate, prefs.getInt("audio_bitrate", 128).toString())
        setSpinnerSelection(binding.spinnerSampleRate, prefs.getInt("sample_rate", 44100).toString())

        val useHevc = prefs.getBoolean("use_hevc", false)
        binding.spinnerCodec.setSelection(if(useHevc) 1 else 0)

        // Novos campos
        binding.etWebOverlayUrl.setText(prefs.getString("alert_url", ""))
        binding.switchLocalRecord.isChecked = prefs.getBoolean("local_record", false)
        
        // Recording settings
        val recordingQuality = prefs.getInt("recording_quality", RecordingService.QUALITY_MEDIUM)
        binding.spinnerRecordingQuality.setSelection(recordingQuality)
        
        val recordingFormat = prefs.getInt("recording_format", RecordingService.FORMAT_MP4)
        binding.spinnerRecordingFormat.setSelection(recordingFormat)

        updateImagePreview()
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)
        val resParts = binding.spinnerResolution.selectedItem.toString().split("x")
        val width = resParts[0].toInt()
        val height = resParts[1].toInt()
        
        prefs.edit().apply {
            putInt("width", width)
            putInt("height", height)
            putInt("fps", binding.spinnerFps.selectedItem.toString().toInt())
            putInt("video_bitrate", binding.spinnerVideoBitrate.selectedItem.toString().toInt())
            putInt("audio_bitrate", binding.spinnerAudioBitrate.selectedItem.toString().toInt())
            putInt("sample_rate", binding.spinnerSampleRate.selectedItem.toString().toInt())
            putBoolean("use_hevc", binding.spinnerCodec.selectedItemPosition == 1)
            
            // Novos campos
            putString("alert_url", binding.etWebOverlayUrl.text.toString())
            putBoolean("local_record", binding.switchLocalRecord.isChecked)
            
            // Recording settings
            putInt("recording_quality", binding.spinnerRecordingQuality.selectedItemPosition)
            putInt("recording_format", binding.spinnerRecordingFormat.selectedItemPosition)
            
            apply()
        }
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImage.launch(intent)
    }

    private fun saveImageInternal(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val file = File(filesDir, "pause_image.png")
            val out = FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            inputStream?.close()
            updateImagePreview(bitmap)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updateImagePreview(bitmap: android.graphics.Bitmap? = null) {
        if (bitmap != null) {
            binding.imgPreview.setImageBitmap(bitmap)
            binding.imgPreview.visibility = View.VISIBLE
            binding.btnRemoveImage.visibility = View.VISIBLE
            binding.txtImageStatus.text = getString(R.string.image_active)
        } else {
            val file = File(filesDir, "pause_image.png")
            if (file.exists()) {
                val saved = BitmapFactory.decodeFile(file.absolutePath)
                binding.imgPreview.setImageBitmap(saved)
                binding.imgPreview.visibility = View.VISIBLE
                binding.btnRemoveImage.visibility = View.VISIBLE
                binding.txtImageStatus.text = getString(R.string.image_active)
            } else {
                binding.imgPreview.visibility = View.GONE
                binding.btnRemoveImage.visibility = View.GONE
                binding.txtImageStatus.text = getString(R.string.no_image_selected)
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
