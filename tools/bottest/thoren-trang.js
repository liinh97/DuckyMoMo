// Menu set theo trang cấp (23/10): mở Tân Thủ (chưa có gì -> trang +0), xem Mũ +0 (món thật + ⚒ RÈN), bấm ▶ sang +1.
const h = require('./h');
const N = process.env.BOTN || 'ThuTrang';
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  await R(`minecraft:clear ${N}`); await t.sleep(1500);
  const sl = (w, re) => w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
  let w = await t.openMenu('/thoren'); await t.click(sl(w, /^Tân Thủ/)); await t.sleep(1200); w = t.bot.currentWindow;
  t.log('## tiêu đề:', JSON.stringify(w.title).replace(/\\"|"/g, '').match(/Rèn[^,}]*/)?.[0]);
  w.slots.slice(0, w.inventoryStart).forEach((it, i) => { if (!it || /pane$/.test(it.name)) return; const x = t.itemText(it); t.log(`  [${i}] ${it.name} | ${x.name}`); });
  const mu = w.slots[sl(w, /Mũ tân thủ/i)]; t.log('## Mũ +0 mô tả:', t.itemText(mu).lore.join(' ¦ '));
  await t.click(sl(w, /Cấp \+1/)); await t.sleep(1200); w = t.bot.currentWindow;
  t.log('## sau ▶:', JSON.stringify(w.title).replace(/\\"|"/g, '').match(/Rèn[^,}]*/)?.[0], '|', t.itemText(w.slots[sl(w, /Giáp tân thủ/i)]).lore.slice(-6).join(' ¦ '));
  t.bot.closeWindow(w); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
