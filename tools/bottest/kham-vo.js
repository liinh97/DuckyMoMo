// Cạn năng lượng (config tạm 20): giày nhận 25 đòn -> lúc ghi (mở menu) phải nhắc / báo vỡ, ô trống lại.
const { start, moveTo } = require('./h');
const A = 'ThuKhamNL';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  let a = await start(A); let R = x => a.rc.cmd(x);
  await R(`minecraft:clear ${A}`); await R(`gamemode survival ${A}`);
  await R(`dmm laydo ht_giay_0 1 ${A}`); await R(`tinhluyen give dong 1 ${A}`); await a.sleep(800);
  await a.bot.equip(a.bot.inventory.items().find(i => /boots/.test(i.name)), 'hand'); await a.sleep(300);
  let w = await a.openMenu('/thoren kham'); w = await a.click(find(a, w, /Ô trống/)); w = await a.click(find(a, w, /^Đồng$/));
  if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  await a.bot.equip(a.bot.inventory.items().find(i => /boots/.test(i.name)), 'feet'); await a.sleep(500);
  a = await moveTo(a, 12280, -2236); R = x => a.rc.cmd(x); await a.sleep(6000);
  const n0 = a.chat.length; const bars = []; a.bot.on('actionBar', m => bars.push(JSON.stringify(m)));
  for (let i = 0; i < 25; i++) { await R(`effect give ${A} minecraft:instant_health 1 5`); await R(`damage ${A} 6 minecraft:cactus`); await a.sleep(500); }
  w = await a.openMenu('/thoren kham');   // ghi phần dồn
  await a.sleep(800);
  a.log('tin:', a.since(n0).filter(l => /Ngọc|cạn/.test(l)).join(' | '));
  if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  a.log('ngọc trên giày:', await R(`data get entity ${A} equipment.feet.components."minecraft:custom_data".PublicBukkitValues."duckymomocore:dmm_kham"`));
  await R(`minecraft:clear ${A}`);
  await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
