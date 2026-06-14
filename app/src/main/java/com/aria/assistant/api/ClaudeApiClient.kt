package com.aria.assistant.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Message(val role: String, val content: String)

data class ClaudeRequest(
    val model: String,
    @SerializedName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<Message>
)

data class ClaudeResponse(
    val content: List<ContentBlock>?,
    val error: ErrorBlock?
)

data class ContentBlock(val type: String, val text: String?)
data class ErrorBlock(val type: String, val message: String)

class ClaudeApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val systemPrompt = """
        Eres ARIA (Artificial Responsive Intelligence Assistant), un asistente de voz personal avanzado
        para Android, inspirado en J.A.R.V.I.S de Iron Man, Siri y Alexa.

        PERSONALIDAD:
        - Eres inteligente, eficiente y ligeramente sofisticado
        - Hablas en español por defecto (a menos que el usuario hable en otro idioma)
        - Eres proactivo y anticipas las necesidades del usuario
        - Tienes un toque de personalidad tecnológica/futurista
        - Eres conciso pero completo en tus respuestas

        CAPACIDADES QUE PUEDES INDICAR AL USUARIO:
        - Control de llamadas: "llamar a [contacto]", "llamar al [número]"
        - Mensajes: "enviar mensaje a [contacto]: [mensaje]"
        - Apps: "abrir [nombre de app]"
        - Configuración: "activar WiFi/Bluetooth", "subir/bajar volumen", "aumentar/disminuir brillo"
        - Alarmas: "poner alarma a las [hora]"
        - Búsqueda: "buscar [término]"
        - Cámara: "abrir cámara"
        - Información del sistema: "estado de batería", "hora actual"

        FORMATO DE RESPUESTA:
        - Sé conciso (máximo 2-3 oraciones para respuestas simples)
        - Para comandos del sistema, responde con "Ejecutando: [acción]"
        - Para preguntas generales, responde con información útil y directa
        - No uses markdown en tus respuestas de voz
        - Usa un tono profesional pero amigable

        Cuando detectes un comando de sistema (llamar, enviar mensaje, abrir app, etc.),
        primero confirma la acción brevemente y luego procede.
    """.trimIndent()

    suspend fun sendMessage(
        apiKey: String,
        conversationHistory: List<Message>,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messages = conversationHistory.toMutableList()
            messages.add(Message("user", userMessage))

            val request = ClaudeRequest(
                model = "claude-haiku-4-5",
                maxTokens = 1024,
                system = systemPrompt,
                messages = messages
            )

            val jsonBody = gson.toJson(request)
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("API Error ${response.code}: $responseBody"))
            }

            val claudeResponse = gson.fromJson(responseBody, ClaudeResponse::class.java)

            if (claudeResponse.error != null) {
                return@withContext Result.failure(IOException(claudeResponse.error.message))
            }

            val text = claudeResponse.content?.firstOrNull { it.type == "text" }?.text
                ?: return@withContext Result.failure(IOException("Empty response from AI"))

            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
