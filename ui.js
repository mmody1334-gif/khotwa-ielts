'use strict';
/* Khotwa IELTS web app: screens. Mirrors the Android screens. */

const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const IOS = /iPhone|iPad|iPod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const ANDROID = /Android/i.test(navigator.userAgent);
const b1 = x => Number(x).toFixed(1);
const fmt = s => { s = Math.max(0, Math.ceil(s)); return String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0'); };
let tt; function toast(t) { const e = $('#toast'); e.textContent = t; e.classList.add('show'); clearTimeout(tt); tt = setTimeout(() => e.classList.remove('show'), 3000); }

/* ---------------------------------------------------------------- tiny view toolkit */
let A = {}, aN = 0;                       // action registry, rebuilt on each render
const act = fn => { const k = 'a' + (aN++); A[k] = fn; return k; };
const btn = (label, fn, cls = '', dis = false) => `<button class="btn ${cls}" data-a="${act(fn)}" ${dis ? 'disabled' : ''}>${label}</button>`;
const chip = (label, on, fn) => `<button class="chip ${on ? 'on' : ''}" data-a="${act(fn)}">${label}</button>`;
const go = (label, h, cls = '') => `<a class="btn ${cls}" href="#${h}">${label}</a>`;
const tile = (inner, h, cls = '') => h ? `<a class="tile ${cls}" href="#${h}">${inner}</a>` : `<div class="tile ${cls}">${inner}</div>`;
const bar = (f, col = 'var(--em)') => `<div class="bar"><i style="width:${Math.round(clamp(f, 0, 1) * 100)}%;background:${col}"></i></div>`;
const pill = (t, bg = '') => `<span class="pill" style="${bg ? 'background:' + bg : ''}">${t}</span>`;
const LIME = 'var(--lime)', MUS = 'var(--mus)', COR = 'var(--cor)', SKY = 'var(--sky)';
const lvColor = lv => lv >= S.goal ? LIME : lv >= S.goal - 1 ? MUS : COR;

let V = {}, vKey = '', TICK = null;        // per-screen state, reset when the route changes
let LAST = null;                           // last result
function rerender() { render(true); }

/* ---------------------------------------------------------------- audio: text to speech */
const TTS = {
  voice: null, speaking: false,
  pick() { const v = speechSynthesis.getVoices(); this.voice = v.find(x => /en[-_]GB/i.test(x.lang)) || v.find(x => /^en/i.test(x.lang)) || null; },
  ok() { return 'speechSynthesis' in window; },
  el: null,
  /** Plays the recorded mp3 for a section when it exists, otherwise reads the script with the device voice. */
  play(script, onEnd, gapAfter = -1, id = '') {
    if (id && C.audio && C.audio[id]) {
      this.stop(); this.speaking = true;
      const el = this.el = new Audio('/shared/audio/' + id + '.mp3?v=' + C.audio[id]);
      el.onended = el.onerror = () => { this.speaking = false; onEnd && onEnd(); };
      el.play().catch(() => { this.speaking = false; this.el = null; this.play(script, onEnd, gapAfter); });
      return;
    }
    if (!this.ok()) return;
    speechSynthesis.cancel(); this.pick();
    const sp = [...new Set(script.map(x => x[0]))], pitches = [1, 0.82, 1.18, 0.92];
    this.speaking = true;
    script.forEach(([s, line], i) => {
      const u = new SpeechSynthesisUtterance(line);
      u.lang = 'en-GB'; if (this.voice) u.voice = this.voice; u.rate = 0.92; u.pitch = pitches[sp.indexOf(s) % 4];
      if (i === script.length - 1) u.onend = u.onerror = () => { this.speaking = false; onEnd && onEnd(); };
      speechSynthesis.speak(u);
      if (i === gapAfter) { const p = new SpeechSynthesisUtterance('.'); p.volume = 0; p.rate = 0.3; speechSynthesis.speak(p); }
    });
  },
  stop() { if (this.el) { this.el.pause(); this.el = null; } if (this.ok()) speechSynthesis.cancel(); this.speaking = false; },
};
if (TTS.ok()) { TTS.pick(); speechSynthesis.onvoiceschanged = () => TTS.pick(); }

/* ---------------------------------------------------------------- speech recognition (Part 2 measuring) */
function SpeechCap(onUpdate) {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  const c = {
    available: !!SR, text: '', partial: '', running: false, error: '', startMs: 0, endMs: 0, r: null,
    get seconds() { return Math.max(0, Math.floor(((this.running ? Date.now() : this.endMs) - this.startMs) / 1000)); },
    start() { if (!SR) { this.error = 'unavailable'; onUpdate(); return; } this.text = ''; this.partial = ''; this.error = ''; this.running = true; this.startMs = Date.now(); this.go(); },
    go() {
      const r = new SR(); r.lang = 'en-GB'; r.continuous = true; r.interimResults = true;
      r.onresult = e => { let interim = ''; for (let i = e.resultIndex; i < e.results.length; i++) { const x = e.results[i]; if (x.isFinal) this.text = (this.text + ' ' + x[0].transcript).trim(); else interim += x[0].transcript; } this.partial = interim; onUpdate(); };
      r.onerror = e => { if (e.error === 'not-allowed' || e.error === 'service-not-allowed') { this.error = 'permission'; this.stop(); onUpdate(); } };
      r.onend = () => { if (this.partial.trim().split(/\s+/).length >= 2) this.text = (this.text + ' ' + this.partial).trim(); this.partial = ''; if (this.running) setTimeout(() => this.running && this.go(), 200); };
      this.r = r; try { r.start(); } catch (e) { }
    },
    stop() { if (!this.running) return; this.running = false; this.endMs = Date.now(); if (this.partial) { this.text = (this.text + ' ' + this.partial).trim(); this.partial = ''; } try { this.r && this.r.stop(); } catch (e) { } },
  };
  return c;
}

/* ---------------------------------------------------------------- recording for playback */
const REC = { mr: null, stream: null, url: '', chunks: [] };
async function recStart(onChange) {
  try {
    REC.stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    REC.chunks = []; REC.mr = new MediaRecorder(REC.stream);
    REC.mr.ondataavailable = e => e.data.size && REC.chunks.push(e.data);
    REC.mr.onstop = () => { if (REC.url) URL.revokeObjectURL(REC.url); REC.url = URL.createObjectURL(new Blob(REC.chunks, { type: REC.mr.mimeType })); REC.stream.getTracks().forEach(t => t.stop()); REC.mr = null; onChange(); };
    REC.mr.start(); onChange();
  } catch (e) { toast(tr('Microphone permission was denied. You can still practise aloud.', 'تم رفض إذن الميكروفون. يمكنك التدرّب بصوت عالٍ.')); }
}
function recStop() { try { REC.mr && REC.mr.state !== 'inactive' && REC.mr.stop(); } catch (e) { } }

/* ---------------------------------------------------------------- shared pieces */
function header(title, back = true, extra = '') {
  return `<div class="row" style="margin-bottom:10px">${back ? `<button class="btn" style="min-height:40px;padding:4px 14px" data-a="${act(() => history.length > 1 ? history.back() : (location.hash = 'home'))}">${I18n.ar && !V.ltr ? '→' : '←'}</button>` : ''}<h1 style="flex:1;font-size:clamp(24px,6.5vw,34px)">${title}</h1>${extra}</div>`;
}
const timer = (id, secs) => `<span class="timer ${secs < 60 ? 'low' : ''}" id="${id}">${fmt(secs)}</span>`;
function countdown(key, total, run = true) {          // keeps time across re-renders of the same screen
  const t = V[key] || (V[key] = { left: total, last: Date.now(), run });
  return t;
}
function tickCountdown(t, onEnd) { const n = Date.now(); if (t.run && t.left > 0) { t.left -= (n - t.last) / 1000; if (t.left <= 0) { t.left = 0; onEnd && onEnd(); } } t.last = n; }

function questionHTML(g, q, key, ans, onAns) {
  let h = `<div class="qblock en"><span class="qn">${q.n}</span>${esc(q.q)}`;
  if (q.t === 'gap') h += `<input class="inp" style="margin-top:8px" data-in="${act(v => { ans[key] = v; onAns && onAns(); })}" value="${esc(ans[key] || '')}" autocomplete="off" autocapitalize="off" spellcheck="false">`;
  else h += `<div class="options">${optionsOf(g, q).map((o, k) => `<button class="option ${String(ans[key]) === String(k) ? 'selected' : ''}" data-a="${act(() => { ans[key] = String(k); onAns && onAns(); rerender(); })}"><span class="letter">${q.t === 'mcq' ? 'ABCD'[k % 4] : (q.t === 'sel' ? '' : '•')}</span><span>${esc(o)}</span></button>`).join('')}</div>`;
  return h + '</div>';
}
function groupsHTML(s, ans, onAns) {
  return s.groups.map(g => `<div class="ins">${esc(g.ins)}</div>${g.form ? `<p class="en" style="color:var(--em);font-weight:800">${esc(g.form)}</p>` : ''}${g.qs.map(q => questionHTML(g, q, s.id + '|' + q.n, ans, onAns)).join('')}`).join('');
}
function passageHTML(s) { return `<div class="passage en">${s.paras.map(([l, t]) => `<p><span class="pl">${esc(l)}</span>${esc(t)}</p>`).join('')}</div>`; }

/* charts for Writing Task 1 */
function barSVG(c) { const W = 520, H = 300, L = 44, B = 48, T = 16, cols = ['#10261f', '#ee6c4d', '#6aa9e6']; const max = Math.max(...c.series.flatMap(s => s.vals)) * 1.15, gw = (W - L - 10) / c.cats.length; let o = `<svg class="chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="${esc(c.unit)}">`; for (let i = 0; i <= 4; i++) { const y = T + (H - B - T) * (1 - i / 4); o += `<line x1="${L}" x2="${W - 6}" y1="${y}" y2="${y}" stroke="#10261f22"/><text x="${L - 6}" y="${y + 4}" font-size="11" text-anchor="end" fill="#5d6b65">${Math.round(max * i / 4)}</text>`; } c.cats.forEach((cat, i) => { const bw = (gw - 14) / c.series.length; c.series.forEach((s, j) => { const h = (H - B - T) * s.vals[i] / max, x = L + i * gw + 7 + j * bw, y = H - B - h; o += `<rect x="${x}" y="${y}" width="${bw - 3}" height="${h}" rx="4" fill="${cols[j]}"/><text x="${x + (bw - 3) / 2}" y="${y - 4}" font-size="10" text-anchor="middle" fill="#10261f">${s.vals[i]}</text>`; }); o += `<text x="${L + i * gw + gw / 2}" y="${H - 28}" font-size="11" text-anchor="middle" fill="#10261f">${esc(cat)}</text>`; }); c.series.forEach((s, j) => { o += `<rect x="${L + j * 130}" y="${H - 16}" width="12" height="12" rx="3" fill="${cols[j]}"/><text x="${L + j * 130 + 17}" y="${H - 6}" font-size="11" fill="#10261f">${esc(s.name)}</text>`; }); return o + `<text x="${L}" y="11" font-size="11" fill="#5d6b65">${esc(c.unit)}</text></svg>`; }
function lineSVG(c) { const W = 520, H = 300, L = 40, B = 52, T = 18, cols = ['#10261f', '#ee6c4d']; const max = Math.max(...c.series.flatMap(s => s.vals)) * 1.1, n = c.x.length, sx = i => L + (W - L - 16) * i / (n - 1), sy = v => T + (H - B - T) * (1 - v / max); let o = `<svg class="chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="${esc(c.unit)}">`; for (let i = 0; i <= 4; i++) { const y = T + (H - B - T) * (1 - i / 4); o += `<line x1="${L}" x2="${W - 6}" y1="${y}" y2="${y}" stroke="#10261f22"/><text x="${L - 6}" y="${y + 4}" font-size="11" text-anchor="end" fill="#5d6b65">${Math.round(max * i / 4)}</text>`; } c.x.forEach((x, i) => o += `<text x="${sx(i)}" y="${H - 30}" font-size="11" text-anchor="middle">${esc(x)}</text>`); c.series.forEach((s, j) => { o += `<polyline fill="none" stroke="${cols[j]}" stroke-width="3" points="${s.vals.map((v, i) => sx(i) + ',' + sy(v)).join(' ')}"/>` + s.vals.map((v, i) => `<circle cx="${sx(i)}" cy="${sy(v)}" r="4" fill="${cols[j]}"/><text x="${sx(i)}" y="${sy(v) - 8}" font-size="10" text-anchor="middle">${v}</text>`).join('') + `<rect x="${L + j * 170}" y="${H - 16}" width="12" height="12" rx="3" fill="${cols[j]}"/><text x="${L + j * 170 + 17}" y="${H - 6}" font-size="11">${esc(s.name)}</text>`; }); return o + '</svg>'; }
function chartHTML(c) { if (!c) return ''; if (c.kind === 'table') return `<table class="t en">${c.rows.map((r, i) => `<tr>${r.map(x => i ? `<td>${esc(x)}</td>` : `<th>${esc(x)}</th>`).join('')}</tr>`).join('')}</table>`; if (c.kind === 'bar') return barSVG(c); if (c.kind === 'line') return lineSVG(c); if (c.kind === 'process') return `<div class="proc">${c.steps.map((s, i) => `<div><b>${i + 1}</b><span class="en" style="font-size:15px">${esc(s)}</span></div>`).join('')}</div>`; return ''; }

function taskRow(day, t) {
  const k = doneKey(day, t), dn = S.has('pdone', k);
  return `<div class="task ${dn ? 'done' : ''}"><button class="chk" aria-pressed="${dn}" data-a="${act(() => { S.toggle('pdone', k); rerender(); })}"></button><a href="#${troute(t)}" style="flex:1;text-decoration:none"><b><bdi>${esc(tlabel(t))}</bdi></b></a><span class="skilldot ${({ reading: 'c-rea', listening: 'c-lis', writing: 'c-wri', speaking: 'c-spe' })[tskill(t)] || 'c-fnd'}"></span></div>`;
}
const typeName = t => t === 'Academic' ? tr('Academic', 'أكاديمي') : tr('General Training', 'عام');
function dateIn(days) { return new Date(Date.now() + days * 864e5).toLocaleDateString(I18n.ar ? 'ar' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' }); }

/* ---------------------------------------------------------------- home */
function vHome() {
  const [pr, plan] = getPlan(), day = programDay(), tasks = tasksFor(day, plan), part = S.mapInt('plc', 'step');
  const cd = Math.min(day, pr.days);
  let h = '';
  if (ANDROID) h += `<a class="apkbar" href="/khotwa-ielts.apk" download="khotwa-ielts.apk"><span class="apkico" aria-hidden="true">&#9660;</span><span><b>${tr('Get the Android app', 'حمّل تطبيق أندرويد')}</b><small>${tr('Works offline · same features', 'يعمل بدون إنترنت · نفس المزايا')}</small></span></a>`;
  if (IOS && !navigator.standalone && !R.iosHint) h += `<div class="notice"><b>${tr('Use it like an app on iPhone', 'استخدمه كتطبيق على الآيفون')}</b><br>${tr('In Safari tap Share, then Add to Home Screen. It then opens full screen and works offline.', 'في سفاري اضغط مشاركة ثم «إضافة إلى الشاشة الرئيسية». سيفتح بملء الشاشة ويعمل بدون إنترنت.')} ${btn(tr('Got it', 'تمام'), () => { R.iosHint = 1; save(); rerender(); }, '')}</div>`;
  h += `<section class="hero"><div class="blob"></div><span class="eyebrow">${esc(typeName(S.type))} · ${tr('GOAL', 'الهدف')} ${b1(S.goal)}</span><h2 style="margin:8px 0">${tr('A personal IELTS program that adapts to your results.', 'برنامج آيلتس شخصي يتكيّف مع نتائجك.')}</h2>
    <div class="row" style="justify-content:flex-start">${pill(tr(S.minutes + ' min', S.minutes + ' دقيقة'), SKY)}${pill(tr('Streak ', 'متتالية ') + S.streak(), COR)}${R.exam && R.exam >= today() ? pill(tr(`Exam in ${R.exam - today()} days`, `الامتحان بعد ${R.exam - today()} يوم`), LIME) : ''}</div></section>`;
  h += assignHTML();
  const due = mistakesDue().length;
  if (due) h += tile(`<h3>${tr(`${due} mistakes to review`, `${due} أخطاء للمراجعة`)}</h3><small>${tr('Questions you got wrong come back until you get them right twice.', 'الأسئلة التي أخطأت فيها ترجع حتى تجيبها صح مرتين.')}</small>`, 'review', 'c-spe');
  if (!S.placed) h += tile(`<h2>${part >= 1 && part <= 5 ? tr(`Continue the placement test (part ${part} of 5)`, `أكمل اختبار تحديد المستوى (الجزء ${part} من 5)`) : tr('Start with the placement test', 'ابدأ باختبار تحديد المستوى')}</h2><p>${tr('5 parts: grammar, reading, listening, writing and speaking, about 50 minutes. You can stop between parts.', '5 أجزاء: القواعد والقراءة والاستماع والكتابة والمحادثة، حوالي 50 دقيقة. يمكنك التوقف بين الأجزاء.')}</p>`, 'placement', 'c-fnd');
  else h += tile(`<h2>${tr(`Now about ${b1(pr.current)} → target ${b1(pr.target)}`, `الآن تقريبًا ${b1(pr.current)} ← الهدف ${b1(pr.target)}`)}</h2><b>${tr(`Week ${Math.floor((cd + 6) / 7)} of ${pr.weeks} · `, `الأسبوع ${Math.floor((cd + 6) / 7)} من ${pr.weeks} · `)}${phaseName(phaseOf(cd, pr.days))}</b><p class="small">${tr('Tap to see your program and what to improve.', 'اضغط لرؤية برنامجك وما تحتاج لتحسينه.')}</p>`, 'program', 'c-wri');
  h += `<div class="tile"><h2>${day > pr.days ? tr(`Extra day ${day}`, `يوم إضافي ${day}`) : tr(`Today, day ${day} of ${pr.days}`, `اليوم، يوم ${day} من ${pr.days}`)}</h2><div class="stack" style="margin-top:10px">${tasks.map(t => taskRow(day, t)).join('')}</div>
    ${day > pr.days ? `<p class="small">${tr('Your program time is over. Retake the placement test or raise your target in My program.', 'انتهت مدة برنامجك. أعد اختبار المستوى أو ارفع هدفك من صفحة برنامجي.')}</p>` : ''}</div>`;
  h += `<div class="actions">${go(tr('Quick practice', 'تدريب سريع'), 'quiz/quick', 'primary')}${go(tr('Mock tests', 'المحاكاة'), 'mocks')}${go(tr('Progress', 'التقدّم'), 'progress')}</div>`;
  return h;
}

/* ---------------------------------------------------------------- program */
function vProgram() {
  const [pr] = getPlan(), day = programDay();
  let h = header(tr('My program', 'برنامجي'));
  if (!S.placed) h += tile(`<h2>${tr('Take the placement test first', 'ابدأ باختبار تحديد المستوى أولًا')}</h2><p>${tr('Your program is built from your level in each skill and your target band. Until then you see a 4-week starter plan.', 'يُبنى برنامجك من مستواك في كل مهارة والدرجة المستهدفة. حتى ذلك الحين تظهر خطة بداية لمدة 4 أسابيع.')}</p>`, 'placement', 'c-fnd');
  h += `<div class="tile c-fnd"><h3>${tr('Estimated level', 'المستوى التقريبي')}</h3><div class="num" style="font-size:44px;display:block">${b1(pr.current)} → ${b1(pr.target)}</div><p>${!S.placed ? tr('Starter plan for 4 weeks. The real length is calculated after the placement test.', 'خطة بداية لمدة 4 أسابيع. المدة الحقيقية تُحسب بعد اختبار المستوى.')
    : pr.gap <= 0 ? tr('You are at or above your target estimate. The program keeps you exam-ready for 4 weeks. You can raise the target in Settings.', 'مستواك التقريبي عند هدفك أو أعلى. البرنامج يحافظ على جاهزيتك 4 أسابيع، ويمكنك رفع الهدف من الإعدادات.')
    : tr(`Gap: ${pr.gap} band. Program: about ${pr.weeks} weeks (${pr.hours} study hours at ${S.minutes} min a day, 6 days a week).`, `الفرق: ${pr.gap} درجة. البرنامج: حوالي ${pr.weeks} أسبوع (${pr.hours} ساعة مذاكرة بمعدل ${S.minutes} دقيقة يوميًا، 6 أيام في الأسبوع).`)}</p>
    ${S.placed && pr.gap > 0 && !pr.exam ? `<b>${tr('Expected finish: ', 'الانتهاء المتوقع: ')}${dateIn(pr.days - day)}</b>${pr.weeks >= 26 ? `<p class="small">${tr('That is long. More minutes a day shortens it a lot.', 'المدة طويلة. زيادة الدقائق اليومية تختصرها كثيرًا.')}</p>` : ''}` : ''}</div>`;
  if (pr.exam) {
    const e = pr.exam;
    h += `<div class="tile ${e.fits ? 'c-fnd' : 'c-spe'}" style="margin-top:12px"><h3>${tr(`Exam in ${e.daysLeft} days`, `الامتحان بعد ${e.daysLeft} يوم`)}</h3><p>${e.fits
      ? tr(`Your target fits: about ${e.availHours} study hours are available at ${S.minutes} min a day.`, `هدفك مناسب للوقت: حوالي ${e.availHours} ساعة مذاكرة متاحة بمعدل ${S.minutes} دقيقة يوميًا.`)
      : tr(`Not enough time for ${b1(pr.target)} at ${S.minutes} min a day. Options: study about ${e.needMinutes} min a day, or aim for about ${b1(e.reachable)} by the exam date.`, `الوقت غير كافٍ للوصول إلى ${b1(pr.target)} بمعدل ${S.minutes} دقيقة يوميًا. الخيارات: ذاكر حوالي ${e.needMinutes} دقيقة يوميًا، أو استهدف حوالي ${b1(e.reachable)} بحلول موعد الامتحان.`)}</p>${e.fits ? '' : `<div class="actions">${e.needMinutes <= 60 ? btn(tr(`Study ${Math.min(60, Math.ceil(e.needMinutes / 15) * 15)} min a day`, `ذاكر ${Math.min(60, Math.ceil(e.needMinutes / 15) * 15)} دقيقة يوميًا`), () => { S.minutes = Math.min(60, Math.ceil(e.needMinutes / 15) * 15); S.replanFuture(); rerender(); }, 'primary') : ''}${e.reachable < pr.target ? btn(tr(`Set target ${b1(e.reachable)}`, `اجعل الهدف ${b1(e.reachable)}`), () => { S.goal = Math.max(5, e.reachable); S.replanFuture(); rerender(); }) : ''}</div>`}</div>`;
  }
  const wt = weakType();
  if (wt) h += `<div class="tile c-spe" style="margin-top:12px"><h3>${tr('Weak spot', 'نقطة ضعف')}: <span class="en">${QTYPE_NAMES[wt.type]}</span></h3><p class="small">${tr(`${Math.round(wt.acc * 100)}% correct in ${wt.n} answers. Your plan now picks passages with this question type more often.`, `${Math.round(wt.acc * 100)}% صحيحة من ${wt.n} إجابة. خطتك الآن تختار قطعًا فيها هذا النوع أكثر.`)}</p></div>`;
  h += `<div class="tile"><h2>${tr('Your skills', 'مهاراتك')}</h2><p class="small mute">${tr('Reading and Listening update after every practice. Writing and Speaking update when you use "estimate" in practice. You can also adjust them if a teacher gave you a level.', 'القراءة والاستماع تتحدّث بعد كل تدريب. الكتابة والمحادثة تتحدّث عند استخدام «قدّر» في التدريب، ويمكنك تعديلها إذا أعطاك معلم مستوى.')}</p>
    ${SKILLS.concat('foundation').map(sk => { const lv = level(sk); return `<div style="margin-top:12px"><div class="row"><b>${skillName(sk)}</b><span>${btn('−', () => { S.setEst(sk, Math.max(3, lv - 0.5)); S.replanFuture(); rerender(); }, '')} <b class="num" style="font-size:22px;margin:0 8px">${b1(lv)}</b> ${btn('+', () => { S.setEst(sk, Math.min(9, lv + 0.5)); S.replanFuture(); rerender(); }, '')}</span></div>${bar(lv / 9, lv >= S.goal ? 'var(--em)' : lv >= S.goal - 1 ? 'var(--mus)' : 'var(--cor)')}</div>`; }).join('')}</div>`;
  const b = Math.max(1, Math.floor(pr.days * 0.3)), p = Math.floor(pr.days * 0.8), cur = phaseOf(Math.min(day, pr.days), pr.days);
  h += `<div class="tile"><h2>${tr('Stages', 'المراحل')}</h2>${[['BUILD', 1, b], ['PRACTICE', b + 1, p], ['EXAM', p + 1, pr.days]].map(([ph, a, z]) => `<div style="margin-top:10px">${pill(tr(`Days ${a}-${z}`, `الأيام ${a}-${z}`), ph === cur ? LIME : '')} <b>${phaseName(ph)}</b><p class="small mute">${phaseInfo(ph)}</p></div>`).join('')}
    <p>${tr('Every 7th day there is a checkpoint test or a mock. Your levels and the plan for the coming days change with the results.', 'كل 7 أيام يوجد اختبار متابعة أو محاكاة، وتتغير مستوياتك وخطة الأيام القادمة حسب النتيجة.')}</p></div>`;
  h += `<h2 style="margin-top:18px">${tr('How to improve', 'خطة التحسين')}</h2>`;
  const order = SKILLS.slice().sort((a, b) => level(a) - level(b));
  h += `<div class="stack">${order.map((sk, i) => { const lv = level(sk); return `<div class="tile ${i === 0 ? 'c-wri' : ''}"><div class="row"><h3>${skillName(sk)}</h3>${pill(b1(lv) + ' → ' + b1(S.goal), lvColor(lv))}</div>${i === 0 ? `<p class="small"><b>${tr('Main focus: this skill gets the most time.', 'التركيز الأساسي: هذه المهارة تأخذ أكبر وقت.')}</b></p>` : ''}<ul style="margin:6px 0;padding-inline-start:20px">${advice(sk, lv).map(a => `<li>${esc(a)}</li>`).join('')}</ul></div>`; }).join('')}</div>`;
  h += `<div class="actions">${btn(tr('Retake placement', 'أعد اختبار المستوى'), () => { S.resetPlacement(); location.hash = 'placement'; }, '')}${V.confirm ? btn(tr('Yes, restart today', 'نعم، ابدأ من اليوم'), () => { S.restartProgram(); V.confirm = false; rerender(); }, 'hot') : btn(tr('Restart program', 'ابدأ البرنامج من جديد'), () => { V.confirm = true; rerender(); })}</div>`;
  return h + `<p class="small mute">${tr('Estimates are rough. Only the official test gives a real band.', 'التقديرات تقريبية. الدرجة الحقيقية من الاختبار الرسمي فقط.')}</p>`;
}

/* ---------------------------------------------------------------- plan */
function vPlan() {
  const [pr, plan] = getPlan(), day = programDay();
  if (V.open == null) { V.open = Math.min(day, pr.days); V.week = Math.floor((V.open - 1) / 7); }
  let h = header(tr('Your plan', 'خطتك'), false);
  h += `<p>${tr(`Focus: ${skillName(weakest())}. ${pr.days} days, the mix follows your weakest skills.`, `التركيز: ${skillName(weakest())}. ${pr.days} يوم، وتوزيع المهام يتبع أضعف مهاراتك.`)}</p>`;
  h += `<div class="row">${btn(I18n.ar ? '→' : '←', () => { V.week--; rerender(); }, '', V.week <= 0)}<div style="text-align:center;flex:1"><b>${tr(`Week ${V.week + 1} of ${pr.weeks}`, `الأسبوع ${V.week + 1} من ${pr.weeks}`)}</b><br><small>${phaseName(phaseOf(V.week * 7 + 1, pr.days))}</small></div>${btn(I18n.ar ? '←' : '→', () => { V.week++; rerender(); }, '', V.week >= pr.weeks - 1)}</div>`;
  h += `<div class="daygrid" style="margin:12px 0">`;
  for (let d = V.week * 7 + 1; d <= V.week * 7 + 7; d++) {
    if (d > pr.days) { h += '<span></span>'; continue; }
    const ts = d <= day ? tasksFor(d, plan) : plan[d - 1], all = ts.length && ts.every(t => S.has('pdone', doneKey(d, t)));
    h += `<a href="javascript:void 0" class="${all ? 'dn' : d === V.open ? 'cur' : ''}" style="${d === day ? 'border-width:4px' : ''}" data-a="${act(() => { V.open = d; rerender(); })}">${d}</a>`;
  }
  h += '</div>';
  if (V.open >= 1 && V.open <= pr.days) {
    const ts = V.open <= day ? tasksFor(V.open, plan) : plan[V.open - 1];
    h += `<div class="tile"><h2>${tr('Day ' + V.open, 'اليوم ' + V.open)}</h2><div class="stack" style="margin-top:10px">${ts.map(t => taskRow(V.open, t)).join('')}</div>${V.open > day ? `<p class="small mute">${tr('Preview. Future days can change after checkpoints.', 'معاينة. الأيام القادمة قد تتغير بعد اختبارات المتابعة.')}</p>` : ''}</div>`;
  }
  return h + `<div class="actions">${go(tr('My program and levels', 'برنامجي ومستوياتي'), 'program', 'primary')}</div>`;
}

/* ---------------------------------------------------------------- lessons */
function vLessons() {
  V.f = V.f || 'all';
  let h = header(tr('Lessons', 'الدروس'));
  h += `<div class="chips">${['all', 'foundation', 'reading', 'listening', 'writing', 'speaking'].map(s => chip(s === 'all' ? tr('All', 'الكل') : skillName(s), V.f === s, () => { V.f = s; rerender(); })).join('')}</div><div class="stack">`;
  for (const l of C.lessons.filter(x => V.f === 'all' || x.skill === V.f)) h += tile(`<div class="row"><h3>${esc(lessonTitle(l))}</h3>${S.has('ldone', l.id) ? pill(tr('Done', 'تم'), LIME) : ''}</div><small>${esc(lessonSub(l))}</small><br><small class="mute">${skillName(l.skill)} · ${esc(I18n.ar ? l.time.replace(' minutes', ' دقائق') : l.time)}</small>`, 'lesson/' + l.id);
  return h + '</div>';
}
function vLesson(id) {
  const l = C.lessons.find(x => x.id === id); if (!l) return vLessons();
  const done = S.has('ldone', l.id);
  let h = header(esc(lessonTitle(l)));
  h += `<p>${pill(skillName(l.skill))} ${pill(esc(l.time), SKY)}</p><h3>${esc(lessonSub(l))}</h3><p>${esc(lessonBody(l))}</p>`;
  if (l.example) h += `<div class="tile c-fnd"><h3>${tr('Example', 'مثال')}</h3><p class="en">${esc(l.example)}</p></div>`;
  if (l.action) h += `<div class="tile c-wri" style="margin-top:12px"><h3>${tr('Try this', 'جرّب')}</h3><p>${esc(lessonAction(l))}</p></div>`;
  if (I18n.ar && l.body_ar) h += `<div class="tile" style="margin-top:12px"><h3>English</h3><p class="en" style="font-size:15px">${esc(l.body)}</p></div>`;
  return h + `<div class="actions">${btn(done ? tr('Marked done ✓', 'تم ✓') : tr('Mark as done', 'علّم كمنتهي'), () => { S.toggle('ldone', l.id); if (S.has('ldone', l.id)) S.addDay(); rerender(); }, done ? '' : 'primary')}</div>`;
}

/* ---------------------------------------------------------------- practice and mocks */
const QTYPES = [['tfng', 'True / False / Not Given'], ['yn', 'Yes / No / Not Given'], ['sel', 'Matching'], ['mcq', 'Multiple choice'], ['gap', 'Completion']];
function vPractice() {
  V.qt = V.qt || '';
  let h = header(tr('Practice', 'تدريب'), false);
  h += `<div class="grid g2">${tile(`<h3>${tr('Quick practice', 'تدريب سريع')}</h3><small>${tr('12 mixed questions, 15 minutes.', '12 سؤال متنوع في 15 دقيقة.')}</small>`, 'quiz/quick', 'c-fnd')}${tile(`<h3>${tr('Checkpoint test', 'اختبار متابعة')}</h3><small>${tr('16 questions. Updates your levels and plan.', '16 سؤال، يحدّث مستوياتك وخطتك.')}</small>`, 'quiz/check', 'c-wri')}</div>`;
  h += `<div class="grid g2" style="margin-top:12px">${tile(`<h3>${tr('Dictation', 'الإملاء')}</h3><small>${tr('Names, numbers, dates, times and prices by audio.', 'أسماء وأرقام وتواريخ وأوقات وأسعار بالصوت.')}</small>`, 'dict', 'c-lis')}${tile(`<h3>${tr('My mistakes', 'أخطائي')}</h3><small>${tr(`${mistakesDue().length} due · ${mistakes().length} saved`, `${mistakesDue().length} للمراجعة · ${mistakes().length} محفوظة`)}</small>`, 'review', 'c-spe')}</div>`;
  h += `<div class="actions">${['foundation', 'reading', 'listening'].map(sk => go(sk === 'foundation' ? tr('Grammar', 'القواعد') : skillName(sk), 'quiz/drill/' + sk)).join('')}</div>`;
  h += `<h2>${tr('By question type', 'حسب نوع السؤال')}</h2><div class="chips">${QTYPES.map(([k, n]) => chip(n, V.qt === k, () => { V.qt = V.qt === k ? '' : k; rerender(); })).join('')}</div>`;
  const has = s => !V.qt || s.groups.some(g => g.qs.some(q => q.t === V.qt));
  const secTile = (s, k) => tile(`<div class="row"><h3>${esc(s.title)}</h3>${S.mapInt('secn', s.id) > 0 ? pill(tr('Done', 'تم'), LIME) : ''}</div><small>${k === 'R' ? esc(s.sub) + ' · ' + tr('20 minutes', '20 دقيقة') : tr('Untimed, can replay', 'بدون وقت، يمكن الإعادة')}${s.mock ? ' · Mock ' + s.mock : ''}</small>`, `paper/${k}/${s.id}`);
  h += `<h2>${tr('Reading passages', 'قطع القراءة')}</h2><div class="stack">${C.reading.filter(has).map(s => secTile(s, 'R')).join('')}</div>`;
  h += `<h2 style="margin-top:16px">${tr('Listening sections', 'أجزاء الاستماع')}</h2><div class="stack">${C.listening.filter(has).map(s => secTile(s, 'L')).join('')}</div>`;
  if (!V.qt) {
    h += `<h2 style="margin-top:16px">${tr('Writing', 'الكتابة')}</h2><div class="stack">${C.writing.filter(w => w.type === S.type || w.type === 'Both').map(w => tile(`<h3>${esc(w.title)}</h3><small>${tr(`${w.minutes} min, at least ${w.min} words`, `${w.minutes} دقيقة، ${w.min} كلمة على الأقل`)}</small>`, 'write/' + w.id)).join('')}</div>`;
    h += `<h2 style="margin-top:16px">${tr('Speaking', 'المحادثة')}</h2><div class="stack">${C.speaking.map(s => tile(`<h3>${esc(s.topic)}</h3><small>${tr('Parts 1, 2 and 3', 'الأجزاء 1 و2 و3')}</small>`, 'speak/' + s.id)).join('')}</div>`;
  }
  return h;
}
function vMocks() {
  let h = header(tr('Mock tests', 'اختبارات المحاكاة'));
  h += `<p>${tr('Timed, one go. Listening audio plays once. Scores are indicative only.', 'بالوقت ومرة واحدة. تسجيل الاستماع يُشغَّل مرة واحدة. الدرجات تقريبية فقط.')}</p><div class="stack">`;
  for (let m = 1; m <= mocks(); m++) h += `<div class="tile"><h2>${tr('Mock ' + m, 'محاكاة ' + m)}</h2><div class="actions">${go(tr('Reading, 60 min', 'القراءة، 60 دقيقة'), 'paper/R/mock' + m, 'primary')}${go(tr('Listening, 35 min', 'الاستماع، 35 دقيقة'), 'paper/L/mock' + m)}</div></div>`;
  h += `<div class="tile"><h2>${tr('Writing and Speaking', 'الكتابة والمحادثة')}</h2><div class="actions">${go(tr('Writing mock, 60 min', 'محاكاة الكتابة، 60 دقيقة'), 'write/mock', 'primary')}${go(tr('Speaking mock', 'محاكاة المحادثة'), 'speak/mock')}</div></div></div>`;
  if (C.external.length) {
    h += `<h2 style="margin-top:18px">${tr('Full tests online', 'اختبارات كاملة أونلاين')}</h2><p class="small mute">${tr('Real-format full tests on ieltsonlinetests.com (free sign-in needed). Take them there, then enter your Reading and Listening scores here to update your levels.', 'اختبارات كاملة بشكل الامتحان على موقع ieltsonlinetests.com (يحتاج تسجيل دخول مجاني). حلّها هناك، ثم أدخل درجتك في القراءة والاستماع هنا لتحديث مستواك.')}</p><div class="stack">`;
    h += C.external.map(x => { const sc = S.mapStr('ext', x.id); return tile(`<div class="row"><h3 class="en">${esc(x.title)}</h3>${S.has('extdone', x.id) ? pill(tr('Done', 'تم'), LIME) : ''}</div><small>${sc ? tr('Your scores (R/L): ', 'درجاتك (ق/س): ') + esc(sc) : tr('Listening 40 · Reading 40 · Writing 2 tasks · Speaking', 'استماع 40 · قراءة 40 · كتابة مهمتان · محادثة')}</small>`, 'ext/' + x.id); }).join('');
    h += '</div>';
  }
  return h;
}

function vExt(id) {
  const x = C.external.find(e => e.id === id); if (!x) return vMocks();
  const a = (href, label, cls = '') => `<a class="btn ${cls}" href="${esc(href)}" target="_blank" rel="noopener">${label} ↗</a>`;
  const [pr, pl] = (S.mapStr('ext', id) || '/').split('/');
  let h = header(tr('Full test online', 'اختبار كامل أونلاين'));
  h += `<h2 class="en">${esc(x.title)}</h2><p class="small mute">${tr('Source: ', 'المصدر: ')}<a href="${esc(x.collection)}" target="_blank" rel="noopener">${esc(x.source)}</a> · ${tr('test ', 'اختبار ')}${esc(x.testId)}</p>`;
  h += `<div class="notice">${tr('The questions, audio and answers are on ieltsonlinetests.com, not in this app. You need a free account there. Sections run in order, and each starts with a short guidance video.', 'الأسئلة والصوت والإجابات على موقع ieltsonlinetests.com وليست في هذا التطبيق. تحتاج حسابًا مجانيًا هناك. الأجزاء بالترتيب، وكل جزء يبدأ بفيديو إرشادي قصير.')}</div>`;
  h += `<div class="actions">${a(x.full, tr('Start the full test', 'ابدأ الاختبار الكامل'), 'primary')}</div>`;
  h += `<div class="stack">
    <div class="tile"><b>${tr('Listening', 'الاستماع')}</b> · ${tr('40 questions, 4 parts, about 32 min. Audio plays on the site only.', '40 سؤالًا، 4 أجزاء، حوالي 32 دقيقة. الصوت على الموقع فقط.')}<div class="actions">${a(x.listening, tr('Open', 'افتح'))}</div></div>
    <div class="tile"><b>${tr('Reading', 'القراءة')}</b> · ${tr('40 questions, 3 passages (13, 13, 14), 60 min.', '40 سؤالًا، 3 قطع (13، 13، 14)، 60 دقيقة.')}<div class="actions">${a(x.reading, tr('Open', 'افتح'))}</div></div>
    <div class="tile"><b>${tr('Writing', 'الكتابة')}</b> · ${tr('2 tasks, 60 min. Check your essays with "Estimate my level" in the app\'s Writing practice.', 'مهمتان، 60 دقيقة. قيّم مقالك بزر «قدّر مستواي» في تدريب الكتابة بالتطبيق.')}<div class="actions">${a(x.writing, tr('Open', 'افتح'))}</div></div>
    <div class="tile"><b>${tr('Speaking', 'المحادثة')}</b> · ${tr('With a live examiner by video call; questions are not published as text.', 'مع ممتحن مباشر بمكالمة فيديو؛ الأسئلة لا تُنشر كنص.')}${x.speakingParts ? ` ${tr('Parts', 'الأجزاء')}: ${x.speakingParts.join(' + ')}${x.speakingMinutes ? ' · ' + x.speakingMinutes + tr(' min', ' دقيقة') : ''}` : ''}<div class="actions">${a(x.speaking, tr('Open', 'افتح'))}</div></div>
  </div>`;
  const inp = (k, v) => `<input class="inp" id="${k}" type="number" inputmode="numeric" min="0" max="40" value="${esc(v || '')}" style="max-width:110px;display:inline-block">`;
  h += `<div class="tile c-fnd" style="margin-top:12px"><h3>${tr('Enter your scores', 'أدخل درجاتك')}</h3><p class="small">${tr('After you submit on the site, enter how many you got right out of 40. Leave a box empty if you did not take that section.', 'بعد التسليم في الموقع، أدخل عدد الإجابات الصحيحة من 40. اترك الخانة فارغة إذا لم تحل هذا الجزء.')}</p>
    <div class="row" style="justify-content:flex-start;gap:16px"><label>${tr('Reading', 'القراءة')} ${inp('xr', pr)} / 40</label><label>${tr('Listening', 'الاستماع')} ${inp('xl', pl)} / 40</label></div>
    <div class="actions">${btn(tr('Save and update my levels', 'احفظ وحدّث مستواي'), () => {
      const v = k => { const t = $('#' + k).value.trim(); if (t === '') return null; const n = Math.round(+t); return n >= 0 && n <= 40 ? n : NaN; };
      const r = v('xr'), l = v('xl');
      if (Number.isNaN(r) || Number.isNaN(l) || (r == null && l == null)) { toast(tr('Enter a number from 0 to 40.', 'أدخل رقمًا من 0 إلى 40.')); return; }
      recordExternal(id, r, l);
      V.saved = tr(`Saved. ${r != null ? 'Reading band about ' + b1(accBand(r, 40, 'R')) + '. ' : ''}${l != null ? 'Listening band about ' + b1(accBand(l, 40, 'L')) + '. ' : ''}Your plan was updated.`, `تم الحفظ. ${r != null ? 'القراءة تقريبًا ' + b1(accBand(r, 40, 'R')) + '. ' : ''}${l != null ? 'الاستماع تقريبًا ' + b1(accBand(l, 40, 'L')) + '. ' : ''}تم تحديث خطتك.`);
      rerender();
    }, 'primary')}</div>${V.saved ? `<p><b>${esc(V.saved)}</b></p>` : ''}</div>`;
  return h + `<p class="small mute">${tr('Independent links. Khotwa is not connected to ieltsonlinetests.com.', 'روابط مستقلة. خطوة غير مرتبطة بموقع ieltsonlinetests.com.')}</p>`;
}

/* ---------------------------------------------------------------- quiz (drill, quick, checkpoint) */
function vQuiz(kind, arg) {
  if (!V.qs) { V.qs = quizQuestions(kind, arg); V.ans = {}; V.i = 0; V.ltr = true; }
  const qs = V.qs; if (!qs.length) return header(tr('Practice', 'تدريب')) + `<p>${tr('No questions found.', 'لا توجد أسئلة.')}</p>`;
  const total = kind === 'quick' ? 15 * 60 : kind === 'check' ? 20 * 60 : 12 * 60, t = countdown('t', total);
  const finish = () => {
    if (V.done) return; V.done = true;
    qs.forEach((q, i) => { if (V.ans[i] !== q.answer && q.id != null) addMistake('b' + q.id); });
    const items = qs.map((q, i) => ({ q: q.q, yours: V.ans[i] == null ? 'No answer' : q.options[V.ans[i]], correct: q.options[q.answer], ok: V.ans[i] === q.answer, why: q.why, tag: q.skill }));
    for (const sk of ['foundation', 'reading', 'listening']) {
      const sub = qs.map((q, i) => i).filter(i => qs[i].skill === sk); if (!sub.length) continue;
      const c = sub.filter(i => V.ans[i] === qs[i].answer).length;
      S.addStat('sk', sk, c, sub.length); updateLevel(sk, c, sub.length, sk === 'listening' ? 'L' : 'R');
    }
    if (kind === 'check') S.replanFuture();
    const title = kind === 'quick' ? tr('Quick practice', 'تدريب سريع') : kind === 'check' ? tr('Checkpoint test', 'اختبار متابعة') : tr('Drill: ', 'تدريب: ') + skillName(arg);
    const score = items.filter(x => x.ok).length; S.addHistory(title, score, qs.length, null);
    LAST = { title, score, total: qs.length, band: null, items, note: kind === 'check' ? tr('Your levels and the coming days of your plan were updated.', 'تم تحديث مستوياتك وخطة الأيام القادمة.') : tr('Short sets give a rough picture only. No band is shown below 30 questions.', 'المجموعات القصيرة تعطي صورة تقريبية فقط. لا تظهر درجة لأقل من 30 سؤالًا.'), next: kind === 'check' ? 'program' : null };
    location.replace('#results');
  };
  TICK = () => { tickCountdown(t, finish); const e = $('#qt'); if (e) { e.textContent = fmt(t.left); e.classList.toggle('low', t.left < 60); } };
  const q = qs[V.i];
  let h = header(kind === 'quick' ? tr('Quick practice', 'تدريب سريع') : kind === 'check' ? tr('Checkpoint', 'متابعة') : tr('Drill', 'تدريب'), true, timer('qt', t.left));
  h += bar((V.i + 1) / qs.length, 'var(--sky)') + `<p class="small mute">${tr(`Question ${V.i + 1} of ${qs.length}`, `السؤال ${V.i + 1} من ${qs.length}`)} · ${skillName(q.skill)}</p>`;
  if (q.context) h += `<div class="passage en"><p>${esc(q.context)}</p></div>`;
  h += `<h3 class="en">${esc(q.q)}</h3><div class="options">${q.options.map((o, k) => `<button class="option ${V.ans[V.i] === k ? 'selected' : ''}" data-a="${act(() => { V.ans[V.i] = k; rerender(); })}"><span class="letter">${'ABCD'[k]}</span><span>${esc(o)}</span></button>`).join('')}</div>`;
  return h + `<div class="actions">${V.i > 0 ? btn(tr('Back', 'السابق'), () => { V.i--; rerender(); }) : ''}${V.i < qs.length - 1 ? btn(tr('Next', 'التالي'), () => { V.i++; rerender(); }, 'primary') : btn(tr('Finish', 'إنهاء'), finish, 'hot')}</div>`;
}

/* ---------------------------------------------------------------- reading / listening paper */
function vPaper(kind, id) {
  const mockN = id.startsWith('mock') ? +id.slice(4) : 0;
  if (!V.secs) { V.secs = mockN ? (kind === 'R' ? C.reading : C.listening).filter(s => s.mock === mockN) : [section(id)].filter(Boolean); V.ans = {}; V.tab = 0; V.ltr = true; }
  const secs = V.secs; if (!secs.length) return vPractice();
  const minutes = kind === 'R' ? (mockN ? 60 : 20) : (mockN ? 35 : 0), t = countdown('t', minutes * 60, minutes > 0);
  const finish = () => {
    if (V.done) return; V.done = true; TTS.stop();
    const items = [];
    for (const s of secs) for (const g of s.groups) for (const q of g.qs) {
      const a = V.ans[s.id + '|' + q.n], ok = isCorrect(q, a);
      items.push({ q: q.n + '. ' + q.q, yours: a == null || a === '' ? 'No answer' : q.t === 'gap' ? a : (optionsOf(g, q)[+a] ?? a), correct: correctText(g, q), ok, why: q.w, tag: q.t });
      S.addStat('ty', q.t, ok ? 1 : 0, 1); S.addStat('sk', kind === 'R' ? 'reading' : 'listening', ok ? 1 : 0, 1);
      if (!ok && a != null && a !== '') addMistake('s' + s.id + '|' + q.n);
    }
    const score = items.filter(x => x.ok).length, b = band(score, items.length, kind);
    for (const s of secs) S.bump('secn', s.id);
    if (Object.keys(V.ans).length) { updateLevel(kind === 'R' ? 'reading' : 'listening', score, items.length, kind); if (mockN) S.replanFuture(); }
    const title = (kind === 'R' ? tr('Reading ', 'قراءة ') : tr('Listening ', 'استماع ')) + (mockN ? tr('Mock ' + mockN, 'محاكاة ' + mockN) : secs[0].title);
    S.addHistory(title, score, items.length, b);
    LAST = { title, score, total: items.length, band: b, kind, items, note: kind === 'L' ? 'Transcript:\n' + secs.map(s => s.script.map(x => x[0] + ': ' + x[1]).join('\n')).join('\n\n') : '', next: mockN ? 'program' : null };
    location.replace('#results');
  };
  if (minutes) TICK = () => { tickCountdown(t, finish); const e = $('#pt'); if (e) { e.textContent = fmt(t.left); e.classList.toggle('low', t.left < 60); } };
  const s = secs[V.tab];
  let h = header(kind === 'R' ? tr('Reading', 'القراءة') : tr('Listening', 'الاستماع'), true, minutes ? timer('pt', t.left) : '');
  if (secs.length > 1) h += `<div class="chips">${secs.map((x, k) => chip(kind === 'R' ? tr('Passage ' + (k + 1), 'القطعة ' + (k + 1)) : tr('Section ' + (k + 1), 'الجزء ' + (k + 1)), V.tab === k, () => { V.tab = k; rerender(); scrollTo(0, 0); })).join('')}</div>`;
  h += `<h2 class="en">${esc(s.title)}</h2><p class="small mute en">${esc(s.sub)}</p>`;
  if (kind === 'R') h += passageHTML(s);
  else {
    const played = V['pl' + s.id];
    h += `<div class="tile c-lis"><b>${mockN ? tr('Audio plays once.', 'التسجيل يُشغَّل مرة واحدة.') : tr('Audio can be replayed.', 'يمكن إعادة التسجيل.')}</b><div class="actions">${btn(TTS.speaking ? tr('Playing...', 'يعمل...') : played && mockN ? tr('Played', 'تم التشغيل') : tr('Play audio', 'شغّل الصوت'), () => { V['pl' + s.id] = 1; TTS.play(s.script, rerender, -1, s.id); rerender(); }, 'primary', TTS.speaking || (played && mockN))}${!mockN ? btn(tr('Stop', 'إيقاف'), () => { TTS.stop(); rerender(); }) : ''}</div>${!TTS.ok() ? `<p class="small">${tr('This browser cannot play the audio.', 'هذا المتصفح لا يستطيع تشغيل الصوت.')}</p>` : ''}</div>`;
  }
  h += groupsHTML(s, V.ans);
  h += `<div class="actions">${secs.length > 1 && V.tab < secs.length - 1 ? btn(tr('Next section', 'الجزء التالي'), () => { V.tab++; rerender(); scrollTo(0, 0); }) : ''}${btn(tr('Submit answers', 'سلّم الإجابات'), finish, 'hot')}</div>`;
  return h + `<p class="small mute">${tr(Object.keys(V.ans).length + ' answered', 'تمت الإجابة عن ' + Object.keys(V.ans).length)}</p>`;
}

/* ---------------------------------------------------------------- results */
function vResults() {
  const r = LAST; let h = header(tr('Results', 'النتيجة'), false);
  if (!r) return h + `<p>${tr('No result to show.', 'لا توجد نتيجة.')}</p>`;
  V.ltr = true;
  h += `<div class="tile c-fnd"><h2>${esc(r.title)}</h2><div class="num">${r.score} / ${r.total}</div>${r.band != null ? `<h2>${tr('Indicative band ' + r.band, 'درجة تقريبية ' + r.band)}</h2>` : r.kind ? `<p class="small">${tr('Too few questions for a band estimate.', 'عدد الأسئلة أقل من أن يعطي درجة.')}</p>` : ''}${r.note && r.kind !== 'L' ? `<p class="small">${esc(r.note)}</p>` : ''}</div>`;
  if (r.next) h += `<div class="actions">${btn(tr('See my program', 'شاهد برنامجي'), () => { location.hash = r.next; }, 'primary')}</div>`;
  h += `<div class="stack" style="margin-top:12px">${r.items.map(it => `<div class="tile"><span class="mk ${it.ok ? 'ok' : 'no'}">${it.ok ? tr('Correct', 'صحيح') : tr('Wrong', 'خطأ')}</span><p class="en">${esc(it.q)}</p><b>${tr('You: ', 'إجابتك: ')}${esc(it.yours)}</b>${it.ok ? '' : `<br><b style="color:var(--em)">${tr('Answer: ', 'الصحيح: ')}${esc(it.correct)}</b>`}<p class="small mute en">${esc(it.why)}</p></div>`).join('')}</div>`;
  if (r.kind === 'L' && r.note) h += `<div class="tile" style="margin-top:12px"><h3>${tr('Transcript', 'نص الاستماع')}</h3><p class="en" style="white-space:pre-wrap;font-size:15px">${esc(r.note.replace('Transcript:\n', ''))}</p></div>`;
  return h + `<div class="actions">${go(tr('Back to home', 'العودة للرئيسية'), 'home', 'primary')}</div>`;
}

/* ---------------------------------------------------------------- writing */
function analyse(text) {
  const s = textStats(text); if (!s.words) return tr('Nothing written yet.', 'لم تكتب شيئًا بعد.');
  const freq = {}; for (const w of (text.toLowerCase().match(/[a-z']+/g) || [])) if (w.length > 4) freq[w] = (freq[w] || 0) + 1;
  const rep = Object.entries(freq).filter(([, n]) => n >= 4).map(([w]) => w).slice(0, 5);
  return tr(`Words: ${s.words}. Sentences: ${s.sentences} (average ${Math.round(s.words / Math.max(1, s.sentences))} words). Paragraphs: ${s.paras}.`, `الكلمات: ${s.words}. الجمل: ${s.sentences} (متوسط ${Math.round(s.words / Math.max(1, s.sentences))} كلمة). الفقرات: ${s.paras}.`) + '\n' +
    tr('Linking words used: ', 'أدوات الربط: ') + (s.linkList.length ? s.linkList.join(', ') : tr('none found', 'لا يوجد')) + '.\n' +
    (rep.length ? tr('Repeated often: ', 'تكررت كثيرًا: ') + rep.join(', ') + tr('. Try synonyms.', '. جرّب مرادفات.') + '\n' : '') +
    tr('This is a simple check. It cannot judge ideas, grammar or give a band.', 'هذا فحص بسيط لا يقيّم الأفكار أو القواعد ولا يعطي درجة.');
}
function vWrite(id) {
  const mock = id === 'mock';
  const items = mock ? [C.writing.find(w => w.task === 1 && w.type === S.type), C.writing.find(w => w.task === 2)].filter(Boolean) : [C.writing.find(w => w.id === id)].filter(Boolean);
  if (!items.length) return vPractice();
  V.tab = V.tab || 0; V.ltr = true;
  const w = items[V.tab], key = w.id, text = () => S.mapStr('draft', key);
  const t = countdown('t', (mock ? 60 : w.minutes) * 60, false);
  TICK = () => { tickCountdown(t); const e = $('#wt'); if (e) { e.textContent = fmt(t.left); e.classList.toggle('low', t.left < 60 && t.run); } };
  const count = () => textStats(text()).words;
  const leave = () => { if (count() >= w.min && !S.has('wdone', key)) { S.add('wdone', key); S.bump('wn', key); } };
  V.onLeave = leave;
  let h = header(mock ? tr('Writing mock', 'محاكاة الكتابة') : tr('Writing', 'الكتابة'), true, timer('wt', t.left));
  if (mock) h += `<div class="chips">${items.map((x, k) => chip('Task ' + x.task, V.tab === k, () => { leave(); V.tab = k; V.report = ''; V.scored = null; rerender(); })).join('')}</div>`;
  h += `<h2 class="en">${esc(w.title)}</h2><div class="passage en"><p>${esc(w.prompt)}</p></div>${chartHTML(w.chart)}`;
  if (w.tips && w.tips.length) h += `<div class="notice"><b>${tr('Tips', 'نصائح')}</b><ul class="en" style="margin:4px 0;padding-inline-start:20px">${w.tips.map(x => `<li>${esc(x)}</li>`).join('')}</ul></div>`;
  h += `<div class="actions">${btn(t.run ? tr('Pause timer', 'أوقف المؤقت') : tr('Start timer', 'ابدأ المؤقت'), () => { t.run = !t.run; t.last = Date.now(); rerender(); })}</div>`;
  h += `<textarea class="inp" id="essay" spellcheck="false" placeholder="${tr('Write here...', 'اكتب هنا...')}"></textarea>`;
  h += `<p id="wc" class="small"></p>`;
  h += `<div class="actions">${btn(tr('Check text', 'افحص النص'), () => { V.report = analyse(text()); rerender(); })}${w.model ? btn(V.model ? tr('Hide model answer', 'أخفِ الإجابة النموذجية') : tr('Show model answer', 'اعرض الإجابة النموذجية'), () => { V.model = !V.model; rerender(); }) : ''}</div>`;
  if (V.report) h += `<div class="notice" style="white-space:pre-wrap">${esc(V.report)}</div>`;
  if (V.model && w.model) h += `<div class="tile"><p class="en" style="white-space:pre-wrap">${esc(w.model)}</p><p class="small mute">${tr('A model shows one way to answer, not the only way.', 'النموذج طريقة واحدة للإجابة وليس الطريقة الوحيدة.')}</p></div>`;
  h += `<div class="actions">${btn(tr('AI feedback (needs internet)', 'ملاحظات الذكاء الاصطناعي (يحتاج إنترنت)'), () => { if (count() < 30) { toast(tr('Write at least 30 words first.', 'اكتب 30 كلمة على الأقل أولًا.')); return; } askFeedback('writing', w.prompt, text(), w.task, w.min, b => { S.setEst('writing', clamp(half(level('writing') * 0.65 + b * 0.35), 3, 9)); }); }, 'hot', V.fb && V.fb.loading)}</div>${feedbackHTML(V.fb)}`;
  h += `<div class="tile" style="margin-top:12px"><h3>${tr('Finished? Estimate my level', 'انتهيت؟ قدّر مستواي')}</h3><p class="small mute">${tr('Uses length, sentences, linking and word variety, limited by your grammar level. It cannot judge your ideas.', 'يعتمد على الطول والجمل وأدوات الربط وتنوّع الكلمات، ومحدود بمستوى قواعدك. لا يقيّم أفكارك.')}</p>
    ${V.scored == null ? btn(tr('Estimate and update my level', 'قدّر وحدّث مستواي'), () => {
      if (count() < 40) { toast(tr('Write at least 40 words first.', 'اكتب 40 كلمة على الأقل أولًا.')); return; }
      const e = writingLevel(text(), w.min, level('foundation'), 1.5);
      S.setEst('writing', clamp(half(level('writing') * 0.7 + e * 0.3), 3, 9)); leave();
      S.addHistory(tr('Writing: ', 'كتابة: ') + w.title, count(), w.min, null); V.scored = e; rerender();
    }, 'primary') : `<b>${tr(`This text: about ${V.scored}. Your writing level is now ${level('writing')}.`, `هذا النص: حوالي ${V.scored}. مستواك في الكتابة الآن ${level('writing')}.`)}</b>`}</div>`;
  V.after = () => {
    const ta = $('#essay'); if (!ta) return; ta.value = text();
    const upd = () => { const n = textStats(ta.value).words; const e = $('#wc'); e.textContent = tr(`${n} words`, `${n} كلمة`) + (n < w.min ? tr(`  (aim for ${w.min}+)`, `  (الهدف ${w.min}+)`) : '  ✓') + tr('  ·  saved automatically', '  ·  يُحفظ تلقائيًا'); e.style.color = n >= w.min ? 'var(--em)' : 'var(--cor)'; };
    ta.oninput = () => { upd(); S.putMap('draft', key, ta.value); };
    ta.onblur = () => S.putMap('draft', key, ta.value);
    upd();
  };
  return h;
}

/* ---------------------------------------------------------------- speaking */
function measureHTML() {
  const cap = V.cap || (V.cap = SpeechCap(() => { const e = $('#cap'); if (e) e.textContent = (V.cap.text + ' ' + V.cap.partial).trim() || tr('Listening...', 'يستمع...'); }));
  const score = () => {
    cap.stop(); const words = textStats(cap.text).words;
    if (words < 20) { V.mres = tr('Too little speech was heard to measure. Try again closer to the microphone.', 'الكلام المسموع قليل جدًا للقياس. جرّب مرة أخرى قريبًا من الميكروفون.'); rerender(); return; }
    const e = speakingLevel(cap.text, cap.seconds, level('foundation'), 1, 1);
    S.setEst('speaking', clamp(half(level('speaking') * 0.7 + e * 0.3), 3, 9));
    V.mres = tr(`${Math.round(words * 60 / Math.max(1, cap.seconds))} words a minute, about ${e}. Your speaking level is now ${level('speaking')}.`, `${Math.round(words * 60 / Math.max(1, cap.seconds))} كلمة في الدقيقة، حوالي ${e}. مستواك في المحادثة الآن ${level('speaking')}.`);
    rerender();
  };
  const prev = TICK;
  TICK = () => { prev && prev(); if (cap.running) { const e = $('#mt'); if (e) e.textContent = fmt(120 - cap.seconds); if (cap.seconds >= 120) score(); } };
  return `<div class="tile c-lis" style="margin-top:12px"><h3>${tr('Speak and measure', 'تكلّم وقِس مستواك')}</h3><p class="small">${tr('The browser writes what you say and measures speed and vocabulary. It cannot judge pronunciation like an examiner.', 'المتصفح يكتب ما تقوله ويقيس السرعة والمفردات، ولا يقيّم النطق مثل الممتحن.')}</p>
    ${!cap.running ? btn(tr('Start', 'ابدأ'), () => { V.mres = ''; cap.start(); rerender(); }, 'primary', !!REC.mr) : `<div class="row">${timer('mt', 120 - cap.seconds)}${btn(tr('Finish', 'إنهاء'), score, 'hot')}</div><p class="en" id="cap" style="font-size:15px">${esc((cap.text + ' ' + cap.partial).trim() || tr('Listening...', 'يستمع...'))}</p>`}
    ${!cap.available ? `<p class="small"><b>${tr('Speech recognition is not available in this browser. On iPhone use Safari with Siri turned on; on Android use Chrome.', 'التعرّف على الكلام غير متاح في هذا المتصفح. على الآيفون استخدم سفاري مع تفعيل Siri، وعلى أندرويد استخدم كروم.')}</b></p>` : ''}
    ${cap.error === 'permission' ? `<p class="small"><b>${tr('Microphone or speech permission was denied.', 'تم رفض إذن الميكروفون أو التعرّف على الكلام.')}</b></p>` : ''}${V.mres ? `<p><b>${esc(V.mres)}</b></p>` : ''}${!cap.running && textStats(cap.text).words >= 30 ? `<div class="actions">${btn(tr('AI feedback on what you said', 'ملاحظات الذكاء الاصطناعي على كلامك'), () => askFeedback('speaking', V.cue || '', cap.text, 2, 0, b => { S.setEst('speaking', clamp(half(level('speaking') * 0.7 + b * 0.3), 3, 9)); }), 'hot', V.fb && V.fb.loading)}</div>` : ''}${feedbackHTML(V.fb)}</div>`;
}
function vSpeak(id) {
  const mock = id === 'mock', sp = mock ? C.speaking[(today()) % C.speaking.length] : C.speaking.find(x => x.id === id);
  if (!sp) return vPractice();
  V.part = V.part || 1; V.ltr = true;
  V.onLeave = () => { recStop(); V.cap && V.cap.stop(); };
  const t = countdown('prep', 60, false);
  if (V.part === 2) TICK = () => { tickCountdown(t, () => { t.run = false; rerender(); }); const e = $('#prep'); if (e) e.textContent = fmt(t.left); };
  let h = header(mock ? tr('Speaking mock', 'محاكاة المحادثة') : tr('Speaking', 'المحادثة'));
  h += `<h2 class="en">${esc(sp.topic)}</h2><div class="chips">${[1, 2, 3].map(p => chip(tr('Part ' + p, 'الجزء ' + p), V.part === p, () => { V.onLeave(); V.part = p; rerender(); })).join('')}</div>`;
  if (V.part === 1) h += `<div class="tile"><ul class="en">${sp.p1.map(q => `<li>${esc(q)}</li>`).join('')}</ul><p class="small mute">${tr('Answer each question in 2 to 4 sentences.', 'أجب عن كل سؤال في 2 إلى 4 جمل.')}</p></div>`;
  if (V.part === 2) {
    V.cue = sp.cue.text + ' You should say: ' + sp.cue.bullets.join('; ');
    h += `<div class="tile c-fnd"><p class="en" style="font-weight:700">${esc(sp.cue.text)}</p><p class="en">You should say:</p><ul class="en">${sp.cue.bullets.map(b => `<li>${esc(b)}</li>`).join('')}</ul></div>`;
    h += `<div class="actions">${btn(t.run ? tr('Preparing...', 'تحضير...') : tr('1 minute to prepare', 'دقيقة للتحضير'), () => { t.run = true; t.last = Date.now(); rerender(); }, '', t.run || t.left <= 0)}${t.run ? timer('prep', t.left) : ''}</div><p class="small mute">${tr('Then speak for 1 to 2 minutes.', 'ثم تكلّم من دقيقة إلى دقيقتين.')}</p>`;
    h += measureHTML();
  }
  if (V.part === 3) h += `<div class="tile"><ul class="en">${sp.p3.map(q => `<li>${esc(q)}</li>`).join('')}</ul><p class="small mute">${tr('Give reasons and examples.', 'قدّم أسبابًا وأمثلة.')}</p></div>`;
  h += `<div class="tile" style="margin-top:12px"><h3>${tr('Record yourself', 'سجّل صوتك')}</h3><div class="actions">${REC.mr ? btn(tr('Stop', 'إيقاف'), () => recStop(), 'hot') : btn(tr('Record', 'سجّل'), () => recStart(rerender), 'hot', V.cap && V.cap.running)}</div>${REC.url && !REC.mr ? `<audio controls src="${REC.url}" style="width:100%"></audio>` : ''}<p class="small mute">${tr('The recording is not uploaded or scored. Listen for pauses, fillers and clear ideas.', 'التسجيل لا يُرفع ولا يُقيَّم. استمع للتوقفات والكلمات الحشو ووضوح الأفكار.')}</p></div>`;
  return h + `<div class="actions">${V.part < 3 ? btn(tr('Next part', 'الجزء التالي'), () => { V.onLeave(); V.part++; rerender(); }, 'primary') : btn(tr('Finish', 'إنهاء'), () => { V.onLeave(); S.bump('spn', sp.id); S.addHistory(mock ? tr('Speaking mock', 'محاكاة المحادثة') : tr('Speaking: ', 'محادثة: ') + sp.topic, 0, 0, null); history.back(); }, 'primary')}</div>`;
}

/* ---------------------------------------------------------------- words */
function vWords() {
  const words = C.vocab, lv = w => S.mapInt('vl', String(w.id));
  if (!V.queue) { V.queue = words.slice().sort((a, b) => lv(a) - lv(b) || a.id - b.id).slice(0, 10); V.i = 0; V.flip = false; }
  let h = header(tr('Words', 'الكلمات'), false);
  const counts = [0, 1, 2, 3, 4].map(l => words.filter(w => lv(w) === l).length);
  h += `<p class="small mute">${tr('Levels 0 to 4: ', 'المستويات من 0 إلى 4: ')}${counts.join('  ')}</p>` + bar(words.filter(w => lv(w) >= 3).length / words.length);
  if (V.i >= V.queue.length) return h + `<div class="tile c-fnd" style="margin-top:12px"><h2>${tr('Session done', 'انتهت الجلسة')}</h2><p>${tr('Come back tomorrow. Words you miss come back sooner.', 'ارجع غدًا. الكلمات التي تخطئ فيها ترجع أسرع.')}</p></div><div class="actions">${btn(tr('Another round', 'جولة أخرى'), () => { V.queue = null; rerender(); }, 'primary')}</div>`;
  const w = V.queue[V.i];
  h += `<p class="small">${tr(`Card ${V.i + 1} of ${V.queue.length}`, `بطاقة ${V.i + 1} من ${V.queue.length}`)}</p>`;
  h += `<button class="tile ${V.flip ? 'c-wri' : ''}" style="min-height:220px;text-align:center" data-a="${act(() => { V.flip = !V.flip; rerender(); })}"><div class="en" style="text-align:center"><h1>${esc(w.w)}</h1>${V.flip ? `<h3>${esc(w.def)}</h3>${w.ar ? `<p dir="rtl" style="font-weight:700">${esc(w.ar)}</p>` : ''}<p>${esc(w.ex)}</p>` : `<small>${tr('Tap to flip', 'اضغط للقلب')}</small>`}</div></button>`;
  if (V.flip) h += `<div class="actions">${btn(tr('Again', 'مرة أخرى'), () => { S.putMap('vl', String(w.id), 0); S.addDay(); V.i++; V.flip = false; rerender(); }, 'hot')}${btn(tr('Got it', 'حفظتها'), () => { S.putMap('vl', String(w.id), Math.min(4, lv(w) + 1)); S.addDay(); V.i++; V.flip = false; rerender(); }, 'primary')}</div>`;
  return h;
}

/* ---------------------------------------------------------------- progress, about, settings */
function vProgress() {
  let h = header(tr('Progress', 'التقدّم'));
  h += `<p>${pill(tr(`Streak ${S.streak()} days`, `${S.streak()} أيام متتالية`), COR)} ${pill(tr(`${S.history().length} sessions`, `${S.history().length} جلسة`), SKY)}</p>`;
  h += tile(`<h2>${tr('Estimated levels', 'المستويات التقريبية')}</h2>${SKILLS.map(sk => `<div style="margin-top:8px"><b>${skillName(sk)}: ${b1(level(sk))}</b>${bar(level(sk) / 9, 'var(--plum)')}</div>`).join('')}`, 'program');
  h += `<div class="tile" style="margin-top:12px"><h2>${tr('By skill', 'حسب المهارة')}</h2>${['foundation', 'reading', 'listening'].map(sk => { const [c, t] = S.stat('sk', sk); return `<div style="margin-top:8px"><b>${skillName(sk)}: ${c} / ${t}</b>${bar(t ? c / t : 0)}</div>`; }).join('')}</div>`;
  h += `<div class="tile" style="margin-top:12px"><h2>${tr('By question type', 'حسب نوع السؤال')}</h2>${QTYPES.map(([k, n]) => { const [c, t] = S.stat('ty', k); return `<div style="margin-top:8px"><b>${n}: ${c} / ${t}</b>${bar(t ? c / t : 0, 'var(--plum)')}</div>`; }).join('')}</div>`;
  h += `<h2 style="margin-top:16px">${tr('Recent sessions', 'الجلسات الأخيرة')}</h2>`;
  const hs = S.history().slice(0, 15);
  if (!hs.length) h += `<p>${tr('Nothing yet. Finish a practice set and it appears here.', 'لا يوجد شيء بعد. أنهِ تدريبًا وسيظهر هنا.')}</p>`;
  return h + `<div class="stack">${hs.map(x => `<div class="tile"><h3>${esc(x.title)}</h3><small>${x.score} / ${x.total}${x.band != null ? tr('  ·  indicative band ', '  ·  درجة تقريبية ') + x.band : ''}</small></div>`).join('')}</div>`;
}
function vAbout() {
  const c = (t, b, cls = '') => `<div class="tile ${cls}" style="margin-top:12px"><h3>${t}</h3><p>${b}</p></div>`;
  return header(tr('About and limits', 'عن التطبيق وحدوده')) +
    c(tr('Honest limits', 'حدود واضحة'), tr('Khotwa is a study aid, not an official IELTS product. It is not connected to or approved by the IELTS partners.', 'خطوة أداة مذاكرة وليست منتجًا رسميًا لآيلتس، ولا علاقة لها بالجهات المالكة للاختبار.'), 'c-fnd') +
    c(tr('Original content', 'محتوى أصلي'), tr('All passages, audio scripts, questions and prompts were written for this app. They follow IELTS formats but are not real exam papers.', 'كل القطع ونصوص الاستماع والأسئلة كُتبت لهذا التطبيق. تتبع شكل آيلتس لكنها ليست امتحانات حقيقية.')) +
    c(tr('Band estimates', 'تقدير الدرجات'), tr('Reading and Listening bands are indicative only and need at least 30 questions. Program levels are rough and only guide the plan.', 'درجات القراءة والاستماع تقريبية وتحتاج 30 سؤالًا على الأقل. مستويات البرنامج تقريبية لتوجيه الخطة فقط.')) +
    c(tr('Writing and Speaking', 'الكتابة والمحادثة'), tr('Estimates use measurable features (length, linking, word variety, speech rate) and your grammar level. They cannot judge ideas, grammar inside your text or pronunciation. For real feedback, ask a teacher.', 'التقدير يعتمد على خصائص قابلة للقياس (الطول، أدوات الربط، تنوّع الكلمات، سرعة الكلام) ومستوى قواعدك. لا يقيّم الأفكار أو القواعد داخل النص أو النطق. للتقييم الحقيقي اسأل معلمًا.')) +
    c(tr('Audio and speech', 'الصوت والكلام'), tr('Listening audio uses your device voice. Speech measuring uses the browser speech recognition, which may send audio to Apple or Google to convert it to text.', 'صوت الاستماع من صوت جهازك. قياس الكلام يستخدم التعرّف على الكلام في المتصفح، وقد يرسل الصوت إلى Apple أو Google لتحويله إلى نص.')) +
    c(tr('Your data', 'بياناتك'), tr('Progress is saved in this browser only. Use Backup in Settings to move it to another device or to the Android app.', 'التقدّم محفوظ في هذا المتصفح فقط. استخدم النسخ الاحتياطي في الإعدادات لنقله لجهاز آخر أو لتطبيق أندرويد.'));
}
function vSettings() {
  let h = header(tr('Settings', 'الإعدادات'));
  h += `<div class="tile"><h3>${tr('Language', 'اللغة')}</h3><div class="chips">${chip('English', S.lang === 'en', () => { S.lang = 'en'; rerender(); })}${chip('العربية', S.lang === 'ar', () => { S.lang = 'ar'; rerender(); })}</div><p class="small mute">${tr('The interface and lessons change. Exam passages and questions stay in English, as in the real test.', 'تتغير الواجهة والدروس. قطع وأسئلة الامتحان تبقى بالإنجليزية كما في الاختبار الحقيقي.')}</p></div>`;
  h += `<div class="tile" style="margin-top:12px"><h3>${tr('Test type', 'نوع الاختبار')}</h3><div class="chips">${['Academic', 'General Training'].map(t => chip(typeName(t), S.type === t, () => { S.type = t; S.replanFuture(); rerender(); })).join('')}</div>
    <h3>${tr('Target band', 'الدرجة المستهدفة')}</h3><div class="chips">${[5, 5.5, 6, 6.5, 7, 7.5, 8].map(g => chip(b1(g), S.goal === g, () => { S.goal = g; S.replanFuture(); rerender(); })).join('')}</div>
    <h3>${tr('Exam date (optional)', 'موعد الامتحان (اختياري)')}</h3><div class="row" style="justify-content:flex-start;gap:8px"><input type="date" class="inp" id="exd" style="max-width:190px" value="${R.exam ? new Date(R.exam * 864e5).toISOString().slice(0, 10) : ''}">${R.exam ? btn(tr('Clear', 'مسح'), () => { delete R.exam; save(); S.replanFuture(); rerender(); }) : ''}</div>
    <h3>${tr('Minutes per day', 'دقائق يوميًا')}</h3><div class="chips">${[15, 30, 45, 60].map(m => chip(String(m), S.minutes === m, () => { S.minutes = m; S.replanFuture(); rerender(); })).join('')}</div><p class="small mute">${tr('Changing these re-plans the coming days.', 'تغيير هذه الإعدادات يعيد تخطيط الأيام القادمة.')}</p></div>`;
  h += `<div class="tile" style="margin-top:12px"><h3>${tr('Listening voice', 'صوت الاستماع')}</h3><p class="small">${TTS.voice ? tr('Using: ', 'المستخدم: ') + esc(TTS.voice.name) : tr('No English voice found yet.', 'لم يتم العثور على صوت إنجليزي بعد.')}</p>${btn(tr('Test the voice', 'جرّب الصوت'), () => TTS.play([['A', 'This is how the listening audio will sound.']]))}${IOS ? `<p class="small mute">${tr('On iPhone: Settings, Accessibility, Spoken Content, Voices, English, to download a better voice.', 'على الآيفون: الإعدادات، تسهيلات الاستخدام، المحتوى المنطوق، الأصوات، الإنجليزية، لتحميل صوت أفضل.')}</p>` : ''}</div>`;
  h += `<div class="tile" style="margin-top:12px"><h3>${tr('Backup', 'النسخ الاحتياطي')}</h3><p class="small mute">${tr('The same backup works in the Android app and the web app.', 'نفس النسخة الاحتياطية تعمل في تطبيق أندرويد وموقع الويب.')}</p><div class="actions">${btn(tr('Download backup', 'تنزيل نسخة احتياطية'), () => { const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([S.export()], { type: 'application/json' })); a.download = 'khotwa-backup.json'; a.click(); })}${btn(tr('Copy as text', 'نسخ كنص'), () => { navigator.clipboard && navigator.clipboard.writeText(S.export()).then(() => toast(tr('Copied.', 'تم النسخ.'))); })}</div>
    <textarea class="inp" id="imp" style="min-height:90px" placeholder="${tr('Paste backup text here', 'الصق نص النسخة الاحتياطية هنا')}"></textarea><div class="actions">${btn(tr('Import pasted data', 'استيراد النص الملصوق'), () => { toast(S.import($('#imp').value) ? tr('Imported.', 'تم الاستيراد.') : tr('That is not valid backup text.', 'هذا ليس نص نسخة احتياطية صالح.')); rerender(); })}<label class="btn">${tr('Import file', 'استيراد ملف')}<input type="file" accept=".json,.txt,application/json,text/plain" hidden id="impf"></label></div></div>`;
  h += classCard();
  h += `<div class="tile" style="margin-top:12px"><h3>${tr('Reset', 'إعادة الضبط')}</h3><p class="small mute">${tr('Deletes progress, drafts and settings in this browser.', 'يحذف التقدّم والمسودات والإعدادات من هذا المتصفح.')}</p>${V.confirm ? btn(tr('Yes, delete', 'نعم، احذف'), () => { S.reset(); V.confirm = false; location.hash = 'home'; }, 'danger') + ' ' + btn(tr('Cancel', 'إلغاء'), () => { V.confirm = false; rerender(); }) : btn(tr('Reset everything', 'احذف كل شيء'), () => { V.confirm = true; rerender(); }, 'danger')}</div>`;
  V.after = () => { const ed = $('#exd'); if (ed) ed.onchange = () => { if (!ed.value) return; const d = Math.floor(Date.parse(ed.value + 'T00:00:00Z') / 864e5); if (d <= today()) { toast(tr('Pick a future date.', 'اختر تاريخًا قادمًا.')); return; } R.exam = d; save(); S.replanFuture(); toast(tr('Exam date saved. Your plan now ends before it.', 'تم حفظ موعد الامتحان. خطتك تنتهي قبله الآن.')); rerender(); }; const f = $('#impf'); if (f) f.onchange = async () => { const t = await f.files[0].text(); toast(S.import(t) ? tr('Imported.', 'تم الاستيراد.') : tr('That is not valid backup text.', 'هذا ليس نص نسخة احتياطية صالح.')); rerender(); }; };
  return h;
}

/* ---------------------------------------------------------------- placement (5 parts) */
const PARTS = 5;
const partBar = step => `<div style="display:grid;grid-template-columns:repeat(5,1fr);gap:6px;margin:8px 0 14px">${[1, 2, 3, 4, 5].map(i => `<i style="height:9px;border-radius:9px;border:2px solid var(--ink);background:${i < step ? 'var(--em)' : i === step ? 'var(--mus)' : 'var(--card)'}"></i>`).join('')}</div>`;
function vPlacement() {
  const step = S.mapInt('plc', 'step'), next = n => { S.putMap('plc', 'step', n); V = {}; rerender(); scrollTo(0, 0); };
  const title = step >= 1 && step <= PARTS ? tr(`Placement · part ${step} of ${PARTS}`, `تحديد المستوى · الجزء ${step} من ${PARTS}`) : tr('Placement test', 'اختبار تحديد المستوى');
  if (step === 0) {
    let h = header(title);
    h += `<h2>${tr('Find your real level', 'اعرف مستواك الحقيقي')}</h2><p>${tr('Each skill is tested the way the exam tests it. Your program is built from the results.', 'كل مهارة تُختبر بطريقة الامتحان، وبرنامجك يُبنى من النتائج.')}</p><div class="stack">`;
    h += [[tr('Grammar and vocabulary', 'القواعد والمفردات'), tr('18 questions, from easy to hard. 12 minutes.', '18 سؤالًا من السهل للصعب. 12 دقيقة.')], [tr('Reading', 'القراءة'), tr('One academic passage, 10 questions. 15 minutes.', 'قطعة أكاديمية و10 أسئلة. 15 دقيقة.')], [tr('Listening', 'الاستماع'), tr('A call and a short talk. The audio plays once. 10 questions.', 'مكالمة وحديث قصير. التسجيل يُشغَّل مرة واحدة. 10 أسئلة.')], [tr('Writing', 'الكتابة'), tr('A short opinion essay, 150 words or more. 15 minutes.', 'مقال رأي قصير، 150 كلمة أو أكثر. 15 دقيقة.')], [tr('Speaking', 'المحادثة'), tr('Talk about a topic for up to 2 minutes. The browser listens and measures your speech.', 'تكلّم عن موضوع حتى دقيقتين. المتصفح يستمع ويقيس كلامك.')]]
      .map(([t, d], i) => `<div class="tile">${pill(String(i + 1), LIME)} <b>${t}</b><br><small>${d}</small></div>`).join('');
    h += `</div><div class="notice" style="margin-top:12px"><b>${tr('Before you start', 'قبل أن تبدأ')}</b><br>${tr('Use headphones in a quiet place. You can stop between parts and continue later. Writing and Speaking results are estimates.', 'استخدم سماعات في مكان هادئ. يمكنك التوقف بين الأجزاء والإكمال لاحقًا. نتيجة الكتابة والمحادثة تقديرية.')}</div>`;
    return h + `<div class="actions">${btn(tr('Start', 'ابدأ'), () => next(1), 'primary')}</div>`;
  }
  if (step === 1) {
    V.ltr = true;
    const qs = V.qs || (V.qs = C.bank.filter(q => q.placement && q.skill === 'foundation')); V.ans = V.ans || {};
    const t = countdown('t', 12 * 60);
    const finish = () => { if (V.done) return; V.done = true; S.putMap('plc', 'fc', qs.filter((q, i) => V.ans[i] === q.answer).length); S.putMap('plc', 'ft', qs.length); next(2); };
    TICK = () => { tickCountdown(t, finish); const e = $('#pt'); if (e) e.textContent = fmt(t.left); };
    let h = header(title, true, timer('pt', t.left)) + partBar(1) + `<h2>${tr('Grammar and vocabulary', 'القواعد والمفردات')}</h2><p class="small mute">${tr('Choose the best answer. If you do not know, leave it: guessing makes your level less accurate.', 'اختر الإجابة الأفضل. إذا لم تعرف اتركها: التخمين يقلل دقة مستواك.')}</p>`;
    h += qs.map((q, i) => `<div class="qblock en"><span class="qn">${i + 1}</span>${esc(q.q)}<div class="options">${q.options.map((o, k) => `<button class="option ${V.ans[i] === k ? 'selected' : ''}" data-a="${act(() => { V.ans[i] = k; rerender(); })}"><span class="letter">${'ABCD'[k]}</span><span>${esc(o)}</span></button>`).join('')}</div></div>`).join('');
    return h + `<p class="small">${tr(`${Object.keys(V.ans).length} of ${qs.length} answered`, `تمت الإجابة عن ${Object.keys(V.ans).length} من ${qs.length}`)}</p><div class="actions">${btn(tr('Finish part 1', 'أنهِ الجزء 1'), finish, 'hot')}</div>`;
  }
  if (step === 2 || step === 3) {
    V.ltr = true;
    const kind = step === 2 ? 'R' : 'L', s = kind === 'R' ? C.placeR : C.placeL; V.ans = V.ans || {};
    const finish = () => { if (V.done) return; V.done = true; TTS.stop(); let c = 0, n = 0; for (const g of s.groups) for (const q of g.qs) { n++; if (isCorrect(q, V.ans[s.id + '|' + q.n])) c++; } if (kind === 'R') { S.putMap('plc', 'rc', c); S.putMap('plc', 'rt', n); } else { S.putMap('plc', 'lc', c); S.putMap('plc', 'lt', n); } next(step + 1); };
    const t = countdown('t', 15 * 60, kind === 'R');
    if (kind === 'R') TICK = () => { tickCountdown(t, finish); const e = $('#pt'); if (e) e.textContent = fmt(t.left); };
    let h = header(title, true, kind === 'R' ? timer('pt', t.left) : '') + partBar(step) + `<h2>${kind === 'R' ? tr('Reading', 'القراءة') : tr('Listening', 'الاستماع')}</h2><h3 class="en">${esc(s.title)}</h3>`;
    if (kind === 'R') h += passageHTML(s);
    else {
      h += `<p class="en">${esc(s.sub)}</p><div class="tile c-lis"><b>${tr('Read the questions first. The audio plays once, like the real test.', 'اقرأ الأسئلة أولًا. التسجيل يُشغَّل مرة واحدة كما في الامتحان.')}</b><div class="actions">${btn(TTS.speaking ? tr('Playing...', 'يعمل...') : V.played ? tr('Played', 'تم التشغيل') : tr('Play audio', 'شغّل الصوت'), () => { V.played = true; TTS.play(s.script, rerender, 8, s.id); rerender(); }, 'primary', TTS.speaking || V.played)}</div>${!TTS.ok() ? `<p class="small">${tr('This browser cannot play audio.', 'هذا المتصفح لا يستطيع تشغيل الصوت.')}</p>` : ''}</div>`;
    }
    h += groupsHTML(s, V.ans);
    return h + `<div class="actions">${btn(tr('Finish this part', 'أنهِ هذا الجزء'), finish, 'hot', kind === 'L' && (!V.played || TTS.speaking) && TTS.ok())}</div>${kind === 'L' && !V.played ? `<p class="small mute">${tr('Play the audio to continue.', 'شغّل التسجيل للمتابعة.')}</p>` : ''}`;
  }
  if (step === 4) {
    V.ltr = true;
    const t = countdown('t', 15 * 60, false), text = () => S.mapStr('draft', 'placement');
    TICK = () => { tickCountdown(t, () => { const ta = $('#essay'); if (ta) S.putMap('draft', 'placement', ta.value); V.sub = true; rerender(); }); const e = $('#pt'); if (e) e.textContent = fmt(t.left); };
    let h = header(title, true, timer('pt', t.left)) + partBar(4) + `<h2>${tr('Writing', 'الكتابة')}</h2><div class="passage en"><p>Some people think that students should study only subjects that are useful for their future jobs. Others believe they should also study subjects such as art and history.</p><p><b>Discuss both views and give your own opinion. Write at least 150 words.</b></p></div>`;
    if (!V.sub) {
      h += `<div class="actions">${btn(t.run ? tr('Pause timer', 'أوقف المؤقت') : tr('Start timer', 'ابدأ المؤقت'), () => { t.run = !t.run; t.last = Date.now(); rerender(); })}</div><textarea class="inp" id="essay" spellcheck="false" autocorrect="off" autocapitalize="sentences"></textarea><p class="small" id="wc"></p>`;
      h += `<div class="actions">${btn(tr('Submit essay', 'سلّم المقال'), () => { const ta = $('#essay'); S.putMap('draft', 'placement', ta.value); if (textStats(ta.value).words < 20) { toast(tr('Write at least 20 words.', 'اكتب 20 كلمة على الأقل.')); return; } V.sub = true; rerender(); }, 'hot')}</div><p class="small mute">${tr('Write without a translator or spell checker, so the result shows your real level.', 'اكتب بدون مترجم أو مصحح إملائي حتى تعكس النتيجة مستواك الحقيقي.')}</p>`;
      V.after = () => { const ta = $('#essay'); ta.value = text(); const upd = () => { const n = textStats(ta.value).words; $('#wc').textContent = tr(`${n} words`, `${n} كلمة`) + (n < 150 ? tr('  (aim for 150+)', '  (الهدف 150+)') : '  ✓'); }; ta.oninput = () => { upd(); S.putMap('draft', 'placement', ta.value); }; upd(); };
      return h;
    }
    V.ck = V.ck || {};
    const yn = (i, q) => `<p style="margin-top:10px">${q}</p><div class="chips">${[[1, tr('Yes', 'نعم')], [2, tr('Not sure', 'غير متأكد')], [0, tr('No', 'لا')]].map(([v, l]) => chip(l, V.ck[i] === v, () => { V.ck[i] = v; rerender(); })).join('')}</div>`;
    h += `<div class="tile"><h3>${tr('Check your own essay', 'قيّم مقالك بنفسك')}</h3><p class="small mute">${tr('Answer honestly. This is combined with what the app measures.', 'أجب بصدق. يُدمج هذا مع ما يقيسه التطبيق.')}</p>${yn(0, tr('Did you discuss both views and give a clear opinion?', 'هل ناقشت الرأيين وقدّمت رأيًا واضحًا؟'))}${yn(1, tr('Did each paragraph have an idea, explanation and example?', 'هل كان في كل فقرة فكرة وشرح ومثال؟'))}${yn(2, tr('Are you fairly sure most sentences are grammatically correct?', 'هل أنت متأكد إلى حد ما أن معظم الجمل صحيحة نحويًا؟'))}</div>`;
    return h + `<div class="actions">${btn(tr('Continue to speaking', 'تابع إلى المحادثة'), () => {
      const yes = Object.values(V.ck).reduce((a, v) => a + (v === 1 ? 1 : v === 2 ? 0.5 : 0), 0), ts = textStats(text()), g = accBand(S.mapInt('plc', 'fc'), S.mapInt('plc', 'ft'));
      S.putMap('plc', 'w', writingLevel(text(), 150, g, yes)); S.putMap('plc', 'wn', ts.words); S.putMap('plc', 'wl', ts.links); S.putMap('plc', 'wv', Math.round(ts.variety * 100)); next(5);
    }, 'hot', Object.keys(V.ck).length < 3)}</div>`;
  }
  if (step === 5) {
    V.ltr = true;
    const cap = V.cap || (V.cap = SpeechCap(() => { const e = $('#cap'); if (e) e.textContent = (V.cap.text + ' ' + V.cap.partial).trim() || tr('Listening...', 'يستمع...'); }));
    V.onLeave = () => cap.stop();
    const prep = countdown('prep', 60, false);
    TICK = () => { tickCountdown(prep, () => { prep.run = false; rerender(); }); const p = $('#prep'); if (p) p.textContent = fmt(prep.left); if (cap.running) { const e = $('#mt'); if (e) e.textContent = fmt(120 - cap.seconds); if (cap.seconds >= 120) { cap.stop(); V.fin = true; rerender(); } } };
    let h = header(title) + partBar(5) + `<h2>${tr('Speaking', 'المحادثة')}</h2><div class="tile c-fnd en"><p style="font-weight:700">Describe a skill you learned that you think is useful.</p><p>You should say:</p><ul><li>what the skill is</li><li>how you learned it</li><li>how often you use it</li><li>and explain why it is useful.</li></ul></div>`;
    if (!V.fin) {
      if (!cap.running) h += `<div class="actions">${btn(prep.run ? tr('Preparing...', 'تحضير...') : tr('1 minute to prepare', 'دقيقة للتحضير'), () => { prep.run = true; prep.last = Date.now(); rerender(); }, '', prep.run || prep.left <= 0)}${prep.run ? timer('prep', prep.left) : ''}</div>`;
      h += `<div class="tile" style="margin-top:12px"><h3>${tr('Speak for up to 2 minutes', 'تكلّم حتى دقيقتين')}</h3><p class="small mute">${tr('Speak in English as in the exam. Speech recognition writes what you say, then the app measures your speed and vocabulary.', 'تكلّم بالإنجليزية كما في الامتحان. التعرّف على الكلام يكتب ما تقوله ثم يقيس التطبيق سرعتك ومفرداتك.')}</p>`;
      h += !cap.running ? `<div class="actions">${btn(tr('Start speaking', 'ابدأ الكلام'), () => { prep.run = false; cap.start(); rerender(); }, 'hot')}</div>` : `<div class="row">${timer('mt', 120 - cap.seconds)}${btn(tr('I have finished', 'انتهيت'), () => { cap.stop(); V.fin = true; rerender(); }, 'hot')}</div><p class="en" id="cap" style="font-size:15px">${esc((cap.text + ' ' + cap.partial).trim() || tr('Listening...', 'يستمع...'))}</p>`;
      if (!cap.available || cap.error) h += `<p class="small"><b>${cap.error === 'permission' ? tr('Microphone permission was denied.', 'تم رفض إذن الميكروفون.') : tr('Speech recognition is not available in this browser. On iPhone use Safari with Siri turned on; on Android use Chrome.', 'التعرّف على الكلام غير متاح في هذا المتصفح. على الآيفون استخدم سفاري مع تفعيل Siri، وعلى أندرويد استخدم كروم.')} ${tr('Speak aloud anyway, then answer the questions.', 'تكلّم بصوت عالٍ على أي حال ثم أجب عن الأسئلة.')}</b></p><div class="actions">${btn(tr('Skip to the questions', 'انتقل إلى الأسئلة'), () => { V.fin = true; rerender(); })}</div>`;
      return h + '</div>';
    }
    const words = textStats(cap.text).words;
    if (words) h += `<div class="tile"><h3>${tr('What was heard', 'ما تم سماعه')}</h3><b>${tr(`${words} words in ${cap.seconds} seconds`, `${words} كلمة في ${cap.seconds} ثانية`)}</b><p class="en" style="font-size:15px">${esc(cap.text)}</p></div>`;
    h += `<div class="tile" style="margin-top:12px"><h3>${tr('Check your own speaking', 'قيّم كلامك بنفسك')}</h3><p>${tr('How long could you keep talking?', 'كم استطعت أن تستمر في الكلام؟')}</p><div class="chips">${[[0, tr('Under 30 seconds', 'أقل من 30 ثانية')], [1, tr('About a minute', 'حوالي دقيقة')], [2, tr('Nearly 2 minutes', 'قرابة دقيقتين')]].map(([v, l]) => chip(l, V.sl === v, () => { V.sl = v; rerender(); })).join('')}</div>
      <p>${tr('How often did you stop to search for words?', 'كم مرة توقفت لتبحث عن كلمات؟')}</p><div class="chips">${[[0, tr('Often', 'كثيرًا')], [1, tr('Sometimes', 'أحيانًا')], [2, tr('Rarely', 'نادرًا')]].map(([v, l]) => chip(l, V.sp === v, () => { V.sp = v; rerender(); })).join('')}</div></div>`;
    return h + `<div class="actions">${btn(tr('See my results', 'شاهد نتيجتي'), () => {
      const g = accBand(S.mapInt('plc', 'fc'), S.mapInt('plc', 'ft'));
      S.putMap('plc', 's', speakingLevel(cap.text, cap.seconds, g, V.sl, V.sp)); S.putMap('plc', 'sw', words); S.putMap('plc', 'ss', cap.seconds);
      applyFullPlacement(); next(6);
    }, 'hot', V.sl == null || V.sp == null)}${btn(tr('Try speaking again', 'جرّب الكلام مرة أخرى'), () => { V.fin = false; V.cap = null; rerender(); })}</div>`;
  }
  // results
  const pr = program(), P = k => S.mapInt('plc', k), sw = P('sw'), ss = P('ss');
  let h = header(tr('Your level', 'مستواك'), false);
  h += `<div class="tile c-fnd"><h3>${tr('Estimated overall', 'المستوى العام التقريبي')}</h3><div class="num" style="font-size:56px;display:block">${b1(pr.current)}</div><b>${tr(`Target ${b1(pr.target)}. Program: about ${pr.weeks} weeks.`, `الهدف ${b1(pr.target)}. البرنامج: حوالي ${pr.weeks} أسبوع.`)}</b></div><div class="stack" style="margin-top:12px">`;
  h += [['foundation', `${P('fc')} / ${P('ft')}`, tr('correct, from easy to hard questions', 'صحيحة، من أسئلة سهلة إلى صعبة')], ['reading', `${P('rc')} / ${P('rt')}`, tr('correct on an academic passage', 'صحيحة في قطعة أكاديمية')], ['listening', `${P('lc')} / ${P('lt')}`, tr('correct, audio played once', 'صحيحة، والتسجيل مرة واحدة')],
  ['writing', tr(`${P('wn')} words`, `${P('wn')} كلمة`), tr(`${P('wl')} linking words, word variety ${P('wv')}%, plus grammar and your check`, `${P('wl')} أدوات ربط، تنوّع الكلمات ${P('wv')}%، مع القواعد وتقييمك`)],
  ['speaking', sw > 0 && ss > 0 ? tr(`${Math.round(sw * 60 / ss)} words a minute`, `${Math.round(sw * 60 / ss)} كلمة في الدقيقة`) : tr('self-check', 'تقييم ذاتي'), tr('speech rate and vocabulary, plus grammar and your check', 'سرعة الكلام والمفردات، مع القواعد وتقييمك')]]
    .map(([sk, main, how]) => `<div class="tile"><div class="row"><h3>${skillName(sk)}</h3>${pill(b1(level(sk)), lvColor(level(sk)))}</div><b>${main}</b><br><small class="mute">${how}</small></div>`).join('');
  h += `</div><p class="small mute">${tr('Reading, Listening and Grammar are measured. Writing and Speaking are estimates; ask a teacher to check them and adjust the level in My program if needed.', 'القراءة والاستماع والقواعد مقاسة فعلًا. الكتابة والمحادثة تقديرية؛ اطلب من معلم مراجعتها وعدّل المستوى من صفحة برنامجي إذا لزم.')}</p>`;
  return h + `<div class="actions">${go(tr('See my program', 'شاهد برنامجي'), 'program', 'primary')}${btn(tr('Retake the test', 'أعد الاختبار'), () => { S.resetPlacement(); V = {}; rerender(); })}</div>`;
}

/* ---------------------------------------------------------------- shell: router, nav, menu */
const ICON = { home: '<path d="M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>', plan: '<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M8 2v4M16 2v4M3 10h18M8 15l2.5 2.5L16 13"/>', practice: '<path d="M4 20V10M10 20V4M16 20v-8M22 20H2"/>', words: '<rect x="3" y="5" width="14" height="16" rx="3"/><path d="M7 1h12a2 2 0 0 1 2 2v14"/>', menu: '<path d="M4 7h16M4 12h16M4 17h10"/>' };
function navHTML(r) {
  const tabs = [['home', tr('Home', 'الرئيسية')], ['plan', tr('Plan', 'الخطة')], ['practice', tr('Practice', 'تدريب')], ['words', tr('Words', 'كلمات')]];
  return tabs.map(([k, l]) => `<a href="#${k}" class="${r === k ? 'on' : ''}"><svg viewBox="0 0 24 24">${ICON[k]}</svg><span>${l}</span></a>`).join('') + `<button class="menubtn2" data-menu="1"><svg viewBox="0 0 24 24">${ICON.menu}</svg><span>${tr('More', 'المزيد')}</span></button>`;
}
function menuHTML() {
  const M = [['program', tr('My program', 'برنامجي'), 'c-wri'], ['placement', tr('Placement test', 'اختبار تحديد المستوى'), 'c-fnd'], ['lessons', tr('Lessons', 'الدروس'), 'c-lis'], ['mocks', tr('Mock tests', 'اختبارات المحاكاة'), 'c-rea'], ['progress', tr('Progress', 'التقدّم'), 'c-spe'], ['teacher', tr('Teacher', 'المعلم/ة'), 'c-lis'], ['settings', tr('Settings', 'الإعدادات'), 'c-em'], ['about', tr('About and limits', 'عن التطبيق وحدوده'), 'c-ink']];
  return `<div class="mgrid">${M.map(([h, t, c]) => `<a href="#${h}" class="${c}"><b>${t}</b></a>`).join('')}</div><p class="mfoot">${tr('Independent practice, not affiliated with IELTS, the British Council or IDP.', 'تدريب مستقل، غير تابع لآيلتس أو المجلس الثقافي البريطاني أو IDP.')}</p>`;
}
const LTR_ROUTES = ['quiz', 'paper', 'write', 'speak', 'results', 'words', 'review', 'dict'];
function render(same) {
  const hash = decodeURIComponent(location.hash.slice(1)) || 'home', p = hash.split('/'), r = p[0];
  if (!same && hash !== vKey) { if (V.onLeave) V.onLeave(); TTS.stop(); V = {}; vKey = hash; }
  A = {}; aN = 0; TICK = null; V.after = null;
  document.documentElement.lang = I18n.ar ? 'ar' : 'en'; document.documentElement.dir = I18n.ar ? 'rtl' : 'ltr';
  let html;
  try {
    html = ({
      home: vHome, program: vProgram, plan: vPlan, lessons: vLessons, practice: vPractice, mocks: vMocks, words: vWords, progress: vProgress, about: vAbout, settings: vSettings, results: vResults, placement: vPlacement,
      lesson: () => vLesson(p[1]), teacher: () => vTeacher(p[1] || ''), review: vReview, dict: vDict, ext: () => vExt(p[1] || ''), quiz: () => p[1] === 'placement' ? vPlacement() : vQuiz(p[1] || 'quick', p[2] || ''), paper: () => vPaper(p[1] || 'R', p[2] || ''), write: () => vWrite(p[1] || ''), speak: () => vSpeak(p[1] || ''),
    }[r] || vHome)();
  } catch (e) { console.error(e); html = `<div class="notice error">${esc(e.message)}</div>`; }
  const main = $('#main');
  main.dir = (I18n.ar && (LTR_ROUTES.includes(r) || V.ltr)) ? 'ltr' : '';
  main.innerHTML = `<div class="view ${same ? 'still' : ''}">${html}</div>`;
  $('#tabs').innerHTML = navHTML(r);
  $('#tabs').style.display = ['home', 'plan', 'practice', 'words', 'program'].includes(r) ? '' : 'none';
  $('#menu').innerHTML = menuHTML();
  const gc = $('.goalchip'); if (gc) gc.textContent = (S.type === 'Academic' ? 'AC' : 'GT') + ' · ' + b1(S.goal);
  if (V.after) V.after();
}
document.addEventListener('click', e => {
  const m = e.target.closest('[data-menu],.menubtn'); if (m) { document.body.classList.toggle('menu-open'); return; }
  if (e.target.closest('#menu a')) document.body.classList.remove('menu-open');
  const b = e.target.closest('[data-a]'); if (b && A[b.dataset.a]) { e.preventDefault(); A[b.dataset.a](); }
});
document.addEventListener('input', e => { const k = e.target.dataset && e.target.dataset.in; if (k && A[k]) A[k](e.target.value); });
window.addEventListener('hashchange', () => { document.body.classList.remove('menu-open'); render(false); scrollTo(0, 0); });
setInterval(() => { if (TICK) try { TICK(); } catch (e) { } }, 500);
window.addEventListener('beforeunload', () => { if (V.onLeave) V.onLeave(); });

(async () => {
  try { await loadContent(); } catch (e) { $('#main').innerHTML = `<div class="notice error">${tr('Could not load the content. Check your connection and reload.', 'تعذّر تحميل المحتوى. تحقّق من الاتصال وأعد التحميل.')}</div>`; return; }
  render(false);
  if (R.cls && (!R.syncAt || Date.now() - R.syncAt > 3600e3)) syncNow();
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => { });
})();

/* ---------------------------------------------------------------- mistakes review */
function vReview() {
  if (!V.q) { V.q = shuffle(mistakesDue()).slice(0, 12).map(m => m.key); V.i = 0; V.ok = 0; V.ltr = true; }
  let h = header(tr('My mistakes', 'أخطائي'));
  if (!V.q.length) return h + `<div class="tile c-fnd"><h2>${tr('Nothing to review now', 'لا شيء للمراجعة الآن')}</h2><p>${tr(`${mistakes().length} questions are waiting for a later day. Wrong answers in practice are added here automatically.`, `${mistakes().length} سؤالًا تنتظر يومًا لاحقًا. الإجابات الخاطئة في التدريب تُضاف هنا تلقائيًا.`)}</p></div>`;
  if (V.i >= V.q.length) return h + `<div class="tile c-fnd"><h2>${tr('Review done', 'انتهت المراجعة')}</h2><div class="num">${V.ok} / ${V.q.length}</div><p>${tr('Right answers come back later; after two in a row they leave the list.', 'الإجابات الصحيحة ترجع لاحقًا؛ وبعد مرتين متتاليتين تخرج من القائمة.')}</p></div><div class="actions">${go(tr('Back to home', 'العودة للرئيسية'), 'home', 'primary')}</div>`;
  const key = V.q[V.i], it = mistakeItem(key);
  if (!it) { reviewAnswer(key, true); reviewAnswer(key, true); V.i++; return vReview(); }
  h += bar((V.i + 1) / V.q.length, 'var(--cor)') + `<p class="small mute">${V.i + 1} / ${V.q.length}</p>`;
  const done = V.checked;
  const next = () => { V.i++; V.checked = false; V.a = null; rerender(); scrollTo(0, 0); };
  if (it.bank) {
    const q = it.bank;
    if (q.context) h += `<div class="passage en"><p>${esc(q.context)}</p></div>`;
    h += `<h3 class="en">${esc(q.q)}</h3><div class="options">${q.options.map((o, k) => `<button class="option ${V.a === k ? 'selected' : ''} ${done && k === q.answer ? 'right' : ''} ${done && V.a === k && k !== q.answer ? 'wrong' : ''}" data-a="${act(() => { if (!done) { V.a = k; rerender(); } })}"><span class="letter">${'ABCD'[k]}</span><span>${esc(o)}</span></button>`).join('')}</div>`;
    if (!done) h += `<div class="actions">${btn(tr('Check', 'تحقق'), () => { if (V.a == null) return; V.checked = true; const ok = V.a === q.answer; if (ok) V.ok++; reviewAnswer(key, ok); rerender(); }, 'primary', V.a == null)}</div>`;
    else h += `<p class="small en">${esc(q.why)}</p><div class="actions">${btn(tr('Next', 'التالي'), next, 'primary')}</div>`;
    return h;
  }
  const { sec, g, q } = it, isL = !!sec.script;
  h += `<p class="small mute en">${esc(sec.title)}</p><div class="ins">${esc(g.ins)}</div>`;
  h += `<div class="actions">${btn(V.ctx ? tr('Hide', 'إخفاء') : isL ? tr('Show transcript', 'اعرض النص') : tr('Show passage', 'اعرض القطعة'), () => { V.ctx = !V.ctx; rerender(); })}</div>`;
  if (V.ctx) h += isL ? `<div class="passage en"><p style="white-space:pre-wrap">${esc(sec.script.map(x => x[0] + ': ' + x[1]).join('\n'))}</p></div>` : passageHTML(sec);
  V.ans = V.ans || {};
  const k = 'r' + key;
  h += questionHTML(g, q, k, V.ans);
  if (!done) h += `<div class="actions">${btn(tr('Check', 'تحقق'), () => { const a = V.ans[k]; if (a == null || a === '') return; V.checked = true; const ok = isCorrect(q, a); V.right = ok; if (ok) V.ok++; reviewAnswer(key, ok); rerender(); }, 'primary')}</div>`;
  else h += `<div class="notice ${V.right ? 'good' : 'error'}"><b>${V.right ? tr('Correct', 'صحيح') : tr('Answer: ', 'الصحيح: ') + esc(correctText(g, q))}</b><br><span class="en">${esc(q.w)}</span></div><div class="actions">${btn(tr('Next', 'التالي'), () => { V.ans = {}; next(); }, 'primary')}</div>`;
  return h;
}

/* ---------------------------------------------------------------- dictation */
function vDict() {
  if (!V.items) { V.items = dictSet(); V.i = 0; V.ok = 0; V.ltr = true; }
  let h = header(tr('Dictation', 'الإملاء'));
  if (V.i >= V.items.length) {
    if (!V.saved) { S.addStat('dict', 'all', V.ok, V.items.length); S.addHistory(tr('Dictation', 'إملاء'), V.ok, V.items.length, null); V.saved = true; }
    return h + `<div class="tile c-fnd"><h2>${tr('Done', 'انتهى')}</h2><div class="num">${V.ok} / ${V.items.length}</div><p>${tr('In the exam, spelling and numbers must be exact. Section 1 depends on them.', 'في الامتحان يجب أن تكون التهجئة والأرقام دقيقة. الجزء الأول يعتمد عليها.')}</p></div><div class="actions">${btn(tr('Another round', 'جولة أخرى'), () => { V.items = null; rerender(); }, 'primary')}</div>`;
  }
  const it = V.items[V.i];
  h += bar((V.i + 1) / V.items.length, 'var(--sky)') + `<p class="small mute">${V.i + 1} / ${V.items.length}</p>`;
  h += `<div class="tile c-lis"><b>${tr('Listen and write exactly what you hear.', 'استمع واكتب بالضبط ما تسمعه.')}</b><div class="actions">${btn(tr('Play', 'شغّل'), () => TTS.play([['A', it.say]]), 'primary')}</div></div>`;
  h += `<p class="en" style="margin-top:12px"><b>${esc(it.prompt)}</b></p><input class="inp" id="dk" autocomplete="off" autocapitalize="off" spellcheck="false" ${V.checked ? 'disabled' : ''}>`;
  if (!V.checked) h += `<div class="actions">${btn(tr('Check', 'تحقق'), () => { const a = $('#dk').value; if (!a.trim()) return; V.last = a; V.right = dictCorrect(it, a); if (V.right) V.ok++; V.checked = true; rerender(); }, 'primary')}</div>`;
  else h += `<div class="notice ${V.right ? 'good' : 'error'}"><b>${V.right ? tr('Correct', 'صحيح') : tr('Answer: ', 'الصحيح: ') + esc(it.ans[0])}</b><br><span class="en small">"${esc(it.say)}"</span></div><div class="actions">${btn(tr('Next', 'التالي'), () => { V.i++; V.checked = false; rerender(); }, 'primary')}</div>`;
  V.after = () => { const e = $('#dk'); if (e) { e.value = V.checked ? V.last : ''; if (!V.checked) e.focus(); } };
  return h;
}

/* ---------------------------------------------------------------- server: classes and AI feedback */
async function api(path, body) {
  try { const r = await fetch('/api/' + path, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) }); const o = await r.json().catch(() => ({ error: 'Bad response' })); if (!r.ok && !o.error) o.error = 'HTTP ' + r.status; return o; }
  catch (e) { return { error: tr('No internet connection.', 'لا يوجد اتصال بالإنترنت.') }; }
}
/** What the teacher sees: levels, plan, recent sessions. Same shape as the Android app sends. */
function syncData() {
  const hs = S.history(), wk = today() - 7;
  return { overall: currentOverall(), est: R.est || {}, goal: S.goal, type: S.type, placed: S.placed, minutes: S.minutes, streak: S.streak(),
    exam: R.exam || 0, week: hs.filter(h => h.day > wk).length, recent: hs.slice(0, 8).map(h => ({ t: h.title, s: h.score, n: h.total, d: h.day })), mistakes: mistakes().length, done: R.assignDone || [] };
}
let syncT;
async function syncNow() {
  if (!R.cls) return;
  const o = await api('sync', { id: R.cls.id, token: R.cls.token, data: syncData() });
  if (o.error) { if (/Not in this class/.test(o.error)) { delete R.cls; save(); } return; }
  R.cls.className = o.className; R.assign = o.assignments || []; R.syncAt = Date.now(); save();
  if (['home'].includes(location.hash.slice(1) || 'home')) rerender();
}
const syncSoon = () => { clearTimeout(syncT); syncT = setTimeout(syncNow, 4000); };
(function hookHistory() { const add = S.addHistory.bind(S); S.addHistory = (...a) => { add(...a); syncSoon(); }; })();

function assignHTML() {
  const done = R.assignDone || [], list = (R.assign || []).filter(a => !done.includes(a.id));
  if (!R.cls || !list.length) return '';
  return `<div class="tile c-wri"><h3>${tr('From your teacher', 'من معلمتك')} · ${esc(R.cls.className || '')}</h3><div class="stack" style="margin-top:8px">${list.map(a => `<div class="task"><button class="chk" data-a="${act(() => { R.assignDone = done.concat(a.id); save(); syncSoon(); rerender(); })}"></button><a href="#${esc(a.route)}" style="flex:1;text-decoration:none"><b>${esc(a.title || a.route)}</b></a></div>`).join('')}</div></div>`;
}
function classCard() {
  if (R.cls) return `<div class="tile" style="margin-top:12px"><h3>${tr('My class', 'فصلي')}</h3><p>${esc(R.cls.className || R.cls.code)} · ${esc(R.cls.name)}</p><p class="small mute">${tr('Your levels and recent sessions are shared with your teacher.', 'مستوياتك وجلساتك الأخيرة تُشارك مع معلمتك.')}${R.syncAt ? ' ' + tr('Last sync: ', 'آخر مزامنة: ') + new Date(R.syncAt).toLocaleString() : ''}</p><div class="actions">${btn(tr('Sync now', 'زامن الآن'), async () => { await syncNow(); toast(tr('Synced.', 'تمت المزامنة.')); rerender(); })}${btn(tr('Leave class', 'غادر الفصل'), () => { delete R.cls; delete R.assign; save(); rerender(); }, 'danger')}</div></div>`;
  return `<div class="tile" style="margin-top:12px"><h3>${tr('Join a class', 'انضم إلى فصل')}</h3><p class="small mute">${tr('If your teacher gave you a class code, join to receive tasks and share your progress.', 'إذا أعطتك معلمتك كود فصل، انضم لتصلك المهام وتشارك تقدّمك.')}</p>
    <input class="inp" id="jc" placeholder="${tr('Class code', 'كود الفصل')}" style="text-transform:uppercase;margin-bottom:8px"><input class="inp" id="jn" placeholder="${tr('Your name', 'اسمك')}">
    <div class="actions">${btn(tr('Join', 'انضم'), async () => { const o = await api('join', { code: $('#jc').value.trim(), name: $('#jn').value.trim() }); if (o.error) { toast(o.error); return; } R.cls = { id: o.id, token: o.token, code: o.code, className: o.className, name: $('#jn').value.trim() }; save(); await syncNow(); toast(tr('Joined ', 'انضممت إلى ') + o.className); rerender(); }, 'primary')}</div>
    <p class="small"><a href="#teacher">${tr('I am a teacher', 'أنا معلم/ة')}</a></p></div>`;
}

/* teacher page */
const ASSIGNABLE = () => [['placement', tr('Placement test', 'اختبار تحديد المستوى')], ['quiz/check', tr('Checkpoint test', 'اختبار متابعة')], ['dict', tr('Dictation', 'الإملاء')], ['review', tr('Review mistakes', 'مراجعة الأخطاء')], ['write/mock', tr('Writing mock', 'محاكاة الكتابة')], ['speak/mock', tr('Speaking mock', 'محاكاة المحادثة')]]
  .concat([...Array(mocks()).keys()].flatMap(i => [['paper/R/mock' + (i + 1), tr('Mock ', 'محاكاة ') + (i + 1) + ' · ' + tr('Reading', 'القراءة')], ['paper/L/mock' + (i + 1), tr('Mock ', 'محاكاة ') + (i + 1) + ' · ' + tr('Listening', 'الاستماع')]]))
  .concat(C.external.map(x => ['ext/' + x.id, x.title]))
  .concat(C.reading.map(x => ['paper/R/' + x.id, tr('Reading: ', 'قراءة: ') + x.title]), C.listening.map(x => ['paper/L/' + x.id, tr('Listening: ', 'استماع: ') + x.title]), C.writing.map(x => ['write/' + x.id, x.title]));
function vTeacher(code) {
  R.teach = R.teach || [];
  let h = header(tr('Teacher', 'المعلم/ة'));
  if (!code) {
    h += `<p>${tr('Create a class, give students the code, then see their levels and send them tasks.', 'أنشئ فصلًا، أعطِ الطلاب الكود، ثم تابع مستوياتهم وأرسل لهم مهام.')}</p><div class="stack">${R.teach.map(c => tile(`<h3>${esc(c.name)}</h3><small>${tr('Code: ', 'الكود: ')}<b>${esc(c.code)}</b></small>`, 'teacher/' + c.code)).join('')}</div>`;
    h += `<div class="tile" style="margin-top:12px"><h3>${tr('New class', 'فصل جديد')}</h3><input class="inp" id="cn" placeholder="${tr('Class name, e.g. Grade 12 IELTS', 'اسم الفصل، مثل: آيلتس الصف 12')}"><div class="actions">${btn(tr('Create', 'أنشئ'), async () => { const o = await api('class/create', { name: $('#cn').value.trim() }); if (o.error) { toast(o.error); return; } R.teach.push(o); save(); location.hash = 'teacher/' + o.code; }, 'primary')}</div></div>`;
    h += `<div class="tile" style="margin-top:12px"><h3>${tr('Open a class on this device', 'افتح فصلًا على هذا الجهاز')}</h3><p class="small mute">${tr('Use the class code and teacher key from the device where you created it.', 'استخدم كود الفصل ومفتاح المعلم من الجهاز الذي أنشأته عليه.')}</p><input class="inp" id="oc" placeholder="${tr('Class code', 'كود الفصل')}" style="margin-bottom:8px"><input class="inp" id="ok" placeholder="${tr('Teacher key', 'مفتاح المعلم')}"><div class="actions">${btn(tr('Open', 'افتح'), async () => { const c = $('#oc').value.trim().toUpperCase(), k = $('#ok').value.trim(); const o = await api('class/view', { code: c, key: k }); if (o.error) { toast(o.error); return; } R.teach.push({ code: c, key: k, name: o.name }); save(); location.hash = 'teacher/' + c; })}</div></div>`;
    return h;
  }
  const c = R.teach.find(x => x.code === code); if (!c) return vTeacher('');
  if (!V.cls && !V.loading) { V.loading = true; api('class/view', { code: c.code, key: c.key }).then(o => { V.cls = o; V.loading = false; rerender(); }); }
  h += `<h2>${esc(c.name)}</h2><div class="notice"><b>${tr('Class code for students: ', 'كود الفصل للطلاب: ')}<span class="en" style="font-size:22px">${esc(c.code)}</span></b><br><small>${tr('Teacher key (keep private): ', 'مفتاح المعلم (احتفظ به لنفسك): ')}<span class="en">${esc(c.key)}</span></small></div>`;
  if (!V.cls) return h + `<p>${tr('Loading...', 'جارٍ التحميل...')}</p>`;
  if (V.cls.error) return h + `<div class="notice error">${esc(V.cls.error)}</div>`;
  const st = V.cls.students || [], ago = t => { const d = Math.floor((Date.now() - t) / 864e5); return d <= 0 ? tr('today', 'اليوم') : tr(d + ' d ago', 'منذ ' + d + ' يوم'); };
  h += `<h3 style="margin-top:14px">${tr('Students', 'الطلاب')} (${st.length})</h3>`;
  if (!st.length) h += `<p class="small mute">${tr('No students yet. They join from Settings, Join a class.', 'لا يوجد طلاب بعد. ينضمون من الإعدادات، «انضم إلى فصل».')}</p>`;
  h += `<div class="stack">${st.map(x => { const d = x.data || {}, e = d.est || {}; return `<div class="tile"><div class="row"><h3>${esc(x.name)}</h3>${pill(d.placed ? b1(d.overall || 0) + ' → ' + b1(d.goal || 0) : tr('No placement yet', 'لم يختبر المستوى'), d.placed ? lvColor(d.overall || 0) : '')}</div>
    <small>${['reading', 'listening', 'writing', 'speaking'].map(k => skillName(k) + ' ' + (e[k] != null ? b1(e[k]) : '-')).join(' · ')}</small><br>
    <small class="mute">${tr('Active ', 'آخر نشاط ')}${ago(x.updated)} · ${tr('sessions this week: ', 'جلسات الأسبوع: ')}${d.week || 0} · ${tr('streak ', 'متتالية ')}${d.streak || 0}${d.exam ? ' · ' + tr('exam in ', 'الامتحان بعد ') + (d.exam - today()) + tr(' days', ' يوم') : ''}</small>
    ${(d.recent || []).length ? `<details><summary class="small">${tr('Recent sessions', 'الجلسات الأخيرة')}</summary>${d.recent.map(r => `<div class="small">${esc(r.t)}: ${r.s}/${r.n}</div>`).join('')}</details>` : ''}
    <div class="actions">${btn(tr('Remove', 'إزالة'), async () => { if (!confirm(tr('Remove this student?', 'إزالة هذا الطالب؟'))) return; await api('class/remove', { code: c.code, key: c.key, id: x.id }); V.cls = null; rerender(); }, 'danger')}</div></div>`; }).join('')}</div>`;
  h += `<h3 style="margin-top:14px">${tr('Tasks for the class', 'مهام الفصل')}</h3><div class="stack">${(V.cls.assignments || []).map(a => { const n = st.filter(x => ((x.data || {}).done || []).includes(a.id)).length; return `<div class="task"><span style="flex:1"><b>${esc(a.title)}</b><br><small class="mute">${tr(`done by ${n} of ${st.length}`, `أنجزها ${n} من ${st.length}`)}</small></span>${btn('✕', async () => { await api('class/unassign', { code: c.code, key: c.key, id: a.id }); V.cls = null; rerender(); })}</div>`; }).join('')}</div>`;
  h += `<div class="tile" style="margin-top:12px"><h3>${tr('Send a task', 'أرسل مهمة')}</h3><select class="inp" id="as">${ASSIGNABLE().map(([r, t]) => `<option value="${esc(r)}">${esc(t)}</option>`).join('')}</select><div class="actions">${btn(tr('Send to the class', 'أرسل للفصل'), async () => { const sel = $('#as'); const o = await api('class/assign', { code: c.code, key: c.key, route: sel.value, title: sel.options[sel.selectedIndex].text }); if (o.error) { toast(o.error); return; } toast(tr('Sent.', 'تم الإرسال.')); V.cls = null; rerender(); }, 'primary')}${btn(tr('Refresh', 'تحديث'), () => { V.cls = null; rerender(); })}</div></div>`;
  return h;
}

/* AI feedback */
function feedbackHTML(fb) {
  if (!fb) return '';
  if (fb.loading) return `<div class="notice">${tr('The AI is reading your answer...', 'الذكاء الاصطناعي يقرأ إجابتك...')}</div>`;
  if (fb.error) return `<div class="notice error">${esc(fb.error)}</div>`;
  return `<div class="tile c-fnd" style="margin-top:12px"><h3>${tr('AI feedback', 'ملاحظات الذكاء الاصطناعي')} · ${tr('about ', 'حوالي ')}${b1(fb.band || 0)}</h3>
    ${(fb.criteria || []).map(c => `<p><b class="en">${esc(c.name)}: ${b1(c.band || 0)}</b><br>${esc(c.comment)}</p>`).join('')}
    ${(fb.strengths || []).length ? `<b>${tr('Strengths', 'نقاط القوة')}</b><ul>${fb.strengths.map(x => `<li>${esc(x)}</li>`).join('')}</ul>` : ''}
    ${(fb.improve || []).length ? `<b>${tr('To improve', 'للتحسين')}</b><ul>${fb.improve.map(x => `<li>${esc(x)}</li>`).join('')}</ul>` : ''}
    ${(fb.corrections || []).length ? `<b>${tr('Corrections', 'تصحيحات')}</b>${fb.corrections.map(x => `<p class="small"><s class="en">${esc(x.wrong)}</s> → <b class="en">${esc(x.better)}</b><br>${esc(x.why)}</p>`).join('')}` : ''}
    <p class="small mute">${tr('A practice estimate by AI, not an official score. It can be wrong; ask your teacher when in doubt.', 'تقدير تدريبي من الذكاء الاصطناعي وليس درجة رسمية. قد يخطئ؛ اسأل معلمك عند الشك.')}</p></div>`;
}
async function askFeedback(kind, prompt, text, task, min, onDone) {
  V.fb = { loading: true }; rerender();
  const o = await api('feedback', { kind, prompt, text, task, min, lang: S.lang });
  V.fb = o; if (!o.error && o.band) onDone && onDone(+o.band); rerender();
}
