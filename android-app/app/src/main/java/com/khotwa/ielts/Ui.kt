package com.khotwa.ielts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val Ink = Color(0xFF10261F)
val Paper = Color(0xFFF6F4EE)
val Card = Color(0xFFFFFFFF)
val Lime = Color(0xFFD9F27A)
val Mustard = Color(0xFFE5A935)
val Coral = Color(0xFFEE6C4D)
val Sky = Color(0xFF6AA9E6)
val Plum = Color(0xFF6B5BD2)
val Green = Color(0xFF0F7A5A)
val Muted = Color(0xFF5B6B64)

val Display = FontFamily(Font(R.font.bric_regular, FontWeight.Normal), Font(R.font.bric_bold, FontWeight.Bold), Font(R.font.bric_black, FontWeight.ExtraBold))
val Serif = FontFamily(Font(R.font.serif_regular, FontWeight.Normal), Font(R.font.serif_semibold, FontWeight.SemiBold))

val H1 = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp, color = Ink)
val H2 = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 25.sp, color = Ink)
val H3 = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp, color = Ink)
val Body = TextStyle(fontFamily = Serif, fontSize = 16.sp, lineHeight = 24.sp, color = Ink)
val Small = TextStyle(fontFamily = Display, fontSize = 13.sp, lineHeight = 17.sp, color = Muted)
val Label = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)

fun Modifier.neo(bg: Color = Card, r: Dp = 18.dp, off: Dp = 4.dp): Modifier = this
    .drawBehind { drawRoundRect(Ink, Offset(off.toPx(), off.toPx()), size, CornerRadius(r.toPx())) }
    .background(bg, RoundedCornerShape(r))
    .border(BorderStroke(2.dp, Ink), RoundedCornerShape(r))

@Composable
fun NCard(modifier: Modifier = Modifier, bg: Color = Card, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 2f else 0f, tween(80), label = "p")
    Box(modifier.padding(end = 5.dp, bottom = 5.dp)) {
        Column(
            Modifier.offset(s.dp, s.dp).fillMaxWidth().neo(bg, off = (4 - s).dp)
                .let { if (onClick != null) it.clickable(src, null) { onClick() } else it }
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun NButton(text: String, modifier: Modifier = Modifier, bg: Color = Lime, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 3f else 0f, tween(80), label = "b")
    Box(modifier.padding(end = 4.dp, bottom = 4.dp)) {
        Box(
            Modifier.offset(s.dp, s.dp).neo(if (enabled) bg else Color(0xFFDDDDD5), 14.dp, (3 - s).dp)
                .let { if (enabled) it.clickable(src, null) { onClick() } else it }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) { Text(text, style = Label) }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 8.dp, bottom = 8.dp).background(if (selected) Ink else Card, RoundedCornerShape(50))
            .border(2.dp, Ink, RoundedCornerShape(50)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 7.dp)
    ) { Text(text, style = Label.copy(color = if (selected) Lime else Ink)) }
}

@Composable
fun Tag(text: String, bg: Color = Lime) {
    Box(Modifier.background(bg, RoundedCornerShape(50)).border(1.5.dp, Ink, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, style = Small.copy(color = Ink, fontWeight = FontWeight.Bold))
    }
}

@Composable
fun Bar(frac: Float, color: Color = Green, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), tween(700), label = "bar")
    Box(modifier.fillMaxWidth().height(14.dp).background(Paper, RoundedCornerShape(50)).border(2.dp, Ink, RoundedCornerShape(50))) {
        if (f > 0.01f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color, RoundedCornerShape(50)))
    }
}

@Composable
fun rememberCountdown(totalSec: Int, running: Boolean = true, onExpire: () -> Unit): Int {
    var left by androidx.compose.runtime.saveable.rememberSaveable(totalSec) { mutableStateOf(totalSec) }
    val cb by rememberUpdatedState(onExpire)
    LaunchedEffect(totalSec, running) {
        while (running && left > 0) { delay(1000); left-- ; if (left == 0) cb() }
    }
    return left
}

fun mmss(s: Int) = "%d:%02d".format(s / 60, s % 60)

@Composable
fun TimerPill(left: Int) {
    Box(Modifier.background(if (left < 60) Coral else Mustard, RoundedCornerShape(50)).border(2.dp, Ink, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(mmss(left), style = Label)
    }
}

@Composable
fun Screen(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(Modifier.padding(end = 10.dp).neo(Card, 12.dp, 2.dp).clickable { onBack() }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text(if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) "→" else "←", style = H3) }
            }
            Text(title, style = H2, modifier = Modifier.weight(1f))
            trailing()
        }
        content()
    }
}
