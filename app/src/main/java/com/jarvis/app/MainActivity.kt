package com.jarvis.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.text.TextUtils
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var recognizer: SpeechRecognizer? = null
    private var continuous = false
    private var listening = false

    private val prefs by lazy { getSharedPreferences("jarvis", Context.MODE_PRIVATE) }
    private fun addr(): String = prefs.getString("addr", "") ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#01070d"))
        setContentView(web)
        setupWebView()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        if (addr().isEmpty()) askAddress() else web.loadUrl(addr())
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(Bridge(), "AndroidJarvis")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            // Solo permite navegar dentro del PC de Jarvis configurado
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val host = request?.url?.host ?: return true
                return host != Uri.parse(addr()).host
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    view?.loadDataWithBaseURL(null, errorHtml(), "text/html", "UTF-8", null)
                }
            }
        }
    }

    private fun errorHtml(): String {
        val u = TextUtils.htmlEncode(addr())
        return "<body style='background:#01070d;color:#9fe8ff;font-family:sans-serif;padding:24px;text-align:center'>" +
            "<h2>No pude conectar con Jarvis</h2><p>$u</p>" +
            "<p>Revisa que el PC esté encendido, con Jarvis corriendo (iniciar_jarvis_celular.bat), y que este celular use el mismo Wi-Fi.</p>" +
            "<p><button style='font-size:18px;padding:10px 20px' onclick=\"location.href='$u'\">Reintentar</button></p>" +
            "<p><button style='font-size:18px;padding:10px 20px' onclick=\"AndroidJarvis.changeAddress()\">Cambiar dirección o PIN</button></p></body>"
    }

    private fun askAddress() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(pad, pad, pad, 0)
        val a = EditText(this)
        a.hint = "http://192.168.1.25:8765"
        a.setText(addr())
        a.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        val p = EditText(this)
        p.hint = "PIN de Jarvis"
        p.setText(prefs.getString("pin", ""))
        p.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        col.addView(a)
        col.addView(p)
        AlertDialog.Builder(this)
            .setTitle("Conectar con Jarvis")
            .setMessage("Escribe la dirección que muestra la ventana de Jarvis en tu PC y el PIN que pusiste en .env.")
            .setView(col)
            .setCancelable(false)
            .setPositiveButton("Conectar") { _, _ ->
                var url = a.text.toString().trim()
                val pin = p.text.toString().trim()
                if (url.isEmpty()) {
                    askAddress()
                    return@setPositiveButton
                }
                if (!url.startsWith("http")) url = "http://$url"
                prefs.edit().putString("addr", url).putString("pin", pin).apply()
                web.loadUrl(url)
            }
            .show()
    }

    // ---------- Micrófono nativo de Android (no necesita https) ----------
    private fun js(code: String) {
        web.evaluateJavascript(code, null)
    }

    private fun startListening(cont: Boolean) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Este celular no tiene reconocimiento de voz disponible", Toast.LENGTH_LONG).show()
            js("onNativeState('idle')")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            js("onNativeState('idle')")
            return
        }
        continuous = cont
        listening = true
        beginRecognition()
    }

    private fun stopListening() {
        continuous = false
        listening = false
        recognizer?.destroy()
        recognizer = null
        js("onNativeState('idle')")
    }

    private fun beginRecognition() {
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (t.isNotBlank()) js("onNativeSpeech(" + JSONObject.quote(t) + ")")
                again(300L)
            }

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) continuous = false
                again(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1000L else 300L)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
        r.startListening(i)
    }

    private fun again(delayMs: Long) {
        if (continuous && listening) {
            web.postDelayed({ if (continuous && listening) beginRecognition() }, delayMs)
        } else {
            listening = false
            js("onNativeState('idle')")
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun listen(cont: Boolean) {
            runOnUiThread { startListening(cont) }
        }

        @JavascriptInterface
        fun stop() {
            runOnUiThread { stopListening() }
        }

        @JavascriptInterface
        fun changeAddress() {
            runOnUiThread { askAddress() }
        }

        @JavascriptInterface
        fun getPin(): String = prefs.getString("pin", "") ?: ""
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        super.onDestroy()
    }
}
