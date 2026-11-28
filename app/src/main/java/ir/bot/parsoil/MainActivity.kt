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
        col.addView(Ui.button(this, "🔍 تشخیص اتصال به سایت (DNS / IPv6 / SSL)", Ui.gold, 13f) { Diag.run(this) }
            .also { it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) } })
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
        val desk = CheckBox(this).apply { text = "حالت دسکتاپ (مثل مرورگر کامپیوتر)"; isChecked = store.desktop }
        val netSp = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("شبکه: خودکار", "فقط Wi-Fi", "فقط داده موبایل"))
            setSelection(store.net) }
        val smart = CheckBox(this).apply { text = "حالت هوشمند: بعد از ۲ خطا یا صفحه سفید، خودکار حالت (موبایل/دسکتاپ، Wi-Fi/موبایل) را عوض کن"; isChecked = store.smart }
        v.addView(desk); v.addView(netSp); v.addView(smart)
        AlertDialog.Builder(this).setTitle("تنظیمات").setView(v).setPositiveButton("ذخیره") { _, _ ->
            store.url = url.text.toString().trim()
            store.refreshSec = (sec.text.toString().toFloatOrNull() ?: 3f).coerceAtLeast(0.5f)
            store.repo = repo.text.toString().trim()
            store.startAt = st.text.toString().trim()
            store.desktop = desk.isChecked; store.net = netSp.selectedItemPosition; store.smart = smart.isChecked
        }.show()
    }

    private fun edit(p: Profile, idx: Int) {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        // ارقام فارسی/عربی همان لحظه تایپ به انگلیسی تبدیل می‌شود
        fun et(h: String, s: String, max: Int = 0, num: Boolean = false) = EditText(this).apply {
            hint = h; setText(s); if (num) inputType = InputType.TYPE_CLASS_NUMBER
            if (max > 0) filters = arrayOf(android.text.InputFilter.LengthFilter(max))
            addTextChangedListener(object : android.text.TextWatcher {
                var busy = false
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun afterTextChanged(e: android.text.Editable) {
                    if (busy) return
                    val t = e.toString(); val n = Options.en(t)
                    if (n != t) { busy = true; e.replace(0, e.length, n); busy = false }
                }
            })
            v.addView(this) }
        fun label(t: String) = v.addView(Ui.text(this, t, 12f, Ui.muted).apply { setPadding(0, dp(10), 0, 0) })
        fun spinner(items: List<Pair<String, String>>, cur: String): Spinner {
            val names = listOf("انتخاب کنید") + items.map { it.second }
            val sp = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names) }
            // ذخیره‌ی قدیمی ممکن است مقدار یا متن باشد
            val i = items.indexOfFirst { it.first == cur || it.second == cur }
            sp.setSelection(if (i >= 0) i + 1 else 0)
            v.addView(sp); return sp
        }
        val nid = et("کد ملی (۱۰ رقم)", p.nationalId, 10, true); val mob = et("موبایل (09...)", p.mobile, 11, true)
        val fn = et("نام", p.first); val ln = et("نام خانوادگی", p.last)
        val bd = et("تاریخ تولد (فعلاً در فرم نیست، اختیاری)", p.birth)
        label("جنسیت")
        // رادیوباتن‌ها باید id داشته باشند وگرنه گروه فقط اولی را می‌پذیرد
        val male = RadioButton(this).apply { id = View.generateViewId(); text = "مرد" }
        val female = RadioButton(this).apply { id = View.generateViewId(); text = "زن" }
        val grp = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; addView(male); addView(female) }
        grp.check(if (p.male) male.id else female.id)
        v.addView(grp)
        val vin = et("VIN (۱۷ کاراکتر)", p.vin, 17)
        vin.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        label("مدل خودرو (سال ساخت)"); val model = spinner(Options.models, p.model)
        val km = et("کیلومتر فعلی (اختیاری)", p.km, 7, true)
        label("نوع خودرو"); val ct = spinner(Options.carTypes, p.carType)
        label("شماره پلاک (به ترتیب چپ به راست روی سایت)")
        val p2 = et("دو رقم اول پلاک", p.plate2, 2, true)
        label("حرف پلاک"); val pl = spinner(Options.letters, p.plateLetter)
        val p3 = et("سه رقم پلاک", p.plate3, 3, true); val pc = et("کد ایران (دو رقم سمت راست)", p.plateCity, 2, true)
        var front = p.front; var back = p.back
        val fb = Button(this).apply { text = "عکس روی کارت ماشین ${if (front.isNotEmpty()) "✓" else ""}" }
        val bb = Button(this).apply { text = "عکس پشت کارت ماشین ${if (back.isNotEmpty()) "✓" else ""}" }
        fb.setOnClickListener { pick { front = it; fb.text = "عکس روی کارت ✓" } }
        bb.setOnClickListener { pick { back = it; bb.text = "عکس پشت کارت ✓" } }
        v.addView(fb); v.addView(bb)
        fun t(e: EditText) = Options.en(e.text.toString().trim())
        fun sel(s: Spinner, items: List<Pair<String, String>>) = if (s.selectedItemPosition > 0) items[s.selectedItemPosition - 1].first else ""
        AlertDialog.Builder(this).setTitle("مشخصات").setView(ScrollView(this).apply { addView(v) })
            .setPositiveButton("ذخیره") { _, _ ->
                val np = Profile(t(nid), t(mob), fn.text.toString().trim(), ln.text.toString().trim(),
                    t(bd), male.isChecked, t(vin).uppercase(), sel(model, Options.models), t(km),
                    sel(ct, Options.carTypes), t(p2), sel(pl, Options.letters), t(p3), t(pc), front, back, p.enabled)
                val l = store.profiles
                if (idx >= 0) l[idx] = np else l.add(np)
                store.profiles = l; render()
            }.setNegativeButton("لغو", null).show()
    }

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
