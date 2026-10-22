// c7 đo tỉ lệ rơi Xu Quái: node c7-xuquai.js <x> <z> <số con> [nhãn]. ThuX1 hạ zombie thường (NoAI) bằng /damage ... by ThuX1,
// đếm gold_nugget (Xu Quái; zombie thường không rơi gold_nugget) bằng clear ... 0. So với Cài đặt tinh-luyen xu-quai.chance.
const { start, moveTo } = require('./h');
const A = 'ThuX1';
const [x, z, kills, nhan] = [+process.argv[2] || 13200, +process.argv[3] || -2900, +process.argv[4] || 200, process.argv[5] || ''];
(async () => {
  let a = await start(A);
  const R = c => a.rc.cmd(c);
  await R(`gamemode survival ${A}`); await R(`effect give ${A} minecraft:resistance 1200 4 true`);
  a = await moveTo(a, x, z);
  await R(`minecraft:clear ${A}`); await R(`execute at ${A} run minecraft:kill @e[type=item,distance=..16]`);
  let dead = 0;
  for (let i = 0; i < kills; i++) {
    await R(`execute at ${A} run summon zombie ~2 ~ ~ {NoAI:1b,Tags:["thuc7"],IsBaby:0b}`);
    if (/Applied/.test(await R(`execute at ${A} run damage @e[tag=thuc7,limit=1] 100 minecraft:player_attack by ${A}`))) dead++;
    if (i % 10 === 9) { await a.sleep(600); await R(`execute at ${A} run minecraft:tp @e[type=item,distance=..8] ${A}`); await a.sleep(1200); await R(`minecraft:clear ${A} minecraft:rotten_flesh`); }
  }
  await a.sleep(1500);
  const left = (await R(`execute at ${A} if entity @e[tag=thuc7,distance=..8]`)).trim();
  const xq = +(((await R(`minecraft:clear ${A} minecraft:gold_nugget 0`)).match(/Found (\d+)/) || [0, 0])[1]);
  a.log(`c7 ${nhan} (${x},${z}) hạ ${dead}/${kills} zombie: ${xq} Xu Quái = ${(100 * xq / dead).toFixed(1)}% | zombie còn sống: ${left}`);
  await R(`execute at ${A} run minecraft:kill @e[tag=thuc7]`); await R(`minecraft:clear ${A}`);
  await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
