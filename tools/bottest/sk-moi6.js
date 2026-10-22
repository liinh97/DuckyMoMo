// Lượt 6: Hoảng sợ (quái bỏ chạy), Khiêu khích (zombie không thấy bot tàng hình -> sau khiêu khích đuổi theo), Khổng lồ dưới trần thấp,
// Hộ giá tới ThuB (cùng thị trấn ThuTranA), Sách cấm tháo giáp trong đấu trường (bước "giap": cần đặt luật trước).
const h = require('./h');
const pp = (id, lv, item = 'minecraft:stick') => `${item}[custom_data={PublicBukkitValues:{"duckymomocore:phuphep_${id}":${lv}}}]`;
const X = 12320, Z = -2610;
const go = async (t, x, z) => { for (let i = 0; i < 4 && (Math.abs(t.bot.entity.position.x - x) > 3 || Math.abs(t.bot.entity.position.z - z) > 3); i++) { t = await h.moveTo(t, x, z); await t.sleep(2500); } return t; };
(async () => {
  let A = await go(await h.start('ThuA'), X, Z); const R = c => A.rc.cmd(c);
  const num = s => parseFloat(String(s).replace(/.*data: /, '').replace(/[^\d.\-]/g, ''));
  const P = async n => (await R(`data get entity ${n} Pos`)).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  const d = (a, b) => Math.hypot(a[0] - b[0], a[2] - b[2]).toFixed(1);
  const hold = async give => { await R('minecraft:clear ThuA'); await R(`minecraft:give ThuA ${give}`); await A.sleep(900); const it = A.bot.inventory.items()[0]; if (it) await A.bot.equip(it, 'hand'); await A.sleep(400); };
  const use = async () => { A.bot.activateItem(); await A.sleep(250); A.bot.deactivateItem(); await A.sleep(500); };
  if (process.argv[2] === 'giap') {
    await R('minecraft:clear ThuA'); await R('dmm laydo giap ThuA 1'); await A.sleep(1000);
    const g = A.bot.inventory.items().find(i => /chestplate/.test(i.name)); await A.bot.equip(g, 'torso'); await A.sleep(800);
    A.log('đang mặc:', (await R('data get entity ThuA equipment.chest.id')).replace(/.*data: /, ''));
    await R('execute in minecraft:dautruong run minecraft:tp ThuA 0.5 65 -130.5'); await A.sleep(2500);
    A.log('vào đấu trường 2,5 giây sau, ngực:', (await R('data get entity ThuA equipment.chest.id')).replace(/.*data: /, ''), '| trong túi còn giáp:', (await R('minecraft:clear ThuA minecraft:leather_chestplate 0')).replace(/.*Found /, ''));
    await R('minecraft:clear ThuA'); A = await go(A, X, Z); await A.end(); process.exit(0);
  }
  // Hoảng sợ lên quái
  await R(`minecraft:execute at ThuA positioned ~ ~ ~4 positioned over motion_blocking_no_leaves run summon husk ~ ~ ~ {Tags:["thusk"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:follow_range",base:0},{id:"minecraft:max_health",base:100}],Health:100f}`); await A.sleep(1000);
  await hold(pp('hoang_so', 2)); { const e = A.bot.nearestEntity(x => x.name === 'husk'); if (e) await A.bot.lookAt(e.position.offset(0, 1.2, 0), true); await A.sleep(300); }
  const hs = async () => (await R('data get entity @e[type=husk,tag=thusk,limit=1] Pos')).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  let a = await P('ThuA'), b = await hs(); await use(); await A.sleep(2500); A.log('Hoảng sợ II lên quái: husk cách', d(a, b), '->', d(a, await hs()), 'khối (mong xa ra)');
  await R('minecraft:kill @e[type=husk,tag=thusk]');
  // Khiêu khích: bot tàng hình, zombie 8 khối không thấy; sau khiêu khích đuổi theo
  await R('effect give ThuA minecraft:invisibility 60 0 true'); await R('effect give ThuA minecraft:resistance 60 4 true'); await R('minecraft:clear ThuA');
  await R(`minecraft:execute at ThuA positioned ~6 ~ ~6 positioned over motion_blocking_no_leaves run summon zombie ~ ~ ~ {Tags:["thusk"],PersistenceRequired:1b,Silent:1b,equipment:{head:{id:"minecraft:leather_helmet",count:1}}}`);
  await A.sleep(800); const zp = async () => (await R('data get entity @e[type=zombie,tag=thusk,limit=1] Pos')).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  a = await P('ThuA'); let z0 = await zp(); await A.sleep(3000); const z1 = await zp(); A.log('Khiêu khích đối chứng (tàng hình, chưa khiêu khích): zombie cách', d(a, z0), '->', d(a, z1));
  await hold(pp('khieu_khich', 2)); await use(); await A.sleep(3000); A.log('Khiêu khích II: zombie cách', d(a, z1), '->', d(a, await zp()), '(mong lại gần)');
  await R('minecraft:kill @e[type=zombie,tag=thusk]'); await R('effect clear ThuA');
  // Khổng lồ dưới trần thấp
  a = await P('ThuA'); const top = `${Math.floor(a[0])} ${Math.floor(a[1]) + 2} ${Math.floor(a[2])}`;
  await R(`setblock ${top} minecraft:glass`); await hold(pp('khong_lo', 3)); await use();
  A.log('Khổng lồ III dưới trần cao 2 khối: scale', (await A.attr('minecraft:scale')).replace(/.*is /, ''), '(mong 1 = từ chối)'); await R(`setblock ${top} minecraft:air`);
  await R('attribute ThuA minecraft:scale modifier remove duckymomocore:phuphep_kichthuoc');
  // Hộ giá tới ThuB (cùng thị trấn)
  await R('lp user ThuB permission set grim.exempt true');
  let B = await go(await h.start('ThuB'), X + 10, Z); A = await go(A, X, Z);
  for (let i = 0; i < 8 && !(A.bot.players.ThuB && A.bot.players.ThuB.entity); i++) await A.sleep(1000);
  await hold(pp('ho_gia', 2)); { const e = A.bot.players.ThuB && A.bot.players.ThuB.entity; if (e) await A.bot.lookAt(e.position.offset(0, 1.4, 0), true); await A.sleep(300); }
  a = await P('ThuA'); const pb = await P('ThuB'); await use(); await A.sleep(2200);
  A.log('Hộ giá II tới ThuB: ThuA cách ThuB', d(a, pb), '->', d(await P('ThuA'), pb), '| tim vàng ThuA', num(await R('data get entity ThuA AbsorptionAmount')), 'ThuB', num(await R('data get entity ThuB AbsorptionAmount')), '(mong 4)');
  await R('minecraft:clear ThuA'); await B.end(); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
