// h1–h3 Hoà Bình.
// h1: ThuH thử lập thị trấn ở ô kề mép đất kinh đô (block 13016 -2520, tính từ Towny townblocks 21/10) rồi /hoabinh vao.
//     21/10: Towny chặn "quá gần thị trấn khác" (min_plot_distance_from_town_plot 5) -> không thị trấn nào chạm được đất liên minh.
// h2/h3: tạm cho ThuH làm dân KinhDo (townyadmin town KinhDo add) để là dân hoà bình: ThuK đánh ThuH trong đất kinh đô (ngoài vùng
//     spawn WorldGuard) và ngoài hoang (đối chứng); ThuK giết ThuH ở dải đệm 50 block ngoài mép -> mở án, cấm vào đất hoà bình.
// Xong: townyadmin town KinhDo kick ThuH.
const { start, moveTo } = require('./h');
const H = 'ThuH', K = 'ThuK';
const TX = 13016, TZ = -2520;
(async () => {
  let h = await start(H);
  const R = c => h.rc.cmd(c);
  const hp = async n => (await R(`data get entity ${n} Health`)).replace(/.*data: /, '');
  await R(`hoatdong them ${H} 240`); await R(`eco set ${H} 20000`); await R(`eco set ${K} 2000`);
  await R(`gamemode survival ${H}`); await R(`newbie remove ${H}`); await R(`newbie remove ${K}`);
  await R(`effect give ${H} minecraft:water_breathing 900 0 true`);
  h = await moveTo(h, TX, TZ);
  h.log('h1 /t new sát đất kinh đô:', (await h.say('/t new ThuTranH', 4000)).join(' | ').slice(0, 300));
  h.log('h1 /hoabinh vao (không có thị trấn):', (await h.say('/hoabinh vao', 2000)).join(' | ').slice(0, 200));
  // dân KinhDo
  h.log('thêm vào KinhDo:', await R(`townyadmin town KinhDo add ${H}`));
  await h.sleep(1500);
  h.log('/hoabinh của H:', (await h.say('/hoabinh', 1500)).join(' | ').slice(-200));
  let k = await start(K); await k.sleep(6000); await k.rc.cmd(`gamemode survival ${K}`);
  const meet = async (x, z) => {
    h = await moveTo(h, x, z); await R(`minecraft:tp ${K} ${H}`); await k.end(); await k.sleep(2500); k = await start(K); await k.sleep(5500);
    await R(`effect give ${H} minecraft:instant_health 1 5`); await h.sleep(500);
  };
  const hit = async () => { const e = k.bot.players[H] && k.bot.players[H].entity; if (!e) return 'K không thấy H'; await k.bot.lookAt(e.position.offset(0, 1.5, 0), true); const n = k.chat.length; k.bot.attack(e); await k.sleep(900); return k.chat.filter((l, i) => i >= n && !l.includes('[GIAO TRANH]')).join(' | '); };
  // h2 trong đất kinh đô, ngoài vùng spawn WorldGuard (x > 12801)
  await meet(12950, TZ);
  let h0 = await hp(H); let m = await hit();
  h.log('h2 trong đất hoà bình (12950): máu H', h0, '->', await hp(H), '| tin K:', m.slice(0, 200), '| ô:', (await h.say('/t here', 1200)).join(' ').slice(0, 120));
  await meet(TX + 600, TZ);
  h0 = await hp(H); m = await hit();
  h.log('h2 ngoài hoang (đối chứng): máu H', h0, '->', await hp(H), '| tin K:', m.slice(0, 200));
  await h.sleep(31000);   // hết 30 giây "tự vệ"
  // h3 giết ở dải đệm (50 block ngoài mép đất kinh đô)
  await meet(TX + 50, TZ);
  const n0 = h.chat.length, m0 = k.chat.length;
  h.log('h3 giết ở dải đệm:', await R(`damage ${H} 100 minecraft:player_attack by ${K}`));
  await h.sleep(3000);
  h.log('h3 tin H:', h.chat.slice(n0).join(' | ').slice(0, 500));
  k.log('h3 tin K:', k.chat.slice(m0).filter(l => !l.includes('[GIAO TRANH]')).join(' | ').slice(0, 600));
  k.log('h3 /bantron toi:', (await k.say('/bantron toi', 1500)).join(' | ').slice(0, 400));
  k.log('h3 /bantron lichsu:', (await k.say('/bantron lichsu', 1500)).join(' | ').slice(0, 400));
  k.log('h3 tiền K', await R(`bal ${K}`), '| H', await R(`bal ${H}`));
  await k.sleep(16000);   // hết giao tranh
  k.log('h3 K /spawn:', (await k.say('/spawn', 5000)).filter(l => !l.includes('[GIAO TRANH]')).join(' | ').slice(0, 300), '| K ở', (await R(`data get entity ${K} Pos`)).replace(/.*data: /, ''));
  h.log('bỏ khỏi KinhDo:', await R(`townyadmin town KinhDo kick ${H}`));
  await k.end(); await h.end();
})().catch(e => { console.log('FAIL', e); process.exit(1); });
