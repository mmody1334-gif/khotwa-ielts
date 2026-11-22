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
    val total = when (kind) { "placement" -> 25 * 60; "quick" -> 15 * 60; else -> 12 * 60 }
    var done by remember { mutableStateOf(false) }
    fun finish() {
        if (done) return
        done = true
        val items = qs.mapIndexed { n, q ->
            val a = ans[n]
            RItem(q.q, a?.let { q.options.getOrNull(it) } ?: "No answer", q.options.getOrElse(q.answer) { "" }, a == q.answer, q.why, q.skill)
        }
        val score = items.count { it.ok }
        for (sk in listOf("foundation", "reading", "listening")) {
            val sub = qs.indices.filter { qs[it].skill == sk }
            if (sub.isEmpty()) continue
            val c = sub.count { ans[it] == qs[it].answer }
            st.addStat("sk", sk, c, sub.size)
            if (kind == "placement") st.addStat("pl", sk, c, sub.size)
        }
        if (kind == "placement") st.placed = true
        val title = when (kind) { "placement" -> "Placement test"; "quick" -> "Quick practice"; else -> "Drill: $arg" }
        st.addHistory(title, score, qs.size, null)
        nav.result = Result(title, score, qs.size, null, null, items, "Short sets give a rough picture only. No band is shown below 30 questions.")
        nav.replace("results")
    }
    val left = rememberCountdown(total) { finish() }
    val q = qs[i]
    Screen(when (kind) { "placement" -> "Placement test"; "quick" -> "Quick practice"; else -> "Drill" }, { nav.back() }, { TimerPill(left) }) {
        Page {
            Bar((i + 1).toFloat() / qs.size, Sky)
            Text("Question ${i + 1} of ${qs.size}  ·  ${q.skill}", style = Small)
            if (q.ctx.isNotBlank()) NCard(bg = Paper) { Text(q.ctx, style = Body) }
            Text(q.q, style = H3)
            for ((k, o) in q.options.withIndex()) OptionRow(o, ans[i] == k, "ABCD"[k % 4].toString()) { ans[i] = k }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (i > 0) NButton("Back", bg = Card) { i-- }
                if (i < qs.size - 1) NButton("Next") { i++ } else NButton("Finish", bg = Mustard) { finish() }
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
    DisposableEffect(Unit) { onDispose { act.tts?.stop() } }
    fun finish() {
        if (done) return
        done = true; act.tts?.stop()
        val items = mutableListOf<RItem>()
        for (s in secs) for (g in s.groups) for (q in g.qs) {
            val a = ans["${s.id}|${q.n}"]
            val ok = isCorrect(q, a)
            val yours = if (a == null || a.isBlank()) "No answer" else if (q.t == "gap") a else optionsOf(g, q).getOrElse(a.toInt()) { a }
            items.add(RItem("${q.n}. ${q.q}", yours, correctText(g, q), ok, q.w, q.t))
            st.addStat("ty", q.t, if (ok) 1 else 0, 1)
            st.addStat("sk", if (kind == 'R') "reading" else "listening", if (ok) 1 else 0, 1)
        }
        val score = items.count { it.ok }
        val b = band(score, items.size, kind)
        val title = (if (kind == 'R') "Reading " else "Listening ") + (if (mockN > 0) "Mock $mockN" else secs[0].title)
        st.addHistory(title, score, items.size, b)
        val note = if (kind == 'L') "Transcript:\n" + secs.joinToString("\n\n") { s -> s.script.joinToString("\n") { "${it.first}: ${it.second}" } } else ""
        nav.result = Result(title, score, items.size, b, kind, items, note)
        nav.replace("results")
    }
    val left = rememberCountdown(if (minutes > 0) minutes * 60 else 1, minutes > 0) { if (minutes > 0) finish() }
    val s = secs[tab.coerceIn(0, secs.size - 1)]
    Screen(if (kind == 'R') "Reading" else "Listening", { nav.back() }, { if (minutes > 0) TimerPill(left) }) {
        Page {
            if (secs.size > 1) Row { for ((k, x) in secs.withIndex()) Chip(if (kind == 'R') "Passage ${k + 1}" else "Section ${k + 1}", tab == k) { tab = k } }
            Text(s.title, style = H2)
            if (kind == 'R') {
                Text(s.sub, style = Small)
                for ((l, t) in s.paras) Row { if (l.isNotBlank()) Text(l, style = H3.copy(color = Green), modifier = Modifier.width(24.dp)); Text(t, style = Body) }
            } else {
                Text(s.sub, style = Body)
                NCard(bg = Sky) {
                    Text(if (mockN > 0) "Audio plays once." else "Audio can be replayed.", style = Label)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        NButton(if (speaking) "Playing..." else if (played && mockN > 0) "Played" else "Play audio", bg = Lime, enabled = !speaking && !(played && mockN > 0)) {
                            val t = act.tts
                            if (t == null || !act.ttsReady) return@NButton
                            speaking = true; played = true
                            t.setSpeechRate(0.92f)
                            s.script.forEachIndexed { n, (sp, line) ->
                                t.speak(line, TextToSpeech.QUEUE_ADD, null, "u$n")
                                t.playSilentUtterance(350, TextToSpeech.QUEUE_ADD, "s$n")
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        if (mockN == 0) NButton("Stop", bg = Card, enabled = speaking) { act.tts?.stop(); speaking = false }
                    }
                    if (!act.ttsReady) Text("Text-to-speech is not ready on this device yet.", style = Small)
                }
                LaunchedEffect(speaking) {
                    while (speaking) { kotlinx.coroutines.delay(600); if (act.tts?.isSpeaking != true) { kotlinx.coroutines.delay(600); if (act.tts?.isSpeaking != true) speaking = false } }
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
                if (secs.size > 1 && tab < secs.size - 1) NButton("Next section", bg = Card) { tab++ }
                Spacer(Modifier.width(8.dp))
                NButton("Submit answers", bg = Mustard) { finish() }
            }
            Text("${ans.size} answered", style = Small)
        }
    }
}

@Composable
fun ResultsScreen(st: Store, nav: Nav) {
    val r = nav.result
    Screen("Results", { nav.root("home") }) {
        if (r == null) { Page { Text("No result to show.", style = Body) }; return@Screen }
        Page {
            NCard(bg = Lime) {
                Text(r.title, style = H3)
                Text("${r.score} / ${r.total}", style = H1.copy(fontSize = 44.sp, lineHeight = 48.sp))
                if (r.band != null) Text("Indicative band ${r.band}", style = H2) else if (r.bandKind != null) Text("Too few questions for a band estimate.", style = Small)
                if (r.note.isNotBlank() && r.bandKind != 'L') Text(r.note, style = Small.copy(color = Ink))
            }
            if (r.band != null) Text("This band is a rough guide from an uncalibrated question set. See About for limits.", style = Small)
            for (it in r.items) NCard(bg = if (it.ok) Card else Paper) {
                Row { Tag(if (it.ok) "Correct" else "Wrong", if (it.ok) Lime else Coral) }
                Spacer(Modifier.height(6.dp))
                Text(it.q, style = Body.copy(fontSize = 15.sp))
                Text("You: ${it.yours}", style = Label)
                if (!it.ok) Text("Answer: ${it.correct}", style = Label.copy(color = Green))
                if (it.why.isNotBlank()) Text(it.why, style = Small)
            }
            if (r.bandKind == 'L' && r.note.isNotBlank()) NCard(bg = Sky) { Text(r.note, style = Body.copy(fontSize = 14.sp)) }
            NButton("Back to home") { nav.root("home") }
        }
    }
}
