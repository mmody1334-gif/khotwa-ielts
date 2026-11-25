package com.khotwa.ielts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** A full test taken on ieltsonlinetests.com. Only links and structure are kept; questions, audio and answers stay on that site. */
class External(
    val id: String, val title: String, val source: String, val collection: String, val full: String, val testId: String,
    val listening: String, val reading: String, val writing: String, val speaking: String,
    val speakingParts: List<Int>?, val speakingMinutes: Int
)

/** Saves Reading and Listening scores (out of 40) from a full online test and moves the levels strongly, as for a full mock. */
fun recordExternal(st: Store, id: String, r: Int?, l: Int?) {
    val x = Content.external.firstOrNull { it.id == id } ?: return
    if (r != null) { st.addStat("sk", "reading", r, 40); updateLevel(st, "reading", r, 40, 'R') }
    if (l != null) { st.addStat("sk", "listening", l, 40); updateLevel(st, "listening", l, 40, 'L') }
    st.putMap("ext", id, "${r ?: ""}/${l ?: ""}"); st.add("extdone", id)
    st.addHistory(x.title, (r ?: 0) + (l ?: 0), (if (r != null) 40 else 0) + (if (l != null) 40 else 0), null)
    st.replanFuture()
}

@Composable
fun ExternalList(st: Store, nav: Nav) {
    if (Content.external.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text(tr("Full tests online", "اختبارات كاملة أونلاين"), style = H2)
    Text(tr("Real-format full tests on ieltsonlinetests.com (free sign-in needed). Take them there, then enter your Reading and Listening scores here to update your levels.",
        "اختبارات كاملة بشكل الامتحان على موقع ieltsonlinetests.com (يحتاج تسجيل دخول مجاني). حلّها هناك، ثم أدخل درجتك في القراءة والاستماع هنا لتحديث مستواك."), style = Small)
    for (x in Content.external) NCard(onClick = { nav.go("ext:${x.id}") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(x.title, style = H3.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr), modifier = Modifier.weight(1f))
            if (st.has("extdone", x.id)) Tag(tr("Done", "تم"), Lime)
        }
        val sc = st.mapStr("ext", x.id)
        Text(if (sc.isNotBlank()) tr("Your scores (R/L): ", "درجاتك (ق/س): ") + sc else tr("Listening 40 · Reading 40 · Writing 2 tasks · Speaking", "استماع 40 · قراءة 40 · كتابة مهمتان · محادثة"), style = Small)
    }
}

@Composable
fun ExtScreen(id: String, st: Store, nav: Nav) {
    val x = Content.external.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    fun open(u: String) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { } }
    val parts = st.mapStr("ext", id).split("/")
    var r by remember { mutableStateOf(parts.getOrElse(0) { "" }) }
    var l by remember { mutableStateOf(parts.getOrElse(1) { "" }) }
    var msg by remember { mutableStateOf("") }
    Screen(tr("Full test online", "اختبار كامل أونلاين"), { nav.back() }) {
        if (x == null) { Page { Text(tr("Test not found.", "الاختبار غير موجود."), style = Body) }; return@Screen }
        Page {
            Text(x.title, style = H2.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
            Text(tr("Source: ", "المصدر: ") + x.source + tr(" · test ", " · اختبار ") + x.testId, style = Small)
            NCard(bg = Paper) {
                Text(tr("The questions, audio and answers are on ieltsonlinetests.com, not in this app. You need a free account there. Sections run in order, and each starts with a short guidance video.",
                    "الأسئلة والصوت والإجابات على موقع ieltsonlinetests.com وليست في هذا التطبيق. تحتاج حسابًا مجانيًا هناك. الأجزاء بالترتيب، وكل جزء يبدأ بفيديو إرشادي قصير."), style = Body)
            }
            NButton(tr("Start the full test ↗", "ابدأ الاختبار الكامل ↗"), Modifier.fillMaxWidth(), Lime) { open(x.full) }
            val sp = x.speakingParts?.let { tr(" Parts: ", " الأجزاء: ") + it.joinToString(" + ") + (if (x.speakingMinutes > 0) tr(" · ${x.speakingMinutes} min", " · ${x.speakingMinutes} دقيقة") else "") } ?: ""
            for ((name, info, url) in listOf(
                Triple(tr("Listening", "الاستماع"), tr("40 questions, 4 parts, about 32 min. Audio plays on the site only.", "40 سؤالًا، 4 أجزاء، حوالي 32 دقيقة. الصوت على الموقع فقط."), x.listening),
                Triple(tr("Reading", "القراءة"), tr("40 questions, 3 passages (13, 13, 14), 60 min.", "40 سؤالًا، 3 قطع (13، 13، 14)، 60 دقيقة."), x.reading),
                Triple(tr("Writing", "الكتابة"), tr("2 tasks, 60 min. Check your essays with \"Estimate my level\" in the app's Writing practice.", "مهمتان، 60 دقيقة. قيّم مقالك بزر «قدّر مستواي» في تدريب الكتابة بالتطبيق."), x.writing),
                Triple(tr("Speaking", "المحادثة"), tr("With a live examiner by video call; questions are not published as text.", "مع ممتحن مباشر بمكالمة فيديو؛ الأسئلة لا تُنشر كنص.") + sp, x.speaking),
            )) NCard {
                Text(name, style = H3); Text(info, style = Small)
                Spacer(Modifier.height(6.dp)); NButton(tr("Open ↗", "افتح ↗"), bg = Card) { open(url) }
            }
            NCard(bg = Lime) {
                Text(tr("Enter your scores", "أدخل درجاتك"), style = H3)
                Text(tr("After you submit on the site, enter how many you got right out of 40. Leave a box empty if you did not take that section.", "بعد التسليم في الموقع، أدخل عدد الإجابات الصحيحة من 40. اترك الخانة فارغة إذا لم تحل هذا الجزء."), style = Small)
                Box40(tr("Reading", "القراءة"), r) { r = it }
                Box40(tr("Listening", "الاستماع"), l) { l = it }
                NButton(tr("Save and update my levels", "احفظ وحدّث مستواي"), bg = Mustard) {
                    val rv = r.toIntOrNull(); val lv = l.toIntOrNull()
                    if ((rv == null && lv == null) || (rv != null && rv > 40) || (lv != null && lv > 40)) { msg = tr("Enter a number from 0 to 40.", "أدخل رقمًا من 0 إلى 40."); return@NButton }
                    recordExternal(st, id, rv, lv)
                    msg = tr("Saved. ", "تم الحفظ. ") +
                        (rv?.let { tr("Reading band about ${accBand(it, 40, 'R')}. ", "القراءة تقريبًا ${accBand(it, 40, 'R')}. ") } ?: "") +
                        (lv?.let { tr("Listening band about ${accBand(it, 40, 'L')}. ", "الاستماع تقريبًا ${accBand(it, 40, 'L')}. ") } ?: "") +
                        tr("Your plan was updated.", "تم تحديث خطتك.")
                }
                if (msg.isNotEmpty()) Text(msg, style = Label)
            }
            Text(tr("Independent links. Khotwa is not connected to ieltsonlinetests.com.", "روابط مستقلة. خطوة غير مرتبطة بموقع ieltsonlinetests.com."), style = Small)
        }
    }
}

@Composable
private fun Box40(label: String, v: String, set: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = Label, modifier = Modifier.width(110.dp))
        BasicTextField(v, { t -> set(t.filter { it.isDigit() }.take(2)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(80.dp).background(Card, RoundedCornerShape(10.dp)).border(2.dp, Ink, RoundedCornerShape(10.dp)).padding(10.dp), textStyle = Body, cursorBrush = SolidColor(Ink))
        Text("  / 40", style = Label)
    }
}
