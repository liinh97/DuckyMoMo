// Mô tả đồ set (28/10 chiều): dòng "KHẢM NGỌC" theo kiểu mục từng set, chỉ số tiếng Việt, không còn dòng OCM / % trùng.
// Chạy: BOTN=ThuMoTa ./tools/bottest/run.sh mota-set.js   Dọn: /choilai admin ThuMoTa moi
const h = require('./h');
const N = process.env.BOTN || 'ThuMoTa';
const IDS = (process.env.IDS || 'dv_kiem_3,ht_ao_3,ta_kiem_5').split(',');
(async () => {
  let t = await h.start(N);
  await t.rc.cmd(`gamemode survival ${N}`); await t.rc.cmd(`minecraft:clear ${N}`);
  for (const id of IDS) await t.rc.cmd(`dmm laydo ${id} 1 ${N}`);
  await t.end(); await t.sleep(3000);
  t = await h.start(N); await t.sleep(2500);
  for (const it of t.bot.inventory.items()) {
    const x = t.itemText(it);
    t.log('===== ' + x.name + '\n  ' + x.lore.join('\n  '));
  }
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
