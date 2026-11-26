package com.khotwa.ielts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------- class membership (same server and data shape as the web app)

/** What the teacher sees: levels, plan, recent sessions. */
fun syncData(st: Store): JSONObject {
    val est = JSONObject(); for (k in SKILLS + "foundation") st.est(k)?.let { est.put(k, it) }
    val hs = st.history(); val wk = today() - 7
    val recent = JSONArray(); hs.take(8).forEach { recent.put(JSONObject().put("t", it.optString("title")).put("s", it.optInt("score")).put("n", it.optInt("total")).put("d", it.optInt("day"))) }
    val done = JSONArray(); st.mapStr("cls", "done").split(",").filter { it.isNotBlank() }.forEach { done.put(it.toInt()) }
    return JSONObject().put("overall", currentOverall(st)).put("est", est).put("goal", st.goal).put("type", st.type).put("placed", st.placed)
        .put("minutes", st.minutes).put("streak", st.streak()).put("exam", st.exam).put("week", hs.count { it.optInt("day") > wk })
        .put("recent", recent).put("mistakes", mistakes(st).size).put("done", done)
}

suspend fun syncNow(st: Store): String? {
    val id = st.mapStr("cls", "id"); if (id.isBlank()) return null
    val o = httpJson("$SITE/api/sync", JSONObject().put("id", id).put("token", st.mapStr("cls", "token")).put("data", syncData(st)))
        ?: return tr("No internet connection.", "لا يوجد اتصال بالإنترنت.")
    if (o.has("error")) { if (o.optString("error").contains("Not in this class")) st.resetKey("cls"); return o.optString("error") }
    st.putMap("cls", "className", o.optString("className")); st.putMap("cls", "assign", (o.optJSONArray("assignments") ?: JSONArray()).toString())
    st.putMap("cls", "syncAt", System.currentTimeMillis().toString())
    return null
}

@Composable
fun AssignmentsCard(st: Store, nav: Nav) {
    if (st.mapStr("cls", "id").isBlank()) return
    val hist = st.history().size
    LaunchedEffect(hist) {
        // sync shortly after each finished session, and at most hourly otherwise
        val last = st.mapStr("cls", "syncAt").toLongOrNull() ?: 0L
        if (hist != st.mapInt("cls", "histAt") || System.currentTimeMillis() - last > 3600_000) { st.putMap("cls", "histAt", hist); syncNow(st) }
    }
    val done = st.mapStr("cls", "done").split(",").filter { it.isNotBlank() }
    val list = try { JSONArray(st.mapStr("cls", "assign").ifBlank { "[]" }) } catch (e: Exception) { JSONArray() }
    val open = (0 until list.length()).map { list.getJSONObject(it) }.filter { it.optInt("id").toString() !in done }
    if (open.isEmpty()) return
    NCard(bg = Mustard) {
        Text(tr("From your teacher", "من معلمتك") + " · " + st.mapStr("cls", "className"), style = H3)
        for (a in open) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Box(Modifier.size(26.dp).background(Card, RoundedCornerShape(8.dp)).border(2.dp, Ink, RoundedCornerShape(8.dp)).clickable { st.putMap("cls", "done", (done + a.optInt("id").toString()).joinToString(",")) })
            Spacer(Modifier.width(10.dp))
            Text(a.optString("title"), style = Body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f).clickable { nav.go(a.optString("route").replace("/", ":")) })
        }
    }
}

@Composable
fun ClassCard(st: Store) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    NCard {
        if (st.mapStr("cls", "id").isNotBlank()) {
            Text(tr("My class", "فصلي"), style = H3)
            Text(st.mapStr("cls", "className") + " · " + st.mapStr("cls", "name"), style = Body)
            Text(tr("Your levels and recent sessions are shared with your teacher.", "مستوياتك وجلساتك الأخيرة تُشارك مع معلمتك."), style = Small)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NButton(tr("Sync now", "زامن الآن"), bg = Card) { scope.launch { msg = syncNow(st) ?: tr("Synced.", "تمت المزامنة.") } }
                NButton(tr("Leave class", "غادر الفصل"), bg = Coral) { st.resetKey("cls") }
            }
        } else {
            Text(tr("Join a class", "انضم إلى فصل"), style = H3)
            Text(tr("If your teacher gave you a class code, join to receive tasks and share your progress.", "إذا أعطتك معلمتك كود فصل، انضم لتصلك المهام وتشارك تقدّمك."), style = Small)
            for ((label, v, set) in listOf(Triple(tr("Class code", "كود الفصل"), code, { s: String -> code = s.uppercase().take(8) }), Triple(tr("Your name", "اسمك"), name, { s: String -> name = s.take(40) }))) {
                Text(label, style = Label)
                BasicTextField(v, set, singleLine = true, modifier = Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(10.dp)).border(2.dp, Ink, RoundedCornerShape(10.dp)).padding(10.dp), textStyle = Body, cursorBrush = SolidColor(Ink))
            }
            NButton(tr("Join", "انضم"), bg = Lime, enabled = code.length >= 4 && name.isNotBlank()) {
                scope.launch {
                    val o = httpJson("$SITE/api/join", JSONObject().put("code", code).put("name", name))
                    if (o == null) { msg = tr("No internet connection.", "لا يوجد اتصال بالإنترنت."); return@launch }
                    if (o.has("error")) { msg = o.optString("error"); return@launch }
                    st.putMap("cls", "id", o.optString("id")); st.putMap("cls", "token", o.optString("token")); st.putMap("cls", "code", o.optString("code"))
                    st.putMap("cls", "className", o.optString("className")); st.putMap("cls", "name", name)
                    msg = tr("Joined ", "انضممت إلى ") + o.optString("className"); syncNow(st)
                }
            }
            Text(tr("I am a teacher (opens the teacher page)", "أنا معلم/ة (يفتح صفحة المعلم)"), style = Label.copy(color = Green),
                modifier = Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$SITE/#teacher"))) }.padding(vertical = 6.dp))
        }
        if (msg.isNotEmpty()) Text(msg, style = Label)
    }
}

// ---------------------------------------------------------------- AI feedback

@Composable
fun AiFeedback(kind: String, prompt: String, text: String, task: Int, min: Int, onBand: (Double) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember(text.length / 50) { mutableStateOf(false) }
    var fb by remember { mutableStateOf<JSONObject?>(null) }
    val words = textStats(text).words
    NButton(if (loading) tr("The AI is reading...", "الذكاء الاصطناعي يقرأ...") else if (kind == "writing") tr("AI feedback (needs internet)", "ملاحظات الذكاء الاصطناعي (يحتاج إنترنت)") else tr("AI feedback on what you said", "ملاحظات الذكاء الاصطناعي على كلامك"),
        bg = Coral, enabled = !loading && words >= 30) {
        loading = true
        scope.launch {
            val o = httpJson("$SITE/api/feedback", JSONObject().put("kind", kind).put("prompt", prompt).put("text", text).put("task", task).put("min", min).put("lang", if (I18n.ar) "ar" else "en"), 90000)
            fb = o ?: JSONObject().put("error", tr("No internet connection.", "لا يوجد اتصال بالإنترنت."))
            if (o != null && !o.has("error") && o.has("band")) onBand(o.optDouble("band"))
            loading = false
        }
    }
    if (words < 30) Text(tr("Needs at least 30 words.", "يحتاج 30 كلمة على الأقل."), style = Small)
    fb?.let { o ->
        if (o.has("error")) { NCard(bg = Coral) { Text(o.optString("error"), style = Body) }; return@let }
        NCard(bg = Lime) {
            Text(tr("AI feedback", "ملاحظات الذكاء الاصطناعي") + " · " + tr("about ", "حوالي ") + o.optDouble("band"), style = H3)
            val cr = o.optJSONArray("criteria") ?: JSONArray()
            for (i in 0 until cr.length()) { val c = cr.getJSONObject(i); Text("${c.optString("name")}: ${c.optDouble("band")}", style = Label); Text(c.optString("comment"), style = Body.copy(fontSize = 14.sp)) }
            for ((key, title) in listOf("strengths" to tr("Strengths", "نقاط القوة"), "improve" to tr("To improve", "للتحسين"))) {
                val a = o.optJSONArray(key) ?: continue
                if (a.length() > 0) { Spacer(Modifier.height(6.dp)); Text(title, style = Label); for (i in 0 until a.length()) Text("• " + a.optString(i), style = Body.copy(fontSize = 14.sp)) }
            }
            val co = o.optJSONArray("corrections") ?: JSONArray()
            if (co.length() > 0) { Spacer(Modifier.height(6.dp)); Text(tr("Corrections", "تصحيحات"), style = Label) }
            for (i in 0 until co.length()) { val c = co.getJSONObject(i); Text("${c.optString("wrong")}  →  ${c.optString("better")}", style = EnText.copy(fontSize = 14.sp)); Text(c.optString("why"), style = Small.copy(color = Ink)) }
            Text(tr("A practice estimate by AI, not an official score. It can be wrong; ask your teacher when in doubt.", "تقدير تدريبي من الذكاء الاصطناعي وليس درجة رسمية. قد يخطئ؛ اسأل معلمك عند الشك."), style = Small.copy(color = Ink))
        }
    }
}
