package ir.bot.parsoil

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.net.http.SslError
import android.os.*
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BotActivity : Activity() {
    private lateinit var store: Store
    private lateinit var web: WebView
    private lateinit var tvState: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvPerson: TextView
    private lateinit var tvClock: TextView
    private lateinit var dotHolder: LinearLayout
    private val h = Handler(Looper.getMainLooper())
    private var profiles = listOf<Profile>()
    private var cur = 0
    private var refreshing = true
    private var opened = false
    private var attempts = 0
    private var chooserCount = 0
    private var lastReload = 0L
    private var lastClick = 0L
    private var armed = false
    private var stateColor = Ui.warn
    private var stateMsg = "آماده"
    private val logLines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun log(s: String) {
        logLines.addFirst("${fmt.format(Date())}  $s")
        while (logLines.size > 80) logLines.removeLast()
    }
    private fun state(color: Int, msg: String) { stateColor = color; stateMsg = msg; log(msg) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Ui.navy
        store = Store(this)
        profiles = store.profiles.filter { it.enabled }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setBackgroundColor(Ui.bg) }

        // ---- پنل وضعیت ----
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.navy)
            setPadding(Ui.dp(this@BotActivity, 14), Ui.dp(this@BotActivity, 30), Ui.dp(this@BotActivity, 14), Ui.dp(this@BotActivity, 10))
        }
        dotHolder = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tvState = Ui.text(this, stateMsg, 16f, Color.WHITE, true)
        dotHolder.addView(tvState)
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, Ui.dp(this@BotActivity, 6), 0, 0) }
        tvCount = Ui.text(this, "", 13f, Color.parseColor("#FFE9A8"), true)
        tvPerson = Ui.text(this, "", 13f, Color.WHITE)
        tvClock = Ui.text(this, "", 13f, Color.parseColor("#B8C4E0"))
        listOf(tvCount, tvPerson, tvClock).forEach { stats.addView(Ui.weight(it)) }
        panel.addView(dotHolder); panel.addView(stats)

        // ---- WebView ----
        web = WebView(this).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // مثل کروم معرفی شو (بعضی سایت‌ها "wv" را نمی‌پسندند)
            settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        }.also { wv ->
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            wv.webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(v: WebView, hd: SslErrorHandler, e: SslError) {
                    // سایت گواهی نامعتبر دارد (در کروم هم قرمز است)؛ فقط برای پارس‌اویل ادامه بده
                    if (Uri.parse(e.url).host?.endsWith("parsoilco.ir") == true) { log("SSL نامعتبر (ادامه)"); hd.proceed() } else hd.cancel()
                }
                override fun onPageFinished(v: WebView, u: String) {
                    log("لود شد: ${v.title?.take(30) ?: u.take(40)}")
                    if (!opened) probe()
                }
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    if (r.isForMainFrame) {
                        state(Ui.bad, "خطا ${e.errorCode}: ${e.description}")
                        lastReload = System.currentTimeMillis() - (store.refreshSec * 1000).toLong() + 500
                    }
                }
                override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: WebResourceResponse) {
                    if (r.isForMainFrame) {
                        state(Ui.bad, "پاسخ سرور ${e.statusCode}")
                        lastReload = System.currentTimeMillis() - (store.refreshSec * 1000).toLong() + 500
                    }
                }
            }
            wv.webChromeClient = object : WebChromeClient() {
                // جایگزین: اگر خود سایت دیالوگ انتخاب فایل باز کرد، به نوبت عکس روی/پشت را بده
                override fun onShowFileChooser(w: WebView, c: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                    val pr = profiles.getOrNull(cur); val path = if (chooserCount++ % 2 == 0) pr?.front else pr?.back
                    c.onReceiveValue(if (!path.isNullOrEmpty()) arrayOf(Uri.fromFile(File(path))) else null)
                    return true
                }
            }
        }

        // ---- نوار دکمه‌ها ----
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE)
            setPadding(Ui.dp(this@BotActivity, 6), Ui.dp(this@BotActivity, 6), Ui.dp(this@BotActivity, 6), Ui.dp(this@BotActivity, 6)) }
        bar.addView(Ui.weight(Ui.button(this, "نفر بعد", Ui.navy, 12f) { nextPerson() }))
        bar.addView(Ui.weight(Ui.button(this, "پر کن", Ui.ok, 12f) { fill(true) }))
        bar.addView(Ui.weight(Ui.button(this, "⏯ رفرش", Ui.gold, 12f) { toggle() }))
        bar.addView(Ui.weight(Ui.button(this, "بازرسی", Ui.muted, 12f) { inspect() }))
        bar.addView(Ui.weight(Ui.button(this, "لاگ", Ui.muted, 12f) { showLog() }))

        root.addView(panel); root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(bar)
        setContentView(root)

        if (profiles.isEmpty()) Toast.makeText(this, "هیچ نفری فعال نیست", Toast.LENGTH_LONG).show()
        state(Ui.warn, "در حال شروع...")
        startLoad()
        h.post(uiTick)
        h.post(ticker)
    }

    private fun startLoad() {
        lastReload = System.currentTimeMillis(); attempts++
        web.loadUrl(store.url)
    }

    private fun toggle() {
        refreshing = !refreshing; opened = false
        if (refreshing) { state(Ui.warn, "رفرش روشن شد"); h.post(ticker) } else state(Ui.muted, "رفرش متوقف شد")
    }

    private fun startMillis(): Long {
        val t = store.startAt.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (t.size < 2) return 0
        return Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, t[0]); set(Calendar.MINUTE, t[1])
            set(Calendar.SECOND, t.getOrElse(2) { 0 }); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }

    // شمارنده و ساعت روی صفحه (هر ۰.۲۵ ثانیه)
    private val uiTick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis(); val t = startMillis()
            tvState.text = stateMsg
            dotHolder.removeAllViews()
            dotHolder.addView(Ui.dot(this@BotActivity, stateColor)); dotHolder.addView(tvState)
            tvCount.text = "رفرش: $attempts"
            tvPerson.text = "نفر ${if (profiles.isEmpty()) 0 else cur + 1}/${profiles.size}"
            tvClock.text = if (t > now) "شروع تا ${(t - now) / 1000}s" else fmt.format(Date(now))
            h.postDelayed(this, 250)
        }
    }

    // قبل از ساعت شروع منتظر؛ بعد هر ۰.۱۵ ثانیه دکمه را چک/کلیک و طبق فاصله رفرش می‌کند
    private val ticker = object : Runnable {
        override fun run() {
            if (!refreshing || opened) return
            val now = System.currentTimeMillis(); val t = startMillis()
            if (t > 0 && now < t - 5000) { stateColor = Ui.warn; stateMsg = "منتظر ساعت شروع"; h.postDelayed(this, 250); return }
            if (!armed) { armed = true; lastReload = 0; state(Ui.warn, "شروع رفرش و پایش دکمه") }
            if (now - lastClick > 1200) {
                web.evaluateJavascript(Js.CLICK_BOOK) { r ->
                    val txt = r?.trim('"') ?: ""
                    if (txt.isNotEmpty() && txt != "null") { lastClick = System.currentTimeMillis(); state(Ui.ok, "کلیک شد: $txt") }
                }
            }
            val loading = web.progress < 100
            val limit = if (loading) 8000L else (store.refreshSec * 1000).toLong()
            if (now - lastReload > limit) {
                if (loading) { web.stopLoading(); log("تایم‌اوت ۸ ثانیه") }
                lastReload = now; attempts++
                web.reload()
            }
            h.postDelayed(this, 150)
        }
    }

    // آیا فرم باز شده؟ فیلد «کد ملی» دیده می‌شود؟
    private fun probe(tries: Int = 0) {
        web.evaluateJavascript(Js.IS_OPEN) { r ->
            if (r == "true") onOpen()
            else if (tries < 4) h.postDelayed({ if (!opened) probe(tries + 1) }, 250)
            else if (stateColor != Ui.bad) { stateColor = Ui.warn; stateMsg = "منتظر فعال شدن نوبت" }
        }
    }

    private fun onOpen() {
        if (opened) return
        opened = true
        state(Ui.ok, "فرم باز شد! پر می‌شود...")
        try { ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500) } catch (_: Exception) {}
        val vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(800, 255)) else @Suppress("DEPRECATION") vib.vibrate(800)
        fill(false)
        h.postDelayed({ fill(true) }, 900) // بار دوم برای ویجت‌هایی که دیر لود می‌شوند + فوکوس روی کد امنیتی
    }

    private fun fill(focusCap: Boolean) {
        val p = profiles.getOrNull(cur) ?: run { state(Ui.bad, "نفری برای پر کردن نیست"); return }
        fun b64(path: String) = if (path.isEmpty() || !File(path).exists()) "" else "data:image/jpeg;base64," + Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)
        val data = JSONObject(p.toJson().toString()).put("frontData", b64(p.front)).put("backData", b64(p.back)).put("focusCap", focusCap)
        web.evaluateJavascript("(" + Js.FILL + ")(" + data + ")", null)
        if (focusCap) {
            state(Ui.ok, "پر شد ✓ فقط محصول + کد امنیتی + ثبت")
            web.requestFocus()
            h.postDelayed({ (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(web, 0) }, 300)
        }
    }

    private fun nextPerson() {
        if (cur + 1 >= profiles.size) { Toast.makeText(this, "نفر دیگری نیست", Toast.LENGTH_SHORT).show(); return }
        cur++; opened = false; attempts = 0; refreshing = true; armed = false
        state(Ui.warn, "نفر بعدی: ${profiles[cur].title()}")
        h.removeCallbacks(ticker); startLoad(); h.post(ticker)
    }

    private fun dialogText(title: String, text: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("باشه", null)
            .setNeutralButton("کپی") { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("log", text))
                Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showLog() = dialogText("لاگ", logLines.joinToString("\n").ifEmpty { "خالی" })

    private fun inspect() = web.evaluateJavascript(Js.INSPECT) { r ->
        val t = r?.let { JSONObject("{\"v\":$it}").getString("v") } ?: ""
        dialogText("فیلدها و دکمه‌های صفحه", t)
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }
}
