// n2 cây lớn nhanh/chậm theo mùa, n4 dưa được mùa (ngoài Hạn hán), n5 mưa theo mùa / sự kiện — test NHANH bằng cách tạm chỉnh:
//   wrapper (scratchpad n-thoigian.sh) sửa config trước: tắt Hạn hán, mùa mưa rain-start-chance 100, mùa khô rain-stop-chance 100, dmm reload;
//   kịch bản này: /mua dat, /mua sukien, random_tick_speed 100, giờ ban ngày; cuối trả lại (mùa/sự kiện theo lịch, tick 3, giờ cũ).
// /dmm reload chạy lại lượt thời tiết sau 30 giây (bình thường 5 phút một lượt).
const { start, moveTo } = require('./h');
const N = 'ThuNa';
(async () => {
  let t = await start(N);
  t = await moveTo(t, 13200, -2900);
  const R = c => t.rc.cmd(c);
  const V = require('vec3');
  await R(`gamemode survival ${N}`); await R(`effect give ${N} minecraft:resistance 1800 4 true`);
  const p0 = t.bot.entity.position.floored(); const X = p0.x, Y = p0.y, Z = p0.z;
  // bot qua Via đọc mưa sai; Essentials chiếm /weather, /time -> dùng minecraft:weather + predicate phía server
  const rain = async () => /passed/.test(await R('execute if predicate {condition:"minecraft:weather_check",raining:true}'));
  const weatherCheck = async (label, setup, preset) => {
    for (const c of setup) await R(c);
    await R(preset); await t.sleep(3000);
    const before = await rain();
    await R('dmm reload'); await t.sleep(36000);
    t.log(`${label}: trước ${before ? 'mưa' : 'tạnh'} -> sau lượt thời tiết ${(await rain()) ? 'MƯA' : 'TẠNH'}`);
  };
  // ---- n2: ruộng 7×3 khoai + 7×3 lúa, hàng nước ở giữa
  const plant = async () => {
    await R(`fill ${X + 2} ${Y} ${Z - 4} ${X + 8} ${Y + 2} ${Z + 4} minecraft:air`);
    await R(`fill ${X + 2} ${Y - 1} ${Z - 3} ${X + 8} ${Y - 1} ${Z + 3} minecraft:farmland[moisture=7]`);
    await R(`fill ${X + 2} ${Y - 1} ${Z} ${X + 8} ${Y - 1} ${Z} minecraft:water`);
    await R(`fill ${X + 2} ${Y} ${Z - 3} ${X + 8} ${Y} ${Z - 1} minecraft:potatoes[age=0]`);
    await R(`fill ${X + 2} ${Y} ${Z + 1} ${X + 8} ${Y} ${Z + 3} minecraft:wheat[age=0]`);
    await R(`fill ${X + 2} ${Y + 1} ${Z - 3} ${X + 8} ${Y + 1} ${Z + 3} minecraft:light[level=15]`);   // sáng đủ cả ban đêm
  };
  const ages = async () => {
    const avg = async (crop, z0, z1) => { let sum = 0, n = 0; for (let x = X + 2; x <= X + 8; x++) for (let z = z0; z <= z1; z++) { for (let ag = 7; ag >= 0; ag--) { if (/passed/.test(await R(`execute if block ${x} ${Y} ${z} minecraft:${crop}[age=${ag}]`))) { sum += ag; n++; break; } } } return n ? (sum / n).toFixed(2) + ` (${n} cây)` : 'không có cây'; };
    return `khoai ${await avg('potatoes', Z - 3, Z - 1)} | lúa ${await avg('wheat', Z + 1, Z + 3)}`;
  };
  const growth = async (label, secs) => {
    await plant(); await t.sleep(1500);
    await R('gamerule random_tick_speed 100');
    await t.sleep(secs * 1000);
    await R('gamerule random_tick_speed 3');
    await t.sleep(1000);
    t.log(`${label} sau ${secs}s (tick ×33): tuổi trung bình (0-7)`, await ages());
  };
  // mùa khô, không sự kiện
  await R('mua dat kho');
  t.log('mùa:', (await t.say('/mua', 1500)).slice(0, 3).join(' | ').slice(0, 220));
  await weatherCheck('n5 mùa khô (tạm tạnh 100%)', ['minecraft:weather rain'], 'mua dat kho');
  await growth('n2 mùa khô', 40);
  // mùa mưa
  await weatherCheck('n5 mùa mưa (tạm mưa 100%)', ['mua dat mua'], 'minecraft:weather clear');
  await growth('n2 mùa mưa', 40);
  // sự kiện Lũ / Hạn hán
  await weatherCheck('n5 sự kiện Lũ (ép mưa)', ['mua sukien lu'], 'minecraft:weather clear');
  await weatherCheck('n5 sự kiện Hạn hán (ép tạnh)', ['mua sukien han'], 'minecraft:weather rain');
  // dọn + trả lại
  await R(`fill ${X + 2} ${Y} ${Z - 4} ${X + 8} ${Y + 2} ${Z + 4} minecraft:air`);
  await R(`fill ${X + 2} ${Y - 1} ${Z - 3} ${X + 8} ${Y - 1} ${Z + 3} minecraft:grass_block`);
  await R('mua sukien tudong'); await R('mua dat tudong'); await R('gamerule random_tick_speed 3');
  await R('minecraft:weather clear');
  t.log('đã trả lại: mùa/sự kiện theo lịch, tick 3 |', (await R('gamerule random_tick_speed')).slice(-4));
  await t.end(); process.exit(0);
})().catch(async e => { console.log('FAIL', e); process.exit(1); });
