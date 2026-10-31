package de.autoknigge.enneagramm

import android.content.Context
import org.json.JSONObject

/** Eine Tafel des Lern-Hefts: eine Überschrift mit einem Eintrag je Typ (1 bis 9). */
class Panel(val page: String, val chap: String, val title: String, val bubble: String, val items: Map<Int, String>)

object Data {
    val COLORS = mapOf(
        1 to 0xFFF28C38.toInt(), 2 to 0xFFF6C945.toInt(), 3 to 0xFFA6CF4B.toInt(),
        4 to 0xFF34B8A6.toInt(), 5 to 0xFF4C9BE8.toInt(), 6 to 0xFF6C6FE0.toInt(),
        7 to 0xFFB35FD6.toInt(), 8 to 0xFFEA5A8A.toInt(), 9 to 0xFFE65B4C.toInt()
    )

    /** Reihenfolge wie im Heft: Bauch (8, 9, 1), Herz (2, 3, 4), Kopf (5, 6, 7). */
    val ORDER = listOf(8, 9, 1, 2, 3, 4, 5, 6, 7)

    /** Kapitel des Lern-Hefts: Anzeigename, Beschreibung, Anker in der Seite. */
    val CHAPTERS = listOf(
        Triple("Start", "So liest du das Heft", "start"),
        Triple("Triaden", "Bauch, Herz und Kopf", "triaden"),
        Triple("Das Grundmuster", "Name, Verlangen, Strategie, Eigenschutz", "muster"),
        Triple("Energie & Erkennen", "Ausstrahlung, Zäsur, Kindheit, Partner", "energie"),
        Triple("Stress & Heilung", "Wohin sich Typen bewegen", "bewegung"),
        Triple("Dreiecke", "Acht Muster-Gruppen", "dreiecke"),
        Triple("Berater", "Der Rat der anderen Typen", "berater"),
        Triple("Untertypen", "Instinkte und 27 Wege", "untertypen")
    )

    lateinit var names: Map<Int, String>
        private set
    lateinit var panels: List<Panel>
        private set

    fun load(ctx: Context) {
        if (::panels.isInitialized) return
        val txt = ctx.assets.open("data.json").bufferedReader().use { it.readText() }
        val o = JSONObject(txt)
        val n = o.getJSONObject("names")
        names = n.keys().asSequence().associate { it.toInt() to n.getString(it) }
        val a = o.getJSONArray("panels")
        panels = (0 until a.length()).map {
            val p = a.getJSONObject(it)
            val items = p.getJSONObject("items")
            Panel(
                p.getString("page"), p.getString("chap"), p.getString("title"), p.optString("bubble"),
                items.keys().asSequence().associate { k -> k.toInt() to items.getString(k) }
            )
        }
    }

    fun label(type: Int) = "$type · ${names[type]}"

    /** Lernfragen: Tafel plus Typ, deren Text innerhalb der Tafel eindeutig ist. */
    class Q(val panel: Panel, val type: Int) {
        val text get() = panel.items[type] ?: ""
    }

    fun questions(): List<Q> {
        val out = ArrayList<Q>()
        for (p in panels) {
            if (p.title == "Nomen est omen") continue
            for ((t, txt) in p.items) {
                if (p.items.values.count { it == txt } == 1 && txt.length in 3..120) out.add(Q(p, t))
            }
        }
        return out
    }
}
