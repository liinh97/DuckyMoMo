// Thợ Rèn (23/10): menu /thoren, rèn Mũ +0, nâng +0->+1, hiệu ứng bộ khi mặc 4 món +3, nâng Áo +3->+4 khi đang mặc (đồ mới vào đúng ô).
const h = require('./h');
const N = process.env.BOTN || 'ThuRen';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await R(`minecraft:clear ${N}`); await R(`eco set ${N} 100000`);
  for (const [id, n] of [['xuquai', 40], ['loiquai', 6]]) await R(`dmm laydo ${id} ${n} ${N}`);
  for (const [m, n] of [['leather', 8], ['cobblestone', 32], ['iron_ingot', 64], ['copper_ingot', 32], ['iron_block', 10], ['gold_ingot', 16]]) await R(`minecraft:give ${N} minecraft:${m} ${n}`);
  await t.sleep(2000);
  const ids = () => t.bot.inventory.slots.map((it, i) => it && t.itemText(it).name ? `${i}:${t.itemText(it).name}` : null).filter(Boolean).filter(x => /Tân Thủ/.test(x));
  let w = await t.openMenu('/thoren');
  if (!w) { t.log('KHÔNG MỞ MENU', msgs.slice(-3)); await t.end(); process.exit(0); }
  const show = w => w.slots.slice(0, w.inventoryStart).forEach((it, i) => { if (!it || /pane$/.test(it.name)) return; const x = t.itemText(it); t.log(`  [${i}] ${it.name} | ${x.name} | ${x.lore.join(' ¦ ').slice(0, 230)}`); });
  t.log('## menu /thoren'); show(w);
  const slotOf = (w, re) => w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
  let n = msgs.length; await t.click(slotOf(w, /^Mũ Tân Thủ/)); await t.sleep(1500);
  t.log('## rèn Mũ +0 ->', msgs.slice(n).join(' / '), '| túi:', ids().join(', '));
  w = t.bot.currentWindow || await t.openMenu('/thoren');
  n = msgs.length; await t.click(slotOf(w, /^Mũ Tân Thủ/)); await t.sleep(1500);
  t.log('## nâng Mũ +0->+1 ->', msgs.slice(n).join(' / '), '| túi:', ids().join(', '));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); await t.sleep(500);
  // hiệu ứng bộ: mặc 4 món +3
  const hp0 = await R(`attribute ${N} minecraft:max_health get`);
  await R(`minecraft:clear ${N} minecraft:chainmail_helmet`); await R(`minecraft:clear ${N} minecraft:leather_helmet`);
  for (const id of ['tt_mu_3', 'giap_tan_thu_3', 'tt_quan_3', 'tt_giay_3']) await R(`dmm laydo ${id} 1 ${N}`);
  await t.sleep(1500);
  for (const [re, dest] of [[/^Mũ/, 'head'], [/^Áo/, 'torso'], [/^Quần/, 'legs'], [/^Giày/, 'feet']]) { const it = t.bot.inventory.items().find(i => re.test(t.itemText(i).name)); if (it) await t.bot.equip(it, dest).catch(e => t.log('equip lỗi', e.message)); await t.sleep(400); }
  await t.sleep(3000);
  t.log('## mặc 4 món +3: máu tối đa', hp0, '->', await R(`attribute ${N} minecraft:max_health get`));
  w = await t.openMenu('/thoren');
  const b = w.slots.slice(0, w.inventoryStart).find(it => it && /Hiệu ứng bộ/.test(t.itemText(it).name)); t.log('   ô hiệu ứng bộ:', b ? t.itemText(b).lore.join(' ¦ ') : '-');
  n = msgs.length; await t.click(slotOf(w, /^Áo Tân Thủ/)); await t.sleep(1500);
  t.log('## nâng Áo +3->+4 khi đang mặc ->', msgs.slice(n).join(' / '));
  t.log('   ô ngực giờ là:', (await R(`data get entity ${N} equipment.chest.components."minecraft:custom_data"`)).replace(/.*data: /, '').slice(0, 120));
  t.log('   túi còn món Tân Thủ:', ids().join(', '));
  t.log('   còn: xu', await R(`bal ${N}`), '| Lõi Quái', t.bot.inventory.items().filter(i => /Lõi Quái/.test(t.itemText(i).name)).reduce((a, i) => a + i.count, 0));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
