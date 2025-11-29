package com.livestream.youtube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.common.ConnectChecker

class StreamingService : Service() {

    private var rtmpDisplay: RtmpDisplay? = null
    private var resultCode: Int = 0
    private var data: Intent? = null
    
    // Variáveis para controle de bitrate
    private var useAdaptiveBitrate = true
    private var targetVideoBitrate = 4000 * 1000
    private var lastBitrateChange = 0L

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
                startForeground(NOTIFICATION_ID, createNotification())
                startStreaming()
                
                try {
                    startService(Intent(this, FloatingControlService::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao iniciar Widget", e)
                }
            }
            ACTION_STOP -> {
                stopStreaming()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopService(Intent(this, FloatingControlService::class.java))
                stopSelf()
            }
            ACTION_MUTE -> {
                val shouldMute = intent.getBooleanExtra("mute", false)
                handleMute(shouldMute)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleMute(isMuted: Boolean) {
        rtmpDisplay?.let { display ->
            if (display.isStreaming) {
                if (isMuted) {
                    display.disableAudio()
                    Log.d(TAG, "Áudio Mutado")
                } else {
                    display.enableAudio()
                    Log.d(TAG, "Áudio Ativado")
                }
            }
        }
    }

    private fun startStreaming() {
        val prefs = getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
        val streamKey = prefs.getString("stream_key", "") ?: ""
        val rtmpUrl = prefs.getString("rtmp_url", "rtmp://a.rtmp.youtube.com/live2") ?: ""
        
        val videoPrefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)
        val width = videoPrefs.getInt("width", 1280)
        val height = videoPrefs.getInt("height", 720)
        val fps = videoPrefs.getInt("fps", 30)
        targetVideoBitrate = videoPrefs.getInt("video_bitrate", 4000) * 1000
        val audioBitrate = videoPrefs.getInt("audio_bitrate", 128) * 1000
        val sampleRate = videoPrefs.getInt("sample_rate", 44100)
        useAdaptiveBitrate = videoPrefs.getBoolean("adaptive_bitrate", true)

        val dpi = resources.displayMetrics.densityDpi

        try {
            rtmpDisplay = RtmpDisplay(this, true, object : ConnectChecker {
                override fun onConnectionStarted(url: String) { 
                    Log.d(TAG, "Conexão iniciada: $url") 
                }
                override fun onConnectionSuccess() { 
                    Log.d(TAG, "Conectado com sucesso!") 
                    isRunning = true
                }
                override fun onConnectionFailed(reason: String) { 
                    Log.e(TAG, "Falha na conexão: $reason")
                    stopStreaming() 
                }
                override fun onNewBitrate(bitrate: Long) {
                    if (useAdaptiveBitrate && isRunning) {
                        handleAdaptiveBitrate(bitrate)
                    }
                }
                override fun onDisconnect() { 
                    Log.d(TAG, "Desconectado") 
                    isRunning = false
                }
                override fun onAuthError() { Log.e(TAG, "Erro de autenticação") }
                override fun onAuthSuccess() { Log.d(TAG, "Autenticação OK") }
            })

            rtmpDisplay?.let { display ->
                data?.let { intentData ->
                    display.setIntentResult(resultCode, intentData)
                }

                val prepareVideo = display.prepareVideo(
                    width, height, fps, targetVideoBitrate, 0, dpi
                )
                
                var prepareAudio = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        prepareAudio = display.prepareInternalAudio(
                            audioBitrate, sampleRate, true, false, false
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Falha ao iniciar áudio interno, tentando microfone", e)
                        prepareAudio = display.prepareAudio(audioBitrate, sampleRate, true)
                    }
                } else {
                    prepareAudio = display.prepareAudio(audioBitrate, sampleRate, true)
                }

                if (prepareVideo && prepareAudio) {
                    display.startStream("$rtmpUrl/$streamKey")
                    Log.d(TAG, "Streaming iniciado: ${width}x${height} @ ${targetVideoBitrate/1000}kbps")
                } else {
                    Log.e(TAG, "Falha ao preparar encoder. Video: $prepareVideo, Audio: $prepareAudio")
                    stopStreaming()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao iniciar streaming", e)
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
                    Log.d(TAG, "Adaptativo: Reduzindo bitrate para ${newBitrate/1000}kbps")
                    lastBitrateChange = now
                } 
                else if (currentBitrate > targetVideoBitrate * 0.9) {
                     display.setVideoBitrateOnFly(targetVideoBitrate)
                     lastBitrateChange = now
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao ajustar bitrate", e)
            }
        }
    }

    private fun stopStreaming() {
        try {
            rtmpDisplay?.let { display ->
                if (display.isStreaming) {
                    display.stopStream()
                }
                if (display.isRecording) {
                    display.stopRecord()
                }
            }
            rtmpDisplay = null
            isRunning = false
            stopService(Intent(this, FloatingControlService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar streaming", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Live Stream",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação de transmissão ao vivo"
                setShowBadge(false)
            }
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

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 Transmitindo ao vivo")
            .setContentText("Toque para abrir o app")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Parar", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
        const val ACTION_MUTE = "com.livestream.youtube.MUTE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val CHANNEL_ID = "streaming_channel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
    }
}
