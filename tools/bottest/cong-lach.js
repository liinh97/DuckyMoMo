// s14 lách công thành (phiên đánh đang mở, vùng vây ThuThanhB): /balo trong vùng vây; thoát game khi đang giao tranh (PvPManager:
// Kill on Logout, rơi đồ, báo cả server); ném ngọc ender vào thị trấn bị vây.
const { start } = require('./h');
const X = 13629, Z = -3152;
(async () => {
  let w1 = await start('ThuW1', '10.9.1.1'); let w2 = await start('ThuW2', '10.9.1.2');
  const R = c => w2.rc.cmd(c);
  await w1.sleep(3000);
  await R('minecraft:time set 1000');
  for (const [n, dx] of [['ThuW1', 0], ['ThuW2', 2]]) { await R(`gamemode survival ${n}`); await R(`effect clear ${n}`); await R(`minecraft:tp ${n} ${X + dx} 95 ${Z}`); }
  await w1.end(); await w1.sleep(2500); w1 = await start('ThuW1', '10.9.1.1'); await w1.sleep(2500);
  w1.log('  vị trí ThuW1', (await R('data get entity ThuW1 Pos')).replace(/.*data: /, ''));
  // /balo trong vùng vây lúc có phiên
  w1.log('s14 /balo trong vùng vây:', (await w1.say('/balo', 1500)).join(' | ').slice(0, 200), '| cửa sổ mở:', !!w1.bot.currentWindow);
  if (w1.bot.currentWindow) w1.bot.closeWindow(w1.bot.currentWindow);
  // thoát game khi đang giao tranh
  await R('minecraft:clear ThuW1'); await R('minecraft:give ThuW1 minecraft:dirt 32'); await R('minecraft:item replace entity ThuW2 weapon.mainhand with minecraft:wooden_sword');
  w2.bot.setQuickBarSlot(0); await w2.sleep(500);
  const e = w2.bot.players.ThuW1 && w2.bot.players.ThuW1.entity;
  if (e) { await w2.bot.lookAt(e.position.offset(0, 1.5, 0), true); w2.bot.attack(e); } else w2.log('  ThuW2 không thấy ThuW1');
  await w2.sleep(800);
  const n0 = w2.chat.length;
  w1.bot.quit(); await w2.sleep(3000);
  w2.log('s14 ThuW1 thoát khi đang giao tranh -> tin cả server:', w2.chat.slice(n0).join(' | ').slice(0, 250));
  w2.log('  đất quanh đó có dirt rơi:', (await R(`execute positioned ${X} 93 ${Z} if entity @e[type=item,distance=..8,nbt={Item:{id:"minecraft:dirt"}}]`)).trim());
  w1 = await start('ThuW1', '10.9.1.1'); await w1.sleep(3000);
  w1.log('  ThuW1 vào lại: còn dirt trong túi?', (await R('minecraft:clear ThuW1 minecraft:dirt 0')).trim(), '| tin:', w1.chat.slice(-4).join(' | ').slice(0, 200));
  await R(`execute positioned ${X} 93 ${Z} run minecraft:kill @e[type=item,distance=..10]`);
  await w1.end(); await w2.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
