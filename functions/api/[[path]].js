// Cloudflare Pages Function for /api/*: teacher classes (D1 database bound as DB) and AI feedback (Gemini, key in GEMINI_API_KEY).
// Setup: Pages project > Settings > Functions: bind a D1 database as DB; add the secret GEMINI_API_KEY. Tables are created on first use.

const H = { 'content-type': 'application/json', 'access-control-allow-origin': '*', 'access-control-allow-headers': 'content-type', 'access-control-allow-methods': 'POST, GET, OPTIONS' };
const J = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: H });
const rid = n => { const a = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; return [...crypto.getRandomValues(new Uint8Array(n))].map(x => a[x % a.length]).join(''); };
const clean = (s, n) => String(s ?? '').replace(/[\u0000-\u001f]/g, ' ').trim().slice(0, n);

let ready = false;
async function schema(db) {
  if (ready) return;
  await db.batch([
    db.prepare('CREATE TABLE IF NOT EXISTS classes(code TEXT PRIMARY KEY, name TEXT, tkey TEXT, created INTEGER)'),
    db.prepare('CREATE TABLE IF NOT EXISTS students(id TEXT PRIMARY KEY, code TEXT, name TEXT, token TEXT, data TEXT, updated INTEGER)'),
    db.prepare('CREATE TABLE IF NOT EXISTS assignments(id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, route TEXT, title TEXT, created INTEGER)'),
    db.prepare('CREATE TABLE IF NOT EXISTS usage(k TEXT PRIMARY KEY, n INTEGER)'),
    db.prepare('CREATE INDEX IF NOT EXISTS st_code ON students(code)'),
  ]);
  ready = true;
}

export async function onRequest({ request, env, params }) {
  if (request.method === 'OPTIONS') return new Response(null, { headers: H });
  const path = [].concat(params.path || []).join('/');
  if (path === 'health') return J({ ok: true, db: !!env.DB, ai: !!env.GEMINI_API_KEY });
  if (request.method !== 'POST') return J({ error: 'POST only' }, 405);
  let b; try { b = await request.json(); } catch (e) { return J({ error: 'Bad request' }, 400); }
  try {
    if (path === 'feedback') return await feedback(b, env, request);
    if (!env.DB) return J({ error: 'Classes are not set up yet on the server (no database).' }, 503);
    const db = env.DB; await schema(db);
    const teacher = async () => { const c = await db.prepare('SELECT * FROM classes WHERE code=?').bind(clean(b.code, 8).toUpperCase()).first(); return c && c.tkey === b.key ? c : null; };
    switch (path) {
      case 'class/create': {
        const name = clean(b.name, 60); if (!name) return J({ error: 'Class name needed' }, 400);
        const code = rid(6), key = rid(20);
        await db.prepare('INSERT INTO classes VALUES(?,?,?,?)').bind(code, name, key, Date.now()).run();
        return J({ code, key, name });
      }
      case 'class/view': {
        const c = await teacher(); if (!c) return J({ error: 'Wrong class code or teacher key' }, 403);
        const st = await db.prepare('SELECT id,name,data,updated FROM students WHERE code=? ORDER BY name').bind(c.code).all();
        const as = await db.prepare('SELECT id,route,title,created FROM assignments WHERE code=? ORDER BY id DESC').bind(c.code).all();
        return J({ name: c.name, code: c.code, students: st.results.map(s => ({ id: s.id, name: s.name, updated: s.updated, data: JSON.parse(s.data || '{}') })), assignments: as.results });
      }
      case 'class/assign': {
        const c = await teacher(); if (!c) return J({ error: 'Wrong class code or teacher key' }, 403);
        const route = clean(b.route, 80), title = clean(b.title, 120); if (!route) return J({ error: 'Nothing to assign' }, 400);
        await db.prepare('INSERT INTO assignments(code,route,title,created) VALUES(?,?,?,?)').bind(c.code, route, title, Date.now()).run();
        return J({ ok: true });
      }
      case 'class/unassign': {
        const c = await teacher(); if (!c) return J({ error: 'Wrong class code or teacher key' }, 403);
        await db.prepare('DELETE FROM assignments WHERE code=? AND id=?').bind(c.code, +b.id || 0).run(); return J({ ok: true });
      }
      case 'class/remove': {
        const c = await teacher(); if (!c) return J({ error: 'Wrong class code or teacher key' }, 403);
        await db.prepare('DELETE FROM students WHERE code=? AND id=?').bind(c.code, clean(b.id, 20)).run(); return J({ ok: true });
      }
      case 'join': {
        const c = await db.prepare('SELECT code,name FROM classes WHERE code=?').bind(clean(b.code, 8).toUpperCase()).first();
        if (!c) return J({ error: 'No class with this code' }, 404);
        const name = clean(b.name, 40); if (!name) return J({ error: 'Your name is needed' }, 400);
        const id = rid(10), token = rid(20);
        await db.prepare('INSERT INTO students VALUES(?,?,?,?,?,?)').bind(id, c.code, name, token, '{}', Date.now()).run();
        return J({ id, token, code: c.code, className: c.name });
      }
      case 'sync': {
        const s = await db.prepare('SELECT * FROM students WHERE id=?').bind(clean(b.id, 20)).first();
        if (!s || s.token !== b.token) return J({ error: 'Not in this class any more' }, 403);
        const data = JSON.stringify(b.data || {}).slice(0, 8000);
        await db.prepare('UPDATE students SET data=?, updated=? WHERE id=?').bind(data, Date.now(), s.id).run();
        const c = await db.prepare('SELECT name FROM classes WHERE code=?').bind(s.code).first();
        const as = await db.prepare('SELECT id,route,title,created FROM assignments WHERE code=? ORDER BY id DESC LIMIT 20').bind(s.code).all();
        return J({ className: c ? c.name : '', assignments: as.results });
      }
    }
    return J({ error: 'Unknown endpoint' }, 404);
  } catch (e) { return J({ error: 'Server error: ' + e.message }, 500); }
}

/* ---------------------------------------------------------------- AI feedback */
async function feedback(b, env, request) {
  if (!env.GEMINI_API_KEY) return J({ error: 'AI feedback is not set up yet on the server.' }, 503);
  const kind = b.kind === 'speaking' ? 'speaking' : 'writing';
  const text = clean(b.text, 7000).replace(/\s+/g, ' ');
  if (text.split(' ').length < 30) return J({ error: kind === 'writing' ? 'Write at least 30 words first.' : 'Speak at least 30 words first.' }, 400);
  if (env.DB) {
    await schema(env.DB);
    const ip = request.headers.get('cf-connecting-ip') || 'x', k = ip + ':' + new Date().toISOString().slice(0, 10);
    const r = await env.DB.prepare('INSERT INTO usage(k,n) VALUES(?,1) ON CONFLICT(k) DO UPDATE SET n=n+1 RETURNING n').bind(k).first();
    if (r && r.n > (+env.AI_DAILY_LIMIT || 15)) return J({ error: 'Daily limit reached. Try again tomorrow.' }, 429);
  }
  const lang = b.lang === 'ar' ? 'Arabic (simple Modern Standard Arabic)' : 'English';
  const crit = kind === 'writing'
    ? (b.task === 1 ? 'Task Achievement' : 'Task Response') + ', Coherence and Cohesion, Lexical Resource, Grammatical Range and Accuracy'
    : 'Fluency and Coherence, Lexical Resource, Grammatical Range and Accuracy (do not judge pronunciation: you only have an automatic transcript, which may contain recognition errors)';
  const prompt = `You are an experienced IELTS teacher giving practice feedback to a learner. Be honest, specific and encouraging. This is a practice estimate, not an official score.
Task type: IELTS ${kind === 'writing' ? 'Writing Task ' + (b.task || 2) : 'Speaking Part 2'}.
Question: ${clean(b.prompt, 1200)}
${kind === 'writing' ? 'Minimum words: ' + (b.min || 250) + '.' : ''}
Learner's ${kind === 'writing' ? 'answer' : 'spoken answer (automatic transcript)'}:
"""${text}"""
Return ONLY JSON with this shape:
{"band": number (overall estimate, 0-9 in steps of 0.5),
 "criteria": [{"name": string, "band": number, "comment": string}] (exactly these criteria: ${crit}),
 "strengths": [string, string],
 "improve": [string, string, string],
 "corrections": [{"wrong": string, "better": string, "why": string}] (up to 5 real errors copied from the text; "wrong" and "better" in English)}
Write every comment, strength, improvement and "why" in ${lang}.`;
  const models = [env.GEMINI_MODEL, 'gemini-flash-latest', 'gemini-2.5-flash'].filter(Boolean);
  let last = '';
  for (const m of models) {
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${m}:generateContent`, {
      method: 'POST', headers: { 'content-type': 'application/json', 'x-goog-api-key': env.GEMINI_API_KEY },
      body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text: prompt }] }], generationConfig: { responseMimeType: 'application/json', temperature: 0.3 } }),
    });
    if (r.status === 404) { last = m + ' not found'; continue; }
    const o = await r.json().catch(() => ({}));
    if (!r.ok) return J({ error: (o.error && o.error.message) || 'AI service error ' + r.status }, 502);
    const t = o.candidates && o.candidates[0] && o.candidates[0].content && o.candidates[0].content.parts.map(p => p.text || '').join('');
    try { const fb = JSON.parse(t); fb.model = m; return J(fb); } catch (e) { return J({ error: 'The AI answer could not be read. Try again.' }, 502); }
  }
  return J({ error: 'No AI model available (' + last + ').' }, 502);
}
