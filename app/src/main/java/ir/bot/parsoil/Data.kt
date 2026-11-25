package ir.bot.parsoil

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Profile(
    var nationalId: String = "", var mobile: String = "",
    var first: String = "", var last: String = "",
    var birth: String = "", var male: Boolean = true,
    var vin: String = "", var model: String = "", var km: String = "", var carType: String = "",
    var plate2: String = "", var plateLetter: String = "", var plate3: String = "", var plateCity: String = "",
    var front: String = "", var back: String = "", var enabled: Boolean = true
) {
    fun title() = "$first $last".trim().ifEmpty { nationalId.ifEmpty { "بدون نام" } }
    fun toJson() = JSONObject().apply {
        put("nationalId", nationalId); put("mobile", mobile); put("first", first); put("last", last)
        put("birth", birth); put("male", male); put("vin", vin); put("model", model); put("km", km)
        put("carType", carType); put("plate2", plate2); put("plateLetter", plateLetter)
        put("plate3", plate3); put("plateCity", plateCity); put("front", front); put("back", back)
        put("enabled", enabled)
    }
    companion object {
        fun from(o: JSONObject) = Profile(
            o.optString("nationalId"), o.optString("mobile"), o.optString("first"), o.optString("last"),
            o.optString("birth"), o.optBoolean("male", true), o.optString("vin"), o.optString("model"),
            o.optString("km"), o.optString("carType"), o.optString("plate2"), o.optString("plateLetter"),
            o.optString("plate3"), o.optString("plateCity"), o.optString("front"), o.optString("back"),
            o.optBoolean("enabled", true))
    }
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("bot", Context.MODE_PRIVATE)
    // لیست به ترتیب اولویت است: اولی بالاترین اولویت
    var profiles: MutableList<Profile>
        get() {
            val a = JSONArray(sp.getString("profiles", "[]"))
            return MutableList(a.length()) { Profile.from(a.getJSONObject(it)) }
        }
        set(v) { sp.edit().putString("profiles", JSONArray(v.map { it.toJson() }).toString()).apply() }
    var url: String
        get() = sp.getString("url", "https://roghan1.parsoilco.ir/DirectSaleAppointment")!!
        set(v) = sp.edit().putString("url", v).apply()
    var refreshSec: Float
        get() = sp.getFloat("refresh", 3f)
        set(v) = sp.edit().putFloat("refresh", v).apply()
    var startAt: String
        get() = sp.getString("startAt", "08:00:00")!!
        set(v) = sp.edit().putString("startAt", v).apply()
    var repo: String
        get() = sp.getString("repo", "alien2005y/parsoil")!!
        set(v) = sp.edit().putString("repo", v).apply()
}
