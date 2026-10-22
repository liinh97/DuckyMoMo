// Sách cấm bước C: c1 = cầm món bị cấm (có ⛔) rồi thoát, giữ món trong túi; c2 = (luật đã bỏ) vào lại, món tự gỡ ⛔ + nhóm hồi chiêu.
const h = require('./h');
(async () => {
  const A = await h.start('ThuA'); const R = c => A.rc.cmd(c);
  const show = async tag => { const pdc = (await R('data get entity ThuA SelectedItem.components."minecraft:custom_data".PublicBukkitValues')).replace(/.*data: /, '');
    A.log(tag, '| sachcam_lore:', /sachcam_lore/.test(pdc), '| hồi chiêu:', (await R('data get entity ThuA SelectedItem.components."minecraft:use_cooldown"')).replace(/.*data: /, '').slice(0, 80)); };
  if (process.argv[2] === 'c1') {
    await R('minecraft:clear ThuA'); await R('minecraft:give ThuA minecraft:stick'); await R('dmm laydo kiem_rong ThuA 1'); await A.sleep(1200);
    await A.bot.equip(A.bot.inventory.items().find(i => i.name !== 'stick'), 'hand'); await A.sleep(1500); await show('[c1] đang có luật');
  } else {
    await A.sleep(2000); await A.bot.equip(A.bot.inventory.items().find(i => i.name !== 'stick'), 'hand').catch(() => {}); await A.sleep(1500); await show('[c2] đã bỏ luật, vào lại');
    await R('minecraft:clear ThuA');
  }
  await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
