// Dịch chuyển + Phóng bay, mỗi cái một phiên bot mới (bot vào lại thì bấm đầu tiên luôn tới server)
const h = require('./h');
const pp = (id, lv, item = 'minecraft:stick') => `${item}[custom_data={PublicBukkitValues:{"duckymomocore:phuphep_${id}":${lv}}}]`;
(async () => {
  let A = await h.start('ThuA');
  const R = c => A.rc.cmd(c);
  const pos = async () => (await R('data get entity ThuA Pos')).replace(/.*data: /, '').replace(/d/g, '').replace(/[\[\]]/g, '').split(',').map(Number);
  const d = (a, b) => Math.hypot(a[0] - b[0], a[2] - b[2]).toFixed(1);
  await R('minecraft:clear ThuA'); await R(`minecraft:give ThuA ${pp('dich_chuyen', 2)}`);
  A = await h.moveTo(A, 12330, -2610); await A.sleep(5000);
  await A.bot.equip(A.bot.inventory.items()[0], 'hand'); await A.sleep(500);
  const bars = []; A.bot.on('actionBar', m => bars.push(A.strip(m.toString())));
  A.log('hướng nhìn yaw/pitch', A.bot.entity.yaw.toFixed(2), A.bot.entity.pitch.toFixed(2), '| khối trước mặt:', [1, 2, 3, 4].map(i => { const v = A.bot.entity.position.offset(-Math.sin(A.bot.entity.yaw) * i, 0, -Math.cos(A.bot.entity.yaw) * i); const b = A.bot.blockAt(v); return b && b.name; }).join(','));
  let p0 = await pos(); A.bot.activateItem(); await A.sleep(300); A.bot.deactivateItem(); await A.sleep(1000);
  A.log('Dịch chuyển II:', d(p0, await pos()), 'khối (mong 6) | thanh:', bars.join(' ; '));
  await A.rc.cmd('minecraft:clear ThuA');
  await A.rc.cmd(`minecraft:item replace entity ThuA armor.chest with ${pp('phong_bay', 2, 'minecraft:elytra')}`); await A.rc.cmd('minecraft:give ThuA minecraft:stick');
  A = await h.moveTo(A, 12340, -2620); await A.sleep(5000);
  await A.bot.equip(A.bot.inventory.items().find(i => i.name === 'stick'), 'hand'); await A.sleep(500);
  const R2 = c => A.rc.cmd(c);
  p0 = (await R2('data get entity ThuA Pos')).replace(/.*data: /, ''); A.bot.activateItem(); await A.sleep(300); A.bot.deactivateItem(); await A.sleep(700);
  A.log('Phóng bay II: trước', p0, '| sau 0,7 giây', (await R2('data get entity ThuA Pos')).replace(/.*data: /, ''), '| lượn:', (await R2('data get entity ThuA FallFlying')).replace(/.*data: /, ''));
  await R2('minecraft:clear ThuA'); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
