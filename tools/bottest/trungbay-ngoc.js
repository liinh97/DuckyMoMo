// Phòng trưng bày hào quang ngọc khảm dọc đường đá phía tây spawn (cách chữ nổi spawn ≥7 ô). Bot vào, nhận đồ Huyền Thoại, ngọc ghi thẳng
// vào món (mã:phẩm:%:năng lượng) bằng /item modify (khỏi khảm/tháo nhiều lần cho ra đúng phẩm), mặc + cầm kiếm, đứng PHUT phút rồi dọn đồ, thoát.
const { start, sleep } = require('./h');
const PHUT = +(process.env.LV || 30);
const PIECES = [['ht_mu_0', 'armor.head'], ['ht_ao_0', 'armor.chest'], ['ht_quan_0', 'armor.legs'], ['ht_giay_0', 'armor.feet'], ['ht_kiem_0', 'weapon.mainhand']];
const gem3 = (g, t, f, e) => [0, 1, 2].map(() => `${g}:${t}:${f}:${e}`).join(',');
const full = (g, t, f, e) => Object.fromEntries(PIECES.map(([id]) => [id, gem3(g, t, f, e)]));
const B = [];
// 27/10 chiều: so kiểu hạt — Thường (chấm), Tốt mới (xoáy), Hoàn Mỹ: xoắn ốc / vòng đầu / kết hợp (phẩm thử 3..6 trong config, tỉ lệ 0)
[['Pham_Thuong', 0], ['Tot_Xoay', 3], ['HM_XoanOc', 4], ['HM_VongDau', 5], ['HM_KetHop', 6]].forEach(([n, t], i) =>
  B.push({ name: n, x: 12690 + 2 * i, z: -2432, yaw: 180, gear: full('kimcuong', t, t === 0 ? 90 : 120, 5000) }));
(async () => {
  const bots = [];
  for (const s of B) {
    try { const t = await start(s.name); bots.push([t, s]); } catch (e) { console.log('không vào được', s.name, e.message); }
  }
  const rc = bots[0][0].rc;
  const R = x => rc.cmd(x);
  for (const [t, s] of bots) {
    const N = s.name;
    await R(`minecraft:clear ${N}`); await R(`gamemode survival ${N}`);
    let slot = 9;   // để món trong túi (ô 9+) rồi chuyển sang chỗ mặc / tay
    for (const [id, where] of PIECES) {
      if (!s.gear[id]) continue;
      await R(`minecraft:give ${N} minecraft:stone`);   // giữ chỗ ô 0 cho tay chính
      await R(`dmm laydo ${id} 1 ${N}`);
      await R(`minecraft:clear ${N} minecraft:stone`);
      // món vừa lấy nằm ô trống đầu tiên: tìm bằng cách thử từng ô
      let found = -1;
      for (let k = 0; k < 36 && found < 0; k++) {
        const r = await R(`data get entity ${N} Inventory[{Slot:${k}b}].components."minecraft:custom_data".PublicBukkitValues."duckymomocore:thuvien_id"`);
        if (r.includes(`"${id}"`)) found = k;
      }
      if (found < 0) { console.log(N, 'không thấy', id); continue; }
      const src = found < 9 ? `hotbar.${found}` : `inventory.${found - 9}`;
      await R(`minecraft:item modify entity ${N} ${src} {function:"minecraft:set_custom_data",tag:"{PublicBukkitValues:{\\"duckymomocore:dmm_kham\\":\\"${s.gear[id]}\\"}}"}`);
      await R(`minecraft:item replace entity ${N} ${where} from entity ${N} ${src}`);
      if (src !== 'hotbar.0' || where !== 'weapon.mainhand') await R(`minecraft:item replace entity ${N} ${src} with minecraft:air`);
    }
    await R(`execute in minecraft:overworld run minecraft:tp ${N} ${s.x + 0.5} 85 ${s.z + 0.5} ${s.yaw} 0`);
  }
  console.log('SẴN SÀNG:', bots.length, 'bot, đứng', PHUT, 'phút');
  for (const [t, s] of bots.slice(0, 3)) console.log(s.name, '->', (await R(`data get entity ${s.name} equipment.head.components."minecraft:custom_data".PublicBukkitValues."duckymomocore:dmm_kham"`)).replace(/.*data: /, ''), '| tay:', (await R(`data get entity ${s.name} SelectedItem.id`)).replace(/.*data: /, ''));
  const end = Date.now() + PHUT * 60000;
  while (Date.now() < end) await sleep(5000);
  for (const [t, s] of bots) { await R(`minecraft:clear ${s.name}`); }
  for (const [t] of bots) await t.end().catch(() => {});
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
