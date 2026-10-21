// s5 nhân đồ qua Chợ đen (zAuctionHouse): A treo 1 kim cương giá 100; (1) A gỡ đúng lúc B bấm mua; (2) B và C bấm mua cùng lúc.
// Đếm kim cương + xu của cả 3 (kim cương phải đúng 1 cái, xu không tự sinh ra). thêm đối số do (run.sh s5-choden.js do) thì chỉ in menu để dò ô.
const { start } = require('./h');
const A = 'ThuCA', B = 'ThuCB', C = 'ThuCC';
const names = (t, w) => w ? w.slots.slice(0, w.inventoryStart).map((it, i) => it && `${i}:${t.itemText(it).name}`).filter(Boolean).join(', ') : '(không mở)';
(async () => {
  const a = await start(A), b = await start(B), c = await start(C);
  const R = x => a.rc.cmd(x);
  const cnt = async n => { const m = (await R(`minecraft:clear ${n} minecraft:diamond 0`)).match(/Found (\d+)/); return m ? +m[1] : 0; };
  const bal = async n => +((await R(`bal ${n}`)).replace(/[^\d.]/g, '') || 0);
  const state = async () => { let s = ''; for (const n of [A, B, C]) s += `${n}: ${await cnt(n)} kc ${await bal(n)} xu | `; return s; };
  for (const n of [A, B, C]) { await R(`eco set ${n} 1000`); await R(`minecraft:clear ${n}`); await R(`gamemode survival ${n}`); }
  const list = async () => {
    await R(`minecraft:item replace entity ${A} weapon.mainhand with minecraft:diamond 1`); await a.sleep(400);
    a.log('treo:', (await a.say('/choden ban 100', 2000)).join(' | ').slice(0, 200));
  };
  // mở trang mua của t, tìm ô kim cương
  const buyMenu = async t => { const w = await t.openMenu('/choden mua'); const i = w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && it.name === 'diamond') : -1; return [w, i]; };
  const myMenu = async () => { const w = await a.openMenu('/choden cuatoi'); const i = w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && it.name === 'diamond') : -1; return [w, i]; };
  if (process.argv.includes('do')) {
    await list();
    let [w, i] = await buyMenu(b); b.log('trang mua:', names(b, w), '| ô kc', i);
    if (i >= 0) { const w2 = await b.click(i); b.log('bấm kc ->', names(b, w2), b.chat.slice(-3).join(' | ')); if (b.bot.currentWindow) b.bot.closeWindow(b.bot.currentWindow); }
    [w, i] = await myMenu(); a.log('cuatoi:', names(a, w), '| ô kc', i);
    if (i >= 0) { const w2 = await a.click(i); a.log('bấm kc ->', names(a, w2), a.chat.slice(-3).join(' | ')); if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow); }
    a.log('cuối:', await state());
    process.exit(0);
  }
  // (1) A gỡ đúng lúc B bấm "Mua món này"
  const confirm = async t => { const [w, i] = await buyMenu(t); if (i < 0) return null; return t.click(i); };
  const race = async (label, fns) => {
    await Promise.all(fns.map(f => f()));
    await a.sleep(2500);
    for (const t of [a, b, c]) if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
    await a.sleep(800);
    a.log(label, '->', await state());
    a.log('   tin:', [a, b, c].map(t => t.chat.slice(-3).filter(l => /Chợ đen/.test(l)).join(' / ')).join(' || ').slice(0, 400));
  };
  await list(); a.log('đầu:', await state());
  let wb = await confirm(b); const [wa, ia] = await myMenu();
  a.log('B ở menu xác nhận:', !!wb, '| A thấy món ở ô', ia);
  await race('s5 (1) A gỡ + B mua cùng lúc', [() => b.bot.clickWindow(0, 0, 0).catch(() => {}), () => a.bot.clickWindow(ia, 0, 0).catch(() => {})]);
  // dọn: ai có kim cương thì giữ; treo lại từ A
  for (const n of [A, B, C]) await R(`minecraft:clear ${n} minecraft:diamond`);
  await list(); a.log('đầu (2):', await state());
  const wc = await confirm(c); await c.sleep(1500); wb = await confirm(b);
  a.log('B, C ở menu xác nhận:', !!wb, !!wc, '| menu C:', names(c, c.bot.currentWindow).slice(0, 120));
  await race('s5 (2) B và C mua cùng lúc', [() => b.bot.clickWindow(0, 0, 0).catch(() => {}), () => c.bot.clickWindow(0, 0, 0).catch(() => {})]);
  a.log('A nhantien:', (await a.say('/choden nhantien', 1500)).join(' | ').slice(0, 200), '->', await state());
  for (const n of [A, B, C]) await R(`minecraft:clear ${n} minecraft:diamond`);
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
