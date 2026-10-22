// w12 Chiến Thần (Cài đặt kill 3, kill-cap 3/người/phiên): ThuW1 (ThuNuocA) và ThuW2 (ThuNuocB) cùng có quân hàm private (wrapper phong),
// đang có cuộc vây + phiên đánh. ThuW2 hạ ThuW1 4 lần -> +3, +3, +3, rồi hết (cap 3). Đọc /bxh chienthan.
const { start } = require('./h');
const X = 13629, Z = -3152;
(async () => {
  let w1 = await start('ThuW1', '10.9.1.1'); let w2 = await start('ThuW2', '10.9.1.2');
  let R = c => w2.rc.cmd(c);
  await w1.sleep(4000); await R('minecraft:time set 1000');
  const place = async () => {
    for (const [n, dx] of [['ThuW1', 0], ['ThuW2', 2]]) { await R(`gamemode survival ${n}`); await R(`effect clear ${n}`); await R(`minecraft:tp ${n} ${X + dx} 95 ${Z}`); }
    await w2.sleep(3000);   // không vào lại: dịch chuyển phía server, 2 bot đứng sát nhau vẫn thấy nhau (như cong-lach2)
  };
  for (let k = 1; k <= 4; k++) {
    await place();
    await R('minecraft:item replace entity ThuW2 weapon.mainhand with minecraft:netherite_sword'); w2.bot.setQuickBarSlot(0);
    await R('effect give ThuW1 minecraft:instant_health 1 5 true'); await w2.sleep(600);
    await R('minecraft:damage ThuW1 12 minecraft:generic'); await w2.sleep(1500);
    w2.log('  vị trí', (await R('data get entity ThuW1 Pos')).replace(/.*data: /, ''), (await R('data get entity ThuW2 Pos')).replace(/.*data: /, ''), '| máu ThuW1', (await R('data get entity ThuW1 Health')).replace(/.*data: /, ''), '| ThuW2 thấy:', Object.keys(w2.bot.players).filter(x => w2.bot.players[x].entity).join(','), '| ThuW2 tự thấy mình ở', w2.bot.entity.position.floored());
    let e = null; for (let i = 0; i < 20 && !e; i++) { e = w2.bot.players.ThuW1 && w2.bot.players.ThuW1.entity; if (!e) await w2.sleep(500); }
    if (e) { await w2.bot.lookAt(e.position.offset(0, 1.5, 0), true); w2.bot.attack(e); await w2.sleep(700); w2.bot.attack(e); } else w2.log('  không thấy ThuW1');
    await w2.sleep(2000);
    w2.log(`w12 lần ${k}:`, w2.chat.filter(l => /ThuW1 (was|died)|Chiến Thần|\+3/.test(l)).slice(-2).join(' | ').slice(0, 200));
    w2.log('   /bxh chienthan:', (await w2.say('/bxh chienthan', 1500)).join(' | ').replace(/\s+/g, ' ').slice(0, 200));
  }
  await w1.end(); await w2.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
