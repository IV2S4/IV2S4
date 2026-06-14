package com.aria.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aria.assistant.api.ClaudeApiClient
import com.aria.assistant.api.Message
import com.aria.assistant.controller.CommandProcessor
import com.aria.assistant.controller.SystemAction
import com.aria.assistant.databinding.ActivityMainBinding
import com.aria.assistant.service.AriaForegroundService
import com.aria.assistant.ui.ChatMessage
import com.aria.assistant.ui.HUDRadarView
import com.aria.assistant.ui.MessageAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var voiceEngine: VoiceEngine
    private lateinit var commandProcessor: CommandProcessor
    private val claudeClient = ClaudeApiClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val conversationHistory = mutableListOf<Message>()
    private var prefs: SharedPreferences? = null

    private val PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("aria_prefs", Context.MODE_PRIVATE)
        commandProcessor = CommandProcessor(this)

        setupRecyclerView()
        setupVoiceEngine()
        setupClickListeners()
        requestPermissions()
        startForegroundService()

        // Show greeting after a short delay
        mainHandler.postDelayed({
            val greeting = getString(R.string.greeting)
            addAriaMessage(greeting)
            voiceEngine.speak(greeting)
        }, 1000)
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter()
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupVoiceEngine() {
        voiceEngine = VoiceEngine(
            context = this,
            onListening = {
                mainHandler.post {
                    setAssistantState(HUDRadarView.State.LISTENING)
                    binding.tvStatusLabel.text = getString(R.string.listening)
                    binding.waveformView.setActive(true)
                }
            },
            onResult = { text ->
                mainHandler.post {
                    handleUserInput(text)
                }
            },
            onError = { errorMsg ->
                mainHandler.post {
                    setAssistantState(HUDRadarView.State.IDLE)
                    binding.tvStatusLabel.text = getString(R.string.idle)
                    binding.waveformView.setActive(false)
                    // Only show error if it's important
                    if (errorMsg.contains("Permiso") || errorMsg.contains("red")) {
                        addAriaMessage("⚠ $errorMsg")
                    }
                }
            },
            onSpeakStart = {
                mainHandler.post {
                    setAssistantState(HUDRadarView.State.SPEAKING)
                    binding.tvStatusLabel.text = getString(R.string.speaking)
                    binding.waveformView.setActive(true)
                }
            },
            onSpeakDone = {
                mainHandler.post {
                    setAssistantState(HUDRadarView.State.IDLE)
                    binding.tvStatusLabel.text = getString(R.string.idle)
                    binding.waveformView.setActive(false)
                }
            },
            onAmplitude = { amp ->
                mainHandler.post {
                    binding.waveformView.updateAmplitude(amp)
                }
            }
        )
    }

    private fun setupClickListeners() {
        // Mic button - start/stop listening
        binding.btnMic.setOnClickListener {
            if (!voiceEngine.isAvailable()) {
                Toast.makeText(this, "Reconocimiento de voz no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            voiceEngine.speak("")
            voiceEngine.stopSpeaking()
            voiceEngine.startListening()
        }

        // Send button
        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                binding.etInput.text?.clear()
                handleUserInput(text)
            }
        }

        // Text input - send on enter
        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                binding.btnSend.performClick()
                true
            } else false
        }

        // Settings
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun handleUserInput(text: String) {
        if (text.isBlank()) return

        addUserMessage(text)
        setAssistantState(HUDRadarView.State.PROCESSING)
        binding.tvStatusLabel.text = getString(R.string.processing)
        binding.waveformView.setActive(false)

        // Check for system commands first
        val commandResult = commandProcessor.processCommand(text)

        if (commandResult.handled) {
            val response = commandResult.response
            conversationHistory.add(Message("user", text))
            conversationHistory.add(Message("assistant", response))
            addAriaMessage(response)
            voiceEngine.speak(response)

            // Execute the system action if any
            commandResult.action?.let { action ->
                mainHandler.postDelayed({
                    try {
                        executeAction(action)
                    } catch (e: Exception) {
                        addAriaMessage("No pude ejecutar esa acción: ${e.message}")
                    }
                }, 800)
            }

            setAssistantState(HUDRadarView.State.IDLE)
            binding.tvStatusLabel.text = getString(R.string.idle)
        } else {
            // Send to Claude API
            val apiKey = prefs?.getString("api_key", "") ?: ""

            if (apiKey.isEmpty()) {
                val noKeyMsg = "No tienes configurada una API Key de Claude. Toca el ícono de configuración para añadirla y poder usar la inteligencia artificial completa."
                addAriaMessage(noKeyMsg)
                voiceEngine.speak("Para usar mi inteligencia completa, necesitas configurar tu API key de Claude.")
                setAssistantState(HUDRadarView.State.IDLE)
                binding.tvStatusLabel.text = getString(R.string.idle)
                return
            }

            val history = conversationHistory.toList()
            conversationHistory.add(Message("user", text))

            lifecycleScope.launch {
                val result = claudeClient.sendMessage(apiKey, history, text)

                mainHandler.post {
                    result.fold(
                        onSuccess = { response ->
                            conversationHistory.add(Message("assistant", response))
                            addAriaMessage(response)
                            voiceEngine.speak(response)
                        },
                        onFailure = { error ->
                            conversationHistory.removeLastOrNull()
                            val errorMsg = "Error de conexión con ARIA AI: ${error.message?.take(80)}"
                            addAriaMessage(errorMsg)
                            voiceEngine.speak("Ocurrió un error al conectar con el sistema de inteligencia artificial.")
                        }
                    )
                    setAssistantState(HUDRadarView.State.IDLE)
                    binding.tvStatusLabel.text = getString(R.string.idle)
                }
            }
        }
    }

    private fun executeAction(action: SystemAction) {
        val controller = commandProcessor
        // PhoneController is used inside CommandProcessor
        // Actions that need system intents are started via their own startActivity calls
        // which are handled in PhoneController.executeAction()
        val phoneController = com.aria.assistant.controller.PhoneController(this)
        phoneController.executeAction(action)
    }

    private fun addUserMessage(text: String) {
        binding.tvEmptyChat.visibility = View.GONE
        adapter.addMessage(ChatMessage(text, isUser = true))
        scrollToBottom()
    }

    private fun addAriaMessage(text: String) {
        binding.tvEmptyChat.visibility = View.GONE
        adapter.addMessage(ChatMessage(text, isUser = false))
        scrollToBottom()
    }

    private fun scrollToBottom() {
        binding.rvMessages.post {
            val count = adapter.itemCount
            if (count > 0) {
                binding.rvMessages.smoothScrollToPosition(count - 1)
            }
        }
    }

    private fun setAssistantState(state: HUDRadarView.State) {
        binding.hudRadarView.setState(state)
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val etApiKey = dialogView.findViewById<EditText>(R.id.etApiKey)
        val etAssistantName = dialogView.findViewById<EditText>(R.id.etAssistantName)

        etApiKey.setText(prefs?.getString("api_key", ""))
        etAssistantName.setText(prefs?.getString("assistant_name", "ARIA"))

        val dialog = AlertDialog.Builder(this, R.style.ARIADialog)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnSaveSettings).setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            val name = etAssistantName.text.toString().trim().ifEmpty { "ARIA" }
            prefs?.edit()?.apply {
                putString("api_key", apiKey)
                putString("assistant_name", name)
                apply()
            }
            dialog.dismiss()
            Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun requestPermissions() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    private fun startForegroundService() {
        val serviceIntent = Intent(this, AriaForegroundService::class.java)
        startForegroundService(serviceIntent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val denied = permissions.filterIndexed { i, _ ->
                grantResults[i] != PackageManager.PERMISSION_GRANTED
            }
            if (denied.isNotEmpty()) {
                Toast.makeText(
                    this,
                    "Algunos permisos no fueron concedidos. Funcionalidad limitada.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceEngine.destroy()
    }
}
