package com.livestream.youtube.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.SurfaceTexture
import android.os.IBinder
import android.view.Surface
import com.livestream.youtube.StreamingService
import com.livestream.youtube.model.overlay.OverlayConfiguration

/**
 * Handles integration between overlay system and StreamingService.
 * Manages surface binding and frame synchronization.
 */
class OverlayStreamIntegration(private val context: Context) {

    interface StreamStateListener {
        fun onStreamStarted()
        fun onStreamStopped()
        fun onSurfaceReady(surface: Surface)
        fun onSurfaceReleased()
    }

    private var streamingService: StreamingService? = null
    private var isBound = false
    private var listener: StreamStateListener? = null
    private var overlaySurface: Surface? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? StreamingService.StreamingBinder
            streamingService = binder?.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            streamingService = null
            isBound = false
            listener?.onStreamStopped()
        }
    }

    /**
     * Binds to StreamingService.
     */
    fun bindToService() {
        val intent = Intent(context, StreamingService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Unbinds from StreamingService.
     */
    fun unbindFromService() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            releaseOverlaySurface()
        }
    }

    /**
     * Sets the stream state listener.
     */
    fun setStreamStateListener(listener: StreamStateListener?) {
        this.listener = listener
    }

    /**
     * Creates a surface for overlay rendering.
     */
    fun createOverlaySurface(): Surface? {
        if (overlaySurface != null) {
            return overlaySurface
        }

        // Try to get surface from streaming service
        val surface = streamingService?.getCaptureSurface()
        if (surface != null) {
            overlaySurface = surface
            listener?.onSurfaceReady(surface)
            return surface
        }

        // Cannot create standalone surface without SurfaceControl
        listener?.onSurfaceReleased()
        return null
    }

    /**
     * Releases the overlay surface.
     */
    fun releaseOverlaySurface() {
        overlaySurface?.release()
        overlaySurface = null
        listener?.onSurfaceReleased()
    }

    /**
     * Gets the overlay surface if available.
     */
    fun getOverlaySurface(): Surface? = overlaySurface

    /**
     * Checks if currently streaming.
     */
    fun isStreaming(): Boolean = streamingService?.isStreaming() == true

    /**
     * Notifies stream started.
     */
    fun onStreamStarted() {
        listener?.onStreamStarted()
    }

    /**
     * Notifies stream stopped.
     */
    fun onStreamStopped() {
        releaseOverlaySurface()
        listener?.onStreamStopped()
    }

    /**
     * Updates overlay configuration.
     */
    fun updateOverlayConfig(config: OverlayConfiguration) {
        // Can be used to sync overlay state with streaming
    }

    /**
     * Gets the current overlay configuration from storage.
     */
    fun getCurrentOverlayConfig(): OverlayConfiguration? {
        val storageManager = OverlayStorageManager(context)
        return storageManager.loadActiveConfiguration()
    }

    /**
     * Saves overlay configuration.
     */
    fun saveOverlayConfig(config: OverlayConfiguration): Boolean {
        val storageManager = OverlayStorageManager(context)
        return storageManager.saveConfiguration(config)
    }
}
