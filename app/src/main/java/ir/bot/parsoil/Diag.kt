package ir.bot.parsoil

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.webkit.WebSettings
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** تشخیص شبکه: DNS، اتصال TCP به هر آدرس (IPv4/IPv6)، گواهی SSL، و پاسخ سرور با UA موبایل و دسکتاپ */
object Diag {
    private const val HOST = "roghan1.parsoilco.ir"
    private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    fun run(act: Activity) {
        Toast.makeText(act, "در حال تشخیص (حدود ۳۰ ثانیه)...", Toast.LENGTH_SHORT).show()
        Thread {
            val out = StringBuilder()
            fun l(s: String) { out.appendLine(s); Logger.w("DIAG", s) }
            l("شبکه فعال: ${Logger.netInfo(act)}")
            val addrs = try { InetAddress.getAllByName(HOST).toList() }
                catch (e: Exception) { l("DNS ناموفق: ${e.javaClass.simpleName}"); emptyList() }
            l("DNS: ${addrs.joinToString(" , ") { it.hostAddress ?: "?" }.ifEmpty { "هیچ" }}")
            for (a in addrs) {
                val v = if (a is Inet6Address) "IPv6" else "IPv4"
                val t = System.currentTimeMillis()
                try { Socket().use { it.connect(InetSocketAddress(a, 443), 5000) }
                    l("TCP $v ${a.hostAddress}: متصل شد (${System.currentTimeMillis() - t}ms)")
                } catch (e: Exception) { l("TCP $v ${a.hostAddress}: خطا ${e.javaClass.simpleName} (${System.currentTimeMillis() - t}ms)") }
            }
            // فقط برای تشخیص: گواهی را رد نمی‌کنیم تا ببینیم سرور جواب می‌دهد یا نه
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
                override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val ssl = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
            val mobileUa = WebSettings.getDefaultUserAgent(act).replace("; wv", "")
            var certLogged = false
            for ((name, ua) in listOf("موبایل" to mobileUa, "دسکتاپ" to DESKTOP_UA)) {
                val t = System.currentTimeMillis()
                try {
                    val c = URL("https://$HOST/DirectSaleAppointment").openConnection() as HttpsURLConnection
                    c.sslSocketFactory = ssl.socketFactory; c.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                    c.connectTimeout = 6000; c.readTimeout = 8000; c.instanceFollowRedirects = false
                    c.setRequestProperty("User-Agent", ua)
                    val code = c.responseCode
                    l("HTTPS با UA $name: HTTP $code (${System.currentTimeMillis() - t}ms)")
                    if (!certLogged) {
                        certLogged = true
                        (c.serverCertificates.firstOrNull() as? X509Certificate)?.let {
                            val expired = it.notAfter.before(java.util.Date())
                            l("گواهی: صادرکننده=${it.issuerX500Principal.name.take(60)} ؛ انقضا=${it.notAfter} ${if (expired) "(منقضی شده!)" else ""}")
                        }
                    }
                    c.disconnect()
                } catch (e: Exception) { l("HTTPS با UA $name: خطا ${e.javaClass.simpleName} ${e.message?.take(60) ?: ""} (${System.currentTimeMillis() - t}ms)") }
            }
            act.runOnUiThread { show(act, out.toString()) }
        }.start()
    }

    private fun show(act: Activity, text: String) {
        val tv = TextView(act).apply {
            this.text = text; textSize = 12f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
            layoutDirection = View.LAYOUT_DIRECTION_LTR; val p = Ui.dp(act, 10); setPadding(p, p, p, p)
        }
        AlertDialog.Builder(act).setTitle("نتیجه تشخیص").setView(ScrollView(act).apply { addView(tv) })
            .setPositiveButton("باشه", null)
            .setNeutralButton("کپی") { _, _ ->
                (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("diag", text))
                Toast.makeText(act, "کپی شد", Toast.LENGTH_SHORT).show()
            }.show()
    }
}
