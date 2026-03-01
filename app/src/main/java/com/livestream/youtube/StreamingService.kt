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
import android.view.Surface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.utils.gl.TranslateTo
import com.pedro.encoder.input.gl.render.filters.NoFilterRender
import java.io.File

class StreamingService : Service() {

    private var rtmpDisplay: RtmpDisplay? = null
    private var resultCode: Int = 0
    private var data: Intent? = null
    
    private var orientationMode = "AUTO"
    private var targetVideoBitrate = 4000 * 1000
    private var pauseBitmap: Bitmap? = null
    private var imageFilter: ImageObjectFilterRender? = null

    /**
     * Binder for clients to access service methods.
     */
    inner class StreamingBinder : android.os.Binder() {
        fun getService(): StreamingService = this@StreamingService
    }

    /**
     * Gets the capture surface for overlays.
     */
    fun getCaptureSurface(): Surface? {
        return rtmpDisplay?.glInterface?.surface
    }

    /**
     * Checks if currently streaming.
     */
    fun isStreaming(): Boolean = isRunning

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
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                orientationMode = intent.getStringExtra("orientation_mode") ?: "AUTO"
                
                startForeground(NOTIFICATION_ID, createNotification())
                startStreaming()
                startService(Intent(this, FloatingControlService::class.java))
            }
            ACTION_STOP -> {
                stopStreaming()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopService(Intent(this, FloatingControlService::class.java))
                stopSelf()
            }
            ACTION_MUTE -> {
                val mute = intent.getBooleanExtra("mute", false)
                if (mute) rtmpDisplay?.disableAudio() else rtmpDisplay?.enableAudio()
            }
            ACTION_PRIVACY -> {
                val privacy = intent.getBooleanExtra("privacy", false)
                handlePrivacy(privacy)
            }
        }
        return START_NOT_STICKY
    }

    private fun handlePrivacy(isPrivacy: Boolean) {
        if (!isPrivacy) {
            rtmpDisplay?.glInterface?.setFilter(NoFilterRender())
            rtmpDisplay?.enableAudio()
            return
        }
        rtmpDisplay?.disableAudio()
        if (pauseBitmap != null) {
            if (imageFilter == null) {
                imageFilter = ImageObjectFilterRender()
                imageFilter?.setImage(pauseBitmap)
                imageFilter?.setScale(100f, 100f)
                imageFilter?.setPosition(TranslateTo.CENTER)
            }
            imageFilter?.let { rtmpDisplay?.glInterface?.setFilter(it) }
        } else {
            rtmpDisplay?.glInterface?.muteVideo()
        }
    }

    private fun startStreaming() {
        try {
            val prefs = getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
            val rtmpUrl = prefs.getString("rtmp_url", "rtmp://a.rtmp.youtube.com/live2") ?: ""
            val streamKey = prefs.getString("stream_key", "") ?: ""

            val vPrefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)
            var width = vPrefs.getInt("width", 1280)
            var height = vPrefs.getInt("height", 720)
            val fps = vPrefs.getInt("fps", 30)
            targetVideoBitrate = vPrefs.getInt("video_bitrate", 4000) * 1000
            val audioBitrate = vPrefs.getInt("audio_bitrate", 128) * 1000
            val sampleRate = vPrefs.getInt("sample_rate", 44100)
            
            val useHevc = vPrefs.getBoolean("use_hevc", false)
            val localRecord = vPrefs.getBoolean("local_record", false)
            val recordingQuality = vPrefs.getInt("recording_quality", RecordingService.QUALITY_MEDIUM)
            val recordingFormat = vPrefs.getInt("recording_format", RecordingService.FORMAT_MP4)

            // Validação básica
            if (streamKey.isBlank()) {
                showToast("Configure a chave de stream primeiro!")
                stopSelf()
                return
            }

            if (width <= 0 || height <= 0 || fps <= 0) {
                showToast("Configurações de vídeo inválidas!")
                stopSelf()
                return
            }

            val sysOri = resources.configuration.orientation
            if (orientationMode == "LANDSCAPE" || (orientationMode == "AUTO" && sysOri == Configuration.ORIENTATION_LANDSCAPE)) {
                if (width < height) { val t = width; width = height; height = t }
            } else {
                if (width > height) { val t = width; width = height; height = t }
            }

            rtmpDisplay = RtmpDisplay(this, true, connectChecker)
            rtmpDisplay?.let { display ->
                data?.let { display.setIntentResult(resultCode, it) }
                
                val prepareVideo = display.prepareVideo(width, height, fps, targetVideoBitrate, 0, resources.displayMetrics.densityDpi)
                val prepareAudio = if (Build.VERSION.SDK_INT >= 29) {
                    try { display.prepareInternalAudio(audioBitrate, sampleRate, true, false, false) } 
                    catch (e: Exception) { display.prepareAudio(audioBitrate, sampleRate, true) }
                } else {
                    display.prepareAudio(audioBitrate, sampleRate, true)
                }

                if (prepareVideo && prepareAudio) {
                    display.startStream("$rtmpUrl/$streamKey")
                    
                    // GRAVAÇÃO LOCAL
                    if (localRecord) {
                        try {
                            val format = if (recordingFormat == RecordingService.FORMAT_MP4) "mp4" else "mkv"
                            val file = File(getExternalFilesDir(null), "rec_${System.currentTimeMillis()}.$format")
                            display.startRecord(file.absolutePath)
                            showToast("Gravando: ${file.name}")
                        } catch (e: Exception) { showToast("Erro gravação: ${e.message}") }
                    }

                } else {
                    showToast("Erro ao preparar vídeo/áudio!")
                    stopStreaming()
                }
            }
        } catch (e: Exception) {
            showToast("Erro: ${e.message}")
            Log.e("StreamingService", "Erro ao iniciar stream", e)
            stopSelf()
        }
    }

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {}
        override fun onConnectionSuccess() { isRunning = true; showToast("Conectado!") }
        override fun onConnectionFailed(reason: String) { stopStreaming(); showToast("Falha: $reason") }
        override fun onNewBitrate(bitrate: Long) {
            val color = if (bitrate < targetVideoBitrate * 0.5) "RED" else "GREEN"
            startService(Intent(this@StreamingService, FloatingControlService::class.java).apply {
                action = "UPDATE_HEALTH"
                putExtra("health_color", color)
            })
        }
        override fun onDisconnect() { isRunning = false; showToast("Desconectado") }
        override fun onAuthError() { stopStreaming(); showToast("Erro Chave") }
        override fun onAuthSuccess() {}
    }

    private fun stopStreaming() {
        rtmpDisplay?.let { 
            if (it.isStreaming) it.stopStream()
            if (it.isRecording) it.stopRecord()
        }
        rtmpDisplay = null
        isRunning = false
    }

    private fun showToast(msg: String) = Handler(Looper.getMainLooper()).post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Stream", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, StreamingService::class.java).apply { action = ACTION_STOP }
        val pStop = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Live On")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_media_pause, "Parar", pStop)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = StreamingBinder()
    override fun onDestroy() { stopStreaming(); super.onDestroy() }

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_MUTE = "MUTE"
        const val ACTION_PRIVACY = "PRIVACY"
        const val EXTRA_RESULT_CODE = "code"
        const val EXTRA_DATA = "data"
        const val CHANNEL_ID = "chn_stream"
        const val NOTIFICATION_ID = 1
        var isRunning = false
    }
}
