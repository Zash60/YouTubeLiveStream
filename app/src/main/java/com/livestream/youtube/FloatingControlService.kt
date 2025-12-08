package com.livestream.youtube

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Este serviço foi desativado para remover o botão flutuante da tela,
 * garantindo que ele não apareça na transmissão para os espectadores.
 * Os controles (Mute, Privacy, Stop) foram movidos para a notificação persistente.
 */
class FloatingControlService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        // Log.d("FloatingControlService", "Service is disabled.")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Log.d("FloatingControlService", "Service is disabled.")
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
}
