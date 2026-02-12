package com.livestream.youtube

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.InputStreamContent
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
    private var selectedThumbnailUri: Uri? = null

    private val categoryIds = arrayOf("20", "24", "22", "28", "27")
    private val latencyValues = arrayOf("normal", "low", "ultraLow")

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedThumbnailUri = uri
                binding.imgThumbnailPreview.setImageURI(uri)
                binding.imgThumbnailPreview.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.imgThumbnailPreview.imageTintList = null 
            }
        }
    }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            handleSignInResult(result.data!!)
        } else {
            updateStatus(getString(R.string.login_cancelled), false)
            binding.progressBar.visibility = View.GONE
            binding.btnSignInCreate.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoogleLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.live_details)
        prefs = getSharedPreferences("live_config_prefs", Context.MODE_PRIVATE)
        setupUI()
        loadSavedData()
        enableImmersiveMode()
    }

    private fun enableImmersiveMode() {
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false
        
        // Hide system bars
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupUI() {
        binding.spinnerPrivacy.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, 
            arrayOf(getString(R.string.privacy_public), getString(R.string.privacy_unlisted), getString(R.string.privacy_private)))
        binding.spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, 
            arrayOf(getString(R.string.category_gaming), getString(R.string.category_entertainment), 
                getString(R.string.category_people), getString(R.string.category_science), getString(R.string.category_education)))
        binding.spinnerLatency.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, 
            arrayOf(getString(R.string.latency_normal), getString(R.string.latency_low), getString(R.string.latency_ultra_low)))

        binding.btnSelectThumbnail.setOnClickListener {
            val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                pickImageLauncher.launch(intent)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 102)
            }
        }

        binding.btnSignInCreate.setOnClickListener {
            if (binding.etLiveTitle.text.toString().trim().isEmpty()) {
                binding.etLiveTitle.error = getString(R.string.title_required)
                return@setOnClickListener
            }
            saveCurrentData()
            startSignIn()
        }
    }

    private fun loadSavedData() {
        binding.etLiveTitle.setText(prefs.getString("title", ""))
        binding.etDescription.setText(prefs.getString("description", ""))
        binding.spinnerPrivacy.setSelection(prefs.getInt("privacy_idx", 1))
        binding.spinnerCategory.setSelection(prefs.getInt("category_idx", 0))
        binding.spinnerLatency.setSelection(prefs.getInt("latency_idx", 2))
        if (prefs.getBoolean("is_kids", false)) binding.rbForKids.isChecked = true else binding.rbNotForKids.isChecked = true
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
        updateStatus(getString(R.string.connecting_google), true)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestScopes(Scope(SCOPE_YOUTUBE)).build()
        val client = GoogleSignIn.getClient(this, gso)
        signInLauncher.launch(client.signInIntent)
    }

    private fun handleSignInResult(data: Intent) {
        try {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(Exception::class.java)
            updateStatus(getString(R.string.login_ok), true)
            createLiveBroadcast()
        } catch (e: Exception) {
            Log.e("GoogleLogin", "Error", e)
            updateStatus(getString(R.string.login_failed, e.message), false)
            binding.progressBar.visibility = View.GONE
            binding.btnSignInCreate.isEnabled = true
        }
    }

    private fun createLiveBroadcast() {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return
        val credential = GoogleAccountCredential.usingOAuth2(this, Collections.singleton(SCOPE_YOUTUBE))
        credential.selectedAccount = account.account
        val youtubeService = YouTube.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential).setApplicationName("YouTubeLiveStreamApp").build()

        val title = binding.etLiveTitle.text.toString()
        val description = binding.etDescription.text.toString()
        val privacy = arrayOf("public", "unlisted", "private")[binding.spinnerPrivacy.selectedItemPosition]
        val latencyPreference = latencyValues[binding.spinnerLatency.selectedItemPosition]
        val isMadeForKids = binding.rbForKids.isChecked

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) { updateStatus(getString(R.string.creating_event), true) }
                
                val broadcastSnippet = LiveBroadcastSnippet().setTitle(title).setDescription(description).setScheduledStartTime(com.google.api.client.util.DateTime(System.currentTimeMillis()))
                val broadcastStatus = LiveBroadcastStatus().setPrivacyStatus(privacy).setSelfDeclaredMadeForKids(isMadeForKids)
                val contentDetails = LiveBroadcastContentDetails()
                val monitorStreamInfo = MonitorStreamInfo()
                monitorStreamInfo.enableMonitorStream = false
                contentDetails.monitorStream = monitorStreamInfo
                contentDetails.enableAutoStart = true 
                contentDetails.enableAutoStop = true
                contentDetails.latencyPreference = latencyPreference

                val broadcast = LiveBroadcast().setKind("youtube#liveBroadcast").setSnippet(broadcastSnippet).setStatus(broadcastStatus).setContentDetails(contentDetails)
                
                val createdBroadcast = youtubeService.liveBroadcasts().insert(listOf("snippet", "status", "contentDetails"), broadcast).execute()
                val liveChatId = createdBroadcast.snippet.liveChatId

                if (selectedThumbnailUri != null) {
                    try {
                        withContext(Dispatchers.Main) { updateStatus(getString(R.string.uploading_thumbnail), true) }
                        val contentResolver = applicationContext.contentResolver
                        val inputStream = contentResolver.openInputStream(selectedThumbnailUri!!)
                        val type = contentResolver.getType(selectedThumbnailUri!!) ?: "image/jpeg"
                        val mediaContent = InputStreamContent(type, inputStream)
                        youtubeService.thumbnails().set(createdBroadcast.id, mediaContent).execute()
                    } catch (e: Exception) { e.printStackTrace() }
                }

                withContext(Dispatchers.Main) { updateStatus(getString(R.string.generating_keys), true) }
                
                val cdnSettings = CdnSettings().setFormat("1080p").setIngestionType("rtmp").setResolution("1080p").setFrameRate("60fps")
                val streamSnippet = LiveStreamSnippet().setTitle("Stream Mobile - $title")
                val stream = LiveStream().setKind("youtube#liveStream").setSnippet(streamSnippet).setCdn(cdnSettings)
                val createdStream = youtubeService.liveStreams().insert(listOf("snippet", "cdn"), stream).execute()

                withContext(Dispatchers.Main) { updateStatus(getString(R.string.finishing), true) }
                
                youtubeService.liveBroadcasts().bind(createdBroadcast.id, listOf("id", "contentDetails")).setStreamId(createdStream.id).execute()
                
                val rtmpUrl = createdStream.cdn.ingestionInfo.ingestionAddress
                val streamKey = createdStream.cdn.ingestionInfo.streamName

                withContext(Dispatchers.Main) { saveAndFinish(rtmpUrl, streamKey, liveChatId) }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus(getString(R.string.api_error, e.message), false)
                    binding.progressBar.visibility = View.GONE
                    binding.btnSignInCreate.isEnabled = true
                    Toast.makeText(this@GoogleLoginActivity, getString(R.string.api_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveAndFinish(url: String, key: String, chatId: String?) {
        getSharedPreferences("stream_settings", Context.MODE_PRIVATE).edit().apply {
            putString("rtmp_url", url)
            putString("stream_key", key)
            putString("live_chat_id", chatId)
            apply()
        }
        Toast.makeText(this, R.string.live_created, Toast.LENGTH_LONG).show()
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("AUTO_START_STREAM", true)
        startActivity(intent)
        finish()
    }

    private fun updateStatus(msg: String, isLoading: Boolean) {
        binding.tvStatus.text = msg
        binding.tvStatus.setTextColor(if (isLoading) ContextCompat.getColor(this, R.color.text_secondary) else ContextCompat.getColor(this, R.color.error))
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
