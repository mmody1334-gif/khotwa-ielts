'use strict';
/* Khotwa IELTS web app: storage and engine. A direct port of the Android app (Engine.kt, Data.kt, Placement.kt),
   reading the same shared/content.json, so both apps behave the same. Backups move between them. */

/* ---------------------------------------------------------------- language */
const I18n = { ar: false };
const tr = (en, ar) => (I18n.ar ? ar : en);

/* ---------------------------------------------------------------- content */
const C = { external: [], bank: [], lessons: [], reading: [], listening: [], writing: [], speaking: [], vocab: [], placeR: null, placeL: null };
async function loadContent() {
  const r = await fetch('/shared/content.json', { cache: 'no-cache' }).catch(() => null);
  const d = r && r.ok ? await r.json() : await (await caches.match('/shared/content.json')).json();
  Object.assign(C, d);
  C.external = d.external || [];
  C.placeR = d.placement && d.placement.reading; C.placeL = d.placement && d.placement.listening;
  for (const s of C.listening.concat(C.placeL ? [C.placeL] : [])) s.sub = s.ctx || '';
  for (const s of C.reading.concat(C.placeR ? [C.placeR] : [])) s.sub = s.topic || '';
}
const section = id => C.reading.concat(C.listening).find(s => s.id === id);
const mocks = () => Math.max(1, ...C.reading.map(s => s.mock || 0));
const lessonTitle = l => (I18n.ar && l.title_ar) || l.title;
const lessonSub = l => (I18n.ar && l.sub_ar) || l.sub;
const lessonBody = l => (I18n.ar && l.body_ar) || l.body;
const lessonAction = l => (I18n.ar && l.action_ar) || l.action;

/* ---------------------------------------------------------------- storage (same JSON shape as the Android app) */
const KEY = 'khotwa-v3';
let R = {};
let storageOK = true;
try { R = JSON.parse(localStorage.getItem(KEY)) || {}; } catch (e) { R = {}; storageOK = false; }
(function migrate() {
  if (Object.keys(R).length) return;
  try {
    const o = JSON.parse(localStorage.getItem('khotwa-ielts-v2'));
    if (!o) return;
    R.type = o.type === 'General Training' ? 'General Training' : 'Academic';
    if (o.goal) R.goal = o.goal; if (o.minutes) R.minutes = o.minutes;
    if (o.drafts) R.draft = o.drafts;
  } catch (e) { }
})();
function save() { try { localStorage.setItem(KEY, JSON.stringify(R)); } catch (e) { storageOK = false; } }
const today = () => { const n = Date.now(); return Math.floor((n - new Date().getTimezoneOffset() * 60000) / 86400000); };
const S = {
  get type() { return R.type || 'Academic'; }, set type(v) { R.type = v; save(); },
  get goal() { return R.goal ?? 6.5; }, set goal(v) { R.goal = v; save(); },
  get minutes() { return R.minutes ?? 30; }, set minutes(v) { R.minutes = v; save(); },
  get start() { return R.start || 0; }, set start(v) { R.start = v; save(); },
  get lang() { return R.lang || 'en'; }, set lang(v) { R.lang = v; I18n.ar = v === 'ar'; save(); },
  get placed() { return !!R.placed; }, set placed(v) { R.placed = v; save(); },
  est(sk) { return R.est && R.est[sk] != null ? R.est[sk] : null; },
  setEst(sk, v) { this.putMap('est', sk, v); },
  restartProgram() { R.start = today(); delete R.dp; save(); },
  replanFuture() { const d = String(today() - this.start + 1); const keep = {}; if (R.dp && R.dp[d]) keep[d] = R.dp[d]; R.dp = keep; save(); },
  has(k, v) { return (R[k] || []).includes(v); },
  toggle(k, v) { const a = R[k] || []; const i = a.indexOf(v); if (i >= 0) a.splice(i, 1); else a.push(v); R[k] = a; save(); },
  add(k, v) { const a = R[k] || []; if (!a.includes(v)) { a.push(v); R[k] = a; save(); } },
  mapInt(k, key, d = 0) { const v = R[k] && R[k][key]; return typeof v === 'number' ? Math.trunc(v) : (v != null && !isNaN(+v) ? Math.trunc(+v) : d); },
  mapNum(k, key, d = 0) { const v = R[k] && R[k][key]; return v != null && !isNaN(+v) ? +v : d; },
  mapStr(k, key) { const v = R[k] && R[k][key]; return v == null ? '' : String(v); },
  putMap(k, key, v) { R[k] = R[k] || {}; R[k][key] = v; save(); },
  bump(k, key) { this.putMap(k, key, this.mapInt(k, key) + 1); },
  resetPlacement() { delete R.plc; save(); },
  stat(g, key) { const o = R[g] && R[g][key]; return [o ? o.c : 0, o ? o.t : 0]; },
  addStat(g, key, c, t) { R[g] = R[g] || {}; const o = R[g][key] || { c: 0, t: 0 }; o.c += c; o.t += t; R[g][key] = o; save(); },
  history() { return (R.hist || []).slice().reverse(); },
  addHistory(title, score, total, band) { const a = R.hist || []; a.push({ title, score, total, band: band ?? null, day: today() }); R.hist = a.slice(-80); this.addDay(); },
  addDay() { const t = String(today()); const a = R.days || []; if (!a.includes(t)) { a.push(t); R.days = a.slice(-500); } save(); },
  streak() { const s = new Set((R.days || []).map(Number)); let d = today(); if (!s.has(d)) d--; let n = 0; while (s.has(d)) { n++; d--; } return n; },
  export() { return JSON.stringify(R, null, 2); },
  import(t) { try { const o = JSON.parse(t); if (typeof o !== 'object' || !o) return false; R = o; save(); I18n.ar = this.lang === 'ar'; return true; } catch (e) { return false; } },
  reset() { const l = this.lang; R = { lang: l }; save(); },
};
I18n.ar = S.lang === 'ar';

/* ---------------------------------------------------------------- bands and grading */
const BANDL = [[39, 9], [37, 8.5], [35, 8], [32, 7.5], [30, 7], [26, 6.5], [23, 6], [18, 5.5], [16, 5], [13, 4.5], [10, 4], [6, 3.5]];
const BANDR = [[39, 9], [37, 8.5], [35, 8], [33, 7.5], [30, 7], [27, 6.5], [23, 6], [19, 5.5], [15, 5], [13, 4.5], [10, 4], [6, 3.5]];
function accBand(c, t, kind = 'R') { if (t <= 0) return 4; const s = Math.round(c / t * 40); for (const [m, b] of kind === 'L' ? BANDL : BANDR) if (s >= m) return b; return 3; }
const band = (score, total, kind) => total < 30 ? null : accBand(score, total, kind);
const half = x => Math.floor(x * 2 + 0.5) / 2;
const clamp = (x, a, b) => Math.min(b, Math.max(a, x));
function overall(v) { const a = v.reduce((x, y) => x + y, 0) / v.length; const f = Math.floor(a), r = a - f; return r < 0.25 ? f : r < 0.75 ? f + 0.5 : f + 1; }
const norm = s => String(s).toLowerCase().trim().replace(/\s+/g, ' ').replace(/\.+$/, '');
function isCorrect(q, ans) { if (ans == null || String(ans).trim() === '') return false; return q.t === 'gap' ? q.a.some(x => norm(x) === norm(ans)) : Number(ans) === q.a; }
function optionsOf(g, q) { return q.t === 'tfng' ? ['True', 'False', 'Not Given'] : q.t === 'yn' ? ['Yes', 'No', 'Not Given'] : q.t === 'sel' ? g.opts : q.o; }
const correctText = (g, q) => q.t === 'gap' ? (q.a[0] || '') : (optionsOf(g, q)[q.a] || '');

/* ---------------------------------------------------------------- skill levels */
const SKILLS = ['reading', 'listening', 'writing', 'speaking'];
function skillName(sk) { return ({ reading: tr('Reading', 'القراءة'), listening: tr('Listening', 'الاستماع'), writing: tr('Writing', 'الكتابة'), speaking: tr('Speaking', 'المحادثة'), foundation: tr('Grammar and vocabulary', 'القواعد والمفردات') })[sk] || sk; }
const level = sk => S.est(sk) ?? 5.0;
const currentOverall = () => overall(SKILLS.map(level));
function updateLevel(sk, c, t, kind = 'R') {
  if (t < 4) return;
  const w = t >= 30 ? 0.6 : t >= 10 ? 0.3 : 0.15;
  S.setEst(sk, clamp(half(level(sk) * (1 - w) + accBand(c, t, kind) * w), 3, 9));
}

/* ---------------------------------------------------------------- program */
const hoursForHalfBand = b => b < 5 ? 35 : b < 6 ? 45 : b < 7 ? 60 : 80;
function program() {
  const cur = currentOverall(), tgt = S.goal;
  let h = 0, b = cur; while (b < tgt - 0.01) { h += hoursForHalfBand(b); b += 0.5; }
  const perWeek = S.minutes / 60 * 6;
  const days = (h === 0 || !S.placed) ? 28 : clamp(Math.ceil(h / perWeek) * 7, 28, 364);
  return { current: cur, target: tgt, days, hours: h, weeks: Math.floor((days + 6) / 7), gap: Math.max(0, tgt - cur) };
}
function phaseOf(day, total) { const f = (day - 1) / Math.max(1, total); return f < 0.3 ? 'BUILD' : f < 0.8 ? 'PRACTICE' : 'EXAM'; }
const phaseName = p => ({ BUILD: tr('Build the basics', 'بناء الأساس'), PRACTICE: tr('Skill practice', 'تدريب المهارات'), EXAM: tr('Exam practice', 'محاكاة الامتحان') })[p];
const phaseInfo = p => ({
  BUILD: tr('Lessons, grammar, vocabulary and short drills on your weakest skills.', 'دروس وقواعد ومفردات وتدريبات قصيرة على أضعف مهاراتك.'),
  PRACTICE: tr('Full passages, listening sections, writing tasks and speaking sets.', 'قطع قراءة كاملة وأجزاء استماع ومهام كتابة ومحادثة.'),
  EXAM: tr('Timed mocks every week, review mistakes, keep your weak skills moving.', 'اختبارات محاكاة بالوقت كل أسبوع ومراجعة الأخطاء مع استمرار التركيز على نقاط الضعف.'),
})[p];

const T = (kind, id = '') => ({ kind, id });
const tcode = t => t.kind + '|' + t.id;
const tparse = c => { const p = c.split('|'); return p.length === 2 ? T(p[0], p[1]) : null; };
function troute(t) {
  return ({ read: 'paper/R/' + t.id, listen: 'paper/L/' + t.id, write: 'write/' + t.id, speak: 'speak/' + t.id, lesson: 'lesson/' + t.id, vocab: 'words',
    drill: 'quiz/drill/' + t.id, check: 'quiz/check', ext: 'ext/' + t.id, mockR: 'paper/R/mock' + t.id, mockL: 'paper/L/mock' + t.id, mockW: 'write/mock', mockS: 'speak/mock' })[t.kind] || 'home';
}
function tlabel(t) {
  const sec = section(t.id);
  switch (t.kind) {
    case 'read': return tr('Reading: ', 'قراءة: ') + (sec ? sec.title : t.id);
    case 'listen': return tr('Listening: ', 'استماع: ') + (sec ? sec.title : t.id);
    case 'write': { const w = C.writing.find(x => x.id === t.id); return w ? tr(`Writing Task ${w.task}: `, `كتابة المهمة ${w.task}: `) + w.title : t.id; }
    case 'speak': { const s = C.speaking.find(x => x.id === t.id); return tr('Speaking: ', 'محادثة: ') + (s ? s.topic : t.id); }
    case 'lesson': { const l = C.lessons.find(x => x.id === t.id); return tr('Lesson: ', 'درس: ') + (l ? lessonTitle(l) : t.id); }
    case 'vocab': return tr('Review 10 words', 'مراجعة 10 كلمات');
    case 'drill': return tr('Drill: ', 'تدريب: ') + skillName(t.id);
    case 'check': return tr('Checkpoint test (updates your plan)', 'اختبار متابعة (يحدّث خطتك)');
    case 'ext': { const x = C.external.find(e => e.id === t.id); return tr('Full test online: ', 'اختبار كامل أونلاين: ') + (x ? x.title : t.id); }
    case 'mockR': return tr(`Mock ${t.id}: Reading, 60 min`, `محاكاة ${t.id}: القراءة، 60 دقيقة`);
    case 'mockL': return tr(`Mock ${t.id}: Listening`, `محاكاة ${t.id}: الاستماع`);
    case 'mockW': return tr('Mock: Writing, 60 min', 'محاكاة: الكتابة، 60 دقيقة');
    case 'mockS': return tr('Mock: Speaking', 'محاكاة: المحادثة');
  }
  return t.kind;
}
function tskill(t) { return ({ ext: 'reading', read: 'reading', listen: 'listening', write: 'writing', speak: 'speaking', mockR: 'reading', mockL: 'listening', mockW: 'writing', mockS: 'speaking' })[t.kind] || (t.kind === 'drill' ? t.id : t.kind === 'lesson' ? ((C.lessons.find(x => x.id === t.id) || {}).skill || 'foundation') : 'foundation'); }

function weights() {
  const t = S.goal, m = {};
  for (const s of SKILLS) m[s] = 1 + 2.5 * Math.max(0, t - level(s));
  const f = level('foundation');
  m.foundation = f < 5.5 ? 1 + (5.5 - f) * 2.5 : f < t ? 0.5 : 0.2;
  return m;
}
const weakest = () => SKILLS.slice().sort((a, b) => (level(a) - (a === 'reading' || a === 'listening' ? 0.01 : 0)) - (level(b) - (b === 'reading' || b === 'listening' ? 0.01 : 0)))[0];

function Picker() {
  const counters = {};
  const n = k => { const v = counters[k] || 0; counters[k] = v + 1; return v; };
  const rotate = (pool, key, done) => { if (!pool.length) return null; const sorted = pool.map((v, i) => [v, i]).sort((a, b) => done(a[0]) - done(b[0]) || a[1] - b[1]).map(x => x[0]); return sorted[n(key) % sorted.length]; };
  const lesson = sk => { const l = rotate(C.lessons.filter(x => x.skill === sk), 'les-' + sk, x => S.has('ldone', x.id) ? 1 : 0); return l ? T('lesson', l.id) : null; };
  const sectionT = kind => { const s = rotate(kind === 'R' ? C.reading : C.listening, 'sec-' + kind, x => S.mapInt('secn', x.id)); return s ? T(kind === 'R' ? 'read' : 'listen', s.id) : null; };
  const writing = task => { const w = rotate(C.writing.filter(x => x.task === task && (task === 2 || x.type === S.type || x.type === 'Both')), 'w' + task, x => S.mapInt('wn', x.id)); return w ? T('write', w.id) : null; };
  const speaking = () => { const s = rotate(C.speaking, 'sp', x => S.mapInt('spn', x.id)); return s ? T('speak', s.id) : null; };
  return {
    task(sk, ph) {
      const k = n('t-' + sk);
      if (sk === 'reading') return ph === 'BUILD' ? (k % 2 === 0 ? lesson('reading') : T('drill', 'reading')) : sectionT('R');
      if (sk === 'listening') return ph === 'BUILD' ? (k % 2 === 0 ? lesson('listening') : T('drill', 'listening')) : sectionT('L');
      if (sk === 'writing') {
        if (ph === 'BUILD') return k % 2 === 0 ? lesson('writing') : writing(1);
        if (ph === 'PRACTICE') return level('writing') < 5.5 ? writing(k % 3 === 2 ? 2 : 1) : writing(k % 2 === 0 ? 2 : 1);
        return writing(k % 3 === 0 ? 1 : 2);
      }
      if (sk === 'speaking') return ph === 'BUILD' && k % 2 === 0 ? lesson('speaking') : speaking();
      return k % 2 === 0 ? T('drill', 'foundation') : (lesson('foundation') || T('vocab'));
    }
  };
}

function generatePlan(total) {
  const w = weights(), slots = clamp(Math.floor(S.minutes / 15), 1, 4), pk = Picker();
  const credit = {}; for (const k in w) credit[k] = 0;
  const sum = Object.values(w).reduce((a, b) => a + b, 0), M = mocks();
  let mockI = 0, extI = 0; const days = [];
  for (let d = 1; d <= total; d++) {
    const ph = phaseOf(d, total), out = [];
    if (d === total) { out.push(T('mockR', String(M)), T('mockL', String(M)), T('mockW'), T('mockS')); days.push(out); continue; }
    if (d % 7 === 0) {
      if (ph === 'EXAM') {
        // Exam stage: every other week a full online test (real Reading and Listening scores), otherwise a mock in the app
        if (C.external.length && extI % 2 === 0) { const pool = C.external.slice().sort((a, b) => (S.has('extdone', a.id) ? 1 : 0) - (S.has('extdone', b.id) ? 1 : 0)); out.push(T('ext', pool[Math.floor(extI / 2) % pool.length].id)); }
        else { const m = (Math.floor(mockI / 2) % M) + 1; out.push(T(mockI % 2 === 0 ? 'mockR' : 'mockL', String(m))); mockI++; }
        extI++;
      }
      else out.push(T('check'));
    }
    const used = new Set();
    while (out.length < slots && out.length < 4) {
      for (const k in credit) credit[k] += w[k];
      let pick = null; for (const k in credit) if (!used.has(k) && (pick === null || credit[k] > credit[pick])) pick = k;
      if (pick === null) break;
      credit[pick] -= sum; used.add(pick);
      const t = pk.task(pick, ph); if (t && !out.some(x => tcode(x) === tcode(t))) out.push(t);
      if (used.size >= Object.keys(credit).length) break;
    }
    if (slots >= 2 && d % 2 === 1 && !out.some(x => x.kind === 'vocab') && out.length < 4) out.push(T('vocab'));
    days.push(out);
  }
  return days;
}
let planCache = { key: '', plan: [] };
function getPlan() {
  const pr = program();
  const key = [S.type, S.goal, S.minutes, S.placed, pr.current, pr.days, S.start, JSON.stringify(R.est || {})].join('/');
  if (planCache.key !== key) planCache = { key, plan: generatePlan(pr.days) };
  return [pr, planCache.plan];
}
function programDay() { if (!S.start) S.start = today(); return Math.max(1, today() - S.start + 1); }
function tasksFor(day, plan) {
  const saved = S.mapStr('dp', String(day));
  if (saved) return saved.split(';').map(tparse).filter(Boolean);
  const t = plan[Math.min(day, plan.length) - 1] || [];
  if (day <= programDay()) S.putMap('dp', String(day), t.map(tcode).join(';'));
  return t;
}
const doneKey = (day, t) => 'd' + day + '-' + tcode(t);

function advice(sk, lv) {
  const A = {
    reading: lv < 5 ? [['Learn 10 academic words a day; most wrong answers come from unknown words.', 'احفظ 10 كلمات أكاديمية يوميًا؛ معظم الأخطاء سببها كلمات غير معروفة.'], ['Practise scanning: find names, dates and numbers in under a minute.', 'تدرّب على البحث السريع عن الأسماء والتواريخ والأرقام في أقل من دقيقة.'], ['Do one short passage a day without a timer, then check every answer.', 'حل قطعة قصيرة يوميًا بدون وقت، ثم راجع كل إجابة.']]
      : lv < 6 ? [['Master True / False / Not Given: Not Given means the text does not say.', 'أتقن True / False / Not Given: الـ Not Given تعني أن النص لا يذكر المعلومة.'], ['Read the questions first, then look for paraphrases, not the same words.', 'اقرأ الأسئلة أولًا ثم ابحث عن إعادة الصياغة وليس نفس الكلمات.'], ['Keep to 20 minutes per passage.', 'التزم بـ 20 دقيقة لكل قطعة.']]
      : lv < 7 ? [['Work on matching headings: find the main idea, not a detail.', 'ركّز على مطابقة العناوين: ابحث عن الفكرة الرئيسية وليس تفصيلة.'], ['Review every wrong answer and write why the right one is right.', 'راجع كل إجابة خاطئة واكتب لماذا الإجابة الصحيحة صحيحة.'], ['Do full timed mocks every week.', 'حل اختبار محاكاة كامل بالوقت كل أسبوع.']]
      : [['Watch for Not Given traps and opinion versus fact (Yes / No / Not Given).', 'انتبه لفخاخ Not Given والفرق بين الرأي والمعلومة.'], ['Read long academic articles outside the app to build speed.', 'اقرأ مقالات أكاديمية طويلة خارج التطبيق لزيادة السرعة.']],
    listening: lv < 5 ? [['Practise spelling, numbers and dates; Section 1 depends on them.', 'تدرّب على التهجئة والأرقام والتواريخ؛ الجزء الأول يعتمد عليها.'], ['Replay practice sections and read the transcript after answering.', 'أعد تشغيل أجزاء التدريب واقرأ النص بعد الإجابة.']]
      : lv < 6 ? [['Predict the type of answer (number, noun, place) before the audio starts.', 'توقّع نوع الإجابة (رقم، اسم، مكان) قبل بدء التسجيل.'], ['Listen for corrections: speakers often change their minds.', 'انتبه للتصحيحات: المتحدث كثيرًا ما يغيّر رأيه.']]
      : lv < 7 ? [['Work on Sections 3 and 4: discussions and lectures.', 'ركّز على الجزأين 3 و4: النقاشات والمحاضرات.'], ['Do mock listening with the audio once only.', 'حل محاكاة الاستماع مع تشغيل التسجيل مرة واحدة فقط.']]
      : [['Listen to English lectures and podcasts daily and take short notes.', 'استمع يوميًا لمحاضرات وبودكاست بالإنجليزية ودوّن ملاحظات قصيرة.']],
    writing: lv < 5 ? [['Write simple, correct sentences first; then join them with because, but, although.', 'اكتب جملًا بسيطة صحيحة أولًا، ثم اربطها بـ because و but و although.'], ['Learn the four-paragraph shape for Task 2.', 'تعلّم شكل الأربع فقرات لمهمة 2.']]
      : lv < 6 ? [['Reach the word count every time: 150 for Task 1, 250 for Task 2.', 'احرص على عدد الكلمات دائمًا: 150 للمهمة 1 و250 للمهمة 2.'], ['In Task 1, write a clear overview of the main trends.', 'في المهمة 1 اكتب نظرة عامة واضحة لأهم الاتجاهات.'], ['Answer every part of the Task 2 question.', 'أجب عن كل أجزاء سؤال المهمة 2.']]
      : lv < 7 ? [['Develop each idea: point, explanation, example.', 'طوّر كل فكرة: النقطة، الشرح، المثال.'], ['Vary vocabulary and use complex sentences accurately.', 'نوّع المفردات واستخدم جملًا مركّبة بدقة.'], ['Compare your essay with the model and ask a teacher for feedback.', 'قارن مقالك بالنموذج واطلب رأي معلم.']]
      : [['Aim for precise, less common words and a clear position throughout.', 'استهدف كلمات دقيقة وأقل شيوعًا وموقفًا واضحًا طوال المقال.']],
    speaking: lv < 5 ? [['Answer in full sentences and add one reason: I like it because...', 'أجب بجمل كاملة وأضف سببًا: I like it because...'], ['Record yourself every day and replay it.', 'سجّل صوتك يوميًا واستمع له.']]
      : lv < 6 ? [['Speak for the full 2 minutes in Part 2; use the 1 minute to make notes.', 'تكلّم دقيقتين كاملتين في الجزء 2؛ استغل دقيقة التحضير لكتابة ملاحظات.'], ['Reduce long pauses; use phrases like Let me think.', 'قلّل التوقف الطويل؛ استخدم عبارات مثل Let me think.']]
      : lv < 7 ? [['In Part 3, give reasons, examples and the other side.', 'في الجزء 3 قدّم أسبابًا وأمثلة والرأي الآخر.'], ['Use a range of tenses and some idiomatic language naturally.', 'استخدم أزمنة متنوعة وبعض التعبيرات بشكل طبيعي.']]
      : [['Practise with a partner or teacher who can comment on pronunciation.', 'تدرّب مع زميل أو معلم يعلّق على النطق.']],
  };
  return (A[sk] || []).map(([e, a]) => tr(e, a));
}

function shuffle(a) { const b = a.slice(); for (let i = b.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [b[i], b[j]] = [b[j], b[i]]; } return b; }
function quizQuestions(kind, arg) {
  const b = C.bank, pick = (sk, n) => shuffle(b.filter(x => x.skill === sk && !x.placement)).slice(0, n);
  if (kind === 'drill') return shuffle(b.filter(x => x.skill === arg)).slice(0, 10);
  if (kind === 'check') return shuffle(pick('foundation', 4).concat(pick('reading', 6), pick('listening', 6)));
  return shuffle(['foundation', 'reading', 'listening'].flatMap(sk => shuffle(b.filter(x => x.skill === sk)).slice(0, 4)));
}

/* ---------------------------------------------------------------- writing and speaking estimates */
const LINKERS = ['however', 'although', 'whereas', 'while', 'therefore', 'moreover', 'furthermore', 'in addition', 'for example', 'for instance', 'consequently', 'in contrast', 'on the other hand', 'overall', 'in conclusion', 'because', 'despite', 'as a result', 'nevertheless', 'firstly', 'finally', 'such as', 'unless'];
function textStats(text) {
  const w = (text.match(/[A-Za-z']+/g) || []).map(x => x.toLowerCase());
  const sents = text.split(/[.!?]+/).filter(x => x.trim()).length;
  const paras = text.split(/\n\s*\n/).filter(x => x.trim()).length;
  const low = ' ' + text.toLowerCase() + ' ';
  const links = LINKERS.filter(x => low.includes(' ' + x + ' ') || low.includes(' ' + x + ',')).length;
  const head = w.slice(0, 120);
  return { words: w.length, sentences: sents, paras, links, variety: head.length ? new Set(head).size / head.length : 0, longRatio: w.length ? w.filter(x => x.length >= 7).length / w.length : 0, linkList: LINKERS.filter(x => low.includes(' ' + x + ' ') || low.includes(' ' + x + ',')) };
}
function writingLevel(text, minWords, grammar, selfYes) {
  const s = textStats(text);
  if (s.words < 40) return Math.max(3, half(Math.min(4, grammar)));
  let h = 4;
  h += s.words >= minWords ? 0.5 : s.words < minWords * 0.7 ? -0.5 : 0;
  const avg = s.words / Math.max(1, s.sentences);
  if (avg >= 11 && avg <= 26) h += 0.5;
  if (s.variety > 0.55) h += 0.5; if (s.variety > 0.65) h += 0.5;
  if (s.longRatio > 0.14) h += 0.5; if (s.longRatio > 0.20) h += 0.5;
  if (s.links >= 3) h += 0.5; if (s.links >= 6) h += 0.5;
  if (s.paras >= 3) h += 0.5;
  const blend = 0.45 * h + 0.55 * grammar + (selfYes - 1.5) * 0.25;
  return half(clamp(blend, 3.5, Math.min(8.5, grammar + 1)));
}
function speakingLevel(transcript, secs, grammar, selfLen, selfPause) {
  const s = textStats(transcript), self = 4.5 + selfLen * 0.5 + selfPause * 0.5;
  let fluency = null;
  if (s.words >= 20 && secs >= 20) {
    const wpm = s.words / (secs / 60);
    let f = wpm < 50 ? 4 : wpm < 70 ? 4.5 : wpm < 90 ? 5 : wpm < 105 ? 5.5 : wpm < 120 ? 6 : wpm < 135 ? 6.5 : wpm < 150 ? 7 : 7.5;
    if (s.variety > 0.6) f += 0.5; if (s.longRatio > 0.12) f += 0.5; if (secs < 60) f -= 0.5;
    fluency = f;
  }
  const blend = fluency != null ? 0.4 * fluency + 0.35 * grammar + 0.25 * self : 0.5 * grammar + 0.5 * self - 0.25;
  return half(clamp(blend, 3.5, Math.min(8.5, grammar + 1)));
}
function applyFullPlacement() {
  const P = k => S.mapInt('plc', k);
  const f = accBand(P('fc'), P('ft')), r = accBand(P('rc'), P('rt'), 'R'), l = accBand(P('lc'), P('lt'), 'L');
  const c = x => clamp(half(x), 3, 9);
  S.setEst('foundation', c(f)); S.setEst('reading', c(r * 0.8 + f * 0.2)); S.setEst('listening', c(l * 0.85 + f * 0.15));
  S.setEst('writing', c(S.mapNum('plc', 'w', f - 0.5))); S.setEst('speaking', c(S.mapNum('plc', 's', f - 0.5)));
  S.addStat('pl', 'foundation', P('fc'), P('ft')); S.addStat('pl', 'reading', P('rc'), P('rt')); S.addStat('pl', 'listening', P('lc'), P('lt'));
  S.addHistory(tr('Placement test', 'اختبار تحديد المستوى'), P('fc') + P('rc') + P('lc'), P('ft') + P('rt') + P('lt'), null);
  S.placed = true; S.restartProgram();
}

/* ---------------------------------------------------------------- full tests taken on ieltsonlinetests.com */
/** Saves Reading and Listening scores (out of 40) from a full online test and moves the levels strongly, as for a full mock. */
function recordExternal(id, r, l) {
  const x = C.external.find(e => e.id === id); if (!x) return;
  if (r != null) { S.addStat('sk', 'reading', r, 40); updateLevel('reading', r, 40, 'R'); }
  if (l != null) { S.addStat('sk', 'listening', l, 40); updateLevel('listening', l, 40, 'L'); }
  S.putMap('ext', id, (r ?? '') + '/' + (l ?? '')); S.add('extdone', id);
  S.addHistory(x.title, (r || 0) + (l || 0), (r != null ? 40 : 0) + (l != null ? 40 : 0), null);
  S.replanFuture();
}
