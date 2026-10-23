// Set mới 23/10: menu 13 set, rèn Thần Thoại +0 từ Huyền Thoại +3, U Linh (set mùa) chặn khi chưa tới mùa / rèn được khi mở,
// hiệu ứng bộ Nông Gia + Thợ Săn (thanh hành động).
const h = require('./h');
const N = process.env.BOTN || 'ThuMoi';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  const bars = []; t.bot._client.on('set_action_bar_text', p => bars.push(txt(p.text)));
  const sl = (w, re) => w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
  const forge = async (setRe, btnRe) => {
    let w = await t.openMenu('/thoren');
    await t.click(sl(w, setRe)); await t.sleep(1200); w = t.bot.currentWindow;
    const b = sl(w, btnRe); t.log('## nút', btnRe.source, ':', t.itemText(w.slots[b]).lore.join(' ¦ '));
    const n = msgs.length; await t.click(b); await t.sleep(1500);
    t.log('## rèn ->', msgs.slice(n).join(' / ') || '(không có tin)');
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); await t.sleep(500);
  };
  await R(`minecraft:clear ${N}`); await R(`eco set ${N} 200000`);
  let w = await t.openMenu('/thoren');
  t.log('## menu chính:', w.slots.slice(0, w.inventoryStart).filter(it => it && !/pane|barrier|anvil/.test(it.name)).map(it => t.itemText(it).name).join(' | '));
  const ul = sl(w, /^U Linh/); t.log('## U Linh:', t.itemText(w.slots[ul]).lore.join(' ¦ '));
  t.bot.closeWindow(w); await t.sleep(500);
  if (!process.env.LV) {
  // Thần Thoại
  for (const [id, n] of [['ht_ao_3', 1], ['mt_an_than', 3], ['nhat_nguyet', 1]]) await R(`dmm laydo ${id} ${n} ${N}`);
  await R(`minecraft:give ${N} minecraft:netherite_ingot 3`); await t.sleep(1500);
  await forge(/^Thần Thoại/, /Rèn Giáp Thần Thoại/);
  t.log('## túi:', t.bot.inventory.items().map(i => t.itemText(i).name).join(', '));
  // U Linh: chưa tới mùa
  await R(`minecraft:clear ${N}`); await R(`dmm laydo keo_hon 8 ${N}`); await R(`dmm laydo xuong_oan 2 ${N}`); await t.sleep(1000);
  await forge(/^U Linh/, /Rèn Giáp U Linh/);
  }
  // Nông Gia +4 đủ bộ, Thợ Săn +4 đủ bộ: hiệu ứng bộ
  for (const [s, at] of [['nn', 'nonggia'], ['ts', 'thosan']]) {
    await R(`minecraft:clear ${N}`); bars.length = 0;
    for (const p of ['mu', 'ao', 'quan', 'giay']) await R(`dmm laydo ${s}_${p}_4 1 ${N}`);
    await t.sleep(1000);
    for (const it of t.bot.inventory.items()) { try { await t.bot.equip(it, it.name.endsWith('helmet') ? 'head' : it.name.endsWith('chestplate') ? 'torso' : it.name.endsWith('leggings') ? 'legs' : 'feet'); } catch (e) { } await t.sleep(300); }
    await t.sleep(4000);
    t.log('## mặc:', [5, 6, 7, 8].map(i => t.bot.inventory.slots[i] ? t.itemText(t.bot.inventory.slots[i]).name : '-').join(', '));
    const mw = await t.openMenu('/thoren'); const it = mw.slots[sl(mw, at === 'nonggia' ? /^Nông Gia/ : /^Thợ Săn/)];
    t.log('## hiệu ứng', at, ':', t.itemText(it).lore.filter(l => /Đang có/.test(l)).join(' '), '| máu tối đa', await R(`attribute ${N} minecraft:max_health get`));
    t.bot.closeWindow(mw); await t.sleep(500);
  }
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
