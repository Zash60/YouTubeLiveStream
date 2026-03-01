package com.livestream.youtube

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer

class AudioCapture(private val context: Context) {

    companion object {
        private const val TAG = "AudioCapture"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val TIMEOUT_US = 10000L
    }

    interface AudioCaptureCallback {
        fun onAudioDataAvailable(data: ByteArray, timestamp: Long)
        fun onError(error: String)
    }

    // Configurações de áudio
    private var sampleRate = 44100
    private var channelCount = 2
    private var bitrate = 128000
    
    private var audioRecord: AudioRecord? = null
    private var mediaCodec: MediaCodec? = null
    
    private var recordThread: HandlerThread? = null
    private var recordHandler: Handler? = null
    private var encodeThread: HandlerThread? = null
    private var encodeHandler: Handler? = null
    
    private var callback: AudioCaptureCallback? = null
    private var isCapturing = false
    
    private var useInternalAudio = true
    private var mediaProjection: MediaProjection? = null

    fun setConfig(sampleRate: Int, channelCount: Int, bitrate: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bitrate = bitrate
    }

    fun setCallback(callback: AudioCaptureCallback) {
        this.callback = callback
    }

    fun setMediaProjection(projection: MediaProjection) {
        this.mediaProjection = projection
    }

    fun setUseInternalAudio(use: Boolean) {
        this.useInternalAudio = use
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!hasAudioPermission()) {
            callback?.onError("Permissão de áudio não concedida")
            return false
        }

        try {
            // Configurar AudioRecord
            val channelConfig = if (channelCount == 2) 
                AudioFormat.CHANNEL_IN_STEREO 
            else 
                AudioFormat.CHANNEL_IN_MONO
                
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 2

            audioRecord = if (useInternalAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                createInternalAudioRecord(channelConfig, bufferSize)
            } else {
                createMicrophoneRecord(channelConfig, bufferSize)
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                callback?.onError("Falha ao inicializar AudioRecord")
                return false
            }

            // Configurar encoder AAC
            if (!setupEncoder()) {
                callback?.onError("Falha ao configurar encoder de áudio")
                return false
            }

            // Iniciar gravação
            audioRecord?.startRecording()
            
            // Iniciar threads
            startThreads(bufferSize)
            
            isCapturing = true
            Log.d(TAG, "Captura de áudio iniciada: ${sampleRate}Hz, ${channelCount}ch, ${bitrate/1000}kbps")
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar captura de áudio", e)
            callback?.onError("Erro ao iniciar captura: ${e.message}")
            return false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private fun createInternalAudioRecord(channelConfig: Int, bufferSize: Int): AudioRecord {
        Log.d(TAG, "Criando AudioRecord para áudio interno")
        
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

    @SuppressLint("MissingPermission")
    private fun createMicrophoneRecord(channelConfig: Int, bufferSize: Int): AudioRecord {
        Log.d(TAG, "Criando AudioRecord para microfone")
        
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }

    private fun setupEncoder(): Boolean {
        try {
            val format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_AAC_PROFILE, 
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec?.start()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar encoder de áudio", e)
            return false
        }
    }

    private fun startThreads(bufferSize: Int) {
        // Thread de gravação
        recordThread = HandlerThread("AudioRecordThread").apply { start() }
        recordHandler = Handler(recordThread!!.looper)
        
        // Thread de encoding
        encodeThread = HandlerThread("AudioEncodeThread").apply { start() }
        encodeHandler = Handler(encodeThread!!.looper)
        
        // Iniciar loops
        recordHandler?.post { recordLoop(bufferSize) }
        encodeHandler?.post { encodeLoop() }
    }

    private fun recordLoop(bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        
        while (isCapturing) {
            try {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (bytesRead > 0) {
                    feedEncoder(buffer, bytesRead)
                }
            } catch (e: Exception) {
                if (isCapturing) {
                    Log.e(TAG, "Erro no loop de gravação", e)
                }
            }
        }
    }

    private fun feedEncoder(data: ByteArray, size: Int) {
        try {
            val inputBufferIndex = mediaCodec?.dequeueInputBuffer(TIMEOUT_US) ?: -1
            
            if (inputBufferIndex >= 0) {
                val inputBuffer = mediaCodec?.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(data, 0, size)
                
                mediaCodec?.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    size,
                    System.nanoTime() / 1000,
                    0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao alimentar encoder", e)
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
                            // Pular ADTS header se presente
                            val outData: ByteArray
                            if (bufferInfo.size > 7 && hasADTSHeader(outputBuffer)) {
                                outData = ByteArray(bufferInfo.size - 7)
                                outputBuffer.position(7)
                                outputBuffer.get(outData)
                            } else {
                                outData = ByteArray(bufferInfo.size)
                                outputBuffer.get(outData)
                            }
                            
                            callback?.onAudioDataAvailable(outData, bufferInfo.presentationTimeUs)
                        }
                        
                        mediaCodec?.releaseOutputBuffer(outputBufferIndex, false)
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        Log.d(TAG, "Audio output format changed: ${mediaCodec?.outputFormat}")
                    }
                }
            } catch (e: Exception) {
                if (isCapturing) {
                    Log.e(TAG, "Erro no loop de encoding", e)
                }
            }
        }
    }

    private fun hasADTSHeader(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 2) return false
        val pos = buffer.position()
        val byte1 = buffer.get(pos).toInt() and 0xFF
        val byte2 = buffer.get(pos + 1).toInt() and 0xFF
        return byte1 == 0xFF && (byte2 and 0xF0) == 0xF0
    }

    fun stop() {
        isCapturing = false
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
            
            recordThread?.quitSafely()
            recordThread = null
            recordHandler = null
            
            encodeThread?.quitSafely()
            encodeThread = null
            encodeHandler = null
            
            Log.d(TAG, "Captura de áudio parada")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar captura de áudio", e)
        }
    }

    fun isCapturing(): Boolean = isCapturing

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Gera o AudioSpecificConfig para AAC
     */
    fun getAudioSpecificConfig(): ByteArray {
        val profile = 2 // AAC-LC
        val sampleRateIndex = getSampleRateIndex(sampleRate)
        
        val config = ByteArray(2)
        config[0] = ((profile shl 3) or (sampleRateIndex shr 1)).toByte()
        config[1] = (((sampleRateIndex and 0x01) shl 7) or (channelCount shl 3)).toByte()
        
        return config
    }

    private fun getSampleRateIndex(sampleRate: Int): Int {
        return when (sampleRate) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            7350 -> 12
            else -> 4 // default to 44100
        }
    }

    fun getSampleRate(): Int = sampleRate
    fun getChannelCount(): Int = channelCount
    fun getBitrate(): Int = bitrate
}
