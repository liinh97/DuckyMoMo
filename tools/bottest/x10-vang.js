// x10 mua bằng Vàng (/vang, bấm 2 lần) + x11 Vàng không chuyển được. Vàng thử do admin cộng tay trước (scratchpad x10.sh), xong trừ lại.
const { start } = require('./h');
const N = 'ThuVang';
(async () => {
  const t = await start(N);
  const R = c => t.rc.cmd(c);
  const names = w => w ? w.slots.slice(0, w.inventoryStart).map((it, i) => it && t.itemText(it).name.trim() && `${i}:${t.itemText(it).name}`).filter(Boolean).join(', ') : '(không mở)';
  let w = await t.openMenu('/vang');
  t.log('x10 /vang:', names(w).slice(0, 600));
  const buy = async (re, label) => {
    w = await t.openMenu('/vang');
    const i = w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name + ' ' + t.itemText(it).lore.join(' '))) : -1;
    if (i < 0) { t.log(label, 'không thấy món'); if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); return; }
    const n0 = t.chat.length;
    w = await t.click(i); await t.sleep(800);
    const lore1 = w && w.slots[i] ? t.itemText(w.slots[i]).lore.slice(-2).join(' / ') : '';
    w = await t.click(i); await t.sleep(2500);
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
    t.log(label, '| sau bấm 1:', lore1.slice(0, 120), '| tin:', t.chat.slice(n0).join(' | ').slice(0, 300));
    t.log('   rank check:', (await R(`rank check ${N}`)).replace(/\s+/g, ' ').slice(0, 200));
  };
  await buy(/Nam Tước.*1 tháng/, 'x10 mua Nam Tước 1 tháng (lần 1)');
  await buy(/Nam Tước.*1 tháng/, 'x10 mua Nam Tước 1 tháng (lần 2, cộng dồn)');
  await buy(/Bá Tước.*12 tháng/, 'x10 mua Bá Tước 12 tháng (đắt hơn số dư)');
  for (const c of ['/vang chuyen ThuK 10', '/vang give ThuK 10', '/vang pay ThuK 10', '/pay ThuK 10 vang']) t.log(`x11 ${c}:`, (await t.say(c, 1300)).join(' | ').slice(0, 200));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
