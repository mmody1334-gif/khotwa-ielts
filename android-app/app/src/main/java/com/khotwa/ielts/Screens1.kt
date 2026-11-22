package com.khotwa.ielts

import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TaskRow(t: PlanTask, done: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(if (done) Green else Card, RoundedCornerShape(8.dp)).border(2.dp, Ink, RoundedCornerShape(8.dp)).clickable { onToggle() }, contentAlignment = Alignment.Center) {
            if (done) Text("✓", style = Label.copy(color = Paper))
        }
        Spacer(Modifier.width(10.dp))
        Text(t.label, style = Body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f).clickable { onOpen() })
        Text("Open", style = Label.copy(color = Green), modifier = Modifier.clickable { onOpen() }.padding(6.dp))
    }
}

fun planDay(st: Store): Int {
    if (st.start == 0) st.start = today()
    return (today() - st.start + 1).coerceIn(1, 28)
}

@Composable
fun HomeScreen(st: Store, nav: Nav) {
    val day = planDay(st)
    val plan = remember(st.type, st.goal, st.minutes, st.placed) { buildPlan(st) }
    val tasks = plan[day - 1]
    Page {
        Spacer(Modifier.height(8.dp))
        Text("Khotwa", style = H1)
        Text("A 28-day IELTS plan that lives on your phone.", style = Body)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag("${st.type}", Lime); Tag("Goal ${st.goal}", Mustard); Tag("${st.minutes} min a day", Sky); Tag("Streak ${st.streak()}", Coral)
        }
        if (!st.placed) {
            NCard(bg = Lime, onClick = { nav.go("quiz:placement") }) {
                Text("Start with the placement test", style = H2)
                Text("24 questions, 25 minutes. It shapes your plan around your weakest skill.", style = Body)
            }
        }
        NCard {
            Text("Today, day $day of 28", style = H2)
            Spacer(Modifier.height(6.dp))
            for ((i, t) in tasks.withIndex()) {
                val k = "d$day-$i"
                TaskRow(t, st.has("pdone", k), { nav.go(t.route()) }, { st.toggle("pdone", k) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NButton("Quick practice", Modifier.weight(1f), Sky) { nav.go("quiz:quick") }
            NButton("Mock tests", Modifier.weight(1f), Mustard) { nav.go("mocks") }
        }
        NCard(onClick = { nav.go("progress") }) { Text("See your progress", style = H3); Text("Scores, skills and recent sessions.", style = Small) }
    }
}

@Composable
fun PlanScreen(st: Store, nav: Nav) {
    val day = planDay(st)
    val plan = remember(st.type, st.goal, st.minutes, st.placed) { buildPlan(st) }
    var open by remember { mutableStateOf(day) }
    Screen("Your 28-day plan", null) {
        Page {
            Text("Focus skill: ${weakness(st).replaceFirstChar { it.uppercase() }}. Tap a day to see its tasks.", style = Body)
            for (w in 0 until 4) {
                Text("Week ${w + 1}", style = H3)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in (w * 7 + 1)..(w * 7 + 7)) {
                        val all = plan[d - 1].indices.all { st.has("pdone", "d$d-$it") }
                        Box(
                            Modifier.weight(1f).height(44.dp).background(if (all) Green else if (d == open) Lime else Card, RoundedCornerShape(10.dp))
                                .border(if (d == day) 3.dp else 2.dp, Ink, RoundedCornerShape(10.dp)).clickable { open = d },
                            contentAlignment = Alignment.Center
                        ) { Text("$d", style = Label.copy(color = if (all) Paper else Ink)) }
                    }
                }
            }
            NCard {
                Text("Day $open", style = H2)
                for ((i, t) in plan[open - 1].withIndex()) {
                    val k = "d$open-$i"
                    TaskRow(t, st.has("pdone", k), { nav.go(t.route()) }, { st.toggle("pdone", k) })
                }
            }
        }
    }
}

@Composable
fun LessonsScreen(st: Store, nav: Nav) {
    var f by remember { mutableStateOf("all") }
    Screen("Lessons", { nav.back() }) {
        Page {
            Row(Modifier.fillMaxWidth()) {
                Column {
                    Row { for (s in listOf("all", "foundation", "reading", "listening")) Chip(s.replaceFirstChar { it.uppercase() }, f == s) { f = s } }
                    Row { for (s in listOf("writing", "speaking")) Chip(s.replaceFirstChar { it.uppercase() }, f == s) { f = s } }
                }
            }
            for (l in Content.lessons.filter { f == "all" || it.skill == f }) {
                NCard(onClick = { nav.go("lesson:${l.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.title, style = H3, modifier = Modifier.weight(1f))
                        if (st.has("ldone", l.id)) Tag("Done", Lime)
                    }
                    Text(l.sub, style = Small); Text("${l.skill}  ·  ${l.time}", style = Small)
                }
            }
        }
    }
}

@Composable
fun LessonScreen(id: String, st: Store, nav: Nav) {
    val l = Content.lessons.first { it.id == id }
    Screen(l.title, { nav.back() }) {
        Page {
            Row { Tag(l.skill); Spacer(Modifier.width(8.dp)); Tag(l.time, Sky) }
            Text(l.sub, style = H3)
            Text(l.body, style = Body)
            if (l.example.isNotBlank()) NCard(bg = Lime) { Text("Example", style = H3); Text(l.example, style = Body) }
            if (l.action.isNotBlank()) NCard(bg = Mustard) { Text("Try this", style = H3); Text(l.action, style = Body) }
            NButton(if (st.has("ldone", l.id)) "Marked done ✓" else "Mark as done", bg = if (st.has("ldone", l.id)) Card else Lime) { st.toggle("ldone", l.id); if (st.has("ldone", l.id)) st.addDay() }
        }
    }
}

@Composable
fun PracticeScreen(st: Store, nav: Nav) {
    Screen("Practice", null) {
        Page {
            NCard(bg = Lime, onClick = { nav.go("quiz:quick") }) { Text("Quick practice", style = H2); Text("12 mixed questions, 15 minutes.", style = Body) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (sk in listOf("foundation", "reading", "listening")) NButton(sk.replaceFirstChar { it.uppercase() }, Modifier.weight(1f), Card) { nav.go("quiz:drill:$sk") }
            }
            Text("Reading passages", style = H2)
            for (s in Content.reading) NCard(onClick = { nav.go("paper:R:${s.id}") }) { Text(s.title, style = H3); Text("${s.sub}  ·  20 minutes", style = Small) }
            Text("Listening sections", style = H2)
            for (s in Content.listening) NCard(onClick = { nav.go("paper:L:${s.id}") }) { Text(s.title, style = H3); Text("Untimed", style = Small) }
            Text("Writing", style = H2)
            for (w in Content.writing.filter { it.type == st.type || it.type == "Both" }) NCard(onClick = { nav.go("write:${w.id}") }) { Text("Task ${w.task}: ${w.title}", style = H3); Text("${w.minutes} min, at least ${w.min} words", style = Small) }
            Text("Speaking", style = H2)
            for (s in Content.speaking) NCard(onClick = { nav.go("speak:${s.id}") }) { Text(s.topic, style = H3); Text("Parts 1, 2 and 3", style = Small) }
        }
    }
}

@Composable
fun MocksScreen(nav: Nav) {
    Screen("Mock tests", { nav.back() }) {
        Page {
            Text("Timed, one go. Listening audio plays once. Scores are indicative only.", style = Body)
            for (m in 1..2) {
                NCard {
                    Text("Mock $m", style = H2)
                    Spacer(Modifier.height(8.dp))
                    NButton("Reading, 60 min", Modifier.fillMaxWidth(), Lime) { nav.go("paper:R:mock$m") }
                    NButton("Listening, 35 min", Modifier.fillMaxWidth(), Sky) { nav.go("paper:L:mock$m") }
                }
            }
            NCard {
                Text("Writing and Speaking", style = H2)
                Spacer(Modifier.height(8.dp))
                NButton("Writing mock, 60 min", Modifier.fillMaxWidth(), Mustard) { nav.go("write:mock") }
                NButton("Speaking mock", Modifier.fillMaxWidth(), Coral) { nav.go("speak:mock") }
            }
        }
    }
}

@Composable
fun WordsScreen(st: Store, nav: Nav) {
    val words = Content.vocab
    val queue = remember(st.placed) { words.sortedWith(compareBy({ st.mapInt("vl", it.id.toString()) }, { it.id })).take(10) }
    var i by remember { mutableStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    Screen("Words", null) {
        Page {
            val counts = (0..4).map { l -> words.count { st.mapInt("vl", it.id.toString()) == l } }
            Text("Levels 0 to 4: ${counts.joinToString("  ")}", style = Small)
            Bar(words.count { st.mapInt("vl", it.id.toString()) >= 3 }.toFloat() / words.size)
            if (i >= queue.size) {
                NCard(bg = Lime) { Text("Session done", style = H2); Text("Come back tomorrow. Words you miss come back sooner.", style = Body) }
                NButton("Another round") { i = 0; flip = false }
            } else {
                val w = queue[i]
                Text("Card ${i + 1} of ${queue.size}", style = Small)
                NCard(bg = if (flip) Mustard else Card, onClick = { flip = !flip }) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(w.w, style = H1)
                            if (flip) { Spacer(Modifier.height(10.dp)); Text(w.def, style = H3); Spacer(Modifier.height(6.dp)); Text(w.ex, style = Body) }
                            else Text("Tap to flip", style = Small)
                        }
                    }
                }
                if (flip) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NButton("Again", Modifier.weight(1f), Coral) { st.putMap("vl", w.id.toString(), 0); st.addDay(); i++; flip = false }
                    NButton("Got it", Modifier.weight(1f), Lime) { st.putMap("vl", w.id.toString(), minOf(4, st.mapInt("vl", w.id.toString()) + 1)); st.addDay(); i++; flip = false }
                }
            }
        }
    }
}

@Composable
fun ProgressScreen(st: Store, nav: Nav) {
    Screen("Progress", { nav.back() }) {
        Page {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag("Streak ${st.streak()} days", Coral); Tag("${st.history().size} sessions", Sky) }
            NCard {
                Text("By skill", style = H2)
                for (sk in listOf("foundation", "reading", "listening")) {
                    val (c, t) = st.stat("sk", sk)
                    Spacer(Modifier.height(8.dp)); Text("${sk.replaceFirstChar { it.uppercase() }}: $c / $t", style = Label)
                    Bar(if (t == 0) 0f else c.toFloat() / t, Green)
                }
            }
            NCard {
                Text("By question type", style = H2)
                for ((k, n) in listOf("tfng" to "True / False / Not Given", "yn" to "Yes / No / Not Given", "sel" to "Matching headings", "mcq" to "Multiple choice", "gap" to "Gap fill")) {
                    val (c, t) = st.stat("ty", k)
                    Spacer(Modifier.height(8.dp)); Text("$n: $c / $t", style = Label)
                    Bar(if (t == 0) 0f else c.toFloat() / t, Plum)
                }
            }
            Text("Recent sessions", style = H2)
            if (st.history().isEmpty()) Text("Nothing yet. Finish a practice set and it appears here.", style = Body)
            for (h in st.history().take(15)) NCard {
                Text(h.optString("title"), style = H3)
                val b = if (h.isNull("band")) "" else "  ·  indicative band ${h.optDouble("band")}"
                Text("${h.optInt("score")} / ${h.optInt("total")}$b", style = Small)
            }
        }
    }
}

@Composable
fun AboutScreen(nav: Nav) {
    Screen("About and limits", { nav.back() }) {
        Page {
            NCard(bg = Lime) { Text("Honest limits", style = H2); Text("Khotwa is a study aid, not an official IELTS product. It is not connected to or approved by the IELTS partners.", style = Body) }
            NCard { Text("Original content", style = H3); Text("All passages, audio scripts, questions and prompts were written for this app. They follow IELTS formats but are not real exam papers.", style = Body) }
            NCard { Text("Band estimates", style = H3); Text("Reading and Listening bands are indicative only. They use a common score-to-band table and an uncalibrated question set, and need at least 30 questions. Real tests can differ by a full band or more.", style = Body) }
            NCard { Text("Writing and Speaking", style = H3); Text("The writing check counts words, paragraphs and linking words. It does not judge quality or give a band. Speaking recordings stay on your phone for you to replay and judge. For real feedback, ask a teacher.", style = Body) }
            NCard { Text("Listening audio", style = H3); Text("Audio is read aloud by your phone's text-to-speech voice. Accents and pace depend on your device.", style = Body) }
            NCard { Text("Your data", style = H3); Text("Everything stays on this device. The app needs no internet. The microphone is used only when you record a speaking answer. Check the official IELTS website for test rules and dates.", style = Body) }
        }
    }
}

@Composable
fun SettingsScreen(st: Store, nav: Nav) {
    val ctx = LocalContext.current
    var imp by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    Screen("Settings", { nav.back() }) {
        Page {
            NCard {
                Text("Test type", style = H3)
                Row { for (t in listOf("Academic", "General Training")) Chip(t, st.type == t) { st.type = t } }
                Text("Target band", style = H3)
                Row { for (g in listOf(5.0, 5.5, 6.0, 6.5, 7.0)) Chip("$g", st.goal == g) { st.goal = g } }
                Text("Minutes per day", style = H3)
                Row { for (m in listOf(15, 30, 45, 60)) Chip("$m", st.minutes == m) { st.minutes = m } }
            }
            NCard {
                Text("Backup", style = H3)
                Text("Export your data as text, or paste a backup to restore.", style = Small)
                Spacer(Modifier.height(6.dp))
                NButton("Export data") {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, st.export())
                    ctx.startActivity(Intent.createChooser(i, "Export"))
                }
                BasicTextField(imp, { imp = it }, Modifier.fillMaxWidth().heightIn(min = 80.dp).background(Paper, RoundedCornerShape(10.dp)).border(2.dp, Ink, RoundedCornerShape(10.dp)).padding(10.dp), textStyle = Body.copy(fontSize = 13.sp), cursorBrush = SolidColor(Ink))
                Spacer(Modifier.height(6.dp))
                NButton("Import pasted data", bg = Sky) { msg = if (st.import(imp)) "Imported." else "That is not valid backup text." }
                if (msg.isNotEmpty()) Text(msg, style = Label)
            }
            NCard {
                Text("Reset", style = H3)
                Text("Deletes progress, drafts and settings on this device.", style = Small)
                Spacer(Modifier.height(6.dp))
                if (!confirm) NButton("Reset everything", bg = Coral) { confirm = true }
                else Row { NButton("Yes, delete", bg = Coral) { st.reset(); confirm = false; nav.root("home") }; Spacer(Modifier.width(8.dp)); NButton("Cancel", bg = Card) { confirm = false } }
            }
        }
    }
}
