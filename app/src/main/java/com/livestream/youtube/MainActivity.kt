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
import android.view.View
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.livestream.youtube.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var isStreaming = false
    private var isRecording = false
    private var isGoogleConnected = false

    private val screenCaptureRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startStreamingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, R.string.capture_permission, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupUI()
        checkPermissions()
        // Carrega as configuracoes iniciais
        loadSavedSettings()
        // Enable full screen immersive mode
        enableImmersiveMode()
        // Check for existing Google account
        checkGoogleSignIn()
    }

    private fun checkGoogleSignIn() {
        // Check if user is already signed in
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            // User is already signed in
            isGoogleConnected = true
            updateGoogleButtonState()
        }
    }

    private fun updateGoogleButtonState() {
        if (isGoogleConnected) {
            binding.btnLoginGoogle.text = getString(R.string.login_google) + " ✓"
        } else {
            binding.btnLoginGoogle.text = getString(R.string.login_google)
        }
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Atualiza o intent para processar no onResume
    }

    override fun onResume() {
        super.onResume()
        // 1. Atualiza o status do servico
        isStreaming = StreamingService.isRunning
        updateUI()
        
        // 2. Recarrega as chaves (caso tenham mudado via Google Login)
        loadSavedSettings()

        // 3. Check Google sign-in status
        checkGoogleSignIn()

        // 4. Verifica se deve iniciar automaticamente (vindo do Google Login)
        if (intent?.getBooleanExtra("AUTO_START_STREAM", false) == true) {
            if (!isStreaming) {
                // Limpa o flag para nao ficar iniciando em loop se girar a tela
                intent?.removeExtra("AUTO_START_STREAM")
                
                // Verifica se tem chave de stream e URL configuradas
                val prefs = getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
                val streamKey = prefs.getString("stream_key", "") ?: ""
                val rtmpUrl = prefs.getString("rtmp_url", "") ?: ""
                
                if (streamKey.isBlank()) {
                    Toast.makeText(this, R.string.stream_key_required, Toast.LENGTH_SHORT).show()
                } else if (rtmpUrl.isBlank()) {
                    Toast.makeText(this, "URL RTMP não configurada!", Toast.LENGTH_SHORT).show()
                } else {
                    binding.btnStartStream.performClick()
                }
                } else {
                    // Simula o clique no botao para pedir permissao
                    binding.btnStartStream.performClick()
                }
            }
    }

    private fun setupUI() {
        val options = arrayOf(
            getString(R.string.orientation_auto),
            getString(R.string.orientation_landscape),
            getString(R.string.orientation_portrait)
        )
        binding.spinnerOrientation.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        binding.spinnerOrientation.setSelection(1)

        // Botao Google
        binding.btnLoginGoogle.setOnClickListener {
            startActivity(Intent(this, GoogleLoginActivity::class.java))
        }

        binding.btnStartStream.setOnClickListener {
            if (!isStreaming) {
                if (validateInputs()) {
                    if (checkOverlayPermission()) {
                        saveSettings()
                        // Aqui pede a permissao de captura de tela
                        screenCaptureRequest.launch(mediaProjectionManager.createScreenCaptureIntent())
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
        // Move o app para o fundo para mostrar o jogo/tela
        moveTaskToBack(true)
    }

    private fun stopStreaming() {
        startService(Intent(this, StreamingService::class.java).apply { action = StreamingService.ACTION_STOP })
        isStreaming = false
        updateUI()
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return false
        }
        return true
    }

    private fun validateInputs(): Boolean {
        val key = binding.etStreamKey.text.toString().trim()
        if (key.isEmpty()) {
            binding.etStreamKey.error = getString(R.string.stream_key_required)
            return false
        }
        return true
    }

    private fun saveSettings() {
        getSharedPreferences("stream_settings", Context.MODE_PRIVATE).edit().apply {
            putString("stream_key", binding.etStreamKey.text.toString().trim())
            putString("rtmp_url", binding.etRtmpUrl.text.toString().trim())
            apply()
        }
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
        val key = prefs.getString("stream_key", "")
        val url = prefs.getString("rtmp_url", "rtmp://a.rtmp.youtube.com/live2")
        
        binding.etStreamKey.setText(key)
        binding.etRtmpUrl.setText(url)
    }

    private fun checkPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val req = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (req.isNotEmpty()) ActivityCompat.requestPermissions(this, req.toTypedArray(), 1001)
    }

    private fun updateUI() {
        if (isStreaming) {
            binding.btnStartStream.text = getString(R.string.stop_stream)
            binding.btnStartStream.backgroundTintList = ContextCompat.getColorStateList(this, R.color.error)
            binding.statusText.text = getString(R.string.online)
            binding.statusText.setTextColor(ContextCompat.getColor(this, R.color.youtube_red))
            binding.btnLoginGoogle.isEnabled = false
            
            // Check if recording is enabled
            val prefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)
            isRecording = prefs.getBoolean("local_record", false)
            
            if (isRecording) {
                binding.recordingStatusLayout.visibility = View.VISIBLE
                binding.recordingStatusText.text = getString(R.string.recording_started)
            } else {
                binding.recordingStatusLayout.visibility = View.GONE
            }
        } else {
            binding.btnStartStream.text = getString(R.string.start_stream)
            binding.btnStartStream.backgroundTintList = ContextCompat.getColorStateList(this, R.color.youtube_red)
            binding.statusText.text = getString(R.string.offline)
            binding.statusText.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            binding.btnLoginGoogle.isEnabled = true
            binding.recordingStatusLayout.visibility = View.GONE
        }
    }
}
