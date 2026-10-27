// Bán lưu ly (lapis): /sell trực tiếp, menu /cuahang (chuột phải), khung ở khu shop (chuột phải).
const { start, moveTo } = require('./h');
const N = process.env.BOTN || 'ThuLuuLy';
(async () => {
  let t = await start(N);
  const R = x => t.rc.cmd(x);
  await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`); await R(`eco set ${N} 0`);
  await R(`minecraft:give ${N} minecraft:lapis_lazuli 64`); await t.sleep(500);
  t.log('/sell lapis_lazuli 5 ->', (await t.say('/sell lapis_lazuli 5', 2000)).join(' | ').slice(0, 300));
  t.log('bal', await R(`bal ${N}`));
  const w = await t.openMenu('/cuahang');
  const names = w => w ? w.slots.slice(0, w.inventoryStart).map((it, i) => it && `${i}:${t.itemText(it).name}`).filter(Boolean).join(', ') : '(không mở)';
  t.log('menu:', names(w).slice(0, 400));
  if (w) {
    const i = w.slots.slice(0, w.inventoryStart).findIndex(it => it && it.name === 'diamond');
    const w2 = await t.click(i); 
    const j = w2.slots.slice(0, w2.inventoryStart).findIndex(it => it && it.name === 'lapis_lazuli');
    t.log('ô lapis', j, j >= 0 ? JSON.stringify(t.itemText(w2.slots[j])).slice(0, 400) : '');
    if (j >= 0) { const n = t.chat.length; await t.bot.clickWindow(j, 1, 0).catch(e => t.log('err', e.message)); await t.sleep(2000); t.log('chuột phải ->', t.since(n).join(' | ').slice(0, 300)); }
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  }
  t.log('bal', await R(`bal ${N}`));
  t = await moveTo(t, 12792, -2645);
  await t.sleep(2000);
  const frames = Object.values(t.bot.entities).filter(e => e.name === 'item_frame' || e.name === 'glow_item_frame');
  t.log('khung thấy:', frames.length);
  let f = frames.find(e => { const md = e.metadata || []; return JSON.stringify(md).includes('lapis_lazuli'); });
  t.log('khung lapis', f ? f.position : 'không thấy');
  if (f) {
    await R(`minecraft:tp ${N} ${f.position.x.toFixed(1)} ${(f.position.y - 1).toFixed(1)} ${f.position.z.toFixed(1)}`);
    t = await moveTo(t, Math.floor(f.position.x), Math.floor(f.position.z)).catch(() => t);
    await t.sleep(1500);
    const f2 = Object.values(t.bot.entities).find(e => (e.name === 'item_frame' || e.name === 'glow_item_frame') && JSON.stringify(e.metadata || []).includes('lapis_lazuli'));
    if (f2) { const n = t.chat.length; await t.use(f2); await t.sleep(1500); t.log('khung chuột phải ->', t.since(n).join(' | ').slice(0, 300)); }
  }
  t.log('bal cuối', await R(`bal ${N}`));
  await R(`minecraft:clear ${N}`);
  await t.end(); process.exit(0);
})();
