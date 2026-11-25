package ir.bot.parsoil

import android.app.Activity
import android.app.AlertDialog
import android.annotation.SuppressLint
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import org.json.JSONObject
import java.io.File

class BotActivity : Activity() {
    private lateinit var store: Store
    private lateinit var web: WebView
    private lateinit var status: TextView
    private val h = Handler(Looper.getMainLooper())
    private var profiles = listOf<Profile>()
    private var cur = 0
    private var refreshing = true
    private var opened = false
    private var attempts = 0
    private var chooserCount = 0
    private var cb: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = Store(this)
        profiles = store.profiles.filter { it.enabled }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply { setPadding(16, 8, 16, 8); setBackgroundColor(Color.parseColor("#223c78")); setTextColor(Color.WHITE) }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, u: String) { if (!opened) probe() }
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    if (r.isForMainFrame) h.postDelayed({ scheduleReload() }, 100)
                }
                override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: WebResourceResponse) {
                    if (r.isForMainFrame) { setStatus("HTTP ${e.statusCode}") }
                }
            }
            webChromeClient = object : WebChromeClient() {
                // جایگزین: اگر خود سایت دیالوگ انتخاب فایل باز کرد، به نوبت عکس روی/پشت را بده
                override fun onShowFileChooser(w: WebView, c: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                    val pr = profiles.getOrNull(cur); val path = if (chooserCount++ % 2 == 0) pr?.front else pr?.back
                    c.onReceiveValue(if (!path.isNullOrEmpty()) arrayOf(Uri.fromFile(File(path))) else null)
                    return true
                }
            }
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun b(t: String, f: () -> Unit) = Button(this).apply { text = t; textSize = 11f; setOnClickListener { f() }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        bar.addView(b("نفر بعد") { nextPerson() })
        bar.addView(b("پر کن") { fill() })
        bar.addView(b("رفرش ⏯") { refreshing = !refreshing; opened = false; if (refreshing) scheduleReload() else setStatus("رفرش متوقف") })
        bar.addView(b("بازرسی") { inspect() })
        root.addView(status); root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(bar)
        setContentView(root)
        if (profiles.isEmpty()) Toast.makeText(this, "هیچ نفری فعال نیست", Toast.LENGTH_LONG).show()
        web.loadUrl(store.url)
    }

    private fun setStatus(s: String) { status.text = "نفر ${cur + 1}/${profiles.size} (${profiles.getOrNull(cur)?.title() ?: "-"}) | تلاش $attempts | $s" }

    private fun scheduleReload() {
        if (!refreshing || opened) return
        h.removeCallbacksAndMessages(null)
        h.postDelayed({ if (refreshing && !opened) { attempts++; setStatus("رفرش..."); web.reload() } }, (store.refreshSec * 1000).toLong())
    }

    // آیا فرم باز شده؟ فیلد «کد ملی» دیده می‌شود؟
    private fun probe(tries: Int = 0) {
        web.evaluateJavascript(Js.IS_OPEN) { r ->
            if (r == "true") onOpen()
            else if (tries < 4) h.postDelayed({ if (!opened) probe(tries + 1) }, 250)
            else { setStatus("فرم هنوز بسته است"); scheduleReload() }
        }
    }

    private fun onOpen() {
        if (opened) return
        opened = true; h.removeCallbacksAndMessages(null)
        try { ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500) } catch (_: Exception) {}
        (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(800, 255))
        fill()
    }

    private fun fill() {
        val p = profiles.getOrNull(cur) ?: return
        fun b64(path: String) = if (path.isEmpty()) "" else "data:image/jpeg;base64," + Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)
        val data = JSONObject(p.toJson().toString()).put("frontData", b64(p.front)).put("backData", b64(p.back))
        web.evaluateJavascript("(" + Js.FILL + ")(" + data + ")", null)
        setStatus("فرم باز شد و پر شد — محصول را انتخاب کن و مرحله بعد")
    }

    private fun nextPerson() {
        if (cur + 1 >= profiles.size) { Toast.makeText(this, "نفر دیگری نیست", Toast.LENGTH_SHORT).show(); return }
        cur++; opened = false; attempts = 0; refreshing = true
        web.loadUrl(store.url)
    }

    private fun inspect() = web.evaluateJavascript(Js.INSPECT) { r ->
        val t = r?.let { JSONObject("{\"v\":$it}").getString("v") } ?: ""
        AlertDialog.Builder(this).setTitle("فیلدهای صفحه").setMessage(t).setPositiveButton("باشه", null).show()
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }
}
