// NPC Nhà Buôn ở khu shop: bấm vào phải mở menu đơn tuần.
const { start, moveTo } = require('./h');
const N = 'ThuNhaBuon';
const names = (t, w) => w ? w.slots.slice(0, w.inventoryStart).map((it, i) => it && `${i}:${t.itemText(it).name}`).filter(Boolean).filter(s => !/Pane/.test(s)).join(' | ') : '(không mở)';
(async () => {
  let t = await start(N);
  await t.rc.cmd(`minecraft:tp ${N} 12788.5 72 -2645.5 180 0`); await t.end(); await t.sleep(2500);   // vào trong nhà shop (moveTo đặt lên mái)
  t = await start(N); await t.sleep(2000);
  const v = Object.values(t.bot.entities).filter(e => e.name === 'villager').sort((x, y) => x.position.distanceTo(t.bot.entity.position) - y.position.distanceTo(t.bot.entity.position))[0];
  t.log('dân làng gần nhất:', v && v.position);
  const p = new Promise(res => { t.bot.once('windowOpen', w => res(w)); setTimeout(() => res(null), 4000); });
  await t.bot.lookAt(v.position.offset(0, 1.5, 0)); await t.use(v);
  const w = await p; t.log('bấm NPC ->', w ? (t.itemText({ customName: w.title }).name || '') + ' ' + names(t, w) : 'không mở');
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
