// d1 vào Đấu Trường: /warp pvp -> sảnh (0.5 65 -130.5) -> đi bộ thẳng vào cổng giữa (san2: x -2..2, z -173..-171)
// -> sân chờ, tin "đang chờ đối thủ" + nút [Đổi sang 2v2] [Cược…] [Thoát về sảnh]; thử /dautruong cuoc, doi, roi.
const { start } = require('./h');
const N = 'ThuD1';
(async () => {
  let t = await start(N);
  const R = c => t.rc.cmd(c);
  const where = async () => (await R(`data get entity ${N} Pos`)).replace(/.*data: /, '');
  await R(`gamemode survival ${N}`);
  await t.sleep(5000);   // bot mới vào lần đầu bị đưa về spawn sau vài giây
  t.log('tp sảnh PvP:', await R(`execute in minecraft:dautruong run minecraft:tp ${N} 0.5 65 -130.5 180 0`));
  await t.sleep(3000);   // đổi thế giới: bot nhận toạ độ mới (KHÔNG vào lại: Đấu Trường đưa người vào game trong thế giới đấu trường về spawn)
  t.log('bot thấy mình ở:', t.bot.entity.position.floored());
  t.log('ở sảnh:', await where());
  // tới sát trước cổng giữa rồi đi bộ (cổng xét lúc bước vào, dịch chuyển thẳng vào không tính)
  await R(`execute in minecraft:dautruong run minecraft:tp ${N} 0.5 65 -167.5 180 0`); await t.sleep(5000);
  t.log('trước cổng:', await where(), '| bot thấy:', t.bot.entity.position.floored(), 'đứng đất', t.bot.entity.onGround);
  const V = require('vec3');
  await t.bot.lookAt(new V(0.5, 66.5, -175), true);
  const n0 = t.chat.length;
  // bot không chạy vật lý được trong thế giới đấu trường (không nhận đất qua Via) -> tự gửi gói vị trí từng bước 0,25 khối về phía cổng
  t.bot.physicsEnabled = false;
  // lần dịch chuyển chưa được bot xác nhận (qua Via) thì server bỏ qua mọi gói di chuyển -> xác nhận một loạt mã
  let lastTp = null; t.bot._client.on('position', p => { lastTp = p.teleportId; });
  for (let id = 0; id < 64; id++) t.bot._client.write('teleport_confirm', { teleportId: id });
  await t.sleep(500);
  for (let z = -167.5; z >= -172.5; z -= 0.25) { t.bot._client.write('position', { x: 0.5, y: 65, z, onGround: true, flags: { onGround: true, hasHorizontalCollision: false } }); await t.sleep(60); if (t.chat.slice(n0).some(l => /chờ đối thủ|đang xem|chưa sẵn sàng/.test(l))) break; }
  await t.sleep(1500);
  t.log('d1 bước vào cổng:', t.chat.slice(n0).join(' | ').slice(0, 600));
  t.log('   vị trí:', await where());
  t.log('d1 /dautruong cuoc 100:', (await t.say('/dautruong cuoc 100', 1500)).join(' | ').slice(0, 250));
  t.log('d1 /dautruong doi 2v2:', (await t.say('/dautruong doi 2v2', 1500)).join(' | ').slice(0, 250));
  t.log('d1 /dautruong roi:', (await t.say('/dautruong roi', 2000)).join(' | ').slice(0, 200), '| vị trí', await where());
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
