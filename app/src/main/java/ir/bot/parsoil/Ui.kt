package ir.bot.parsoil

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    val navy = Color.parseColor("#233F78")
    val gold = Color.parseColor("#C8A24B")
    val bg = Color.parseColor("#F4F6FB")
    val ok = Color.parseColor("#1E9E5A")
    val bad = Color.parseColor("#C0392B")
    val warn = Color.parseColor("#E0A526")
    val muted = Color.parseColor("#6B7690")

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun shape(c: Context, color: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(c, radius).toFloat()
        if (stroke != 0) setStroke(dp(c, 1), stroke)
    }

    fun text(c: Context, t: String, size: Float = 14f, color: Int = Color.parseColor("#1E2A4A"), bold: Boolean = false) =
        TextView(c).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    fun button(c: Context, t: String, color: Int = navy, size: Float = 15f, onClick: () -> Unit) = Button(c).apply {
        text = t; textSize = size; setTextColor(Color.WHITE); isAllCaps = false
        background = shape(c, color, 12)
        minHeight = dp(c, 48); minimumHeight = dp(c, 48)
        setPadding(dp(c, 8), 0, dp(c, 8), 0)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    fun card(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(c, Color.WHITE, 14, Color.parseColor("#E3E7F1"))
        setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(c, 6), 0, dp(c, 6)) }
    }

    fun weight(v: View, w: Float = 1f, margin: Int = 4): View {
        v.layoutParams = LinearLayout.LayoutParams(0, -2, w).apply { setMargins(dp(v.context, margin), 0, dp(v.context, margin), 0) }
        return v
    }

    fun dot(c: Context, color: Int) = View(c).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(dp(c, 12), dp(c, 12)).apply { setMargins(dp(c, 8), 0, dp(c, 8), 0); gravity = Gravity.CENTER_VERTICAL }
    }
}
