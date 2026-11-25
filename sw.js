// Network first, so updates show at once; the cache keeps the app working offline.
const CACHE='khotwa-v4-1';
const ASSETS=['/','/index.html','/style.css','/fonts.css','/core.js','/ui.js','/shared/content.json','/manifest.webmanifest','/icon.svg','/icon-192.png','/icon-512.png','/icon-maskable.png','/fonts/bricolage-400.woff2','/fonts/bricolage-700.woff2','/fonts/bricolage-800.woff2','/fonts/sourceserif4-fe5374.woff2'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS)));self.skipWaiting();});
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==CACHE).map(x=>caches.delete(x)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{const u=new URL(e.request.url);if(e.request.method!=='GET'||u.pathname.endsWith('.apk')||u.origin!==location.origin)return;
e.respondWith(fetch(e.request).then(r=>{if(r.ok){const c=r.clone();caches.open(CACHE).then(x=>x.put(e.request,c));}return r;}).catch(async()=>(await caches.match(e.request,{ignoreSearch:true}))||(e.request.mode==='navigate'?caches.match('/index.html'):Response.error())));});
