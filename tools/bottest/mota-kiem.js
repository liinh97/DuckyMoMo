// Mô tả đồ set (28/10): kiếm Thần Thánh +3 -> phù phép gộp 3 cái một dòng, tên tiếng Việt, dòng "◈ Khảm 0/4" trên THIÊN UY;
// khảm 1 Sắt -> "◈ Khảm 1/4: ◆Sắt …"; kèm %dmmviec_ketqua% sau /dmm viecnpc (câu kết quả trong bảng hội thoại).
// Chạy: BOTN=ThuMoTa ./tools/bottest/run.sh mota-kiem.js   Dọn: /choilai admin ThuMoTa moi
const h = require('./h');
const N = process.env.BOTN || 'ThuMoTa';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  let t = await h.start(N);
  let R = x => t.rc.cmd(x);
  await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`);
  t.log('laydo:', await R(`dmm laydo dv_kiem_3 1 ${N}`));
  await R(`tinhluyen give sat 1 ${N}`);
  await t.end(); await t.sleep(3000);
  t = await h.start(N); R = x => t.rc.cmd(x); await t.sleep(2500);
  const sword = () => t.bot.inventory.items().find(i => /sword/.test(i.name));
  t.log('## mô tả kiếm (sau vào lại):\n  ' + (sword() ? t.itemText(sword()).lore.join('\n  ') : '(không có kiếm)'));
  await t.bot.equip(sword(), 'hand'); await t.sleep(500);
  let w = await t.openMenu('/thoren kham');
  let i = find(t, w, /Ô trống/);
  if (i >= 0) { w = await t.click(i); const j = find(t, w, /Sắt/); if (j >= 0) w = await t.click(j); }
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await t.sleep(800);
  t.log('## sau khảm Sắt:\n  ' + t.itemText(t.bot.heldItem).lore.filter(l => /Khảm|THIÊN UY/.test(l)).join('\n  '));
  await R(`dmm viecnpc ${N} gialang`);
  t.log('## ket qua trong bang:', await R(`papi parse ${N} %dmmviec_ketqua%`));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
