package com.livestream.youtube

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.ContextThemeWrapper // Importação necessária para corrigir o erro de tema

class FloatingControlService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    
    private var isMenuExpanded = false
    private var isMuted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupFloatingView()
    }

    private fun setupFloatingView() {
        // CORREÇÃO: Criar um Contexto com Tema para que atributos como 
        // "?attr/selectableItemBackgroundBorderless" funcionem no XML.
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_YouTubeLiveStream)
        
        // Usar o LayoutInflater a partir desse contexto com tema
        floatingView = LayoutInflater.from(contextThemeWrapper).inflate(R.layout.widget_floating_control, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Posição inicial (Topo Esquerdo)
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 0
        layoutParams.y = 200

        setupTouchListener()
        setupClickListeners()

        try {
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupClickListeners() {
        val menuLayout = floatingView.findViewById<LinearLayout>(R.id.layout_menu)
        val btnMute = floatingView.findViewById<ImageView>(R.id.btn_mute)
        val btnStop = floatingView.findViewById<ImageView>(R.id.btn_stop)
        val mainIcon = floatingView.findViewById<ImageView>(R.id.img_floating_icon)

        // Expandir/Recolher Menu
        mainIcon.setOnClickListener {
            isMenuExpanded = !isMenuExpanded
            menuLayout.visibility = if (isMenuExpanded) View.VISIBLE else View.GONE
        }

        // Ação de Mutar
        btnMute.setOnClickListener {
            isMuted = !isMuted
            val intent = Intent(this, StreamingService::class.java).apply {
                action = StreamingService.ACTION_MUTE
                putExtra("mute", isMuted)
            }
            startService(intent)
            
            // Atualizar ícone visualmente
            btnMute.setImageResource(
                if (isMuted) android.R.drawable.ic_lock_silent_mode 
                else android.R.drawable.ic_lock_silent_mode_off
            )
        }

        // Ação de Parar Stream
        btnStop.setOnClickListener {
            val intent = Intent(this, StreamingService::class.java).apply {
                action = StreamingService.ACTION_STOP
            }
            startService(intent)
            stopSelf() // Fecha este widget
        }
    }

    private fun setupTouchListener() {
        val mainIcon = floatingView.findViewById<ImageView>(R.id.img_floating_icon)
        
        mainIcon.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            v.performClick()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        // Considera arrasto se moveu mais que 10 pixels
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true
                            layoutParams.x = initialX + dx
                            layoutParams.y = initialY + dy
                            try {
                                windowManager.updateViewLayout(floatingView, layoutParams)
                            } catch (e: Exception) {
                                // View removida
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
