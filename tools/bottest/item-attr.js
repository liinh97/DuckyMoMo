// Thư viện đồ, chỉ số gốc: OldCombatMechanics (old-tool-damage) có dùng attribute_modifiers gắn trên đồ không?
// Bot ThuA (có grim.exempt) đánh thật zombie đứng yên bằng kiếm kim cương thường và kiếm sửa sát thương = 20, so máu mất.
const h = require('./h');
(async () => {
  let A = await h.start('ThuA');
  const R = c => A.rc.cmd(c);
  A = await h.moveTo(A, 12320, -2610); await A.sleep(5000);
  const test = async (label, give) => {
    await R('minecraft:clear ThuA'); await R(give); await A.sleep(800);
    const sw = A.bot.inventory.items().find(i => i.name === 'diamond_sword'); await A.bot.equip(sw, 'hand'); await A.sleep(1200);
    await R('minecraft:kill @e[type=zombie,tag=thuattr]');
    await R('minecraft:execute at ThuA run summon zombie ~ ~ ~1.5 {NoAI:1b,Tags:["thuattr"],PersistenceRequired:1b,attributes:[{id:"minecraft:max_health",base:200}],Health:200f}');
    await A.sleep(1500);
    const z = A.bot.nearestEntity(e => e.name === 'zombie'); if (!z) return A.log(label, 'không thấy zombie');
    await A.bot.lookAt(z.position.offset(0, 1, 0), true); await A.sleep(600);
    A.bot.attack(z); await A.sleep(1200);
    A.log(label, '->', (await R('data get entity @e[type=zombie,tag=thuattr,limit=1] Health')).replace(/.*data: /, ''), '(máu gốc 200)');
  };
  await test('kiếm kim cương thường', 'minecraft:give ThuA minecraft:diamond_sword');
  await test('kiếm sửa sát thương 20', 'minecraft:give ThuA minecraft:diamond_sword[attribute_modifiers=[{type:"minecraft:attack_damage",amount:19,id:"duckymomo:thu",operation:"add_value",slot:"mainhand"}]]');
  await R('minecraft:kill @e[type=zombie,tag=thuattr]'); await R('minecraft:clear ThuA');
  await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
