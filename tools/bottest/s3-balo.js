// s3 nhân đồ qua balo: bỏ đồ vào /balo rồi thoát ngay (thoát êm và rút mạng), lấy đồ ra rồi thoát ngay; vào lại đếm cả túi + balo.
const { start } = require('./h');
const N = 'ThuS';
const count = async (R) => {
  const inv = await R(`minecraft:clear ${N} minecraft:diamond 0`);
  const m = inv.match(/Found (\d+)/);
  return m ? +m[1] : 0;
};
(async () => {
  let t = await start(N);
  const R = c => t.rc.cmd(c);
  t.log('danh phận:', await R(`danhphan dat ${N} dan`));
  await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`);
  const baloCount = async () => {   // mở balo, đếm kim cương trong balo, đóng
    const w = await t.openMenu('/balo');
    if (!w) return 'không mở được balo';
    const n = w.slots.slice(0, w.inventoryStart).filter(i => i && i.name === 'diamond').reduce((a, i) => a + i.count, 0);
    t.bot.closeWindow(w); await t.sleep(500);
    return n;
  };
  const round = async (label, into, abrupt) => {
    await R(`minecraft:clear ${N}`);
    // dọn balo: mở, lấy hết kim cương ra rồi xoá khỏi túi
    let w = await t.openMenu('/balo');
    if (w) { for (let i = 0; i < w.inventoryStart; i++) if (w.slots[i]) await t.bot.clickWindow(i, 0, 1).catch(() => {}); t.bot.closeWindow(w); await t.sleep(600); }
    await R(`minecraft:clear ${N}`);
    if (into) await R(`minecraft:give ${N} minecraft:diamond 10`);
    else { await R(`minecraft:give ${N} minecraft:diamond 10`); w = await t.openMenu('/balo'); const s = w.slots.findIndex((it, i) => i >= w.inventoryStart && it && it.name === 'diamond'); await t.bot.clickWindow(s, 0, 1); await t.sleep(800); t.bot.closeWindow(w); await t.sleep(800); }
    const before = `túi ${await count(R)} + balo ${await baloCount()}`;
    w = await t.openMenu('/balo');
    const s = into ? w.slots.findIndex((it, i) => i >= w.inventoryStart && it && it.name === 'diamond') : w.slots.findIndex((it, i) => i < w.inventoryStart && it && it.name === 'diamond');
    t.bot.clickWindow(s, 0, 1).catch(() => {});   // shift-click, KHÔNG chờ
    if (abrupt) t.bot._client.socket.destroy(); else t.bot.quit();
    await t.sleep(4000);
    t = await start(N); await t.sleep(1500);
    t.log(`${label}: trước ${before} -> sau túi ${await count(R)} + balo ${await baloCount()}`);
  };
  await round('s3 bỏ vào + thoát ngay', true, false);
  await round('s3 bỏ vào + rút mạng', true, true);
  await round('s3 lấy ra + thoát ngay', false, false);
  await round('s3 lấy ra + rút mạng', false, true);
  await R(`minecraft:clear ${N}`);
  await t.end();
  process.exit(0);   // socket bot rút mạng còn treo tiến trình
})().catch(e => { console.log('FAIL', e); process.exit(1); });
