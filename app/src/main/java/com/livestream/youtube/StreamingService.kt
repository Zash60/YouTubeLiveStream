package com.livestream.youtube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.utils.gl.TranslateTo
import com.pedro.encoder.input.gl.render.filters.NoFilterRender
import java.io.File

class StreamingService : Service() {

    private var rtmpDisplay: RtmpDisplay? = null
    private var resultCode: Int = 0
    private var data: Intent? = null
    
    private var orientationMode = "AUTO"
    private var useAdaptiveBitrate = true
    private var targetVideoBitrate = 4000 * 1000
    private var lastBitrateChange = 0L
    
    private var pauseBitmap: Bitmap? = null
    private var imageFilter: ImageObjectFilterRender? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        loadPauseImage()
    }

    private fun loadPauseImage() {
        try {
            val file = File(filesDir, "pause_image.png")
            if (file.exists()) {
                pauseBitmap = BitmapFactory.decodeFile(file.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro imagem", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                
                orientationMode = intent.getStringExtra("orientation_mode") ?: "AUTO"
                
                startForeground(NOTIFICATION_ID, createNotification())
                startStreaming()
                
                try {
                    startService(Intent(this, FloatingControlService::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Erro widget", e)
                }
            }
            ACTION_STOP -> {
                stopStreaming()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopService(Intent(this, FloatingControlService::class.java))
                stopSelf()
            }
            ACTION_MUTE_AUDIO -> {
                val shouldMute = intent.getBooleanExtra("mute", false)
                handleAudioMute(shouldMute)
            }
            ACTION_PRIVACY_MODE -> {
                val isPrivacyOn = intent.getBooleanExtra("privacy", false)
                handlePrivacyMode(isPrivacyOn)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleAudioMute(isMuted: Boolean) {
        rtmpDisplay?.let { display ->
            if (display.isStreaming) {
                if (isMuted) display.disableAudio() else display.enableAudio()
            }
        }
    }

    private fun handlePrivacyMode(isPrivacyOn: Boolean) {
        rtmpDisplay?.let { display ->
            if (display.isStreaming) {
                try {
                    if (isPrivacyOn) {
                        if (pauseBitmap != null) {
                            if (imageFilter == null) {
                                imageFilter = ImageObjectFilterRender()
                                imageFilter?.setImage(pauseBitmap)
                            }
                            imageFilter?.setScale(100f, 100f)
                            imageFilter?.setPosition(TranslateTo.CENTER)
                            imageFilter?.let { display.glInterface.setFilter(it) }
                        } else {
                            display.glInterface.muteVideo()
                        }
                        display.disableAudio()
                    } else {
                        if (pauseBitmap != null) {
                            display.glInterface.setFilter(NoFilterRender())
                        } else {
                            display.glInterface.unMuteVideo()
                        }
                        display.enableAudio()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro privacy", e)
                }
            }
        }
    }

    private fun startStreaming() {
        val prefs = getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
        var streamKey = prefs.getString("stream_key", "") ?: ""
        var rtmpUrl = prefs.getString("rtmp_url", "rtmp://a.rtmp.youtube.com/live2") ?: "rtmp://a.rtmp.youtube.com/live2"
        
        // --- CORREÇÃO CRÍTICA DE URL E CHAVE ---
        rtmpUrl = rtmpUrl.trim()
        streamKey = streamKey.trim()

        // Remove barra final se o usuário colocou acidentalmente
        if (rtmpUrl.endsWith("/")) {
            rtmpUrl = rtmpUrl.dropLast(1)
        }

        // Se a URL estiver "quebrada" (como no log anterior), forçar a correta
        if (rtmpUrl.contains("rtmp://a.rtmp.") && rtmpUrl.length < 25) {
            Log.w(TAG, "URL inválida detectada ($rtmpUrl), corrigindo automaticamente...")
            rtmpUrl = "rtmp://a.rtmp.youtube.com/live2"
        }
        // ---------------------------------------

        val videoPrefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)
        var width = videoPrefs.getInt("width", 1280)
        var height = videoPrefs.getInt("height", 720)
        
        // Ajuste de Orientação
        val systemOrientation = resources.configuration.orientation
        when (orientationMode) {
            "LANDSCAPE" -> if (width < height) { val t = width; width = height; height = t }
            "PORTRAIT" -> if (width > height) { val t = width; width = height; height = t }
            else -> { 
                if (systemOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                    if (width < height) { val t = width; width = height; height = t }
                } else {
                    if (width > height) { val t = width; width = height; height = t }
                }
            }
        }

        val fps = videoPrefs.getInt("fps", 30)
        targetVideoBitrate = videoPrefs.getInt("video_bitrate", 4000) * 1000
        val audioBitrate = videoPrefs.getInt("audio_bitrate", 128) * 1000
        val sampleRate = videoPrefs.getInt("sample_rate", 44100)
        useAdaptiveBitrate = videoPrefs.getBoolean("adaptive_bitrate", true)
        val dpi = resources.displayMetrics.densityDpi

        try {
            rtmpDisplay = RtmpDisplay(this, true, object : ConnectChecker {
                override fun onConnectionStarted(url: String) {}
                override fun onConnectionSuccess() { 
                    isRunning = true 
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Conectado com sucesso! 🟢", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onConnectionFailed(reason: String) { 
                    Log.e(TAG, "FALHA CONEXÃO: $reason")
                    Handler(Looper.getMainLooper()).post {
                        // Mensagem mais detalhada para debug
                        Toast.makeText(applicationContext, "Erro: Não foi possível conectar ao YouTube.\nVerifique se a chave está correta.", Toast.LENGTH_LONG).show()
                    }
                    stopStreaming() 
                }
                
                override fun onNewBitrate(bitrate: Long) {
                    if (useAdaptiveBitrate && isRunning) handleAdaptiveBitrate(bitrate)
                    
                    val healthColor = when {
                        bitrate >= targetVideoBitrate * 0.8 -> "GREEN"
                        bitrate >= targetVideoBitrate * 0.5 -> "YELLOW"
                        else -> "RED"
                    }
                    
                    startService(Intent(this@StreamingService, FloatingControlService::class.java).apply {
                        action = "UPDATE_HEALTH"
                        putExtra("health_color", healthColor)
                    })
                }
                
                override fun onDisconnect() { 
                    isRunning = false 
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Desconectado", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onAuthError() {
                     Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Erro na Chave de Transmissão!", Toast.LENGTH_LONG).show()
                    }
                    stopStreaming()
                }
                override fun onAuthSuccess() {}
            })

            rtmpDisplay?.let { display ->
                data?.let { display.setIntentResult(resultCode, it) }

                val prepareVideo = display.prepareVideo(width, height, fps, targetVideoBitrate, 0, dpi)
                
                var prepareAudio = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        prepareAudio = display.prepareInternalAudio(audioBitrate, sampleRate, true, false, false)
                    } catch (e: Exception) {
                        prepareAudio = display.prepareAudio(audioBitrate, sampleRate, true)
                    }
                } else {
                    prepareAudio = display.prepareAudio(audioBitrate, sampleRate, true)
                }

                if (prepareVideo && prepareAudio) {
                    // Monta a URL final
                    val finalUrl = "$rtmpUrl/$streamKey"
                    Log.d(TAG, "Tentando conectar em: $rtmpUrl/HIDDEN_KEY")
                    display.startStream(finalUrl)
                } else {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Erro hardware: Reinicie o app", Toast.LENGTH_SHORT).show()
                    }
                    stopStreaming()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal", e)
            stopStreaming()
        }
    }

    private fun handleAdaptiveBitrate(currentBitrate: Long) {
        val now = System.currentTimeMillis()
        if (now - lastBitrateChange < 2000) return

        rtmpDisplay?.let { display ->
            try {
                if (currentBitrate < targetVideoBitrate * 0.7) {
                    val newBitrate = (targetVideoBitrate * 0.8).toInt()
                    display.setVideoBitrateOnFly(newBitrate)
                    lastBitrateChange = now
                } else if (currentBitrate > targetVideoBitrate * 0.9) {
                     display.setVideoBitrateOnFly(targetVideoBitrate)
                     lastBitrateChange = now
                }
            } catch (e: Exception) {}
        }
    }

    private fun stopStreaming() {
        try {
            rtmpDisplay?.let { display ->
                if (display.isStreaming) display.stopStream()
                if (display.isRecording) display.stopRecord()
            }
            rtmpDisplay = null
            isRunning = false
            try {
                stopService(Intent(this, FloatingControlService::class.java))
            } catch (e: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Erro stop", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Live Stream", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, StreamingService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 Live YouTube")
            .setContentText("Transmitindo...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_media_pause, "Parar", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    companion object {
        const val TAG = "StreamingService"
        const val ACTION_START = "com.livestream.youtube.START"
        const val ACTION_STOP = "com.livestream.youtube.STOP"
        const val ACTION_MUTE_AUDIO = "com.livestream.youtube.MUTE_AUDIO"
        const val ACTION_PRIVACY_MODE = "com.livestream.youtube.PRIVACY_MODE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val CHANNEL_ID = "streaming_channel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
    }
}
