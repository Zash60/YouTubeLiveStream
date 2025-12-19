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
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat // NEW IMPORT for edge-to-edge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
            Toast.makeText(this, "Permissão concedida!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // NEW: Enable edge-to-edge for Android 15+ compatibility
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
            as MediaProjectionManager

        setupUI()
        setupOrientationSpinner() // NOVO
        checkPermissions()
        loadSavedSettings()
    }

    // NOVO: Configura o Spinner de Orientação
    private fun setupOrientationSpinner() {
        val options = arrayOf("Automático (Detectar)", "Deitado (Paisagem/Jogos)", "Em Pé (Retrato)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        binding.spinnerOrientation.adapter = adapter
        // Padrão: Deitado (index 1) pois é o mais comum para jogos
        binding.spinnerOrientation.setSelection(1)
    }

    private fun setupUI() {
        binding.btnStartStream.setOnClickListener {
            if (!isStreaming) {
                if (validateInputs()) {
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
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1001)
        }
    }

    private fun requestScreenCapture() {
        screenCaptureRequest.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startStreamingService(resultCode: Int, data: Intent) {
        // Pega a opção selecionada no Spinner
        val selectedOrientationIndex = binding.spinnerOrientation.selectedItemPosition
        val orientationMode = when (selectedOrientationIndex) {
            1 -> "LANDSCAPE" // Deitado
            2 -> "PORTRAIT"  // Em pé
            else -> "AUTO"
        }

        val serviceIntent = Intent(this, StreamingService::class.java).apply {
            action = StreamingService.ACTION_START
            putExtra(StreamingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(StreamingService.EXTRA_DATA, data)
            putExtra("orientation_mode", orientationMode) // Envia para o serviço
        }
        
        ContextCompat.startForegroundService(this, serviceIntent)
        
        isStreaming = true
        updateUI()
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
            binding.btnStartStream.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            binding.statusText.text = "🔴 AO VIVO"
            binding.statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            binding.spinnerOrientation.isEnabled = false // Trava a mudança durante a live
        } else {
            binding.btnStartStream.text = "▶ Iniciar Transmissão"
            binding.btnStartStream.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            binding.statusText.text = "⚫ Offline"
            binding.statusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            binding.spinnerOrientation.isEnabled = true
        }
    }

    override fun onResume() {
        super.onResume()
        isStreaming = StreamingService.isRunning
        updateUI()
    }
}
