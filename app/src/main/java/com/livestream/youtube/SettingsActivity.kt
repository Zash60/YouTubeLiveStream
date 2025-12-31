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
import android.widget.RadioButton
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

    // Enum para os perfis de qualidade
    enum class QualityProfile(val displayName: String, val width: Int, val height: Int, val fps: Int, val videoBitrate: Int, val audioBitrate: Int) {
        HIGH("Qualidade Alta", 1920, 1080, 60, 8000, 256),
        MEDIUM("Qualidade Média", 1280, 720, 30, 4000, 128),
        LOW("Qualidade Baixa", 854, 480, 24, 2000, 96),
        CUSTOM("Personalizado", 0, 0, 0, 0, 0)
    }

    private var currentQualityProfile = QualityProfile.MEDIUM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configurações"

        setupSpinners()
        setupQualityProfiles()
        setupImagePicker()
        loadSettings()
        setupSaveButton()
    }

    private fun setupImagePicker() {
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

    private fun setupQualityProfiles() {
        binding.rbQualityHigh.setOnClickListener { selectQualityProfile(QualityProfile.HIGH) }
        binding.rbQualityMedium.setOnClickListener { selectQualityProfile(QualityProfile.MEDIUM) }
        binding.rbQualityLow.setOnClickListener { selectQualityProfile(QualityProfile.LOW) }
        binding.rbQualityCustom.setOnClickListener { selectQualityProfile(QualityProfile.CUSTOM) }

        // Aplicar perfil padrão
        selectQualityProfile(QualityProfile.MEDIUM)
    }

    private fun selectQualityProfile(profile: QualityProfile) {
        currentQualityProfile = profile

        // Desmarcar todos os radio buttons
        binding.rbQualityHigh.isChecked = false
        binding.rbQualityMedium.isChecked = false
        binding.rbQualityLow.isChecked = false
        binding.rbQualityCustom.isChecked = false

        // Marcar o selecionado
        when (profile) {
            QualityProfile.HIGH -> binding.rbQualityHigh.isChecked = true
            QualityProfile.MEDIUM -> binding.rbQualityMedium.isChecked = true
            QualityProfile.LOW -> binding.rbQualityLow.isChecked = true
            QualityProfile.CUSTOM -> binding.rbQualityCustom.isChecked = true
        }

        // Aplicar configurações se não for customizado
        if (profile != QualityProfile.CUSTOM) {
            applyQualityProfile(profile)
        }

        // Habilitar/desabilitar campos customizados
        val customEnabled = profile == QualityProfile.CUSTOM
        binding.etCustomVideoBitrate.isEnabled = customEnabled
        binding.etCustomAudioBitrate.isEnabled = customEnabled
        binding.etCustomWidth.isEnabled = customEnabled
        binding.etCustomHeight.isEnabled = customEnabled
        binding.spinnerResolution.isEnabled = !customEnabled
        binding.spinnerFps.isEnabled = !customEnabled
        binding.spinnerVideoBitrate.isEnabled = !customEnabled
        binding.spinnerAudioBitrate.isEnabled = !customEnabled
    }

    private fun applyQualityProfile(profile: QualityProfile) {
        when (profile) {
            QualityProfile.HIGH -> {
                binding.spinnerResolution.setSelection(0) // 1920x1080
                binding.spinnerFps.setSelection(0) // 60
                binding.spinnerVideoBitrate.setSelection(0) // 8000
                binding.spinnerAudioBitrate.setSelection(1) // 256
            }
            QualityProfile.MEDIUM -> {
                binding.spinnerResolution.setSelection(1) // 1280x720
                binding.spinnerFps.setSelection(1) // 30
                binding.spinnerVideoBitrate.setSelection(3) // 4000
                binding.spinnerAudioBitrate.setSelection(3) // 128
            }
            QualityProfile.LOW -> {
                binding.spinnerResolution.setSelection(2) // 854x480
                binding.spinnerFps.setSelection(2) // 24
                binding.spinnerVideoBitrate.setSelection(6) // 2000
                binding.spinnerAudioBitrate.setSelection(4) // 96
            }
            QualityProfile.CUSTOM -> {
                // Não aplicar nada, deixar campos customizados ativos
            }
        }
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

        // Carregar configurações customizadas
        binding.etCustomVideoBitrate.setText(prefs.getString("custom_video_bitrate", ""))
        binding.etCustomAudioBitrate.setText(prefs.getString("custom_audio_bitrate", ""))
        binding.etCustomWidth.setText(prefs.getString("custom_width", ""))
        binding.etCustomHeight.setText(prefs.getString("custom_height", ""))

        // Carregar perfil salvo
        val savedProfile = prefs.getString("quality_profile", "MEDIUM") ?: "MEDIUM"
        currentQualityProfile = QualityProfile.valueOf(savedProfile)
        selectQualityProfile(currentQualityProfile)

        // Carregar imagem salva
        updateImagePreview()
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                saveSettings()
                finish()
            }
        }
    }

    private fun validateInputs(): Boolean {
        if (currentQualityProfile == QualityProfile.CUSTOM) {
            // Validar campos customizados
            val customVideoBitrate = binding.etCustomVideoBitrate.text.toString().trim()
            val customAudioBitrate = binding.etCustomAudioBitrate.text.toString().trim()
            val customWidth = binding.etCustomWidth.text.toString().trim()
            val customHeight = binding.etCustomHeight.text.toString().trim()

            if (customVideoBitrate.isEmpty() || customAudioBitrate.isEmpty() || 
                customWidth.isEmpty() || customHeight.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos customizados", Toast.LENGTH_SHORT).show()
                return false
            }

            val videoBitrate = customVideoBitrate.toIntOrNull()
            val audioBitrate = customAudioBitrate.toIntOrNull()
            val width = customWidth.toIntOrNull()
            val height = customHeight.toIntOrNull()

            if (videoBitrate == null || videoBitrate < 500 || videoBitrate > 20000) {
                Toast.makeText(this, "Bitrate de vídeo deve estar entre 500 e 20000 kbps", Toast.LENGTH_SHORT).show()
                return false
            }

            if (audioBitrate == null || audioBitrate < 64 || audioBitrate > 320) {
                Toast.makeText(this, "Bitrate de áudio deve estar entre 64 e 320 kbps", Toast.LENGTH_SHORT).show()
                return false
            }

            if (width == null || width < 320 || width > 3840) {
                Toast.makeText(this, "Largura deve estar entre 320 e 3840 pixels", Toast.LENGTH_SHORT).show()
                return false
            }

            if (height == null || height < 240 || height > 2160) {
                Toast.makeText(this, "Altura deve estar entre 240 e 2160 pixels", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)
        
        var width: Int
        var height: Int
        var fps: Int
        var videoBitrate: Int
        var audioBitrate: Int

        if (currentQualityProfile == QualityProfile.CUSTOM) {
            // Usar valores customizados
            width = binding.etCustomWidth.text.toString().toInt()
            height = binding.etCustomHeight.text.toString().toInt()
            fps = 30 // Padrão para customizado
            videoBitrate = binding.etCustomVideoBitrate.text.toString().toInt()
            audioBitrate = binding.etCustomAudioBitrate.text.toString().toInt()

            // Salvar configurações customizadas
            prefs.edit().apply {
                putString("custom_video_bitrate", binding.etCustomVideoBitrate.text.toString())
                putString("custom_audio_bitrate", binding.etCustomAudioBitrate.text.toString())
                putString("custom_width", binding.etCustomWidth.text.toString())
                putString("custom_height", binding.etCustomHeight.text.toString())
                apply()
            }
        } else {
            // Usar valores dos spinners
            val resolution = binding.spinnerResolution.selectedItem.toString().split("x")
            width = resolution[0].toInt()
            height = resolution[1].toInt()
            fps = binding.spinnerFps.selectedItem.toString().toInt()
            videoBitrate = binding.spinnerVideoBitrate.selectedItem.toString().toInt()
            audioBitrate = binding.spinnerAudioBitrate.selectedItem.toString().toInt()
        }
        
        prefs.edit().apply {
            putInt("width", width)
            putInt("height", height)
            putInt("fps", fps)
            putInt("video_bitrate", videoBitrate)
            putInt("audio_bitrate", audioBitrate)
            putInt("sample_rate", binding.spinnerSampleRate.selectedItem.toString().toInt())
            putBoolean("adaptive_bitrate", binding.switchAdaptiveBitrate.isChecked)
            putString("quality_profile", currentQualityProfile.name)
            apply()
        }

        Toast.makeText(this, "Configurações salvas com sucesso!", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
