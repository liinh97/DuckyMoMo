// Đo hào quang mờ dần: A khảm 1 Sắt vào kiếm (đầy 5.000), B đếm chấm + cỡ 6 giây ở 100%; A hạ "trùm" (tên 『N』, -10/con) để rút về 50%, 25%, 10%.
const { start, moveTo } = require('./h');
const A = 'ThuHaoA', B = 'ThuHaoB';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  let a = await start(A); let R = x => a.rc.cmd(x);
  await R(`minecraft:clear ${A}`); await R(`dmm laydo ht_kiem_0 1 ${A}`); await R(`tinhluyen give sat 1 ${A}`); await a.sleep(800);
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(300);
  let w = await a.openMenu('/thoren kham'); w = await a.click(find(a, w, /Ô trống/)); w = await a.click(find(a, w, /^Sắt$/)); a.log('khảm:', a.chat.slice(-1)[0]);
  if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  a = await moveTo(a, 12280, -2236); R = x => a.rc.cmd(x); await a.sleep(1000);
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(800);
  let b = await start(B); b = await moveTo(b, 12280, -2236); await b.sleep(1500);
  const tagOf = async () => (await R(`data get entity ${A} SelectedItem.components."minecraft:custom_data".PublicBukkitValues."duckymomocore:dmm_kham"`)).match(/"[a-z]+:(\d):(\d+):(\d+)/);
  const measure = async () => {
    const tg = await tagOf(); const t = +tg[1], e = +tg[3], f = e / 5000;
    let n = 0, sizes = [];
    const h = p => { const d = p.particle && p.particle.data; if (p.particle && p.particle.type === 'dust') { n += p.amount ?? 1; if (d && typeof d.scale === 'number') sizes.push(d.scale); } };
    b.bot._client.on('world_particles', h); await b.sleep(6000); b.bot._client.removeListener('world_particles', h);
    const avg = sizes.length ? (sizes.reduce((x, y) => x + y, 0) / sizes.length).toFixed(2) : '-';
    b.log(`năng lượng ${e} (${Math.round(100 * f)}%): chấm ${n} (kỳ vọng ~${Math.round(30 * (t + 1) * (0.25 + 0.75 * f))}) | cỡ ${avg} (kỳ vọng ${((0.55 + 0.3 * t) * (0.45 + 0.55 * f)).toFixed(2)}) | phẩm ${t}`);
  };
  const killBoss = async k => { for (let i = 0; i < k; i++) {
      await R(`execute at ${A} run summon minecraft:zombie ~3 ~ ~ {NoAI:1b,Silent:1b,Health:1f,Tags:["haodo"],CustomName:'"『5』 Trùm thử"'}`);
      await R(`damage @e[tag=haodo,limit=1,sort=nearest] 50 minecraft:player_attack by ${A}`); }
    await R(`kill @e[type=minecraft:item,distance=..8]`); await R(`kill @e[tag=haodo]`); };
  await measure();
  await killBoss(250); await measure();
  await killBoss(125); await measure();
  await killBoss(75); await measure();
  await R(`minecraft:clear ${A}`); await R(`xp set ${A} 0 levels`);
  await b.end(); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
