// Lòng đất map Trái Đất có hang / quặng không (23/10, chủ server hỏi): bot tới vài vùng đất hoang, đếm khối dưới mặt đất trong các chunk đã nạp.
const h = require('./h');
const N = process.env.BOTN || 'ThuDat';
const SPOTS = [[12100, -2300, 'Lào (đồi núi)'], [12900, -1700, 'Bắc VN'], [12500, -2900, 'Trung Lào / Thái']];
(async () => {
  let t = await h.start(N);
  await t.rc.cmd(`gamemode spectator ${N}`);
  for (const [x, z, ten] of SPOTS) {
    t = await h.moveTo(t, x, z); await t.sleep(6000);
    const c = {}; let total = 0, below = 0;
    const p = t.bot.entity.position.floored();
    for (let dx = -40; dx <= 40; dx += 1) for (let dz = -40; dz <= 40; dz += 1) {
      // mặt đất: khối rắn cao nhất
      let top = null;
      for (let y = 200; y > -64; y--) { const b = t.bot.blockAt(p.offset(dx, y - p.y, dz)); if (b && b.name !== 'air' && b.name !== 'void_air' && !/leaves|log|grass$|short_grass|tall_grass|fern|flower|vine|snow$|water/.test(b.name)) { top = y; break; } }
      if (top === null) continue;
      for (let y = top - 6; y > -60; y--) {
        const b = t.bot.blockAt(p.offset(dx, y - p.y, dz)); if (!b) continue;
        total++;
        const n = /air/.test(b.name) ? 'KHOẢNG TRỐNG' : /_ore$/.test(b.name) ? b.name.replace('deepslate_', '') : b.name === 'lava' ? 'lava' : b.name === 'water' ? 'water' : null;
        if (n) c[n] = (c[n] || 0) + 1;
      }
    }
    t.log(`## ${ten} (${x},${z}): ${total} khối dưới mặt đất | ` + Object.entries(c).sort((a, b) => b[1] - a[1]).map(([k, v]) => `${k} ${v} (${(100 * v / total).toFixed(2)}%)`).join(', '));
  }
  await t.rc.cmd(`gamemode survival ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
