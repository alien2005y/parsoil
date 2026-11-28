package ir.bot.parsoil

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** لاگ دائمی روی فایل؛ بدون اطلاعات شخصی (کد ملی، نام، شماره). برای ارسال و بررسی بعدی. */
object Logger {
    private var file: File? = null
    private var hooked = false
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(ctx: Context) {
        if (file == null) {
            val f = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "bot-log.txt")
            file = f
            try { if (f.exists() && f.length() > 400_000) f.writeText(f.readText().takeLast(200_000)) } catch (_: Exception) {}
        }
        if (!hooked) {
            hooked = true
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                w("ERROR", "CRASH: " + Log.getStackTraceString(e))
                prev?.uncaughtException(t, e)
            }
        }
    }

    @Synchronized
    fun w(level: String, msg: String) {
        try { file?.appendText("${fmt.format(Date())} [$level] $msg\n") } catch (_: Exception) {}
    }

    fun text(): String = try { file?.readText() ?: "" } catch (_: Exception) { "" }
    fun clear() { try { file?.writeText("") } catch (_: Exception) {} }

    /** نوع شبکه فعال: WIFI / CELLULAR / VPN */
    fun netInfo(ctx: Context): String = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork
        val c = if (n != null) cm.getNetworkCapabilities(n) else null
        if (c == null) "بدون-شبکه" else listOfNotNull(
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) "WIFI" else null,
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) "CELLULAR" else null,
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) "VPN" else null,
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) "ETH" else null
        ).joinToString("+").ifEmpty { "?" }
    } catch (e: Exception) { "?" }

    fun sessionHeader(ctx: Context, s: Store) {
        val ps = s.profiles
        w("INFO", "===== جلسه جدید =====")
        w("INFO", "app=${BuildConfig.VERSION_NAME} device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.SDK_INT}")
        w("INFO", "network=${netInfo(ctx)} netMode=${s.net} ua=${if (s.desktop) "desktop" else "mobile"}")
        w("INFO", "config url=${s.url} refresh=${s.refreshSec}s start=${s.startAt.ifEmpty { "-" }} profiles=${ps.size} enabled=${ps.count { it.enabled }} withPhotos=${ps.count { it.front.isNotEmpty() && it.back.isNotEmpty() }}")
    }

    fun dialog(act: Activity) {
        val t = text()
        val tv = TextView(act).apply {
            text = t.takeLast(15000).ifEmpty { "لاگ خالی است" }
            textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            val p = Ui.dp(act, 10); setPadding(p, p, p, p)
        }
        val sv = ScrollView(act).apply { addView(tv); post { fullScroll(View.FOCUS_DOWN) } }
        AlertDialog.Builder(act).setTitle("گزارش و لاگ").setView(sv)
            .setPositiveButton("ارسال فایل") { _, _ -> share(act) }
            .setNeutralButton("کپی") { _, _ ->
                (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("log", t.takeLast(100_000)))
                Toast.makeText(act, "کپی شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("پاک کردن") { _, _ -> clear(); Toast.makeText(act, "لاگ پاک شد", Toast.LENGTH_SHORT).show() }
            .show()
    }

    private fun share(act: Activity) {
        val f = file ?: return
        try {
            val uri = FileProvider.getUriForFile(act, "${act.packageName}.fp", f)
            act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "ارسال لاگ"))
        } catch (e: Exception) { Toast.makeText(act, "ارسال نشد: ${e.message}", Toast.LENGTH_LONG).show() }
    }
}
