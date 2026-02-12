package com.livestream.youtube

import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Simple data class for resolution dimensions
 */
data class Resolution(val width: Int, val height: Int)

/**
 * RecordingService handles local video recording during live streams.
 * Supports multiple quality presets and formats (MP4, MKV).
 */
class RecordingService(private val context: Context) {

    companion object {
        private const val TAG = "RecordingService"
        
        // Quality presets
        const val QUALITY_HIGH = 0   // 1080p
        const val QUALITY_MEDIUM = 1  // 720p
        const val QUALITY_LOW = 2     // 480p
        
        // Formats
        const val FORMAT_MP4 = 0
        const val FORMAT_MKV = 1
        
        // Video dimensions
        private val QUALITY_DIMENSIONS = mapOf(
            QUALITY_HIGH to Resolution(1920, 1080),
            QUALITY_MEDIUM to Resolution(1280, 720),
            QUALITY_LOW to Resolution(854, 480)
        )
        
        // Bitrates
        private val QUALITY_BITRATES = mapOf(
            QUALITY_HIGH to 8_000_000,   // 8 Mbps
            QUALITY_MEDIUM to 4_000_000, // 4 Mbps
            QUALITY_LOW to 2_000_000     // 2 Mbps
        )
        
        // Frame rates
        private const val FRAME_RATE = 30
    }

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFilePath: String? = null
    private var currentQuality = QUALITY_MEDIUM
    private var currentFormat = FORMAT_MP4
    private var recordingStartTime: Long = 0
    
    private var recordingListener: RecordingListener? = null
    
    interface RecordingListener {
        fun onRecordingStarted(filePath: String)
        fun onRecordingStopped(filePath: String)
        fun onRecordingError(error: String)
        fun onRecordingProgress(durationMs: Long)
    }
    
    fun setRecordingListener(listener: RecordingListener) {
        this.recordingListener = listener
    }
    
    /**
     * Set recording quality preset
     */
    fun setQuality(quality: Int) {
        if (!isRecording) {
            currentQuality = quality
        }
    }
    
    /**
     * Set recording format
     */
    fun setFormat(format: Int) {
        if (!isRecording) {
            currentFormat = format
        }
    }
    
    /**
     * Check if recording is active
     */
    fun isCurrentlyRecording(): Boolean = isRecording
    
    /**
     * Get current recording duration in milliseconds
     */
    fun getRecordingDuration(): Long {
        return if (isRecording) {
            System.currentTimeMillis() - recordingStartTime
        } else {
            0
        }
    }
    
    /**
     * Initialize the recording surface with a SurfaceTexture
     */
    fun initializeRecorder(surfaceTexture: SurfaceTexture): Boolean {
        if (isRecording) {
            Log.w(TAG, "Cannot initialize - already recording")
            return false
        }
        
        try {
            val dimensions = QUALITY_DIMENSIONS[currentQuality] ?: Resolution(1280, 720)
            val width = dimensions.width
            val height = dimensions.height
            val bitrate = QUALITY_BITRATES[currentQuality] ?: 4_000_000
            val extension = if (currentFormat == FORMAT_MP4) "mp4" else "mkv"
            val fileName = generateFileName(extension)
            
            mediaRecorder = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                // Note: MKV format uses MPEG_4 container format internally
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                
                // Audio settings
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                
                // Video settings
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(width, height)
                setVideoFrameRate(FRAME_RATE)
                setVideoEncodingBitRate(bitrate)
                
                // Output file
                currentFilePath = getOutputFilePath(fileName)
                setOutputFile(currentFilePath)
                
                prepare()
            }
            
            // Set up the surface
            surfaceTexture.setDefaultBufferSize(width, height)
            val surface = Surface(surfaceTexture)
            mediaRecorder?.setInputSurface(surface)
            surface.release()
            
            Log.d(TAG, "Recorder initialized: ${width}x${height} @ ${bitrate / 1_000_000}Mbps")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize recorder", e)
            recordingListener?.onRecordingError("Falha ao iniciar gravacao: ${e.message}")
            return false
        }
    }
    
    /**
     * Start recording
     */
    fun startRecording(): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }
        
        return try {
            mediaRecorder?.start()
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            
            currentFilePath?.let { path ->
                recordingListener?.onRecordingStarted(path)
            }
            
            Log.i(TAG, "Recording started: $currentFilePath")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            recordingListener?.onRecordingError("Erro ao iniciar: ${e.message}")
            false
        }
    }
    
    /**
     * Stop recording and save to gallery
     */
    fun stopRecording(): String? {
        if (!isRecording) {
            return null
        }
        
        val filePath = currentFilePath
        
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            recordingListener?.onRecordingError("Erro ao parar: ${e.message}")
        } finally {
            mediaRecorder = null
            isRecording = false
            recordingStartTime = 0
        }
        
        filePath?.let { saveToGallery(it) }
        recordingListener?.onRecordingStopped(filePath ?: "")
        Log.i(TAG, "Recording stopped: $filePath")
        
        return filePath
    }
    
    /**
     * Stop recording without saving
     */
    fun cancelRecording() {
        if (!isRecording) return
        
        try {
            mediaRecorder?.apply {
                // Only call stop() if we actually started recording
                // to avoid IllegalStateException
                if (recordingStartTime > 0) {
                    try {
                        stop()
                    } catch (e: IllegalStateException) {
                        // Already stopped or not started, ignore
                        Log.w(TAG, "stop() called on MediaRecorder that wasn't started")
                    }
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cancel", e)
        }
        
        mediaRecorder = null
        isRecording = false
        recordingStartTime = 0
        
        // Delete the incomplete file
        currentFilePath?.let { path ->
            File(path).delete()
        }
        
        Log.i(TAG, "Recording cancelled")
    }
    
    /**
     * Update video encoder bitrate dynamically (for adaptive streaming)
     */
    fun updateBitrate(newBitrate: Int) {
        if (isRecording && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.setVideoEncodingBitRate(newBitrate)
                Log.d(TAG, "Bitrate updated to: ${newBitrate / 1_000_000}Mbps")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update bitrate", e)
            }
        }
    }
    
    /**
     * Generate unique filename with timestamp
     */
    private fun generateFileName(extension: String): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "LiveStream_$timestamp.$extension"
    }
    
    /**
     * Get output file path in app's external files directory
     */
    private fun getOutputFilePath(fileName: String): String {
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val recordingDir = File(moviesDir, "LiveStream")
        if (!recordingDir.exists()) {
            recordingDir.mkdirs()
        }
        return File(recordingDir, fileName).absolutePath
    }
    
    /**
     * Save recorded file to device gallery
     */
    private fun saveToGallery(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "File not found: $filePath")
            return
        }
        
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, if (currentFormat == FORMAT_MP4) "video/mp4" else "video/x-matroska")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATA, filePath)
                
                // Get dimensions from quality preset
                val dimensions = QUALITY_DIMENSIONS[currentQuality] ?: Resolution(1280, 720)
                val width = dimensions.width
                val height = dimensions.height
                put(MediaStore.Video.Media.WIDTH, width)
                put(MediaStore.Video.Media.HEIGHT, height)
                
                // Use duration if available
                val duration = getRecordingDuration()
                if (duration > 0) {
                    put(MediaStore.Video.Media.DURATION, duration)
                }
            }
            
            val mimeType = if (currentFormat == FORMAT_MP4) "video/mp4" else "video/x-matroska"
            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            
            uri?.let {
                MediaScannerConnection.scanFile(context, arrayOf(filePath), arrayOf(mimeType), null)
                Log.i(TAG, "Saved to gallery: $uri")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to gallery", e)
            // File is still saved in app's directory
        }
    }
    
    /**
     * Get recording statistics
     */
    fun getRecordingStats(): RecordingStats {
        return RecordingStats(
            isRecording = isRecording,
            durationMs = getRecordingDuration(),
            filePath = currentFilePath,
            quality = currentQuality,
            format = currentFormat,
            bitrate = QUALITY_BITRATES[currentQuality] ?: 0,
            resolution = QUALITY_DIMENSIONS[currentQuality] ?: Resolution(0, 0)
        )
    }
    
    data class RecordingStats(
        val isRecording: Boolean,
        val durationMs: Long,
        val filePath: String?,
        val quality: Int,
        val format: Int,
        val bitrate: Int,
        val resolution: Resolution
    ) {
        fun getFormattedDuration(): String {
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / (1000 * 60)) % 60
            val hours = durationMs / (1000 * 60 * 60)
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }
        
        fun getQualityString(): String = when (quality) {
            QUALITY_HIGH -> "1080p"
            QUALITY_MEDIUM -> "720p"
            QUALITY_LOW -> "480p"
            else -> "Unknown"
        }
        
        fun getFormatString(): String = when (format) {
            FORMAT_MP4 -> "MP4"
            FORMAT_MKV -> "MKV"
            else -> "Unknown"
        }
    }
}
