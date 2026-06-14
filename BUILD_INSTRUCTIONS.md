# ARIA - Artificial Responsive Intelligence Assistant
## Asistente de Voz Android con IA

---

## ¿Qué es ARIA?

ARIA es un asistente personal de voz para Android inspirado en J.A.R.V.I.S (Iron Man), Siri y Alexa. Combina reconocimiento de voz, inteligencia artificial (Claude de Anthropic) y control completo del teléfono en una sola app con interfaz futurista.

---

## Características

### Interfaz
- HUD circular animado estilo J.A.R.V.I.S
- Tema oscuro con colores neón cyan/azul
- Visualizador de audio en tiempo real
- Radar rotativo con efectos de barrido
- Historial de conversación tipo chat

### Control del Teléfono (sin root)
- 📞 Llamar a contactos o números
- 💬 Enviar SMS
- 📱 Abrir cualquier aplicación
- 🔊 Controlar volumen
- ☀️ Ajustar brillo
- 📡 Activar/desactivar WiFi y Bluetooth
- ⏰ Crear alarmas
- 🔍 Búsqueda web
- 📷 Abrir cámara
- ⚙️ Acceso a configuración del sistema

### Inteligencia Artificial
- Integración con Claude AI (Anthropic)
- Conversación natural en español
- Contexto de conversación persistente
- Respuestas de voz sintetizadas

---

## Cómo Compilar el APK

### Opción 1: Android Studio (Recomendado)

1. Instala [Android Studio](https://developer.android.com/studio)
2. Clona o descarga este repositorio
3. Abre el proyecto en Android Studio (`File > Open`)
4. Espera a que sincronice Gradle
5. Ve a `Build > Build Bundle(s) / APK(s) > Build APK(s)`
6. El APK estará en `app/build/outputs/apk/debug/app-debug.apk`

### Opción 2: Línea de Comandos

```bash
# Asegúrate de tener Java 17+ y Android SDK
export ANDROID_HOME=/path/to/android/sdk

# En Linux/Mac:
chmod +x gradlew
./gradlew assembleDebug

# En Windows:
gradlew.bat assembleDebug
```

El APK generado estará en:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Instalación en el Teléfono

1. En tu Android, ve a **Ajustes > Seguridad > Fuentes desconocidas** (o "Instalar apps desconocidas") y actívalo
2. Copia el APK a tu teléfono (por USB, email, Drive, etc.)
3. Abre el archivo APK en tu teléfono
4. Sigue las instrucciones de instalación

---

## Configuración Inicial

1. Abre ARIA
2. Concede todos los permisos que solicite (micrófono, contactos, teléfono, etc.)
3. Toca el ícono ⚙️ de configuración
4. Ingresa tu **API Key de Anthropic Claude**:
   - Ve a [console.anthropic.com](https://console.anthropic.com)
   - Crea una cuenta y genera una API Key
   - La key comienza con `sk-ant-...`
5. Guarda la configuración

---

## Comandos de Voz

| Comando | Acción |
|---------|--------|
| "Llama a [nombre]" | Llama a ese contacto |
| "Llama al [número]" | Marca ese número |
| "Envía mensaje a [nombre]: [texto]" | Envía SMS |
| "Abre [app]" | Lanza una aplicación |
| "Sube el volumen" / "Baja el volumen" | Controla el volumen |
| "Pon alarma a las [hora]" | Crea una alarma |
| "Busca [término]" | Busca en Google |
| "Abre la cámara" | Abre la cámara |
| "Activa WiFi" | Abre configuración WiFi |
| "¿Qué hora es?" | Dice la hora actual |
| "¿Qué día es hoy?" | Dice la fecha |
| Cualquier pregunta | Responde con IA |

---

## Tecnologías

- **Lenguaje**: Kotlin
- **Min SDK**: Android 8.0 (API 26)
- **IA**: Claude Haiku (Anthropic API)
- **Voz**: Android SpeechRecognizer + TextToSpeech
- **UI**: Custom Views (Canvas/OpenGL), Material Design 3
- **Red**: OkHttp3

---

## Estructura del Proyecto

```
app/src/main/
├── java/com/aria/assistant/
│   ├── MainActivity.kt          # Actividad principal
│   ├── VoiceEngine.kt           # Motor de voz (STT + TTS)
│   ├── api/
│   │   └── ClaudeApiClient.kt   # Cliente API de Claude
│   ├── controller/
│   │   ├── PhoneController.kt   # Control del teléfono
│   │   └── CommandProcessor.kt  # Procesador de comandos
│   ├── ui/
│   │   ├── HUDRadarView.kt     # Vista del radar J.A.R.V.I.S
│   │   ├── AudioWaveformView.kt # Visualizador de audio
│   │   └── MessageAdapter.kt    # Adaptador de chat
│   └── service/
│       ├── AriaForegroundService.kt
│       └── NotificationListenerService.kt
└── res/
    ├── layout/                  # Layouts XML
    ├── drawable/                # Íconos y fondos
    └── values/                  # Colores, strings, temas
```
