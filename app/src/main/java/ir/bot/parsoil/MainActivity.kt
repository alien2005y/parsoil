package ir.bot.parsoil

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var list: LinearLayout
    private var pickCb: ((String) -> Unit)? = null

    private fun dp(v: Int) = Ui.dp(this, v)
    private lateinit var summary: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        Logger.init(this)
        window.statusBarColor = Ui.navy
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setBackgroundColor(Ui.bg) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.navy); setPadding(dp(20), dp(34), dp(20), dp(18)) }
        header.addView(Ui.text(this, "ربات نوبت‌گیری پارس‌اویل", 20f, Color.WHITE, true))
        header.addView(Ui.text(this, "نسخه ${BuildConfig.VERSION_NAME}", 12f, Color.parseColor("#B8C4E0")))
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)) }
        scroll.addView(col)

        val start = Ui.card(this)
        summary = Ui.text(this, "", 13f, Ui.muted)
        start.addView(summary)
        start.addView(Ui.button(this, "▶   شروع نوبت‌گیری", Ui.ok, 18f) {
            if (store.profiles.none { it.enabled }) Toast.makeText(this, "اول یک نفر اضافه و فعال کن", Toast.LENGTH_SHORT).show()
            else startActivity(Intent(this, BotActivity::class.java))
        }.also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(8) } })
        col.addView(start)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Ui.weight(Ui.button(this, "＋ نفر جدید", Ui.navy, 13f) { edit(Profile(), -1) }))
        row.addView(Ui.weight(Ui.button(this, "⚙ تنظیمات", Ui.navy, 13f) { settings() }))
        row.addView(Ui.weight(Ui.button(this, "⟳ آپدیت", Ui.gold, 13f) { Updater.check(this, store.repo) }))
        col.addView(row)
        col.addView(Ui.button(this, "📋 گزارش و لاگ (برای بررسی با Claude)", Ui.muted, 13f) { Logger.dialog(this) }
            .also { it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) } })

        col.addView(Ui.text(this, "افراد به ترتیب اولویت", 15f, Ui.navy, true).apply { setPadding(dp(4), dp(18), 0, dp(4)) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)
        root.addView(header); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val ps = store.profiles
        summary.text = "ساعت شروع: ${store.startAt.ifEmpty { "همین الان" }}   •   رفرش هر ${store.refreshSec} ثانیه   •   ${ps.count { it.enabled }} نفر فعال"
        list.removeAllViews()
        if (ps.isEmpty()) list.addView(Ui.text(this, "هنوز کسی اضافه نشده.\nبا «＋ نفر جدید» مشخصات و عکس کارت ماشین را وارد کن.", 13f, Ui.muted).apply { setPadding(dp(4), dp(8), dp(4), 0) })
        ps.forEachIndexed { i, p ->
            val c = Ui.card(this)
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val cb = CheckBox(this).apply { isChecked = p.enabled
                setOnCheckedChangeListener { _, v -> val l = store.profiles; l[i].enabled = v; store.profiles = l; render() } }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setOnClickListener { edit(p, i) } }
            info.addView(Ui.text(this, "${i + 1}. ${p.title()}", 16f, if (p.enabled) Ui.navy else Ui.muted, true))
            val photos = if (p.front.isNotEmpty() && p.back.isNotEmpty()) "عکس‌ها ✓" else "⚠ عکس ناقص"
            info.addView(Ui.text(this, "کد ملی ${p.nationalId.ifEmpty { "-" }}  •  $photos", 12f, Ui.muted))
            top.addView(cb); top.addView(info)
            c.addView(top)
            val acts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
            acts.addView(Ui.weight(Ui.button(this, "▲", Ui.muted, 13f) { move(i, -1) }))
            acts.addView(Ui.weight(Ui.button(this, "▼", Ui.muted, 13f) { move(i, 1) }))
            acts.addView(Ui.weight(Ui.button(this, "ویرایش", Ui.navy, 13f) { edit(p, i) }, 1.5f))
            acts.addView(Ui.weight(Ui.button(this, "حذف", Ui.bad, 13f) {
                AlertDialog.Builder(this).setMessage("${p.title()} حذف شود؟").setPositiveButton("حذف") { _, _ ->
                    val l = store.profiles; l.removeAt(i); store.profiles = l; render() }.setNegativeButton("نه", null).show() }))
            c.addView(acts)
            list.addView(c)
        }
    }

    private fun move(i: Int, d: Int) {
        val l = store.profiles; val j = i + d
        if (j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t; store.profiles = l; render()
    }

    private fun settings() {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 0) }
        val url = EditText(this).apply { setText(store.url); hint = "آدرس صفحه‌ای که دکمه نوبت‌گیری دارد" }
        val sec = EditText(this).apply { setText(store.refreshSec.toString()); hint = "فاصله رفرش (ثانیه، مثلا 1.5)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val repo = EditText(this).apply { setText(store.repo); hint = "github user/repo" }
        val st = EditText(this).apply { setText(store.startAt); hint = "ساعت شروع HH:mm:ss (خالی = همین الان)" }
        listOf(url, st, sec, repo).forEach { v.addView(it) }
        AlertDialog.Builder(this).setTitle("تنظیمات").setView(v).setPositiveButton("ذخیره") { _, _ ->
            store.url = url.text.toString().trim()
            store.refreshSec = (sec.text.toString().toFloatOrNull() ?: 3f).coerceAtLeast(0.5f)
            store.repo = repo.text.toString().trim()
            store.startAt = st.text.toString().trim()
        }.show()
    }

    private fun edit(p: Profile, idx: Int) {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        fun et(h: String, s: String, num: Boolean = false) = EditText(this).apply {
            hint = h; setText(s); if (num) inputType = InputType.TYPE_CLASS_NUMBER; v.addView(this) }
        val nid = et("کد ملی", p.nationalId, true); val mob = et("موبایل (09...)", p.mobile, true)
        val fn = et("نام", p.first); val ln = et("نام خانوادگی", p.last)
        val bd = et("تاریخ تولد (فعلاً در فرم نیست، اختیاری)", p.birth)
        val male = RadioButton(this).apply { text = "مرد"; isChecked = p.male }
        val female = RadioButton(this).apply { text = "زن"; isChecked = !p.male }
        v.addView(RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; addView(male); addView(female) })
        val vin = et("VIN", p.vin); val model = et("مدل خودرو (سال شمسی، مثلا 1392)", p.model)
        val km = et("کیلومتر فعلی", p.km, true); val ct = et("نوع خودرو (مثلا پژو 405)", p.carType)
        val p2 = et("پلاک: دو رقم سمت چپ", p.plate2, true); val pl = et("پلاک: حرف (مثلا د یا الف)", p.plateLetter)
        val p3 = et("پلاک: سه رقم", p.plate3, true); val pc = et("پلاک: کد ایران (دو رقم)", p.plateCity, true)
        var front = p.front; var back = p.back
        val fb = Button(this).apply { text = "عکس روی کارت ماشین ${if (front.isNotEmpty()) "✓" else ""}" }
        val bb = Button(this).apply { text = "عکس پشت کارت ماشین ${if (back.isNotEmpty()) "✓" else ""}" }
        fb.setOnClickListener { pick { front = it; fb.text = "عکس روی کارت ✓" } }
        bb.setOnClickListener { pick { back = it; bb.text = "عکس پشت کارت ✓" } }
        v.addView(fb); v.addView(bb)
        AlertDialog.Builder(this).setTitle("مشخصات").setView(ScrollView(this).apply { addView(v) })
            .setPositiveButton("ذخیره") { _, _ ->
                val np = Profile(fa(nid), fa(mob), fn.text.toString().trim(), ln.text.toString().trim(),
                    fa(bd), male.isChecked, vin.text.toString().trim(), model.text.toString().trim(), fa(km),
                    ct.text.toString().trim(), fa(p2), pl.text.toString().trim(), fa(p3), fa(pc), front, back, p.enabled)
                val l = store.profiles
                if (idx >= 0) l[idx] = np else l.add(np)
                store.profiles = l; render()
            }.show()
    }

    // ارقام فارسی/عربی -> انگلیسی (سایت انگلیسی می‌خواهد)
    private fun fa(e: EditText) = e.text.toString().trim().map {
        when (it) { in '۰'..'۹' -> '0' + (it - '۰'); in '٠'..'٩' -> '0' + (it - '٠'); else -> it } }.joinToString("")

    private fun pick(cb: (String) -> Unit) {
        pickCb = cb
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 7)
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        if (rc != 7 || res != RESULT_OK || d?.data == null) return
        // کوچک‌کردن تا زیر ۵۰۰ کیلوبایت (محدودیت سایت)
        val bmp = contentResolver.openInputStream(d.data!!).use { BitmapFactory.decodeStream(it) } ?: return
        var scale = 1f; var q = 90
        val f = File(filesDir, "img_${System.currentTimeMillis()}.jpg")
        while (true) {
            val w = (bmp.width * scale).toInt(); val h = (bmp.height * scale).toInt()
            val s = Bitmap.createScaledBitmap(bmp, w, h, true)
            f.outputStream().use { s.compress(Bitmap.CompressFormat.JPEG, q, it) }
            if (f.length() < 480_000 || (q <= 50 && scale < 0.3f)) break
            if (q > 60) q -= 10 else scale *= 0.8f
        }
        pickCb?.invoke(f.absolutePath)
    }
}
