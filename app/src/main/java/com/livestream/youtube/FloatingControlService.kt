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
    private var isPrivacyOn = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_YouTubeLiveStream)
        val inflater = LayoutInflater.from(contextThemeWrapper)

        floatingView = inflater.inflate(R.layout.widget_floating_control, null)

        setupLayoutParams()
        setupViews()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "UPDATE_HEALTH") {
            val color = intent.getStringExtra("health_color")
            updateHealthIndicator(color)
        }
        
        return START_STICKY
    }

    private fun updateHealthIndicator(color: String?) {
        if (isPrivacyOn || !::floatingView.isInitialized) return

        try {
            val mainIcon = floatingView.findViewById<ImageView>(R.id.img_floating_icon)
            mainIcon.background.setTintList(null)
            
            when (color) {
                "GREEN" -> mainIcon.setBackgroundResource(R.drawable.bg_circle_green)
                "YELLOW" -> mainIcon.setBackgroundResource(R.drawable.bg_circle_yellow)
                "RED" -> mainIcon.setBackgroundResource(R.drawable.bg_circle_red)
                else -> mainIcon.setBackgroundResource(R.drawable.bg_circle_red)
            }
        } catch (e: Exception) {
            // Ignorar erros de atualização do indicador
        }
    }

    private fun setupLayoutParams() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            flags,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 20
        layoutParams.y = 200
    }

    private fun setupViews() {
        try {
            // Verificar se a view já está adicionada
            if (floatingView.windowToken == null) {
                windowManager.addView(floatingView, layoutParams)
            }
        } catch (e: Exception) {
            // Se já existe, tentar atualizar
            try {
                windowManager.updateViewLayout(floatingView, layoutParams)
            } catch (updateException: Exception) {
                // Se falhar, tentar remover e readicionar
                try {
                    windowManager.removeView(floatingView)
                } catch (removeException: Exception) {}
                try {
                    windowManager.addView(floatingView, layoutParams)
                } catch (addException: Exception) {
                    addException.printStackTrace()
                }
            }
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

        mainIcon.setOnClickListener {
            toggleMenu(menuLayout, mainIcon)
        }

        btnPrivacy.setOnClickListener {
            togglePrivacy(btnPrivacy, mainIcon)
            if (isMenuExpanded) toggleMenu(menuLayout, mainIcon)
        }

        btnMute.setOnClickListener {
            toggleMute(btnMute)
        }

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
        
        mainIcon.setImageResource(
            if (isMenuExpanded) android.R.drawable.ic_menu_close_clear_cancel 
            else if (isPrivacyOn) android.R.drawable.ic_secure
            else android.R.drawable.ic_menu_camera
        )
        
        if (isMenuExpanded) {
            mainIcon.background.setTint(0xFF444444.toInt())
        } else if (isPrivacyOn) {
            mainIcon.background.setTint(0xFF000000.toInt()) 
        } else {
            mainIcon.background.setTintList(null)
            mainIcon.setBackgroundResource(R.drawable.bg_circle_red)
        }
    }

    private fun togglePrivacy(btnPrivacy: ImageView, mainIcon: ImageView) {
        isPrivacyOn = !isPrivacyOn

        val intent = Intent(this, StreamingService::class.java).apply {
            action = StreamingService.ACTION_PRIVACY_MODE
            putExtra("privacy", isPrivacyOn)
        }
        startService(intent)

        if (isPrivacyOn) {
            btnPrivacy.setColorFilter(0xFFFF0000.toInt())
            mainIcon.setImageResource(android.R.drawable.ic_secure)
            mainIcon.background.setTint(0xFF000000.toInt()) 
            
            val btnMute = floatingView.findViewById<ImageView>(R.id.btn_mute)
            isMuted = true
            btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode)
            btnMute.setColorFilter(0xFFFF0000.toInt())
        } else {
            btnPrivacy.setColorFilter(0xFFFFFFFF.toInt())
            mainIcon.setImageResource(android.R.drawable.ic_menu_camera)
            mainIcon.background.setTintList(null) 
            
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
        try {
            if (::floatingView.isInitialized) {
                if (floatingView.windowToken != null) {
                    windowManager.removeView(floatingView)
                }
            }
        } catch (e: Exception) {
            // Ignorar erros de limpeza
        }
        super.onDestroy()
    }
}
