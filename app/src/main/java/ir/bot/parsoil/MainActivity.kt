package ir.bot.parsoil

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        val root = ScrollView(this).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 48) }
        root.addView(col)
        col.addView(TextView(this).apply { text = "ربات ثبت نام پارس‌اویل — v${BuildConfig.VERSION_NAME}"; textSize = 20f })
        col.addView(btn("▶ شروع") { startActivity(Intent(this, BotActivity::class.java)) })
        col.addView(btn("＋ افزودن نفر") { edit(Profile(), -1) })
        col.addView(btn("⚙ تنظیمات (آدرس، فاصله رفرش، ریپو)") { settings() })
        col.addView(btn("⟳ به‌روزرسانی برنامه") { Updater.check(this, store.repo) })
        col.addView(TextView(this).apply { text = "\nافراد به ترتیب اولویت (بالاترین اول):"; textSize = 16f })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)
        setContentView(root)
    }

    override fun onResume() { super.onResume(); render() }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

    private fun render() {
        list.removeAllViews()
        val ps = store.profiles
        ps.forEachIndexed { i, p ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val cb = CheckBox(this).apply { isChecked = p.enabled
                setOnCheckedChangeListener { _, v -> val l = store.profiles; l[i].enabled = v; store.profiles = l } }
            val tv = TextView(this).apply { text = "${i + 1}. ${p.title()}"; textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setOnClickListener { edit(p, i) } }
            row.addView(cb); row.addView(tv)
            row.addView(btn("▲") { move(i, -1) }); row.addView(btn("▼") { move(i, 1) })
            row.addView(btn("🗑") { val l = store.profiles; l.removeAt(i); store.profiles = l; render() })
            list.addView(row)
        }
    }

    private fun move(i: Int, d: Int) {
        val l = store.profiles; val j = i + d
        if (j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t; store.profiles = l; render()
    }

    private fun settings() {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 0) }
        val url = EditText(this).apply { setText(store.url); hint = "آدرس صفحه" }
        val sec = EditText(this).apply { setText(store.refreshSec.toString()); hint = "فاصله رفرش (ثانیه، مثلا 1.5)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val repo = EditText(this).apply { setText(store.repo); hint = "github user/repo" }
        listOf(url, sec, repo).forEach { v.addView(it) }
        AlertDialog.Builder(this).setTitle("تنظیمات").setView(v).setPositiveButton("ذخیره") { _, _ ->
            store.url = url.text.toString().trim()
            store.refreshSec = (sec.text.toString().toFloatOrNull() ?: 3f).coerceAtLeast(0.5f)
            store.repo = repo.text.toString().trim()
        }.show()
    }

    private fun edit(p: Profile, idx: Int) {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        fun et(h: String, s: String, num: Boolean = false) = EditText(this).apply {
            hint = h; setText(s); if (num) inputType = InputType.TYPE_CLASS_NUMBER; v.addView(this) }
        val nid = et("کد ملی", p.nationalId, true); val mob = et("موبایل (09...)", p.mobile, true)
        val fn = et("نام", p.first); val ln = et("نام خانوادگی", p.last)
        val bd = et("تاریخ تولد (مثلا 1370/05/20)", p.birth)
        val male = RadioButton(this).apply { text = "مرد"; isChecked = p.male }
        val female = RadioButton(this).apply { text = "زن"; isChecked = !p.male }
        v.addView(RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; addView(male); addView(female) })
        val vin = et("VIN", p.vin); val model = et("مدل خودرو (متن گزینه)", p.model)
        val km = et("کیلومتر فعلی", p.km, true); val ct = et("نوع خودرو (متن گزینه)", p.carType)
        val p2 = et("پلاک: دو رقم سمت چپ", p.plate2, true); val pl = et("پلاک: حرف (متن گزینه)", p.plateLetter)
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
