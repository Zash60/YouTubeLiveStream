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
import com.livestream.youtube.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var isStreaming = false

    private val screenCaptureRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startStreamingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Permissão negada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupUI()
        checkPermissions()
        loadSavedSettings()
    }

    private fun setupUI() {
        val options = arrayOf("Automático", "Paisagem (Jogos)", "Retrato")
        binding.spinnerOrientation.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        binding.spinnerOrientation.setSelection(1)

        // Botão Google
        binding.btnLoginGoogle.setOnClickListener {
            startActivity(Intent(this, GoogleLoginActivity::class.java))
        }

        binding.btnStartStream.setOnClickListener {
            if (!isStreaming) {
                if (validateInputs() && checkOverlayPermission()) {
                    saveSettings()
                    screenCaptureRequest.launch(mediaProjectionManager.createScreenCaptureIntent())
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
            clipboard.primaryClip?.getItemAt(0)?.text?.let { binding.etStreamKey.setText(it) }
        }
    }

    private fun startStreamingService(code: Int, data: Intent) {
        val ori = when (binding.spinnerOrientation.selectedItemPosition) {
            1 -> "LANDSCAPE"
            2 -> "PORTRAIT"
            else -> "AUTO"
        }
        val i = Intent(this, StreamingService::class.java).apply {
            action = StreamingService.ACTION_START
            putExtra(StreamingService.EXTRA_RESULT_CODE, code)
            putExtra(StreamingService.EXTRA_DATA, data)
            putExtra("orientation_mode", ori)
        }
        ContextCompat.startForegroundService(this, i)
        isStreaming = true
        updateUI()
    }

    private fun stopStreaming() {
        startService(Intent(this, StreamingService::class.java).apply { action = StreamingService.ACTION_STOP })
        isStreaming = false
        updateUI()
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return false
        }
        return true
    }

    private fun validateInputs() = binding.etStreamKey.text.isNotEmpty()

    private fun saveSettings() {
        getSharedPreferences("stream_settings", Context.MODE_PRIVATE).edit().apply {
            putString("stream_key", binding.etStreamKey.text.toString().trim())
            putString("rtmp_url", binding.etRtmpUrl.text.toString().trim())
            apply()
        }
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
        binding.etStreamKey.setText(prefs.getString("stream_key", ""))
        binding.etRtmpUrl.setText(prefs.getString("rtmp_url", "rtmp://a.rtmp.youtube.com/live2"))
    }

    private fun checkPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val req = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (req.isNotEmpty()) ActivityCompat.requestPermissions(this, req.toTypedArray(), 1001)
    }

    private fun updateUI() {
        binding.btnStartStream.text = if (isStreaming) "⏹ Parar" else "▶ Iniciar Transmissão"
        binding.btnStartStream.backgroundTintList = ContextCompat.getColorStateList(this, if (isStreaming) android.R.color.holo_red_dark else android.R.color.holo_red_light)
        binding.statusText.text = if (isStreaming) "🔴 AO VIVO" else "⚫ Offline"
        binding.statusText.setTextColor(if (isStreaming) 0xFFFF0000.toInt() else 0xFF888888.toInt())
    }

    override fun onResume() {
        super.onResume()
        isStreaming = StreamingService.isRunning
        updateUI()
    }
}
