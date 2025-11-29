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
    private lateinit var privacyView: View
    
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var privacyParams: WindowManager.LayoutParams
    
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
        privacyView = inflater.inflate(R.layout.layout_privacy_mode, null)

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

        privacyParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
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

        mainIcon.setOnClickListener {
            isMenuExpanded = !isMenuExpanded
            menuLayout.visibility = if (isMenuExpanded) View.VISIBLE else View.GONE
            mainIcon.setImageResource(
                if (isMenuExpanded) android.R.drawable.ic_menu_close_clear_cancel 
                else android.R.drawable.ic_menu_camera
            )
            mainIcon.background.setTint(if (isMenuExpanded) 0xFF444444.toInt() else 0xFFFF0000.toInt())
        }

        btnPrivacy.setOnClickListener {
            togglePrivacy(btnPrivacy)
            if (isMenuExpanded) {
                isMenuExpanded = false
                menuLayout.visibility = View.GONE
                mainIcon.setImageResource(android.R.drawable.ic_menu_camera)
                mainIcon.background.setTint(0xFFFF0000.toInt())
            }
        }

        btnMute.setOnClickListener {
            isMuted = !isMuted
            val intent = Intent(this, StreamingService::class.java).apply {
                action = StreamingService.ACTION_MUTE
                putExtra("mute", isMuted)
            }
            startService(intent)
            
            btnMute.setImageResource(
                if (isMuted) android.R.drawable.ic_lock_silent_mode 
                else android.R.drawable.ic_lock_silent_mode_off
            )
            btnMute.setColorFilter(if(isMuted) 0xFFFF0000.toInt() else 0xFFFFFFFF.toInt())
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, StreamingService::class.java).apply {
                action = StreamingService.ACTION_STOP
            }
            startService(intent)
            stopSelf()
        }
    }

    private fun togglePrivacy(btnPrivacy: ImageView) {
        isPrivacyOn = !isPrivacyOn

        if (isPrivacyOn) {
            try {
                windowManager.addView(privacyView, privacyParams)
                if (!isMuted) {
                    val intent = Intent(this, StreamingService::class.java).apply {
                        action = StreamingService.ACTION_MUTE
                        putExtra("mute", true)
                    }
                    startService(intent)
                }
                btnPrivacy.setColorFilter(0xFFFF0000.toInt())
            } catch (e: Exception) { e.printStackTrace() }
        } else {
            try {
                windowManager.removeView(privacyView)
                btnPrivacy.setColorFilter(0xFFFFFFFF.toInt())
                if (!isMuted) { // Volta ao estado normal (desmutado) se estava desmutado antes
                    val intent = Intent(this, StreamingService::class.java).apply {
                        action = StreamingService.ACTION_MUTE
                        putExtra("mute", false)
                    }
                    startService(intent)
                }
            } catch (e: Exception) { e.printStackTrace() }
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
            if (::floatingView.isInitialized && floatingView.windowToken != null) windowManager.removeView(floatingView)
            if (::privacyView.isInitialized && privacyView.windowToken != null) windowManager.removeView(privacyView)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
