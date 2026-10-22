// Chợ đen: món hết hạn tự về túi (AuctionReturn, 22/10). Tạm default-expiration 60 giây (trả lại sau test).
// A treo kim cương rồi đứng online; B treo ngọc lục bảo rồi làm đầy túi (phải nhắc túi đầy, dọn túi thì món về); C treo vàng rồi thoát, vào lại.
const { start, sleep } = require('./h');
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const listen = t => { const m = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p).trim(); if (/Chợ đen/.test(v)) m.push(v); }); return m; };
(async () => {
  let a = await start('ThuCA'), b = await start('ThuCB'), c = await start('ThuCC');
  const R = x => a.rc.cmd(x);
  const cnt = async (n, it) => { const r = await R(`minecraft:clear ${n} minecraft:${it} 0`); const m = r.match(/Found (\d+)/); return m ? +m[1] : 0; };
  const ma = listen(a), mb = listen(b);
  for (const [t, n, it] of [[a, 'ThuCA', 'diamond'], [b, 'ThuCB', 'emerald'], [c, 'ThuCC', 'gold_ingot']]) {
    await R(`minecraft:clear ${n}`); await R(`minecraft:item replace entity ${n} weapon.mainhand with minecraft:${it} 1`); await sleep(500);
    t.log(n, 'treo:', (await t.say('/choden ban 100', 1500)).filter(x => /Chợ đen/.test(x)).join(' ').slice(0, 90), '| còn trong túi:', await cnt(n, it));
  }
  await R('minecraft:give ThuCB minecraft:dirt 2304'); b.log('ThuCB đầy túi, đất:', await cnt('ThuCB', 'dirt'));
  await c.end(); c.log('ThuCC thoát game');
  await sleep(150000);   // hết hạn 60 giây + zAuctionHouse quét mỗi 60 giây + AuctionReturn mỗi 60 giây
  a.log('A (online) kim cương:', await cnt('ThuCA', 'diamond'), '| tin:', ma.join(' / '));
  b.log('B (túi đầy) ngọc:', await cnt('ThuCB', 'emerald'), '| tin:', mb.join(' / '));
  await R('minecraft:clear ThuCB minecraft:dirt 64'); b.log('B dọn 1 ô, chờ lượt sau');
  await sleep(65000);
  b.log('B sau khi dọn: ngọc', await cnt('ThuCB', 'emerald'), '| tin:', mb.join(' / '));
  c = await start('ThuCC'); const mc = listen(c); await sleep(14000);
  c.log('C vào lại: vàng', await cnt('ThuCC', 'gold_ingot'), '| tin:', mc.join(' / '));
  for (const n of ['ThuCA', 'ThuCB', 'ThuCC']) await R(`minecraft:clear ${n}`);
  await a.end(); await b.end(); await c.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
