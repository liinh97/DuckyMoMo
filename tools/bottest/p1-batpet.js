// p1 bắt pet: heo yếu (NoAI, máu 2/20) đánh bằng dây xích; trượt thì heo không mất máu; 3 lần trượt thì hoảng;
// heo còn đầy máu thì không tính lần thử; đánh liền nhau < 2 giây thì báo chờ; sói đã thuần của mình bắt chắc chắn.
// Lưu ý: triệu hồi ở ngay chân bot (~ ~ ~), lệch ra có thể kẹt trong đất chết ngạt; dùng minecraft:item / minecraft:kill (Essentials chiếm /item, /kill).
const { start, moveTo } = require('./h');
const crypto = require('crypto');
const N = process.env.BOTN || 'ThuPet';
function uuidInts(name) {
  const h = crypto.createHash('md5').update('OfflinePlayer:' + name).digest(); h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80;
  return [0, 4, 8, 12].map(i => h.readInt32BE(i)).join(',');
}
(async () => {
  let t = await start(N);
  t = await moveTo(t, 13200, -2900);
  const R = c => t.rc.cmd(c);
  const msgs = [];
  t.bot.on('messagestr', m => msgs.push(m));
  t.bot._client.on('action_bar', p => { const x = t.strip(flat(p.text)); if (!x.includes('❤ [')) msgs.push('[bar] ' + x); });
  t.bot._client.on('system_chat', p => { if (p.isActionBar) { const x = t.strip(flat(p.content)); if (!x.includes('❤ [')) msgs.push('[bar] ' + x); } });
  await R(`effect give ${N} minecraft:resistance 900 4 true`);
  await R(`gamemode survival ${N}`);
  const hasPet = async () => !(await t.say('/petinfo', 1500)).join(' ').includes('không có');
  const release = async () => { const r = await t.say('/petrelease Pig', 1500); await R(`execute at ${N} run minecraft:kill @e[type=pig,distance=..12]`); return r.join(' | '); };
  if (await hasPet()) t.log('pet có sẵn, thả:', await release());
  const lead = () => R(`minecraft:item replace entity ${N} weapon.mainhand with minecraft:lead 64`);
  const summon = async (hp, max = 20) => {
    await R(`execute at ${N} run minecraft:kill @e[type=pig,distance=..12]`); await t.sleep(300);
    await R(`execute at ${N} run summon pig ~ ~ ~ {NoAI:1b,Tags:["thupet"],attributes:[{id:"minecraft:max_health",base:${max}}],Health:${hp}f}`);
    await t.sleep(800);
    return t.bot.nearestEntity(e => e.name === 'pig');
  };
  const pigHp = async () => (await R('data get entity @e[type=pig,tag=thupet,limit=1] Health')).replace(/.*data: /, '');
  const hit = async (e) => {
    await t.bot.lookAt(e.position.offset(0, 0.5, 0), true); await t.sleep(300);
    const n0 = msgs.length;
    t.bot.attack(e); await t.sleep(1300);
    return msgs.slice(n0).join(' | ');
  };
  await lead();
  // 1. heo đầy máu: dây xích chỉ gây 1 sát thương, không tính lần thử
  let pig = await summon(20);
  t.log('1 ĐẦY MÁU:', await hit(pig) || '(không tin)', '| máu heo 20 ->', await pigHp(), '| có pet:', await hasPet());
  // 2. heo yếu: tới khi thấy cả bắt được và hoảng (tối đa 10 con)
  let caught = 0, miss = 0, panic = 0, tries = 0;
  for (let k = 0; k < 10 && !(caught >= 2 && panic >= 1); k++) {
    pig = await summon(2);
    for (let i = 0; i < 4; i++) {
      await t.sleep(2300); await lead();
      const m = await hit(pig); const hp = await pigHp(); tries++;
      t.log(`2 heo ${k + 1} lần ${i + 1}:`, m || '(không tin)', '| máu heo:', hp);
      if (/Trượt/.test(m)) miss++;
      if (/hoảng sợ/.test(m)) { panic++; break; }
      if (/thuần hoá/.test(m)) { caught++; t.log('   thả:', await release()); break; }
      if (/No entity/.test(hp)) { t.log('   heo chết, không bắt'); break; }
    }
  }
  t.log(`2 TỔNG: ${tries} cú, bắt ${caught}, trượt ${miss}, hoảng ${panic}`);
  // 3. đánh liền nhau < 2 giây: lần 2 báo chờ, heo không chết
  pig = await summon(2); await t.sleep(2300); await lead();
  const a1 = await hit(pig); await lead(); const a2 = await hit(pig);
  t.log('3 CHỜ: lần1', a1 || '(không tin)', '|| lần2', a2 || '(không tin)', '| máu heo', await pigHp());
  if (await hasPet()) t.log('   thả:', await release());
  await R(`execute at ${N} run minecraft:kill @e[type=pig,distance=..12]`);
  // 4. sói đã thuần của mình, đầy máu: bắt chắc chắn
  await t.sleep(2300);
  await R(`execute at ${N} run summon wolf ~ ~ ~ {NoAI:1b,Tags:["thupet"],Owner:[I;${uuidInts(N)}]}`); await t.sleep(800);
  const wolf = t.bot.nearestEntity(e => e.name === 'wolf');
  await lead();
  t.log('4 SÓI CỦA MÌNH:', wolf ? (await hit(wolf) || '(không tin)') : 'không thấy sói', '| /petinfo:', (await t.say('/petinfo', 1500)).join(' / ').slice(0, 120));
  t.log('   thả:', (await t.say('/petrelease Wolf', 1500)).join(' | '));
  await R(`execute at ${N} run minecraft:kill @e[type=wolf,distance=..12]`);
  // 5. sói hoang (của người khác / chưa thuần) đầy máu: không bắt được
  await t.sleep(2300);
  await R(`execute at ${N} run summon wolf ~ ~ ~ {NoAI:1b,Tags:["thupet"]}`); await t.sleep(800);
  const w2 = t.bot.nearestEntity(e => e.name === 'wolf'); await lead();
  t.log('5 SÓI HOANG:', w2 ? (await hit(w2) || '(không tin)') : 'không thấy', '| có pet:', await hasPet());
  await R(`execute at ${N} run minecraft:kill @e[type=wolf,distance=..12]`);
  await R(`minecraft:clear ${N}`);
  await t.end();
})().catch(e => { console.log('FAIL', e); process.exit(1); });
function flat(c) { if (c == null) return ''; if (typeof c === 'string') { try { return flat(JSON.parse(c)); } catch (e) { return c; } } if (Array.isArray(c)) return c.map(flat).join(''); if (typeof c === 'object') { if (c.type === 'compound' || c.type === 'list' || c.type === 'string') return flat(c.value); let s = (c.text ?? c[''] ?? ''); if (typeof s === 'object') s = flat(s); return s + flat(c.extra); } return String(c); }
