// Phù phép thụ động Tăng tốc (22/10): cầm que có phuphep_tang_toc cấp 2 -> tốc chạy +20% (0,1 -> 0,12); đổi ô -> về 0,1; tay phụ -> 0,12.
const h = require('./h');
(async () => {
  const t = await h.start('ThuR3'); const R = c => t.rc.cmd(c); await t.sleep(2000);
  const sp = async () => (await R('attribute ThuR3 minecraft:movement_speed get')).replace(/.*is /, '');
  await R('minecraft:clear ThuR3');
  await R('minecraft:item replace entity ThuR3 hotbar.0 with minecraft:stick[custom_data={PublicBukkitValues:{"duckymomocore:phuphep_tang_toc":2}}]');
  t.bot.setQuickBarSlot(1); await t.sleep(1500); t.log('cầm ô trống:', await sp());
  t.bot.setQuickBarSlot(0); await t.sleep(1500); t.log('cầm que Tăng tốc II:', await sp());
  t.bot.activateItem(); await t.sleep(300); t.bot.deactivateItem(); await t.sleep(500);
  t.bot.setQuickBarSlot(1); await t.sleep(1500); t.log('đổi sang ô trống:', await sp());
  await R('minecraft:item replace entity ThuR3 weapon.offhand from entity ThuR3 hotbar.0'); await R('minecraft:item replace entity ThuR3 hotbar.0 with minecraft:air');
  await t.sleep(1500); t.log('que ở tay phụ:', await sp());
  await R('minecraft:clear ThuR3'); await t.sleep(1500); t.log('bỏ que:', await sp());
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
