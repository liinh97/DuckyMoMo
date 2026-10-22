// w9 lính đánh thuê (Mercenary, Cài đặt mercenary: phí 10%, hợp đồng 48 giờ). Phiên đánh đang mở quanh ThuThanhB.
// ThuW4 (không nước) đăng ký 500 -> vua ThuW1 (ThuNuocA) thuê -> ThuW4 nhận: ngân khố ThuNuocA -500, ThuW4 +450.
// Trong vùng vây: ThuW4 không bị "ốm" (so với ThuW5 không nước, không hợp đồng); ThuW4 đánh ThuW1 (nước thuê) không mất máu, đánh ThuW2 (địch) có.
const { start } = require('./h');
const X = 13629, Z = -3152;
(async () => {
  const w1 = await start('ThuW1', '10.9.1.1');
  const R = c => w1.rc.cmd(c);
  const bal = async n => +((await R(`bal ${n}`)).replace(/.*:\s*/, '').replace(/[^\d.]/g, '') || 0);
  const bank = async () => (await R('papi parse ThuW1 %townyadvanced_nation_bank%')).trim();
  const hp = async n => +((await R(`data get entity ${n} Health`)).match(/([\d.]+)f/) || [0, 0])[1];
  const w4 = await start('ThuW4', '10.9.1.4'); const w5 = await start('ThuW5', '10.9.1.5'); const w2 = await start('ThuW2', '10.9.1.2');
  await w1.sleep(4000);
  for (const n of ['ThuW1', 'ThuW2', 'ThuW4', 'ThuW5']) { await R(`gamemode survival ${n}`); await R(`effect clear ${n}`); }
  if (!process.argv.includes('vung')) {
  await R('eco set ThuW4 1000');
  w4.log('đăng ký:', (await w4.say('/linhdanhthue dangky 500', 1500)).join(' | ').slice(0, 200));
  const b0 = await bank(), m0 = await bal('ThuW4');
  w1.log('thuê:', (await w1.say('/linhdanhthue thue ThuW4', 1500)).join(' | ').slice(0, 200));
  w4.log('nhận:', (await w4.say('/linhdanhthue nhan', 2000)).join(' | ').slice(0, 300));
  w1.log(`w9 tiền: ngân khố ThuNuocA ${b0} -> ${await bank()} | ThuW4 ${m0} -> ${await bal('ThuW4')} (Cài đặt phí 10% => 450)`);
  w1.log('  /linhdanhthue:', (await w1.say('/linhdanhthue', 1500)).join(' | ').slice(0, 300));
  }
  // tất cả vào vùng vây (tp server rồi vào lại để bot nhận toạ độ)
  const bots = { ThuW1: w1, ThuW2: w2, ThuW4: w4, ThuW5: w5 };
  const ips = { ThuW1: '10.9.1.1', ThuW2: '10.9.1.2', ThuW4: '10.9.1.4', ThuW5: '10.9.1.5' };
  const off = { ThuW1: [0, 0], ThuW2: [2, 0], ThuW4: [1, 1], ThuW5: [-6, 3] };
  for (const n of Object.keys(bots)) await R(`minecraft:tp ${n} ${X + off[n][0]} 95 ${Z + off[n][1]}`);
  for (const n of Object.keys(bots)) await bots[n].end();   // đóng sau cùng: R dùng rcon của ThuW1
  await w1.sleep(2500);
  for (const n of Object.keys(bots)) { bots[n] = await start(n, ips[n]); }
  const a = bots;
  const R2 = c => a.ThuW1.rc.cmd(c);
  // người không thuộc nước nào vào lại game trong vùng vây bị SiegeWar đưa về spawn -> dịch chuyển lúc đang online, không vào lại
  await a.ThuW1.sleep(15000);
  for (const n of ['ThuW4', 'ThuW5']) { a.ThuW1.log(`  tp ${n}:`, (await R2(`minecraft:tp ${n} ${X + off[n][0]} 93 ${Z + off[n][1]}`)).trim(), '| tin:', a[n].chat.slice(-2).join(' | ').slice(0, 150)); }
  await a.ThuW1.sleep(3000);
  for (const n of ['ThuW4', 'ThuW5']) a.ThuW1.log(`  3 giây sau ${n} ở`, (await R2(`data get entity ${n} Pos`)).replace(/.*data: /, ''), '| tin:', a[n].chat.slice(-3).join(' | ').slice(0, 200));
  await a.ThuW1.sleep(32000);   // SiegeWar: seconds_warning 20 giây rồi mới ốm
  const hp2 = async n => +((await R2(`data get entity ${n} Health`)).match(/([\d.]+)f/) || [0, 0])[1];
  for (const n of Object.keys(a)) a.ThuW1.log('  vị trí', n, (await R2(`data get entity ${n} Pos`)).replace(/.*data: /, ''), '| bot thấy:', Object.keys(a[n].bot.players).filter(x => a[n].bot.players[x].entity).join(','));
  const has = async (n, e) => /passed/.test(await R2(`execute as ${n} if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{effects:{"minecraft:${e}":{}}}}`));
  for (const n of ['ThuW4', 'ThuW5']) a.ThuW1.log(`w9 ${n} trong vùng vây: nausea ${await has(n, 'nausea')}, poison ${await has(n, 'poison')}, weakness ${await has(n, 'weakness')} | tin: ${a[n].chat.slice(-3).join(' | ').slice(0, 160)}`);
  for (const [atk, vic] of [['ThuW4', 'ThuW1'], ['ThuW4', 'ThuW2'], ['ThuW1', 'ThuW4']]) {
    await R2(`effect clear ${vic}`); await R2(`effect give ${vic} minecraft:instant_health 1 5 true`); await R2(`minecraft:item replace entity ${atk} weapon.mainhand with minecraft:iron_sword`); a[atk].bot.setQuickBarSlot(0); await a.ThuW1.sleep(800);
    const e = a[atk].bot.players[vic] && a[atk].bot.players[vic].entity;
    const h0 = await hp2(vic);
    if (e) { await a[atk].bot.lookAt(e.position.offset(0, 1.5, 0), true); a[atk].bot.attack(e); }
    await a.ThuW1.sleep(300);
    a.ThuW1.log(`w9 ${atk} đánh ${vic}: ${e ? '' : '(không thấy) '}máu ${h0} -> ${await hp2(vic)} | tin ${atk}: ${a[atk].chat.slice(-2).join(' | ').slice(0, 140)}`);
  }
  for (const n of Object.keys(a)) await a[n].end();
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
