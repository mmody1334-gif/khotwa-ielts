package com.khotwa.ielts

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val BANDL = listOf(39 to 9.0, 37 to 8.5, 35 to 8.0, 32 to 7.5, 30 to 7.0, 26 to 6.5, 23 to 6.0, 18 to 5.5, 16 to 5.0, 13 to 4.5, 10 to 4.0, 6 to 3.5)
private val BANDR = listOf(39 to 9.0, 37 to 8.5, 35 to 8.0, 33 to 7.5, 30 to 7.0, 27 to 6.5, 23 to 6.0, 19 to 5.5, 15 to 5.0, 13 to 4.5, 10 to 4.0, 6 to 3.5)

/** Band shown on a results screen: only for 30+ questions. */
fun band(score: Int, total: Int, kind: Char): Double? {
    if (total < 30) return null
    return accBand(score, total, kind)
}

/** Rough level from any number of questions (used for the study program, never shown as a test band). */
fun accBand(c: Int, t: Int, kind: Char = 'R'): Double {
    if (t <= 0) return 4.0
    val s = Math.round(c.toDouble() / t * 40).toInt()
    for ((m, b) in if (kind == 'L') BANDL else BANDR) if (s >= m) return b
    return 3.0
}

fun half(x: Double): Double = floor(x * 2 + 0.5) / 2

/** IELTS overall rounding: average of four, .25 rounds up to .5 and .75 up to the next band. */
fun overall(vals: List<Double>): Double {
    val a = vals.average(); val f = floor(a); val r = a - f
    return when { r < 0.25 -> f; r < 0.75 -> f + 0.5; else -> f + 1.0 }
}

fun norm(s: String) = s.lowercase().trim().replace(Regex("\\s+"), " ").trimEnd('.')

fun isCorrect(q: Q, ans: String?): Boolean {
    if (ans == null || ans.isBlank()) return false
    return if (q.t == "gap") q.accepted.any { norm(it) == norm(ans) } else ans.toIntOrNull() == q.a
}

fun optionsOf(g: Group, q: Q): List<String> = when (q.t) {
    "tfng" -> listOf("True", "False", "Not Given")
    "yn" -> listOf("Yes", "No", "Not Given")
    "sel" -> g.opts
    else -> q.o
}

fun correctText(g: Group, q: Q): String = if (q.t == "gap") q.accepted.firstOrNull() ?: "" else optionsOf(g, q).getOrElse(q.a) { "" }

class RItem(val q: String, val yours: String, val correct: String, val ok: Boolean, val why: String, val tag: String)
class Result(val title: String, val score: Int, val total: Int, val band: Double?, val bandKind: Char?, val items: List<RItem>, val note: String = "", val next: String? = null)

// ---------------------------------------------------------------- skill levels

val SKILLS = listOf("reading", "listening", "writing", "speaking")

fun skillName(sk: String): String = when (sk) {
    "reading" -> tr("Reading", "القراءة")
    "listening" -> tr("Listening", "الاستماع")
    "writing" -> tr("Writing", "الكتابة")
    "speaking" -> tr("Speaking", "المحادثة")
    "foundation" -> tr("Grammar and vocabulary", "القواعد والمفردات")
    else -> sk
}

fun level(st: Store, sk: String): Double = st.est(sk) ?: if (sk == "foundation") 5.0 else 5.0

fun currentOverall(st: Store): Double = overall(SKILLS.map { level(st, it) })

/** Called after the placement test. Writing and Speaking start from grammar and vocabulary, and can be adjusted by hand. */
fun applyPlacement(st: Store, f: Pair<Int, Int>, r: Pair<Int, Int>, l: Pair<Int, Int>) {
    val bf = accBand(f.first, f.second)
    val br = accBand(r.first, r.second, 'R')
    val bl = accBand(l.first, l.second, 'L')
    fun clamp(x: Double) = half(x).coerceIn(3.5, 8.5)
    st.setEst("foundation", clamp(bf))
    st.setEst("reading", clamp(br * 0.75 + bf * 0.25))
    st.setEst("listening", clamp(bl * 0.75 + bf * 0.25))
    st.setEst("writing", clamp(bf - 0.5))
    st.setEst("speaking", clamp(bf - 0.5 + (bl - bf) * 0.25))
    st.placed = true
    st.restartProgram()
}

/** Moves a skill estimate towards a new result. Bigger sets move it more. */
fun updateLevel(st: Store, sk: String, c: Int, t: Int, kind: Char = 'R') {
    if (t < 4) return
    val w = when { t >= 30 -> 0.6; t >= 10 -> 0.3; else -> 0.15 }
    val old = level(st, sk)
    st.setEst(sk, half(old * (1 - w) + accBand(c, t, kind) * w).coerceIn(3.0, 9.0))
}

// ---------------------------------------------------------------- the program

class ExamFit(val daysLeft: Int, val availHours: Int, val fits: Boolean, val reachable: Double, val needMinutes: Int)

class Program(val current: Double, val target: Double, val days: Int, val hours: Double, val exam: ExamFit? = null) {
    val weeks get() = (days + 6) / 7
    val gap get() = max(0.0, target - current)
}

private fun hoursForHalfBand(from: Double): Double = when {
    from < 5.0 -> 35.0
    from < 6.0 -> 45.0
    from < 7.0 -> 60.0
    else -> 80.0
}

fun program(st: Store): Program {
    val cur = currentOverall(st)
    val tgt = st.goal
    var h = 0.0; var b = cur
    while (b < tgt - 0.01) { h += hoursForHalfBand(b); b += 0.5 }
    val perWeek = st.minutes / 60.0 * 6
    var days = if (h == 0.0 || !st.placed) 28 else (ceil(h / perWeek) * 7).toInt().coerceIn(28, 364)
    // With an exam date the program ends the day before the exam. It also says what fits in that time.
    val examDay = st.exam
    val left = examDay - today()
    var fit: ExamFit? = null
    if (st.placed && examDay > 0 && left > 0) {
        val start = if (st.start == 0) today() else st.start
        days = max(7, examDay - start)
        val avail = left / 7.0 * perWeek
        var reach = cur; var used = 0.0
        while (reach < 9 && used + hoursForHalfBand(reach) <= avail) { used += hoursForHalfBand(reach); reach += 0.5 }
        fit = ExamFit(left, Math.round(avail).toInt(), avail >= h, reach, if (h > 0) ceil(h * 60 / (left / 7.0 * 6)).toInt() else 0)
    }
    return Program(cur, tgt, days, h, fit)
}

enum class Phase { BUILD, PRACTICE, EXAM }

fun phaseOf(day: Int, total: Int): Phase {
    val f = (day - 1).toDouble() / max(1, total)
    return when { f < 0.3 -> Phase.BUILD; f < 0.8 -> Phase.PRACTICE; else -> Phase.EXAM }
}

fun phaseName(p: Phase) = when (p) {
    Phase.BUILD -> tr("Build the basics", "بناء الأساس")
    Phase.PRACTICE -> tr("Skill practice", "تدريب المهارات")
    Phase.EXAM -> tr("Exam practice", "محاكاة الامتحان")
}

fun phaseInfo(p: Phase) = when (p) {
    Phase.BUILD -> tr("Lessons, grammar, vocabulary and short drills on your weakest skills.", "دروس وقواعد ومفردات وتدريبات قصيرة على أضعف مهاراتك.")
    Phase.PRACTICE -> tr("Full passages, listening sections, writing tasks and speaking sets.", "قطع قراءة كاملة وأجزاء استماع ومهام كتابة ومحادثة.")
    Phase.EXAM -> tr("Timed mocks every week, review mistakes, keep your weak skills moving.", "اختبارات محاكاة بالوقت كل أسبوع ومراجعة الأخطاء مع استمرار التركيز على نقاط الضعف.")
}

class PlanTask(val kind: String, val id: String) {
    fun route(): String = when (kind) {
        "read" -> "paper:R:$id"
        "listen" -> "paper:L:$id"
        "write" -> "write:$id"
        "speak" -> "speak:$id"
        "lesson" -> "lesson:$id"
        "vocab" -> "words"
        "review" -> "review"
        "dict" -> "dict"
        "drill" -> "quiz:drill:$id"
        "check" -> "quiz:check"
        "ext" -> "ext:$id"
        "mockR" -> "paper:R:mock$id"
        "mockL" -> "paper:L:mock$id"
        "mockW" -> "write:mock"
        "mockS" -> "speak:mock"
        else -> "home"
    }
    fun label(): String = when (kind) {
        "read" -> tr("Reading: ", "قراءة: ") + (Content.sectionOrNull(id)?.title ?: id)
        "listen" -> tr("Listening: ", "استماع: ") + (Content.sectionOrNull(id)?.title ?: id)
        "write" -> Content.writing.firstOrNull { it.id == id }?.let { tr("Writing Task ${it.task}: ", "كتابة المهمة ${it.task}: ") + it.title } ?: id
        "speak" -> tr("Speaking: ", "محادثة: ") + (Content.speaking.firstOrNull { it.id == id }?.topic ?: id)
        "lesson" -> tr("Lesson: ", "درس: ") + (Content.lessons.firstOrNull { it.id == id }?.let { it.titleL() } ?: id)
        "vocab" -> tr("Review 10 words", "مراجعة 10 كلمات")
        "review" -> tr("Review your mistakes", "راجع أخطاءك") + mistakesDue(Content.store).size.let { if (it > 0) " ($it)" else "" }
        "dict" -> tr("Dictation: names, numbers and dates", "إملاء: أسماء وأرقام وتواريخ")
        "drill" -> tr("Drill: ", "تدريب: ") + skillName(id)
        "check" -> tr("Checkpoint test (updates your plan)", "اختبار متابعة (يحدّث خطتك)")
        "ext" -> tr("Full test online: ", "اختبار كامل أونلاين: ") + (Content.external.firstOrNull { it.id == id }?.title ?: id)
        "mockR" -> tr("Mock $id: Reading, 60 min", "محاكاة $id: القراءة، 60 دقيقة")
        "mockL" -> tr("Mock $id: Listening", "محاكاة $id: الاستماع")
        "mockW" -> tr("Mock: Writing, 60 min", "محاكاة: الكتابة، 60 دقيقة")
        "mockS" -> tr("Mock: Speaking", "محاكاة: المحادثة")
        else -> kind
    }
    fun code() = "$kind|$id"
    companion object { fun of(code: String): PlanTask? { val p = code.split("|"); return if (p.size == 2) PlanTask(p[0], p[1]) else null } }
}

/** Weight of each skill in the plan: the further below target, the more time it gets. */
fun weights(st: Store): Map<String, Double> {
    val t = st.goal
    val m = SKILLS.associateWith { 1.0 + 2.5 * max(0.0, t - level(st, it)) }.toMutableMap()
    val f = level(st, "foundation")
    m["foundation"] = if (f < 5.5) 1.0 + (5.5 - f) * 2.5 else if (f < t) 0.5 else 0.2
    return m
}

fun weakest(st: Store): String = SKILLS.minByOrNull { level(st, it) - if (it == "reading" || it == "listening") 0.01 else 0.0 } ?: "reading"

private class Picker(val st: Store) {
    val counters = mutableMapOf<String, Int>()
    fun n(k: String): Int { val v = counters[k] ?: 0; counters[k] = v + 1; return v }

    fun <T> rotate(pool: List<T>, key: String, done: (T) -> Int): T? {
        if (pool.isEmpty()) return null
        val sorted = pool.withIndex().sortedWith(compareBy({ done(it.value) }, { it.index })).map { it.value }
        return sorted[n(key) % sorted.size]
    }
    fun lesson(sk: String): PlanTask? {
        val l = Content.lessons.filter { it.skill == sk }
        return rotate(l, "les-$sk") { if (st.has("ldone", it.id)) 1 else 0 }?.let { PlanTask("lesson", it.id) }
    }
    val wt = weakType(st)
    fun section(kind: Char): PlanTask? {
        var pool = if (kind == 'R') Content.reading else Content.listening
        // every other time, pick a passage that has your weakest question type
        if (wt != null && n("weak-$kind") % 2 == 0) { val f = pool.filter { s -> s.groups.any { g -> g.qs.any { it.t == wt.type } } }; if (f.isNotEmpty()) pool = f }
        return rotate(pool, "sec-$kind") { st.mapInt("secn", it.id) }?.let { PlanTask(if (kind == 'R') "read" else "listen", it.id) }
    }
    fun writing(task: Int): PlanTask? {
        val pool = Content.writing.filter { it.task == task && (task == 2 || it.type == st.type || it.type == "Both") }
        return rotate(pool, "w$task") { st.mapInt("wn", it.id) }?.let { PlanTask("write", it.id) }
    }
    fun speaking(): PlanTask? = rotate(Content.speaking, "sp") { st.mapInt("spn", it.id) }?.let { PlanTask("speak", it.id) }

    fun task(sk: String, ph: Phase): PlanTask? {
        val k = n("t-$sk")
        return when (sk) {
            "reading" -> when (ph) { Phase.BUILD -> if (k % 2 == 0) lesson("reading") else PlanTask("drill", "reading"); else -> section('R') }
            "listening" -> when (ph) { Phase.BUILD -> listOf(lesson("listening"), PlanTask("drill", "listening"), PlanTask("dict", ""))[k % 3]; else -> if (k % 4 == 3) PlanTask("dict", "") else section('L') }
            "writing" -> when (ph) {
                Phase.BUILD -> if (k % 2 == 0) lesson("writing") else writing(1)
                Phase.PRACTICE -> if (level(st, "writing") < 5.5) writing(if (k % 3 == 2) 2 else 1) else writing(if (k % 2 == 0) 2 else 1)
                Phase.EXAM -> writing(if (k % 3 == 0) 1 else 2)
            }
            "speaking" -> if (ph == Phase.BUILD && k % 2 == 0) lesson("speaking") else speaking()
            else -> if (k % 2 == 0) PlanTask("drill", "foundation") else (lesson("foundation") ?: PlanTask("vocab", ""))
        }
    }
}

private fun mockCount(): Int = max(1, Content.reading.maxOfOrNull { it.mock } ?: 1)

/** Builds the task list for every day of the program. Days are simulated in order so the mix follows the weights. */
fun generatePlan(st: Store, total: Int): List<List<PlanTask>> {
    val w = weights(st)
    val slots = (st.minutes / 15).coerceIn(1, 4)
    val pk = Picker(st)
    val credit = w.keys.associateWith { 0.0 }.toMutableMap()
    val sum = w.values.sum()
    val mocks = mockCount()
    var mockI = 0
    var extI = 0
    val days = mutableListOf<List<PlanTask>>()
    for (d in 1..total) {
        val ph = phaseOf(d, total)
        val out = mutableListOf<PlanTask>()
        val last = d == total
        if (last) {
            val m = mocks
            out += PlanTask("mockR", "$m"); out += PlanTask("mockL", "$m"); out += PlanTask("mockW", ""); out += PlanTask("mockS", "")
            days += out; continue
        }
        if (d % 7 == 0) {
            if (ph == Phase.EXAM) {
                // Exam stage: every other week a full online test (real Reading and Listening scores), otherwise a mock in the app
                if (Content.external.isNotEmpty() && extI % 2 == 0) {
                    val pool = Content.external.sortedBy { if (st.has("extdone", it.id)) 1 else 0 }
                    out += PlanTask("ext", pool[(extI / 2) % pool.size].id)
                } else { val m = ((mockI / 2) % mocks) + 1; out += PlanTask(if (mockI % 2 == 0) "mockR" else "mockL", "$m"); mockI++ }
                extI++
            }
            else out += PlanTask("check", "")
        }
        val used = mutableSetOf<String>()
        while (out.size < slots && out.size < 4) {
            // smooth weighted round robin
            for (k in credit.keys) credit[k] = credit[k]!! + w[k]!!
            val pick = credit.entries.filter { it.key !in used }.maxByOrNull { it.value }?.key ?: break
            credit[pick] = credit[pick]!! - sum
            used += pick
            pk.task(pick, ph)?.let { t -> if (out.none { it.code() == t.code() }) out += t }
            if (used.size >= credit.size) break
        }
        if (slots >= 2 && d % 2 == 1 && out.none { it.kind == "vocab" } && out.size < 4) out += PlanTask("vocab", "")
        if (d % 3 == 2 && out.size < 4) out += PlanTask("review", "")
        days += out
    }
    return days
}

fun programDay(st: Store): Int {
    if (st.start == 0) st.start = today()
    return max(1, today() - st.start + 1)
}

/** Today's tasks are frozen once shown, so they do not move while you work. Future days are a preview. */
fun tasksFor(st: Store, day: Int, plan: List<List<PlanTask>>): List<PlanTask> {
    val saved = st.mapStr("dp", "$day")
    if (saved.isNotBlank()) return saved.split(";").mapNotNull { PlanTask.of(it) }
    val d = min(day, plan.size)
    val t = plan.getOrElse(d - 1) { emptyList() }
    if (day <= programDay(st)) st.putMap("dp", "$day", t.joinToString(";") { it.code() })
    return t
}

/** Advice for one skill at its current level. */
fun advice(sk: String, lv: Double): List<String> = when (sk) {
    "reading" -> when {
        lv < 5.0 -> listOf(
            tr("Learn 10 academic words a day; most wrong answers come from unknown words.", "احفظ 10 كلمات أكاديمية يوميًا؛ معظم الأخطاء سببها كلمات غير معروفة."),
            tr("Practise scanning: find names, dates and numbers in under a minute.", "تدرّب على البحث السريع عن الأسماء والتواريخ والأرقام في أقل من دقيقة."),
            tr("Do one short passage a day without a timer, then check every answer.", "حل قطعة قصيرة يوميًا بدون وقت، ثم راجع كل إجابة."))
        lv < 6.0 -> listOf(
            tr("Master True / False / Not Given: Not Given means the text does not say.", "أتقن True / False / Not Given: الـ Not Given تعني أن النص لا يذكر المعلومة."),
            tr("Read the questions first, then look for paraphrases, not the same words.", "اقرأ الأسئلة أولًا ثم ابحث عن إعادة الصياغة وليس نفس الكلمات."),
            tr("Keep to 20 minutes per passage.", "التزم بـ 20 دقيقة لكل قطعة."))
        lv < 7.0 -> listOf(
            tr("Work on matching headings: find the main idea, not a detail.", "ركّز على مطابقة العناوين: ابحث عن الفكرة الرئيسية وليس تفصيلة."),
            tr("Review every wrong answer and write why the right one is right.", "راجع كل إجابة خاطئة واكتب لماذا الإجابة الصحيحة صحيحة."),
            tr("Do full timed mocks every week.", "حل اختبار محاكاة كامل بالوقت كل أسبوع."))
        else -> listOf(
            tr("Watch for Not Given traps and opinion versus fact (Yes / No / Not Given).", "انتبه لفخاخ Not Given والفرق بين الرأي والمعلومة."),
            tr("Read long academic articles outside the app to build speed.", "اقرأ مقالات أكاديمية طويلة خارج التطبيق لزيادة السرعة."))
    }
    "listening" -> when {
        lv < 5.0 -> listOf(
            tr("Practise spelling, numbers and dates; Section 1 depends on them.", "تدرّب على التهجئة والأرقام والتواريخ؛ الجزء الأول يعتمد عليها."),
            tr("Replay practice sections and read the transcript after answering.", "أعد تشغيل أجزاء التدريب واقرأ النص بعد الإجابة."))
        lv < 6.0 -> listOf(
            tr("Predict the type of answer (number, noun, place) before the audio starts.", "توقّع نوع الإجابة (رقم، اسم، مكان) قبل بدء التسجيل."),
            tr("Listen for corrections: speakers often change their minds.", "انتبه للتصحيحات: المتحدث كثيرًا ما يغيّر رأيه."))
        lv < 7.0 -> listOf(
            tr("Work on Sections 3 and 4: discussions and lectures.", "ركّز على الجزأين 3 و4: النقاشات والمحاضرات."),
            tr("Do mock listening with the audio once only.", "حل محاكاة الاستماع مع تشغيل التسجيل مرة واحدة فقط."))
        else -> listOf(tr("Listen to English lectures and podcasts daily and take short notes.", "استمع يوميًا لمحاضرات وبودكاست بالإنجليزية ودوّن ملاحظات قصيرة."))
    }
    "writing" -> when {
        lv < 5.0 -> listOf(
            tr("Write simple, correct sentences first; then join them with because, but, although.", "اكتب جملًا بسيطة صحيحة أولًا، ثم اربطها بـ because و but و although."),
            tr("Learn the four-paragraph shape for Task 2.", "تعلّم شكل الأربع فقرات لمهمة 2."))
        lv < 6.0 -> listOf(
            tr("Reach the word count every time: 150 for Task 1, 250 for Task 2.", "احرص على عدد الكلمات دائمًا: 150 للمهمة 1 و250 للمهمة 2."),
            tr("In Task 1, write a clear overview of the main trends.", "في المهمة 1 اكتب نظرة عامة واضحة لأهم الاتجاهات."),
            tr("Answer every part of the Task 2 question.", "أجب عن كل أجزاء سؤال المهمة 2."))
        lv < 7.0 -> listOf(
            tr("Develop each idea: point, explanation, example.", "طوّر كل فكرة: النقطة، الشرح، المثال."),
            tr("Vary vocabulary and use complex sentences accurately.", "نوّع المفردات واستخدم جملًا مركّبة بدقة."),
            tr("Compare your essay with the model and ask a teacher for feedback.", "قارن مقالك بالنموذج واطلب رأي معلم."))
        else -> listOf(tr("Aim for precise, less common words and a clear position throughout.", "استهدف كلمات دقيقة وأقل شيوعًا وموقفًا واضحًا طوال المقال."))
    }
    else -> when {
        lv < 5.0 -> listOf(
            tr("Answer in full sentences and add one reason: I like it because...", "أجب بجمل كاملة وأضف سببًا: I like it because..."),
            tr("Record yourself every day and replay it.", "سجّل صوتك يوميًا واستمع له."))
        lv < 6.0 -> listOf(
            tr("Speak for the full 2 minutes in Part 2; use the 1 minute to make notes.", "تكلّم دقيقتين كاملتين في الجزء 2؛ استغل دقيقة التحضير لكتابة ملاحظات."),
            tr("Reduce long pauses; use phrases like Let me think.", "قلّل التوقف الطويل؛ استخدم عبارات مثل Let me think."))
        lv < 7.0 -> listOf(
            tr("In Part 3, give reasons, examples and the other side.", "في الجزء 3 قدّم أسبابًا وأمثلة والرأي الآخر."),
            tr("Use a range of tenses and some idiomatic language naturally.", "استخدم أزمنة متنوعة وبعض التعبيرات بشكل طبيعي."))
        else -> listOf(tr("Practise with a partner or teacher who can comment on pronunciation.", "تدرّب مع زميل أو معلم يعلّق على النطق."))
    }
}

fun quizQuestions(kind: String, arg: String): List<BankQ> {
    val b = Content.bank
    fun pick(sk: String, n: Int) = b.filter { it.skill == sk && !it.placement }.shuffled().take(n)
    return when (kind) {
        "placement" -> b.filter { it.placement }
        "drill" -> b.filter { it.skill == arg }.shuffled().take(10)
        "check" -> (pick("foundation", 4) + pick("reading", 6) + pick("listening", 6)).shuffled()
        else -> listOf("foundation", "reading", "listening").flatMap { sk -> b.filter { it.skill == sk }.shuffled().take(4) }.shuffled()
    }
}
