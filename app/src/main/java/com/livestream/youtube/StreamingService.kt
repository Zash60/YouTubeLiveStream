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
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
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
    
    // Variável para armazenar a escolha do usuário
    private var orientationMode = "AUTO"

    private var useAdaptiveBitrate = true
    private var targetVideoBitrate = 4000 * 1000
    private var lastBitrateChange = 0L
    
    // Não mantemos o bitmap em cache global para evitar problemas de rotação/memória
    // Carregamos na hora que precisa

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
                    Log.e(TAG, "Erro ao iniciar widget", e)
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
                        // 1. Tenta carregar a imagem na hora (fresco)
                        val currentBitmap = loadBitmapFromStorage()

                        if (currentBitmap != null) {
                            // 2. Cria um novo filtro do zero para garantir a escala correta
                            val filter = ImageObjectFilterRender()
                            filter.setImage(currentBitmap)
                            filter.setScale(100f, 100f) // Força preencher 100% da tela
                            filter.setPosition(TranslateTo.CENTER)
                            
                            display.glInterface.setFilter(filter)
                            Log.d(TAG, "Privacidade: Imagem aplicada com sucesso")
                        } else {
                            // 3. FALLBACK: Se a imagem falhar (nula), usa TELA PRETA
                            // Isso garante que o jogo não apareça se a imagem der erro
                            Log.e(TAG, "Privacidade: Imagem não encontrada, usando Tela Preta")
                            display.glInterface.muteVideo()
                        }
                        
                        // Sempre muta o áudio na privacidade
                        display.disableAudio()
                    } else {
                        // Desativar Privacidade
                        // Remove qualquer filtro
                        display.glInterface.setFilter(NoFilterRender())
                        // Garante que o vídeo não está mudo (caso tenha caído no fallback)
                        display.glInterface.unMuteVideo()
                        
                        Log.d(TAG, "Privacidade: Desativada")
                        display.enableAudio()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro crítico na privacidade. Forçando tela preta.", e)
                    // Em caso de erro (crash do filtro), força tela preta
                    display.glInterface.muteVideo()
                    display.disableAudio()
                }
            }
        }
    }

    private fun loadBitmapFromStorage(): Bitmap? {
        return try {
            val file = File(filesDir, "pause_image.png")
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler arquivo de imagem", e)
            null
        }
    }

    private fun startStreaming() {
        val prefs = getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
        val streamKey = prefs.getString("stream_key", "") ?: ""
        val rtmpUrl = prefs.getString("rtmp_url", "rtmp://a.rtmp.youtube.com/live2") ?: ""
        
        val videoPrefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)
        var width = videoPrefs.getInt("width", 1280)
        var height = videoPrefs.getInt("height", 720)
        
        // Lógica de Orientação (Mantida a mesma, pois estava correta)
        val systemOrientation = resources.configuration.orientation
        
        when (orientationMode) {
            "LANDSCAPE" -> {
                if (width < height) { val t = width; width = height; height = t }
            }
            "PORTRAIT" -> {
                if (width > height) { val t = width; width = height; height = t }
            }
            else -> { // AUTO
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
                override fun onConnectionSuccess() { isRunning = true }
                override fun onConnectionFailed(reason: String) { stopStreaming() }
                override fun onNewBitrate(bitrate: Long) {
                    if (useAdaptiveBitrate && isRunning) handleAdaptiveBitrate(bitrate)
                }
                override fun onDisconnect() { isRunning = false }
                override fun onAuthError() {}
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
                    display.startStream("$rtmpUrl/$streamKey")
                    Log.d(TAG, "Streaming started: $orientationMode ${width}x${height}")
                } else {
                    stopStreaming()
                }
            }
        } catch (e: Exception) {
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
                } 
                else if (currentBitrate > targetVideoBitrate * 0.9) {
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
            stopService(Intent(this, FloatingControlService::class.java))
        } catch (e: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Live Stream",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, StreamingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 Live Streaming")
            .setContentText("Tap to open")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
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
