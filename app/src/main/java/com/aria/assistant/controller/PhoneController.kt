package com.aria.assistant.controller

import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import java.util.Calendar

sealed class SystemAction {
    data class MakeCall(val number: String, val name: String = "") : SystemAction()
    data class SendSms(val number: String, val name: String, val message: String) : SystemAction()
    data class OpenApp(val packageName: String, val appName: String) : SystemAction()
    data class SetVolume(val level: Int) : SystemAction()
    data class SetBrightness(val level: Int) : SystemAction()
    data class ToggleWifi(val enable: Boolean) : SystemAction()
    data class ToggleBluetooth(val enable: Boolean) : SystemAction()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "") : SystemAction()
    data class WebSearch(val query: String) : SystemAction()
    object OpenCamera : SystemAction()
    object OpenSettings : SystemAction()
    object OpenCalculator : SystemAction()
    object OpenMaps : SystemAction()
    data class OpenUrl(val url: String) : SystemAction()
    data class PlayMusic(val query: String) : SystemAction()
}

class PhoneController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun executeAction(action: SystemAction): String {
        return try {
            when (action) {
                is SystemAction.MakeCall -> makeCall(action.number, action.name)
                is SystemAction.SendSms -> sendSms(action.number, action.name, action.message)
                is SystemAction.OpenApp -> openApp(action.packageName, action.appName)
                is SystemAction.SetVolume -> setVolume(action.level)
                is SystemAction.SetBrightness -> setBrightness(action.level)
                is SystemAction.ToggleWifi -> toggleWifi(action.enable)
                is SystemAction.ToggleBluetooth -> toggleBluetooth(action.enable)
                is SystemAction.SetAlarm -> setAlarm(action.hour, action.minute, action.label)
                is SystemAction.WebSearch -> webSearch(action.query)
                SystemAction.OpenCamera -> openCamera()
                SystemAction.OpenSettings -> openSettings()
                SystemAction.OpenCalculator -> openCalculator()
                SystemAction.OpenMaps -> openMaps()
                is SystemAction.OpenUrl -> openUrl(action.url)
                is SystemAction.PlayMusic -> playMusic(action.query)
            }
        } catch (e: Exception) {
            Log.e("PhoneController", "Error executing action: ${e.message}")
            "Error al ejecutar la acción: ${e.message}"
        }
    }

    private fun makeCall(number: String, name: String): String {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return "Llamando${if (name.isNotEmpty()) " a $name" else " al $number"}..."
    }

    private fun sendSms(number: String, name: String, message: String): String {
        val smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(number, null, message, null, null)
        return "Mensaje enviado a ${name.ifEmpty { number }}"
    }

    private fun openApp(packageName: String, appName: String): String {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            "Abriendo $appName"
        } else {
            // Try by name search
            val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val found = allApps.find {
                pm.getApplicationLabel(it).toString().lowercase().contains(appName.lowercase())
            }
            if (found != null) {
                val launchIntent = pm.getLaunchIntentForPackage(found.packageName)
                launchIntent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                launchIntent?.let { context.startActivity(it) }
                "Abriendo ${pm.getApplicationLabel(found)}"
            } else {
                "No encontré la aplicación: $appName"
            }
        }
    }

    private fun setVolume(level: Int): String {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (level / 100f * maxVolume).toInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
        return "Volumen ajustado al $level%"
    }

    private fun setBrightness(level: Int): String {
        return try {
            val brightness = (level / 100f * 255).toInt().coerceIn(0, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness
            )
            "Brillo ajustado al $level%"
        } catch (e: Exception) {
            "Necesito permiso de escritura en configuración para ajustar el brillo"
        }
    }

    @Suppress("DEPRECATION")
    private fun toggleWifi(enable: Boolean): String {
        return try {
            // In Android 10+ you need to show the wifi settings panel
            val intent = Intent(Settings.Panel.ACTION_WIFI)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            if (enable) "Abriendo configuración de WiFi para activar" else "Abriendo configuración de WiFi"
        } catch (e: Exception) {
            "No se pudo cambiar el estado del WiFi"
        }
    }

    private fun toggleBluetooth(enable: Boolean): String {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return "Abriendo configuración de Bluetooth"
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        val ampm = if (hour < 12) "AM" else "PM"
        val h = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
        return "Alarma configurada para las $h:${minute.toString().padStart(2, '0')} $ampm"
    }

    private fun webSearch(query: String): String {
        val encodedQuery = Uri.encode(query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encodedQuery"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return "Buscando: $query"
    }

    private fun openCamera(): String {
        val intent = Intent("android.media.action.STILL_IMAGE_CAMERA")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return "Abriendo cámara"
    }

    private fun openSettings(): String {
        val intent = Intent(Settings.ACTION_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return "Abriendo configuración del sistema"
    }

    private fun openCalculator(): String {
        val intent = context.packageManager.getLaunchIntentForPackage("com.android.calculator2")
            ?: Intent().apply { action = Intent.ACTION_VIEW }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return "Abriendo calculadora"
    }

    private fun openMaps(): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return "Abriendo mapas"
    }

    private fun openUrl(url: String): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return "Abriendo $url"
    }

    private fun playMusic(query: String): String {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://media/external/audio/media")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            "Abriendo música"
        } catch (e: Exception) {
            val spotifyIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (spotifyIntent != null) {
                spotifyIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(spotifyIntent)
                "Abriendo Spotify"
            } else {
                "No encontré una app de música compatible"
            }
        }
    }

    fun getContactNumber(name: String): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            } else null
        }
    }

    fun getCurrentVolume(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current.toFloat() / max * 100).toInt()
    }

    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled
}
