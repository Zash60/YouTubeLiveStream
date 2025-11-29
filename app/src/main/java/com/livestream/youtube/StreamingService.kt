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
    
    // Controle de Bitrate
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
                    Log.e(TAG, "Error starting widget", e)
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
            // AÇÃO NOVA: Alternar Modo Privacidade (Tela Preta)
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
                if (isMuted) {
                    display.disableAudio()
                } else {
                    display.enableAudio()
                }
            }
        }
    }

    // Lógica da Tela Preta
    private fun handlePrivacyMode(isPrivacyOn: Boolean) {
        rtmpDisplay?.let { display ->
            if (display.isStreaming) {
                try {
                    // muteVideo(true) = Envia frames pretos para a live
                    // muteVideo(false) = Volta a capturar a tela
                    display.glInterface.muteVideo(isPrivacyOn)
                    
                    if (isPrivacyOn) {
                        Log.d(TAG, "Privacy ON: Streaming Black Screen")
                        // Muta o áudio também para segurança total
                        display.disableAudio() 
                    } else {
                        Log.d(TAG, "Privacy OFF: Streaming Screen")
                        // Desmuta o áudio ao voltar
                        display.enableAudio()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error toggling privacy", e)
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
                    Log.d(TAG, "Connection started") 
                }
                override fun onConnectionSuccess() { 
                    Log.d(TAG, "Connected successfully") 
                    isRunning = true
                }
                override fun onConnectionFailed(reason: String) { 
                    Log.e(TAG, "Connection failed: $reason")
                    stopStreaming() 
                }
                override fun onNewBitrate(bitrate: Long) {
                    if (useAdaptiveBitrate && isRunning) {
                        handleAdaptiveBitrate(bitrate)
                    }
                }
                override fun onDisconnect() { 
                    Log.d(TAG, "Disconnected") 
                    isRunning = false
                }
                override fun onAuthError() { Log.e(TAG, "Auth Error") }
                override fun onAuthSuccess() { Log.d(TAG, "Auth Success") }
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
                        prepareAudio = display.prepareAudio(audioBitrate, sampleRate, true)
                    }
                } else {
                    prepareAudio = display.prepareAudio(audioBitrate, sampleRate, true)
                }

                if (prepareVideo && prepareAudio) {
                    display.startStream("$rtmpUrl/$streamKey")
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
            } catch (e: Exception) {
                Log.e(TAG, "Bitrate error", e)
            }
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
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }
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
