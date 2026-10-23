// Già Làng (cốt truyện chương 1): bot bấm chuột phải vào NPC, xem có mở hội thoại không.
const h = require('./h');
const N = process.env.BOTN || 'ThuGl';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  let t = await h.start(N);
  await t.rc.cmd(`minecraft:tp ${N} 12710.5 66 -2512.5`); await t.end(); await t.sleep(2500); t = await h.start(N);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await t.sleep(2000);
  t.log('## người/NPC thấy:', Object.values(t.bot.entities).filter(e => e.type === 'player').map(e => e.username + '@' + e.position.floored()).join(', '));
  const npc = Object.values(t.bot.entities).filter(e => e.type === 'player' && /Già|Gia/.test(e.username || '')).sort((a, b) => a.position.distanceTo(t.bot.entity.position) - b.position.distanceTo(t.bot.entity.position))[0];
  t.log('## NPC gần nhất:', npc && npc.username, npc && npc.position, '| bot ở', t.bot.entity.position);
  if (npc) { await t.bot.lookAt(npc.position.offset(0, 1.6, 0)); await t.use(npc); await t.sleep(3000); }
  t.log('## sau khi bấm:', msgs.join(' / ').slice(0, 900), '| cửa sổ:', t.bot.currentWindow ? t.bot.currentWindow.title : 'không');
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
