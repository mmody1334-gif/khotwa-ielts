package com.khotwa.ielts

private val BANDL = listOf(39 to 9.0, 37 to 8.5, 35 to 8.0, 32 to 7.5, 30 to 7.0, 26 to 6.5, 23 to 6.0, 18 to 5.5, 16 to 5.0, 13 to 4.5, 10 to 4.0)
private val BANDR = listOf(39 to 9.0, 37 to 8.5, 35 to 8.0, 33 to 7.5, 30 to 7.0, 27 to 6.5, 23 to 6.0, 19 to 5.5, 15 to 5.0, 13 to 4.5, 10 to 4.0)

fun band(score: Int, total: Int, kind: Char): Double? {
    if (total < 30) return null
    val s = Math.round(score.toDouble() / total * 40).toInt()
    for ((m, b) in if (kind == 'L') BANDL else BANDR) if (s >= m) return b
    return 3.5
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
class Result(val title: String, val score: Int, val total: Int, val band: Double?, val bandKind: Char?, val items: List<RItem>, val note: String = "")

class PlanTask(val kind: String, val id: String, val label: String) {
    fun route(): String = when (kind) {
        "read" -> "paper:R:$id"
        "listen" -> "paper:L:$id"
        "write" -> "write:$id"
        "speak" -> "speak:$id"
        "lesson" -> "lesson:$id"
        "vocab" -> "words"
        "drill" -> "quiz:drill:$id"
        "mixed" -> "quiz:quick"
        "mockR" -> "paper:R:mock$id"
        "mockL" -> "paper:L:mock$id"
        "mockW" -> "write:mock"
        "mockS" -> "speak:mock"
        else -> "home"
    }
}

fun weakness(st: Store): String {
    val pl = mutableMapOf<String, Double>()
    for (sk in listOf("foundation", "reading", "listening")) {
        val (c, t) = st.stat("pl", sk); if (t > 0) pl[sk] = c.toDouble() / t
    }
    for (sk in listOf("reading", "listening")) {
        val (c, t) = st.stat("sk", sk); if (t >= 10) pl[sk] = c.toDouble() / t
    }
    return pl.minByOrNull { it.value }?.key ?: "foundation"
}

fun buildPlan(st: Store): List<List<PlanTask>> {
    val goal = st.goal; val gt = st.type == "General Training"
    val n = if (st.minutes <= 15) 1 else if (st.minutes <= 30) 2 else 3
    val weak = weakness(st)
    fun lessonOf(sk: String, i: Int): PlanTask? {
        val l = Content.lessons.filter { it.skill == sk }; if (l.isEmpty()) return null
        val x = l[i % l.size]; return PlanTask("lesson", x.id, "Lesson: ${x.title}")
    }
    val t1 = Content.writing.filter { it.task == 1 && (it.type == st.type || it.type == "Both") }
    val t2 = Content.writing.filter { it.task == 2 }
    val rd = Content.reading; val ls = Content.listening; val sp = Content.speaking
    val days = mutableListOf<List<PlanTask>>()
    for (d in 1..28) {
        val k = d - 1
        val th = (d - 1) % 7
        val tasks = mutableListOf<PlanTask>()
        fun read() { val s = rd[k % rd.size]; tasks.add(PlanTask("read", s.id, "Reading practice: ${s.title}")) }
        fun listen() { val s = ls[k % ls.size]; tasks.add(PlanTask("listen", s.id, "Listening practice: ${s.title}")) }
        fun write(two: Boolean) { val w = if (two) t2[k % t2.size] else t1[k % t1.size]; tasks.add(PlanTask("write", w.id, "Writing Task ${w.task}: ${w.title}")) }
        fun speak() { val s = sp[k % sp.size]; tasks.add(PlanTask("speak", s.id, "Speaking: ${s.topic}")) }
        fun vocab() = tasks.add(PlanTask("vocab", "", "Review 10 words"))
        fun les(sk: String) { lessonOf(sk, k / 7)?.let { tasks.add(it) } }
        when {
            d == 7 -> { tasks.add(PlanTask("mixed", "", "Mixed drill (12 questions)")); les("foundation") }
            d == 14 -> { tasks.add(PlanTask("mockR", "1", "Mock 1: Reading (60 min)")); les("reading") }
            d == 21 -> { tasks.add(PlanTask("mockL", "1", "Mock 1: Listening")); les("listening") }
            d == 28 -> { tasks.add(PlanTask("mockW", "", "Mock: Writing")); tasks.add(PlanTask("mockS", "", "Mock: Speaking")) }
            else -> when (th) {
                0 -> { read(); les("reading") }
                1 -> { listen(); les("listening") }
                2 -> { write(goal >= 6.5 && d > 10); les("writing") }
                3 -> { speak(); if (goal <= 5.5) les("foundation") else les("speaking") }
                4 -> { read(); if (goal >= 6.5) write(true) else tasks.add(PlanTask("drill", "foundation", "Foundation drill")) }
                else -> { listen(); vocab() }
            }
        }
        if (n >= 3 && d !in listOf(14, 21, 28)) {
            if (d % 2 == 0) tasks.add(PlanTask("drill", weak, "Weak-skill drill: ${weak.replaceFirstChar { it.uppercase() }}")) else vocab()
        }
        val cut = maxOf(n, 2)
        days.add(if (d in listOf(14, 21, 28)) tasks else tasks.take(if (n >= 3) 3 else cut))
    }
    return days
}

fun quizQuestions(kind: String, arg: String): List<BankQ> {
    val b = Content.bank
    return when (kind) {
        "placement" -> b.filter { it.placement }
        "drill" -> b.filter { it.skill == arg }.shuffled().take(10)
        else -> listOf("foundation", "reading", "listening").flatMap { sk -> b.filter { it.skill == sk }.shuffled().take(4) }.shuffled()
    }
}
