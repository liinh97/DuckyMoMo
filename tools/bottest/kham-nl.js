// Phẩm + năng lượng (config tạm nang-luong 20): khảm ra phẩm, ngọc cũ = Thường 100%, vũ khí hao theo đòn đánh rồi vỡ, giáp hao theo đòn
// nhận (ghi khi mở menu), nạp đầy, tháo giữ phẩm + năng lượng rồi khảm lại đúng phẩm.
const { start, moveTo } = require('./h');
const A = 'ThuKhamNL';
const names = (t, w) => w ? w.slots.slice(0, w.inventoryStart).filter(Boolean).map(i => t.itemText(i)).filter(x => !/Pane/.test(x.name)) : [];
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  let a = await start(A); let R = x => a.rc.cmd(x);
  await R(`minecraft:clear ${A}`); await R(`gamemode survival ${A}`); await R(`eco set ${A} 20000`);
  await R(`dmm laydo ht_kiem_0 1 ${A}`); await R(`dmm laydo ht_giay_0 1 ${A}`); await R(`tinhluyen give sat 6 ${A}`); await a.sleep(800);
  const kham = async (re) => { await a.bot.equip(a.bot.inventory.items().find(i => re.test(i.name)), 'hand'); await a.sleep(300);
    let w = await a.openMenu('/thoren kham'); w = await a.click(find(a, w, /Ô trống/)); w = await a.click(find(a, w, /^Sắt$/)); a.log('khảm:', a.chat.slice(-1)[0]);
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow); await a.sleep(300); };
  await kham(/boots/); await kham(/sword/);
  a.log('lore kiếm:', a.itemText(a.bot.heldItem).lore.filter(l => /◆|năng lượng/.test(l)).join(' / '));
  await a.bot.equip(a.bot.inventory.items().find(i => /boots/.test(i.name)), 'feet'); await a.sleep(500);
  a = await moveTo(a, 12280, -2236); R = x => a.rc.cmd(x); await a.sleep(6000);
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(500);
  // giáp: 8 đòn nhận
  for (let i = 0; i < 8; i++) { await R(`effect give ${A} minecraft:instant_health 1 5`); await R(`damage ${A} 6 minecraft:cactus`); await a.sleep(550); }
  // vũ khí: đánh husk tới khi ngọc vỡ
  await R(`execute at ${A} run summon minecraft:husk ~ ~ ~2 {NoAI:1b,Silent:1b,Health:1000f,attributes:[{id:"minecraft:max_health",base:1000}],Tags:["thukham"]}`);
  let mob = null; for (let i = 0; i < 20 && !mob; i++) { await a.sleep(300); mob = Object.values(a.bot.entities).find(e => e.name === 'husk'); }
  a.log('husk:', mob ? 'thấy' : 'KHÔNG thấy'); const n0 = a.chat.length; const bars = [];
  a.bot.on('actionBar', m => bars.push(String(m)));
  for (let i = 0; i < 30 && mob; i++) { await a.bot.lookAt(mob.position.offset(0, 1, 0)); a.bot.attack(mob); await a.sleep(600); }
  a.log('husk còn máu:', (await R(`data get entity @e[tag=thukham,limit=1] Health`)).replace(/.*data: /, ''), '| lore kiếm:', a.itemText(a.bot.heldItem).lore.filter(l => /◆|◇|năng lượng/.test(l)).join(' / '));
  a.log('khi đánh:', a.since(n0).filter(l => /Ngọc|năng lượng/.test(l)).join(' | '), '| thanh:', bars.filter(b => /cạn/.test(b)).slice(0, 1).join(''));
  await R(`kill @e[tag=thukham]`);
  // mở menu khảm cầm giày: ghi năng lượng giáp đã dồn (8 đòn -> còn 12/20 = 60%)
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand');
  await a.bot.unequip('feet'); await a.sleep(400);
  await a.bot.equip(a.bot.inventory.items().find(i => /boots/.test(i.name)), 'hand'); await a.sleep(400);
  let w = await a.openMenu('/thoren kham');
  a.log('giày sau 8 đòn:', names(a, w).filter(x => /◆/.test(x.name)).map(x => x.name + ' ' + x.lore.filter(l => /Năng lượng/.test(l)).join('')).join(''));
  // nạp đầy
  w = await a.click(find(a, w, /◆/)); w = await a.click(find(a, w, /Nạp đầy/));
  a.log('nạp:', a.chat.slice(-1)[0], '| bal', await R(`bal ${A}`));
  // tháo tới khi còn nguyên (50% vỡ), xem món trả về + khảm lại đúng phẩm
  for (let k = 0; k < 6; k++) {
    w = await a.openMenu('/thoren kham'); const wasName = names(a, w).find(x => /◆/.test(x.name)); if (!wasName) { await R(`tinhluyen give sat 1 ${A}`); await a.sleep(300); w = await a.click(find(a, w, /Ô trống/)); w = await a.click(find(a, w, /^Sắt$/)); if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow); continue; }
    w = await a.click(find(a, w, /◆/)); w = await a.click(find(a, w, /Tháo ngọc/));
    const msg = a.chat.slice(-1)[0]; a.log('tháo lần', k + 1, ':', wasName.name, '->', msg);
    if (/nguyên vẹn/.test(msg)) {
      w = await a.openMenu('/thoren kham'); w = await a.click(find(a, w, /Ô trống/));
      a.log('chọn ngọc:', names(a, w).filter(x => /Sắt/.test(x.name)).map(x => x.lore.filter(l => /Phẩm|Năng/.test(l)).join(' ')).join(' || '));
      const j = w.slots.slice(0, w.inventoryStart).findIndex(it => it && /Sắt/.test(a.itemText(it).name) && a.itemText(it).lore.join('').includes('đã tháo'));
      w = await a.click(j); a.log('khảm lại:', a.chat.slice(-1)[0]); break;
    }
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  }
  if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  await R(`minecraft:clear ${A}`); await R(`eco set ${A} 0`);
  await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
