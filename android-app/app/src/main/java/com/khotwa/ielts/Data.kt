package com.khotwa.ielts

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

object I18n { var ar by mutableStateOf(false) }

/** Picks the English or Arabic text for the interface. Exam content stays in English. */
fun tr(en: String, ar: String): String = if (I18n.ar) ar else en

/** English exam text keeps left-to-right layout inside the Arabic interface. */
val EnText get() = Body.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr)

fun timeL(t: String) = if (I18n.ar) t.replace(" minutes", " دقائق").replace(" minute", " دقيقة") else t

class Q(val t: String, val q: String, val a: Int, val accepted: List<String>, val w: String, val o: List<String>, val n: Int)
class Group(val ins: String, val opts: List<String>, val form: String?, val qs: List<Q>)
class Section(
    val id: String, val mock: Int, val kind: Char, val title: String, val sub: String,
    val paras: List<Pair<String, String>>, val script: List<Pair<String, String>>, val groups: List<Group>
)
class BankQ(val id: String, val skill: String, val ctx: String, val q: String, val options: List<String>, val answer: Int, val why: String, val placement: Boolean)
class Lesson(val id: String, val title: String, val skill: String, val time: String, val sub: String, val body: String, val example: String, val action: String,
             val titleAr: String = "", val subAr: String = "", val bodyAr: String = "", val actionAr: String = "") {
    fun titleL() = if (I18n.ar && titleAr.isNotBlank()) titleAr else title
    fun subL() = if (I18n.ar && subAr.isNotBlank()) subAr else sub
    fun bodyL() = if (I18n.ar && bodyAr.isNotBlank()) bodyAr else body
    fun actionL() = if (I18n.ar && actionAr.isNotBlank()) actionAr else action
}
class Writing(val id: String, val type: String, val task: Int, val title: String, val min: Int, val minutes: Int, val prompt: String, val chart: JSONObject?, val tips: List<String>, val model: String?)
class Speaking(val id: String, val topic: String, val p1: List<String>, val cue: String, val bullets: List<String>, val p3: List<String>)
class Vocab(val id: Int, val w: String, val def: String, val ex: String, val ar: String = "")

private fun JSONArray?.strs(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
private fun JSONArray?.pairs(): List<Pair<String, String>> = if (this == null) emptyList() else (0 until length()).map { val p = getJSONArray(it); p.getString(0) to p.getString(1) }

object Content {
    lateinit var store: Store
    var bank = listOf<BankQ>()
    var lessons = listOf<Lesson>()
    var reading = listOf<Section>()
    var listening = listOf<Section>()
    var writing = listOf<Writing>()
    var speaking = listOf<Speaking>()
    var vocab = listOf<Vocab>()
    var external = listOf<External>()
    var placeR: Section? = null
    var placeL: Section? = null
    private var loaded = false

    private fun sections(arr: JSONArray, kind: Char): List<Section> = (0 until arr.length()).map { i ->
        val s = arr.getJSONObject(i)
        val gs = s.getJSONArray("groups")
        val groups = (0 until gs.length()).map { gi ->
            val g = gs.getJSONObject(gi)
            val qa = g.getJSONArray("qs")
            val qs = (0 until qa.length()).map { qi ->
                val q = qa.getJSONObject(qi)
                val a = q.get("a")
                val acc = if (a is JSONArray) a.strs() else emptyList()
                Q(q.getString("t"), q.getString("q"), if (a is Int) a else -1, acc, q.optString("w"), q.optJSONArray("o").strs(), q.optInt("n"))
            }
            Group(g.optString("ins"), g.optJSONArray("opts").strs(), g.optString("form").ifEmpty { null }, qs)
        }
        Section(
            s.getString("id"), s.optInt("mock"), kind,
            s.optString("title"), if (kind == 'R') s.optString("topic") else s.optString("ctx"),
            s.optJSONArray("paras").pairs(), s.optJSONArray("script").pairs(), groups
        )
    }

    fun load(ctx: Context) {
        if (loaded) return
        val txt = ctx.assets.open("content.json").bufferedReader().use { it.readText() }
        val o = JSONObject(txt)
        o.getJSONArray("bank").let { a ->
            bank = (0 until a.length()).map {
                val b = a.getJSONObject(it)
                BankQ(b.optString("id"), b.optString("skill"), b.optString("context"), b.optString("q"), b.optJSONArray("options").strs(), b.optInt("answer"), b.optString("why"), b.optBoolean("placement"))
            }
        }
        o.getJSONArray("lessons").let { a ->
            lessons = (0 until a.length()).map {
                val b = a.getJSONObject(it)
                Lesson(b.optString("id"), b.optString("title"), b.optString("skill"), b.optString("time"), b.optString("sub"), b.optString("body"), b.optString("example"), b.optString("action"),
                    b.optString("title_ar"), b.optString("sub_ar"), b.optString("body_ar"), b.optString("action_ar"))
            }
        }
        reading = sections(o.getJSONArray("reading"), 'R')
        listening = sections(o.getJSONArray("listening"), 'L')
        o.getJSONArray("writing").let { a ->
            writing = (0 until a.length()).map {
                val b = a.getJSONObject(it)
                Writing(b.optString("id"), b.optString("type"), b.optInt("task"), b.optString("title"), b.optInt("min"), b.optInt("minutes"), b.optString("prompt"), b.optJSONObject("chart"), b.optJSONArray("tips").strs(), b.optString("model").ifEmpty { null })
            }
        }
        o.getJSONArray("speaking").let { a ->
            speaking = (0 until a.length()).map {
                val b = a.getJSONObject(it)
                val c = b.getJSONObject("cue")
                Speaking(b.optString("id"), b.optString("topic"), b.optJSONArray("p1").strs(), c.optString("text"), c.optJSONArray("bullets").strs(), b.optJSONArray("p3").strs())
            }
        }
        o.getJSONArray("vocab").let { a ->
            vocab = (0 until a.length()).map {
                val b = a.getJSONObject(it)
                Vocab(b.optInt("id"), b.optString("w"), b.optString("def"), b.optString("ex"), b.optString("ar"))
            }
        }
        o.optJSONArray("external")?.let { a ->
            external = (0 until a.length()).map { i -> val x = a.getJSONObject(i)
                External(x.optString("id"), x.optString("title"), x.optString("source"), x.optString("collection"), x.optString("full"), x.optString("testId"),
                    x.optString("listening"), x.optString("reading"), x.optString("writing"), x.optString("speaking"),
                    x.optJSONArray("speakingParts")?.let { sp -> (0 until sp.length()).map { sp.getInt(it) } }, x.optInt("speakingMinutes", 0)) }
        }
        o.optJSONObject("placement")?.let { p ->
            p.optJSONObject("reading")?.let { placeR = sections(JSONArray().put(it), 'R').first() }
            p.optJSONObject("listening")?.let { placeL = sections(JSONArray().put(it), 'L').first() }
        }
        loaded = true
    }
    fun section(id: String) = (reading + listening).first { it.id == id }
    fun sectionOrNull(id: String) = (reading + listening).firstOrNull { it.id == id }
    fun mocks(): Int = maxOf(1, reading.maxOfOrNull { it.mock } ?: 1)
}

fun today(): Int {
    val now = System.currentTimeMillis()
    return ((now + TimeZone.getDefault().getOffset(now)) / 86400000L).toInt()
}

class Store(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("khotwa", 0)
    private var root = JSONObject(sp.getString("s", "{}") ?: "{}")
    private var rev by mutableStateOf(0)
    private fun r(): JSONObject { rev; return root }
    fun save() { sp.edit().putString("s", root.toString()).apply(); rev++ }

    var type: String
        get() = r().optString("type", "Academic")
        set(v) { root.put("type", v); save() }
    var goal: Double
        get() = r().optDouble("goal", 6.5)
        set(v) { root.put("goal", v); save() }
    var minutes: Int
        get() = r().optInt("minutes", 30)
        set(v) { root.put("minutes", v); save() }
    var start: Int
        get() = r().optInt("start", 0)
        set(v) { root.put("start", v); save() }
    var lang: String
        get() = r().optString("lang", "en")
        set(v) { root.put("lang", v); save(); I18n.ar = v == "ar" }
    fun est(sk: String): Double? { val m = map("est"); return if (m.has(sk)) m.optDouble(sk) else null }
    fun setEst(sk: String, v: Double) = putMap("est", sk, v)
    /** Starts the program again from today, keeping progress and levels. */
    fun restartProgram() { root.put("start", today()); root.remove("dp"); save() }
    /** Re-plans from today onwards without moving the day count. */
    fun replanFuture() { val d = (today() - start + 1).toString(); val m = map("dp"); val keep = JSONObject(); if (m.has(d)) keep.put(d, m.get(d)); root.put("dp", keep); save() }
    fun bump(k: String, key: String) = putMap(k, key, mapInt(k, key) + 1)
    /** Exam date as a day number (same as today()); 0 when not set. Same key as the web app. */
    var exam: Int
        get() = r().optInt("exam", 0)
        set(v) { if (v <= 0) root.remove("exam") else root.put("exam", v); save() }
    fun mapKeys(k: String): List<String> = map(k).keys().asSequence().toList()
    fun resetKey(k: String) { root.remove(k); save() }
    fun removeKey(k: String, key: String) { val m = map(k); m.remove(key); root.put(k, m); save() }
    var placed: Boolean
        get() = r().optBoolean("placed", false)
        set(v) { root.put("placed", v); save() }

    private fun set(k: String): MutableSet<String> {
        val a = r().optJSONArray(k) ?: return mutableSetOf()
        return (0 until a.length()).map { a.getString(it) }.toMutableSet()
    }
    fun has(k: String, v: String) = set(k).contains(v)
    fun toggle(k: String, v: String) {
        val s = set(k); if (!s.add(v)) s.remove(v)
        root.put(k, JSONArray(s.toList())); save()
    }
    fun add(k: String, v: String) { val s = set(k); if (s.add(v)) { root.put(k, JSONArray(s.toList())); save() } }

    private fun map(k: String): JSONObject = r().optJSONObject(k) ?: JSONObject()
    fun mapInt(k: String, key: String, d: Int = 0) = map(k).optInt(key, d)
    fun putMap(k: String, key: String, v: Any) { val m = map(k); m.put(key, v); root.put(k, m); save() }
    fun mapStr(k: String, key: String) = map(k).optString(key, "")
    fun mapDouble(k: String, key: String, d: Double = 0.0) = map(k).optDouble(key, d)
    /** Clears the saved parts of the placement test so it starts again. */
    fun resetPlacement() { root.remove("plc"); save() }

    fun stat(group: String, key: String): Pair<Int, Int> { val o = map(group).optJSONObject(key); return (o?.optInt("c") ?: 0) to (o?.optInt("t") ?: 0) }
    fun addStat(group: String, key: String, c: Int, t: Int) {
        val m = map(group); val o = m.optJSONObject(key) ?: JSONObject()
        o.put("c", o.optInt("c") + c); o.put("t", o.optInt("t") + t); m.put(key, o); root.put(group, m); save()
    }
    fun history(): List<JSONObject> { val a = r().optJSONArray("hist") ?: return emptyList(); return (0 until a.length()).map { a.getJSONObject(it) }.reversed() }
    fun addHistory(title: String, score: Int, total: Int, band: Double?) {
        val a = root.optJSONArray("hist") ?: JSONArray()
        a.put(JSONObject().put("title", title).put("score", score).put("total", total).put("band", band ?: JSONObject.NULL).put("day", today()))
        val out = JSONArray(); val from = maxOf(0, a.length() - 80)
        for (i in from until a.length()) out.put(a.get(i))
        root.put("hist", out); addDay()
    }
    fun addDay() {
        val t = today().toString(); val s = set("days"); if (s.add(t)) root.put("days", JSONArray(s.toList()))
        save()
    }
    fun streak(): Int {
        val s = set("days").mapNotNull { it.toIntOrNull() }.toSet()
        var d = today(); if (!s.contains(d)) d -= 1
        var n = 0; while (s.contains(d)) { n++; d-- }
        return n
    }
    fun export(): String = r().toString(2)
    fun import(txt: String): Boolean = try { root = JSONObject(txt); save(); I18n.ar = lang == "ar"; true } catch (e: Exception) { false }
    fun reset() { val l = lang; root = JSONObject().put("lang", l); save() }
}
