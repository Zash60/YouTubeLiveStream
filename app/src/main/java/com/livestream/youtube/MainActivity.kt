package com.livestream.youtube

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.livestream.youtube.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var isStreaming = false

    // Callback da Captura de Tela
    private val screenCaptureRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startStreamingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Permissão de captura negada", Toast.LENGTH_SHORT).show()
        }
    }

    // Callback da Permissão de Janela Flutuante (Overlay)
    private val overlayPermissionRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasOverlayPermission()) {
            Toast.makeText(this, "Permissão de Widget concedida!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissão de Widget necessária para o controle flutuante", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
            as MediaProjectionManager

        setupUI()
        checkPermissions()
        loadSavedSettings()
    }

    private fun setupUI() {
        binding.btnStartStream.setOnClickListener {
            if (!isStreaming) {
                if (validateInputs()) {
                    // Verificar permissão de sobreposição antes de iniciar
                    if (checkAndRequestOverlayPermission()) {
                        saveSettings()
                        requestScreenCapture()
                    }
                }
            } else {
                stopStreaming()
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnPasteKey.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                binding.etStreamKey.setText(clip.getItemAt(0).text.toString())
            }
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun checkAndRequestOverlayPermission(): Boolean {
        if (!hasOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionRequest.launch(intent)
            return false
        }
        return true
    }

    private fun validateInputs(): Boolean {
        val streamKey = binding.etStreamKey.text.toString().trim()
        if (streamKey.isEmpty()) {
            binding.etStreamKey.error = "Insira a chave de transmissão"
            return false
        }
        return true
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("stream_key", binding.etStreamKey.text.toString().trim())
            putString("rtmp_url", binding.etRtmpUrl.text.toString().trim()
                .ifEmpty { "rtmp://a.rtmp.youtube.com/live2" })
            apply()
        }
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
        binding.etStreamKey.setText(prefs.getString("stream_key", ""))
        binding.etRtmpUrl.setText(prefs.getString("rtmp_url", "rtmp://a.rtmp.youtube.com/live2"))
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                1001
            )
        }
    }

    private fun requestScreenCapture() {
        screenCaptureRequest.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startStreamingService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, StreamingService::class.java).apply {
            action = StreamingService.ACTION_START
            putExtra(StreamingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(StreamingService.EXTRA_DATA, data)
        }
        
        ContextCompat.startForegroundService(this, serviceIntent)
        
        isStreaming = true
        updateUI()
        
        // Minimizar app para mostrar gameplay
        moveTaskToBack(true)
    }

    private fun stopStreaming() {
        val serviceIntent = Intent(this, StreamingService::class.java).apply {
            action = StreamingService.ACTION_STOP
        }
        startService(serviceIntent)
        isStreaming = false
        updateUI()
    }

    private fun updateUI() {
        if (isStreaming) {
            binding.btnStartStream.text = "⏹ Parar Transmissão"
            binding.btnStartStream.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
            binding.statusText.text = "🔴 AO VIVO"
            binding.statusText.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_red_light)
            )
        } else {
            binding.btnStartStream.text = "▶ Iniciar Transmissão"
            binding.btnStartStream.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_red_light)
            )
            binding.statusText.text = "⚫ Offline"
            binding.statusText.setTextColor(
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isStreaming = StreamingService.isRunning
        updateUI()
    }
}
