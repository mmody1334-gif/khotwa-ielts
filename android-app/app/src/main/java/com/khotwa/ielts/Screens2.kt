package com.khotwa.ielts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.speech.tts.TextToSpeech

@Composable
fun OptionRow(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).background(if (selected) Lime else Card, RoundedCornerShape(12.dp))
            .border(2.dp, Ink, RoundedCornerShape(12.dp)).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tag, style = Label.copy(color = Green), modifier = Modifier.width(26.dp))
        Text(text, style = Body.copy(fontSize = 15.sp))
    }
}

@Composable
fun QuizScreen(kind: String, arg: String, st: Store, nav: Nav) {
    val qs = remember { quizQuestions(kind, arg) }
    val ans = remember { mutableStateMapOf<Int, Int>() }
    var i by remember { mutableStateOf(0) }
    val total = when (kind) { "placement" -> 30 * 60; "quick" -> 15 * 60; "check" -> 20 * 60; else -> 12 * 60 }
    var done by remember { mutableStateOf(false) }
    fun finish() {
        if (done) return
        done = true
        val items = qs.mapIndexed { n, q ->
            val a = ans[n]
            RItem(q.q, a?.let { q.options.getOrNull(it) } ?: "No answer", q.options.getOrElse(q.answer) { "" }, a == q.answer, q.why, q.skill)
        }
        val score = items.count { it.ok }
        val per = mutableMapOf<String, Pair<Int, Int>>()
        for (sk in listOf("foundation", "reading", "listening")) {
            val sub = qs.indices.filter { qs[it].skill == sk }
            if (sub.isEmpty()) continue
            val c = sub.count { ans[it] == qs[it].answer }
            per[sk] = c to sub.size
            st.addStat("sk", sk, c, sub.size)
            if (kind == "placement") st.addStat("pl", sk, c, sub.size)
            else updateLevel(st, sk, c, sub.size, if (sk == "listening") 'L' else 'R')
        }
        qs.forEachIndexed { i, q -> if (kind != "placement" && ans[i] != q.answer) addMistake(st, "b${q.id}") }
        if (kind == "placement") applyPlacement(st, per["foundation"] ?: (0 to 0), per["reading"] ?: (0 to 0), per["listening"] ?: (0 to 0))
        else if (kind == "check") st.replanFuture()
        val title = when (kind) { "placement" -> tr("Placement test", "اختبار تحديد المستوى"); "quick" -> tr("Quick practice", "تدريب سريع"); "check" -> tr("Checkpoint test", "اختبار متابعة"); else -> tr("Drill: ", "تدريب: ") + skillName(arg) }
        st.addHistory(title, score, qs.size, null)
        val note = when (kind) {
            "placement" -> tr("Your starting levels are set and your program is ready.", "تم تحديد مستواك المبدئي وبرنامجك جاهز.")
            "check" -> tr("Your levels and the coming days of your plan were updated.", "تم تحديث مستوياتك وخطة الأيام القادمة.")
            else -> tr("Short sets give a rough picture only. No band is shown below 30 questions.", "المجموعات القصيرة تعطي صورة تقريبية فقط. لا تظهر درجة لأقل من 30 سؤالًا.")
        }
        nav.result = Result(title, score, qs.size, null, null, items, note, if (kind == "placement" || kind == "check") "program" else null)
        nav.replace("results")
    }
    val left = rememberCountdown(total) { finish() }
    if (qs.isEmpty()) { Screen(tr("Practice", "تدريب"), { nav.back() }) { Page { Text(tr("No questions found.", "لا توجد أسئلة."), style = Body) } }; return }
    val q = qs[i.coerceIn(0, qs.size - 1)]
    Screen(when (kind) { "placement" -> tr("Placement test", "اختبار المستوى"); "quick" -> tr("Quick practice", "تدريب سريع"); "check" -> tr("Checkpoint", "متابعة"); else -> tr("Drill", "تدريب") }, { nav.back() }, { TimerPill(left) }) {
        Page {
            Bar((i + 1).toFloat() / qs.size, Sky)
            Text(tr("Question ${i + 1} of ${qs.size}", "السؤال ${i + 1} من ${qs.size}") + "  ·  ${skillName(q.skill)}", style = Small)
            if (q.ctx.isNotBlank()) NCard(bg = Paper) { Text(q.ctx, style = Body) }
            Text(q.q, style = H3)
            for ((k, o) in q.options.withIndex()) OptionRow(o, ans[i] == k, "ABCD"[k % 4].toString()) { ans[i] = k }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (i > 0) NButton(tr("Back", "السابق"), bg = Card) { i-- }
                if (i < qs.size - 1) NButton(tr("Next", "التالي")) { i++ } else NButton(tr("Finish", "إنهاء"), bg = Mustard) { finish() }
            }
        }
    }
}

@Composable
fun QuestionView(g: Group, q: Q, key: String, ans: SnapshotStateMap<String, String>) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("${q.n}. ${q.q}", style = Body.copy(fontSize = 15.sp, lineHeight = 21.sp))
        Spacer(Modifier.height(4.dp))
        if (q.t == "gap") {
            BasicTextField(
                ans[key] ?: "", { ans[key] = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(10.dp)).border(2.dp, Ink, RoundedCornerShape(10.dp)).padding(10.dp),
                textStyle = Body, cursorBrush = SolidColor(Ink)
            )
        } else {
            for ((k, o) in optionsOf(g, q).withIndex()) OptionRow(o, ans[key] == k.toString(), if (q.t == "mcq") "ABCD"[k % 4].toString() else "") { ans[key] = k.toString() }
        }
    }
}

@Composable
fun PaperScreen(kind: Char, id: String, st: Store, nav: Nav, act: MainActivity) {
    val mockN = if (id.startsWith("mock")) id.removePrefix("mock").toInt() else 0
    val secs = remember { if (mockN > 0) (if (kind == 'R') Content.reading else Content.listening).filter { it.mock == mockN } else listOf(Content.section(id)) }
    val ans = remember { mutableStateMapOf<String, String>() }
    var tab by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }
    var played by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    val minutes = if (kind == 'R') (if (mockN > 0) 60 else 20) else (if (mockN > 0) 35 else 0)
    DisposableEffect(Unit) { onDispose { act.stopAudio() } }
    fun finish() {
        if (done) return
        done = true; act.stopAudio()
        val items = mutableListOf<RItem>()
        for (s in secs) for (g in s.groups) for (q in g.qs) {
            val a = ans["${s.id}|${q.n}"]
            val ok = isCorrect(q, a)
            val yours = if (a == null || a.isBlank()) "No answer" else if (q.t == "gap") a else optionsOf(g, q).getOrElse(a.toInt()) { a }
            items.add(RItem("${q.n}. ${q.q}", yours, correctText(g, q), ok, q.w, q.t))
            st.addStat("ty", q.t, if (ok) 1 else 0, 1)
            st.addStat("sk", if (kind == 'R') "reading" else "listening", if (ok) 1 else 0, 1)
            if (!ok && !a.isNullOrBlank()) addMistake(st, "s${s.id}|${q.n}")
        }
        val score = items.count { it.ok }
        val b = band(score, items.size, kind)
        for (x in secs) st.bump("secn", x.id)
        if (ans.isNotEmpty()) { updateLevel(st, if (kind == 'R') "reading" else "listening", score, items.size, kind); if (mockN > 0) st.replanFuture() }
        val title = (if (kind == 'R') tr("Reading ", "قراءة ") else tr("Listening ", "استماع ")) + (if (mockN > 0) tr("Mock $mockN", "محاكاة $mockN") else secs[0].title)
        st.addHistory(title, score, items.size, b)
        val note = if (kind == 'L') "Transcript:\n" + secs.joinToString("\n\n") { s -> s.script.joinToString("\n") { "${it.first}: ${it.second}" } } else ""
        nav.result = Result(title, score, items.size, b, kind, items, note, if (mockN > 0) "program" else null)
        nav.replace("results")
    }
    val left = rememberCountdown(if (minutes > 0) minutes * 60 else 1, minutes > 0) { if (minutes > 0) finish() }
    val s = secs[tab.coerceIn(0, secs.size - 1)]
    Screen(if (kind == 'R') tr("Reading", "القراءة") else tr("Listening", "الاستماع"), { nav.back() }, { if (minutes > 0) TimerPill(left) }) {
        Page {
            if (secs.size > 1) Row { for ((k, x) in secs.withIndex()) Chip(if (kind == 'R') tr("Passage ${k + 1}", "القطعة ${k + 1}") else tr("Section ${k + 1}", "الجزء ${k + 1}"), tab == k) { tab = k } }
            Text(s.title, style = H2)
            if (kind == 'R') {
                Text(s.sub, style = Small)
                for ((l, t) in s.paras) Row { if (l.isNotBlank()) Text(l, style = H3.copy(color = Green), modifier = Modifier.width(24.dp)); Text(t, style = Body) }
            } else {
                Text(s.sub, style = Body)
                NCard(bg = Sky) {
                    Text(if (mockN > 0) tr("Audio plays once.", "التسجيل يُشغَّل مرة واحدة.") else tr("Audio can be replayed.", "يمكن إعادة التسجيل."), style = Label)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        NButton(if (speaking) tr("Playing...", "يعمل...") else if (played && mockN > 0) tr("Played", "تم التشغيل") else tr("Play audio", "شغّل الصوت"), bg = Lime, enabled = !speaking && !(played && mockN > 0)) {
                            if (act.playAsset(s.id)) { speaking = true; played = true; return@NButton }
                            val t = act.tts
                            if (t == null || !act.ttsReady) return@NButton
                            speaking = true; played = true
                            t.setSpeechRate(0.92f)
                            val speakers = s.script.map { it.first }.distinct()
                            val pitches = listOf(1.0f, 0.82f, 1.18f, 0.92f)
                            s.script.forEachIndexed { n, (sp, line) ->
                                t.setPitch(pitches[speakers.indexOf(sp).coerceAtLeast(0) % pitches.size])
                                t.speak(line, TextToSpeech.QUEUE_ADD, null, "u$n")
                                t.playSilentUtterance(350, TextToSpeech.QUEUE_ADD, "s$n")
                            }
                            t.setPitch(1.0f)
                        }
                        Spacer(Modifier.width(8.dp))
                        if (mockN == 0) NButton(tr("Stop", "إيقاف"), bg = Card, enabled = speaking) { act.stopAudio(); speaking = false }
                    }
                    if (!act.ttsReady || !act.ttsEnglish) {
                        Text(tr("No English voice is ready on this phone. Install English (UK) in voice settings.", "لا يوجد صوت إنجليزي جاهز في هاتفك. ثبّت English (UK) من إعدادات الصوت."), style = Small.copy(color = Ink))
                        NButton(tr("Voice settings", "إعدادات الصوت"), bg = Card) { openTtsSettings(act) }
                    }
                }
                LaunchedEffect(speaking) {
                    while (speaking) { kotlinx.coroutines.delay(600); if (!act.audioBusy()) { kotlinx.coroutines.delay(600); if (!act.audioBusy()) speaking = false } }
                }
            }
            for (g in s.groups) {
                NCard {
                    Text(g.ins, style = H3)
                    if (g.form != null) Text(g.form, style = Label.copy(color = Green))
                    if (g.opts.isNotEmpty() && g.qs.firstOrNull()?.t == "sel") { Spacer(Modifier.height(4.dp)); for (o in g.opts) Text(o, style = Small.copy(color = Ink)) }
                    for (q in g.qs) QuestionView(g, q, "${s.id}|${q.n}", ans)
                }
            }
            Row {
                if (secs.size > 1 && tab < secs.size - 1) NButton(tr("Next section", "الجزء التالي"), bg = Card) { tab++ }
                Spacer(Modifier.width(8.dp))
                NButton(tr("Submit answers", "سلّم الإجابات"), bg = Mustard) { finish() }
            }
            Text(tr("${ans.size} answered", "تمت الإجابة عن ${ans.size}"), style = Small)
        }
    }
}

@Composable
fun ResultsScreen(st: Store, nav: Nav) {
    val r = nav.result
    Screen(tr("Results", "النتيجة"), { nav.root("home") }) {
        if (r == null) { Page { Text(tr("No result to show.", "لا توجد نتيجة."), style = Body) }; return@Screen }
        Page {
            NCard(bg = Lime) {
                Text(r.title, style = H3)
                Text("${r.score} / ${r.total}", style = H1.copy(fontSize = 44.sp, lineHeight = 48.sp))
                if (r.band != null) Text(tr("Indicative band ${r.band}", "درجة تقريبية ${r.band}"), style = H2) else if (r.bandKind != null) Text(tr("Too few questions for a band estimate.", "عدد الأسئلة أقل من أن يعطي درجة."), style = Small)
                if (r.note.isNotBlank() && r.bandKind != 'L') Text(r.note, style = Small.copy(color = Ink))
            }
            r.next?.let { nx -> NButton(tr("See my program", "شاهد برنامجي"), Modifier.fillMaxWidth(), Mustard) { nav.root("home"); nav.go(nx) } }
            if (r.band != null) Text(tr("This band is a rough guide from an uncalibrated question set. See About for limits.", "هذه الدرجة تقريبية من مجموعة أسئلة غير معايرة. راجع صفحة عن التطبيق."), style = Small)
            for (it in r.items) NCard(bg = if (it.ok) Card else Paper) {
                Row { Tag(if (it.ok) tr("Correct", "صحيح") else tr("Wrong", "خطأ"), if (it.ok) Lime else Coral) }
                Spacer(Modifier.height(6.dp))
                Text(it.q, style = Body.copy(fontSize = 15.sp))
                Text(tr("You: ", "إجابتك: ") + it.yours, style = Label)
                if (!it.ok) Text(tr("Answer: ", "الصحيح: ") + it.correct, style = Label.copy(color = Green))
                if (it.why.isNotBlank()) Text(it.why, style = Small)
            }
            if (r.bandKind == 'L' && r.note.isNotBlank()) NCard(bg = Sky) { Text(r.note, style = Body.copy(fontSize = 14.sp)) }
            NButton(tr("Back to home", "العودة للرئيسية")) { nav.root("home") }
        }
    }
}
