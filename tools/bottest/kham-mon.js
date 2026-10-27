// Chậm mòn (Đồng): KIEU=kham (giày 3 Đồng) hoặc KIEU=tron (giày trơn); 120 đòn generic 16, đọc độ mòn giày từ server.
const { start, moveTo } = require('./h');
const A = 'ThuKhamM', KHAM = process.env.KIEU === 'kham', SO = +process.env.SO || 120;
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  let a = await start(A);
  let R = x => a.rc.cmd(x);
  await R(`minecraft:clear ${A}`); await R(`gamemode survival ${A}`);
  await R(`dmm laydo ht_giay_0 1 ${A}`); await R(`tinhluyen give dong 3 ${A}`); await a.sleep(800);
  const it = a.bot.inventory.items().find(i => /boots/.test(i.name));
  if (KHAM) {
    await a.bot.equip(it, 'hand'); await a.sleep(300);
    let w = await a.openMenu('/thoren kham');
    for (let k = 0; k < 3; k++) { w = await a.click(find(a, w, /Ô trống/)); w = await a.click(find(a, w, /^Đồng$/)); }
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  }
  await a.sleep(1000);
  await a.bot.equip(a.bot.inventory.items().find(i => /boots/.test(i.name)), 'feet').catch(e => a.log('equip lỗi', e.message)); await a.sleep(1000);
  a.log('chân trước khi đi:', await R(`data get entity ${A} equipment.feet.id`));
  a = await moveTo(a, 12280, -2236); R = x => a.rc.cmd(x); await a.sleep(6000);
  const feet = async () => R(`data get entity ${A} equipment.feet`);
  a.log('ngọc giày đang mang:', await R(`data get entity ${A} equipment.feet.components."minecraft:custom_data".PublicBukkitValues."duckymomocore:dmm_kham"`));
  const dmg = async () => { const m = (await R(`data get entity ${A} equipment.feet.components."minecraft:damage"`)).match(/data: (\d+)/); return m ? +m[1] : 0; };
  const d0 = await dmg();
  for (let i = 0; i < SO; i++) { await R(`effect give ${A} minecraft:instant_health 1 10`); await R(`damage ${A} 16 minecraft:cactus`); await a.sleep(520); }
  await a.sleep(500);
  a.log(KHAM ? 'giày 3 Đồng (chậm mòn 45%):' : 'giày trơn:', 'mòn thêm', (await dmg()) - d0, 'điểm sau', SO, 'đòn (cactus 16)');
  await R(`minecraft:clear ${A}`);
  await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
