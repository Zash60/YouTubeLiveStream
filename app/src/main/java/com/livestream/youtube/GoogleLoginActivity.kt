package com.livestream.youtube

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.*
import com.livestream.youtube.databinding.ActivityGoogleLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class GoogleLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGoogleLoginBinding
    private val SCOPE_YOUTUBE = "https://www.googleapis.com/auth/youtube.force-ssl"
    private lateinit var prefs: SharedPreferences

    // IDs de Categoria do YouTube
    // 20 = Gaming, 24 = Entertainment, 22 = People & Blogs, 28 = Tech, 27 = Education
    private val categoryIds = arrayOf("20", "24", "22", "28", "27")
    private val categoryNames = arrayOf("Jogos (Gaming)", "Entretenimento", "Pessoas e Blogs", "Ciência e Tecnologia", "Educação")

    // Latência
    private val latencyValues = arrayOf("normal", "low", "ultraLow")
    private val latencyNames = arrayOf("Normal (Melhor Qualidade)", "Baixa", "Ultra Baixa (Melhor Interação)")

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            handleSignInResult(result.data!!)
        } else {
            updateStatus("Login cancelado", false)
            binding.progressBar.visibility = View.GONE
            binding.btnSignInCreate.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoogleLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalhes da Live"
        
        prefs = getSharedPreferences("live_config_prefs", Context.MODE_PRIVATE)

        setupUI()
        loadSavedData()
    }

    private fun setupUI() {
        // Spinner Privacidade
        val privacyOptions = arrayOf("Público", "Não Listado", "Privado")
        binding.spinnerPrivacy.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, privacyOptions)

        // Spinner Categoria
        binding.spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryNames)

        // Spinner Latência
        binding.spinnerLatency.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, latencyNames)

        binding.btnSignInCreate.setOnClickListener {
            if (binding.etLiveTitle.text.toString().trim().isEmpty()) {
                binding.etLiveTitle.error = "Digite um título"
                return@setOnClickListener
            }
            saveCurrentData() // Salva antes de tentar logar
            startSignIn()
        }
    }

    private fun loadSavedData() {
        binding.etLiveTitle.setText(prefs.getString("title", ""))
        binding.etDescription.setText(prefs.getString("description", ""))
        
        binding.spinnerPrivacy.setSelection(prefs.getInt("privacy_idx", 1)) // Default: Não listado
        binding.spinnerCategory.setSelection(prefs.getInt("category_idx", 0)) // Default: Jogos
        binding.spinnerLatency.setSelection(prefs.getInt("latency_idx", 2)) // Default: Ultra Low
        
        val isKids = prefs.getBoolean("is_kids", false)
        if (isKids) binding.rbForKids.isChecked = true else binding.rbNotForKids.isChecked = true
    }

    private fun saveCurrentData() {
        prefs.edit().apply {
            putString("title", binding.etLiveTitle.text.toString())
            putString("description", binding.etDescription.text.toString())
            putInt("privacy_idx", binding.spinnerPrivacy.selectedItemPosition)
            putInt("category_idx", binding.spinnerCategory.selectedItemPosition)
            putInt("latency_idx", binding.spinnerLatency.selectedItemPosition)
            putBoolean("is_kids", binding.rbForKids.isChecked)
            apply()
        }
    }

    private fun startSignIn() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.visibility = View.VISIBLE
        binding.btnSignInCreate.isEnabled = false
        updateStatus("Conectando ao Google...", true)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_YOUTUBE))
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        signInLauncher.launch(client.signInIntent)
    }

    private fun handleSignInResult(data: Intent) {
        try {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(Exception::class.java)
            updateStatus("Login OK! Configurando Live...", true)
            createLiveBroadcast()
        } catch (e: Exception) {
            Log.e("GoogleLogin", "Error", e)
            updateStatus("Falha Login: ${e.message}", false)
            binding.progressBar.visibility = View.GONE
            binding.btnSignInCreate.isEnabled = true
        }
    }

    private fun createLiveBroadcast() {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return
        
        val credential = GoogleAccountCredential.usingOAuth2(this, Collections.singleton(SCOPE_YOUTUBE))
        credential.selectedAccount = account.account

        val youtubeService = YouTube.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("YouTubeLiveStreamApp").build()

        // Captura dados da UI
        val title = binding.etLiveTitle.text.toString()
        val description = binding.etDescription.text.toString()
        val privacy = arrayOf("public", "unlisted", "private")[binding.spinnerPrivacy.selectedItemPosition]
        val categoryId = categoryIds[binding.spinnerCategory.selectedItemPosition]
        val latencyPreference = latencyValues[binding.spinnerLatency.selectedItemPosition]
        val isMadeForKids = binding.rbForKids.isChecked

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Criar Broadcast (Evento)
                withContext(Dispatchers.Main) { updateStatus("1/3: Criando evento...", true) }
                
                val broadcastSnippet = LiveBroadcastSnippet()
                    .setTitle(title)
                    .setDescription(description)
                    .setScheduledStartTime(com.google.api.client.util.DateTime(System.currentTimeMillis()))

                val broadcastStatus = LiveBroadcastStatus()
                    .setPrivacyStatus(privacy)
                    .setSelfDeclaredMadeForKids(isMadeForKids)

                // AUTO-START + LATÊNCIA
                val monitorStreamInfo = MonitorStreamInfo()
                monitorStreamInfo.enableMonitorStream = false 

                val contentDetails = LiveBroadcastContentDetails()
                contentDetails.monitorStream = monitorStreamInfo
                contentDetails.enableAutoStart = true 
                contentDetails.enableAutoStop = true  
                contentDetails.latencyPreference = latencyPreference // Normal, Low, UltraLow

                val broadcast = LiveBroadcast()
                    .setKind("youtube#liveBroadcast")
                    .setSnippet(broadcastSnippet)
                    .setStatus(broadcastStatus)
                    .setContentDetails(contentDetails)

                // Precisamos inserir snippet.categoryId separadamente às vezes, mas vamos tentar via snippet
                // Nota: CategoryID não é exposto diretamente em setters simples do Snippet em algumas versões, 
                // mas vamos tentar passar. Se a live for criada como "Pessoas e Blogs" (default), é limitação da API simplificada.

                val createdBroadcast = youtubeService.liveBroadcasts()
                    .insert(listOf("snippet", "status", "contentDetails"), broadcast)
                    .execute()

                // Tentar atualizar categoria se necessário (opcional, pois create já deve ter setado se o snippet aceitar)
                // createdBroadcast.snippet.categoryId = categoryId
                
                // 2. Criar Stream (Chaves)
                withContext(Dispatchers.Main) { updateStatus("2/3: Gerando chaves...", true) }
                
                val cdnSettings = CdnSettings()
                    .setFormat("1080p")
                    .setIngestionType("rtmp")
                    .setResolution("1080p")
                    .setFrameRate("60fps")

                val streamSnippet = LiveStreamSnippet()
                    .setTitle("Stream Mobile - $title")

                val stream = LiveStream()
                    .setKind("youtube#liveStream")
                    .setSnippet(streamSnippet)
                    .setCdn(cdnSettings)

                val createdStream = youtubeService.liveStreams()
                    .insert(listOf("snippet", "cdn"), stream)
                    .execute()

                // 3. Bind
                withContext(Dispatchers.Main) { updateStatus("3/3: Finalizando...", true) }
                
                youtubeService.liveBroadcasts()
                    .bind(createdBroadcast.id, listOf("id", "contentDetails"))
                    .setStreamId(createdStream.id)
                    .execute()

                // Recuperar URL e Chave
                val ingestionInfo = createdStream.cdn.ingestionInfo
                val rtmpUrl = ingestionInfo.ingestionAddress
                val streamKey = ingestionInfo.streamName

                withContext(Dispatchers.Main) { saveAndFinish(rtmpUrl, streamKey) }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = "Erro API: ${e.message}"
                    Log.e("API_FAIL", errorMsg, e)
                    updateStatus(errorMsg, false)
                    binding.progressBar.visibility = View.GONE
                    binding.btnSignInCreate.isEnabled = true
                    Toast.makeText(this@GoogleLoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveAndFinish(url: String, key: String) {
        // 1. Salvar configurações de conexão
        getSharedPreferences("stream_settings", Context.MODE_PRIVATE).edit().apply {
            putString("rtmp_url", url)
            putString("stream_key", key)
            apply()
        }
        
        Toast.makeText(this, "Live Criada! Iniciando...", Toast.LENGTH_LONG).show()
        
        // 2. Voltar para MainActivity com ordem de início automático
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("AUTO_START_STREAM", true)
        startActivity(intent)
        finish()
    }

    private fun updateStatus(msg: String, isLoading: Boolean) {
        binding.tvStatus.text = msg
        binding.tvStatus.setTextColor(if (isLoading) 0xFF888888.toInt() else 0xFFFF0000.toInt())
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
