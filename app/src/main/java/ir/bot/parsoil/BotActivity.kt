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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    // آمار برای پایش
    private var loadStart = 0L; private var loads = 0; private var loadMsSum = 0L
    private var errors = 0; private var timeouts = 0; private var clicks = 0; private var fills = 0
    private var lastPath = ""
    private var navPendingUntil = 0L
    private var clickInFlight = false
    private var armedAt = 0L; private var clickedAt = 0L; private var lastCandLog = 0L

    private fun log(s: String, level: String = "INFO") = Logger.w(level, s)
    private fun state(color: Int, msg: String) {
        stateColor = color; stateMsg = msg
        log(msg, if (color == Ui.bad) "ERROR" else "INFO")
    }
    private val summaryTick = object : Runnable {
        override fun run() { logSummary(); h.postDelayed(this, 30000) }
    }
    private fun logSummary() = log("آمار: رفرش=$attempts لود=$loads میانگین=${if (loads > 0) loadMsSum / loads else 0}ms خطا=$errors تایم‌اوت=$timeouts کلیک=$clicks پرکردن=$fills")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Ui.navy
        store = Store(this)
        Logger.init(this); Logger.sessionHeader(this, store)
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
        }.also { wv ->
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            wv.webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(v: WebView, hd: SslErrorHandler, e: SslError) {
                    // سایت گواهی نامعتبر دارد (در کروم هم قرمز است)؛ فقط برای پارس‌اویل ادامه بده
                    if (Uri.parse(e.url).host?.endsWith("parsoilco.ir") == true) { log("SSL نامعتبر ${e.primaryError} (ادامه)", "WARN"); hd.proceed() } else hd.cancel()
                }
                override fun onPageStarted(v: WebView, u: String, f: android.graphics.Bitmap?) {
                    loadStart = System.currentTimeMillis()
                    val path = Uri.parse(u).path ?: ""
                    if (path != lastPath) {
                        val q = Uri.parse(u).queryParameterNames
                        log("ناوبری: $lastPath ← $path${if (q.isNotEmpty()) " پارامترها=$q" else ""}")
                        lastPath = path
                    }
                }
                override fun onPageFinished(v: WebView, u: String) {
                    val dur = if (loadStart > 0) System.currentTimeMillis() - loadStart else 0
                    navPendingUntil = 0
                    loads++; loadMsSum += dur
                    log("لود شد ${dur}ms مسیر=${Uri.parse(u).path} عنوان='${v.title?.take(30) ?: ""}'", if (dur > 3000) "WARN" else "INFO")
                    if (!opened) probe()
                }
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    if (r.isForMainFrame) {
                        errors++
                        state(Ui.bad, "خطای لود ${e.errorCode}: ${e.description}")
                        log("شبکه فعال هنگام خطا: ${Logger.netInfo(this@BotActivity)} ua=${if (store.desktop) "desktop" else "mobile"}", "WARN")
                        lastReload = System.currentTimeMillis() - (store.refreshSec * 1000).toLong() + 500
                    }
                }
                override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: WebResourceResponse) {
                    if (r.isForMainFrame) {
                        errors++
                        state(Ui.bad, "پاسخ سرور ${e.statusCode} مسیر=${r.url.path}")
                        lastReload = System.currentTimeMillis() - (store.refreshSec * 1000).toLong() + 500
                    }
                }
            }
            wv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) log("خطای JS صفحه: ${m.message().take(160)} (خط ${m.lineNumber()})", "WARN")
                    return true
                }
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

        val bar2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE)
            setPadding(Ui.dp(this@BotActivity, 6), 0, Ui.dp(this@BotActivity, 6), Ui.dp(this@BotActivity, 6)) }
        btnUa = Ui.button(this, "", Ui.navy, 12f) {
            store.desktop = !store.desktop; applyUa()
            log("حالت: ${if (store.desktop) "دسکتاپ" else "موبایل"} — بارگذاری مجدد"); startLoad()
        }
        btnNet = Ui.button(this, "", Ui.navy, 12f) {
            store.net = (store.net + 1) % 3; applyNet()
            h.postDelayed({ log("بارگذاری مجدد با شبکه ${Logger.netInfo(this)}"); startLoad() }, 1500)
        }
        bar2.addView(Ui.weight(btnUa)); bar2.addView(Ui.weight(btnNet))
        applyUa(); applyNet()
        root.addView(panel); root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(bar); root.addView(bar2)
        setContentView(root)

        if (profiles.isEmpty()) Toast.makeText(this, "هیچ نفری فعال نیست", Toast.LENGTH_LONG).show()
        state(Ui.warn, "در حال شروع...")
        startLoad()
        h.post(uiTick)
        h.post(ticker)
        h.postDelayed(summaryTick, 30000)
    }

    private val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private lateinit var btnUa: Button
    private lateinit var btnNet: Button
    private var netCb: ConnectivityManager.NetworkCallback? = null

    // UserAgent: موبایل (بدون wv، مثل کروم) یا دسکتاپ (مثل کامپیوتر)
    private fun applyUa() {
        val st = web.settings
        if (store.desktop) { st.userAgentString = DESKTOP_UA; st.useWideViewPort = true; st.loadWithOverviewMode = true }
        else {
            st.userAgentString = WebSettings.getDefaultUserAgent(this).replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
            st.useWideViewPort = true; st.loadWithOverviewMode = false
        }
        if (::btnUa.isInitialized) btnUa.text = if (store.desktop) "🖥 دسکتاپ: روشن" else "📱 موبایل"
    }

    // شبکه: همه ترافیک اپ را به Wi-Fi یا داده موبایل قفل می‌کند (0 = خودکار)
    private fun applyNet() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        netCb?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        netCb = null
        if (::btnNet.isInitialized) btnNet.text = when (store.net) { 1 -> "📶 فقط Wi-Fi"; 2 -> "📡 فقط موبایل"; else -> "🌐 شبکه: خودکار" }
        try {
            if (store.net == 0) { cm.bindProcessToNetwork(null); log("شبکه: خودکار (${Logger.netInfo(this)})"); return }
            val tr = if (store.net == 1) NetworkCapabilities.TRANSPORT_WIFI else NetworkCapabilities.TRANSPORT_CELLULAR
            val name = if (store.net == 1) "WIFI" else "CELLULAR"
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(n: Network) { cm.bindProcessToNetwork(n); log("اپ به $name قفل شد") }
                override fun onLost(n: Network) { log("شبکه $name قطع شد", "WARN") }
                override fun onUnavailable() { log("شبکه $name در دسترس نیست", "WARN") }
            }
            netCb = cb
            cm.requestNetwork(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addTransportType(tr).build(), cb)
        } catch (e: Exception) { log("تنظیم شبکه ناموفق: ${e.message}", "ERROR") }
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
            if (!armed) { armed = true; armedAt = now; if (web.progress >= 100) lastReload = 0; state(Ui.warn, "شروع رفرش و پایش دکمه") }
            // کلیک شد ولی فرم باز نشد؟
            if (clickedAt > 0 && now - clickedAt > 6000) {
                log("کلیک شد ولی بعد از ۶ ثانیه فرم باز نشد", "WARN"); clickedAt = 0
                web.evaluateJavascript(Js.CANDIDATES) { r -> log("وضعیت صفحه: ${r?.take(300)}", "WARN") }
            }
            // دکمه فعالی پیدا نمی‌شود؟ هر ۱۰ ثانیه یک بار علت را لاگ کن
            if (now - lastCandLog > 10000) {
                lastCandLog = now
                web.evaluateJavascript(Js.CANDIDATES) { r -> log("دکمه فعالی نیست. دکمه‌های صفحه: ${r?.take(300)}", "INFO") }
            }
            // بعد از کلیک تا ۸ ثانیه صبر کن تا ناوبری کامل شود (کلیک/رفرش دوباره، ناوبری را کنسل می‌کرد)
            if (now >= navPendingUntil && !clickInFlight && now - lastClick > 1200) {
                clickInFlight = true
                web.evaluateJavascript(Js.CLICK_BOOK) { r ->
                    clickInFlight = false
                    val txt = r?.trim('"') ?: ""
                    if (txt.isNotEmpty() && txt != "null") {
                        lastClick = System.currentTimeMillis(); clickedAt = lastClick; clicks++
                        navPendingUntil = lastClick + 8000
                        state(Ui.ok, "کلیک شد: $txt")
                    }
                }
            }
            val loading = web.progress < 100
            val limit = if (loading) 6000L else (store.refreshSec * 1000).toLong()
            if (now >= navPendingUntil && now - lastReload > limit) {
                if (loading) { web.stopLoading(); timeouts++; log("تایم‌اوت لود (۶ ثانیه) مسیر=${Uri.parse(web.url ?: "").path}", "WARN") }
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
        log("فرم باز شد ${if (armedAt > 0) (System.currentTimeMillis() - armedAt) / 1000 else 0}s بعد از شروع پایش، رفرش=$attempts")
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
        val pj = p.toJson()
        for (k in listOf("nationalId", "mobile", "km", "plate2", "plate3", "plateCity", "vin", "model", "birth"))
            pj.put(k, Options.en(pj.optString(k)))
        val data = JSONObject(pj.toString()).put("frontData", b64(p.front)).put("backData", b64(p.back)).put("focusCap", focusCap)
        fills++
        web.evaluateJavascript("(" + Js.FILL + ")(" + data + ")") { r ->
            try {
                val rep = JSONObject(JSONObject("{\"v\":$r}").getString("v"))
                val bad = rep.keys().asSequence().filter { k -> val v = rep.getString(k); v != "ok" && v != "focused" && v != "empty-profile" && v != "not-on-page" }
                    .map { k -> "$k=${rep.getString(k)}" }.toList()
                log("پرکردن(${if (focusCap) "۲" else "۱"}): ${if (bad.isEmpty()) "همه فیلدها ok" else "مشکل در " + bad.joinToString(", ")} | $rep",
                    if (bad.isEmpty()) "INFO" else "WARN")
                if (bad.isNotEmpty() && focusCap) { stateColor = Ui.warn; stateMsg = "بعضی فیلدها پر نشد: " + bad.take(3).joinToString("،") }
            } catch (e: Exception) { log("گزارش پرکردن خوانده نشد: ${e.message} raw=${r?.take(200)}", "ERROR") }
        }
        if (focusCap) {
            if (stateColor != Ui.warn) state(Ui.ok, "پر شد ✓ فقط محصول + کد امنیتی + ثبت")
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

    private fun showLog() = Logger.dialog(this)

    private fun inspect() = web.evaluateJavascript(Js.INSPECT) { r ->
        val t = r?.let { JSONObject("{\"v\":$it}").getString("v") } ?: ""
        log("بازرسی صفحه:\n$t")
        dialogText("فیلدها و دکمه‌های صفحه", t)
    }

    override fun onDestroy() {
        try { val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            netCb?.let { cm.unregisterNetworkCallback(it) }; cm.bindProcessToNetwork(null) } catch (_: Exception) {}
        logSummary(); log("پایان جلسه"); h.removeCallbacksAndMessages(null); super.onDestroy() }
}
