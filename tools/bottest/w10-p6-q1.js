// w10 treo thưởng + chuỗi giết; p6 cho pet ăn cá EMF (giới hạn 300 kinh nghiệm/ngày); q1 thanh máu quái (HoloMobHealth) + /hmh toggle.
// Cá EMF giả bằng dấu PDC evenmorefish:emf-fish-rarity (PetFish chỉ đọc dấu này). Chạy ở đất hoang 13200 -2860.
const { start, moveTo } = require('./h');
const A = 'ThuBA', B = 'ThuBB', C = 'ThuBC';
// mỗi bot một IP giả: Bounty không trả thưởng khi người hạ và người bị hạ cùng IP
const IP = { ThuBA: '10.1.0.1', ThuBB: '10.1.0.2', ThuBC: '10.1.0.3' };
const rejoin = async (t, n) => { await t.end(); await t.sleep(2500); const t2 = await start(n, IP[n]); await t2.sleep(5500); return t2; };
(async () => {
  let a = await start(A, IP[A]);
  const R = x => a.rc.cmd(x);
  const bal = async n => +((await R(`bal ${n}`)).replace(/[^\d.]/g, '') || 0);
  let b = await start(B, IP[B]), c = await start(C, IP[C]);
  await a.sleep(5000);
  for (const n of [A, B, C]) { await R(`eco set ${n} 5000`); await R(`gamemode survival ${n}`); await R(`newbie remove ${n}`); await R(`minecraft:clear ${n}`); }
  a = await moveTo(a, 13200, -2860, 'minecraft:overworld', IP[A]);
  for (const n of [B, C]) await R(`minecraft:tp ${n} ${A}`);
  b = await rejoin(b, B); c = await rejoin(c, C);
  if (!process.argv.includes('p6')) {   // run.sh w10-p6-q1.js p6 = bỏ phần treo thưởng
  // w10
  a.log('w10 A treo 1000 lên B:', (await a.say(`/treothuong dat ${B} 1000`, 2000)).join(' | ').slice(0, 250), '| tiền A', await bal(A));
  a.log('w10 /treothuong:', (await a.say('/treothuong', 1500)).join(' | ').slice(0, 300));
  let c0 = await bal(C);
  c.log('w10 C hạ B:', await R(`damage ${B} 100 minecraft:player_attack by ${C}`)); await c.sleep(3000);
  c.log('w10 tiền C', c0, '->', await bal(C), '| tin C:', c.chat.slice(-4).filter(l => /thưởng|treo|xu/i.test(l)).join(' | ').slice(0, 250));
  // chuỗi: C hạ B thêm 4 lần (tổng 5) không chết
  for (let i = 0; i < 4; i++) { await c.sleep(2000); await b.end().catch(() => {}); await c.sleep(2500); b = await start(B, IP[B]); await R(`minecraft:tp ${B} ${C}`); await c.sleep(6500); c.log(`   lần ${i + 2}:`, await R(`damage ${B} 100 minecraft:player_attack by ${C}`)); }
  await c.sleep(2500);
  c.log('w10 sau 5 lần giết liền:', c.chat.slice(-8).filter(l => /chuỗi|treo|thưởng|liền/i.test(l)).join(' | ').slice(0, 300));
  a.log('w10 /treothuong:', (await a.say('/treothuong', 1500)).join(' | ').slice(0, 300));
  }
  // p6
  a.log('p6 tạo pet:', await R(`petadmin create ${A} Wolf`)); await a.sleep(2500);
  const info = async () => (await a.say('/petinfo', 1500)).join(' ').replace(/\s+/g, ' ').match(/Máu: [\d.]+\/[\d.]+|Đói: \d+|Kinh nghiệm: [\d.]+\/[\d.]+|Cấp độ: \d+/g)?.join(', ');
  a.log('p6 pet trước:', await info());
  // cá đặt thẳng vào tay bằng lệnh (bot qua Via đôi khi không thấy đồ mới trong túi)
  const hold = async (item, comp, label) => {
    a.log(label, 'cầm:', await R(`minecraft:item replace entity ${A} weapon.mainhand with minecraft:${item}${comp} 1`));
    await a.sleep(600);
    const wolf = a.bot.nearestEntity(e => e.name === 'wolf'); if (!wolf) return a.log(label, 'không thấy pet');
    await a.bot.lookAt(wolf.position.offset(0, 0.5, 0), true);
    if (wolf.position.distanceTo(a.bot.entity.position) > 2.5) { await R(`execute at ${A} run minecraft:tp @e[type=wolf,distance=..20] ^ ^ ^1.5`); await a.sleep(800); }
    const bar = []; const ab = p => bar.push(a.strip(flat(p.text)).slice(0, 160)); a.bot._client.on('action_bar', ab);
    const n = a.chat.length; await a.use(wolf); await a.sleep(500);
    a.bot._client.removeListener('action_bar', ab);
    a.log(label, 'cách', wolf.position.distanceTo(a.bot.entity.position).toFixed(1), a.chat.slice(n).join(' | ').slice(0, 200), bar.filter(x => !x.includes('❤ [')).join(' / ').slice(0, 150), '| tay:', (await R(`data get entity ${A} SelectedItem`)).replace(/.*data: /, '').slice(0, 60), '| pet:', await info());
  };
  const LEG = '[minecraft:custom_data={PublicBukkitValues:{"evenmorefish:emf-fish-rarity":"Legendary"}}]';
  await hold('salmon', '', 'p6 cá hồi thường (không phải cá EMF):');
  for (let i = 1; i <= 4; i++) await hold('cod', LEG, `p6 cá Huyền thoại ${i}:`);
  await a.say('/petrelease Wolf', 1500);
  // q1
  const p = a.bot.entity.position.floored();
  await R(`summon zombie ${p.x + 2} ${p.y} ${p.z} {NoAI:1b,Tags:["thuq1"],PersistenceRequired:1b}`); await a.sleep(1200);
  const hitZ = async () => { const z = a.bot.nearestEntity(e => e.name === 'zombie'); if (!z) return 'không thấy zombie'; await a.bot.lookAt(z.position.offset(0, 1, 0), true); a.bot.attack(z); await a.sleep(800); const md = z.metadata || []; const name = md.find(m => m && typeof m === 'object' && (m.text || m.value || m.extra)) || md[2]; return JSON.stringify(name || md.slice(0, 4)).replace(/"type":"[a-z]+",|"value":|[{}\[\]"]/g, '').slice(0, 160) + ' | hiện tên: ' + md[3]; };
  a.log('q1 đánh zombie:', await hitZ());
  a.log('q1 /hmh toggle:', (await a.say('/hmh toggle', 1500)).join(' | ').slice(0, 150));
  await a.sleep(5500);
  a.log('q1 đánh lại (đã tắt):', await hitZ());
  a.log('q1 /hmh toggle (bật lại):', (await a.say('/hmh toggle', 1500)).join(' | ').slice(0, 150));
  await R('minecraft:kill @e[tag=thuq1]');
  for (const n of [A, B, C]) await R(`minecraft:clear ${n}`);
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });

function flat(c) { if (c == null) return ''; if (typeof c === 'string') { try { return flat(JSON.parse(c)); } catch (e) { return c; } } if (Array.isArray(c)) return c.map(flat).join(''); if (typeof c === 'object') { if (c.type === 'compound' || c.type === 'list' || c.type === 'string') return flat(c.value); let s = (c.text ?? c[''] ?? ''); if (typeof s === 'object') s = flat(s); return s + flat(c.extra); } return String(c); }
