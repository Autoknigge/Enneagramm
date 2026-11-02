package de.autoknigge.enneagramm

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.viewpager2.widget.ViewPager2
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Die Matrix des Lebendigen – Das Enneagramm: das Buch von Marc Krekler als Leseapp. */
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

        /** Inhaltsverzeichnis des Buches: Ebene (0 = Teil, 1 = Abschnitt), Titel, Seite (gleich der PDF-Seite). */
        private val TOC = listOf(
            Triple(0, "Titel", 1), Triple(0, "Inhalt", 2),
            Triple(0, "Einleitung", 3),
            Triple(1, "Einstieg: Warum das Enneagramm? · Neun Vorteile, Typenüberblick", 3),
            Triple(1, "Männliche und weibliche Prinzipien, Blick hinter die Kulissen", 8),
            Triple(0, "Hauptteil · 1 Hinführung", 10),
            Triple(1, "Die drei Zentren, Fünf Fragen im Moment", 10),
            Triple(0, "2 Grundlagen", 14),
            Triple(1, "Flügel und energetische Ausstrahlung", 14),
            Triple(1, "Untertypen, Tierentsprechungen und Kontratypen", 25),
            Triple(1, "Ego-Sichtweise", 40),
            Triple(0, "3 Die Typen unterscheiden", 43),
            Triple(1, "Mangel, Verlangen, Strategie, Empfindsamkeit", 43),
            Triple(1, "Kindheit, Kernkompetenzen, Herausforderungen", 59),
            Triple(1, "Merkmale, Stress, Heilung und Berater", 65),
            Triple(1, "Typ erkennen: Innen- und Außenansicht, Prüffragen", 79),
            Triple(1, "Vertiefung: Triaden und Dreiecke", 85),
            Triple(0, "4 Blickqualität", 95),
            Triple(0, "5 Nutzen im Leben", 109),
            Triple(1, "Familie und Partnerschaft", 109),
            Triple(1, "Berufe und Tätigkeiten", 114),
            Triple(0, "6 Fallen und Entwicklung", 122),
            Triple(1, "Ego-Fixierung und Bewusstheit", 122),
            Triple(1, "Falle des Kenners, blinde Flecken, Fallen der Erfahrenen", 133),
            Triple(1, "Leistungsfallen und Methode im Alltag", 156),
            Triple(0, "Schluss und Ausblick", 164),
            Triple(1, "Der entscheidende Augenblick und Entwicklung", 164)
        )
    }

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var adManager: AdManager
    private lateinit var host: FrameLayout
    private val io = Executors.newSingleThreadExecutor()
    private var bookFile: File? = null
    private var adapter: PageAdapter? = null
    private var pager: ViewPager2? = null
    private var pageCount = 166
    private val back = ArrayList<() -> Unit>()
    private var texts: List<String>? = null
    private var inReader = false
    private var pagesSinceAd = 0

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------ Aufbau

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = getSharedPreferences("enneagramm", MODE_PRIVATE)

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
        adManager = AdManager(this, adBox, adSettings, true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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

    private fun card(bg: Int = PANEL, stroke: Int = LINE) = GradientDrawable().apply {
        setColor(bg)
        setStroke(dp(1), stroke)
        cornerRadius = dp(14).toFloat()
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

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
        if (sub.isNotEmpty()) col.addView(text(sub, 13f, DIM))
        addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(text("›", 24f, GOLD, true))
    }

    private fun leaveReader() {
        val p = pager
        if (p != null) prefs.edit().putInt("page", p.currentItem).apply()
        adapter?.shutdown()
        adapter = null
        pager = null
        inReader = false
    }

    private fun screen(title: String, backTo: (() -> Unit)?, content: (LinearLayout) -> Unit, scroll: Boolean = true): LinearLayout {
        leaveReader()
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
        top.addView(text(title, 20f, GOLD, true).apply { maxLines = 1 })
        root.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(14)) }
        content(col)
        if (scroll) root.addView(ScrollView(this).apply { addView(col, ViewGroup.LayoutParams(MATCH, WRAP)) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        else root.addView(col, LinearLayout.LayoutParams(MATCH, 0, 1f))
        host.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
        return root
    }

    private fun push(prev: () -> Unit) { back.add(prev) }
    private fun goBack() { if (back.isEmpty()) showHome() else back.removeAt(back.size - 1)() }

    // ------------------------------------------------------------------ Buchdatei

    /** Kopiert das Buch aus der App in den Speicher, damit der PDF-Betrachter es lesen kann (einmalig). */
    private fun withBook(then: (File) -> Unit) {
        bookFile?.let { then(it); return }
        val f = File(filesDir, "buch_v1.pdf")
        if (f.exists() && f.length() > 1_000_000) { bookFile = f; then(f); return }
        val dlg = AlertDialog.Builder(this).setMessage("Das Buch wird vorbereitet …").setCancelable(false).show()
        io.execute {
            val ok = runCatching {
                val tmp = File(filesDir, "buch_v1.tmp")
                assets.open("buch.pdf").use { i -> tmp.outputStream().use { o -> i.copyTo(o, 128 * 1024) } }
                tmp.renameTo(f)
            }.getOrDefault(false)
            runOnUiThread {
                dlg.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) { bookFile = f; then(f) } else android.widget.Toast.makeText(this, "Das Buch konnte nicht geladen werden (zu wenig Speicher?).", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // ------------------------------------------------------------------ Start

    private fun showHome() {
        back.clear()
        screen("Die Matrix des Lebendigen", null, { c ->
            c.addView(android.widget.ImageView(this).apply {
                setImageResource(R.drawable.logo_enneagramm)
                adjustViewBounds = true
                contentDescription = "Enneagramm"
            }, LinearLayout.LayoutParams(MATCH, WRAP))
            c.addView(text("Das Enneagramm · von Marc Krekler", 14f, DIM).apply { gravity = Gravity.CENTER }, lp(8))
            val last = prefs.getInt("page", 0)
            if (last > 0) c.addView(bigButton("📖", "Weiterlesen", "Seite ${last + 1} von $pageCount") { push { showHome() }; openReader(last) }, lp(16))
            else c.addView(bigButton("📖", "Buch lesen", "Von Anfang an") { push { showHome() }; openReader(0) }, lp(16))
            c.addView(bigButton("📑", "Inhalt", "Kapitel und Abschnitte") { push { showHome() }; showToc() }, lp(10))
            c.addView(bigButton("🔎", "Suche", "Begriffe im ganzen Buch finden") { push { showHome() }; showSearch() }, lp(10))
            c.addView(bigButton("🧭", "Typ-Finder", "Geführte Fragen zu Typ, Untertyp und Flügel") { push { showHome() }; showTypFinderIntro() }, lp(10))
            c.addView(bigButton("🔖", "Lesezeichen", "${bookmarks().size} gesetzt") { push { showHome() }; showBookmarks() }, lp(10))
            c.addView(bigButton("ℹ️", "Info", "Hinweise zum Buch und zur App") { push { showHome() }; showInfo() }, lp(10))
        })
    }

    // ------------------------------------------------------------------ Lesen

    private fun openReader(page: Int) {
        withBook { file ->
            leaveReader()
            host.removeAllViews()
            inReader = true
            val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(2), dp(8), dp(2)) }
            top.addView(text("‹", 32f, GOLD).apply {
                gravity = Gravity.CENTER
                contentDescription = "Zurück"
                setOnClickListener { goBack() }
            }, LinearLayout.LayoutParams(dp(42), dp(44)))
            val title = text("", 15f, INK, true).apply { maxLines = 1; gravity = Gravity.CENTER }
            title.setOnClickListener { gotoDialog() }
            top.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
            val star = text("☆", 26f, GOLD).apply { gravity = Gravity.CENTER; contentDescription = "Lesezeichen" }
            val menu = text("⋮", 26f, GOLD).apply { gravity = Gravity.CENTER; contentDescription = "Menü" }
            top.addView(star, LinearLayout.LayoutParams(dp(42), dp(44)))
            top.addView(menu, LinearLayout.LayoutParams(dp(42), dp(44)))
            root.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))
            val pg = ViewPager2(this)
            val ad = PageAdapter(file, (resources.displayMetrics.widthPixels * 1.7f).toInt())
            pg.adapter = ad
            pg.offscreenPageLimit = 1
            pageCount = ad.itemCount
            pg.setBackgroundColor(Color.parseColor("#202824"))
            pg.setCurrentItem(page.coerceIn(0, pageCount - 1), false)
            fun refresh() {
                val cur = pg.currentItem
                title.text = "Seite ${cur + 1} von $pageCount"
                star.text = if (bookmarks().contains(cur)) "★" else "☆"
            }
            pg.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    prefs.edit().putInt("page", position).apply()
                    refresh()
                    // Vollbild-Anzeige nach jeweils 20 geblätterten Seiten, auch mitten in einem Teil
                    pagesSinceAd++
                    if (pagesSinceAd >= 20) {
                        if (adManager.showInterstitialIfDue()) pagesSinceAd = 0
                    }
                }
            })
            star.setOnClickListener { toggleBookmark(pg.currentItem); refresh() }
            menu.setOnClickListener { readerMenu(pg) }
            root.addView(pg, LinearLayout.LayoutParams(MATCH, 0, 1f))
            host.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
            adapter = ad
            pager = pg
            refresh()
        }
    }

    private fun readerMenu(pg: ViewPager2) {
        val items = arrayOf("📑  Inhalt", "🔎  Suche", "🔖  Lesezeichen", "↗️  Gehe zu Seite …")
        AlertDialog.Builder(this).setItems(items) { _, i ->
            val here = pg.currentItem
            when (i) {
                0 -> { push { openReader(here) }; showToc() }
                1 -> { push { openReader(here) }; showSearch() }
                2 -> { push { openReader(here) }; showBookmarks() }
                3 -> gotoDialog()
            }
        }.show()
    }

    private fun gotoDialog() {
        val p = pager ?: return
        val et = EditText(this).apply {
            hint = "Seite (1 bis $pageCount)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val box = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(et) }
        AlertDialog.Builder(this).setTitle("Gehe zu Seite").setView(box)
            .setPositiveButton("Öffnen") { _, _ -> et.text.toString().toIntOrNull()?.let { if (it in 1..pageCount) p.setCurrentItem(it - 1, false) } }
            .setNegativeButton("Abbrechen", null).show()
    }

    // ------------------------------------------------------------------ Inhalt, Suche, Lesezeichen

    private fun showToc() {
        screen("Inhalt", { goBack() }, { c ->
            for ((lvl, title, page) in TOC) {
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = if (lvl == 0) card() else null
                    setPadding(dp(if (lvl == 0) 12 else 24), dp(10), dp(12), dp(10))
                    isClickable = true
                    setOnClickListener { push { showToc() }; openReader(page - 1) }
                }
                row.addView(text(title, if (lvl == 0) 16f else 14.5f, if (lvl == 0) GOLD else INK, lvl == 0), LinearLayout.LayoutParams(0, WRAP, 1f))
                row.addView(text("$page", 14f, DIM))
                c.addView(row, lp(if (lvl == 0) 10 else 2))
            }
        })
    }

    /** Text je Seite (Originalschreibweise) und dazu eine "gefaltete" Fassung für die Suche. */
    private var folded: List<String>? = null

    /** Kleinbuchstaben ohne Umlaute und Weichtrennungen; gleiche Länge wie das Original, damit Textausschnitte passen. */
    private fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (val c = ch.lowercaseChar()) {
                'ä' -> sb.append('a'); 'ö' -> sb.append('o'); 'ü' -> sb.append('u'); 'ß' -> sb.append('s')
                'é', 'è', 'ê' -> sb.append('e'); 'á', 'à', 'â' -> sb.append('a'); 'ó', 'ô' -> sb.append('o'); 'ú', 'û' -> sb.append('u')
                '\u00AD' -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Schreibweisen des Suchworts: mit Umlauten, mit ae/oe/ue und mit ss für ß. */
    private fun variants(word: String): List<String> {
        val base = fold(word)
        val v = linkedSetOf(base)
        v.add(base.replace("ae", "a").replace("oe", "o").replace("ue", "u"))
        v.add(base.replace("ss", "s"))
        v.add(base.replace("ae", "a").replace("oe", "o").replace("ue", "u").replace("ss", "s"))
        return v.toList()
    }

    private fun loadTexts(then: (List<String>) -> Unit) {
        texts?.let { then(it); return }
        io.execute {
            val list = runCatching {
                val a = JSONObject(assets.open("text.json").bufferedReader().use { it.readText() }).getJSONArray("pages")
                (0 until a.length()).map { a.getString(it).replace('\u00AD', ' ') }
            }.getOrDefault(emptyList())
            val f = list.map { fold(it) }
            runOnUiThread { texts = list; folded = f; if (!isFinishing && !isDestroyed) then(list) }
        }
    }

    private fun showSearch(initial: String = "") {
        screen("Suche", { goBack() }, { c ->
            val et = EditText(this).apply {
                hint = "Begriff suchen, zum Beispiel „Löwe“"
                setText(initial)
                setTextColor(INK)
                setHintTextColor(DIM)
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                backgroundTintList = android.content.res.ColorStateList.valueOf(GOLD)
            }
            c.addView(et, lp(0))
            val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(results, lp(8))
            fun run(q: String) {
                results.removeAllViews()
                val words = q.trim().split(Regex("\\s+")).filter { it.length >= 2 }
                if (words.isEmpty()) return
                val wordVariants = words.map { variants(it) }
                loadTexts { list ->
                    val fl = folded ?: return@loadTexts
                    val hits = ArrayList<Triple<Int, String, Int>>()
                    for ((i, low) in fl.withIndex()) {
                        // alle Wörter müssen auf der Seite vorkommen (in einer der Schreibweisen)
                        var first = -1
                        var count = 0
                        var ok = true
                        for ((wi, vs) in wordVariants.withIndex()) {
                            var best = -1
                            for (v in vs) {
                                val at = low.indexOf(v)
                                if (at >= 0 && (best < 0 || at < best)) best = at
                            }
                            if (best < 0) { ok = false; break }
                            if (wi == 0) {
                                first = best
                                for (v in vs) { var k = low.indexOf(v); while (k >= 0) { count++; k = low.indexOf(v, k + v.length) } }
                            }
                        }
                        if (!ok) continue
                        val t = list[i]
                        val from = (first - 40).coerceAtLeast(0)
                        val to = (first + words[0].length + 70).coerceAtMost(t.length)
                        hits.add(Triple(i, (if (from > 0) "… " else "") + t.substring(from, to) + (if (to < t.length) " …" else ""), count))
                    }
                    results.removeAllViews()
                    if (hits.isEmpty()) { results.addView(text("Keine Treffer.", 14f, DIM)); return@loadTexts }
                    results.addView(text("${hits.size} ${if (hits.size == 1) "Seite" else "Seiten"} mit „${q.trim()}“", 13f, DIM))
                    for ((page, snippet, n) in hits.take(150)) {
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            background = card()
                            setPadding(dp(12), dp(8), dp(12), dp(10))
                            isClickable = true
                            setOnClickListener { push { showSearch(q) }; openReader(page) }
                        }
                        row.addView(text("Seite ${page + 1}" + if (n > 1) "  ·  $n Treffer" else "", 13f, GOLD, true))
                        row.addView(text(snippet, 14f, INK))
                        results.addView(row, lp(8))
                    }
                }
            }
            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                override fun afterTextChanged(s: Editable?) { run(s?.toString() ?: "") }
            })
            if (initial.isNotEmpty()) run(initial)
            c.addView(text("Die Suche findet auch Wörter in den Grafiken und Bildseiten des Buches. Umlaute sind egal: „Löwe“, „Lowe“ und „Loewe“ führen zum selben Ergebnis.", 12f, DIM), lp(14))
        })
    }

    private fun bookmarks(): List<Int> = try {
        val a = JSONArray(prefs.getString("marks", "[]"))
        (0 until a.length()).map { a.getInt(it) }.sorted()
    } catch (e: Exception) { emptyList() }

    private fun toggleBookmark(page: Int) {
        val m = bookmarks().toMutableList()
        if (!m.remove(page)) m.add(page)
        prefs.edit().putString("marks", JSONArray(m.sorted()).toString()).apply()
    }

    private fun showBookmarks() {
        screen("Lesezeichen", { goBack() }, { c ->
            val m = bookmarks()
            if (m.isEmpty()) c.addView(text("Noch keine Lesezeichen. Tippe beim Lesen oben rechts auf ☆, um eine Seite zu merken.", 14.5f, DIM), lp(4))
            for (p in m) {
                val title = TOC.lastOrNull { it.third <= p + 1 }?.second ?: ""
                c.addView(bigButton("🔖", "Seite ${p + 1}", title) { push { showBookmarks() }; openReader(p) }, lp(8))
            }
        })
    }

    // ------------------------------------------------------------------ Info


    // ------------------------------------------------------------------ Typ-Finder

    private var tf: TypFinder? = null
    private val tfAnswers = LinkedHashMap<String, Int>()

    private fun finder(): TypFinder {
        tf?.let { return it }
        val f = TypFinder(assets.open("typfinder.json").bufferedReader().use { it.readText() })
        tf = f
        return f
    }

    private fun tfLoad() {
        tfAnswers.clear()
        for (part in (prefs.getString("tf_ans", "") ?: "").split(";")) {
            val kv = part.split(":")
            if (kv.size == 2) kv[1].toIntOrNull()?.let { tfAnswers[kv[0]] = it }
        }
    }

    private fun tfSave() {
        prefs.edit().putString("tf_ans", tfAnswers.entries.joinToString(";") { "${it.key}:${it.value}" }).apply()
    }

    private fun showTypFinderIntro() {
        tfLoad()
        back.clear(); push { showHome() }
        screen("Typ-Finder", { showHome() }, { c ->
            c.addView(text(
                "Ein geführtes Interview zur Selbsterkenntnis. In jeder Runde kommen zwei bis drei Fragen aus dem Alltag und aus deinem Leben. " +
                    "Am Ende bekommst du eine Einschätzung zu Instinkt, Typ, Untertyp und Flügel, mit Begründung, Gegenargumenten und Sicherheit.",
                15f, INK), lp(4))
            c.addView(text("• Es dauert etwa 10 bis 15 Minuten.\n• Antworte nach dem Warum, nicht nach dem, was du gern wärst.\n• Ist nichts passend, wähle „Keine passt wirklich“.\n• Deine Antworten bleiben auf deinem Gerät.", 14f, DIM), lp(12))
            val n = tfAnswers.size
            if (n > 0) c.addView(bigButton("▶️", "Fortsetzen", "$n Antworten gespeichert") { showTypFinder() }, lp(16))
            c.addView(bigButton("🧭", if (n > 0) "Neu beginnen" else "Starten", "") {
                tfAnswers.clear(); tfSave(); showTypFinder()
            }, lp(if (n > 0) 10 else 16))
            c.addView(text("Das Ergebnis ist eine Einladung zum Prüfen, kein festes Etikett (Buch, Seite 82). Es ersetzt weder Beratung noch Therapie.", 12f, DIM), lp(14))
        })
    }

    private fun showTypFinder() {
        val step = finder().replay(tfAnswers)
        val res = step.result
        if (res != null) showTfResult(res) else showTfRound(step)
    }

    private fun tfBack(step: TypFinder.Step) {
        if (step.lastRound.isEmpty()) { showTypFinderIntro(); return }
        for (id in step.lastRound) tfAnswers.remove(id)
        tfSave()
        showTypFinder()
    }

    private fun showTfRound(step: TypFinder.Step) {
        val round = step.round ?: return
        back.clear(); push { tfBack(step) }
        val sel = HashMap<String, Int>()
        screen("Typ-Finder", { tfBack(step) }, { c ->
            c.addView(text("Runde ${step.roundNo} · ${round.name}", 13f, GOLD, true), lp(2))
            c.addView(text("Wähle, was am ehesten zutrifft. Die erste Regung zählt.", 12f, DIM), lp(2))
            for (q in round.qs) {
                c.addView(text(q.q, 16f, INK, true), lp(18))
                val views = ArrayList<Pair<Int, TextView>>()
                fun restyle() {
                    for ((idx, v) in views) {
                        val on = sel[q.id] == idx
                        v.background = if (on) card(Color.parseColor("#3C3A1F"), GOLD) else card()
                    }
                }
                val order = (q.o.indices).toList().shuffled(java.util.Random(q.id.hashCode().toLong()))
                for (idx in order + listOf(-1)) {
                    val label = if (idx < 0) "Keine passt wirklich" else q.o[idx].text
                    val v = text(label, if (idx < 0) 13f else 15f, if (idx < 0) DIM else INK).apply {
                        setPadding(dp(14), dp(11), dp(14), dp(11))
                        isClickable = true
                        setOnClickListener { sel[q.id] = idx; restyle() }
                    }
                    views.add(idx to v)
                    c.addView(v, lp(8))
                }
                restyle()
            }
            c.addView(bigButton("➡️", "Weiter", "") {
                if (round.qs.all { sel.containsKey(it.id) }) {
                    tfAnswers.putAll(sel); tfSave(); showTypFinder()
                } else android.widget.Toast.makeText(this, "Bitte jede Frage beantworten.", android.widget.Toast.LENGTH_SHORT).show()
            }, lp(22))
        })
    }

    private fun showTfResult(r: TFResult) {
        back.clear(); push { showHome() }
        val top = r.types[0].first
        val sub = TypFinder.SUB[r.sub]
        val shareText = StringBuilder()
        screen("Deine Einschätzung", { showHome() }, { c ->
            val head = "Typ $top · ${TypFinder.TYPE_NAMES[top]}"
            c.addView(text(head, 20f, GOLD, true), lp(4))
            c.addView(text("Sicherheit: ${r.confidence}", 14f, INK), lp(2))
            if (r.confidence == "offen") c.addView(text("Zwei bis drei Typen liegen nah beieinander. Prüfe sie im Buch (Seiten 79 bis 82).", 13f, DIM), lp(4))
            c.addView(text("Instinkt", 13f, GOLD, true), lp(16))
            val i0 = TypFinder.INST_NAMES[r.inst[0].first] ?: ""
            c.addView(text(i0 + if (r.instOpen) "\nDer zweite Instinkt liegt nah dran: ${TypFinder.INST_NAMES[r.inst[1].first]}." else "", 15f, INK), lp(2))
            c.addView(text("Untertyp", 13f, GOLD, true), lp(14))
            if (sub != null) {
                c.addView(text("${r.sub} · ${sub.first}", 16f, INK, true), lp(2))
                c.addView(text(sub.second, 14f, DIM), lp(2))
                if (r.sub in TypFinder.KONTRA) c.addView(text("Hinweis (Seite 37): Kontratypen sind besonders schwer zu bestimmen, weil der Instinkt der Grundleidenschaft entgegenwirkt.", 12f, DIM), lp(6))
            }
            c.addView(text("Flügel", 13f, GOLD, true), lp(14))
            c.addView(text(if (r.wing != null) "${top}w${r.wing}" else "Kein klarer Flügel erkennbar", 15f, INK), lp(2))
            if (r.pro.isNotEmpty()) {
                c.addView(text("Dafür sprach", 13f, GOLD, true), lp(14))
                for (s in r.pro) c.addView(text("• $s", 14f, INK), lp(4))
            }
            val run = r.types[1].first
            c.addView(text("Auch möglich: Typ $run · ${TypFinder.TYPE_NAMES[run]}", 13f, GOLD, true), lp(14))
            for (s in r.contra) c.addView(text("• $s", 14f, DIM), lp(4))
            c.addView(text("Prüffrage zum Gegenlesen: Buch, Seite 82.", 13f, DIM), lp(6))
            c.addView(text("Rangliste: " + r.types.joinToString(" · ") { "Typ ${it.first}" }, 13f, DIM), lp(10))
            c.addView(text("Grundlage: ${r.questionsAsked} Antworten. Das Ergebnis ist eine Einladung zum Prüfen, kein festes Etikett.", 12f, DIM), lp(8))
            c.addView(bigButton("📖", "Im Buch: die Untertypen", "Seite ${27 + top}") { push { showTfResult(r) }; openReader(26 + top) }, lp(18))
            c.addView(bigButton("📖", "Im Buch: Typ erkennen", "Seiten 79 bis 82") { push { showTfResult(r) }; openReader(78) }, lp(10))
            c.addView(bigButton("📤", "Ergebnis teilen", "") {
                val msg = "Mein Typ-Finder-Ergebnis (Enneagramm · Matrix des Lebens): Typ $top, ${r.sub}" + (r.wing?.let { ", Flügel ${top}w$it" } ?: "") + ". Sicherheit: ${r.confidence}. Eine Einladung zum Prüfen, kein Etikett."
                startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, msg) }, "Teilen"))
            }, lp(10))
            c.addView(bigButton("🔄", "Neu beginnen", "") { tfAnswers.clear(); tfSave(); showTypFinder() }, lp(10))
        })
    }

    private fun showInfo() {
        screen("Info", { goBack() }, { c ->
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = card(); setPadding(dp(16), dp(14), dp(16), dp(16)) }
            box.addView(text("Das Buch", 17f, GOLD, true))
            box.addView(text("„Die Matrix des Lebendigen – Das Enneagramm“ von Marc Krekler. 166 Seiten, vollständig in der App, auch ohne Internet.", 14.5f, INK), lp(8))
            box.addView(text("Zur Nutzung", 17f, GOLD, true), lp(16))
            box.addView(text("Das Enneagramm ist ein Modell zur Selbstreflexion und Orientierung. Es ist kein Diagnoseverfahren und ersetzt weder ärztlichen noch psychologischen oder therapeutischen Rat.", 14.5f, INK), lp(8))
            box.addView(text("So liest du", 17f, GOLD, true), lp(16))
            box.addView(text("• Wische nach links und rechts, um zu blättern.\n• Doppeltippen oder zwei Finger zoomen die Seite.\n• ☆ merkt die Seite, ⋮ öffnet Inhalt, Suche, Lesezeichen und „Gehe zu Seite“.\n• Die App merkt sich, wo du aufgehört hast.", 14.5f, INK), lp(8))
            box.addView(text("Typ-Finder", 17f, GOLD, true), lp(16))
            box.addView(text("Eine geführte Befragung mit Fragen aus dem Alltag, die zu einer Einschätzung von Instinkt, Typ, Untertyp und Flügel führt. Die Fragen folgen dem Buch. Das Ergebnis ist eine Einladung zum Prüfen, kein festes Etikett. Deine Antworten bleiben auf deinem Gerät.", 14.5f, INK), lp(8))
            box.addView(text("©Marc Krekler · Eine Idee von autoknigge.de. Die App ist kostenlos und zeigt am unteren Rand Werbung. Deine Lesezeichen und die zuletzt gelesene Seite bleiben nur auf deinem Gerät.", 13f, DIM), lp(16))
            c.addView(box, lp(4))
        })
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
        pager?.let { prefs.edit().putInt("page", it.currentItem).apply() }
        if (::adManager.isInitialized) adManager.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::adManager.isInitialized) adManager.resume()
    }

    override fun onDestroy() {
        adapter?.shutdown()
        adapter = null
        io.shutdown()
        if (::adManager.isInitialized) adManager.destroy()
        super.onDestroy()
    }
}
