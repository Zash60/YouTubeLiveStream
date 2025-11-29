package com.livestream.youtube

import android.animation.LayoutTransition
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.ContextThemeWrapper

class FloatingControlService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    
    private var isMenuExpanded = false
    private var isMuted = false
    private var isPrivacyOn = false // Controla a tela preta na live

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_YouTubeLiveStream)
        val inflater = LayoutInflater.from(contextThemeWrapper)

        // Apenas inflamos o widget flutuante. Não usamos mais overlay de tela cheia.
        floatingView = inflater.inflate(R.layout.widget_floating_control, null)

        setupLayoutParams()
        setupViews()
    }

    private fun setupLayoutParams() {
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
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 20
        layoutParams.y = 200
    }

    private fun setupViews() {
        try {
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setupTouchListener()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        val menuLayout = floatingView.findViewById<LinearLayout>(R.id.layout_menu)
        menuLayout.layoutTransition = LayoutTransition()
        
        val btnPrivacy = floatingView.findViewById<ImageView>(R.id.btn_privacy)
        val btnMute = floatingView.findViewById<ImageView>(R.id.btn_mute)
        val btnStop = floatingView.findViewById<ImageView>(R.id.btn_stop)
        val mainIcon = floatingView.findViewById<ImageView>(R.id.img_floating_icon)

        // 1. Abrir/Fechar Menu
        mainIcon.setOnClickListener {
            toggleMenu(menuLayout, mainIcon)
        }

        // 2. Botão Privacidade (Gera Tela Preta na Live)
        btnPrivacy.setOnClickListener {
            togglePrivacy(btnPrivacy, mainIcon)
            // Fecha o menu após clicar
            if (isMenuExpanded) toggleMenu(menuLayout, mainIcon)
        }

        // 3. Botão Mutar
        btnMute.setOnClickListener {
            toggleMute(btnMute)
        }

        // 4. Botão Parar
        btnStop.setOnClickListener {
            val intent = Intent(this, StreamingService::class.java).apply {
                action = StreamingService.ACTION_STOP
            }
            startService(intent)
            stopSelf()
        }
    }

    private fun toggleMenu(menuLayout: LinearLayout, mainIcon: ImageView) {
        isMenuExpanded = !isMenuExpanded
        menuLayout.visibility = if (isMenuExpanded) View.VISIBLE else View.GONE
        
        // Altera o ícone principal dependendo do estado
        mainIcon.setImageResource(
            if (isMenuExpanded) android.R.drawable.ic_menu_close_clear_cancel 
            else if (isPrivacyOn) android.R.drawable.ic_secure // Cadeado se estiver em privacidade
            else android.R.drawable.ic_menu_camera
        )
        
        // Fundo preto se privacidade on, vermelho se off
        mainIcon.background.setTint(
            if (isMenuExpanded) 0xFF444444.toInt() 
            else if (isPrivacyOn) 0xFF000000.toInt() 
            else 0xFFFF0000.toInt()
        )
    }

    private fun togglePrivacy(btnPrivacy: ImageView, mainIcon: ImageView) {
        isPrivacyOn = !isPrivacyOn

        // Envia comando para o serviço fazer a tela ficar preta
        val intent = Intent(this, StreamingService::class.java).apply {
            action = StreamingService.ACTION_PRIVACY_MODE
            putExtra("privacy", isPrivacyOn)
        }
        startService(intent)

        // Atualiza UI
        if (isPrivacyOn) {
            // Privacidade Ligada: Ícone Vermelho
            btnPrivacy.setColorFilter(0xFFFF0000.toInt())
            
            // UI Update: Muta o ícone de áudio (o serviço já muta o som real)
            val btnMute = floatingView.findViewById<ImageView>(R.id.btn_mute)
            isMuted = true
            btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode)
            btnMute.setColorFilter(0xFFFF0000.toInt())
        } else {
            // Privacidade Desligada: Ícone Branco
            btnPrivacy.setColorFilter(0xFFFFFFFF.toInt())
            
            // Restaura ícone principal
            mainIcon.setImageResource(android.R.drawable.ic_menu_camera)
            mainIcon.background.setTint(0xFFFF0000.toInt())
            
            // Desmuta ícone
            val btnMute = floatingView.findViewById<ImageView>(R.id.btn_mute)
            isMuted = false
            btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            btnMute.setColorFilter(0xFFFFFFFF.toInt())
        }
    }

    private fun toggleMute(btnMute: ImageView) {
        isMuted = !isMuted
        val intent = Intent(this, StreamingService::class.java).apply {
            action = StreamingService.ACTION_MUTE_AUDIO
            putExtra("mute", isMuted)
        }
        startService(intent)
        
        btnMute.setImageResource(
            if (isMuted) android.R.drawable.ic_lock_silent_mode 
            else android.R.drawable.ic_lock_silent_mode_off
        )
        btnMute.setColorFilter(if(isMuted) 0xFFFF0000.toInt() else 0xFFFFFFFF.toInt())
    }

    private fun setupTouchListener() {
        val mainIcon = floatingView.findViewById<ImageView>(R.id.img_floating_icon)
        
        mainIcon.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private val touchSlop = 10

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

                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            isDragging = true
                            layoutParams.x = initialX + dx
                            layoutParams.y = initialY + dy
                            try {
                                windowManager.updateViewLayout(floatingView, layoutParams)
                            } catch (e: Exception) {}
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
        try {
            if (::floatingView.isInitialized && floatingView.windowToken != null) {
                windowManager.removeView(floatingView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
