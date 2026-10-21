// s11 tiền âm / số tràn: /pay, /t deposit, /treothuong, /dungeon gop, /choden ban với số âm, 1e20, NaN, Infinity. So số dư trước/sau.
const { start } = require('./h');
const A = 'ThuS', B = 'ThuK';
(async () => {
  const a = await start(A), b = await start(B);
  const R = c => a.rc.cmd(c);
  const bal = async n => +((await R(`bal ${n}`)).replace(/[^\d.]/g, '') || 0);
  await R(`eco set ${A} 1000`); await R(`eco set ${B} 1000`);
  const cmds = [
    `/pay ${B} -100`, `/pay ${B} 1e20`, `/pay ${B} NaN`, `/pay ${B} Infinity`, `/pay ${B} 0.0000001`, `/pay ${B} 1,5`,
    '/t deposit -50', '/t withdraw -50', `/treothuong dat ${B} -1`, `/treothuong dat ${B} 1e20`,
    '/dungeon gop knights_castle_lair -10', '/dungeon gop knights_castle_lair 1e20',
    '/eco give ThuS 100', `/pay ${A} 100`,
  ];
  for (const c of cmds) {
    const a0 = await bal(A), b0 = await bal(B);
    const out = (await a.say(c, 1300)).join(' | ').slice(0, 220);
    a.log(`${c} -> A ${a0}->${await bal(A)}, B ${b0}->${await bal(B)} | ${out}`);
  }
  await R(`minecraft:give ${A} minecraft:dirt 1`);
  for (const p of ['-100', 'NaN', '0']) a.log(`/choden ban ${p}:`, (await a.say(`/choden ban ${p}`, 1500)).join(' | ').slice(0, 200));
  await R(`minecraft:clear ${A}`);
  await b.end(); await a.end();
  process.exit(0);   // socket bot rút mạng còn treo tiến trình
})().catch(e => { console.log('FAIL', e); process.exit(1); });
