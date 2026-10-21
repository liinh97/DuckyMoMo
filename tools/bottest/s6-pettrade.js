// p7 bán pet /pettrade B 100 (B chưa có pet) + s6: A rút mạng đúng lúc B chấp nhận; B chấp nhận 2 lần. Pet phải thuộc đúng 1 người, tiền trừ 1 lần.
const { start } = require('./h');
const A = 'ThuPetA', B = 'ThuPetB';
(async () => {
  let a = await start(A), b = await start(B);
  const R = x => b.rc.cmd(x);
  const bal = async n => +((await R(`bal ${n}`)).replace(/[^\d.]/g, '') || 0);
  const pet = async t => { const l = (await t.say('/petinfo', 1500)).join(' '); return /không có/.test(l) ? 'không pet' : (l.match(/^\s*([^:]+):/) || [, 'có pet'])[1].trim(); };
  const state = async () => `A ${await pet(a)} ${await bal(A)} xu | B ${await pet(b)} ${await bal(B)} xu`;
  for (const n of [A, B]) { await R(`eco set ${n} 1000`); await R(`gamemode survival ${n}`); }
  // dọn pet cũ
  for (const t of [a, b]) { const p = await pet(t); if (p !== 'không pet') await t.say(`/petrelease ${p}`, 1500); }
  await R(`petadmin create ${A} Pig`); await a.sleep(1500);
  a.log('đầu:', await state());
  // p7: bán bình thường
  a.log('p7 A mời:', (await a.say(`/pettrade ${B} 100`, 1500)).join(' | ').slice(0, 250));
  b.log('p7 B tin:', b.chat.slice(-3).join(' | ').slice(0, 300));
  b.log('p7 B accept:', (await b.say('/pettrade accept', 2000)).join(' | ').slice(0, 250));
  a.log('p7 sau:', await state());
  // s6: B bán lại cho A, B rút mạng đúng lúc A chấp nhận
  b.log('s6 B mời A:', (await b.say(`/pettrade ${A} 100`, 1500)).join(' | ').slice(0, 200));
  a.bot.chat('/pettrade accept'); b.bot._client.socket.destroy();
  await a.sleep(4000); b = await start(B); await b.sleep(1500);
  a.log('s6 rút mạng lúc chấp nhận ->', await state());
  // s6b: người nhận bấm chấp nhận 2 lần liền
  const holder = (await pet(a)) !== 'không pet' ? [a, A, b, B] : [b, B, a, A];
  const [s, sn, r, rn] = holder;
  s.log('s6b mời:', (await s.say(`/pettrade ${rn} 100`, 1500)).join(' | ').slice(0, 200));
  r.bot.chat('/pettrade accept'); r.bot.chat('/pettrade accept');
  await r.sleep(3000);
  a.log('s6b chấp nhận 2 lần ->', await state(), '| tin người nhận:', r.chat.slice(-3).join(' | ').slice(0, 250));
  for (const t of [a, b]) { const p = await pet(t); if (p !== 'không pet') await t.say(`/petrelease ${p}`, 1500); }
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
