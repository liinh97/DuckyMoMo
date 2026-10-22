// x8 nạp Vàng bằng QR trong game: cầm kiếm sắt, /rank mua -> chọn 20k -> tấm bản đồ QR thay món trên tay; /rank huyqr (= nút [Huỷ])
// -> kiếm về đúng ô; lần 2 huỷ bằng Q (vứt) cũng trả kiếm. KHÔNG bấm [Tôi đã chuyển khoản]. Quét QR bằng app ngân hàng: CẦN NGƯỜI.
const { start } = require('./h');
const N = 'ThuVang';
(async () => {
  const t = await start(N);
  const R = c => t.rc.cmd(c);
  const hand = async () => (await R(`data get entity ${N} SelectedItem`)).replace(/.*data: /, '').slice(0, 90);
  const names = w => w ? w.slots.slice(0, w.inventoryStart).map((it, i) => it && t.itemText(it).name.trim() && `${i}:${t.itemText(it).name}`).filter(Boolean).join(', ') : '(không mở)';
  await R(`minecraft:clear ${N}`); await R(`minecraft:item replace entity ${N} weapon.mainhand with minecraft:iron_sword 1`); await t.sleep(600);
  const qr = async label => {
    let w = await t.openMenu('/rank mua');
    t.log(label, 'menu:', names(w).slice(0, 300));
    let i = w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && /Nạp|QR|chuyển khoản/i.test(t.itemText(it).name + t.itemText(it).lore.join(' '))) : -1;
    if (i >= 0) { w = await t.click(i); t.log('   sau bấm nạp:', names(w).slice(0, 300)); }
    i = w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && /20[.,]?000|20k/i.test(t.itemText(it).name + ' ' + t.itemText(it).lore.join(' '))) : -1;
    const n0 = t.chat.length;
    if (i >= 0) { await t.click(i); }
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
    await t.sleep(3500);
    t.log(label, 'chọn 20k (ô', i, '):', t.chat.slice(n0).join(' | ').slice(0, 400));
    t.log('   tay:', await hand());
  };
  await qr('x8 lần 1');
  t.log('x8 /rank huyqr:', (await t.say('/rank huyqr', 1500)).join(' | ').slice(0, 200), '| tay:', await hand());
  await qr('x8 lần 2');
  t.bot.tossStack(t.bot.heldItem).catch(() => {}); await t.sleep(1500);
  t.log('x8 bấm Q (vứt QR):', t.chat.slice(-2).join(' | ').slice(0, 200), '| tay:', await hand());
  await R(`minecraft:clear ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
