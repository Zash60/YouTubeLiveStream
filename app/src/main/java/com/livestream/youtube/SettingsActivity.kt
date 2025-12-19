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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.livestream.youtube.databinding.ActivitySettingsBinding
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    // Callback para pegar a imagem da galeria
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

        setupSpinners()
        setupImagePicker()
        loadSettings()
        setupSaveButton()
    }

    private fun setupImagePicker() {
        binding.btnSelectImage.setOnClickListener {
            // Verificar permissões
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
            binding.imgPreview.visibility = View.GONE
            binding.btnRemoveImage.visibility = View.GONE
            binding.txtImageStatus.text = "Nenhuma imagem selecionada (Usará Tela Preta)"
            Toast.makeText(this, "Imagem removida", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImage.launch(intent)
    }

    private fun saveImageInternal(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            
            // Salvar no armazenamento interno do app
            val file = File(filesDir, "pause_image.png")
            val out = FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            inputStream?.close()

            updateImagePreview(bitmap)
            Toast.makeText(this, "Imagem salva com sucesso!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao salvar imagem", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun updateImagePreview(bitmap: android.graphics.Bitmap? = null) {
        if (bitmap != null) {
            binding.imgPreview.setImageBitmap(bitmap)
            binding.imgPreview.visibility = View.VISIBLE
            binding.btnRemoveImage.visibility = View.VISIBLE
            binding.txtImageStatus.text = "Imagem personalizada ativa"
        } else {
            val file = File(filesDir, "pause_image.png")
            if (file.exists()) {
                val savedBitmap = BitmapFactory.decodeFile(file.absolutePath)
                binding.imgPreview.setImageBitmap(savedBitmap)
                binding.imgPreview.visibility = View.VISIBLE
                binding.btnRemoveImage.visibility = View.VISIBLE
                binding.txtImageStatus.text = "Imagem personalizada ativa"
            } else {
                binding.imgPreview.visibility = View.GONE
                binding.btnRemoveImage.visibility = View.GONE
                binding.txtImageStatus.text = "Nenhuma imagem selecionada (Usará Tela Preta)"
            }
        }
    }

    private fun setupSpinners() {
        // Resoluções
        val resolutions = arrayOf("1920x1080", "1280x720", "854x480", "640x360")
        binding.spinnerResolution.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resolutions)

        // FPS
        val fpsOptions = arrayOf("60", "30", "24")
        binding.spinnerFps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fpsOptions)

        // Video Bitrate
        val videoBitrateOptions = arrayOf("8000", "6000", "4500", "4000", "3000", "2500", "2000", "1500")
        binding.spinnerVideoBitrate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, videoBitrateOptions)

        // Audio Bitrate
        val audioBitrateOptions = arrayOf("320", "256", "192", "128", "96")
        binding.spinnerAudioBitrate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, audioBitrateOptions)

        // Sample Rate
        val sampleRateOptions = arrayOf("48000", "44100", "22050")
        binding.spinnerSampleRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sampleRateOptions)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)
        
        val width = prefs.getInt("width", 1280)
        val height = prefs.getInt("height", 720)
        val resolution = "${width}x${height}"
        val resolutions = arrayOf("1920x1080", "1280x720", "854x480", "640x360")
        binding.spinnerResolution.setSelection(resolutions.indexOf(resolution).coerceAtLeast(0))
        
        val fps = prefs.getInt("fps", 30)
        val fpsOptions = arrayOf("60", "30", "24")
        binding.spinnerFps.setSelection(fpsOptions.indexOf(fps.toString()).coerceAtLeast(0))
        
        val videoBitrate = prefs.getInt("video_bitrate", 4000)
        val videoBitrateOptions = arrayOf("8000", "6000", "4500", "4000", "3000", "2500", "2000", "1500")
        binding.spinnerVideoBitrate.setSelection(videoBitrateOptions.indexOf(videoBitrate.toString()).coerceAtLeast(0))
        
        val audioBitrate = prefs.getInt("audio_bitrate", 128)
        val audioBitrateOptions = arrayOf("320", "256", "192", "128", "96")
        binding.spinnerAudioBitrate.setSelection(audioBitrateOptions.indexOf(audioBitrate.toString()).coerceAtLeast(0))
        
        val sampleRate = prefs.getInt("sample_rate", 44100)
        val sampleRateOptions = arrayOf("48000", "44100", "22050")
        binding.spinnerSampleRate.setSelection(sampleRateOptions.indexOf(sampleRate.toString()).coerceAtLeast(0))

        val adaptiveBitrate = prefs.getBoolean("adaptive_bitrate", true)
        binding.switchAdaptiveBitrate.isChecked = adaptiveBitrate

        // NOVO: Carregar opção de Bola Invisível
        val invisibleWidget = prefs.getBoolean("invisible_widget", false)
        binding.switchInvisibleWidget.isChecked = invisibleWidget

        // Carregar imagem salva
        updateImagePreview()
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            saveSettings()
            finish()
        }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)
        val resolution = binding.spinnerResolution.selectedItem.toString().split("x")
        val width = resolution[0].toInt()
        val height = resolution[1].toInt()
        
        prefs.edit().apply {
            putInt("width", width)
            putInt("height", height)
            putInt("fps", binding.spinnerFps.selectedItem.toString().toInt())
            putInt("video_bitrate", binding.spinnerVideoBitrate.selectedItem.toString().toInt())
            putInt("audio_bitrate", binding.spinnerAudioBitrate.selectedItem.toString().toInt())
            putInt("sample_rate", binding.spinnerSampleRate.selectedItem.toString().toInt())
            putBoolean("adaptive_bitrate", binding.switchAdaptiveBitrate.isChecked)
            
            // NOVO: Salvar opção de Bola Invisível
            putBoolean("invisible_widget", binding.switchInvisibleWidget.isChecked)
            
            apply()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
