package de.autoknigge.enneagramm

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/** Enneagramm Lern-Heft: Lernposter, Typen-Übersicht, Karteikarten und Quiz. */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val URL_HOME = "https://autoknigge.de"
        private const val URL_APPS = "https://autoknigge.de/apps/"
        private const val URL_IMPRINT = "https://autoknigge.de/impressum"
        private const val URL_PRIVACY = "https://autoknigge.de/enneagramm/datenschutz.html"
        private val GOLD = Color.parseColor("#D4AF37")
        private val INK = Color.parseColor("#F1EEDC")
        private val DIM = Color.parseColor("#B9C4BC")
        private val PANEL = Color.parseColor("#17301F")
        private val LINE = Color.parseColor("#2C4A38")
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var adManager: AdManager
    private lateinit var host: FrameLayout
    private var webView: WebView? = null
    private var current: String = "home"
    private val back = ArrayList<() -> Unit>()
    private var dark = false

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------ Aufbau

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = getSharedPreferences("enneagramm", MODE_PRIVATE)
        dark = prefs.getBoolean("dark", false)
        Data.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_screen)
        }
        Ui.insets(root)
        host = FrameLayout(this)
        root.addView(host, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val adSettings = link("Werbe-Einstellungen", null)
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dp(4), 0, dp(4), 0) }
        footer.addView(link("Datenschutz", URL_PRIVACY))
        footer.addView(text("·", 12f, GOLD))
        footer.addView(link("Impressum", URL_IMPRINT))
        footer.addView(text("·", 12f, GOLD))
        footer.addView(adSettings)
        footer.addView(text("·", 12f, GOLD))
        footer.addView(link("Weitere Apps", URL_APPS))
        root.addView(footer, LinearLayout.LayoutParams(MATCH, WRAP))
        val adBox = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_ad)
            minimumHeight = dp(60)
        }
        root.addView(adBox, LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(root)
        adManager = AdManager(this, adBox, adSettings, false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webView
                if (wv != null && wv.canGoBack()) { wv.goBack(); return }
                if (back.isEmpty()) { isEnabled = false; onBackPressedDispatcher.onBackPressed() } else back.removeAt(back.size - 1)()
            }
        })
        showHome()
        showIntro { adManager.start() }
    }

    // ------------------------------------------------------------------ Bausteine

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun link(label: String, url: String?) = text(label, 12f, GOLD).apply {
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        setPadding(dp(6), dp(8), dp(6), dp(8))
        maxLines = 1
        if (url != null) setOnClickListener { Ui.openUrl(this@MainActivity, url) }
    }

    private fun card(bg: Int = PANEL, stroke: Int = LINE): GradientDrawable = GradientDrawable().apply {
        setColor(bg)
        setStroke(dp(1), stroke)
        cornerRadius = dp(14).toFloat()
    }

    private fun bigButton(icon: String, title: String, sub: String, onClick: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = card()
        setPadding(dp(14), dp(12), dp(14), dp(12))
        isClickable = true
        setOnClickListener { onClick() }
        addView(text(icon, 26f, INK), LinearLayout.LayoutParams(dp(46), WRAP))
        val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(title, 17f, INK, true))
        col.addView(text(sub, 13f, DIM))
        addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(text("›", 24f, GOLD, true))
    }

    private fun screen(title: String, backTo: (() -> Unit)?, content: (LinearLayout) -> Unit, scroll: Boolean = true) {
        webView?.let { it.stopLoading(); it.destroy() }
        webView = null
        host.removeAllViews()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(14), dp(4)) }
        if (backTo != null) {
            top.addView(text("‹", 32f, GOLD).apply {
                gravity = Gravity.CENTER
                contentDescription = "Zurück"
                setOnClickListener { backTo() }
            }, LinearLayout.LayoutParams(dp(46), dp(46)))
        } else top.setPadding(dp(16), dp(10), dp(14), dp(4))
        top.addView(text(title, 21f, GOLD, true).apply { maxLines = 1 })
        root.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(14)) }
        content(col)
        if (scroll) root.addView(ScrollView(this).apply { addView(col, ViewGroup.LayoutParams(MATCH, WRAP)) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        else root.addView(col, LinearLayout.LayoutParams(MATCH, 0, 1f))
        host.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun push(prev: () -> Unit) { back.add(prev) }
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    // ------------------------------------------------------------------ Start

    private fun showHome() {
        back.clear()
        screen("Lern-Heft", null, { c ->
            c.addView(android.widget.ImageView(this).apply {
                setImageResource(R.drawable.logo_enneagramm)
                adjustViewBounds = true
                contentDescription = "Enneagramm"
            }, LinearLayout.LayoutParams(MATCH, WRAP))
            c.addView(text("Die neun Muster auf einen Blick: lesen, nachschlagen und üben.", 14f, DIM).apply { gravity = Gravity.CENTER }, lp(8))
            c.addView(typeRow(), lp(14))
            c.addView(bigButton("📖", "Lern-Heft", "Die Lernposter, Kapitel für Kapitel") { push { showHome() }; showChapters() }, lp(16))
            c.addView(bigButton("🔎", "Die neun Typen", "Alles zu einem Typ auf einer Seite") { push { showHome() }; showTypes() }, lp(10))
            c.addView(bigButton("🃏", "Karteikarten", "Frage ansehen, Antwort aufdecken") { push { showHome() }; showCards() }, lp(10))
            c.addView(bigButton("🎯", "Quiz", "Zehn Fragen: Welcher Typ passt?") { push { showHome() }; showQuiz() }, lp(10))
            c.addView(bigButton("ℹ️", "Info", "Wie du das Heft nutzt, Hinweise") { push { showHome() }; showInfo() }, lp(10))
        })
    }

    /** Neun bunte Kreise als Kopf der Startseite. */
    private fun typeRow(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        for (t in 1..9) addView(circle(t, 32), LinearLayout.LayoutParams(dp(32), dp(32)).apply { setMargins(dp(3), 0, dp(3), 0) })
    }

    private fun circle(t: Int, size: Int): TextView = text("$t", (size * 0.42f), Color.parseColor("#161A22"), true).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Data.COLORS[t] ?: Color.GRAY)
            setStroke(dp(2), Color.parseColor("#161A22"))
        }
    }

    // ------------------------------------------------------------------ Lern-Heft

    private fun showChapters() {
        screen("Lern-Heft", { goBack() }, { c ->
            for ((name, sub, anchor) in Data.CHAPTERS) {
                c.addView(bigButton("📄", name, sub) { push { showChapters() }; showHeft(name, anchor) }, lp(10))
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showHeft(title: String, anchor: String) {
        screen(title, { goBack() }, { c ->
            val bar = LinearLayout(this).apply { gravity = Gravity.END }
            bar.addView(text(if (dark) "☀️ Hell" else "🌙 Dunkel", 13f, GOLD, true).apply {
                setPadding(dp(10), dp(4), dp(10), dp(4))
                setOnClickListener {
                    dark = !dark
                    prefs.edit().putBoolean("dark", dark).apply()
                    text = if (dark) "☀️ Hell" else "🌙 Dunkel"
                    applyTheme()
                }
            })
            c.addView(bar, lp(0))
            val wv = WebView(this)
            wv.settings.javaScriptEnabled = true
            wv.settings.setSupportZoom(true)
            wv.settings.builtInZoomControls = true
            wv.settings.displayZoomControls = false
            wv.settings.useWideViewPort = false
            wv.setBackgroundColor(Color.TRANSPARENT)
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) = applyTheme()
            }
            wv.loadUrl("file:///android_asset/heft.html#$anchor")
            webView = wv
            c.addView(wv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }, scroll = false)
    }

    private fun applyTheme() {
        webView?.evaluateJavascript("document.documentElement.setAttribute('data-theme','${if (dark) "dark" else "light"}')", null)
    }

    // ------------------------------------------------------------------ Typen

    private fun showTypes() {
        screen("Die neun Typen", { goBack() }, { c ->
            c.addView(text("Tippe auf einen Typ, um alles dazu zu sehen.", 14f, DIM), lp(0))
            var row: LinearLayout? = null
            for ((i, t) in Data.ORDER.withIndex()) {
                if (i % 3 == 0) { row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; c.addView(row, lp(10)) }
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(12), dp(4), dp(12))
                    background = card(blend(Data.COLORS[t] ?: Color.GRAY))
                    isClickable = true
                    setOnClickListener { push { showTypes() }; showType(t) }
                }
                tile.addView(circle(t, 40), LinearLayout.LayoutParams(dp(40), dp(40)))
                tile.addView(text(Data.names[t] ?: "", 13f, INK, true).apply { gravity = Gravity.CENTER; maxLines = 1 }, lp(6))
                row!!.addView(tile, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            }
            c.addView(text("Bauch: 8 · 9 · 1     Herz: 2 · 3 · 4     Kopf: 5 · 6 · 7", 12f, DIM).apply { gravity = Gravity.CENTER }, lp(12))
        })
    }

    private fun blend(c: Int): Int = Color.rgb(
        (Color.red(c) * 0.28 + 20).toInt(), (Color.green(c) * 0.28 + 30).toInt(), (Color.blue(c) * 0.28 + 24).toInt()
    )

    private fun showType(t: Int) {
        screen("Typ $t · ${Data.names[t]}", { goBack() }, { c ->
            var lastChap = ""
            for (p in Data.panels) {
                if (p.title == "Nomen est omen") continue
                val v = p.items[t] ?: continue
                if (p.chap != lastChap) {
                    lastChap = p.chap
                    c.addView(text(p.chap.uppercase(), 11f, DIM, true).apply { letterSpacing = 0.1f }, lp(if (c.childCount == 0) 0 else 16))
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = card(blend(Data.COLORS[t] ?: Color.GRAY))
                    setPadding(dp(12), dp(8), dp(12), dp(10))
                }
                row.addView(text(p.title, 12.5f, DIM, true))
                row.addView(text(v, 16f, INK))
                c.addView(row, lp(8))
            }
        })
    }

    // ------------------------------------------------------------------ Karteikarten

    private fun showCards() {
        val deck = Data.questions().shuffled()
        var i = 0
        var known = 0
        var flipped = false
        fun render() {
            screen("Karteikarten", { goBack() }, { c ->
                if (i >= deck.size) {
                    c.addView(text("Geschafft! 🎉", 24f, GOLD, true), lp(20))
                    c.addView(text("Du hast $known von ${deck.size} Karten gewusst.", 16f, INK), lp(10))
                    c.addView(bigButton("🔁", "Noch einmal", "Neu gemischt") { showCards() }, lp(20))
                    return@screen
                }
                val q = deck[i]
                c.addView(text("Karte ${i + 1} von ${deck.size}", 13f, DIM), lp(0))
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = card(blend(Data.COLORS[q.type] ?: Color.GRAY), Data.COLORS[q.type] ?: LINE)
                    setPadding(dp(18), dp(24), dp(18), dp(24))
                    minimumHeight = dp(220)
                    isClickable = true
                }
                card.addView(circle(q.type, 44), LinearLayout.LayoutParams(dp(44), dp(44)))
                card.addView(text(Data.names[q.type] ?: "", 20f, INK, true).apply { gravity = Gravity.CENTER }, lp(8))
                card.addView(text(q.panel.title, 15f, DIM).apply { gravity = Gravity.CENTER }, lp(10))
                if (flipped) card.addView(text(q.text, 22f, INK, true).apply { gravity = Gravity.CENTER }, lp(14))
                else card.addView(text("Tippe, um die Antwort zu sehen", 13f, GOLD).apply { gravity = Gravity.CENTER }, lp(14))
                card.setOnClickListener { flipped = true; render() }
                c.addView(card, lp(12))
                if (flipped) {
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    row.addView(bigButton("↻", "Nochmal", "") { flipped = false; i++; render() }.apply { (this as LinearLayout).removeViewAt(2) }, LinearLayout.LayoutParams(0, WRAP, 1f))
                    row.addView(bigButton("✓", "Gewusst", "") { known++; flipped = false; i++; render() }.apply { (this as LinearLayout).removeViewAt(2) }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(8) })
                    c.addView(row, lp(12))
                }
            })
        }
        render()
    }

    // ------------------------------------------------------------------ Quiz

    private fun showQuiz() {
        val qs = Data.questions().shuffled().take(10)
        var i = 0
        var score = 0
        fun render() {
            screen("Quiz", { goBack() }, { c ->
                if (i >= qs.size) {
                    c.addView(text("Ergebnis: $score von ${qs.size}", 24f, GOLD, true), lp(20))
                    c.addView(text(when { score >= 9 -> "Hervorragend! 🌟"; score >= 6 -> "Gut gemacht! 👍"; else -> "Weiter üben, es lohnt sich! 💪" }, 16f, INK), lp(10))
                    c.addView(bigButton("🔁", "Neues Quiz", "Zehn neue Fragen") { showQuiz() }, lp(20))
                    return@screen
                }
                val q = qs[i]
                c.addView(text("Frage ${i + 1} von ${qs.size}   ·   $score richtig", 13f, DIM), lp(0))
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = card()
                    setPadding(dp(16), dp(14), dp(16), dp(16))
                }
                box.addView(text(q.panel.title, 13f, DIM, true))
                box.addView(text("„${q.text}“", 20f, INK, true), lp(6))
                box.addView(text("Welcher Typ passt?", 14f, GOLD), lp(10))
                c.addView(box, lp(10))
                val options = (Data.ORDER.filter { it != q.type }.shuffled().take(3) + q.type).shuffled()
                var answered = false
                val btns = ArrayList<Pair<Int, TextView>>()
                for (t in options) {
                    val b = text(Data.label(t), 17f, INK, true).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        background = card()
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        isClickable = true
                    }
                    btns.add(t to b)
                    c.addView(b, lp(8))
                }
                val next = text("", 16f, GOLD, true).apply { gravity = Gravity.CENTER; visibility = View.GONE; setPadding(0, dp(12), 0, dp(12)) }
                for ((t, b) in btns) b.setOnClickListener {
                    if (answered) return@setOnClickListener
                    answered = true
                    val ok = t == q.type
                    if (ok) score++
                    b.background = card(Color.parseColor(if (ok) "#1E5A3A" else "#6B2A2A"), Color.parseColor(if (ok) "#46C27E" else "#E57373"))
                    if (!ok) btns.first { it.first == q.type }.second.background = card(Color.parseColor("#1E5A3A"), Color.parseColor("#46C27E"))
                    next.text = if (i + 1 >= qs.size) "Ergebnis ansehen ›" else "Nächste Frage ›"
                    next.visibility = View.VISIBLE
                    next.setOnClickListener { i++; render() }
                }
                c.addView(next, lp(10))
            })
        }
        render()
    }

    // ------------------------------------------------------------------ Info

    private fun showInfo() {
        screen("Info", { goBack() }, { c ->
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = card(); setPadding(dp(16), dp(14), dp(16), dp(16)) }
            box.addView(text("Zur Nutzung", 17f, GOLD, true))
            box.addView(text("Das Enneagramm ist ein Modell zur Selbstreflexion und Orientierung. Es ist kein Diagnoseverfahren und ersetzt weder ärztlichen noch psychologischen oder therapeutischen Rat. Die Typen sind Beschreibungen von Mustern, keine Schubladen: Niemand besteht nur aus einem Typ.", 14.5f, INK), lp(8))
            box.addView(text("So nutzt du die App", 17f, GOLD, true), lp(16))
            box.addView(text("• Im Lern-Heft liest du die Poster Kapitel für Kapitel. Mit zwei Fingern zoomst du.\n• Bei „Die neun Typen“ siehst du alles, was das Heft zu einem Typ sagt.\n• Mit Karteikarten und Quiz übst du, die Muster den Typen zuzuordnen.\n• Die Tipps im Heft: Die eigenen Muster sind einem oft nicht bewusst. Frage Partner, Kinder oder gute Freunde nach ihrer Einschätzung.", 14.5f, INK), lp(8))
            box.addView(text("Über die App", 17f, GOLD, true), lp(16))
            box.addView(text("Inhalte: ©MK · Lern-Heft Enneagramm. Die App ist kostenlos und zeigt am unteren Rand Werbung. Sie läuft ohne Internet, deine Eingaben und Ergebnisse werden nicht gespeichert.", 14.5f, INK), lp(8))
            c.addView(box, lp(4))
        })
    }

    private fun goBack() {
        if (back.isEmpty()) showHome() else back.removeAt(back.size - 1)()
    }

    // ------------------------------------------------------------------ Werbehinweis

    private fun showIntro(then: () -> Unit) {
        if (prefs.getBoolean("intro_seen", false)) { then(); return }
        AlertDialog.Builder(this)
            .setTitle("Kostenlos dank Werbung")
            .setMessage(
                "Diese App ist kostenlos und finanziert sich durch Werbung: ein Banner am unteren Rand.\n\n" +
                    "Im nächsten Schritt kannst du der Nutzung deiner Daten für Werbezwecke zustimmen und damit die kostenlose App unterstützen. " +
                    "Deine Auswahl kannst du jederzeit unter „Werbe-Einstellungen“ ändern."
            )
            .setCancelable(false)
            .setPositiveButton("Weiter") { _, _ ->
                prefs.edit().putBoolean("intro_seen", true).apply()
                then()
            }
            .show()
    }

    override fun onPause() {
        webView?.onPause()
        if (::adManager.isInitialized) adManager.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        if (::adManager.isInitialized) adManager.resume()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        if (::adManager.isInitialized) adManager.destroy()
        super.onDestroy()
    }
}
