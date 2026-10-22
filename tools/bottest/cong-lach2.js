// s14 /balo trong vùng vây (ThuW1 tạm lên Dân để có balo) + w9 đánh nhau: lính ThuW4 (thuê bởi ThuNuocA) vs ThuW1 (ThuNuocA), ThuW2 (địch).
// Wrapper tạm cho người ngoài dịch chuyển vào vùng vây (SiegeWar non_resident_spawn…) rồi trả lại.
const { start } = require('./h');
const X = 13629, Z = -3152;
(async () => {
  const b = {}; for (const [n, ip] of [['ThuW1', '10.9.1.1'], ['ThuW2', '10.9.1.2'], ['ThuW4', '10.9.1.4']]) b[n] = await start(n, ip);
  const R = c => b.ThuW2.rc.cmd(c);
  await b.ThuW1.sleep(4000);
  await R('minecraft:time set 1000');
  await R('lp user ThuW1 parent add dan'); await b.ThuW1.sleep(1500);
  const off = { ThuW1: 0, ThuW2: 2, ThuW4: 1 };
  for (const n of Object.keys(b)) { await R(`gamemode survival ${n}`); await R(`effect clear ${n}`); await R(`minecraft:tp ${n} ${X + off[n]} 95 ${Z}`); }
  await b.ThuW1.sleep(3000);
  b.ThuW1.log('s14 /balo (Dân) trong vùng vây lúc có phiên:', (await b.ThuW1.say('/balo', 1500)).join(' | ').slice(0, 200), '| cửa sổ mở:', !!b.ThuW1.bot.currentWindow);
  if (b.ThuW1.bot.currentWindow) b.ThuW1.bot.closeWindow(b.ThuW1.bot.currentWindow);
  await R('lp user ThuW1 parent remove dan');
  const hp = async n => +((await R(`data get entity ${n} Health`)).match(/([\d.]+)f/) || [0, 0])[1];
  for (const [atk, vic] of [['ThuW4', 'ThuW2'], ['ThuW4', 'ThuW1'], ['ThuW1', 'ThuW4'], ['ThuW2', 'ThuW4']]) {
    await R(`effect give ${vic} minecraft:instant_health 1 5 true`); await R(`effect give ${vic} minecraft:hunger 30 255 true`);
    await R(`minecraft:item replace entity ${atk} weapon.mainhand with minecraft:iron_sword`); b[atk].bot.setQuickBarSlot(0);
    await b[atk].sleep(1500);   // hồi đòn đầy
    const e = b[atk].bot.players[vic] && b[atk].bot.players[vic].entity;
    const h0 = await hp(vic);
    if (e) { await b[atk].bot.lookAt(e.position.offset(0, 1.5, 0), true); b[atk].bot.attack(e); }
    await b[atk].sleep(250);
    b.ThuW1.log(`w9 ${atk} đánh ${vic}: ${e ? '' : '(không thấy) '}máu ${h0} -> ${await hp(vic)} | tin ${atk}: ${b[atk].chat.slice(-1).join(' | ').slice(0, 120)}`);
    await R(`effect clear ${vic}`);
  }
  for (const n of Object.keys(b)) await b[n].end();
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
