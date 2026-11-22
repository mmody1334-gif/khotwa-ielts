package com.khotwa.ielts

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

class Nav {
    val stack: SnapshotStateList<String> = mutableStateListOf("home")
    var result by mutableStateOf<Result?>(null)
    var menu by mutableStateOf(false)
    fun go(r: String) { menu = false; if (stack.last() != r) stack.add(r) }
    fun root(r: String) { menu = false; stack.clear(); stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun replace(r: String) { stack[stack.size - 1] = r }
}

class MainActivity : ComponentActivity() {
    var tts: TextToSpeech? = null
    var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Content.load(this)
        val store = Store(this)
        val nav = Nav()
        intent.getStringExtra("route")?.let { if (it != "home") nav.stack.add(it) }
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) { tts?.language = Locale.UK; ttsReady = true }
        }
        setContent {
            BackHandler(enabled = nav.menu || nav.stack.size > 1) { if (nav.menu) nav.menu = false else nav.back() }
            Box(Modifier.fillMaxSize().background(Paper).systemBarsPadding().imePadding()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        AnimatedContent(nav.stack.last(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { route ->
                            Route(route, store, nav, this@MainActivity)
                        }
                    }
                    val top = nav.stack.last()
                    if (top in listOf("home", "plan", "practice", "words")) BottomBar(top, nav)
                }
                AnimatedVisibility(nav.menu, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
                    MenuOverlay(nav)
                }
            }
        }
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

@Composable
fun BottomBar(cur: String, nav: Nav) {
    Row(Modifier.fillMaxWidth().background(Ink).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
        for ((r, label) in listOf("home" to "Home", "plan" to "Plan", "practice" to "Practice", "words" to "Words")) {
            val on = cur == r
            Box(Modifier.background(if (on) Lime else Ink, RoundedCornerShape(50)).clickable { nav.root(r) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(label, style = Label.copy(color = if (on) Ink else Paper))
            }
        }
        Box(Modifier.clickable { nav.menu = true }.padding(horizontal = 14.dp, vertical = 8.dp)) { Text("More ☰", style = Label.copy(color = Paper)) }
    }
}

@Composable
fun MenuOverlay(nav: Nav) {
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.92f)).clickable { nav.menu = false }, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Menu", style = H1.copy(color = Lime))
            for ((r, l) in listOf("lessons" to "Lessons", "mocks" to "Mock tests", "progress" to "Progress", "about" to "About and limits", "settings" to "Settings")) {
                Text(l, style = H1.copy(color = Paper), modifier = Modifier.clickable { nav.go(r) }.padding(vertical = 4.dp))
            }
            Text("Close", style = Label.copy(color = Mustard), modifier = Modifier.clickable { nav.menu = false }.padding(vertical = 10.dp))
        }
    }
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun Route(route: String, st: Store, nav: Nav, act: MainActivity) {
    val p = route.split(":")
    when (p[0]) {
        "home" -> HomeScreen(st, nav)
        "plan" -> PlanScreen(st, nav)
        "practice" -> PracticeScreen(st, nav)
        "words" -> WordsScreen(st, nav)
        "lessons" -> LessonsScreen(st, nav)
        "lesson" -> LessonScreen(p[1], st, nav)
        "mocks" -> MocksScreen(nav)
        "progress" -> ProgressScreen(st, nav)
        "about" -> AboutScreen(nav)
        "settings" -> SettingsScreen(st, nav)
        "quiz" -> QuizScreen(p[1], p.getOrElse(2) { "" }, st, nav)
        "paper" -> PaperScreen(p[1][0], p[2], st, nav, act)
        "write" -> WritingScreen(p[1], st, nav)
        "speak" -> SpeakingScreen(p[1], st, nav)
        "results" -> ResultsScreen(st, nav)
        else -> HomeScreen(st, nav)
    }
}
