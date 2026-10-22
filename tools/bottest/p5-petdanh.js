// p5 pet đánh người: sát thương pet lên người giảm pet-combat.player-damage-reduce-percent (50%); pet hạ người có treo thưởng
// -> chủ pet nhận thưởng. Ở đất hoang (13200 -2860), 3 bot khác IP (Bounty không trả khi cùng IP).
const { start, moveTo } = require('./h');
const A = 'ThuBA', B = 'ThuBB', C = 'ThuBC';
const IP = { ThuBA: '10.1.0.1', ThuBB: '10.1.0.2', ThuBC: '10.1.0.3' };
(async () => {
  let a = await start(A, IP[A]);
  const R = x => a.rc.cmd(x);
  const bal = async n => +((await R(`bal ${n}`)).replace(/[^\d.]/g, '') || 0);
  const hp = async n => (await R(`data get entity ${n} Health`)).replace(/.*data: /, '');
  for (const n of [A, B, C]) { await R(`newbie remove ${n}`); await R(`gamemode survival ${n}`); await R(`eco set ${n} 5000`); await R(`minecraft:clear ${n}`); }
  a = await moveTo(a, 13200, -2860, 'minecraft:overworld', IP[A]);
  let b = await start(B, IP[B]); await b.sleep(5000); await R(`minecraft:tp ${B} ${A}`);
  const c = await start(C, IP[C]);
  t0: {
    const old = (await a.say('/petinfo', 1500)).join(' '); const nm = (old.match(/^\s*([^:]+):/) || [])[1];
    if (nm && !/không có/.test(old)) await a.say('/petrelease ' + nm.trim(), 1500);
  }
  // sói cũ quanh đây (pet thả ra ở lần test trước thành sói thường) -> xoá, để @e chỉ trúng pet MyPet
  await R(`execute at ${A} run minecraft:kill @e[type=wolf,distance=..60]`);
  a.log('pet:', await R(`petadmin create ${A} Wolf`)); await a.sleep(1500);
  a.log('/petcall:', (await a.say('/petcall', 2500)).join(' | ').slice(0, 120), '| sói quanh A:', (await R(`execute at ${A} if entity @e[type=wolf,distance=..30]`)).slice(-10));
  const pet = `@e[type=wolf,distance=..30,limit=1,sort=nearest]`;
  // sát thương pet -> người: 10 sát thương thô
  await R(`effect clear ${B}`); await R(`effect give ${B} minecraft:instant_health 1 5`); await a.sleep(800);
  let h0 = await hp(B);
  a.log('p5 pet đánh B 10 sát thương:', await R(`execute at ${A} run damage ${B} 10 minecraft:mob_attack by ${pet}`), '| máu B', h0, '->', await hp(B));
  // đối chứng: zombie đánh B 10
  await R(`effect give ${B} minecraft:instant_health 1 5`); await a.sleep(800);
  await R(`execute at ${A} run summon zombie ~3 ~ ~ {NoAI:1b,Tags:["thup5"]}`); h0 = await hp(B);
  a.log('   đối chứng zombie đánh B 10:', await R(`execute at ${A} run damage ${B} 10 minecraft:mob_attack by @e[tag=thup5,limit=1]`), '| máu B', h0, '->', await hp(B));
  await R('minecraft:kill @e[tag=thup5]');
  // treo thưởng lên B (C treo), pet của A hạ B
  c.log('p5 C treo 500 lên B:', (await c.say(`/treothuong dat ${B} 500`, 2000)).join(' | ').slice(0, 160));
  await a.sleep(16000);   // hết giao tranh / tự vệ
  const a0 = await bal(A);
  await R(`effect give ${B} minecraft:instant_health 1 5`); await a.sleep(500);
  for (let i = 0; i < 6; i++) { await R(`execute at ${A} run damage ${B} 30 minecraft:mob_attack by ${pet}`); await a.sleep(700); }
  await a.sleep(2500);
  a.log('p5 pet của A hạ B: tiền A', a0, '->', await bal(A), '| tin A:', a.chat.slice(-6).filter(l => /treo|thưởng|hạ/i.test(l)).join(' | ').slice(0, 250));
  const nm2 = ((await a.say('/petinfo', 1500)).join(' ').match(/^\s*([^:]+):/) || [])[1]; if (nm2) await a.say('/petrelease ' + nm2.trim(), 1500);
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
