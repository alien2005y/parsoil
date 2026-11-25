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
import java.util.Calendar
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
    private var lastReload = 0L
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
                override fun onPageFinished(v: WebView, u: String) { lastReload = System.currentTimeMillis(); if (!opened) probe() }
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    if (r.isForMainFrame) setStatus("خطای شبکه")
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
        bar.addView(b("رفرش ⏯") { refreshing = !refreshing; opened = false; if (refreshing) h.post(ticker) else setStatus("رفرش متوقف") })
        bar.addView(b("بازرسی") { inspect() })
        root.addView(status); root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(bar)
        setContentView(root)
        if (profiles.isEmpty()) Toast.makeText(this, "هیچ نفری فعال نیست", Toast.LENGTH_LONG).show()
        web.loadUrl(store.url)
        h.post(ticker)
    }

    private fun setStatus(s: String) { status.text = "نفر ${cur + 1}/${profiles.size} (${profiles.getOrNull(cur)?.title() ?: "-"}) | تلاش $attempts | $s" }

    private fun startMillis(): Long {
        val t = store.startAt.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (t.size < 2) return 0
        return Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, t[0]); set(Calendar.MINUTE, t[1])
            set(Calendar.SECOND, t.getOrElse(2) { 0 }); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }

    // قبل از ساعت شروع منتظر می‌ماند؛ بعد هر ۰.۱۵ ثانیه دکمه نوبت‌گیری را چک و کلیک می‌کند
    private val ticker = object : Runnable {
        override fun run() {
            if (!refreshing || opened) return
            val now = System.currentTimeMillis(); val t = startMillis()
            if (t > 0 && now < t - 5000) { setStatus("شروع تا ${(t - now) / 1000} ثانیه دیگر"); h.postDelayed(this, 250); return }
            web.evaluateJavascript(Js.CLICK_BOOK) { if (it == "true") setStatus("روی نوبت‌گیری کلیک شد...") }
            if (now - lastReload > store.refreshSec * 1000) { lastReload = now; attempts++; web.reload() }
            h.postDelayed(this, 150)
        }
    }

    // آیا فرم باز شده؟ فیلد «کد ملی» دیده می‌شود؟
    private fun probe(tries: Int = 0) {
        web.evaluateJavascript(Js.IS_OPEN) { r ->
            if (r == "true") onOpen()
            else if (tries < 4) h.postDelayed({ if (!opened) probe(tries + 1) }, 250)
            else setStatus("منتظر فعال شدن نوبت‌گیری")
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
        h.removeCallbacksAndMessages(null); web.loadUrl(store.url); h.post(ticker)
    }

    private fun inspect() = web.evaluateJavascript(Js.INSPECT) { r ->
        val t = r?.let { JSONObject("{\"v\":$it}").getString("v") } ?: ""
        AlertDialog.Builder(this).setTitle("فیلدهای صفحه").setMessage(t).setPositiveButton("باشه", null).show()
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }
}
