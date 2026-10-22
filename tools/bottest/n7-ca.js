// n7 EvenMoreFish: (1) bán cá ở kinh đô: giá = dài (cm) × worth-multiplier của độ hiếm (Cài đặt emf_*_gia);
// (2) thi câu: admin mở 2 phút LARGEST_FISH, ThuN7 câu thật bằng bot.fish() ở hồ nước tự đặt (đất hoang), ThuN7b online đứng xem
// (minimum-players 2). Hết giờ: hạng 1 nhận thưởng theo competitions/thicau.yml (xu + Bùa bắt pet).
const { start, moveTo } = require('./h');
const A = 'ThuN7', B = 'ThuN7b';
(async () => {
  let a = await start(A, '10.9.0.81');
  const R = c => a.rc.cmd(c);
  const bal = async n => +((await R(`bal ${n}`)).replace(/.*:\s*/, '').replace(/[^\d.]/g, '') || 0);
  await R(`gamemode survival ${A}`); await R(`minecraft:clear ${A}`); await R(`lp user ${A} permission set grim.exempt true`);
  // ---- (1) bán cá ở spawn (kinh đô)
  if (!process.argv.includes('thi')) {
  a = await moveTo(a, 12699, -2523, 'minecraft:overworld', '10.9.0.81');
  for (const [rar, fish] of [['Common', 'CaRoDong'], ['Common', 'CaChep']]) {
    const r = await R(`emf admin fish ${rar} ${fish || ''} 1 ${A}`.replace('  ', ' ')); await a.sleep(800);
    a.log(`  phát cá ${rar}:`, r.trim().slice(0, 100));
  }
  const fishes = a.bot.inventory.items().map(it => ({ n: a.itemText(it).name, len: +((a.itemText(it).lore.join(' ').match(/Dài ([\d.,]+)/) || [0, 0])[1].replace(',', '.')) })).filter(f => f.len);
  a.log('  cá trong túi:', JSON.stringify(fishes));
  const b0 = await bal(A);
  const c0 = a.chat.length; const w = await a.openMenu('/emf shop');
  a.log('  tin sau /emf shop:', a.chat.slice(c0).join(' | ').slice(0, 300));
  for (const c of ['/emf help', '/emf top', '/emf journal', '/emf sellall']) a.log(`  ${c}:`, (await a.say(c, 1500)).join(' | ').slice(0, 300));
  a.log('  /emf shop:', w ? (w.title && JSON.stringify(w.title).slice(0, 80)) + ' | ' + w.slots.slice(0, w.inventoryStart).filter(Boolean).map(x => a.itemText(x).name).join(', ').slice(0, 200) : 'không mở');
  const sell = w && w.slots.slice(0, w.inventoryStart).findIndex(x => x && /bán|Bán|sell/i.test(a.itemText(x).name));
  if (w) {
    // bỏ cá vào ô shop (shift-click từ túi) rồi bấm nút bán
    for (let i = w.inventoryStart; i < w.slots.length; i++) if (w.slots[i] && /Dài/.test(a.itemText(w.slots[i]).lore.join(' '))) await a.click(i, true);
    await a.sleep(600);
    const btn = w.slots.slice(0, w.inventoryStart).findIndex(x => x && /bán|Bán|sell/i.test(a.itemText(x).name));
    a.log('  nút bán:', btn, btn >= 0 ? a.itemText(w.slots[btn]).name + ' | ' + a.itemText(w.slots[btn]).lore.join(' / ').slice(0, 150) : '');
    if (btn >= 0) { await a.click(btn); await a.sleep(1200); const w2 = a.bot.currentWindow; const c2 = w2 && w2.slots.slice(0, w2.inventoryStart).findIndex(x => x && /xác nhận|Xác nhận|confirm/i.test(a.itemText(x).name)); if (c2 >= 0) { await a.click(c2); await a.sleep(1200); } }
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  }
  const exp = fishes.map(f => f.len * (/Thường/.test(f.n) || fishes.indexOf(f) === 0 ? 0.06 : NaN));
  a.log(`n7 bán cá: tiền ${b0} -> ${await bal(A)} | cá: ${fishes.map(f => f.n + ' ' + f.len + 'cm').join(', ')} | còn cá trong túi: ${a.bot.inventory.items().filter(it => /Dài/.test(a.itemText(it).lore.join(' '))).length}`);
  if (process.argv.includes('ban')) { await a.end(); process.exit(0); }
  }
  // ---- (2) thi câu
  a = await moveTo(a, 13200, -2900, 'minecraft:overworld', '10.9.0.81');
  const p = a.bot.entity.position.floored();
  await R(`fill ${p.x + 2} ${p.y - 3} ${p.z - 2} ${p.x + 6} ${p.y - 1} ${p.z + 2} minecraft:water`);
  await R(`minecraft:clear ${A}`); await R(`minecraft:item replace entity ${A} weapon.mainhand with minecraft:fishing_rod`); a.bot.setQuickBarSlot(0);
  let b = await start(B, '10.9.0.82'); await b.sleep(3000);
  const a0 = await bal(A), bb0 = await bal(B);
  a.log('  mở thi câu:', (await R('emf admin competition start thiCauCuoiTuan ' + (process.argv[3] || '2'))).trim().slice(0, 150));
  await a.lookAt?.(null);
  await a.bot.lookAt(p.offset(4, -1, 0), true);
  const T0 = Date.now(); a.bot.on('messagestr', m => { if (/thi câu|câu được/i.test(m)) console.log(`  [${((Date.now() - T0) / 1000).toFixed(0)}s] ${m.slice(0, 160)}`); });
  const end = Date.now() + 115000; let caught = 0;
  while (Date.now() < end) {
    try { await Promise.race([a.bot.fish(), new Promise((_, rej) => setTimeout(() => rej(new Error('lâu')), 40000))]); caught++; } catch (e) { a.log('  câu:', e.message); try { a.bot.activateItem(); } catch (_) {} }
  }
  a.log('  câu được (bot đếm):', caught);
  await a.sleep(15000);
  const charms = (await R(`minecraft:clear ${A} minecraft:paper 0`)).trim();
  a.log(`n7 thi câu: A tiền ${a0} -> ${await bal(A)} | B tiền ${bb0} -> ${await bal(B)} | tin A: ${a.chat.filter(l => /thi câu|Giải|Bùa|hạng|#1|câu được/i.test(l)).join(' | ').slice(0, 600)}`);
  a.log('  đồ trong túi A:', a.bot.inventory.items().map(it => a.itemText(it).name + '×' + it.count).join(', ').slice(0, 300));
  await R(`fill ${p.x + 2} ${p.y - 3} ${p.z - 2} ${p.x + 6} ${p.y - 1} ${p.z + 2} minecraft:dirt`);
  await R(`lp user ${A} permission unset grim.exempt`);
  await b.end(); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
