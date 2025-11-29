package com.livestream.youtube

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.livestream.youtube.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configurações"

        setupSpinners()
        loadSettings()
        setupSaveButton()
    }

    private fun setupSpinners() {
        // Resoluções
        val resolutions = arrayOf("1920x1080", "1280x720", "854x480", "640x360")
        binding.spinnerResolution.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, resolutions
        )

        // FPS
        val fpsOptions = arrayOf("60", "30", "24")
        binding.spinnerFps.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, fpsOptions
        )

        // Video Bitrate
        val videoBitrateOptions = arrayOf("8000", "6000", "4500", "4000", "3000", "2500", "2000", "1500")
        binding.spinnerVideoBitrate.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, videoBitrateOptions
        )

        // Audio Bitrate
        val audioBitrateOptions = arrayOf("320", "256", "192", "128", "96")
        binding.spinnerAudioBitrate.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, audioBitrateOptions
        )

        // Sample Rate
        val sampleRateOptions = arrayOf("48000", "44100", "22050")
        binding.spinnerSampleRate.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, sampleRateOptions
        )
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
        binding.spinnerVideoBitrate.setSelection(
            videoBitrateOptions.indexOf(videoBitrate.toString()).coerceAtLeast(0)
        )
        
        val audioBitrate = prefs.getInt("audio_bitrate", 128)
        val audioBitrateOptions = arrayOf("320", "256", "192", "128", "96")
        binding.spinnerAudioBitrate.setSelection(
            audioBitrateOptions.indexOf(audioBitrate.toString()).coerceAtLeast(0)
        )
        
        val sampleRate = prefs.getInt("sample_rate", 44100)
        val sampleRateOptions = arrayOf("48000", "44100", "22050")
        binding.spinnerSampleRate.setSelection(
            sampleRateOptions.indexOf(sampleRate.toString()).coerceAtLeast(0)
        )
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
            apply()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
