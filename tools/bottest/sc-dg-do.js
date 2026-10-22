// Dò: /dungeon vao the_climb_dungeon -> bấm ô 2 -> in cửa sổ / tin nhắn kế tiếp; in mô tả đầy đủ 2 que thử.
const h = require('./h');
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start('ThuR1'); const R = c => t.rc.cmd(c);
  const m = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p).trim(); if (v && !/^❤|^▏/.test(v)) m.push(v); });
  await R('minecraft:clear ThuR1'); await R('dmm laydo thu_sc_que ThuR1 1'); await R('dmm laydo thu_sc_luot ThuR1 1'); await t.sleep(4000);
  for (const it of t.bot.inventory.items()) t.log('que:', JSON.stringify(t.itemText(it)));
  await R('minecraft:clear ThuR1');
  const w = await t.openMenu('/dungeon vao the_climb_dungeon');
  t.log('menu 1:', w && w.slots.slice(0, w.inventoryStart).map((it, i) => it && `${i}:${t.itemText(it).name} [${t.itemText(it).lore.join(' / ').slice(0, 150)}]`).filter(Boolean).join(' | '));
  const w2 = await t.click(2); await t.sleep(3000);
  t.log('sau bấm:', t.bot.currentWindow ? t.bot.currentWindow.slots.slice(0, t.bot.currentWindow.inventoryStart).map((it, i) => it && `${i}:${t.itemText(it).name}`).filter(Boolean).join(' | ').slice(0, 400) : '(không còn cửa sổ)');
  t.log('tin:', m.join(' / ').slice(0, 500));
  t.log('thế giới:', await R('data get entity ThuR1 Dimension'));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
