package de.autoknigge.enneagramm

import org.json.JSONObject

/** Geführte Befragung zu Instinkt, Zentrum, Typ, Untertyp und Flügel. Alles offline, nichts wird übertragen. */
class TFOption(val text: String, val w: Map<String, Double>)

class TFQuestion(val id: String, val st: String, val ty: List<Int>, val pair: Boolean, val q: String, val o: List<TFOption>)

class TFRound(val name: String, val qs: List<TFQuestion>)

class TFResult(
    val types: List<Pair<Int, Double>>,   // die drei wahrscheinlichsten Typen
    val inst: List<Pair<String, Double>>, // sp, so, sx nach Gewicht
    val sub: String,                      // z. B. SP6
    val wing: Int?,                       // null = kein klarer Flügel
    val confidence: String,
    val instOpen: Boolean,
    val pro: List<String>,
    val contra: List<String>,
    val questionsAsked: Int
)

class TypFinder(json: String) {
    val bank: List<TFQuestion>
    private val byId: Map<String, TFQuestion>

    init {
        val arr = JSONObject(json).getJSONArray("questions")
        bank = (0 until arr.length()).map { i ->
            val q = arr.getJSONObject(i)
            val o = q.getJSONArray("o")
            val ty = q.optJSONArray("ty")
            TFQuestion(
                q.getString("id"), q.getString("st"),
                if (ty == null) emptyList() else (0 until ty.length()).map { ty.getInt(it) },
                q.optBoolean("pair", false), q.getString("q"),
                (0 until o.length()).map { j ->
                    val oo = o.getJSONObject(j); val w = oo.getJSONObject("w")
                    TFOption(oo.getString("t"), w.keys().asSequence().associateWith { k -> w.getDouble(k) })
                }
            )
        }
        byId = bank.associateBy { it.id }
    }

    private class State {
        val t = DoubleArray(10)
        val i = doubleArrayOf(0.0, 0.0, 0.0)           // sp, so, sx
        val b = doubleArrayOf(0.0, 0.0, 0.0)           // bauch, herz, kopf
        val w = doubleArrayOf(0.0, 0.0)                // unterer / oberer Flügel
        val asked = HashSet<String>()
        val hist = ArrayList<Pair<TFQuestion, Int>>()
        var phase = "A"
        var gegen = false
        var cn = 0
        var last: List<String> = emptyList()
        var rounds = 0
    }

    class Step(val round: TFRound?, val result: TFResult?, val roundNo: Int, val lastRound: List<String>)

    private fun apply(s: State, q: TFQuestion, idx: Int) {
        s.asked.add(q.id)
        if (idx < 0) return
        for ((k, v) in q.o[idx].w) when (k) {
            "sp" -> s.i[0] += v
            "so" -> s.i[1] += v
            "sx" -> s.i[2] += v
            "bauch" -> { s.b[0] += v; for (ty in CEN[0]) s.t[ty] += v }
            "herz" -> { s.b[1] += v; for (ty in CEN[1]) s.t[ty] += v }
            "kopf" -> { s.b[2] += v; for (ty in CEN[2]) s.t[ty] += v }
            "wl" -> s.w[0] += v
            "wh" -> s.w[1] += v
            else -> s.t[k.toInt()] += v
        }
        s.hist.add(q to idx)
    }

    private fun ranked(s: State) = (1..9).sortedWith(compareBy({ -s.t[it] }, { it }))
    private fun lead(v: DoubleArray): Double { val a = v.sortedDescending(); return a[0] - a[1] }
    private fun pool(st: String) = bank.filter { it.st == st }

    private fun cover(s: State, top3: List<Int>, n: Int): List<TFQuestion> {
        val sc = ArrayList<Pair<Double, TFQuestion>>()
        for (q in bank) {
            if (q.st != "C" || q.id in s.asked) continue
            val c0 = if (top3[0] in q.ty) 1 else 0
            val c1 = if (top3[1] in q.ty) 1 else 0
            val c2 = if (top3[2] in q.ty) 1 else 0
            if (c0 + c1 == 0) continue
            if (c0 + c1 < 2 && c2 == 0) continue
            sc.add((-(c0 * 4.0 + c1 * 3.0 + c2 * 1.5 + (if (q.pair) 0.0 else 0.5))) to q)
        }
        return sc.sortedWith(compareBy({ it.first }, { it.second.id })).take(n).map { it.second }
    }

    /** Nächste Runde oder null am Ende. Rein deterministisch, daher reicht die Antwortliste zum Fortsetzen. */
    private fun pick(s: State): TFRound? {
        while (true) {
            when (s.phase) {
                "A" -> {
                    val n = pool("A").count { it.id in s.asked }
                    if (n >= 6 && (lead(s.i) >= 4.0 || n >= 10)) { s.phase = "B"; continue }
                    val r = pool("A").filter { it.id !in s.asked }.take(3)
                    if (r.isEmpty()) { s.phase = "B"; continue }
                    return TFRound("Instinkt", r)
                }
                "B" -> {
                    val n = pool("B").count { it.id in s.asked }
                    if (n >= 3 && (lead(s.b) >= 3.0 || n >= 9)) { s.phase = "C"; continue }
                    val r = pool("B").filter { it.id !in s.asked }.take(3)
                    if (r.isEmpty()) { s.phase = "C"; continue }
                    return TFRound("Zentrum", r)
                }
                "C" -> {
                    val rk = ranked(s)
                    if ((s.cn >= 6 && s.t[rk[0]] - s.t[rk[1]] >= 4.0) || s.cn >= 15) { s.phase = "G"; continue }
                    val r = cover(s, rk.take(3), 3)
                    if (r.isEmpty()) { s.phase = "G"; continue }
                    return TFRound("Typ", r)
                }
                "G" -> {
                    if (s.gegen) { s.phase = "D"; continue }
                    s.gegen = true
                    val rk = ranked(s); val top = rk[0]
                    val cands = listOf(rk[1]) + (CONF[top] ?: emptyList()).filter { it != rk[1] }
                    val r = ArrayList<TFQuestion>()
                    for (c in cands) {
                        val q = bank.firstOrNull { it.st == "C" && it.pair && it.ty.toSet() == setOf(top, c) && it.id !in s.asked && it !in r }
                        if (q != null) r.add(q)
                        if (r.size >= 3) break
                    }
                    if (r.isEmpty()) continue
                    return TFRound("Gegenprobe", r)
                }
                "D" -> {
                    val top = ranked(s)[0]
                    val r = pool("D").filter { it.ty == listOf(top) && it.id !in s.asked }.take(3)
                    s.phase = "E"
                    if (r.isNotEmpty()) return TFRound("Untertyp", r)
                }
                "E" -> {
                    val top = ranked(s)[0]
                    val r = pool("E").filter { it.ty == listOf(top) && it.id !in s.asked }
                    s.phase = "X"
                    if (r.isNotEmpty()) return TFRound("Flügel", r)
                }
                else -> return null
            }
        }
    }

    /** answers: Frage-ID zu gewählter Option (Index im Original, -1 = „keine passt“). */
    fun replay(answers: Map<String, Int>): Step {
        val s = State()
        while (true) {
            val r = pick(s) ?: return Step(null, build(s), s.rounds, s.last)
            if (r.qs.all { answers.containsKey(it.id) }) {
                for (q in r.qs) { apply(s, q, answers.getValue(q.id)); if (q.st == "C") s.cn++ }
                s.last = r.qs.map { it.id }
                s.rounds++
            } else return Step(r, null, s.rounds + 1, s.last)
        }
    }

    private fun build(s: State): TFResult {
        val rk = ranked(s); val top = rk[0]; val run = rk[1]
        val inst = listOf("sp" to s.i[0], "so" to s.i[1], "sx" to s.i[2]).sortedByDescending { it.second }
        val l = s.t[top] - s.t[run]
        val conf = when { l >= 6.0 && s.cn >= 8 -> "eher sicher"; l >= 3.0 -> "wahrscheinlich"; else -> "offen" }
        val lo = s.w[0]; val hi = s.w[1]
        val wingPair = WINGS.getValue(top)
        val wing = when { lo - hi >= 1.5 -> wingPair.first; hi - lo >= 1.5 -> wingPair.second; else -> null }
        val key = top.toString()
        val pro = s.hist.filter { (q, i) -> i >= 0 && q.st in setOf("C", "B") && (q.o[i].w[key] ?: 0.0) >= 2.0 }.map { it.first.o[it.second].text }.take(3)
        val con = s.hist.filter { (q, i) -> i >= 0 && q.st == "C" && (q.o[i].w[run.toString()] ?: 0.0) >= 2.0 }.map { it.first.o[it.second].text }.take(2)
        return TFResult(rk.take(3).map { it to s.t[it] }, inst, inst[0].first.uppercase() + top, wing, conf,
            inst[0].second - inst[1].second < 2.0, pro, con, s.hist.size)
    }

    companion object {
        val CEN = listOf(listOf(8, 9, 1), listOf(2, 3, 4), listOf(5, 6, 7))
        val CONF = mapOf(1 to listOf(6, 8, 2), 2 to listOf(4, 3, 1), 3 to listOf(8, 7, 2), 4 to listOf(5, 9, 2), 5 to listOf(4, 9, 6),
            6 to listOf(1, 7, 5), 7 to listOf(6, 3), 8 to listOf(3, 9, 1), 9 to listOf(5, 4, 8))
        val WINGS = mapOf(1 to (9 to 2), 2 to (1 to 3), 3 to (2 to 4), 4 to (3 to 5), 5 to (4 to 6), 6 to (5 to 7), 7 to (6 to 8), 8 to (7 to 9), 9 to (8 to 1))
        val TYPE_NAMES = mapOf(1 to "Perfektionist / Reformer", 2 to "Helfer / Beziehungsmensch", 3 to "Erfolgsmensch / Macher",
            4 to "Individualist / Romantiker", 5 to "Denker / Beobachter", 6 to "Skeptiker / Loyalist", 7 to "Enthusiast / Optimist",
            8 to "Herausforderer / Beschützer", 9 to "Vermittler / Friedensstifter")
        val INST_NAMES = mapOf("sp" to "Selbsterhaltung (sp, „Meins“)", "so" to "Sozial (so, „Unser“)", "sx" to "Sexuell / Zweier (sx, „Deins“)")
        /** Untertypen nach den Seiten 28 bis 36 des Buches. Kontratypen sind in KONTRA markiert. */
        val SUB = mapOf(
            "SO1" to ("Vorbild / Unanfechtbarkeit" to "Will ein unanfechtbares Vorbild in der Gesellschaft sein. Wirkt oft förmlich, ethisch und prinzipientreu."),
            "SX1" to ("Eifer / Verbesserung" to "Richtet die Strenge nach außen. Will andere oder die Welt offen reformieren und fordert Perfektion laut ein."),
            "SP1" to ("Sorge / Vorsorge" to "Richtet die Strenge nach innen. Ist vorsichtig, besorgt und gut vorbereitet und versucht, Fehler im Vorfeld zu vermeiden."),
            "SO2" to ("Einfluss / Ambition" to "Agiert als strategischer Netzwerker. Will in Organisationen und Gruppen eine unentbehrliche Schlüsselfigur sein."),
            "SX2" to ("Verführung / Eroberung" to "Baut intensive Eins-zu-eins-Beziehungen auf und verführt durch persönliche Zuwendung, Charme und emotionale Nähe."),
            "SP2" to ("Privileg / Kindlichkeit" to "Reagiert unbewusst mit Niedlichkeit oder Hilflosigkeit. Will versorgt werden, statt immer nur zu geben."),
            "SO3" to ("Status / Rolle" to "Der klassische Vorzeige-Erfolgreiche. Liebt Titel, sichtbare Ergebnisse und Repräsentation nach außen."),
            "SX3" to ("Attraktivität / Ausstrahlung" to "Richtet den Erfolg darauf aus, für den Partner oder Wunschmenschen attraktiv, wertvoll und begehrenswert zu sein."),
            "SP3" to ("Effizienz / Sicherheit" to "Arbeitet extrem hart, hängt den Erfolg aber nicht an die große Glocke. Der leise Workaholic."),
            "SO4" to ("Scham / Opferrolle" to "Spürt das Gefühl des Andersseins gegenüber der Gruppe stark. Fühlt sich oft unverstanden oder als Außenseiter."),
            "SX4" to ("Konkurrenz / Neid" to "Reagiert auf Gefühle der Unzulänglichkeit mit Wut oder Verlangen. Tritt oft fordernd und kompromisslos auf."),
            "SP4" to ("Zähigkeit / Ausdauer" to "Leidet im Stillen. Bucht den Schmerz nach innen, beschwert sich selten und zeigt große Ausdauer im Ertragen von Härte."),
            "SO5" to ("Expertise / Totem" to "Sucht den Platz in der Welt über Expertenwissen. Verbindet sich mit Gruppen über Theorien, Systeme und Wissenschaft."),
            "SX5" to ("Vertrauen / Geheimnis" to "Sucht eine einzige, tiefgründige Person, mit der er seine geheimsten Gedanken und Ideen teilen kann."),
            "SP5" to ("Zuflucht / Schutzraum" to "Der Rückzugstyp. Braucht eine feste, physische Höhle oder Grenze und minimiert eigene Bedürfnisse radikal."),
            "SO6" to ("Pflicht / Regeln" to "Sucht Sicherheit in Strukturen, Systemen, Verträgen und klaren Regeln, an die er sich strikt hält."),
            "SX6" to ("Stärke / Gegenphobisch" to "Geht der Angst entgegen. Tritt mutig, provokant oder aggressiv auf („Angriff ist die beste Verteidigung“)."),
            "SP6" to ("Warmherzigkeit / Bündnis" to "Phobisch. Macht sich durch Freundlichkeit, Sanftmut und Beziehungsaufbau unangreifbar."),
            "SO7" to ("Aufopferung / Idealismus" to "Zügelt die eigene Ausgelassenheit und verzichtet auf eigene Wünsche, um für das Wohl einer Gruppe oder Sache da zu sein."),
            "SX7" to ("Faszination / Utopie" to "Wandelt in einer Traumwelt aus Möglichkeiten. Verliebt sich leicht in Ideen, Visionen und magische Momente."),
            "SP7" to ("Netzwerk / Bande" to "Sehr pragmatisch. Baut sich eine „Sippe“ oder ein Netzwerk auf, um stets Zugang zu Genuss, Vorteilen und Sicherheit zu haben."),
            "SO8" to ("Der Beschützer" to "Rebelliert gegen Ungerechtigkeit für andere. Wirkt oft nicht wie der typische „egoistische“ Achter, sondern wie ein starker, beschützender Anführer."),
            "SX8" to ("Einfluss / Besitz" to "Extrem intensiv, leidenschaftlich und einnehmend. Will emotionale Dominanz und fordert absolute Hingabe."),
            "SP8" to ("Überleben / Versteck" to "Sehr pragmatisch, fokussiert auf materielle Unabhängigkeit, physischen Schutz und Territorium. Spricht wenig, handelt direkt."),
            "SO9" to ("Beteiligung / Mitarbeit" to "Sehr aktiv und gesellig in Gruppen. Wirkt energisch und fröhlich, lenkt damit aber von den eigenen tiefen Wünschen und Konflikten ab."),
            "SX9" to ("Verschmelzung" to "Tauscht die eigene Identität fast vollständig gegen die des Partners oder Lieblingsmenschen ein, um Konflikte zu vermeiden."),
            "SP9" to ("Erstattung / Komfort" to "Betäubt eigene Bedürfnisse durch physische Routinen, Essen, Medien oder feste Gemütlichkeit.")
        )
        /** Kontratypen (Buch S. 37): Der Instinkt wirkt der Grundleidenschaft entgegen. */
        val KONTRA = setOf("SX1", "SP2", "SP3", "SP4", "SX5", "SX6", "SO7", "SO8", "SO9")
    }
}
