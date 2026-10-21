// /xemdo ở banghoi-1: F trong menu (/kit), trade dân làng bán món mô tả dài (chọn món -> nhắc [Xem đủ], F ở ô kết quả, /xemdo trade),
// /xemdo <tên> xem món bot khác cầm.
const h = require('./h');
(async () => {
  let A = await h.start('ThuA'); const B = await h.start('ThuB');
  const R = c => A.rc.cmd(c);
  const pressF = (win, slot) => A.bot._client.write('window_click', { windowId: win.id, stateId: win.stateId ?? 0, slot, mouseButton: 40, mode: 2, changedSlots: [], cursorItem: { itemCount: 0 } });
  const lore = Array.from({ length: 25 }, (_, i) => `'{"text":"Dòng mô tả ${i + 1}","italic":false}'`).join(',');
  const longItem = `minecraft:diamond_chestplate[lore=[${lore}],enchantments={protection:4,blast_protection:3}]`;
  // /xemdo <tên>
  await R('minecraft:clear ThuB'); await R(`minecraft:give ThuB ${longItem}`); await B.sleep(1000);
  await B.bot.equip(B.bot.inventory.items().find(i => i.name === 'diamond_chestplate'), 'hand'); await A.sleep(800);
  let n0 = A.chat.length; A.bot.chat('/xemdo ThuB'); await A.sleep(1500);
  A.log('/xemdo ThuB:', A.chat.slice(n0).length, 'dòng |', A.chat.slice(n0, n0 + 2).join(' | '));
  // F trong menu /kit
  await A.sleep(1000);
  let w = await A.openMenu('/kit'); n0 = A.chat.length; pressF(w, 0); await A.sleep(1500);
  A.log('F trong /kit:', A.chat.slice(n0).length, 'dòng |', A.chat.slice(n0, n0 + 2).join(' | '), '| menu còn mở:', !!A.bot.currentWindow);
  A.bot.closeWindow(A.bot.currentWindow || w); await A.sleep(1000);
  // trade dân làng
  A = await h.moveTo(A, 12320, -2610); await A.sleep(5000);
  const R2 = c => A.rc.cmd(c);
  const pressF2 = (win, slot) => A.bot._client.write('window_click', { windowId: win.id, stateId: win.stateId ?? 0, slot, mouseButton: 40, mode: 2, changedSlots: [], cursorItem: { itemCount: 0 } });
  await R2('minecraft:kill @e[type=villager,tag=thuxem]'); await R2('minecraft:clear ThuA'); await R2('minecraft:give ThuA minecraft:emerald 1'); await A.sleep(800);
  await R2(`minecraft:execute at ThuA run summon villager ~ ~ ~ {NoAI:1b,Tags:["thuxem"],VillagerData:{profession:"minecraft:armorer",level:2,type:"minecraft:plains"},Offers:{Recipes:[{buy:{id:"minecraft:emerald",count:1},sell:{id:"minecraft:diamond_chestplate",count:1,components:{"minecraft:lore":[${Array.from({ length: 25 }, (_, i) => `{text:"Dòng mô tả ${i + 1}",italic:false}`).join(',')}]}},maxUses:99}]}}`);
  await A.sleep(1500);
  const v = A.bot.nearestEntity(e => e.name === 'villager'); if (!v) { A.log('không thấy dân làng'); } else {
    await A.bot.lookAt(v.position.offset(0, 1.5, 0), true); await A.sleep(500); let vw = null; A.bot._client.on('open_window', p => A.log('gói open_window:', JSON.stringify(p).slice(0, 120))); A.bot._client.on('trade_list', p => A.log('gói trade_list:', (p.trades || []).length, 'món'));
    A.bot.on('windowOpen', w => { vw = w; });
    A.bot.activateEntityAt(v, v.position.offset(0, 1, 0)); await A.sleep(1500); if (!vw) { A.bot.activateEntity(v); await A.sleep(2500); }
    if (!vw) A.log('mở trade lỗi: không có cửa sổ');
    if (vw) {
      A.log('trade mở, số món:', (vw.trades || []).length);
      n0 = A.chat.length; A.bot._client.write('select_trade', { slot: 0 }); await A.sleep(1500);
      A.log('chọn trade:', A.chat.slice(n0).join(' | ').slice(0, 200));
      n0 = A.chat.length; pressF2(vw, 2); await A.sleep(1500);
      A.log('F ô kết quả:', A.chat.slice(n0).length, 'dòng |', A.chat.slice(n0, n0 + 2).join(' | '));
      A.bot.closeWindow(vw); await A.sleep(1200);
      n0 = A.chat.length; A.bot.chat('/xemdo trade'); await A.sleep(1500);
      A.log('/xemdo trade:', A.chat.slice(n0).length, 'dòng');
    }
  }
  await R2('minecraft:kill @e[type=villager,tag=thuxem]'); await R2('minecraft:clear ThuB');
  await A.end(); await B.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
