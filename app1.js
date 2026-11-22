'use strict';
/* ===== Core: state, helpers, content indexes, grading, plan ===== */
const KEY='khotwa-ielts-v2', OLD='khotwa-ielts-v1';
const GOALS=[5,5.5,6,6.5,7];
const SK={foundation:{n:'Foundations',c:'c-fnd',e:'Foundation'},reading:{n:'Reading',c:'c-rea',e:'Reading'},listening:{n:'Listening',c:'c-lis',e:'Listening'},writing:{n:'Writing',c:'c-wri',e:'Writing'},speaking:{n:'Speaking',c:'c-spe',e:'Speaking'}};
const fresh=()=>({v:2,type:'Academic',goal:6.5,minutes:30,lessons:[],done:{},history:[],drafts:{},placement:null,quiz:null,run:null,vocab:{},stats:{sk:{},ty:{}},dates:[],rec:{}});
let S=fresh(), storageOK=true;
try{let x=JSON.parse(localStorage.getItem(KEY));if(x&&x.v===2)S={...fresh(),...x};else{const o=JSON.parse(localStorage.getItem(OLD));if(o&&o.version===1){S={...fresh(),type:o.type==='General Training'?'General Training':'Academic',goal:GOALS.includes(o.goal)?o.goal:6.5,minutes:[15,30,45,60].includes(o.minutes)?o.minutes:30,lessons:(o.lessons||[]).filter(x=>typeof x==='string'),drafts:o.drafts&&typeof o.drafts==='object'?o.drafts:{}};}}}catch(e){storageOK=false;}
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const norm=s=>String(s).toLowerCase().replace(/[^a-z0-9\u0600-\u06ff]/g,'');
const save=()=>{try{localStorage.setItem(KEY,JSON.stringify(S));}catch(e){if(storageOK)toast('Storage is unavailable. Download a backup before you close this page.');storageOK=false;}};
let tt;function toast(t){const e=$('#toast');e.textContent=t;e.classList.add('show');clearTimeout(tt);tt=setTimeout(()=>e.classList.remove('show'),3200);}
const buzz=n=>{try{navigator.vibrate&&navigator.vibrate(n||8)}catch(e){}};
const REDUCE=matchMedia('(prefers-reduced-motion: reduce)').matches;
function confetti(){if(REDUCE)return;const cols=['#d9f27a','#e5a935','#ee6c4d','#6aa9e6','#6b5bd2','#0f7a5a'];for(let i=0;i<36;i++){const d=document.createElement('i');d.className='confetti';d.style.cssText=`left:${Math.random()*100}vw;background:${cols[i%6]};animation-delay:${Math.random()*.4}s;border-radius:${i%3?'2px':'50%'}`;document.body.appendChild(d);setTimeout(()=>d.remove(),2400);}}
const day=()=>{const d=new Date();return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');};
function mark(){const d=day();if(!S.dates.includes(d)){S.dates.push(d);S.dates=S.dates.slice(-400);}}
function streak(){let n=0;const d=new Date();const has=x=>S.dates.includes(x.getFullYear()+'-'+String(x.getMonth()+1).padStart(2,'0')+'-'+String(x.getDate()).padStart(2,'0'));if(!has(d))d.setDate(d.getDate()-1);while(has(d)){n++;d.setDate(d.getDate()-1);}return n;}
const fmt=ms=>{const n=Math.max(0,Math.ceil(ms/1000));return String(Math.floor(n/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0');};
const words=x=>x.trim()?x.trim().split(/\s+/u).length:0;
const shuffle=a=>a.slice().sort(()=>Math.random()-.5);

/* ===== content indexes ===== */
const LESS=LESSONS.concat(KH.LESSONS2);
const READ=KH.READING, LIST=KH.LISTENING, SECS={};
function indexSecs(){for(const [arr,k] of [[READ,'R'],[LIST,'L']]){const off={};for(const s of arr){const m=s.mock;off[m]=off[m]||0;s.k=k;s.flat=[];let n=off[m];s.groups.forEach((g,gi)=>g.qs.forEach((q,qi)=>{n++;s.flat.push({q,g,gi,qi,n,key:s.id+'.'+n});}));off[m]=n;s.count=s.flat.length;SECS[s.id]=s;}}}
(function(){let i=0;for(const s of [...READ,...LIST])for(const g of s.groups)for(const q of g.qs){if(q.t==='mcq'){const t=(i++*5+2)%q.o.length;if(t!==q.a){const o=q.o;[o[t],o[q.a]]=[o[q.a],o[t]];q.a=t;}}}})();
indexSecs();
const WRIT=KH.WRITING, SPK=KH.SPEAKING, VOC=KH.VOCAB;
const TYLAB={mcq:'Multiple choice',tfng:'True/False/NG',yn:'Yes/No/NG',gap:'Completion',sel:'Matching'};
const BANDL=[[39,9],[37,8.5],[35,8],[32,7.5],[30,7],[26,6.5],[23,6],[18,5.5],[16,5],[13,4.5],[10,4]];
const BANDR=[[39,9],[37,8.5],[35,8],[33,7.5],[30,7],[27,6.5],[23,6],[19,5.5],[15,5],[13,4.5],[10,4]];
function band(score,total,k){if(total<30)return null;const s=Math.round(score/total*40);const t=k==='L'?BANDL:BANDR;for(const [m,b] of t)if(s>=m)return b;return 3.5;}
function correct(f,v){if(v===undefined||v===''||v===null)return false;const q=f.q;if(q.t==='gap')return q.a.some(a=>norm(a)===norm(v));return Number(v)===q.a;}
function addStat(sk,ty,c,t){const a=S.stats.sk[sk]=S.stats.sk[sk]||{c:0,t:0};a.c+=c;a.t+=t;const b=S.stats.ty[ty]=S.stats.ty[ty]||{c:0,t:0};b.c+=c;b.t+=t;}
const pct=o=>o&&o.t?Math.round(o.c/o.t*100):null;

/* ===== papers (reading / listening sections) ===== */
function startPaper(k,mock,secs,mode){
  if(S.run&&!confirm('There is an open test. Start a new one instead?'))return false;
  const mins=mode==='mock'?(k==='R'?60:35):(k==='R'?20*secs.length:0);
  S.run={k,mock,secs,tab:0,ans:{},mode,deadline:mins?Date.now()+mins*60000:null,started:Date.now(),played:{}};
  save();location.hash='paper';return true;}
function gradePaper(){
  const r=S.run;if(!r)return;let score=0,total=0;const types={},secScores={};
  for(const id of r.secs){const s=SECS[id];let sc=0;for(const f of s.flat){const ok=correct(f,r.ans[f.key]);if(ok){sc++;}total++;const t=types[f.q.t]=types[f.q.t]||[0,0];t[1]++;if(ok)t[0]++;}secScores[id]=sc;score+=sc;}
  const bd=r.mock&&r.mode==='mock'?band(score,total,r.k):null;
  const res={kind:'paper',k:r.k,mock:r.mock,mode:r.mode,secs:r.secs,ans:r.ans,date:new Date().toISOString(),score,total,band:bd,secScores,types,secsUsed:Math.round((Date.now()-r.started)/60000)};
  for(const [ty,[c,t]] of Object.entries(types))addStat(r.k==='R'?'reading':'listening',ty,c,t);
  S.history.push(res);S.history=S.history.slice(-80);S.run=null;mark();save();confetti();location.hash='results/'+(S.history.length-1);}
/* ===== quick quiz (legacy bank) ===== */
function startQuiz(kind){if(S.quiz&&!confirm('There is a saved test. Start a new one instead?'))return false;let qs;if(kind==='placement')qs=BANK.filter(q=>q.placement);else if(kind==='mix')qs=['foundation','reading','listening'].flatMap(s=>shuffle(BANK.filter(q=>q.skill===s)).slice(0,4));else qs=shuffle(BANK.filter(q=>q.skill===kind)).slice(0,12);
  S.quiz={kind,ids:qs.map(q=>q.id),answers:{},skipped:[],pos:0,deadline:Date.now()+(kind==='placement'?25:15)*60000,started:Date.now()};save();location.hash='quiz';return true;}
function finishQuiz(){const z=S.quiz;if(!z)return;const skills={};let total=0,c=0;for(const id of z.ids){const q=BANK[id];if(z.skipped.includes(id))continue;const k=skills[q.skill]=skills[q.skill]||{total:0,correct:0};k.total++;total++;const ok=z.answers[id]===q.answer;if(ok){c++;k.correct++;}addStat(q.skill,'mcq',ok?1:0,1);}
  const r={kind:'quiz',sub:z.kind,ids:z.ids,answers:z.answers,skipped:z.skipped,date:new Date().toISOString(),skills,total,correct:c};if(z.kind==='placement')S.placement=r;S.history.push(r);S.history=S.history.slice(-80);S.quiz=null;mark();save();confetti();location.hash='results/'+(S.history.length-1);}
function weak(){const p=S.placement;let cand=[];if(p)cand=Object.entries(p.skills).filter(([,v])=>v.total>0).map(([k,v])=>[k,v.correct/v.total]);for(const k of ['reading','listening']){const s=S.stats.sk[k];if(s&&s.t>=10)cand=cand.filter(x=>x[0]!==k).concat([[k,s.c/s.t]]);}
  if(!cand.length)return 'foundation';return cand.sort((a,b)=>a[1]-b[1])[0][0];}

/* ===== plan generator (28 days) ===== */
const lessonsBy=s=>LESS.filter(l=>l.skill===s);
function genPlan(){
  const w=weak(),g=S.goal,n=S.minutes<=15?1:S.minutes<=30?2:3,days=[];
  const rd=READ.map(x=>x.id),ls=LIST.map(x=>x.id),t1=WRIT.filter(x=>x.task===1&&(x.type===S.type)).map(x=>x.id),t2=WRIT.filter(x=>x.task===2).map(x=>x.id),sp=SPK.map(x=>x.id);
  let ri=0,li=0,w1=0,w2=0,si=0,vi=0,fl=0,ll=0,rl=0,wl=0,sl2=0;
  const T=(k,ref,label,skill,href,sub)=>({k,ref,label,skill,href,sub});
  const read=()=>{const id=rd[ri++%rd.length];return T('read',id,'Reading: '+SECS[id].title,'reading','#go/read:'+id,'Passage + questions · '+(SECS[id].count)+' questions');};
  const listen=()=>{const id=ls[li++%ls.length];return T('listen',id,'Listening: '+SECS[id].title,'listening','#go/listen:'+id,'Section '+SECS[id].sec+' · 10 questions');};
  const write=(two)=>{const id=two?t2[w2++%t2.length]:(t1[w1++%t1.length]||t2[0]);return T('write',id,'Writing: '+WRIT.find(x=>x.id===id).title,'writing','#writing/'+id,two?'250 words':'150 words');};
  const speak=()=>{const id=sp[si++%sp.length];return T('speak',id,'Speaking: '+SPK.find(x=>x.id===id).topic,'speaking','#speaking/'+id+'/p1','Record and review');};
  const lesson=(s)=>{const a=lessonsBy(s);const l=a[(s==='foundation'?fl++:s==='reading'?rl++:s==='listening'?ll++:s==='writing'?wl++:sl2++)%a.length];return T('lesson',l.id,'Lesson: '+l.title,s,'#lesson/'+l.id,l.time);};
  const vocab=()=>T('vocab',0,'Words: 10 cards','foundation','#words',(vi++,'Quick review'));
  const drill=(s)=>T('drill',s,'Quick drill: '+SK[s].n,s,'#go/drill:'+s,'12 questions');
  for(let d=1;d<=28;d++){
    const th=(d-1)%7;let t=[];
    if(d===7)t=[T('drill','mix','Mixed challenge','foundation','#go/drill:mix','12 questions'),lesson('foundation')];
    else if(d===14)t=[T('mock','R1','Mini reading test (Mock 1)','reading','#go/mock:R:1','3 passages · 60 minutes'),lesson('reading')];
    else if(d===21)t=[T('mock','L1','Mini listening test (Mock 1)','listening','#go/mock:L:1','4 sections · 40 questions'),lesson('listening')];
    else if(d===28)t=[T('mock','W1','Writing Mock 1','writing','#mockw/1','60 minutes'),T('mock','S1','Speaking Mock 1','speaking','#mockspk/1','3 parts')];
    else if(th===0)t=[read(),lesson('reading')];
    else if(th===1)t=[listen(),lesson('listening')];
    else if(th===2)t=[write(g>=6.5&&d>10),lesson('writing')];
    else if(th===3)t=[speak(),g<=5.5?lesson('foundation'):lesson('speaking')];
    else if(th===4)t=[read(),g>=6.5?write(true):drill('foundation')];
    else t=[listen(),vocab()];
    if(w!=='foundation'&&n>=3&&d%2===0&&!['mock'].includes(t[0].k)){t.push(w==='writing'||w==='speaking'?(w==='writing'?write(false):speak()):w==='reading'?read():listen());}
    else if(n>=3&&d%2===1)t.push(vocab());
    days.push(t.slice(0,Math.max(n,2)).map((x,i)=>({...x,id:'d'+d+'t'+i})));
  }
  return days;
}
function curDay(plan){for(let i=0;i<28;i++)if(plan[i].some(t=>!S.done[t.id]))return i+1;return 28;}
