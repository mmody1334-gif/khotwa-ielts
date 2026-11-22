package com.khotwa.ielts

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskRow(t: PlanTask, done: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(if (done) Green else Card, RoundedCornerShape(8.dp)).border(2.dp, Ink, RoundedCornerShape(8.dp)).clickable { onToggle() }, contentAlignment = Alignment.Center) {
            if (done) Text("✓", style = Label.copy(color = Paper))
        }
        Spacer(Modifier.width(10.dp))
        Text(t.label(), style = Body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f).clickable { onOpen() })
        Text(tr("Open", "افتح"), style = Label.copy(color = Green), modifier = Modifier.clickable { onOpen() }.padding(6.dp))
    }
}

fun typeName(t: String) = if (t == "Academic") tr("Academic", "أكاديمي") else tr("General Training", "عام")

fun doneKey(day: Int, t: PlanTask) = "d$day-${t.code()}"

@Composable
fun rememberPlan(st: Store): Pair<Program, List<List<PlanTask>>> {
    val pr = program(st)
    val plan = remember(st.type, st.goal, st.minutes, st.placed, pr.current, pr.days, st.start, I18n.ar) { generatePlan(st, pr.days) }
    return pr to plan
}

fun dateIn(days: Int): String {
    val f = SimpleDateFormat("d MMM yyyy", if (I18n.ar) Locale("ar") else Locale.UK)
    return f.format(Date(System.currentTimeMillis() + days * 86400000L))
}

@Composable
fun HomeScreen(st: Store, nav: Nav) {
    val (pr, plan) = rememberPlan(st)
    val day = programDay(st)
    val tasks = tasksFor(st, day, plan)
    Page {
        Spacer(Modifier.height(8.dp))
        Text("Khotwa", style = H1)
        Text(tr("A personal IELTS program that adapts to your results.", "برنامج آيلتس شخصي يتكيّف مع نتائجك."), style = Body)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(typeName(st.type), Lime); Tag(tr("Goal ", "الهدف ") + st.goal, Mustard); Tag(tr("${st.minutes} min", "${st.minutes} دقيقة"), Sky); Tag(tr("Streak ", "متتالية ") + st.streak(), Coral)
        }
        if (!st.placed) {
            NCard(bg = Lime, onClick = { nav.go("quiz:placement") }) {
                Text(tr("Start with the placement test", "ابدأ باختبار تحديد المستوى"), style = H2)
                Text(tr("${Content.bank.count { it.placement }} questions, 30 minutes. It finds your level and builds a program up to your target.",
                    "${Content.bank.count { it.placement }} سؤال في 30 دقيقة. يحدد مستواك ويبني برنامجًا حتى تصل لهدفك."), style = Body)
            }
        } else {
            NCard(bg = Mustard, onClick = { nav.go("program") }) {
                Text(tr("Now about ${pr.current}  →  target ${pr.target}", "الآن تقريبًا ${pr.current}  ←  الهدف ${pr.target}"), style = H2)
                val ph = phaseOf(minOf(day, pr.days), pr.days)
                Text(tr("Week ${(minOf(day, pr.days) + 6) / 7} of ${pr.weeks} · ", "الأسبوع ${(minOf(day, pr.days) + 6) / 7} من ${pr.weeks} · ") + phaseName(ph), style = Label)
                Text(tr("Tap to see your program and what to improve.", "اضغط لرؤية برنامجك وما تحتاج لتحسينه."), style = Small.copy(color = Ink))
            }
        }
        NCard {
            Text(if (day > pr.days) tr("Extra day $day", "يوم إضافي $day") else tr("Today, day $day of ${pr.days}", "اليوم، يوم $day من ${pr.days}"), style = H2)
            Spacer(Modifier.height(6.dp))
            for (t in tasks) TaskRow(t, st.has("pdone", doneKey(day, t)), { nav.go(t.route()) }, { st.toggle("pdone", doneKey(day, t)) })
            if (day > pr.days) Text(tr("Your program time is over. Retake the placement test or raise your target in My program.", "انتهت مدة برنامجك. أعد اختبار المستوى أو ارفع هدفك من صفحة برنامجي."), style = Small)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NButton(tr("Quick practice", "تدريب سريع"), Modifier.weight(1f), Sky) { nav.go("quiz:quick") }
            NButton(tr("Mock tests", "المحاكاة"), Modifier.weight(1f), Mustard) { nav.go("mocks") }
        }
        NCard(onClick = { nav.go("progress") }) { Text(tr("See your progress", "شاهد تقدّمك"), style = H3); Text(tr("Scores, skills and recent sessions.", "الدرجات والمهارات والجلسات الأخيرة."), style = Small) }
    }
}

@Composable
fun ProgramScreen(st: Store, nav: Nav) {
    val (pr, _) = rememberPlan(st)
    val day = programDay(st)
    var confirm by remember { mutableStateOf(false) }
    Screen(tr("My program", "برنامجي"), { nav.back() }) {
        Page {
            if (!st.placed) {
                NCard(bg = Lime, onClick = { nav.go("quiz:placement") }) {
                    Text(tr("Take the placement test first", "ابدأ باختبار تحديد المستوى أولًا"), style = H2)
                    Text(tr("Your program is built from your level in each skill and your target band. Until then you see a 4-week starter plan.", "يُبنى برنامجك من مستواك في كل مهارة والدرجة المستهدفة. حتى ذلك الحين تظهر خطة بداية لمدة 4 أسابيع."), style = Body)
                }
            }
            NCard(bg = Lime) {
                Text(tr("Estimated level", "المستوى التقريبي"), style = H3)
                Text("${pr.current}  ${if (I18n.ar) "←" else "→"}  ${pr.target}", style = H1.copy(fontSize = 40.sp, lineHeight = 44.sp))
                if (pr.gap <= 0.0) {
                    Text(tr("You are at or above your target estimate. The program keeps you exam-ready for 4 weeks. You can raise the target in Settings.",
                        "مستواك التقريبي عند هدفك أو أعلى. البرنامج يحافظ على جاهزيتك 4 أسابيع، ويمكنك رفع الهدف من الإعدادات."), style = Body)
                } else {
                    Text(tr("Gap: ${pr.gap} band. Program: about ${pr.weeks} weeks (${pr.hours.toInt()} study hours at ${st.minutes} min a day, 6 days a week).",
                        "الفرق: ${pr.gap} درجة. البرنامج: حوالي ${pr.weeks} أسبوع (${pr.hours.toInt()} ساعة مذاكرة بمعدل ${st.minutes} دقيقة يوميًا، 6 أيام في الأسبوع)."), style = Body)
                    Text(tr("Expected finish: ", "الانتهاء المتوقع: ") + dateIn(pr.days - day), style = Label)
                    if (pr.weeks >= 26) Text(tr("That is long. More minutes a day shortens it a lot.", "المدة طويلة. زيادة الدقائق اليومية تختصرها كثيرًا."), style = Small.copy(color = Ink))
                }
            }
            NCard {
                Text(tr("Your skills", "مهاراتك"), style = H2)
                Text(tr("Reading and Listening update after every practice. Writing and Speaking cannot be marked by the app, so adjust them if a teacher gave you a level.",
                    "القراءة والاستماع تتحدّث بعد كل تدريب. الكتابة والمحادثة لا يصحّحها التطبيق، فعدّلها إذا أعطاك معلم مستوى."), style = Small)
                for (sk in SKILLS + "foundation") {
                    val lv = level(st, sk)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(skillName(sk), style = Label, modifier = Modifier.weight(1f))
                        Text("−", style = H2, modifier = Modifier.clickable { st.setEst(sk, (lv - 0.5).coerceAtLeast(3.0)); st.replanFuture() }.padding(horizontal = 10.dp))
                        Text("$lv", style = H3)
                        Text("+", style = H2, modifier = Modifier.clickable { st.setEst(sk, (lv + 0.5).coerceAtMost(9.0)); st.replanFuture() }.padding(horizontal = 10.dp))
                    }
                    Bar((lv / 9.0).toFloat(), if (lv >= st.goal) Green else if (lv >= st.goal - 1) Mustard else Coral)
                }
            }
            NCard {
                Text(tr("Stages", "المراحل"), style = H2)
                val b = (pr.days * 0.3).toInt().coerceAtLeast(1); val p = (pr.days * 0.8).toInt()
                val ranges = listOf(Phase.BUILD to (1 to b), Phase.PRACTICE to (b + 1 to p), Phase.EXAM to (p + 1 to pr.days))
                val cur = phaseOf(minOf(day, pr.days), pr.days)
                for ((ph, r) in ranges) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag(tr("Days ${r.first}-${r.second}", "الأيام ${r.first}-${r.second}"), if (ph == cur) Lime else Paper)
                        Spacer(Modifier.width(8.dp)); Text(phaseName(ph), style = H3)
                    }
                    Text(phaseInfo(ph), style = Small)
                }
                Spacer(Modifier.height(8.dp))
                Text(tr("Every 7th day there is a checkpoint test or a mock. Your levels and the plan for the coming days change with the results.",
                    "كل 7 أيام يوجد اختبار متابعة أو محاكاة، وتتغير مستوياتك وخطة الأيام القادمة حسب النتيجة."), style = Body.copy(fontSize = 14.sp))
            }
            Text(tr("How to improve", "خطة التحسين"), style = H2)
            val order = SKILLS.sortedBy { level(st, it) }
            for (sk in order) {
                val lv = level(st, sk)
                NCard(bg = if (sk == order.first()) Mustard else Card) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(skillName(sk), style = H3, modifier = Modifier.weight(1f))
                        Tag("$lv → ${st.goal}", if (lv >= st.goal) Lime else Coral)
                    }
                    if (sk == order.first()) Text(tr("Main focus: this skill gets the most time.", "التركيز الأساسي: هذه المهارة تأخذ أكبر وقت."), style = Small.copy(color = Ink))
                    for (a in advice(sk, lv)) Text("• $a", style = Body.copy(fontSize = 14.sp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NButton(tr("Retake placement", "أعد اختبار المستوى"), bg = Sky) { nav.go("quiz:placement") }
                if (!confirm) NButton(tr("Restart program", "ابدأ البرنامج من جديد"), bg = Card) { confirm = true }
                else NButton(tr("Yes, restart today", "نعم، ابدأ من اليوم"), bg = Coral) { st.restartProgram(); confirm = false }
            }
            Text(tr("Estimates are rough. Only the official test gives a real band.", "التقديرات تقريبية. الدرجة الحقيقية من الاختبار الرسمي فقط."), style = Small)
        }
    }
}

@Composable
fun PlanScreen(st: Store, nav: Nav) {
    val (pr, plan) = rememberPlan(st)
    val day = programDay(st)
    var open by remember { mutableStateOf(minOf(day, pr.days)) }
    var week by remember { mutableStateOf((minOf(day, pr.days) - 1) / 7) }
    Screen(tr("Your plan", "خطتك"), null) {
        Page {
            Text(tr("Focus: ${skillName(weakest(st))}. ${pr.days} days, the mix follows your weakest skills.", "التركيز: ${skillName(weakest(st))}. ${pr.days} يوم، وتوزيع المهام يتبع أضعف مهاراتك."), style = Body)
            Row(verticalAlignment = Alignment.CenterVertically) {
                NButton(if (I18n.ar) "→" else "←", bg = Card, enabled = week > 0) { week-- }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Week ${week + 1} of ${pr.weeks}", "الأسبوع ${week + 1} من ${pr.weeks}"), style = H3)
                    Text(phaseName(phaseOf(week * 7 + 1, pr.days)), style = Small)
                }
                NButton(if (I18n.ar) "←" else "→", bg = Card, enabled = week < pr.weeks - 1) { week++ }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in (week * 7 + 1)..(week * 7 + 7)) {
                    val valid = d <= pr.days
                    val ts = if (valid) (if (d <= day) tasksFor(st, d, plan) else plan[d - 1]) else emptyList()
                    val all = valid && ts.isNotEmpty() && ts.all { st.has("pdone", doneKey(d, it)) }
                    Box(
                        Modifier.weight(1f).height(44.dp).background(if (!valid) Paper else if (all) Green else if (d == open) Lime else Card, RoundedCornerShape(10.dp))
                            .border(if (d == day) 3.dp else 2.dp, Ink, RoundedCornerShape(10.dp)).clickable(enabled = valid) { open = d },
                        contentAlignment = Alignment.Center
                    ) { Text(if (valid) "$d" else "", style = Label.copy(color = if (all) Paper else Ink)) }
                }
            }
            if (open in 1..pr.days) NCard {
                Text(tr("Day $open", "اليوم $open"), style = H2)
                val ts = if (open <= day) tasksFor(st, open, plan) else plan[open - 1]
                for (t in ts) TaskRow(t, st.has("pdone", doneKey(open, t)), { nav.go(t.route()) }, { st.toggle("pdone", doneKey(open, t)) })
                if (open > day) Text(tr("Preview. Future days can change after checkpoints.", "معاينة. الأيام القادمة قد تتغير بعد اختبارات المتابعة."), style = Small)
            }
            NButton(tr("My program and levels", "برنامجي ومستوياتي"), bg = Mustard) { nav.go("program") }
        }
    }
}

@Composable
fun LessonsScreen(st: Store, nav: Nav) {
    var f by remember { mutableStateOf("all") }
    Screen(tr("Lessons", "الدروس"), { nav.back() }) {
        Page {
            Column {
                Row { for (s in listOf("all", "foundation", "reading", "listening")) Chip(if (s == "all") tr("All", "الكل") else skillName(s), f == s) { f = s } }
                Row { for (s in listOf("writing", "speaking")) Chip(skillName(s), f == s) { f = s } }
            }
            for (l in Content.lessons.filter { f == "all" || it.skill == f }) {
                NCard(onClick = { nav.go("lesson:${l.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.titleL(), style = H3, modifier = Modifier.weight(1f))
                        if (st.has("ldone", l.id)) Tag(tr("Done", "تم"), Lime)
                    }
                    Text(l.subL(), style = Small); Text("${skillName(l.skill)}  ·  ${timeL(l.time)}", style = Small)
                }
            }
        }
    }
}

@Composable
fun LessonScreen(id: String, st: Store, nav: Nav) {
    val l = Content.lessons.first { it.id == id }
    Screen(l.titleL(), { nav.back() }) {
        Page {
            Row { Tag(skillName(l.skill)); Spacer(Modifier.width(8.dp)); Tag(timeL(l.time), Sky) }
            Text(l.subL(), style = H3)
            Text(l.bodyL(), style = Body)
            if (l.example.isNotBlank()) NCard(bg = Lime) { Text(tr("Example", "مثال"), style = H3); Text(l.example, style = EnText) }
            if (l.action.isNotBlank()) NCard(bg = Mustard) { Text(tr("Try this", "جرّب"), style = H3); Text(l.actionL(), style = Body) }
            if (I18n.ar && l.bodyAr.isNotBlank()) NCard(bg = Paper) { Text("English", style = H3); Text(l.body, style = EnText.copy(fontSize = 14.sp)) }
            val done = st.has("ldone", l.id)
            NButton(if (done) tr("Marked done ✓", "تم ✓") else tr("Mark as done", "علّم كمنتهي"), bg = if (done) Card else Lime) { st.toggle("ldone", l.id); if (st.has("ldone", l.id)) st.addDay() }
        }
    }
}

val QTYPES = listOf("tfng" to "True / False / Not Given", "yn" to "Yes / No / Not Given", "sel" to "Matching", "mcq" to "Multiple choice", "gap" to "Completion")

@Composable
fun PracticeScreen(st: Store, nav: Nav) {
    var qt by remember { mutableStateOf("") }
    Screen(tr("Practice", "تدريب"), null) {
        Page {
            NCard(bg = Lime, onClick = { nav.go("quiz:quick") }) { Text(tr("Quick practice", "تدريب سريع"), style = H2); Text(tr("12 mixed questions, 15 minutes.", "12 سؤال متنوع في 15 دقيقة."), style = Body) }
            NCard(bg = Mustard, onClick = { nav.go("quiz:check") }) { Text(tr("Checkpoint test", "اختبار متابعة"), style = H2); Text(tr("16 questions. Updates your levels and plan.", "16 سؤال، يحدّث مستوياتك وخطتك."), style = Body) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (sk in listOf("foundation", "reading", "listening")) NButton(if (sk == "foundation") tr("Grammar", "القواعد") else skillName(sk), Modifier.weight(1f), Card) { nav.go("quiz:drill:$sk") }
            }
            Text(tr("By question type", "حسب نوع السؤال"), style = H2)
            Column { Row { for ((k, n) in QTYPES.take(3)) Chip(n, qt == k) { qt = if (qt == k) "" else k } }; Row { for ((k, n) in QTYPES.drop(3)) Chip(n, qt == k) { qt = if (qt == k) "" else k } } }
            fun has(s: Section) = qt == "" || s.groups.any { g -> g.qs.any { it.t == qt } }
            Text(tr("Reading passages", "قطع القراءة"), style = H2)
            for (s in Content.reading.filter { has(it) }) NCard(onClick = { nav.go("paper:R:${s.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(s.title, style = H3, modifier = Modifier.weight(1f)); if (st.mapInt("secn", s.id) > 0) Tag(tr("Done", "تم")) }
                Text("${s.sub}  ·  " + tr("20 minutes", "20 دقيقة") + if (s.mock > 0) "  ·  Mock ${s.mock}" else "", style = Small)
            }
            Text(tr("Listening sections", "أجزاء الاستماع"), style = H2)
            for (s in Content.listening.filter { has(it) }) NCard(onClick = { nav.go("paper:L:${s.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(s.title, style = H3, modifier = Modifier.weight(1f)); if (st.mapInt("secn", s.id) > 0) Tag(tr("Done", "تم")) }
                Text(tr("Untimed, can replay", "بدون وقت، يمكن الإعادة") + if (s.mock > 0) "  ·  Mock ${s.mock}" else "", style = Small)
            }
            if (qt == "") {
                Text(tr("Writing", "الكتابة"), style = H2)
                for (w in Content.writing.filter { it.type == st.type || it.type == "Both" }) NCard(onClick = { nav.go("write:${w.id}") }) {
                    Text("Task ${w.task}: ${w.title}", style = H3); Text(tr("${w.minutes} min, at least ${w.min} words", "${w.minutes} دقيقة، ${w.min} كلمة على الأقل"), style = Small)
                }
                Text(tr("Speaking", "المحادثة"), style = H2)
                for (s in Content.speaking) NCard(onClick = { nav.go("speak:${s.id}") }) { Text(s.topic, style = H3); Text(tr("Parts 1, 2 and 3", "الأجزاء 1 و2 و3"), style = Small) }
            }
        }
    }
}

@Composable
fun MocksScreen(nav: Nav) {
    Screen(tr("Mock tests", "اختبارات المحاكاة"), { nav.back() }) {
        Page {
            Text(tr("Timed, one go. Listening audio plays once. Scores are indicative only.", "بالوقت ومرة واحدة. تسجيل الاستماع يُشغَّل مرة واحدة. الدرجات تقريبية فقط."), style = Body)
            for (m in 1..Content.mocks()) {
                NCard {
                    Text(tr("Mock $m", "محاكاة $m"), style = H2)
                    Spacer(Modifier.height(8.dp))
                    NButton(tr("Reading, 60 min", "القراءة، 60 دقيقة"), Modifier.fillMaxWidth(), Lime) { nav.go("paper:R:mock$m") }
                    NButton(tr("Listening, 35 min", "الاستماع، 35 دقيقة"), Modifier.fillMaxWidth(), Sky) { nav.go("paper:L:mock$m") }
                }
            }
            NCard {
                Text(tr("Writing and Speaking", "الكتابة والمحادثة"), style = H2)
                Spacer(Modifier.height(8.dp))
                NButton(tr("Writing mock, 60 min", "محاكاة الكتابة، 60 دقيقة"), Modifier.fillMaxWidth(), Mustard) { nav.go("write:mock") }
                NButton(tr("Speaking mock", "محاكاة المحادثة"), Modifier.fillMaxWidth(), Coral) { nav.go("speak:mock") }
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
    Screen(tr("Words", "الكلمات"), null) {
        Page {
            val counts = (0..4).map { l -> words.count { st.mapInt("vl", it.id.toString()) == l } }
            Text(tr("Levels 0 to 4: ", "المستويات من 0 إلى 4: ") + counts.joinToString("  "), style = Small)
            Bar(words.count { st.mapInt("vl", it.id.toString()) >= 3 }.toFloat() / words.size)
            if (i >= queue.size) {
                NCard(bg = Lime) { Text(tr("Session done", "انتهت الجلسة"), style = H2); Text(tr("Come back tomorrow. Words you miss come back sooner.", "ارجع غدًا. الكلمات التي تخطئ فيها ترجع أسرع."), style = Body) }
                NButton(tr("Another round", "جولة أخرى")) { i = 0; flip = false }
            } else {
                val w = queue[i]
                Text(tr("Card ${i + 1} of ${queue.size}", "بطاقة ${i + 1} من ${queue.size}"), style = Small)
                NCard(bg = if (flip) Mustard else Card, onClick = { flip = !flip }) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(w.w, style = H1)
                            if (flip) { Spacer(Modifier.height(10.dp)); Text(w.def, style = H3); Spacer(Modifier.height(6.dp)); Text(w.ex, style = Body) }
                            else Text(tr("Tap to flip", "اضغط للقلب"), style = Small)
                        }
                    }
                }
                if (flip) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NButton(tr("Again", "مرة أخرى"), Modifier.weight(1f), Coral) { st.putMap("vl", w.id.toString(), 0); st.addDay(); i++; flip = false }
                    NButton(tr("Got it", "حفظتها"), Modifier.weight(1f), Lime) { st.putMap("vl", w.id.toString(), minOf(4, st.mapInt("vl", w.id.toString()) + 1)); st.addDay(); i++; flip = false }
                }
            }
        }
    }
}

@Composable
fun ProgressScreen(st: Store, nav: Nav) {
    Screen(tr("Progress", "التقدّم"), { nav.back() }) {
        Page {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag(tr("Streak ${st.streak()} days", "${st.streak()} أيام متتالية"), Coral); Tag(tr("${st.history().size} sessions", "${st.history().size} جلسة"), Sky) }
            NCard(onClick = { nav.go("program") }) {
                Text(tr("Estimated levels", "المستويات التقريبية"), style = H2)
                for (sk in SKILLS) { Spacer(Modifier.height(6.dp)); Text("${skillName(sk)}: ${level(st, sk)}", style = Label); Bar((level(st, sk) / 9.0).toFloat(), Plum) }
            }
            NCard {
                Text(tr("By skill", "حسب المهارة"), style = H2)
                for (sk in listOf("foundation", "reading", "listening")) {
                    val (c, t) = st.stat("sk", sk)
                    Spacer(Modifier.height(8.dp)); Text("${skillName(sk)}: $c / $t", style = Label)
                    Bar(if (t == 0) 0f else c.toFloat() / t, Green)
                }
            }
            NCard {
                Text(tr("By question type", "حسب نوع السؤال"), style = H2)
                for ((k, n) in QTYPES) {
                    val (c, t) = st.stat("ty", k)
                    Spacer(Modifier.height(8.dp)); Text("$n: $c / $t", style = Label)
                    Bar(if (t == 0) 0f else c.toFloat() / t, Plum)
                }
            }
            Text(tr("Recent sessions", "الجلسات الأخيرة"), style = H2)
            if (st.history().isEmpty()) Text(tr("Nothing yet. Finish a practice set and it appears here.", "لا يوجد شيء بعد. أنهِ تدريبًا وسيظهر هنا."), style = Body)
            for (h in st.history().take(15)) NCard {
                Text(h.optString("title"), style = H3)
                val b = if (h.isNull("band")) "" else tr("  ·  indicative band ", "  ·  درجة تقريبية ") + h.optDouble("band")
                Text("${h.optInt("score")} / ${h.optInt("total")}$b", style = Small)
            }
        }
    }
}

@Composable
fun AboutScreen(nav: Nav) {
    Screen(tr("About and limits", "عن التطبيق وحدوده"), { nav.back() }) {
        Page {
            NCard(bg = Lime) { Text(tr("Honest limits", "حدود واضحة"), style = H2); Text(tr("Khotwa is a study aid, not an official IELTS product. It is not connected to or approved by the IELTS partners.", "خطوة أداة مذاكرة وليست منتجًا رسميًا لآيلتس، ولا علاقة لها بالجهات المالكة للاختبار."), style = Body) }
            NCard { Text(tr("Original content", "محتوى أصلي"), style = H3); Text(tr("All passages, audio scripts, questions and prompts were written for this app. They follow IELTS formats but are not real exam papers.", "كل القطع ونصوص الاستماع والأسئلة كُتبت لهذا التطبيق. تتبع شكل آيلتس لكنها ليست امتحانات حقيقية."), style = Body) }
            NCard { Text(tr("Band estimates", "تقدير الدرجات"), style = H3); Text(tr("Reading and Listening bands are indicative only and need at least 30 questions. Program levels are rough and only guide the plan. Real tests can differ by a full band or more.", "درجات القراءة والاستماع تقريبية وتحتاج 30 سؤالًا على الأقل. مستويات البرنامج تقريبية لتوجيه الخطة فقط، وقد يختلف الاختبار الحقيقي بدرجة كاملة أو أكثر."), style = Body) }
            NCard { Text(tr("Writing and Speaking", "الكتابة والمحادثة"), style = H3); Text(tr("The writing check counts words, paragraphs and linking words. It does not judge quality or give a band. Speaking recordings stay on your phone. For real feedback, ask a teacher.", "فحص الكتابة يعدّ الكلمات والفقرات وأدوات الربط فقط ولا يقيّم الجودة. تسجيلات المحادثة تبقى على هاتفك. للتقييم الحقيقي اسأل معلمًا."), style = Body) }
            NCard { Text(tr("Listening audio", "صوت الاستماع"), style = H3); Text(tr("Audio is read by your phone's text-to-speech voice. Install an English (UK) voice in your phone's settings for the best sound.", "الصوت يُقرأ بمحرك النطق في هاتفك. ثبّت صوت English (UK) من إعدادات الهاتف لأفضل جودة."), style = Body) }
            NCard { Text(tr("Your data", "بياناتك"), style = H3); Text(tr("Everything stays on this device. The app needs no internet. The microphone is used only when you record a speaking answer.", "كل شيء يبقى على جهازك، والتطبيق لا يحتاج إنترنت. الميكروفون يُستخدم فقط عند تسجيل إجابة المحادثة."), style = Body) }
        }
    }
}

fun openTtsSettings(ctx: android.content.Context) {
    try { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { try { ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }
}

@Composable
fun SettingsScreen(st: Store, nav: Nav) {
    val ctx = LocalContext.current
    var imp by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    Screen(tr("Settings", "الإعدادات"), { nav.back() }) {
        Page {
            NCard {
                Text(tr("Language", "اللغة"), style = H3)
                Row { Chip("English", st.lang == "en") { st.lang = "en" }; Chip("العربية", st.lang == "ar") { st.lang = "ar" } }
                Text(tr("The interface and lessons change. Exam passages and questions stay in English, as in the real test.", "تتغير الواجهة والدروس. قطع وأسئلة الامتحان تبقى بالإنجليزية كما في الاختبار الحقيقي."), style = Small)
            }
            NCard {
                Text(tr("Test type", "نوع الاختبار"), style = H3)
                Row { for (t in listOf("Academic", "General Training")) Chip(typeName(t), st.type == t) { st.type = t; st.replanFuture() } }
                Text(tr("Target band", "الدرجة المستهدفة"), style = H3)
                Column {
                    Row { for (g in listOf(5.0, 5.5, 6.0, 6.5)) Chip("$g", st.goal == g) { st.goal = g; st.replanFuture() } }
                    Row { for (g in listOf(7.0, 7.5, 8.0)) Chip("$g", st.goal == g) { st.goal = g; st.replanFuture() } }
                }
                Text(tr("Minutes per day", "دقائق يوميًا"), style = H3)
                Row { for (m in listOf(15, 30, 45, 60)) Chip("$m", st.minutes == m) { st.minutes = m; st.replanFuture() } }
                Text(tr("Changing these re-plans the coming days.", "تغيير هذه الإعدادات يعيد تخطيط الأيام القادمة."), style = Small)
            }
            NCard {
                Text(tr("Listening voice", "صوت الاستماع"), style = H3)
                Text(tr("Install or choose an English (UK) voice for clearer listening audio.", "ثبّت أو اختر صوت English (UK) لصوت استماع أوضح."), style = Small)
                Spacer(Modifier.height(6.dp))
                NButton(tr("Open voice settings", "افتح إعدادات الصوت"), bg = Sky) { openTtsSettings(ctx) }
            }
            NCard {
                Text(tr("Backup", "النسخ الاحتياطي"), style = H3)
                Text(tr("Export your data as text, or paste a backup to restore.", "صدّر بياناتك كنص، أو الصق نسخة احتياطية للاستعادة."), style = Small)
                Spacer(Modifier.height(6.dp))
                NButton(tr("Export data", "تصدير البيانات")) {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, st.export())
                    ctx.startActivity(Intent.createChooser(i, "Export"))
                }
                BasicTextField(imp, { imp = it }, Modifier.fillMaxWidth().heightIn(min = 80.dp).background(Paper, RoundedCornerShape(10.dp)).border(2.dp, Ink, RoundedCornerShape(10.dp)).padding(10.dp), textStyle = Body.copy(fontSize = 13.sp), cursorBrush = SolidColor(Ink))
                Spacer(Modifier.height(6.dp))
                NButton(tr("Import pasted data", "استيراد النص الملصوق"), bg = Sky) { msg = if (st.import(imp)) tr("Imported.", "تم الاستيراد.") else tr("That is not valid backup text.", "هذا ليس نص نسخة احتياطية صالح.") }
                if (msg.isNotEmpty()) Text(msg, style = Label)
            }
            NCard {
                Text(tr("Reset", "إعادة الضبط"), style = H3)
                Text(tr("Deletes progress, drafts and settings on this device.", "يحذف التقدّم والمسودات والإعدادات من هذا الجهاز."), style = Small)
                Spacer(Modifier.height(6.dp))
                if (!confirm) NButton(tr("Reset everything", "احذف كل شيء"), bg = Coral) { confirm = true }
                else Row { NButton(tr("Yes, delete", "نعم، احذف"), bg = Coral) { st.reset(); confirm = false; nav.root("home") }; Spacer(Modifier.width(8.dp)); NButton(tr("Cancel", "إلغاء"), bg = Card) { confirm = false } }
            }
        }
    }
}
