// m13 /tphere: ThuR2 (quyền tạm essentials.tphere) kéo ThuR3 đứng xa 30 khối về chỗ mình.
const h = require('./h');
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  let b = await h.start('ThuR2'); b = await h.moveTo(b, 13200, -2900);
  let c = await h.start('ThuR3'); c = await h.moveTo(c, 13230, -2900);
  const R = x => b.rc.cmd(x);
  const m = []; b.bot._client.on('system_chat', p => { const v = txt(p.content ?? p).trim(); if (v && !/^❤/.test(v)) m.push(v); });
  await R('lp user ThuR2 permission settemp essentials.tphere true 3m'); await b.sleep(6000);
  const P = async n => (await R(`data get entity ${n} Pos`)).replace(/.*data: /, '').replace(/d/g, '');
  b.log('trước: ThuR2', await P('ThuR2'), '| ThuR3', await P('ThuR3'));
  b.bot.chat('/tphere ThuR3'); await b.sleep(8000);
  b.log('sau 8 giây: ThuR3', await P('ThuR3'), '| tin ThuR2:', m.join(' / ').slice(0, 300));
  await R('lp user ThuR2 permission unset essentials.tphere');
  await b.end(); await c.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
