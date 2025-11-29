package com.livestream.youtube

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Captura áudio interno e microfone simultaneamente e mixa em um único stream
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MixedAudioCapture(private val context: Context) {

    companion object {
        private const val TAG = "MixedAudioCapture"
    }

    interface MixedAudioCallback {
        fun onMixedAudioData(data: ByteArray, timestamp: Long)
        fun onError(error: String)
    }

    private var internalAudioRecord: AudioRecord? = null
    private var microphoneRecord: AudioRecord? = null
    
    private var mixThread: HandlerThread? = null
    private var mixHandler: Handler? = null
    
    private var callback: MixedAudioCallback? = null
    private var isCapturing = false
    
    private var mediaProjection: MediaProjection? = null
    
    private var sampleRate = 44100
    private var channelCount = 2
    
    // Volume controls (0.0 to 1.0)
    private var internalVolume = 1.0f
    private var micVolume = 0.5f

    fun setConfig(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
    }

    fun setCallback(callback: MixedAudioCallback) {
        this.callback = callback
    }

    fun setMediaProjection(projection: MediaProjection) {
        this.mediaProjection = projection
    }

    fun setVolumes(internalVol: Float, micVol: Float) {
        this.internalVolume = internalVol.coerceIn(0f, 1f)
        this.micVolume = micVol.coerceIn(0f, 1f)
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!hasAudioPermission()) {
            callback?.onError("Permissão de áudio não concedida")
            return false
        }

        if (mediaProjection == null) {
            callback?.onError("MediaProjection não configurada")
            return false
        }

        try {
            val channelConfig = if (channelCount == 2) 
                AudioFormat.CHANNEL_IN_STEREO 
            else 
                AudioFormat.CHANNEL_IN_MONO

            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 2

            // Criar AudioRecord para áudio interno
            internalAudioRecord = createInternalAudioRecord(channelConfig, bufferSize)
            
            // Criar AudioRecord para microfone
            microphoneRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (internalAudioRecord?.state != AudioRecord.STATE_INITIALIZED ||
                microphoneRecord?.state != AudioRecord.STATE_INITIALIZED) {
                callback?.onError("Falha ao inicializar AudioRecords")
                return false
            }

            // Iniciar gravação
            internalAudioRecord?.startRecording()
            microphoneRecord?.startRecording()
            
            // Iniciar thread de mixagem
            startMixThread(bufferSize)
            
            isCapturing = true
            Log.d(TAG, "Captura de áudio mista iniciada")
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar captura mista", e)
            callback?.onError("Erro: ${e.message}")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private fun createInternalAudioRecord(channelConfig: Int, bufferSize: Int): AudioRecord {
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()

        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .build()
    }

    private fun startMixThread(bufferSize: Int) {
        mixThread = HandlerThread("AudioMixThread").apply { start() }
        mixHandler = Handler(mixThread!!.looper)
        
        mixHandler?.post { mixLoop(bufferSize) }
    }

    private fun mixLoop(bufferSize: Int) {
        val internalBuffer = ShortArray(bufferSize / 2)
        val micBuffer = ShortArray(bufferSize / 2)
        val mixedBuffer = ShortArray(bufferSize / 2)
        
        while (isCapturing) {
            try {
                // Ler áudio interno
                val internalRead = internalAudioRecord?.read(
                    internalBuffer, 0, internalBuffer.size
                ) ?: 0
                
                // Ler microfone
                val micRead = microphoneRecord?.read(
                    micBuffer, 0, micBuffer.size
                ) ?: 0
                
                if (internalRead > 0 || micRead > 0) {
                    val samplesToMix = maxOf(internalRead, micRead)
                    
                    // Mixar os dois streams
                    for (i in 0 until samplesToMix) {
                        val internalSample = if (i < internalRead) 
                            (internalBuffer[i] * internalVolume).toInt() 
                        else 0
                            
                        val micSample = if (i < micRead) 
                            (micBuffer[i] * micVolume).toInt() 
                        else 0
                        
                        // Somar e clipar para evitar distorção
                        var mixed = internalSample + micSample
                        mixed = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        
                        mixedBuffer[i] = mixed.toShort()
                    }
                    
                    // Converter para bytes
                    val byteBuffer = ByteArray(samplesToMix * 2)
                    for (i in 0 until samplesToMix) {
                        byteBuffer[i * 2] = (mixedBuffer[i].toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = (mixedBuffer[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    
                    callback?.onMixedAudioData(byteBuffer, System.nanoTime() / 1000)
                }
            } catch (e: Exception) {
                if (isCapturing) {
                    Log.e(TAG, "Erro no loop de mixagem", e)
                }
            }
        }
    }

    fun stop() {
        isCapturing = false
        
        try {
            internalAudioRecord?.stop()
            internalAudioRecord?.release()
            internalAudioRecord = null
            
            microphoneRecord?.stop()
            microphoneRecord?.release()
            microphoneRecord = null
            
            mixThread?.quitSafely()
            mixThread = null
            mixHandler = null
            
            Log.d(TAG, "Captura mista parada")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar captura", e)
        }
    }

    fun isCapturing(): Boolean = isCapturing

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
