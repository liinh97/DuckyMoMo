// Đo hào quang: B đếm chấm DUST + cỡ trung bình của hào quang A (kiếm 1 ngọc) trong 6 giây. KIEU=setup: A khảm 1 Sắt trước.
const { start, moveTo } = require('./h');
const A = 'ThuHaoA', B = 'ThuHaoB';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  let a = await start(A); let R = x => a.rc.cmd(x);
  {
    await R(`minecraft:clear ${A}`); await R(`dmm laydo ht_kiem_0 1 ${A}`); await R(`tinhluyen give sat 1 ${A}`); await a.sleep(800);
    await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(300);
    let w = await a.openMenu('/thoren kham'); w = await a.click(find(a, w, /Ô trống/)); w = await a.click(find(a, w, /^Sắt$/)); a.log('khảm:', a.chat.slice(-1)[0]);
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
    a = await moveTo(a, 12280, -2236); R = x => a.rc.cmd(x);
  }
  await a.sleep(1000);
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(800);
  a.log('ngọc:', await R(`data get entity ${A} SelectedItem.components."minecraft:custom_data".PublicBukkitValues."duckymomocore:dmm_kham"`));
  let b = await start(B); b = await moveTo(b, 12280, -2236); await b.sleep(1500);
  let n = 0, packets = 0, sizes = []; let sample = null;
  const h = p => { packets++; n += p.amount ?? p.particles ?? 1; const sc = p.particle && p.particle.data && p.particle.data.scale; if (typeof sc === 'number') sizes.push(sc); if (!sample) sample = JSON.stringify(p).slice(0, 300); };
  b.bot._client.on('world_particles', h); await b.sleep(6000); b.bot._client.removeListener('world_particles', h);
  const avg = sizes.length ? (sizes.reduce((x, y) => x + y, 0) / sizes.length).toFixed(2) : '?';
  const tag = (await a.rc.cmd(`data get entity ${A} SelectedItem.components."minecraft:custom_data".PublicBukkitValues."duckymomocore:dmm_kham"`)).match(/"[a-z]+:(\d):(\d+):(\d+)/);
  const t = tag ? +tag[1] : 0, nl = +(process.env.LV || 5000), f = Math.min(1, 5000 / nl);
  b.log('6 giây: chấm', n, '(kỳ vọng', Math.round(30 * (t + 1) * (0.25 + 0.75 * f)) + ') | cỡ TB', avg, '(kỳ vọng', ((0.55 + 0.3 * t) * (0.45 + 0.55 * f)).toFixed(2) + ') | phẩm', t, '| năng lượng', Math.round(100 * f) + '%');
  if (process.env.DEBUG) b.log('mẫu gói:', sample);
  await b.end(); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
