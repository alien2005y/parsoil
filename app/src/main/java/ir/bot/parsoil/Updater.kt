package ir.bot.parsoil

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    private fun get(u: String): HttpURLConnection =
        (URL(u).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 15000; readTimeout = 60000
            setRequestProperty("User-Agent", "ParsoilBot")
        }

    private fun newer(tag: String, cur: String): Boolean {
        val a = tag.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val b = cur.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun check(act: Activity, repo: String) {
        Thread {
            try {
                val c = get("https://api.github.com/repos/$repo/releases/latest")
                val j = JSONObject(c.inputStream.bufferedReader().readText())
                val tag = j.getString("tag_name")
                if (!newer(tag, BuildConfig.VERSION_NAME)) {
                    act.runOnUiThread { Toast.makeText(act, "آخرین نسخه نصب است (${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show() }
                    return@Thread
                }
                val assets = j.getJSONArray("assets")
                var apk = ""
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) apk = a.getString("browser_download_url")
                }
                act.runOnUiThread { Toast.makeText(act, "در حال دانلود $tag ...", Toast.LENGTH_SHORT).show() }
                val f = File(act.getExternalFilesDir(null), "update.apk")
                get(apk).inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                act.runOnUiThread { install(act, f) }
            } catch (e: Exception) {
                act.runOnUiThread { Toast.makeText(act, "خطا در به‌روزرسانی: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun install(act: Activity, f: File) {
        val uri = FileProvider.getUriForFile(act, "${act.packageName}.fp", f)
        act.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
