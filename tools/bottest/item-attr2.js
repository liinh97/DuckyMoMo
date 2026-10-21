// Thư viện đồ › Chỉ số gốc: món thu_chiso (kiếm kim cương, sát thương 12, máu +4). Kiểm thành phần trên đồ, máu tối đa khi cầm,
// sát thương thật lên zombie đứng yên (máu 200) so với kiếm kim cương thường (OCM 8).
const h = require('./h');
(async () => {
  let A = await h.start('ThuA');
  const R = c => A.rc.cmd(c);
  A = await h.moveTo(A, 12320, -2610); await A.sleep(5000);
  const hit = async (label, give) => {
    await R('minecraft:clear ThuA'); A.log('đưa:', (await R(give)).trim()); await A.sleep(1000);
    const sw = A.bot.inventory.items().find(i => i.name === 'diamond_sword'); await A.bot.equip(sw, 'hand'); await A.sleep(1500);
    A.log(label, 'chỉ số trên đồ:', (await R('data get entity ThuA SelectedItem.components."minecraft:attribute_modifiers"')).replace(/.*data: /, '').slice(0, 400));
    A.log(label, 'máu tối đa khi cầm:', (await R('attribute ThuA minecraft:max_health get')).replace(/.*is /, ''));
    await R('minecraft:kill @e[type=zombie,tag=thuattr]');
    await R('minecraft:execute at ThuA run summon zombie ~ ~ ~1.5 {NoAI:1b,Tags:["thuattr"],PersistenceRequired:1b,attributes:[{id:"minecraft:max_health",base:200}],Health:200f}');
    await A.sleep(1500);
    const z = A.bot.nearestEntity(e => e.name === 'zombie'); if (!z) return A.log('không thấy zombie');
    await A.bot.lookAt(z.position.offset(0, 1, 0), true); await A.sleep(600); A.bot.attack(z); await A.sleep(1200);
    A.log(label, 'zombie còn:', (await R('data get entity @e[type=zombie,tag=thuattr,limit=1] Health')).replace(/.*data: /, ''));
  };
  await hit('kiếm thường', 'minecraft:give ThuA minecraft:diamond_sword');
  await hit('thu_chiso', 'dmm laydo thu_chiso ThuA 1');
  await R('minecraft:kill @e[type=zombie,tag=thuattr]'); await R('minecraft:clear ThuA');
  await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
