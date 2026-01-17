package com.livestream.youtube

import android.app.Activity
import android.content.Context
import android.content.Intent
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
// IMPORTANTE: Importar todo o pacote model
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
        setupUI()
    }

    private fun setupUI() {
        val options = arrayOf("Público", "Não Listado", "Privado")
        binding.spinnerPrivacy.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        binding.spinnerPrivacy.setSelection(1)

        binding.btnSignInCreate.setOnClickListener {
            if (binding.etLiveTitle.text.toString().trim().isEmpty()) {
                binding.etLiveTitle.error = "Digite um título"
                return@setOnClickListener
            }
            startSignIn()
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
            updateStatus("Login OK! Criando Live...", true)
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

        val title = binding.etLiveTitle.text.toString()
        val privacy = arrayOf("public", "unlisted", "private")[binding.spinnerPrivacy.selectedItemPosition]

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Criar Broadcast
                withContext(Dispatchers.Main) { updateStatus("1/3: Criando evento...", true) }
                
                val broadcastSnippet = LiveBroadcastSnippet()
                    .setTitle(title)
                    .setScheduledStartTime(com.google.api.client.util.DateTime(System.currentTimeMillis()))

                val broadcastStatus = LiveBroadcastStatus()
                    .setPrivacyStatus(privacy)
                    .setSelfDeclaredMadeForKids(false)

                val broadcast = LiveBroadcast()
                    .setKind("youtube#liveBroadcast")
                    .setSnippet(broadcastSnippet)
                    .setStatus(broadcastStatus)

                val createdBroadcast = youtubeService.liveBroadcasts()
                    .insert(listOf("snippet", "status"), broadcast)
                    .execute()

                // 2. Criar Stream
                withContext(Dispatchers.Main) { updateStatus("2/3: Gerando chaves...", true) }
                
                // CORREÇÃO AQUI: A classe correta é CdnSettings, não LiveStreamCdn
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
                    .setCdn(cdnSettings) // setCdn espera um objeto CdnSettings

                val createdStream = youtubeService.liveStreams()
                    .insert(listOf("snippet", "cdn"), stream)
                    .execute()

                // 3. Bind
                withContext(Dispatchers.Main) { updateStatus("3/3: Finalizando...", true) }
                
                youtubeService.liveBroadcasts()
                    .bind(createdBroadcast.id, listOf("id", "contentDetails"))
                    .setStreamId(createdStream.id)
                    .execute()

                // Recuperar dados
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
        getSharedPreferences("stream_settings", Context.MODE_PRIVATE).edit().apply {
            putString("rtmp_url", url)
            putString("stream_key", key)
            apply()
        }
        Toast.makeText(this, "Live Criada!", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
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
