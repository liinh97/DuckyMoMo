// 23/10: (1) /dmm laydo <mã> [số] [người]; (2) menu /hoatdong; (3) dungeon chuỗi: bot vượt Đỉnh Vách Đá (hạ 3 mục tiêu bằng
// /damage ... by bot) -> phải rơi Chìa khoá Hang Thánh Địa + quà lần đầu 750 xu + "Lần đầu vượt".
const h = require('./h');
const N = process.env.BOTN || 'ThuDg';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  const count = id => t.bot.inventory.items().filter(i => t.itemText(i).name && JSON.stringify(i.components || []).includes(id) || /Test/.test(t.itemText(i).name)).reduce((a, i) => a + i.count, 0);
  if (!process.env.CHI_DUNGEON) {
    await R(`lp user ${N} parent add quanly`); await t.sleep(3000); await R(`minecraft:clear ${N}`); await t.sleep(500);
    for (const c of ['/dmm laydo kiem_test 2', `/dmm laydo kiem_test 1 ${N}`, `/dmm laydo kiem_test ${N} 3`, '/dmm laydo kiem_test 2 KhongCoAi']) {
      const n = msgs.length; const before = t.bot.inventory.items().reduce((a, i) => a + i.count, 0);
      t.bot.chat(c); await t.sleep(1800);
      const after = t.bot.inventory.items().reduce((a, i) => a + i.count, 0);
      t.log(`## ${c}: +${after - before} món | ${msgs.slice(n).join(' / ').slice(0, 160)}`);
    }
    await R(`lp user ${N} parent remove quanly`); await R(`minecraft:clear ${N}`); await t.sleep(1000);
    const w = await t.openMenu('/hoatdong');
    if (!w) t.log('## /hoatdong: KHÔNG MỞ MENU | ' + msgs.slice(-2).join(' / '));
    else { t.log('## /hoatdong menu'); w.slots.slice(0, w.inventoryStart).forEach((it, i) => { if (!it || /pane$/.test(it.name)) return; const x = t.itemText(it); t.log(`  [${i}] ${it.name} x${it.count} | ${x.name} | ${x.lore.join(' ¦ ').slice(0, 170)}`); }); t.bot.closeWindow(w); }
    await t.sleep(800);
  }
  // (3) dungeon
  await R(`effect give ${N} minecraft:resistance 900 4 true`); await R(`effect give ${N} minecraft:regeneration 900 4 true`);
  const bal0 = await R(`bal ${N}`);
  const dim = async () => (await R(`data get entity ${N} Dimension`)).replace(/.*data: /, '').replace(/"/g, '');
  await R(`execute in minecraft:overworld run minecraft:tp ${N} 12699 80 -2523`); for (let i = 0; i < 15 && /em_/.test(await dim()); i++) await t.sleep(1000); await t.sleep(3000);
  const w2 = await t.openMenu('/dungeon vao the_climb_dungeon');
  if (w2) { await t.click(2); await t.sleep(800); if (t.bot.currentWindow) await t.click(2); }
  for (let i = 0; i < 25 && !/em_/.test(await dim()); i++) await t.sleep(1000);
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  const D = await dim(); t.log('## dungeon: vào', D, '|', bal0);
  if (!/em_/.test(D)) { t.log('KHÔNG VÀO ĐƯỢC | ' + msgs.slice(-4).join(' / ')); await t.end(); process.exit(0); }
  { const n = msgs.length; t.bot.chat('/em start'); await t.sleep(15000); t.log('em start ->', msgs.slice(n).join(' / ').slice(0, 300)); }
  for (const [x1, z1] of [[160, 0], [400, 0], [160, 240], [400, 240]]) t.log('forceload:', await R(`execute in ${D} run forceload add ${x1} ${z1} ${x1 + 239} ${z1 + 239}`));
  await t.sleep(10000);
  for (let round = 0; round < 15; round++) {
    const out = [];
    for (const ty of ['skeleton', 'wither_skeleton', 'stray', 'zombie', 'husk']) {
      const r = process.env.KIEU === 'kill' ? await R(`execute in ${D} run minecraft:kill @e[type=minecraft:${ty}]`) : await R(`execute in ${D} as @e[type=minecraft:${ty}] run damage @s 100000 minecraft:player_attack by ${N}`);
      const m = r.match(/Applied|Killed/g); if (m) out.push(`${ty}×${m.length}`); else if (r) out.push(`${ty}: ${r.slice(0, 50)}`);
    }
    await t.sleep(5000);
    const hit = msgs.filter(m => /Chìa khoá|Lần đầu vượt|ĐẦU TIÊN|Quà lần đầu/.test(m));
    t.log(`vòng ${round}: bot ở ${await dim()} | ${out.join(', ')}`, hit.length ? '=> ' + hit.join(' / ').slice(0, 300) : '');
    if (hit.some(m => /Chìa khoá/.test(m))) break;
  }
  await t.sleep(2000);
  for (const it of t.bot.inventory.items()) t.log('  túi:', it.name, '|', t.itemText(it).name, '|', t.itemText(it).lore.join(' ¦ ').slice(0, 200));
  t.log('tiền sau:', await R(`bal ${N}`));
  await R(`execute in ${D} run forceload remove all`);
  await R(`execute in minecraft:overworld run minecraft:tp ${N} 12699 80 -2523`); await t.sleep(2000);
  await R(`effect clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
