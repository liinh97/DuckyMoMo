// w12 bảng xếp hạng mùa (Leaderboard, Cài đặt leaderboard.points: attack-win 30, kill 3). Wrapper tạm hạ min-defender-residents,
// min-town-age-days về 0 (thị trấn thử mới lập, 1 dân). Chiến Thần: ThuW2 (vua phòng thủ) hạ ThuW1 (vua tấn công) trong vùng vây.
// Rồi /swa siege ThuThanhB setbalance 5000 + end -> bên tấn công thắng -> ThuNuocA +30. Đọc /bxh trước và sau.
const { start } = require('./h');
const X = 13629, Z = -3152;
(async () => {
  let w1 = await start('ThuW1', '10.9.1.1'); let w2 = await start('ThuW2', '10.9.1.2');
  let R = c => w2.rc.cmd(c);
  const thu = process.argv.includes('thu');   // lần 2: bên phòng thủ thắng
  await w1.sleep(4000);
  await R('minecraft:time set 1000');
  for (const [n, dx] of [['ThuW1', 0], ['ThuW2', 2]]) { await R(`gamemode survival ${n}`); await R(`effect clear ${n}`); await R(`minecraft:tp ${n} ${X + dx} 95 ${Z}`); }
  await w1.end(); await w2.end(); await new Promise(r => setTimeout(r, 2500));   // vào lại để 2 bot thấy nhau
  w1 = await start('ThuW1', '10.9.1.1'); w2 = await start('ThuW2', '10.9.1.2'); R = c => w2.rc.cmd(c); await w2.sleep(3000);
  w2.log('/bxh trước:', (await w2.say('/bxh', 1800)).join(' | ').replace(/\s+/g, ' ').slice(0, 500));
  // Chiến Thần: ThuW1 còn 1 máu, ThuW2 chém
  await R('minecraft:item replace entity ThuW2 weapon.mainhand with minecraft:iron_sword'); w2.bot.setQuickBarSlot(0);
  await R('minecraft:damage ThuW1 19 minecraft:generic'); await w2.sleep(1200);
  const e = w2.bot.players.ThuW1 && w2.bot.players.ThuW1.entity;
  if (e) { await w2.bot.lookAt(e.position.offset(0, 1.5, 0), true); w2.bot.attack(e); } else w2.log('  không thấy ThuW1');
  await w2.sleep(2000);
  w2.log('w12 ThuW2 hạ ThuW1:', w2.chat.slice(-3).join(' | ').slice(0, 250));
  w2.log('  balance trước khi chốt:', (await R('swa siege ThuThanhB setbalance ' + (thu ? -5000 : 5000))).replace(/\s+/g, ' ').slice(0, 150));
  const n0 = w2.chat.length;
  w2.log('  kết thúc trận:', (await R('swa siege ThuThanhB end')).replace(/\s+/g, ' ').slice(0, 150));
  await w2.sleep(4000);
  w2.log('w12 tin sau trận:', w2.chat.slice(n0).join(' | ').replace(/\s+/g, ' ').slice(0, 600));
  w2.log('/bxh chienthan:', (await w2.say('/bxh chienthan', 1800)).join(' | ').replace(/\s+/g, ' ').slice(0, 300));
  w2.log('/bxh sau:', (await w2.say('/bxh', 1800)).join(' | ').replace(/\s+/g, ' ').slice(0, 600));
  await w1.end(); await w2.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
