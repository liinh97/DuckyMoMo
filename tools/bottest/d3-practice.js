// d3 Practice (practice-1, Duels): /duel, chọn kit trong menu, người kia đồng ý, trận đấu; /party; tiếng Việt.
const { start } = require('./h');
const A = 'ThuPA', B = 'ThuPB';
const menu = (t, w) => w ? w.slots.slice(0, w.inventoryStart).map((it, i) => it && `${i}:${t.itemText(it).name}`).filter(Boolean).join(', ') : '(không mở menu)';
(async () => {
  const a = await start(A, undefined, 'practice-1'), b = await start(B, undefined, 'practice-1');
  const R = c => a.rc.cmd(c);
  a.log('A vào practice:', a.chat.join(' | ').slice(0, 300));
  a.log('/duel help:', (await a.say('/duel', 1500)).join(' | ').slice(0, 500));
  // /duel B: menu chọn kit
  let w = await a.openMenu(`/duel ${B}`);
  a.log('menu /duel:', w && a.strip(JSON.stringify(w.title)).slice(0, 80), '|', menu(a, w));
  if (w) {
    const n0 = a.chat.length, m0 = b.chat.length;
    const find = (w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(a.itemText(it).name)) : -1;
    // Chọn bộ đồ -> (loại bộ đồ: Bộ đồ có sẵn) -> bấm bộ đồ đầu tiên -> về menu thách đấu -> GỬI
    w = await a.click(20); a.log('Chọn bộ đồ ->', menu(a, w));
    let i = find(w, /có sẵn/i); if (i >= 0) { w = await a.click(i); a.log('Bộ đồ có sẵn ->', menu(a, w)); }
    i = w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && a.itemText(it).name.trim() && !/trang|quay lại|chưa có/i.test(a.itemText(it).name)) : -1;
    if (i >= 0) { const kit = a.itemText(w.slots[i]); w = await a.click(i); a.log('chọn kit', kit.name, kit.lore.join(' / ').slice(0, 120), '->', menu(a, w)); }
    if (w) { const d = a.itemText(w.slots[4]); a.log('Chi tiết:', d && d.lore.join(' / ')); }
    i = find(w, /GỬI/); if (i >= 0) { w = await a.click(i); a.log('GỬI ->', w ? menu(a, w) : 'đóng menu'); }
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
    await a.sleep(1500);
    a.log('A chat:', a.chat.slice(n0).join(' | ').slice(0, 500));
    b.log('B chat (lời mời):', b.chat.slice(m0).join(' | ').slice(0, 500));
    b.log('B đồng ý:', (await b.say(`/duel accept ${A}`, 6000)).join(' | ').slice(0, 600));
    a.log('A trong trận:', a.chat.slice(n0).join(' | ').slice(-600));
    a.log('túi A:', a.bot.inventory.items().map(i => i.name + 'x' + i.count).join(', ').slice(0, 300));
    const k0 = a.chat.length, l0 = b.chat.length;
    a.log('B chết:', await R(`minecraft:kill ${B}`));
    await a.sleep(8000);
    a.log('KQ A:', a.chat.slice(k0).join(' | ').slice(0, 700));
    b.log('KQ B:', b.chat.slice(l0).join(' | ').slice(0, 700));
    a.log('/duel stats (mình):', (await a.say('/duel stats', 1500)).join(' | ').slice(0, 400));
  }
  a.log('/party:', (await a.say('/party', 1500)).join(' | ').slice(0, 500));
  a.log('/party B (mời):', (await a.say(`/party ${B}`, 1500)).join(' | ').slice(0, 300));
  b.log('B party accept:', (await b.say(`/party accept ${A}`, 1500)).join(' | ').slice(0, 300));
  a.log('/party list:', (await a.say('/party list', 1500)).join(' | ').slice(0, 300));
  a.log('/party disband:', (await a.say('/party disband', 1500)).join(' | ').slice(0, 300));
  w = await a.openMenu('/queue'); a.log('/queue:', a.strip(JSON.stringify(w && w.title)).slice(-60), menu(a, w), '| mô tả ô 10:', w && w.slots[10] && a.itemText(w.slots[10]).lore.join(' / ')); if (w) a.bot.closeWindow(w);
  await b.end(); await a.end();
})().catch(e => { console.log('FAIL', e); process.exit(1); });
