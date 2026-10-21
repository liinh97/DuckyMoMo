// q2 lệnh săn + o4 dọn rác, test NHANH (wrapper q2-o4.sh tạm đặt wanted.interval-minutes 1, expire-minutes 5, item-cleaner.interval-minutes 1).
// q2: 2 bot tới chỗ lệnh săn -> quái phát sáng xuất hiện -> A gây nhiều sát thương hơn B -> A nhận thưởng; lệnh khác để hết 5 phút -> trốn.
// o4: vứt đồ ra đất -> báo trước 30 giây / 5 giây -> dọn; tắt "Thông báo dọn rác" trong /caidat thì không thấy báo.
const { start, moveTo } = require('./h');
const A = 'ThuBA', B = 'ThuBB';
const IP = { ThuBA: '10.1.0.1', ThuBB: '10.1.0.2' };
(async () => {
  let a = await start(A, IP[A]);
  const R = c => a.rc.cmd(c);
  const bal = async n => +((await R(`bal ${n}`)).replace(/[^\d.]/g, '') || 0);
  await R(`effect give ${A} minecraft:resistance 1800 4 true`); await R(`gamemode survival ${A}`);
  // ---- q2
  let ls = '';
  for (let k = 0; k < 30; k++) { ls = (await a.say('/lenhsan', 1500)).concat(a.chat.filter(l => /LỆNH SĂN/.test(l))).join(' | '); if (/X\s*-?\d+\s*Z/.test(ls)) break; await a.sleep(10000); }   // chờ lệnh mới (interval 1 phút)
  a.log('q2 /lenhsan:', ls.slice(0, 400));
  const m = ls.match(/X\s*(-?\d+)\s*Z\s*(-?\d+)/);
  if (m) {
    const X = +m[1], Z = +m[2];
    a = await moveTo(a, X, Z, 'minecraft:overworld', IP[A]);
    let b = await start(B, IP[B]); await R(`minecraft:tp ${B} ${A}`); await R(`effect give ${B} minecraft:resistance 1800 4 true`);
    await b.end(); await b.sleep(2500); b = await start(B, IP[B]);
    let mob = null;
    for (let i = 0; i < 12 && !mob; i++) { await a.sleep(5000); const r = await R(`execute at ${A} if entity @e[distance=..80,type=!player,type=!item,nbt={Glowing:1b}]`); if (/passed/.test(r)) mob = r; }
    a.log('q2 quái phát sáng gần đó:', mob || 'KHÔNG thấy sau 60 giây', '|', a.chat.filter(l => /LỆNH SĂN|xuất hiện/.test(l)).slice(-2).join(' | ').slice(0, 200));
    if (mob) {
      const sel = `@e[distance=..80,type=!player,type=!item,nbt={Glowing:1b},limit=1,sort=nearest]`;
      const a0 = await bal(A), b0 = await bal(B);
      await R(`execute at ${A} run damage ${sel} 20 minecraft:player_attack by ${B}`);
      for (let i = 0; i < 12; i++) { await R(`execute at ${A} run damage ${sel} 40 minecraft:player_attack by ${A}`); await a.sleep(600); }
      await a.sleep(2500);
      a.log('q2 hạ quái: tiền A', a0, '->', await bal(A), '| B', b0, '->', await bal(B), '| tin:', a.chat.filter(l => /lệnh săn|Lệnh săn|hạ|thưởng/i.test(l)).slice(-3).join(' | ').slice(0, 300));
    }
    await b.end();
  }
  if (process.argv.includes('q2')) { await a.end(); process.exit(0); }   // run.sh q2-o4.js q2 = chỉ lệnh săn
  // ---- o4
  a = await moveTo(a, 13200, -2900, 'minecraft:overworld', IP[A]);
  const p = a.bot.entity.position.floored();
  const drop = async () => { await R(`summon item ${p.x + 3} ${p.y + 1} ${p.z} {Item:{id:"minecraft:dirt",count:1},Age:-32768,PickupDelay:32767,Tags:["thuo4"]}`); };
  await drop();
  let n0 = a.chat.length;
  for (let i = 0; i < 26; i++) { await a.sleep(5000); if (a.chat.slice(n0).some(l => /Đã dọn sạch/.test(l))) break; }
  a.log('o4 thông báo:', a.chat.slice(n0).filter(l => /Dọn|dọn/.test(l)).join(' | ').slice(0, 300), '| đồ còn:', (await R('execute if entity @e[tag=thuo4]')).slice(-12));
  // tắt thông báo dọn rác trong /caidat
  let w = await a.openMenu('/caidat');
  const i = w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && /dọn rác/i.test(a.itemText(it).name)) : -1;
  if (i >= 0) { w = await a.click(i); } if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  await drop(); n0 = a.chat.length;
  for (let k = 0; k < 26; k++) { await a.sleep(5000); if (!/passed/.test(await R('execute if entity @e[tag=thuo4]'))) break; }
  a.log('o4 sau khi tắt (ô', i, '):', a.chat.slice(n0).filter(l => /Dọn|dọn/.test(l)).join(' | ').slice(0, 200) || '(không thấy báo)', '| đồ còn:', (await R('execute if entity @e[tag=thuo4]')).slice(-12));
  if (i >= 0) { w = await a.openMenu('/caidat'); await a.click(i); if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow); }
  await R('minecraft:kill @e[tag=thuo4]');
  await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
