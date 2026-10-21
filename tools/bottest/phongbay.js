// gỡ lỗi Phóng bay: cùng trình tự với phuphep.js (moveTo rồi chờ), Lướt trước để chắc sự kiện bấm tới server
const h = require('./h');
const pp = (id, lv, item = 'minecraft:stick') => `${item}[custom_data={PublicBukkitValues:{"duckymomocore:phuphep_${id}":${lv}}}]`;
(async () => {
  let A = await h.start('ThuA');
  A = await h.moveTo(A, 12330, -2610); await A.sleep(5000);
  const R = c => A.rc.cmd(c);
  const pos = async () => (await R('data get entity ThuA Pos')).replace(/.*data: /, '');
  const bars = []; A.bot.on('actionBar', m => bars.push(A.strip(m.toString())));
  await R('minecraft:clear ThuA'); await R(`minecraft:give ThuA ${pp('luot', 1)}`); await A.sleep(900); await A.bot.equip(A.bot.inventory.items()[0], 'hand'); await A.sleep(500);
  let p0 = await pos(); A.bot.activateItem(); await A.sleep(300); A.bot.deactivateItem(); await A.sleep(1200); A.log('Lướt:', p0, '->', await pos(), '| Motion:', (await R('data get entity ThuA Motion')).replace(/.*data: /, ''));
  A.bot.activateItem(); await A.sleep(300); A.bot.deactivateItem(); await A.sleep(800); A.log('bấm lần 2 ngay sau, thanh:', bars.join(' ; '));
  await R(`minecraft:item replace entity ThuA armor.chest with ${pp('phong_bay', 2, 'minecraft:elytra')}`); await A.sleep(800);
  p0 = await pos(); A.bot.activateItem(); await A.sleep(300); A.bot.deactivateItem(); await A.sleep(1500);
  A.log('Phóng bay (cầm que Lướt, Lướt đang hồi):', p0, '->', await pos(), '| thanh:', bars.join(' ; '));
  await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
