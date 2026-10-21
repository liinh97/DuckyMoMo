// d1/d2 Đấu Trường: /warp pvp, /dautruong, 1v1 cược 200 giữa 2 bot; đòn chết giả bằng lệnh damage (bot qua Via khó đánh
// sau dịch chuyển). Kiểm: đếm ngược, không rơi đồ, kết quả, ELO, tiền cược/thưởng, kinh nghiệm pet người thắng.
const { start } = require('./h');
const A = 'ThuDA', B = 'ThuDB';
(async () => {
  const a = await start(A), b = await start(B);
  // thanh giao tranh của PvPManager spam mỗi 0,1 giây: bỏ khỏi log
  const clean = arr => arr.filter(l => !l.includes('[GIAO TRANH]'));
  const gm = async n => (await R(`data get entity ${n} playerGameType`)).replace(/.*data: /, '');
  const R = c => a.rc.cmd(c);
  const bal = async n => (await R(`bal ${n}`)).replace(/\s+/g, ' ').trim();
  const where = async n => (await R(`data get entity ${n} Dimension`)).replace(/.*data: /, '') + ' ' + (await R(`data get entity ${n} Pos`)).replace(/.*data: /, '');
  for (const n of [A, B]) { await R(`eco set ${n} 2000`); await R(`gamemode survival ${n}`); await R(`minecraft:clear ${n}`); }
  await R(`minecraft:give ${B} minecraft:dirt 5`);
  // pet cho A để xem kinh nghiệm pet khi thắng
  a.log('pet A:', await R(`petadmin create ${A} Pig`));
  await a.sleep(1000); a.log('petinfo A:', (await a.say('/petinfo', 1500)).join(' / ').slice(0, 160));
  // d1: /warp pvp
  a.log('d1 /warp pvp:', (await a.say('/warp pvp', 5000)).join(' | '), '| A ở', await where(A));
  a.log('d1 /dautruong:', (await a.say('/dautruong', 1500)).join(' | ').slice(0, 700));
  a.log('d1 /dautruong toi:', clean(await a.say('/dautruong toi', 1500)).join(' | '));
  // d2: 1v1 cược 200
  const n0 = a.chat.length, m0 = b.chat.length;
  a.log('A vao:', (await a.say('/dautruong vao 1v1 200', 1500)).join(' | '));
  b.log('B vao:', (await b.say('/dautruong vao 1v1 200', 1500)).join(' | '));
  await a.sleep(1500);
  a.log('A ở', await where(A), '| B ở', await where(B), '| tiền A', await bal(A), '| B', await bal(B));
  await a.sleep(7000);   // đếm ngược 5 giây
  a.log('chat A tới giờ:', clean(a.chat.slice(n0)).join(' | ').slice(0, 900));
  b.log('chat B tới giờ:', clean(b.chat.slice(m0)).join(' | ').slice(0, 900));
  a.log('chế độ A', await gm(A), 'B', await gm(B), '| máu B', (await R(`data get entity ${B} Health`)).replace(/.*data: /, ''));
  a.log('đánh chết B:', await R(`damage ${B} 100 minecraft:player_attack by ${A}`));
  await a.sleep(4000);
  a.log('KQ chat A:', clean(a.chat.slice(n0)).join(' | ').slice(-900));
  b.log('KQ chat B:', clean(b.chat.slice(m0)).join(' | ').slice(-900));
  a.log('sau trận: A ở', await where(A), '| B ở', await where(B));
  a.log('tiền A', await bal(A), '| B', await bal(B));
  a.log('đồ B còn:', await R(`minecraft:clear ${B} minecraft:dirt 0`));
  await a.sleep(16000);   // hết giao tranh
  a.log('sau 16 giây: A ở', await where(A), gm(A) && await gm(A), '| B ở', await where(B), await gm(B));
  b.log('chat B thêm:', clean(b.chat.slice(m0)).join(' | ').slice(-500));
  a.log('A toi:', clean(await a.say('/dautruong toi', 1500)).join(' | '));
  b.log('B toi:', clean(await b.say('/dautruong toi', 1500)).join(' | '));
  a.log('petinfo A:', clean(await a.say('/petinfo', 1500)).join(' / ').slice(0, 200));
  a.log('top:', clean(await a.say('/dautruong top', 1500)).join(' | ').slice(0, 400));
  await a.say('/petrelease Pig', 1500);
  for (const n of [A, B]) await R(`minecraft:clear ${n}`);
  await b.end(); await a.end();
})().catch(e => { console.log('FAIL', e); process.exit(1); });
