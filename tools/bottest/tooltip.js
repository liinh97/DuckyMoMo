// Thư viện đồ › tooltip gọn: phát giap_tan_thu_5 (29 dòng) cho bot, đọc lore + cờ ẩn trên đồ.
const h = require('./h');
(async () => {
  const A = await h.start('ThuA');
  const R = c => A.rc.cmd(c);
  await R('minecraft:clear ThuA'); A.log((await R('dmm laydo giap_tan_thu_5 ThuA 1')).trim()); await A.sleep(1000);
  const it = A.bot.inventory.items()[0]; const x = A.itemText(it);
  A.log('tên:', x.name); x.lore.forEach((l, i) => A.log('  lore', i, l));
  A.log('ẩn:', (await R('data get entity ThuA Inventory[0].components."minecraft:tooltip_display"')).replace(/.*data: /, ''));
  await R('minecraft:clear ThuA'); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
