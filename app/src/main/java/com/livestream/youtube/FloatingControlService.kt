package com.livestream.youtube

import android.animation.LayoutTransition
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.*
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.livestream.youtube.model.overlay.*
import com.livestream.youtube.overlay.*
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import kotlinx.coroutines.*
import java.util.Collections

class FloatingControlService : Service() {

    private lateinit var windowManager: WindowManager
    
    // CONTROL VIEW (Botão Flutuante)
    private lateinit var controlView: View
    private lateinit var controlParams: WindowManager.LayoutParams
    private var isMenuExpanded = false
    private var isMuted = false
    private var isPrivacyOn = false
    private var isChatVisible = true // Novo estado para o chat
    
    // OVERLAY VIEW (Chat, Alertas, Viewers)
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var rvChat: RecyclerView? = null
    private var overlayRenderer: OverlayRenderer? = null
    private var overlayManager: OverlayElementManager? = null
    private var chatAdapter: ChatAdapter? = null
    
    // DADOS
    private var youtubeService: YouTube? = null
    private var liveChatId: String? = null
    private var isRunning = true
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        setupYouTubeApi()
        setupOverlayView()
        setupControlView()
        
        startDataPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "UPDATE_HEALTH") {
            val color = intent.getStringExtra("health_color")
            updateHealthIndicator(color)
        }
        return START_STICKY
    }

    private fun setupYouTubeApi() {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return
        val credential = GoogleAccountCredential.usingOAuth2(this, Collections.singleton("https://www.googleapis.com/auth/youtube.force-ssl"))
        credential.selectedAccount = account.account
        youtubeService = YouTube.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential).setApplicationName("YouTubeLiveStreamApp").build()
        
        val prefs = getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
        liveChatId = prefs.getString("live_chat_id", null)
    }

    private fun setupOverlayView() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.widget_overlay_layer, null)

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type, flags, PixelFormat.TRANSLUCENT
        )

        // Webview Alertas
        val webViewAlerts = overlayView!!.findViewById<WebView>(R.id.webViewAlerts)
        val prefs = getSharedPreferences("video_settings", Context.MODE_PRIVATE)
        val alertUrl = prefs.getString("alert_url", "")
        if (!alertUrl.isNullOrEmpty()) {
            webViewAlerts.settings.javaScriptEnabled = true
            webViewAlerts.setBackgroundColor(0x00000000)
            webViewAlerts.loadUrl(alertUrl)
        } else {
            webViewAlerts.visibility = View.GONE
        }

        // Chat
        rvChat = overlayView!!.findViewById(R.id.rvChat)
        chatAdapter = ChatAdapter()
        rvChat?.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChat?.adapter = chatAdapter

        // Setup Overlay Renderer with Viewer Count
        setupOverlayRenderer()

        try { windowManager.addView(overlayView, overlayParams) } catch (e: Exception) {}
    }

    private fun setupOverlayRenderer() {
        overlayManager = OverlayElementManager()

        // Load overlay configuration from storage
        val storageManager = OverlayStorageManager(this)
        val savedConfig = storageManager.loadActiveConfiguration()

        if (savedConfig == null || savedConfig.elements.isEmpty()) {
            // Add default viewer count element
            overlayManager?.addElement(ViewerCountOverlayElement.createDefault())
        } else {
            overlayManager?.setConfiguration(savedConfig, saveUndo = false)
        }

        // Get or create container view for renderer
        val containerView = overlayView?.findViewById<ViewGroup>(R.id.overlayContainer)
            ?: (overlayView as? ViewGroup) ?: return

        overlayRenderer = OverlayRenderer(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        containerView.addView(overlayRenderer, 0)
        updateOverlayRenderer()
    }

    private fun updateOverlayRenderer() {
        overlayRenderer?.setElements(overlayManager?.getVisibleElements() ?: emptyList())
    }

    private fun updateViewerCount(count: String) {
        // Update viewer count value in all ViewerCountOverlayElements
        overlayManager?.getAllElements()?.filterIsInstance<ViewerCountOverlayElement>()?.forEach { element ->
            element.viewerValue = count
            overlayManager?.updateElement(element.id, { updated -> updated })
        }
        overlayRenderer?.invalidate()
    }

    private fun setupControlView() {
        val inflater = LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_YouTubeLiveStream))
        controlView = inflater.inflate(R.layout.widget_floating_control, null)

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        controlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type, flags, PixelFormat.TRANSLUCENT
        )
        controlParams.gravity = Gravity.TOP or Gravity.START
        controlParams.x = 20
        controlParams.y = 200

        try { windowManager.addView(controlView, controlParams) } catch (e: Exception) {}

        setupTouchListener()
        setupClickListeners()
    }

    private fun startDataPolling() {
        scope.launch {
            while (isRunning) {
                if (youtubeService != null) {
                    try {
                        // Viewers
                        val broadcastList = youtubeService!!.liveBroadcasts().list(listOf("id", "statistics"))
                            .setBroadcastStatus("active").execute()
                        if (broadcastList.items.isNotEmpty()) {
                            val v = broadcastList.items[0].statistics.concurrentViewers ?: "0"
                            withContext(Dispatchers.Main) { updateViewerCount("👁️ $v") }
                        }

                        // Chat
                        if (liveChatId != null) {
                            val chatResp = youtubeService!!.liveChatMessages().list(liveChatId!!, listOf("snippet", "authorDetails"))
                                .setMaxResults(10L).execute()
                            
                            val msgs = chatResp.items.map { item ->
                                // EXTRAIR CARGOS DO YOUTUBE
                                val details = item.authorDetails
                                ChatMessage(
                                    author = details.displayName,
                                    message = item.snippet.displayMessage,
                                    profileUrl = details.profileImageUrl,
                                    isOwner = details.isChatOwner ?: false,
                                    isMod = details.isChatModerator ?: false,
                                    isMember = details.isChatSponsor ?: false
                                )
                            }
                            withContext(Dispatchers.Main) {
                                chatAdapter?.updateMessages(msgs)
                                if (msgs.isNotEmpty()) rvChat?.smoothScrollToPosition(msgs.size - 1)
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                delay(10000) 
            }
        }
    }

    private fun updateHealthIndicator(color: String?) {
        if (isPrivacyOn || !::controlView.isInitialized) return
        try {
            val mainIcon = controlView.findViewById<ImageView>(R.id.img_floating_icon)
            mainIcon.background.setTintList(null)
            when (color) {
                "GREEN" -> mainIcon.setBackgroundResource(R.drawable.bg_circle_green)
                "YELLOW" -> mainIcon.setBackgroundResource(R.drawable.bg_circle_yellow)
                "RED" -> mainIcon.setBackgroundResource(R.drawable.bg_circle_red)
                else -> mainIcon.setBackgroundResource(R.drawable.bg_circle_red)
            }
        } catch (e: Exception) {}
    }

    private fun setupClickListeners() {
        val menuLayout = controlView.findViewById<LinearLayout>(R.id.layout_menu)
        menuLayout.layoutTransition = LayoutTransition()
        
        val btnChatToggle = controlView.findViewById<ImageView>(R.id.btn_chat_toggle)
        val btnPrivacy = controlView.findViewById<ImageView>(R.id.btn_privacy)
        val btnMute = controlView.findViewById<ImageView>(R.id.btn_mute)
        val btnStop = controlView.findViewById<ImageView>(R.id.btn_stop)
        val mainIcon = controlView.findViewById<ImageView>(R.id.img_floating_icon)

        mainIcon.setOnClickListener {
            isMenuExpanded = !isMenuExpanded
            menuLayout.visibility = if (isMenuExpanded) View.VISIBLE else View.GONE
            // Muda ícone (X ou Câmera)
            val iconRes = if (isMenuExpanded) android.R.drawable.ic_menu_close_clear_cancel 
                          else if (isPrivacyOn) android.R.drawable.ic_secure 
                          else android.R.drawable.ic_menu_camera
            mainIcon.setImageResource(iconRes)
        }

        // --- LÓGICA DO BOTÃO DE CHAT ---
        btnChatToggle.setOnClickListener {
            isChatVisible = !isChatVisible
            if (isChatVisible) {
                rvChat?.visibility = View.VISIBLE
                btnChatToggle.alpha = 1.0f // Opaco
            } else {
                rvChat?.visibility = View.GONE
                btnChatToggle.alpha = 0.5f // Transparente para indicar "desligado"
            }
        }

        btnPrivacy.setOnClickListener {
            isPrivacyOn = !isPrivacyOn
            val i = Intent(this, StreamingService::class.java).apply { action = StreamingService.ACTION_PRIVACY; putExtra("privacy", isPrivacyOn) }
            startService(i)
            btnPrivacy.setColorFilter(if(isPrivacyOn) 0xFFFF0000.toInt() else 0xFFFFFFFF.toInt())
            mainIcon.setImageResource(if(isPrivacyOn) android.R.drawable.ic_secure else android.R.drawable.ic_menu_camera)
        }

        btnMute.setOnClickListener {
            isMuted = !isMuted
            val i = Intent(this, StreamingService::class.java).apply { action = StreamingService.ACTION_MUTE; putExtra("mute", isMuted) }
            startService(i)
            btnMute.setColorFilter(if(isMuted) 0xFFFF0000.toInt() else 0xFFFFFFFF.toInt())
        }

        btnStop.setOnClickListener {
            startService(Intent(this, StreamingService::class.java).apply { action = StreamingService.ACTION_STOP })
            stopSelf()
        }
    }

    private fun setupTouchListener() {
        val mainIcon = controlView.findViewById<ImageView>(R.id.img_floating_icon)
        mainIcon.setOnTouchListener(object : View.OnTouchListener {
            var iX=0; var iY=0; var tX=0f; var tY=0f; var drag=false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when(e.action) {
                    MotionEvent.ACTION_DOWN -> { iX=controlParams.x; iY=controlParams.y; tX=e.rawX; tY=e.rawY; drag=false; return true }
                    MotionEvent.ACTION_UP -> { if(!drag) v.performClick(); return true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx=(e.rawX-tX).toInt(); val dy=(e.rawY-tY).toInt()
                        if(Math.abs(dx)>10 || Math.abs(dy)>10) { drag=true; controlParams.x=iX+dx; controlParams.y=iY+dy; windowManager.updateViewLayout(controlView, controlParams) }
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        isRunning = false
        try {
            if (::controlView.isInitialized) windowManager.removeView(controlView)
            if (overlayView != null) windowManager.removeView(overlayView)
        } catch (e: Exception) {}
        super.onDestroy()
    }
}

// --- ADAPTER E MODELO ATUALIZADOS ---

data class ChatMessage(
    val author: String, 
    val message: String, 
    val profileUrl: String,
    val isOwner: Boolean = false,
    val isMod: Boolean = false,
    val isMember: Boolean = false
)

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    private var messages = listOf<ChatMessage>()
    
    fun updateMessages(new: List<ChatMessage>) { messages = new; notifyDataSetChanged() }
    
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = ChatViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_chat_message, p, false))
    
    override fun onBindViewHolder(h: ChatViewHolder, i: Int) {
        val m = messages[i]
        
        // Cores dos Cargos
        val nameColor = when {
            m.isOwner -> 0xFFFFD700.toInt() // Dourado
            m.isMod -> 0xFF5E84F1.toInt()   // Azul
            m.isMember -> 0xFF2BA640.toInt() // Verde
            else -> 0xFFAAAAAA.toInt()       // Cinza Claro
        }

        // Formatar texto com cores (Spannable)
        val fullText = "${m.author}: ${m.message}"
        val spannable = SpannableString(fullText)
        
        // Pinta apenas o nome do autor
        spannable.setSpan(
            ForegroundColorSpan(nameColor), 
            0, 
            m.author.length + 1, // +1 para incluir os dois pontos
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        h.tvMsg.text = spannable
        Glide.with(h.itemView).load(m.profileUrl).circleCrop().into(h.img)
    }
    
    override fun getItemCount() = messages.size
    
    class ChatViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvMsg: TextView = v.findViewById(R.id.tvMessage)
        val img: ImageView = v.findViewById(R.id.imgProfile)
    }
}
