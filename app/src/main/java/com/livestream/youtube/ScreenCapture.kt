package com.livestream.youtube

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import java.nio.ByteBuffer

class ScreenCapture(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCapture"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 2
        private const val TIMEOUT_US = 10000L
    }

    interface ScreenCaptureCallback {
        fun onVideoDataAvailable(data: ByteArray, isKeyFrame: Boolean, timestamp: Long)
        fun onError(error: String)
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    
    private var encoderThread: HandlerThread? = null
    private var encoderHandler: Handler? = null
    
    private var callback: ScreenCaptureCallback? = null
    
    private var isCapturing = false
    
    private var width = 1280
    private var height = 720
    private var bitrate = 4000000
    private var dpi = 320

    fun setConfig(width: Int, height: Int, bitrate: Int, dpi: Int) {
        this.width = width
        this.height = height
        this.bitrate = bitrate
        this.dpi = dpi
    }

    fun setCallback(callback: ScreenCaptureCallback) {
        this.callback = callback
    }

    fun start(resultCode: Int, data: Intent): Boolean {
        try {
            // Obter MediaProjection
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
                as MediaProjectionManager
            
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                callback?.onError("Falha ao obter MediaProjection")
                return false
            }

            // Registrar callback para quando a projeção parar
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection parada")
                    stop()
                }
            }, null)

            // Configurar encoder
            if (!setupEncoder()) {
                callback?.onError("Falha ao configurar encoder de vídeo")
                return false
            }

            // Criar VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
            )

            if (virtualDisplay == null) {
                callback?.onError("Falha ao criar VirtualDisplay")
                return false
            }

            // Iniciar thread de encoding
            startEncoderThread()
            
            isCapturing = true
            Log.d(TAG, "Captura de tela iniciada: ${width}x${height} @ ${bitrate/1000}kbps")
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar captura", e)
            callback?.onError("Erro ao iniciar captura: ${e.message}")
            return false
        }
    }

    private fun setupEncoder(): Boolean {
        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, 
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                
                // Configurações adicionais para melhor qualidade
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(MediaFormat.KEY_PROFILE, 
                        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                    setInteger(MediaFormat.KEY_LEVEL, 
                        MediaCodecInfo.CodecProfileLevel.AVCLevel4)
                }
                
                // Bitrate mode
                setInteger(MediaFormat.KEY_BITRATE_MODE, 
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }

            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            
            inputSurface = mediaCodec?.createInputSurface()
            mediaCodec?.start()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar encoder", e)
            return false
        }
    }

    private fun startEncoderThread() {
        encoderThread = HandlerThread("VideoEncoderThread").apply { start() }
        encoderHandler = Handler(encoderThread!!.looper)
        
        encoderHandler?.post {
            encodeLoop()
        }
    }

    private fun encodeLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        
        while (isCapturing) {
            try {
                val outputBufferIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_US) ?: -1
                
                when {
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = mediaCodec?.getOutputBuffer(outputBufferIndex)
                        
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val data = ByteArray(bufferInfo.size)
                            outputBuffer.get(data)
                            
                            val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                            
                            callback?.onVideoDataAvailable(
                                data, 
                                isKeyFrame, 
                                bufferInfo.presentationTimeUs
                            )
                        }
                        
                        mediaCodec?.releaseOutputBuffer(outputBufferIndex, false)
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        Log.d(TAG, "Output format changed: ${mediaCodec?.outputFormat}")
                    }
                }
            } catch (e: Exception) {
                if (isCapturing) {
                    Log.e(TAG, "Erro no loop de encoding", e)
                }
            }
        }
    }

    fun requestKeyFrame() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val params = android.os.Bundle()
                params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                mediaCodec?.setParameters(params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao solicitar keyframe", e)
        }
    }

    fun updateBitrate(newBitrate: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                val params = android.os.Bundle()
                params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, newBitrate)
                mediaCodec?.setParameters(params)
                Log.d(TAG, "Bitrate atualizado para: ${newBitrate/1000}kbps")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar bitrate", e)
        }
    }

    fun stop() {
        isCapturing = false
        
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
            
            inputSurface?.release()
            inputSurface = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            encoderThread?.quitSafely()
            encoderThread = null
            encoderHandler = null
            
            Log.d(TAG, "Captura de tela parada")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar captura", e)
        }
    }

    fun isCapturing(): Boolean = isCapturing

    fun getWidth(): Int = width
    fun getHeight(): Int = height

    /**
     * Obtém as dimensões da tela do dispositivo
     */
    fun getScreenDimensions(): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return Pair(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            return Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    /**
     * Obtém o DPI da tela
     */
    fun getScreenDpi(): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val configuration = context.resources.configuration
            return configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            return metrics.densityDpi
        }
    }
}
