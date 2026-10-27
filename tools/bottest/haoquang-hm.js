// Đo hào quang phẩm Hoàn Mỹ: khảm / tháo Sắt vào kiếm tới khi ra Hoàn Mỹ, B đếm chấm DUST, cỡ, tia END_ROD trong 6 giây.
const { start, moveTo } = require('./h');
const A = 'ThuHaoA', B = 'ThuHaoB';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  let a = await start(A); let R = x => a.rc.cmd(x);
  await R(`minecraft:clear ${A}`); await R(`eco set ${A} 200000`); await R(`dmm laydo ht_kiem_0 1 ${A}`); a.log('trước:', await R(`clear ${A} minecraft:netherite_sword 0`)); await R(`tinhluyen give sat 40 ${A}`); await a.sleep(800);
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(300);
  let got = false, tries = 0;
  while (!got && tries < 40) {
    tries++;
    let w = await a.openMenu('/thoren kham'); w = await a.click(find(a, w, /Ô trống/));
    const j = w.slots.slice(0, w.inventoryStart).findIndex(it => it && /^Sắt$/.test(a.itemText(it).name) && !a.itemText(it).lore.join('').includes('đã tháo'));
    w = await a.click(j); const m = a.chat.slice(-1)[0] || '';
    got = /Hoàn Mỹ/.test(m);
    if (!got) { w = await a.click(find(a, w, /◆/)); w = await a.click(find(a, w, /Tháo ngọc/)); }
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow); await a.sleep(200);
  }
  a.log('ra Hoàn Mỹ sau', tries, 'lần khảm:', a.chat.filter(l => /Hoàn Mỹ/.test(l)).slice(-1)[0]);
  if (!got) { await R(`minecraft:clear ${A}`); await a.end(); process.exit(0); }
  a = await moveTo(a, 12280, -2236); R = x => a.rc.cmd(x);
  let sw = null; for (let i = 0; i < 25 && !sw; i++) { await a.sleep(300); sw = a.bot.inventory.items().find(i => /sword/.test(i.name)) || (a.bot.heldItem && /sword/.test(a.bot.heldItem.name) ? a.bot.heldItem : null); }
  a.log('kiếm sau khi vào lại:', sw ? sw.name : 'KHÔNG THẤY', '| server:', await R(`clear ${A} minecraft:netherite_sword 0`));
  if (sw) { await a.bot.equip(sw, 'hand'); await a.sleep(800); }
  let b = await start(B); b = await moveTo(b, 12280, -2236); await b.sleep(1500);
  let n = 0, rod = 0, sizes = [];
  const h = p => { if (!p.particle) return; if (p.particle.type === 'dust') { n += p.amount ?? 1; sizes.push(p.particle.data.scale); } if (p.particle.type === 'end_rod') rod += p.amount ?? 1; };
  b.bot._client.on('world_particles', h); await b.sleep(6000); b.bot._client.removeListener('world_particles', h);
  b.log(`Hoàn Mỹ đầy năng lượng: chấm ${n} (kỳ vọng ~90) | cỡ ${(sizes.reduce((x, y) => x + y, 0) / sizes.length).toFixed(2)} (kỳ vọng 1.15) | tia lấp lánh ${rod} (kỳ vọng ~3–4)`);
  await R(`minecraft:clear ${A}`); await R(`eco set ${A} 0`);
  await b.end(); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
