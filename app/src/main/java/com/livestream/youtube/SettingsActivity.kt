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
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
        supportActionBar?.title = "Configurações"

        setupUI()
        loadSettings()
    }

    private fun setupUI() {
        // Image Picker
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
            Toast.makeText(this, "Imagem removida", Toast.LENGTH_SHORT).show()
        }

        // Spinners
        val resolutions = arrayOf("1920x1080", "1280x720", "854x480", "640x360")
        binding.spinnerResolution.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resolutions)

        val fpsOptions = arrayOf("60", "30", "24")
        binding.spinnerFps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fpsOptions)

        val videoBitrateOptions = arrayOf("8000", "6000", "4500", "4000", "3000", "2500", "2000", "1500")
        binding.spinnerVideoBitrate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, videoBitrateOptions)

        val audioBitrateOptions = arrayOf("320", "256", "192", "128", "96")
        binding.spinnerAudioBitrate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, audioBitrateOptions)

        val sampleRateOptions = arrayOf("48000", "44100", "22050")
        binding.spinnerSampleRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sampleRateOptions)

        // Codec
        val codecs = arrayOf("H.264 (Padrão)", "H.265 (HEVC - Alta Qualidade)")
        binding.spinnerCodec.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codecs)

        // Save Button
        binding.btnSave.setOnClickListener {
            saveSettings()
            finish()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)

        // Resolução
        val width = prefs.getInt("width", 1280)
        val height = prefs.getInt("height", 720)
        val resStr = "${width}x${height}"
        val resAdapter = binding.spinnerResolution.adapter as ArrayAdapter<String>
        binding.spinnerResolution.setSelection(resAdapter.getPosition(resStr).coerceAtLeast(0))

        // FPS
        val fps = prefs.getInt("fps", 30).toString()
        val fpsAdapter = binding.spinnerFps.adapter as ArrayAdapter<String>
        binding.spinnerFps.setSelection(fpsAdapter.getPosition(fps).coerceAtLeast(0))

        // Bitrates
        val vBitrate = prefs.getInt("video_bitrate", 4000).toString()
        val vAdapter = binding.spinnerVideoBitrate.adapter as ArrayAdapter<String>
        binding.spinnerVideoBitrate.setSelection(vAdapter.getPosition(vBitrate).coerceAtLeast(0))

        val aBitrate = prefs.getInt("audio_bitrate", 128).toString()
        val aAdapter = binding.spinnerAudioBitrate.adapter as ArrayAdapter<String>
        binding.spinnerAudioBitrate.setSelection(aAdapter.getPosition(aBitrate).coerceAtLeast(0))

        // Codec
        val useHevc = prefs.getBoolean("use_hevc", false)
        binding.spinnerCodec.setSelection(if(useHevc) 1 else 0)

        // Volumes (0-200 slider mapped to 0.0-2.0 float)
        val micVol = (prefs.getFloat("mic_volume", 1.0f) * 100).toInt()
        val intVol = (prefs.getFloat("internal_volume", 1.0f) * 100).toInt()
        binding.seekMicVolume.progress = micVol
        binding.seekInternalVolume.progress = intVol

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
            
            // Novos recursos
            putBoolean("use_hevc", binding.spinnerCodec.selectedItemPosition == 1)
            putFloat("mic_volume", binding.seekMicVolume.progress / 100f)
            putFloat("internal_volume", binding.seekInternalVolume.progress / 100f)
            
            apply()
        }
        Toast.makeText(this, "Salvo!", Toast.LENGTH_SHORT).show()
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateImagePreview(bitmap: android.graphics.Bitmap? = null) {
        if (bitmap != null) {
            binding.imgPreview.setImageBitmap(bitmap)
            binding.imgPreview.visibility = View.VISIBLE
            binding.btnRemoveImage.visibility = View.VISIBLE
            binding.txtImageStatus.text = "Imagem Ativa"
        } else {
            val file = File(filesDir, "pause_image.png")
            if (file.exists()) {
                val saved = BitmapFactory.decodeFile(file.absolutePath)
                binding.imgPreview.setImageBitmap(saved)
                binding.imgPreview.visibility = View.VISIBLE
                binding.btnRemoveImage.visibility = View.VISIBLE
                binding.txtImageStatus.text = "Imagem Ativa"
            } else {
                binding.imgPreview.visibility = View.GONE
                binding.btnRemoveImage.visibility = View.GONE
                binding.txtImageStatus.text = "Nenhuma imagem selecionada"
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
