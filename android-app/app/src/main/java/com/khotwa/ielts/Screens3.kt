package com.khotwa.ielts

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File

val seriesColors = listOf(Green, Mustard, Coral, Plum)

@Composable
fun ChartView(c: JSONObject) {
    when (c.optString("kind")) {
        "table" -> {
            val rows = c.getJSONArray("rows")
            Column(Modifier.fillMaxWidth().border(2.dp, Ink, RoundedCornerShape(10.dp))) {
                for (r in 0 until rows.length()) {
                    val row = rows.getJSONArray(r)
                    Row(Modifier.fillMaxWidth().background(if (r == 0) Lime else Card).padding(8.dp)) {
                        for (k in 0 until row.length()) Text(row.getString(k), style = if (r == 0) Label else Body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        "process" -> {
            val s = c.getJSONArray("steps")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until s.length()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).background(Mustard, RoundedCornerShape(50)).border(2.dp, Ink, RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Text("${i + 1}", style = Label) }
                    Spacer(Modifier.width(8.dp)); Text(s.getString(i), style = Body.copy(fontSize = 14.sp))
                }
            }
        }
        else -> {
            val line = c.optString("kind") == "line"
            val labels = (c.optJSONArray(if (line) "x" else "cats"))
            val ser = c.getJSONArray("series")
            var mx = 1.0
            for (i in 0 until ser.length()) { val v = ser.getJSONObject(i).getJSONArray("vals"); for (k in 0 until v.length()) mx = maxOf(mx, v.getDouble(k)) }
            Text(c.optString("unit"), style = Small)
            Canvas(Modifier.fillMaxWidth().height(170.dp).background(Card).border(2.dp, Ink, RoundedCornerShape(10.dp))) {
                val n = labels?.length() ?: 1
                val w = size.width - 24.dp.toPx(); val h = size.height - 24.dp.toPx()
                val slot = w / n
                for (i in 0 until ser.length()) {
                    val v = ser.getJSONObject(i).getJSONArray("vals"); val col = seriesColors[i % 4]
                    if (line) {
                        var prev: Offset? = null
                        for (k in 0 until v.length()) {
                            val p = Offset(12.dp.toPx() + slot * (k + 0.5f), 12.dp.toPx() + h - (v.getDouble(k) / mx * h).toFloat())
                            prev?.let { drawLine(col, it, p, 4.dp.toPx()) }
                            drawCircle(col, 5.dp.toPx(), p); prev = p
                        }
                    } else {
                        val bw = slot / (ser.length() + 1)
                        for (k in 0 until v.length()) {
                            val bh = (v.getDouble(k) / mx * h).toFloat()
                            drawRect(col, Offset(12.dp.toPx() + slot * k + bw * (i + 0.5f), 12.dp.toPx() + h - bh), Size(bw, bh))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) { for (k in 0 until (labels?.length() ?: 0)) Text(labels!!.getString(k), style = Small, modifier = Modifier.weight(1f)) }
            Row { for (i in 0 until ser.length()) { Box(Modifier.size(12.dp).background(seriesColors[i % 4])); Spacer(Modifier.width(4.dp)); Text(ser.getJSONObject(i).optString("name"), style = Small); Spacer(Modifier.width(12.dp)) } }
            if (!line) Text("Values: " + (0 until ser.length()).joinToString("; ") { i -> val o = ser.getJSONObject(i); o.optString("name") + " " + o.getJSONArray("vals").toString().trim('[', ']') }, style = Small)
        }
    }
}

private val LINKS = listOf("however", "although", "whereas", "while", "therefore", "moreover", "furthermore", "in addition", "for example", "for instance", "consequently", "in contrast", "on the other hand", "overall", "in conclusion", "because", "despite", "as a result")

fun analyse(text: String): String {
    val words = Regex("[A-Za-z']+").findAll(text).map { it.value.lowercase() }.toList()
    if (words.isEmpty()) return tr("Nothing written yet.", "لم تكتب شيئًا بعد.")
    val sents = text.split(Regex("[.!?]+")).count { it.isNotBlank() }
    val paras = text.split(Regex("\n\\s*\n")).count { it.isNotBlank() }
    val low = text.lowercase()
    val links = LINKS.filter { low.contains(it) }
    val rep = words.filter { it.length > 4 }.groupingBy { it }.eachCount().filter { it.value >= 4 }.keys
    return tr("Words: ${words.size}. Sentences: $sents (average ${words.size / maxOf(1, sents)} words). Paragraphs: $paras.\n", "الكلمات: ${words.size}. الجمل: $sents (متوسط ${words.size / maxOf(1, sents)} كلمة). الفقرات: $paras.\n") +
        tr("Linking words used: ", "أدوات الربط: ") + (if (links.isEmpty()) tr("none found", "لا يوجد") else links.joinToString(", ")) + ".\n" +
        (if (rep.isNotEmpty()) tr("Repeated often: ", "تكررت كثيرًا: ") + rep.joinToString(", ") + tr(". Try synonyms.\n", ". جرّب مرادفات.\n") else "") +
        (if (paras < 4) tr("Task 2 usually needs 4 or 5 paragraphs.\n", "المهمة 2 تحتاج عادة 4 أو 5 فقرات.\n") else "") +
        tr("This is a simple check. It cannot judge ideas, grammar or give a band.", "هذا فحص بسيط لا يقيّم الأفكار أو القواعد ولا يعطي درجة.")
}

@Composable
fun WritingScreen(id: String, st: Store, nav: Nav) {
    val mock = id == "mock"
    val t1 = Content.writing.filter { it.task == 1 && (it.type == st.type || it.type == "Both") }
    val items = if (mock) listOf(t1.first(), Content.writing.first { it.task == 2 }) else listOf(Content.writing.first { it.id == id })
    var tab by remember { mutableStateOf(0) }
    val w = items[tab]
    var text by remember(w.id) { mutableStateOf(st.mapStr("draft", w.id)) }
    var report by remember(w.id) { mutableStateOf("") }
    var model by remember(w.id) { mutableStateOf(false) }
    var run by remember { mutableStateOf(false) }
    val left = rememberCountdown((if (mock) 60 else w.minutes) * 60, run) { run = false }
    val count = Regex("[A-Za-z0-9']+").findAll(text).count()
    // Autosave: a short pause after typing, when leaving the screen, and on system back.
    LaunchedEffect(w.id, text) {
        kotlinx.coroutines.delay(800)
        if (text != st.mapStr("draft", w.id)) st.putMap("draft", w.id, text)
    }
    val latest by rememberUpdatedState(text)
    val wid by rememberUpdatedState(w.id)
    DisposableEffect(Unit) { onDispose { if (latest != st.mapStr("draft", wid)) st.putMap("draft", wid, latest) } }
    fun leave() {
        st.putMap("draft", w.id, text)
        if (count >= w.min && !st.has("wdone", w.id)) { st.add("wdone", w.id); st.bump("wn", w.id) }
        nav.back()
    }
    BackHandler { leave() }
    Screen(if (mock) tr("Writing mock", "محاكاة الكتابة") else tr("Writing", "الكتابة"), { leave() }, { TimerPill(left) }) {
        Page {
            if (mock) Row { for ((k, x) in items.withIndex()) Chip("Task ${x.task}", tab == k) { st.putMap("draft", w.id, text); tab = k } }
            Text(w.title, style = H2)
            Row { Tag("Task ${w.task}"); Spacer(Modifier.width(8.dp)); Tag("${w.minutes} min", Mustard); Spacer(Modifier.width(8.dp)); Tag("${w.min}+ words", Sky) }
            NCard(bg = Paper) { Text(w.prompt, style = Body) }
            w.chart?.let { ChartView(it) }
            if (w.tips.isNotEmpty()) NCard(bg = Lime) { Text("Tips", style = H3); for (t in w.tips) Text("• $t", style = Body.copy(fontSize = 14.sp)) }
            NButton(if (run) tr("Pause timer", "أوقف المؤقت") else tr("Start timer", "ابدأ المؤقت"), bg = Card) { run = !run }
            BasicTextField(
                text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 220.dp).background(Card, RoundedCornerShape(12.dp)).border(2.dp, Ink, RoundedCornerShape(12.dp)).padding(12.dp),
                textStyle = Body, cursorBrush = SolidColor(Ink)
            )
            Text(tr("$count words", "$count كلمة") + (if (count < w.min) tr("  (aim for ${w.min}+)", "  (الهدف ${w.min}+)") else "  ✓") + tr("  ·  saved automatically", "  ·  يُحفظ تلقائيًا"), style = Label.copy(color = if (count >= w.min) Green else Coral))
            Row {
                NButton(tr("Save draft", "حفظ المسودة"), bg = Lime) { st.putMap("draft", w.id, text); st.addDay() }
                Spacer(Modifier.width(8.dp))
                NButton(tr("Check text", "افحص النص"), bg = Sky) { report = analyse(text) }
            }
            if (report.isNotEmpty()) NCard { Text(report, style = Body.copy(fontSize = 14.sp)) }
            if (w.model != null) {
                NButton(if (model) tr("Hide model answer", "أخفِ الإجابة النموذجية") else tr("Show model answer", "اعرض الإجابة النموذجية"), bg = Mustard) { model = !model }
                if (model) NCard(bg = Paper) { Text(w.model, style = Body) ; Text(tr("A model shows one way to answer, not the only way.", "النموذج طريقة واحدة للإجابة وليس الطريقة الوحيدة."), style = Small) }
            }
        }
    }
}

@Composable
fun SpeakingScreen(id: String, st: Store, nav: Nav) {
    val ctx = LocalContext.current
    val sp = if (id == "mock") Content.speaking.first() else Content.speaking.first { it.id == id }
    val mock = id == "mock"
    var part by remember { mutableStateOf(1) }
    var rec by remember { mutableStateOf<MediaRecorder?>(null) }
    var hasRec by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<MediaPlayer?>(null) }
    var msg by remember { mutableStateOf("") }
    var prep by remember { mutableStateOf(false) }
    val file = remember { File(ctx.cacheDir, "speaking.m4a") }
    DisposableEffect(Unit) { onDispose { try { rec?.stop() } catch (e: Exception) {}; rec?.release(); playing?.release() } }
    fun startRec() {
        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setOutputFile(file.absolutePath); r.prepare(); r.start()
            rec = r; msg = tr("Recording...", "جارٍ التسجيل...")
        } catch (e: Exception) { msg = tr("Could not start recording on this device.", "تعذّر بدء التسجيل على هذا الجهاز.") }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startRec() else msg = tr("Microphone permission was denied. You can still practise aloud.", "تم رفض إذن الميكروفون. يمكنك التدرّب بصوت عالٍ.") }
    val left = rememberCountdown(60, prep) { prep = false }
    Screen(if (mock) tr("Speaking mock", "محاكاة المحادثة") else tr("Speaking", "المحادثة"), { nav.back() }) {
        Page {
            Row { for (p in 1..3) Chip(tr("Part $p", "الجزء $p"), part == p) { part = p } }
            Text(sp.topic, style = H2)
            when (part) {
                1 -> { Text(tr("Answer each question in 2 to 4 sentences.", "أجب عن كل سؤال في 2 إلى 4 جمل."), style = Small); for (q in sp.p1) NCard { Text(q, style = Body) } }
                2 -> {
                    NCard(bg = Lime) { Text(sp.cue, style = H3); for (b in sp.bullets) Text("• $b", style = Body) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NButton(if (prep) tr("Preparing...", "تحضير...") else tr("1 minute to prepare", "دقيقة للتحضير"), bg = Mustard) { prep = true }
                        Spacer(Modifier.width(10.dp)); if (prep) TimerPill(left)
                    }
                    Text(tr("Then speak for 1 to 2 minutes.", "ثم تكلّم من دقيقة إلى دقيقتين."), style = Small)
                }
                else -> { Text(tr("Give reasons and examples.", "قدّم أسبابًا وأمثلة."), style = Small); for (q in sp.p3) NCard { Text(q, style = Body) } }
            }
            NCard(bg = Paper) {
                Text(tr("Record yourself", "سجّل صوتك"), style = H3)
                Spacer(Modifier.height(6.dp))
                Row {
                    if (rec == null) NButton(tr("Record", "سجّل"), bg = Coral) {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec() else perm.launch(Manifest.permission.RECORD_AUDIO)
                    } else NButton(tr("Stop", "إيقاف"), bg = Coral) {
                        try { rec?.stop(); hasRec = true; msg = tr("Saved on this device.", "حُفظ على جهازك.") } catch (e: Exception) { msg = tr("Recording was too short.", "التسجيل قصير جدًا.") }
                        rec?.release(); rec = null
                    }
                    Spacer(Modifier.width(8.dp))
                    NButton(if (playing != null) tr("Stop playback", "أوقف التشغيل") else tr("Play back", "استمع"), bg = Sky, enabled = hasRec && rec == null) {
                        if (playing != null) { playing?.release(); playing = null } else {
                            try { val m = MediaPlayer(); m.setDataSource(file.absolutePath); m.prepare(); m.setOnCompletionListener { it.release(); playing = null }; m.start(); playing = m } catch (e: Exception) { msg = tr("Could not play the recording.", "تعذّر تشغيل التسجيل.") }
                        }
                    }
                }
                if (msg.isNotEmpty()) Text(msg, style = Small)
                Text(tr("The recording is not uploaded or scored. Listen for pauses, fillers and clear ideas.", "التسجيل لا يُرفع ولا يُقيَّم. استمع للتوقفات والكلمات الحشو ووضوح الأفكار."), style = Small)
            }
            Row {
                if (part < 3) NButton(tr("Next part", "الجزء التالي")) { part++ } else NButton(tr("Finish", "إنهاء"), bg = Lime) { st.bump("spn", sp.id); st.addHistory(if (mock) tr("Speaking mock", "محاكاة المحادثة") else tr("Speaking: ", "محادثة: ") + sp.topic, 0, 0, null); nav.back() }
            }
        }
    }
}
