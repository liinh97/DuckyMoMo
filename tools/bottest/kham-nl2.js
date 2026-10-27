// Hao năng lượng bản 27/10: giáp không hao vì xương rồng / cháy, hao khi quái đánh; vũ khí -1 mỗi con hạ, -10 khi hạ "trùm" (tên 『N』).
const { start, moveTo } = require('./h');
const A = 'ThuKhamNL';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  let a = await start(A); let R = x => a.rc.cmd(x);
  await R(`minecraft:clear ${A}`); await R(`gamemode survival ${A}`);
  await R(`dmm laydo ht_giay_0 1 ${A}`); await R(`dmm laydo ht_kiem_0 1 ${A}`); await R(`tinhluyen give sat 2 ${A}`); await a.sleep(800);
  for (const re of [/boots/, /sword/]) { await a.bot.equip(a.bot.inventory.items().find(i => re.test(i.name)), 'hand'); await a.sleep(300);
    let w = await a.openMenu('/thoren kham'); w = await a.click(find(a, w, /Ô trống/)); w = await a.click(find(a, w, /^Sắt$/)); if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow); await a.sleep(300); }
  await a.bot.equip(a.bot.inventory.items().find(i => /boots/.test(i.name)), 'feet'); await a.sleep(500);
  a = await moveTo(a, 12280, -2236); R = x => a.rc.cmd(x); await a.sleep(6000);
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(500);
  const feet = async () => (await R(`data get entity ${A} equipment.feet.components."minecraft:custom_data".PublicBukkitValues."duckymomocore:dmm_kham"`)).replace(/.*data: /, '');
  const hand = async () => (await R(`data get entity ${A} SelectedItem.components."minecraft:custom_data".PublicBukkitValues."duckymomocore:dmm_kham"`)).replace(/.*data: /, '');
  const flush = async () => { const w = await a.openMenu('/thoren kham'); if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow); await a.sleep(300); };
  a.log('đầu: giày', await feet(), '| kiếm', await hand());
  for (let i = 0; i < 5; i++) { await R(`effect give ${A} minecraft:instant_health 1 5`); await R(`damage ${A} 4 minecraft:cactus`); await a.sleep(500); }
  await R(`effect give ${A} minecraft:poison 4 1`); await a.sleep(4500);
  await flush(); a.log('sau 5 lần xương rồng + 4 giây trúng độc: giày', await feet(), '(phải giữ nguyên)');
  await R(`execute at ${A} run summon minecraft:zombie ~ ~ ~3 {NoAI:1b,Silent:1b,Tags:["thukham2"]}`); await a.sleep(800);
  for (let i = 0; i < 4; i++) { await R(`effect give ${A} minecraft:instant_health 1 5`); await R(`damage ${A} 4 minecraft:mob_attack by @e[tag=thukham2,limit=1]`); await a.sleep(600); }
  await flush(); a.log('sau 4 đòn zombie: giày', await feet(), '(phải -4)');
  await R(`kill @e[tag=thukham2]`);
  // hạ 3 quái thường + 1 "trùm"
  for (const nm of ['', '', '', '{CustomName:\'"『5』 Trùm thử"\'}'.replace('{', '').replace('}', '')]) {
    await R(`execute at ${A} run summon minecraft:zombie ~ ~ ~2 {NoAI:1b,Silent:1b,Health:1f,Tags:["thukham3"]${nm ? ',' + nm : ''}}`);
    await a.sleep(500); a.log('hạ:', await R(`damage @e[tag=thukham3,limit=1] 50 minecraft:player_attack by ${A}`));
    await R(`kill @e[tag=thukham3]`); await a.sleep(300);
  }
  a.log('sau hạ 3 quái + 1 trùm: kiếm', await hand(), '(phải -13)');
  await R(`minecraft:clear ${A}`); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
