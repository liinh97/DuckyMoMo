// Đọc mô tả que Lướt (thu_sc_luot) phía server sau 5 giây trong túi.
const h = require('./h');
(async () => {
  const t = await h.start('ThuR1'); const R = c => t.rc.cmd(c);
  await R('minecraft:clear ThuR1'); await R('dmm laydo thu_sc_luot ThuR1 1'); await t.sleep(5000);
  t.log('lore server:', (await R('data get entity ThuR1 Inventory[0].components."minecraft:lore"')).replace(/.*data: /, '').slice(0, 400));
  t.log('pdc:', (await R('data get entity ThuR1 Inventory[0].components."minecraft:custom_data".PublicBukkitValues')).replace(/.*data: /, '').slice(0, 400));
  await R('minecraft:clear ThuR1'); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
