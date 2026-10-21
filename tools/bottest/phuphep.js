// Phù phép riêng (SkillEnchants) + tốc đánh (AttackSpeed): mỗi phù phép 1 món gắn dấu PDC bằng /give, bot ThuA (grim.exempt) bấm chuột phải.
const h = require('./h');
const pp = (id, lv, item = 'minecraft:stick') => `${item}[custom_data={PublicBukkitValues:{"duckymomocore:phuphep_${id}":${lv}}}]`;
(async () => {
  let A = await h.start('ThuA');
  A = await h.moveTo(A, 12320, -2610); await A.sleep(5000);
  const R = c => A.rc.cmd(c);
  const pos = async () => (await R('data get entity ThuA Pos')).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  const hp = async () => parseFloat((await R('data get entity ThuA Health')).replace(/.*data: /, ''));
  const hold = async (give) => { await R('minecraft:clear ThuA'); await R(`minecraft:give ThuA ${give}`); await A.sleep(900); await A.bot.equip(A.bot.inventory.items()[0], 'hand'); await A.sleep(500); };
  const use = async () => { const n0 = A.chat.length; A.bot.activateItem(); await A.sleep(300); A.bot.deactivateItem(); return n0; };
  const zombie = async (dx, dz) => { await R('minecraft:kill @e[type=zombie,tag=thupp]'); await R(`minecraft:execute at ThuA run summon zombie ~${dx} ~ ~${dz} {NoAI:1b,Tags:["thupp"],PersistenceRequired:1b,attributes:[{id:"minecraft:max_health",base:200}],Health:200f}`); await A.sleep(1200); };
  const zhp = async () => parseFloat((await R('data get entity @e[type=zombie,tag=thupp,limit=1] Health')).replace(/.*data: /, ''));
  const d = (a, b) => Math.hypot(a[0] - b[0], a[2] - b[2]).toFixed(1);
  // Lướt
  await hold(pp('luot', 2)); let p0 = await pos(); await use(); await A.sleep(1200); A.log('Lướt II: đi được', d(p0, await pos()), 'khối');
  // Dịch chuyển
  await hold(pp('dich_chuyen', 2)); p0 = await pos(); await use(); await A.sleep(800); A.log('Dịch chuyển II:', d(p0, await pos()), 'khối (mong 6, trừ khi có tường)');
  // Hồi máu
  await R('minecraft:damage ThuA 10 minecraft:generic'); await A.sleep(500); let h0 = await hp();
  await hold(pp('hoi_mau', 2)); await use(); await A.sleep(600); A.log('Hồi máu II: máu', h0, '->', await hp(), '(mong +4)');
  // Bay lên
  await hold(pp('bay', 1)); p0 = await pos(); await use(); await A.sleep(1500); const p1 = await pos();
  A.log('Bay lên I: cao thêm', (p1[1] - p0[1]).toFixed(1), 'khối | hiệu ứng:', (await R('data get entity ThuA active_effects')).replace(/.*data: /, '').slice(0, 160));
  await A.sleep(4000);
  // Cầu lửa
  await zombie(0, 4); await hold(pp('cau_lua', 2));
  const z = A.bot.nearestEntity(e => e.name === 'zombie'); if (z) await A.bot.lookAt(z.position.offset(0, 1.2, 0), true); await A.sleep(500);
  let n0 = await use(); await A.sleep(2000); A.log('Cầu lửa II: zombie 200 ->', await zhp(), '(mong mất 7)');
  // Dậm đất
  await zombie(1.5, 0); await hold(pp('dam_dat', 2)); await use(); await A.sleep(1200); A.log('Dậm đất II: zombie 200 ->', await zhp(), '(mong mất 4, trừ giáp)');
  // Huyết chiến + đánh
  await R('effect give ThuA minecraft:instant_health 1 5'); await A.sleep(800); h0 = await hp();
  await hold(pp('cuong_no', 2, 'minecraft:diamond_sword')); n0 = await use(); await A.sleep(600);
  A.log('Huyết chiến II: máu', h0, '->', await hp(), '| chat:', A.chat.slice(n0).filter(x => /Huyết|tim/.test(x)).join(' | '));
  await zombie(0, 1.5); const z2 = A.bot.nearestEntity(e => e.name === 'zombie'); await A.bot.lookAt(z2.position.offset(0, 1, 0), true); await A.sleep(600);
  A.bot.attack(z2); await A.sleep(1000); A.log('  đánh kiếm kim cương khi Huyết chiến: zombie 200 ->', await zhp(), '(thường mất 7,36; +30% ≈ 9,57)');
  // Phóng bay (elytra)
  await R('minecraft:clear ThuA'); await R(`minecraft:item replace entity ThuA armor.chest with ${pp('phong_bay', 2, 'minecraft:elytra')}`); await R('minecraft:give ThuA minecraft:stick');
  await A.sleep(900); await A.bot.equip(A.bot.inventory.items().find(i => i.name === 'stick'), 'hand'); await A.sleep(400);
  A.bot.look(A.bot.entity.yaw, -0.3, true); await A.sleep(300);
  p0 = await pos(); await use(); await A.sleep(1200); const p2 = await pos();
  A.log('Phóng bay II: cao thêm', (p2[1] - p0[1]).toFixed(1), ', đi xa', d(p0, p2), '| đang lượn:', (await R('data get entity ThuA FallFlying')).replace(/.*data: /, ''));
  await A.sleep(5000);
  // Tốc đánh 1 đòn/giây: 2 đòn cách 0,3 giây và 2 đòn cách 1,2 giây
  await R('minecraft:clear ThuA'); await hold('minecraft:diamond_sword[custom_data={PublicBukkitValues:{"duckymomocore:thuvien_tocdanh":1.0d}}]');
  for (const gap of [300, 1200]) {
    await zombie(0, 1.5); const z3 = A.bot.nearestEntity(e => e.name === 'zombie'); await A.bot.lookAt(z3.position.offset(0, 1, 0), true); await A.sleep(1500);
    A.bot.attack(z3); await A.sleep(gap); const mid = await zhp(); A.bot.attack(z3); await A.sleep(800);
    A.log(`Tốc đánh 1/giây, 2 đòn cách ${gap} ms: đòn 1 mất`, (200 - mid).toFixed(2), '| đòn 2 mất', (mid - await zhp()).toFixed(2));
  }
  await R('minecraft:kill @e[type=zombie,tag=thupp]'); await R('minecraft:clear ThuA');
  await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
