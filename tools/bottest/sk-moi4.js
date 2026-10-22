// Lượt 4: Hố đen (đã sửa nhấc nhẹ), Chắn đạn (bộ xương bắn từ sau lưng không chắn / trước mặt có chắn), chiêu nhắm người chơi
// (ThuX1 đưa tới bằng moveTo có thử lại: vào lại game hay bị đưa về kinh đô).
const h = require('./h');
const pp = (id, lv, item = 'minecraft:stick') => `${item}[custom_data={PublicBukkitValues:{"duckymomocore:phuphep_${id}":${lv}}}]`;
const X = 12320, Z = -2610;
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join('');
  if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value);
  let s = ''; if ('text' in o) s += txt(o.text); if ('' in o) s += txt(o['']); if ('extra' in o) s += txt(o.extra); if ('content' in o) s += txt(o.content); return s; };
const tap = t => { const ab = []; const push = p => { const s = txt(p.text ?? p.content ?? p).trim(); if (s && !/^❤ \[/.test(s)) ab.push(s); };
  for (const ev of ['action_bar', 'set_action_bar_text', 'system_chat']) t.bot._client.on(ev, push); return ab; };
const go = async (t, x, z) => { for (let i = 0; i < 4 && (Math.abs(t.bot.entity.position.x - x) > 3 || Math.abs(t.bot.entity.position.z - z) > 3); i++) { t = await h.moveTo(t, x, z); await t.sleep(2500); } return t; };
(async () => {
  let A = await go(await h.start('ThuA'), X, Z);
  const R = c => A.rc.cmd(c); let ab = tap(A);
  const num = s => parseFloat(String(s).replace(/.*data: /, '').replace(/[^\d.\-]/g, ''));
  const P = async n => (await R(`data get entity ${n} Pos`)).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  const hp = async n => num(await R(`data get entity ${n} Health`));
  const hold = async (give, t, n) => { await R(`minecraft:clear ${n}`); await R(`minecraft:give ${n} ${give}`); await t.sleep(900); const it = t.bot.inventory.items()[0]; if (it) await t.bot.equip(it, 'hand'); await t.sleep(400); };
  const use = async (t, abl) => { const n = abl.length; t.bot.activateItem(); await t.sleep(250); t.bot.deactivateItem(); await t.sleep(600); return abl.slice(n).join(' | ').slice(0, 160); };
  const husk = async (dx, dz, tag) => { await R(`minecraft:execute at ThuA positioned ~${dx} ~ ~${dz} positioned over motion_blocking_no_leaves run summon husk ~ ~ ~ {Tags:["thusk","${tag}"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:max_health",base:200},{id:"minecraft:movement_speed",base:0.0001},{id:"minecraft:follow_range",base:0}],Health:200f}`); await A.sleep(1000); };
  const hpos = async tag => (await R(`data get entity @e[type=husk,tag=${tag},limit=1] Pos`)).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  const d = (a, b) => Math.hypot(a[0] - b[0], a[2] - b[2]).toFixed(1);
  const clean = async () => { await R('minecraft:kill @e[type=husk,tag=thusk]'); await R('minecraft:kill @e[type=skeleton,tag=thusk]'); await R('minecraft:kill @e[type=arrow]'); };
  // Hố đen
  await husk(0, 7, 'o'); await husk(3, 7, 'o2'); await hold(pp('ho_den', 2), A, 'ThuA');
  { const e = A.bot.nearestEntity(x => x.name === 'husk'); if (e) await A.bot.lookAt(e.position, true); await A.sleep(400); }
  const c0 = await hpos('o'), b0 = await hpos('o2'); let m = await use(A, ab); const tr = []; for (let i = 0; i < 5; i++) { await A.sleep(450); tr.push(d(await hpos('o2'), c0)); }
  A.log('Hố đen II:', m, '| o2 cách chỗ o:', d(b0, c0), '->', tr.join(' ')); await clean();
  // Chắn đạn: bộ xương trước mặt
  await R('gamemode survival ThuA'); await R('effect clear ThuA');
  const shoot = async (sx, shield, label) => {
    await R('effect give ThuA minecraft:instant_health 1 5'); await A.sleep(400);
    const yaw = A.bot.entity.yaw, fx = -Math.sin(yaw), fz = -Math.cos(yaw);
    await R(`minecraft:execute at ThuA positioned ~${(fx * 7 * sx).toFixed(2)} ~ ~${(fz * 7 * sx).toFixed(2)} positioned over motion_blocking_no_leaves run summon skeleton ~ ~ ~ {Tags:["thusk"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:movement_speed",base:0},{id:"minecraft:follow_range",base:20}],equipment:{mainhand:{id:"minecraft:bow",count:1}}}`);
    let mm = ''; if (shield) { await hold(pp('chan_dan', 3), A, 'ThuA'); mm = await use(A, ab); }
    const h0 = await hp('ThuA'); await A.sleep(4500); A.log(label, mm, '| máu', h0, '->', await hp('ThuA')); await clean(); await A.sleep(1500);
  };
  await shoot(-1, false, 'Không chắn, bộ xương sau lưng (đối chứng):');
  await shoot(1, true, 'Chắn đạn III, bộ xương trước mặt:');
  // ----- người chơi
  let B = await go(await h.start('ThuX1'), X, Z + 4);
  A = await go(A, X, Z); ab = tap(A); const abB = tap(B);
  for (let i = 0; i < 8 && !(A.bot.players.ThuX1 && A.bot.players.ThuX1.entity); i++) await A.sleep(1000);
  await R('effect clear ThuX1');
  const lookB = async () => { const e = A.bot.players.ThuX1 && A.bot.players.ThuX1.entity; if (e) await A.bot.lookAt(e.position.offset(0, 1.4, 0), true); await A.sleep(400); return !!e; };
  A.log('ThuA / ThuX1 ở', (await P('ThuA')).map(Math.round), (await P('ThuX1')).map(Math.round), '| thấy:', await lookB());
  const T = async (name, f) => { try { await f(); } catch (e) { A.log(name, 'LỖI', e.message); } await clean(); await A.sleep(400); };
  await T('Hồi máu đồng đội', async () => { await R('minecraft:damage ThuX1 8 minecraft:generic'); await A.sleep(600); const h0 = await hp('ThuX1'); await hold(pp('hoi_mau_dong_doi', 2), A, 'ThuA'); await lookB(); const mm = await use(A, ab); A.log('Hồi máu đồng đội II:', mm, '| ThuX1 máu', h0, '->', await hp('ThuX1'), '(mong +6) | ThuX1 thấy:', abB.slice(-1).join('')); });
  await T('Soi sáng', async () => { await hold(pp('soi_sang', 1), A, 'ThuA'); const mm = await use(A, ab); A.log('Soi sáng I:', mm, '| ThuX1 có Glowing:', /glowing/.test(await R('data get entity ThuX1 active_effects'))); });
  await T('Câm lặng', async () => { await hold(pp('cam_lang', 2), A, 'ThuA'); await lookB(); const mm = await use(A, ab); await hold(pp('khien_phep', 1), B, 'ThuX1'); const mb = await use(B, abB); A.log('Câm lặng II:', mm, '| ThuX1 thử Khiên phép:', mb, '| tim vàng', (await R('data get entity ThuX1 AbsorptionAmount')).replace(/.*data: /, '')); });
  await T('Giải khống chế khi bị câm', async () => { await hold(pp('giai_khong_che', 1), B, 'ThuX1'); const mb = await use(B, abB); A.log('Giải khống chế lúc đang Câm lặng:', mb); await A.sleep(1500); });
  await T('Hoá cừu', async () => {
    await R('minecraft:execute at ThuX1 run summon husk ^ ^ ^1.5 {Tags:["thusk","hc"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:max_health",base:200},{id:"minecraft:movement_speed",base:0},{id:"minecraft:follow_range",base:0}],Health:200f}');
    await R('minecraft:clear ThuX1'); await R('minecraft:give ThuX1 minecraft:diamond_sword'); await B.sleep(800); await B.bot.equip(B.bot.inventory.items().find(i => i.name === 'diamond_sword'), 'hand');
    const e = B.bot.nearestEntity(x => x.name === 'husk'); if (e) await B.bot.lookAt(e.position.offset(0, 1, 0), true);
    await hold(pp('hoa_cuu', 3), A, 'ThuA'); await lookB(); const mm = await use(A, ab); if (e) B.bot.attack(e); await B.sleep(500);
    A.log('Hoá cừu III:', mm, '| ThuX1 chém husk ngay: 200 ->', num(await R('data get entity @e[type=husk,tag=hc,limit=1] Health')), '(mong 200)');
  });
  await T('Hoảng sợ', async () => { await A.sleep(1500); await hold(pp('hoang_so', 2), A, 'ThuA'); await lookB(); const mm = await use(A, ab); A.log('Hoảng sợ II:', mm, '| ThuX1 hiệu ứng:', (await R('data get entity ThuX1 active_effects')).match(/minecraft:[a-z_]+/g)); });
  await R('minecraft:clear ThuA'); await R('minecraft:clear ThuX1');
  await B.end(); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
