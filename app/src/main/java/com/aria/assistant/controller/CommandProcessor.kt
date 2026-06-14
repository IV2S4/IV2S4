package com.aria.assistant.controller

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

class CommandProcessor(private val context: Context) {

    private val phoneController = PhoneController(context)

    data class CommandResult(
        val handled: Boolean,
        val response: String,
        val action: SystemAction? = null
    )

    fun processCommand(input: String): CommandResult {
        val lower = input.lowercase().trim()

        // === LLAMADAS ===
        val callPatterns = listOf(
            Regex("llama(?:r)? a (.+)"),
            Regex("haz una llamada a (.+)"),
            Regex("llama al (.+)"),
            Regex("llamar a (.+)")
        )
        callPatterns.forEach { pattern ->
            pattern.find(lower)?.let { match ->
                val target = match.groupValues[1].trim()
                val number = if (target.matches(Regex("[0-9+ ()-]+"))) {
                    target.replace(" ", "")
                } else {
                    phoneController.getContactNumber(target)
                }
                return if (number != null) {
                    CommandResult(
                        true,
                        "Llamando a $target...",
                        SystemAction.MakeCall(number, target)
                    )
                } else {
                    CommandResult(true, "No encontré el contacto: $target. Verifica el nombre o di el número directamente.")
                }
            }
        }

        // === MENSAJES SMS ===
        val smsPattern = Regex("(?:envía|manda|enviar|mandar) (?:un )?(?:mensaje|sms|texto) a (.+?)[:,.] ?(.+)")
        smsPattern.find(lower)?.let { match ->
            val recipient = match.groupValues[1].trim()
            val message = match.groupValues[2].trim()
            val number = phoneController.getContactNumber(recipient)
            return if (number != null) {
                CommandResult(
                    true,
                    "Enviando mensaje a $recipient: \"$message\"",
                    SystemAction.SendSms(number, recipient, message)
                )
            } else {
                CommandResult(true, "No encontré el contacto: $recipient")
            }
        }

        // === ABRIR APPS ===
        val openPatterns = listOf(
            Regex("abr(?:e|ir) (.+)"),
            Regex("abre la(?:?) app(?:licación)? (.+)"),
            Regex("lanzar (.+)")
        )
        openPatterns.forEach { pattern ->
            pattern.find(lower)?.let { match ->
                val appName = match.groupValues[1].trim()
                val packageName = resolveAppPackage(appName)
                return CommandResult(
                    true,
                    "Abriendo $appName...",
                    SystemAction.OpenApp(packageName, appName)
                )
            }
        }

        // === VOLUMEN ===
        when {
            lower.contains("sube el volumen") || lower.contains("volumen más alto") ||
                    lower.contains("aumenta el volumen") || lower.contains("más volumen") -> {
                val current = phoneController.getCurrentVolume()
                val newLevel = minOf(100, current + 20)
                return CommandResult(true, "Subiendo el volumen al $newLevel%", SystemAction.SetVolume(newLevel))
            }
            lower.contains("baja el volumen") || lower.contains("volumen más bajo") ||
                    lower.contains("disminuye el volumen") || lower.contains("menos volumen") -> {
                val current = phoneController.getCurrentVolume()
                val newLevel = maxOf(0, current - 20)
                return CommandResult(true, "Bajando el volumen al $newLevel%", SystemAction.SetVolume(newLevel))
            }
            lower.contains("silencio") || lower.contains("silenciar") || lower.contains("modo silencio") -> {
                return CommandResult(true, "Poniendo en silencio", SystemAction.SetVolume(0))
            }
        }

        Regex("(?:pon|poner|ajusta|ajustar) (?:el )?volumen (?:al? )?([0-9]+)").find(lower)?.let {
            val level = it.groupValues[1].toIntOrNull() ?: 50
            return CommandResult(true, "Ajustando volumen al $level%", SystemAction.SetVolume(level))
        }

        // === BRILLO ===
        when {
            lower.contains("sube el brillo") || lower.contains("más brillo") ||
                    lower.contains("aumenta el brillo") -> {
                return CommandResult(true, "Aumentando el brillo", SystemAction.SetBrightness(80))
            }
            lower.contains("baja el brillo") || lower.contains("menos brillo") -> {
                return CommandResult(true, "Disminuyendo el brillo", SystemAction.SetBrightness(30))
            }
        }

        // === WIFI ===
        when {
            lower.contains("activa") && (lower.contains("wifi") || lower.contains("wi-fi")) ||
                    lower.contains("enciende el wifi") || lower.contains("conectar wifi") -> {
                return CommandResult(true, "Activando WiFi...", SystemAction.ToggleWifi(true))
            }
            lower.contains("desactiva") && (lower.contains("wifi") || lower.contains("wi-fi")) ||
                    lower.contains("apaga el wifi") -> {
                return CommandResult(true, "Desactivando WiFi...", SystemAction.ToggleWifi(false))
            }
        }

        // === BLUETOOTH ===
        when {
            lower.contains("activa") && lower.contains("bluetooth") ||
                    lower.contains("enciende el bluetooth") -> {
                return CommandResult(true, "Abriendo configuración de Bluetooth...", SystemAction.ToggleBluetooth(true))
            }
            lower.contains("desactiva") && lower.contains("bluetooth") ||
                    lower.contains("apaga el bluetooth") -> {
                return CommandResult(true, "Abriendo configuración de Bluetooth...", SystemAction.ToggleBluetooth(false))
            }
        }

        // === ALARMAS ===
        val alarmPattern = Regex("(?:pon|poner|crear|programa|programar) (?:una )?alarma (?:para las? |a las? )?([0-9]{1,2}):?([0-9]{0,2})\\s*(am|pm)?")
        alarmPattern.find(lower)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val ampm = match.groupValues[3]
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return CommandResult(
                true,
                "Configurando alarma...",
                SystemAction.SetAlarm(hour, minute, "ARIA")
            )
        }

        // === BÚSQUEDA ===
        val searchPatterns = listOf(
            Regex("busca(?:r)? (.+)"),
            Regex("busca en (?:google|internet|web) (.+)"),
            Regex("googlea (.+)"),
            Regex("qué es (.+)")
        )
        searchPatterns.forEach { pattern ->
            pattern.find(lower)?.let { match ->
                val query = match.groupValues[1].trim()
                return CommandResult(true, "Buscando: $query", SystemAction.WebSearch(query))
            }
        }

        // === CÁMARA ===
        if (lower.contains("abre la cámara") || lower.contains("tomar foto") ||
                lower.contains("toma una foto") || lower.contains("abrir cámara")) {
            return CommandResult(true, "Abriendo cámara...", SystemAction.OpenCamera)
        }

        // === NAVEGACIÓN ===
        if (lower.contains("abre el mapa") || lower.contains("abrir mapas") ||
                lower.contains("google maps") || lower.contains("cómo llegar")) {
            return CommandResult(true, "Abriendo Maps...", SystemAction.OpenMaps)
        }

        if (lower.contains("calculadora") || lower.contains("calcular")) {
            return CommandResult(true, "Abriendo calculadora...", SystemAction.OpenCalculator)
        }

        if (lower.contains("configuración") || lower.contains("ajustes del teléfono") ||
                lower.contains("settings")) {
            return CommandResult(true, "Abriendo configuración...", SystemAction.OpenSettings)
        }

        // === MÚSICA ===
        if (lower.contains("pon música") || lower.contains("reproducir música") ||
                lower.contains("play música") || lower.contains("abrir spotify") ||
                lower.contains("música")) {
            val query = lower.replace("pon música", "").replace("reproducir", "").trim()
            return CommandResult(true, "Reproduciendo música...", SystemAction.PlayMusic(query))
        }

        // === INFO LOCAL ===
        if (lower.contains("qué hora") || lower.contains("que hora es") || lower.contains("hora actual")) {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            return CommandResult(true, "Son las $time")
        }

        if (lower.contains("qué día") || lower.contains("que dia") || lower.contains("fecha de hoy")) {
            val date = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("es")).format(Date())
            return CommandResult(true, "Hoy es $date")
        }

        if (lower.contains("cómo me llamo") || lower.contains("mi nombre")) {
            return CommandResult(true, "No tengo acceso a tu nombre directamente, pero puedo buscarlo en tus contactos si quieres.")
        }

        if (lower.contains("batería") || lower.contains("carga del teléfono")) {
            return CommandResult(true, "Verificando el nivel de batería. Revisa la barra de estado de tu teléfono para el porcentaje exacto.")
        }

        // === SALUDOS ===
        if (lower.matches(Regex("(hola|hey|hi|buenos días|buenas tardes|buenas noches|qué tal|cómo estás).?"))) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when {
                hour < 12 -> "Buenos días"
                hour < 18 -> "Buenas tardes"
                else -> "Buenas noches"
            }
            return CommandResult(true, "$greeting. Sistema ARIA operativo y listo para asistirte. ¿En qué puedo ayudarte?")
        }

        return CommandResult(false, "")
    }

    private fun resolveAppPackage(appName: String): String {
        return when {
            appName.contains("whatsapp") -> "com.whatsapp"
            appName.contains("telegram") -> "org.telegram.messenger"
            appName.contains("instagram") -> "com.instagram.android"
            appName.contains("facebook") -> "com.facebook.katana"
            appName.contains("youtube") -> "com.google.android.youtube"
            appName.contains("spotify") -> "com.spotify.music"
            appName.contains("gmail") -> "com.google.android.gm"
            appName.contains("maps") || appName.contains("mapa") -> "com.google.android.apps.maps"
            appName.contains("chrome") -> "com.android.chrome"
            appName.contains("cámara") || appName.contains("camara") || appName.contains("camera") -> "com.android.camera2"
            appName.contains("calculadora") -> "com.android.calculator2"
            appName.contains("calendario") -> "com.google.android.calendar"
            appName.contains("contactos") -> "com.android.contacts"
            appName.contains("teléfono") || appName.contains("telefono") -> "com.android.dialer"
            appName.contains("netflix") -> "com.netflix.mediaclient"
            appName.contains("twitter") || appName.contains("x") -> "com.twitter.android"
            appName.contains("tiktok") -> "com.zhiliaoapp.musically"
            appName.contains("uber") -> "com.ubercab"
            appName.contains("amazon") -> "com.amazon.mShop.android.shopping"
            appName.contains("mercado libre") || appName.contains("mercadolibre") -> "com.mercadolibre"
            appName.contains("configuración") || appName.contains("ajustes") || appName.contains("settings") -> "com.android.settings"
            else -> appName.lowercase().replace(" ", ".")
        }
    }
}
