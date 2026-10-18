package com.kpdev.tirarfoto

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var previewView: PreviewView
    private lateinit var statusView: TextView
    private lateinit var listeningView: TextView
    private lateinit var voiceButton: Button
    private lateinit var cameraButton: Button

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false
    private var voiceEnabled = true
    private var takingPhotos = false
    private var initialized = false
    private var appInForeground = false
    private var lastCommandAt = 0L

    companion object {
        private const val PERMISSION_REQUEST = 4401
        private const val COLOR_BG = 0xFF10131C.toInt()
        private const val COLOR_PANEL = 0xFF1B2030.toInt()
        private const val COLOR_PURPLE = 0xFF8B5CF6.toInt()
        private const val COLOR_WHITE = 0xFFF7F7FB.toInt()
        private const val COLOR_MUTED = 0xFFB5B9C9.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        createInterface()
        requestNeededPermissions()
    }

    private fun createInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            setPadding(dp(18), dp(14), dp(18), dp(18))
        }

        val title = TextView(this).apply {
            text = "📸  Tirar Foto por Voz"
            textSize = 24f
            setTextColor(COLOR_WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(48)))

        val subtitle = TextView(this).apply {
            text = "Diga “tirar foto” para fotografar com as duas câmeras."
            textSize = 14f
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(2), 0, dp(12))
        }
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.BLACK)
        }
        val previewParams = LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(4)
            bottomMargin = dp(12)
        }
        previewView.minimumHeight = dp(220)
        root.addView(previewView, previewParams)

        statusView = TextView(this).apply {
            text = "Preparando câmera e microfone…"
            textSize = 15f
            setTextColor(COLOR_WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(COLOR_PANEL)
        }
        root.addView(statusView, LinearLayout.LayoutParams(-1, -2))

        listeningView = TextView(this).apply {
            text = "O app só escuta enquanto está aberto."
            textSize = 13f
            setTextColor(COLOR_MUTED)
            setPadding(dp(2), dp(8), dp(2), dp(10))
        }
        root.addView(listeningView, LinearLayout.LayoutParams(-1, -2))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        voiceButton = makeButton("Pausar voz", COLOR_PURPLE)
        voiceButton.setOnClickListener {
            if (voiceEnabled) {
                voiceEnabled = false
                stopListening()
                voiceButton.text = "Ativar voz"
                statusView.text = "Comando de voz pausado."
                listeningView.text = "Toque em “Ativar voz” para voltar a ouvir."
            } else {
                voiceEnabled = true
                voiceButton.text = "Pausar voz"
                startListening()
            }
        }
        buttons.addView(voiceButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginEnd = dp(6)
        })

        cameraButton = makeButton("Tirar agora", 0xFF343B51.toInt())
        cameraButton.setOnClickListener { takeBothPhotos() }
        buttons.addView(cameraButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginStart = dp(6)
        })

        root.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun makeButton(label: String, backgroundColor: Int): Button = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(backgroundColor)
        stateListAnimator = null
    }

    private fun requestNeededPermissions() {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST)
        } else {
            initializeCameraAndVoice()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            val cameraOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (cameraOk && audioOk) {
                initializeCameraAndVoice()
            } else {
                statusView.text = "Para funcionar, permita o acesso à câmera e ao microfone nas permissões do app."
                listeningView.text = "Nenhuma foto será tirada sem o comando ou o botão manual."
            }
        }
    }

    private fun initializeCameraAndVoice() {
        if (initialized) {
            if (voiceEnabled) startListening()
            return
        }
        initialized = true
        statusView.text = "Abrindo câmera traseira…"
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                bindCamera(CameraSelector.LENS_FACING_BACK) { ready ->
                    if (ready) {
                        if (voiceEnabled) startListening()
                    } else {
                        statusView.text = "Não consegui abrir a câmera traseira. Verifique se outro app está usando a câmera."
                    }
                }
            } catch (e: Exception) {
                statusView.text = "Erro ao preparar a câmera: ${e.localizedMessage ?: "erro desconhecido"}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(lensFacing: Int, onReady: (Boolean) -> Unit) {
        val provider = cameraProvider
        if (provider == null) {
            onReady(false)
            return
        }
        try {
            provider.unbindAll()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(95)
                .build()
            provider.bindToLifecycle(this, selector, preview, capture)
            imageCapture = capture
            mainHandler.postDelayed({ onReady(true) }, 450L)
        } catch (e: Exception) {
            imageCapture = null
            onReady(false)
        }
    }

    private fun createRecognizerIfNeeded(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusView.text = "O reconhecimento de voz não está disponível neste celular."
            listeningView.text = "Instale/ative um serviço de reconhecimento de voz nas configurações do Android."
            return false
        }
        if (speechRecognizer != null) return true

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    statusView.text = "🎙️ Estou ouvindo. Diga: “tirar foto”."
                    listeningView.text = "Microfone ativo enquanto este app estiver na tela."
                }

                override fun onBeginningOfSpeech() {
                    listeningView.text = "Reconhecendo sua fala…"
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { listening = false }

                override fun onError(error: Int) {
                    listening = false
                    if (voiceEnabled && !takingPhotos && hasRequiredPermissions()) {
                        mainHandler.postDelayed({ startListening() }, 650L)
                    }
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val commandFound = phrases.any { normalize(it).contains("tirar foto") }
                    if (commandFound) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastCommandAt > 2500L) {
                            lastCommandAt = now
                            takeBothPhotos()
                        } else {
                            scheduleListeningAgain()
                        }
                    } else {
                        scheduleListeningAgain()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
        return true
    }

    private fun startListening() {
        if (!appInForeground || !voiceEnabled || takingPhotos || listening || !hasRequiredPermissions()) return
        if (!createRecognizerIfNeeded()) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try {
            listening = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            listening = false
            statusView.text = "Não consegui iniciar o microfone. Verifique as permissões e tente novamente."
            mainHandler.postDelayed({
                if (voiceEnabled && !takingPhotos) startListening()
            }, 1000L)
        }
    }

    private fun scheduleListeningAgain() {
        if (voiceEnabled && !takingPhotos) {
            mainHandler.postDelayed({ startListening() }, 350L)
        }
    }

    private fun stopListening() {
        listening = false
        try { speechRecognizer?.cancel() } catch (_: Exception) { }
    }

    private fun takeBothPhotos() {
        if (takingPhotos) return
        if (!hasRequiredPermissions()) {
            requestNeededPermissions()
            return
        }
        if (cameraProvider == null) {
            statusView.text = "A câmera ainda está carregando. Tente de novo em um instante."
            return
        }

        takingPhotos = true
        stopListening()
        cameraButton.isEnabled = false
        voiceButton.isEnabled = false
        statusView.text = "Comando reconhecido! Fotografando com a câmera traseira…"
        listeningView.text = "Aguarde: a câmera frontal será usada logo depois."

        val saved = mutableListOf<String>()
        captureFromFacing(CameraSelector.LENS_FACING_BACK, "traseira") { backOk ->
            if (backOk) saved.add("traseira")
            statusView.text = "Agora fotografando com a câmera frontal…"
            captureFromFacing(CameraSelector.LENS_FACING_FRONT, "frontal") { frontOk ->
                if (frontOk) saved.add("frontal")
                bindCamera(CameraSelector.LENS_FACING_BACK) {
                    takingPhotos = false
                    cameraButton.isEnabled = true
                    voiceButton.isEnabled = true
                    statusView.text = when (saved.size) {
                        2 -> "✅ Pronto! As duas fotos foram salvas em Pictures/TirarFoto."
                        1 -> "⚠️ Uma foto foi salva (${saved.firstOrNull()}). A outra câmera pode não estar disponível."
                        else -> "❌ Não consegui salvar as fotos. Verifique as permissões e tente novamente."
                    }
                    listeningView.text = "Diga “tirar foto” novamente quando quiser outra foto."
                    if (voiceEnabled) mainHandler.postDelayed({ startListening() }, 900L)
                }
            }
        }
    }

    private fun captureFromFacing(lensFacing: Int, label: String, done: (Boolean) -> Unit) {
        bindCamera(lensFacing) ready@{ ready ->
            if (!ready) {
                done(false)
                return@ready
            }
            val capture = imageCapture
            if (capture == null) {
                done(false)
                return@ready
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Foto_${label}_$timestamp.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TirarFoto")
            }
            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()

            capture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    done(true)
                }

                override fun onError(exception: ImageCaptureException) {
                    done(false)
                }
            })
        }
    }

    private fun normalize(text: String): String {
        return Normalizer.normalize(text.lowercase(Locale.forLanguageTag("pt-BR")), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun hasRequiredPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        appInForeground = true
        if (initialized && voiceEnabled && !takingPhotos && hasRequiredPermissions()) {
            mainHandler.postDelayed({ startListening() }, 300L)
        }
    }

    override fun onPause() {
        appInForeground = false
        if (!takingPhotos) stopListening()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        try { speechRecognizer?.destroy() } catch (_: Exception) { }
        speechRecognizer = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
