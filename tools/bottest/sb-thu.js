// Skyblock (31/10): bot vào lần đầu -> tự có đảo, tiền đầu, /is, /shop, /is level, /is challenges. Chạy: HOST=skyblock-1 BOTN=ThuSky ./run.sh sb-thu.js
const h = require('./h');
const N = process.env.BOTN || 'ThuSky';
const HOST = process.env.HOST || 'skyblock-1';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N, undefined, HOST); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await t.sleep(9000);
  const where = async () => (await R(`data get entity ${N} Dimension`)).replace(/.*data: /, '') + ' ' + (await R(`data get entity ${N} Pos`)).replace(/.*data: /, '');
  t.log('## sau 9 giây:', await where());
  t.log('tin:', msgs.slice(-5).join(' / ').slice(0, 300));
  const under = await R(`execute at ${N} run data get entity ${N} OnGround`);
  t.log('đứng đất:', under.slice(-12), '| tiền:', (await R(`bal ${N}`)).slice(0, 80));
  for (const c of ['/is', '/is level', '/is challenges', '/shop', '/is warps', '/is upgrade', '/bal']) {
    const n0 = msgs.length;
    const p = new Promise(res => { t.bot.once('windowOpen', w => res(w)); setTimeout(() => res(null), 3000); });
    t.bot.chat(c); const w = await p; await t.sleep(600);
    const title = w ? txt(w.title ?? '').slice(0, 60) || JSON.stringify(w.title).slice(0, 80) : '(không mở cửa sổ)';
    const items = w ? w.slots.slice(0, w.inventoryStart ?? 54).filter(Boolean).length : 0;
    t.log(`${c} -> ${title} | ${items} ô | tin: ${msgs.slice(n0).join(' / ').slice(0, 200)}`);
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
    await t.sleep(500);
  }
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
