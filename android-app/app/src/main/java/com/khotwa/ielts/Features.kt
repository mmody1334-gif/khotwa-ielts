package com.khotwa.ielts

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// ---------------------------------------------------------------- weak spot

val QTYPE_NAMES = mapOf("tfng" to "True / False / Not Given", "yn" to "Yes / No / Not Given", "sel" to "Matching", "mcq" to "Multiple choice", "gap" to "Completion")
class Weak(val type: String, val acc: Double, val n: Int)

/** The question type with the lowest accuracy (at least 8 answers, under 75%). */
fun weakType(st: Store): Weak? {
    var best: Weak? = null
    for (t in QTYPE_NAMES.keys) {
        val (c, n) = st.stat("ty", t); if (n < 8) continue
        val a = c.toDouble() / n
        if (a < 0.75 && (best == null || a < best.acc)) best = Weak(t, a, n)
    }
    return best
}

// ---------------------------------------------------------------- mistakes bank (spaced review)

private val BOXES = listOf(1, 3, 7, 14)
class Mistake(val key: String, val box: Int, val due: Int)

/** Keys: "b<bank id>" or "s<section id>|<question n>". Value: "box|due day". Same format as the web app. */
fun addMistake(st: Store, key: String) = st.putMap("mist", key, "0|${today() + 1}")
fun mistakes(st: Store): List<Mistake> = st.mapKeys("mist").map { k ->
    val p = st.mapStr("mist", k).split("|"); Mistake(k, p.getOrNull(0)?.toIntOrNull() ?: 0, p.getOrNull(1)?.toIntOrNull() ?: 0)
}
fun mistakesDue(st: Store) = mistakes(st).filter { it.due <= today() }
fun reviewAnswer(st: Store, key: String, ok: Boolean) {
    val m = mistakes(st).firstOrNull { it.key == key } ?: return
    if (!ok) { st.putMap("mist", key, "0|${today() + 1}"); return }
    val b = m.box + 1
    if (b >= 2) st.removeKey("mist", key)          // right twice in a row: learned
    else st.putMap("mist", key, "$b|${today() + BOXES[b]}")
}

class MItem(val bank: BankQ? = null, val sec: Section? = null, val g: Group? = null, val q: Q? = null)
fun mistakeItem(key: String): MItem? {
    if (key.startsWith("b")) return Content.bank.firstOrNull { it.id == key.drop(1) }?.let { MItem(bank = it) }
    val parts = key.drop(1).split("|"); if (parts.size != 2) return null
    val all = Content.reading + Content.listening + listOfNotNull(Content.placeR, Content.placeL)
    val s = all.firstOrNull { it.id == parts[0] } ?: return null
    for (g in s.groups) for (q in g.qs) if (q.n.toString() == parts[1]) return MItem(sec = s, g = g, q = q)
    return null
}

@Composable
fun ReviewScreen(st: Store, nav: Nav) {
    val queue = remember { mistakesDue(st).shuffled().take(12).map { it.key } }
    var i by remember { mutableStateOf(0) }
    var okCount by remember { mutableStateOf(0) }
    var checked by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    var showCtx by remember { mutableStateOf(false) }
    val ans = remember { mutableStateMapOf<String, String>() }
    fun next() { i++; checked = false; showCtx = false; ans.clear() }
    Screen(tr("My mistakes", "أخطائي"), { nav.back() }) {
        Page {
            if (queue.isEmpty()) {
                NCard(bg = Lime) { Text(tr("Nothing to review now", "لا شيء للمراجعة الآن"), style = H2); Text(tr("${mistakes(st).size} questions are waiting for a later day. Wrong answers in practice are added here automatically.", "${mistakes(st).size} سؤالًا تنتظر يومًا لاحقًا. الإجابات الخاطئة في التدريب تُضاف هنا تلقائيًا."), style = Body) }
                return@Page
            }
            if (i >= queue.size) {
                NCard(bg = Lime) { Text(tr("Review done", "انتهت المراجعة"), style = H2); Text("$okCount / ${queue.size}", style = H1); Text(tr("Right answers come back later; after two in a row they leave the list.", "الإجابات الصحيحة ترجع لاحقًا؛ وبعد مرتين متتاليتين تخرج من القائمة."), style = Body) }
                NButton(tr("Back to home", "العودة للرئيسية")) { nav.root("home") }
                return@Page
            }
            val key = queue[i]
            val it = mistakeItem(key)
            if (it == null) { LaunchedEffect(key) { st.removeKey("mist", key); next() }; return@Page }
            Bar((i + 1f) / queue.size, Coral); Text("${i + 1} / ${queue.size}", style = Small)
            if (it.bank != null) {
                val q = it.bank
                if (q.ctx.isNotBlank()) NCard(bg = Paper) { Text(q.ctx, style = EnText) }
                Text(q.q, style = EnText.copy(fontSize = 17.sp))
                for ((k, o) in q.options.withIndex()) OptionRow(o, ans["x"] == k.toString(), "ABCD"[k].toString()) { if (!checked) ans["x"] = k.toString() }
                if (!checked) NButton(tr("Check", "تحقق"), bg = Lime, enabled = ans["x"] != null) {
                    checked = true; right = ans["x"]?.toIntOrNull() == q.answer; if (right) okCount++; reviewAnswer(st, key, right)
                } else {
                    NCard(bg = if (right) Lime else Coral) { Text(if (right) tr("Correct", "صحيح") else tr("Answer: ", "الصحيح: ") + q.options[q.answer], style = H3); Text(q.why, style = EnText.copy(fontSize = 14.sp)) }
                    NButton(tr("Next", "التالي")) { next() }
                }
            } else {
                val s = it.sec!!; val g = it.g!!; val q = it.q!!; val isL = s.script.isNotEmpty()
                Text(s.title, style = Small)
                Text(g.ins, style = Label)
                NButton(if (showCtx) tr("Hide", "إخفاء") else if (isL) tr("Show transcript", "اعرض النص") else tr("Show passage", "اعرض القطعة"), bg = Card) { showCtx = !showCtx }
                if (showCtx) NCard(bg = Paper) {
                    if (isL) Text(s.script.joinToString("\n") { "${it.first}: ${it.second}" }, style = EnText.copy(fontSize = 14.sp))
                    else for ((l, t) in s.paras) Text("$l  $t", style = EnText.copy(fontSize = 15.sp))
                }
                QuestionView(g, q, "x", ans)
                if (!checked) NButton(tr("Check", "تحقق"), bg = Lime, enabled = !ans["x"].isNullOrBlank()) {
                    checked = true; right = isCorrect(q, ans["x"]); if (right) okCount++; reviewAnswer(st, key, right)
                } else {
                    NCard(bg = if (right) Lime else Coral) { Text(if (right) tr("Correct", "صحيح") else tr("Answer: ", "الصحيح: ") + correctText(g, q), style = H3); Text(q.w, style = EnText.copy(fontSize = 14.sp)) }
                    NButton(tr("Next", "التالي")) { next() }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- dictation (listening Section 1 skills)

private val DNAMES = listOf("Mansour", "Harrison", "Thompson", "Al Rawahi", "Fitzgerald", "Okonkwo", "Whitaker", "Haddad", "Pemberton", "Kowalski", "Baxter", "Nakamura", "Ellsworth", "Gallagher", "Al Balushi", "Henshaw")
private val DSTREETS = listOf("Wellington Road", "Kingsley Avenue", "Marsh Lane", "Beacon Street", "Hollybush Close", "Oakfield Drive")
private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val ONES = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
private fun words(n: Int): String = when {
    n < 20 -> ONES[n]
    n < 100 -> TENS[n / 10] + if (n % 10 != 0) "-" + ONES[n % 10] else ""
    else -> ONES[n / 100] + " hundred" + if (n % 100 != 0) " and " + words(n % 100) else ""
}
private val ORD = listOf("", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth", "twentieth")
private fun ordinal(n: Int) = if (n <= 20) ORD[n] else if (n == 30) "thirtieth" else (if (n < 30) "twenty-" else "thirty-") + ORD[n % 10]
private fun ri(a: Int, b: Int) = Random.nextInt(a, b + 1)

class DictItem(val say: String, val prompt: String, val ans: List<String>)

fun dictItem(kind: String): DictItem = when (kind) {
    "name" -> { val n = DNAMES.random(); DictItem("My surname is $n. That's ${n.replace(" ", "").uppercase().toList().joinToString(", ")}.", "Surname:", listOf(n)) }
    "phone" -> { val d = "07" + (1..9).joinToString("") { ri(0, 9).toString() }; val s = d.map { if (it == '0') "oh" else ONES[it - '0'] }
        DictItem("The number is ${s.subList(0, 5).joinToString(" ")}, ${s.subList(5, 8).joinToString(" ")}, ${s.subList(8, 11).joinToString(" ")}.", "Phone number:", listOf(d, d.substring(0, 5) + " " + d.substring(5, 8) + " " + d.substring(8))) }
    "date" -> { val dd = ri(1, 28); val m = ri(0, 11); val mn = MONTHS[m]
        DictItem("The course starts on the ${ordinal(dd)} of $mn.", "Start date (day and month):", listOf("$dd $mn", "$mn $dd", "$dd ${mn.take(3)}", "$dd/${m + 1}")) }
    "time" -> { val h = ri(1, 11); val q = listOf(0, 15, 30, 45).random()
        val say = when (q) { 0 -> "${words(h)} o'clock"; 15 -> "quarter past ${words(h)}"; 30 -> "half past ${words(h)}"; else -> "quarter to ${words(h + 1)}" }
        val mm = q.toString().padStart(2, '0')
        DictItem("The meeting is at $say.", "Time:", listOf("$h:$mm", "$h.$mm") + if (q == 0) listOf("$h") else emptyList()) }
    "price" -> { val p = ri(5, 95); val c = listOf(0, 50, 25, 99).random(); val cc = c.toString().padStart(2, '0')
        DictItem("It costs ${words(p)} pounds${if (c > 0) " " + words(c) else ""}.", "Price (£):", if (c > 0) listOf("$p.$cc", "£$p.$cc") else listOf("$p", "£$p", "$p.00")) }
    "teen" -> { val n = listOf(13, 14, 15, 16, 17, 18, 19, 30, 40, 50, 60, 70, 80, 90).random(); DictItem("There are ${words(n)} people in the group.", "Number of people:", listOf("$n")) }
    "address" -> { val n = ri(2, 199); val s = DSTREETS.random(); DictItem("I live at ${words(n)}, $s.", "Address:", listOf("$n $s")) }
    else -> { val pc = listOf("BR", "SW", "NE", "LS", "CB").random() + ri(1, 9) + " " + ri(1, 9) + listOf("AB", "TN", "LP", "RX", "DW").random()
        DictItem("The postcode is ${pc.replace(" ", "").toList().joinToString(", ")}.", "Postcode:", listOf(pc, pc.replace(" ", ""))) }
}
fun dictSet(): List<DictItem> { val k = listOf("name", "phone", "date", "time", "price", "teen", "address", "postcode"); return (k + k.shuffled().take(2)).shuffled().map { dictItem(it) } }
private fun dnorm(s: String) = s.lowercase().replace(Regex("(\\d+)(st|nd|rd|th)\\b"), "$1").replace(Regex("[£,]"), "").replace(Regex("\\s+"), " ").trim().trimEnd('.')
fun dictCorrect(it: DictItem, a: String) = it.ans.any { dnorm(it) == dnorm(a) }

@Composable
fun DictScreen(st: Store, nav: Nav, act: MainActivity) {
    var items by remember { mutableStateOf(dictSet()) }
    var i by remember { mutableStateOf(0) }
    var okCount by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { act.tts?.stop() } }
    Screen(tr("Dictation", "الإملاء"), { nav.back() }) {
        Page {
            if (i >= items.size) {
                if (!saved) { saved = true; st.addStat("dict", "all", okCount, items.size); st.addHistory(tr("Dictation", "إملاء"), okCount, items.size, null) }
                NCard(bg = Lime) { Text(tr("Done", "انتهى"), style = H2); Text("$okCount / ${items.size}", style = H1); Text(tr("In the exam, spelling and numbers must be exact. Section 1 depends on them.", "في الامتحان يجب أن تكون التهجئة والأرقام دقيقة. الجزء الأول يعتمد عليها."), style = Body) }
                NButton(tr("Another round", "جولة أخرى")) { items = dictSet(); i = 0; okCount = 0; saved = false; checked = false; text = "" }
                return@Page
            }
            val it = items[i]
            Bar((i + 1f) / items.size, Sky); Text("${i + 1} / ${items.size}", style = Small)
            NCard(bg = Sky) {
                Text(tr("Listen and write exactly what you hear.", "استمع واكتب بالضبط ما تسمعه."), style = Label)
                Spacer(Modifier.height(8.dp))
                NButton(tr("Play", "شغّل"), bg = Lime) { act.tts?.let { t -> t.setSpeechRate(0.9f); t.setPitch(1f); t.speak(it.say, TextToSpeech.QUEUE_FLUSH, null, "d$i") } }
            }
            Text(it.prompt, style = H3.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
            BasicTextField(text, { if (!checked) text = it }, singleLine = true, modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(10.dp)).border(2.dp, Ink, RoundedCornerShape(10.dp)).padding(12.dp), textStyle = EnText, cursorBrush = SolidColor(Ink))
            if (!checked) NButton(tr("Check", "تحقق"), bg = Lime, enabled = text.isNotBlank()) { right = dictCorrect(it, text); if (right) okCount++; checked = true }
            else {
                NCard(bg = if (right) Lime else Coral) { Text(if (right) tr("Correct", "صحيح") else tr("Answer: ", "الصحيح: ") + it.ans[0], style = H3); Text("\"${it.say}\"", style = EnText.copy(fontSize = 14.sp)) }
                NButton(tr("Next", "التالي")) { i++; checked = false; text = "" }
            }
        }
    }
}
