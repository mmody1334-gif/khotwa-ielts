package com.khotwa.ielts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- estimates for writing and speaking

private val LINKERS = listOf("however", "although", "whereas", "while", "therefore", "moreover", "furthermore", "in addition", "for example", "for instance",
    "consequently", "in contrast", "on the other hand", "overall", "in conclusion", "because", "despite", "as a result", "nevertheless", "firstly", "finally", "such as", "unless")

class TextStats(val words: Int, val sentences: Int, val paras: Int, val links: Int, val variety: Double, val longRatio: Double)

fun textStats(text: String): TextStats {
    val w = Regex("[A-Za-z']+").findAll(text).map { it.value.lowercase() }.toList()
    val sents = text.split(Regex("[.!?]+")).count { it.isNotBlank() }
    val paras = text.split(Regex("\n\\s*\n")).count { it.isNotBlank() }
    val low = " " + text.lowercase() + " "
    val links = LINKERS.count { low.contains(" $it ") || low.contains(" $it,") }
    val head = w.take(120)
    val variety = if (head.isEmpty()) 0.0 else head.toSet().size.toDouble() / head.size
    val longRatio = if (w.isEmpty()) 0.0 else w.count { it.length >= 7 }.toDouble() / w.size
    return TextStats(w.size, sents, paras, links, variety, longRatio)
}

/**
 * Rough writing level. Text features (length, sentence length, linking, word variety, long words) are blended with the
 * grammar result and the learner's own check. The app cannot judge grammar or ideas in an essay, so grammar keeps it in check.
 */
fun writingLevel(text: String, minWords: Int, grammar: Double, selfYes: Double): Double {
    val s = textStats(text)
    if (s.words < 40) return half(minOf(4.0, grammar)).coerceAtLeast(3.0)
    var h = 4.0
    h += if (s.words >= minWords) 0.5 else if (s.words < minWords * 0.7) -0.5 else 0.0
    val avg = s.words.toDouble() / maxOf(1, s.sentences)
    if (avg in 11.0..26.0) h += 0.5
    if (s.variety > 0.55) h += 0.5
    if (s.variety > 0.65) h += 0.5
    if (s.longRatio > 0.14) h += 0.5
    if (s.longRatio > 0.20) h += 0.5
    if (s.links >= 3) h += 0.5
    if (s.links >= 6) h += 0.5
    if (s.paras >= 3) h += 0.5
    val blend = 0.45 * h + 0.55 * grammar + (selfYes - 1.5) * 0.25
    return half(blend.coerceIn(3.5, minOf(8.5, grammar + 1.0)))
}

/** Rough speaking level from speech rate and vocabulary in the transcript, the grammar result and the learner's own check. */
fun speakingLevel(transcript: String, secs: Int, grammar: Double, selfLen: Int, selfPause: Int): Double {
    val s = textStats(transcript)
    val self = 4.5 + selfLen * 0.5 + selfPause * 0.5
    val fluency: Double? = if (s.words >= 20 && secs >= 20) {
        val wpm = s.words / (secs / 60.0)
        var f = when { wpm < 50 -> 4.0; wpm < 70 -> 4.5; wpm < 90 -> 5.0; wpm < 105 -> 5.5; wpm < 120 -> 6.0; wpm < 135 -> 6.5; wpm < 150 -> 7.0; else -> 7.5 }
        if (s.variety > 0.6) f += 0.5
        if (s.longRatio > 0.12) f += 0.5
        if (secs < 60) f -= 0.5
        f
    } else null
    val blend = if (fluency != null) 0.4 * fluency + 0.35 * grammar + 0.25 * self else 0.5 * grammar + 0.5 * self - 0.25
    return half(blend.coerceIn(3.5, minOf(8.5, grammar + 1.0)))
}

/** Saves the levels from all five parts and starts the program. */
fun applyFullPlacement(st: Store) {
    val f = accBand(st.mapInt("plc", "fc"), st.mapInt("plc", "ft"))
    val r = accBand(st.mapInt("plc", "rc"), st.mapInt("plc", "rt"), 'R')
    val l = accBand(st.mapInt("plc", "lc"), st.mapInt("plc", "lt"), 'L')
    fun c(x: Double) = half(x).coerceIn(3.0, 9.0)
    st.setEst("foundation", c(f))
    st.setEst("reading", c(r * 0.8 + f * 0.2))
    st.setEst("listening", c(l * 0.85 + f * 0.15))
    st.setEst("writing", c(st.mapDouble("plc", "w", f - 0.5)))
    st.setEst("speaking", c(st.mapDouble("plc", "s", f - 0.5)))
    st.addStat("pl", "foundation", st.mapInt("plc", "fc"), st.mapInt("plc", "ft"))
    st.addStat("pl", "reading", st.mapInt("plc", "rc"), st.mapInt("plc", "rt"))
    st.addStat("pl", "listening", st.mapInt("plc", "lc"), st.mapInt("plc", "lt"))
    st.addHistory(tr("Placement test", "اختبار تحديد المستوى"), st.mapInt("plc", "fc") + st.mapInt("plc", "rc") + st.mapInt("plc", "lc"),
        st.mapInt("plc", "ft") + st.mapInt("plc", "rt") + st.mapInt("plc", "lt"), null)
    st.placed = true
    st.restartProgram()
}

// ---------------------------------------------------------------- speech capture

/** Listens with the phone's speech recognizer and restarts after each pause, so the learner can talk for up to two minutes. */
class SpeechCapture(private val ctx: Context) {
    var text by mutableStateOf("")
    var partial by mutableStateOf("")
    var running by mutableStateOf(false)
    var error by mutableStateOf("")
    var startMs = 0L
    var endMs = 0L
    private var sr: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())

    val available get() = SpeechRecognizer.isRecognitionAvailable(ctx)
    val seconds get() = (((if (running) System.currentTimeMillis() else endMs) - startMs) / 1000).toInt().coerceAtLeast(0)

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)

    private fun add(s: String?) { if (!s.isNullOrBlank()) text = (text + " " + s).trim() }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(b: Bundle?) { partial = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: partial }
        override fun onResults(b: Bundle?) {
            add(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()); partial = ""
            if (running) h.postDelayed({ listen() }, 150)
        }
        override fun onError(code: Int) {
            if (partial.split(" ").size >= 2) add(partial); partial = ""
            if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { error = "permission"; stop(); return }
            if (running) h.postDelayed({ listen() }, if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 600 else 200)
        }
    }

    private fun listen() { if (running) try { sr?.startListening(intent()) } catch (e: Exception) { } }

    fun start() {
        if (!available) { error = "unavailable"; return }
        text = ""; partial = ""; error = ""; running = true; startMs = System.currentTimeMillis()
        sr = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(listener) }
        listen()
    }

    fun stop() {
        if (!running) return
        running = false; endMs = System.currentTimeMillis()
        if (partial.isNotBlank()) { add(partial); partial = "" }
        h.removeCallbacksAndMessages(null)
        try { sr?.stopListening(); sr?.cancel(); sr?.destroy() } catch (e: Exception) { }
        sr = null
    }
}

// ---------------------------------------------------------------- screens

private const val PARTS = 5

@Composable
fun PlacementScreen(st: Store, nav: Nav, act: MainActivity) {
    var step by remember { mutableStateOf(st.mapInt("plc", "step")) }
    fun next(n: Int) { st.putMap("plc", "step", n); step = n }
    val title = if (step in 1..PARTS) tr("Placement · part $step of $PARTS", "تحديد المستوى · الجزء $step من $PARTS") else tr("Placement test", "اختبار تحديد المستوى")
    when (step) {
        0 -> PlIntro(title, nav) { next(1) }
        1 -> PlGrammar(title, st, nav) { next(2) }
        2 -> PlSection(title, 'R', st, nav, act) { next(3) }
        3 -> PlSection(title, 'L', st, nav, act) { next(4) }
        4 -> PlWriting(title, st, nav) { next(5) }
        5 -> PlSpeaking(title, st, nav) { applyFullPlacement(st); next(6) }
        else -> PlResult(st, nav) { st.resetPlacement(); step = 0 }
    }
}

@Composable
private fun PartBar(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..PARTS) Box(Modifier.weight(1f).height(8.dp).background(if (i < step) Green else if (i == step) Mustard else Card, RoundedCornerShape(50)).border(1.5.dp, Ink, RoundedCornerShape(50)))
    }
}

@Composable
private fun PlIntro(title: String, nav: Nav, start: () -> Unit) {
    Screen(title, { nav.back() }) {
        Page {
            Text(tr("Find your real level", "اعرف مستواك الحقيقي"), style = H2)
            Text(tr("Each skill is tested the way the exam tests it. Your program is built from the results.", "كل مهارة تُختبر بطريقة الامتحان، وبرنامجك يُبنى من النتائج."), style = Body)
            for ((n, t, d) in listOf(
                Triple(1, tr("Grammar and vocabulary", "القواعد والمفردات"), tr("18 questions, from easy to hard. 12 minutes.", "18 سؤالًا من السهل للصعب. 12 دقيقة.")),
                Triple(2, tr("Reading", "القراءة"), tr("One academic passage, 10 questions. 15 minutes.", "قطعة أكاديمية و10 أسئلة. 15 دقيقة.")),
                Triple(3, tr("Listening", "الاستماع"), tr("A call and a short talk. The audio plays once. 10 questions.", "مكالمة وحديث قصير. التسجيل يُشغَّل مرة واحدة. 10 أسئلة.")),
                Triple(4, tr("Writing", "الكتابة"), tr("A short opinion essay, 150 words or more. 15 minutes.", "مقال رأي قصير، 150 كلمة أو أكثر. 15 دقيقة.")),
                Triple(5, tr("Speaking", "المحادثة"), tr("Talk about a topic for up to 2 minutes. The phone listens and measures your speech.", "تكلّم عن موضوع حتى دقيقتين. الهاتف يستمع ويقيس كلامك.")),
            )) NCard {
                Row { Tag("$n"); Spacer(Modifier.width(8.dp)); Text(t, style = H3) }
                Text(d, style = Small)
            }
            NCard(bg = Paper) {
                Text(tr("Before you start", "قبل أن تبدأ"), style = H3)
                Text(tr("Use headphones in a quiet place. You can stop between parts and continue later. Writing and Speaking results are estimates: the app measures your text and speech but cannot mark them like an examiner.",
                    "استخدم سماعات في مكان هادئ. يمكنك التوقف بين الأجزاء والإكمال لاحقًا. نتيجة الكتابة والمحادثة تقديرية: التطبيق يقيس النص والكلام لكنه لا يصحّح مثل الممتحن."), style = Body.copy(fontSize = 14.sp))
            }
            NButton(tr("Start", "ابدأ"), Modifier.fillMaxWidth()) { start() }
        }
    }
}

@Composable
private fun PlGrammar(title: String, st: Store, nav: Nav, done: () -> Unit) {
    val qs = remember { Content.bank.filter { it.placement && it.skill == "foundation" } }
    val ans = remember { mutableStateMapOf<Int, Int>() }
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (finished) return; finished = true
        st.putMap("plc", "fc", qs.indices.count { ans[it] == qs[it].answer }); st.putMap("plc", "ft", qs.size); done()
    }
    val left = rememberCountdown(12 * 60) { finish() }
    Screen(title, { nav.back() }, { TimerPill(left) }) {
        Page {
            PartBar(1)
            Text(tr("Grammar and vocabulary", "القواعد والمفردات"), style = H2)
            Text(tr("Choose the best answer. If you do not know, leave it: guessing makes your level less accurate.", "اختر الإجابة الأفضل. إذا لم تعرف اتركها: التخمين يقلل دقة مستواك."), style = Small)
            for ((i, q) in qs.withIndex()) NCard {
                Text("${i + 1}. ${q.q}", style = Body.copy(fontSize = 15.sp))
                for ((k, o) in q.options.withIndex()) OptionRow(o, ans[i] == k, "ABCD"[k % 4].toString()) { ans[i] = k }
            }
            Text(tr("${ans.size} of ${qs.size} answered", "تمت الإجابة عن ${ans.size} من ${qs.size}"), style = Small)
            NButton(tr("Finish part 1", "أنهِ الجزء 1"), Modifier.fillMaxWidth(), Mustard) { finish() }
        }
    }
}

@Composable
private fun PlSection(title: String, kind: Char, st: Store, nav: Nav, act: MainActivity, done: () -> Unit) {
    val s = (if (kind == 'R') Content.placeR else Content.placeL)
    if (s == null) { LaunchedEffect(Unit) { done() }; return }
    val ans = remember { mutableStateMapOf<String, String>() }
    var finished by remember { mutableStateOf(false) }
    var played by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { act.stopAudio() } }
    fun finish() {
        if (finished) return; finished = true; act.stopAudio()
        var c = 0; var t = 0
        for (g in s.groups) for (q in g.qs) { t++; if (isCorrect(q, ans["${s.id}|${q.n}"])) c++ }
        if (kind == 'R') { st.putMap("plc", "rc", c); st.putMap("plc", "rt", t) } else { st.putMap("plc", "lc", c); st.putMap("plc", "lt", t) }
        done()
    }
    val left = rememberCountdown(15 * 60, kind == 'R') { if (kind == 'R') finish() }
    Screen(title, { nav.back() }, { if (kind == 'R') TimerPill(left) }) {
        Page {
            PartBar(if (kind == 'R') 2 else 3)
            Text(if (kind == 'R') tr("Reading", "القراءة") else tr("Listening", "الاستماع"), style = H2)
            Text(s.title, style = H3)
            if (kind == 'R') {
                for ((l, t) in s.paras) Row { Text(l, style = H3.copy(color = Green), modifier = Modifier.width(24.dp)); Text(t, style = Body) }
            } else {
                Text(s.sub, style = Body)
                NCard(bg = Sky) {
                    Text(tr("Read the questions first. The audio plays once, like the real test.", "اقرأ الأسئلة أولًا. التسجيل يُشغَّل مرة واحدة كما في الامتحان."), style = Label)
                    Spacer(Modifier.height(8.dp))
                    NButton(if (speaking) tr("Playing...", "يعمل...") else if (played) tr("Played", "تم التشغيل") else tr("Play audio", "شغّل الصوت"), bg = Lime, enabled = !speaking && !played) {
                        if (act.playAsset(s.id)) { speaking = true; played = true; return@NButton }
                        val t = act.tts
                        if (t == null || !act.ttsReady) return@NButton
                        speaking = true; played = true
                        t.setSpeechRate(0.92f)
                        val speakers = s.script.map { it.first }.distinct()
                        val pitches = listOf(1.0f, 0.82f, 1.18f, 0.92f)
                        s.script.forEachIndexed { n, (sp, line) ->
                            t.setPitch(pitches[speakers.indexOf(sp).coerceAtLeast(0) % pitches.size])
                            t.speak(line, TextToSpeech.QUEUE_ADD, null, "p$n")
                            t.playSilentUtterance(if (n == 8) 1500 else 350, TextToSpeech.QUEUE_ADD, "ps$n")
                        }
                        t.setPitch(1.0f)
                    }
                    if (!act.ttsReady || !act.ttsEnglish) {
                        Text(tr("No English voice is ready. Install English (UK) in voice settings, then come back.", "لا يوجد صوت إنجليزي جاهز. ثبّت English (UK) من إعدادات الصوت ثم ارجع."), style = Small.copy(color = Ink))
                        NButton(tr("Voice settings", "إعدادات الصوت"), bg = Card) { openTtsSettings(act) }
                    }
                }
                LaunchedEffect(speaking) {
                    while (speaking) { delay(600); if (!act.audioBusy()) { delay(600); if (!act.audioBusy()) speaking = false } }
                }
            }
            for (g in s.groups) NCard {
                Text(g.ins, style = H3)
                if (g.form != null) Text(g.form, style = Label.copy(color = Green))
                for (q in g.qs) QuestionView(g, q, "${s.id}|${q.n}", ans)
            }
            NButton(tr("Finish this part", "أنهِ هذا الجزء"), Modifier.fillMaxWidth(), Mustard, enabled = kind == 'R' || (played && !speaking)) { finish() }
            if (kind == 'L' && !played) Text(tr("Play the audio to continue.", "شغّل التسجيل للمتابعة."), style = Small)
        }
    }
}

@Composable
private fun YesNo(q: String, value: Int?, set: (Int) -> Unit) {
    Text(q, style = Body.copy(fontSize = 15.sp))
    Row { Chip(tr("Yes", "نعم"), value == 1) { set(1) }; Chip(tr("Not sure", "غير متأكد"), value == 2) { set(2) }; Chip(tr("No", "لا"), value == 0) { set(0) } }
}

@Composable
private fun PlWriting(title: String, st: Store, nav: Nav, done: () -> Unit) {
    var text by remember { mutableStateOf(st.mapStr("draft", "placement")) }
    var submitted by remember { mutableStateOf(false) }
    val checks = remember { mutableStateMapOf<Int, Int>() }
    var run by remember { mutableStateOf(false) }
    LaunchedEffect(text) { delay(800); st.putMap("draft", "placement", text) }
    val left = rememberCountdown(15 * 60, run && !submitted) { run = false; submitted = true }
    val n = textStats(text).words
    Screen(title, { st.putMap("draft", "placement", text); nav.back() }, { TimerPill(left) }) {
        Page {
            PartBar(4)
            Text(tr("Writing", "الكتابة"), style = H2)
            NCard(bg = Paper) {
                Text("Some people think that students should study only subjects that are useful for their future jobs. Others believe they should also study subjects such as art and history.", style = EnText)
                Spacer(Modifier.height(6.dp))
                Text("Discuss both views and give your own opinion. Write at least 150 words.", style = EnText.copy(fontSize = 14.sp))
            }
            if (!submitted) {
                NButton(if (run) tr("Pause timer", "أوقف المؤقت") else tr("Start timer", "ابدأ المؤقت"), bg = Card) { run = !run }
                BasicTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 240.dp).background(Card, RoundedCornerShape(12.dp)).border(2.dp, Ink, RoundedCornerShape(12.dp)).padding(12.dp),
                    textStyle = EnText, cursorBrush = SolidColor(Ink))
                Text(tr("$n words", "$n كلمة") + if (n < 150) tr("  (aim for 150+)", "  (الهدف 150+)") else "  ✓", style = Label.copy(color = if (n >= 150) Green else Coral))
                NButton(tr("Submit essay", "سلّم المقال"), Modifier.fillMaxWidth(), Mustard, enabled = n >= 20) { submitted = true; st.putMap("draft", "placement", text) }
                Text(tr("Write without a translator or spell checker, so the result shows your real level.", "اكتب بدون مترجم أو مصحح إملائي حتى تعكس النتيجة مستواك الحقيقي."), style = Small)
            } else {
                NCard {
                    Text(tr("Check your own essay", "قيّم مقالك بنفسك"), style = H3)
                    Text(tr("Answer honestly. This is combined with what the app measures.", "أجب بصدق. يُدمج هذا مع ما يقيسه التطبيق."), style = Small)
                    Spacer(Modifier.height(6.dp))
                    YesNo(tr("Did you discuss both views and give a clear opinion?", "هل ناقشت الرأيين وقدّمت رأيًا واضحًا؟"), checks[0]) { checks[0] = it }
                    YesNo(tr("Did each paragraph have an idea, explanation and example?", "هل كان في كل فقرة فكرة وشرح ومثال؟"), checks[1]) { checks[1] = it }
                    YesNo(tr("Are you fairly sure most sentences are grammatically correct?", "هل أنت متأكد إلى حد ما أن معظم الجمل صحيحة نحويًا؟"), checks[2]) { checks[2] = it }
                }
                NButton(tr("Continue to speaking", "تابع إلى المحادثة"), Modifier.fillMaxWidth(), Mustard, enabled = checks.size == 3) {
                    val yes = checks.values.sumOf { if (it == 1) 1.0 else if (it == 2) 0.5 else 0.0 }
                    val ts = textStats(text)
                    val g = accBand(st.mapInt("plc", "fc"), st.mapInt("plc", "ft"))
                    st.putMap("plc", "w", writingLevel(text, 150, g, yes))
                    st.putMap("plc", "wn", ts.words); st.putMap("plc", "wl", ts.links); st.putMap("plc", "wv", (ts.variety * 100).toInt())
                    done()
                }
            }
        }
    }
}

@Composable
private fun PlSpeaking(title: String, st: Store, nav: Nav, done: () -> Unit) {
    val ctx = LocalContext.current
    val cap = remember { SpeechCapture(ctx) }
    var prep by remember { mutableStateOf(false) }
    var finishedTalk by remember { mutableStateOf(false) }
    var selfLen by remember { mutableStateOf<Int?>(null) }
    var selfPause by remember { mutableStateOf<Int?>(null) }
    var tick by remember { mutableStateOf(0) }
    DisposableEffect(Unit) { onDispose { cap.stop() } }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) cap.start() else cap.error = "permission" }
    val prepLeft = rememberCountdown(60, prep) { prep = false }
    LaunchedEffect(cap.running) {
        while (cap.running) { delay(500); tick++; if (cap.seconds >= 120) { cap.stop(); finishedTalk = true } }
    }
    Screen(title, { cap.stop(); nav.back() }) {
        Page {
            PartBar(5)
            Text(tr("Speaking", "المحادثة"), style = H2)
            NCard(bg = Lime) {
                Text("Describe a skill you learned that you think is useful.", style = EnText.copy(fontSize = 17.sp))
                for (b in listOf("what the skill is", "how you learned it", "how often you use it", "and explain why it is useful.")) Text("• $b", style = EnText.copy(fontSize = 15.sp))
            }
            if (!finishedTalk) {
                if (!cap.running) Row {
                    NButton(if (prep) tr("Preparing...", "تحضير...") else tr("1 minute to prepare", "دقيقة للتحضير"), bg = Card, enabled = !prep && prepLeft > 0) { prep = true }
                    Spacer(Modifier.width(8.dp)); if (prep) TimerPill(prepLeft)
                }
                NCard(bg = Paper) {
                    Text(tr("Speak for up to 2 minutes", "تكلّم حتى دقيقتين"), style = H3)
                    Text(tr("Speak in English as in the exam. The phone's speech recognition writes what you say, then measures your speed and vocabulary.", "تكلّم بالإنجليزية كما في الامتحان. التعرّف على الكلام في الهاتف يكتب ما تقوله ثم يقيس سرعتك ومفرداتك."), style = Small)
                    Spacer(Modifier.height(8.dp))
                    if (!cap.running) NButton(tr("Start speaking", "ابدأ الكلام"), bg = Coral) {
                        prep = false
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) cap.start() else perm.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        Row { TimerPill(maxOf(0, 120 - cap.seconds + tick * 0)); Spacer(Modifier.width(8.dp)); NButton(tr("I have finished", "انتهيت"), bg = Coral) { cap.stop(); finishedTalk = true } }
                        Spacer(Modifier.height(6.dp))
                        Text((cap.text + " " + cap.partial).trim().ifBlank { tr("Listening...", "يستمع...") }, style = EnText.copy(fontSize = 14.sp))
                    }
                    when (cap.error) {
                        "unavailable" -> Text(tr("Speech recognition is not available on this phone. Speak aloud anyway, then answer the questions.", "التعرّف على الكلام غير متاح في هذا الهاتف. تكلّم بصوت عالٍ على أي حال ثم أجب عن الأسئلة."), style = Small.copy(color = Ink))
                        "permission" -> Text(tr("Microphone permission was denied. Speak aloud anyway, then answer the questions.", "تم رفض إذن الميكروفون. تكلّم بصوت عالٍ على أي حال ثم أجب عن الأسئلة."), style = Small.copy(color = Ink))
                    }
                    if (cap.error.isNotEmpty()) NButton(tr("Skip to the questions", "انتقل إلى الأسئلة"), bg = Card) { finishedTalk = true }
                }
            } else {
                val words = textStats(cap.text).words
                if (words > 0) NCard(bg = Paper) {
                    Text(tr("What the phone heard", "ما سمعه الهاتف"), style = H3)
                    Text(tr("$words words in ${cap.seconds} seconds", "$words كلمة في ${cap.seconds} ثانية"), style = Label)
                    Text(cap.text, style = EnText.copy(fontSize = 14.sp))
                }
                NCard {
                    Text(tr("Check your own speaking", "قيّم كلامك بنفسك"), style = H3)
                    Text(tr("How long could you keep talking?", "كم استطعت أن تستمر في الكلام؟"), style = Body.copy(fontSize = 15.sp))
                    Column {
                        Chip(tr("Under 30 seconds", "أقل من 30 ثانية"), selfLen == 0) { selfLen = 0 }
                        Chip(tr("About a minute", "حوالي دقيقة"), selfLen == 1) { selfLen = 1 }
                        Chip(tr("Nearly 2 minutes", "قرابة دقيقتين"), selfLen == 2) { selfLen = 2 }
                    }
                    Text(tr("How often did you stop to search for words?", "كم مرة توقفت لتبحث عن كلمات؟"), style = Body.copy(fontSize = 15.sp))
                    Row { Chip(tr("Often", "كثيرًا"), selfPause == 0) { selfPause = 0 }; Chip(tr("Sometimes", "أحيانًا"), selfPause == 1) { selfPause = 1 }; Chip(tr("Rarely", "نادرًا"), selfPause == 2) { selfPause = 2 } }
                }
                NButton(tr("See my results", "شاهد نتيجتي"), Modifier.fillMaxWidth(), Mustard, enabled = selfLen != null && selfPause != null) {
                    val g = accBand(st.mapInt("plc", "fc"), st.mapInt("plc", "ft"))
                    st.putMap("plc", "s", speakingLevel(cap.text, cap.seconds, g, selfLen ?: 0, selfPause ?: 0))
                    st.putMap("plc", "sw", words); st.putMap("plc", "ss", cap.seconds)
                    done()
                }
                if (selfLen == null || selfPause == null) NButton(tr("Try speaking again", "جرّب الكلام مرة أخرى"), bg = Card) { finishedTalk = false }
            }
        }
    }
}

@Composable
private fun PlResult(st: Store, nav: Nav, retake: () -> Unit) {
    val pr = program(st)
    Screen(tr("Your level", "مستواك"), { nav.root("home") }) {
        Page {
            NCard(bg = Lime) {
                Text(tr("Estimated overall", "المستوى العام التقريبي"), style = H3)
                Text("${pr.current}", style = H1.copy(fontSize = 52.sp, lineHeight = 56.sp))
                Text(tr("Target ${pr.target}. Program: about ${pr.weeks} weeks.", "الهدف ${pr.target}. البرنامج: حوالي ${pr.weeks} أسبوع."), style = Label)
            }
            val sw = st.mapInt("plc", "sw"); val ss = st.mapInt("plc", "ss")
            val rows = listOf(
                Triple("foundation", "${st.mapInt("plc", "fc")} / ${st.mapInt("plc", "ft")}", tr("correct, from easy to hard questions", "صحيحة، من أسئلة سهلة إلى صعبة")),
                Triple("reading", "${st.mapInt("plc", "rc")} / ${st.mapInt("plc", "rt")}", tr("correct on an academic passage", "صحيحة في قطعة أكاديمية")),
                Triple("listening", "${st.mapInt("plc", "lc")} / ${st.mapInt("plc", "lt")}", tr("correct, audio played once", "صحيحة، والتسجيل مرة واحدة")),
                Triple("writing", tr("${st.mapInt("plc", "wn")} words", "${st.mapInt("plc", "wn")} كلمة"),
                    tr("${st.mapInt("plc", "wl")} linking words, word variety ${st.mapInt("plc", "wv")}%, plus grammar and your check", "${st.mapInt("plc", "wl")} أدوات ربط، تنوّع الكلمات ${st.mapInt("plc", "wv")}%، مع القواعد وتقييمك")),
                Triple("speaking", if (sw > 0 && ss > 0) tr("${sw * 60 / ss} words a minute", "${sw * 60 / ss} كلمة في الدقيقة") else tr("self-check", "تقييم ذاتي"),
                    tr("speech rate and vocabulary, plus grammar and your check", "سرعة الكلام والمفردات، مع القواعد وتقييمك")),
            )
            for ((sk, main, how) in rows) NCard {
                Row {
                    Text(skillName(sk), style = H3, modifier = Modifier.weight(1f))
                    Tag("${level(st, sk)}", if (level(st, sk) >= st.goal) Lime else if (level(st, sk) >= st.goal - 1) Mustard else Coral)
                }
                Text(main, style = Label); Text(how, style = Small)
            }
            Text(tr("Reading, Listening and Grammar are measured. Writing and Speaking are estimates; ask a teacher to check them and adjust the level in My program if needed.",
                "القراءة والاستماع والقواعد مقاسة فعلًا. الكتابة والمحادثة تقديرية؛ اطلب من معلم مراجعتها وعدّل المستوى من صفحة برنامجي إذا لزم."), style = Small)
            NButton(tr("See my program", "شاهد برنامجي"), Modifier.fillMaxWidth(), Mustard) { nav.root("home"); nav.go("program") }
            NButton(tr("Retake the test", "أعد الاختبار"), bg = Card) { retake() }
        }
    }
}
