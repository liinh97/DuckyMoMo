// Lượt 3: test lại phần lượt 2 chưa kết luận (giải mã action bar / chat từ gói tin).
const h = require('./h');
const pp = (id, lv, item = 'minecraft:stick') => `${item}[custom_data={PublicBukkitValues:{"duckymomocore:phuphep_${id}":${lv}}}]`;
const X = 12320, Z = -2610;
// gói tin chữ (NBT dạng {type, value}) -> chuỗi
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join('');
  if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value);
  let s = ''; if ('text' in o) s += txt(o.text); if ('' in o) s += txt(o['']); if ('extra' in o) s += txt(o.extra); if ('content' in o) s += txt(o.content); if ('formattedMessage' in o) s += txt(o.formattedMessage); return s; };
const tap = t => { const ab = []; const push = p => { const s = txt(p.text ?? p.content ?? p).trim(); if (s && !/^❤ \[/.test(s)) ab.push(s); };
  for (const ev of ['action_bar', 'set_action_bar_text', 'system_chat']) t.bot._client.on(ev, push); return ab; };
(async () => {
  let A = await h.start('ThuA'); for (let i = 0; i < 3 && Math.abs(A.bot.entity.position.x - X) > 5; i++) { A = await h.moveTo(A, X, Z); await A.sleep(3000); }
  const R = c => A.rc.cmd(c); const ab = tap(A);
  const num = s => parseFloat(String(s).replace(/.*data: /, '').replace(/[^\d.\-]/g, ''));
  const P = async n => (await R(`data get entity ${n} Pos`)).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  const hp = async n => num(await R(`data get entity ${n} Health`));
  const hold = async (give, t = A, n = 'ThuA') => { await R(`minecraft:clear ${n}`); await R(`minecraft:give ${n} ${give}`); await t.sleep(900); const it = t.bot.inventory.items()[0]; if (it) await t.bot.equip(it, 'hand'); await t.sleep(400); };
  const use = async (t = A, abl = ab) => { const n = abl.length; t.bot.activateItem(); await A.sleep(250); t.bot.deactivateItem(); await A.sleep(600); return abl.slice(n).join(' | ').slice(0, 160); };
  const husk = async (dx, dz, tag, speed = 0) => { await R(`minecraft:execute at ThuA positioned ~${dx} ~ ~${dz} positioned over motion_blocking_no_leaves run summon husk ~ ~ ~ {Tags:["thusk","${tag}"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:max_health",base:200},{id:"minecraft:movement_speed",base:${speed}},{id:"minecraft:follow_range",base:0}],Health:200f}`); await A.sleep(1000); };
  const sel = tag => `@e[type=husk,tag=${tag},limit=1]`;
  const hhp = async tag => num(await R(`data get entity ${sel(tag)} Health`));
  const hpos = async tag => (await R(`data get entity ${sel(tag)} Pos`)).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  const look = async (dy = 1.2) => { const e = A.bot.nearestEntity(x => x.name === 'husk'); if (e) await A.bot.lookAt(e.position.offset(0, dy, 0), true); await A.sleep(400); };
  const d = (a, b) => Math.hypot(a[0] - b[0], a[2] - b[2]).toFixed(1);
  const T = async (name, f) => { try { await f(); } catch (e) { A.log(name, 'LỖI', e.message); } await R('minecraft:kill @e[type=husk,tag=thusk]'); await R('minecraft:kill @e[type=skeleton,tag=thusk]'); await A.sleep(400); };

  if (0) await T('Thu nhỏ', async () => { await hold(pp('thu_nho', 3)); const m = await use(); A.log('Thu nhỏ III:', await A.attr('minecraft:scale'), '| báo:', m); await R('attribute ThuA minecraft:scale modifier remove duckymomocore:phuphep_kichthuoc'); });
  if (0) await T('Lốc xoáy', async () => { await husk(1.5, 0, 'l'); await hold(pp('loc_xoay', 2)); await use(); await A.sleep(3200); A.log('Lốc xoáy II: 200 ->', await hhp('l'), '(6 × 1,84 ≈ 11)'); });
  await T('Hố đen', async () => { await husk(0, 7, 'o', 0.0001); await husk(3, 7, 'o2', 0.0001); await hold(pp('ho_den', 2)); await look(0); const b = await hpos('o2'), c = await hpos('o'); const m = await use(); const tr = []; for (let i = 0; i < 5; i++) { await A.sleep(450); tr.push(d(await hpos('o2'), c)); } A.log('Hố đen II:', m, '| máu o / o2', await hhp('o'), await hhp('o2'), '| o2 cách chỗ o:', d(b, c), '->', tr.join(' ')); });
  if (0) await T('Rút bài', async () => { await husk(0, 2.5, 'r', 0.23); await hold(pp('rut_bai', 2, 'minecraft:diamond_sword')); await use(); await A.sleep(1300); const m = await use(); await look(); const e = A.bot.nearestEntity(x => x.name === 'husk'); A.bot.attack(e); await A.sleep(600); A.log('Rút bài II:', m, '| husk 200 ->', await hhp('r'), '(kiếm 7,36; Đỏ +3 trừ giáp; Vàng tốc 0)', '| tốc', (await R(`attribute ${sel('r')} minecraft:movement_speed get`)).replace(/.*is /, '')); });
  await T('Chắn đạn', async () => {
    await R('effect clear ThuA'); await R('effect give ThuA minecraft:instant_health 1 5'); await R('gamemode survival ThuA');
    for (const [name, sx] of [['trước mặt', 1], ['sau lưng', -1]]) {
      await hold(pp('chan_dan', 3)); await R('effect give ThuA minecraft:instant_health 1 5'); await A.sleep(500);
      const yaw = A.bot.entity.yaw, fx = -Math.sin(yaw), fz = -Math.cos(yaw);
      await R(`minecraft:execute at ThuA positioned ~${(fx * 7 * sx).toFixed(2)} ~ ~${(fz * 7 * sx).toFixed(2)} positioned over motion_blocking_no_leaves run summon skeleton ~ ~ ~ {Tags:["thusk"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:movement_speed",base:0},{id:"minecraft:follow_range",base:20}],equipment:{mainhand:{id:"minecraft:bow",count:1}}}`);
      await A.sleep(1500); const m = await use(); const h0 = await hp('ThuA'); await A.sleep(4000);
      A.log('Chắn đạn III, bộ xương bắn từ', name, ':', m, '| máu ThuA', h0, '->', await hp('ThuA'));
      await R('minecraft:kill @e[type=skeleton,tag=thusk]'); await R('minecraft:kill @e[type=arrow]'); await A.sleep(4000);
    }
  });

  // ----- người chơi
  let B = await h.start('ThuX1'); await B.sleep(1000);
  await R('minecraft:execute at ThuA positioned ~ ~ ~4 positioned over motion_blocking_no_leaves run tp ThuX1 ~ ~ ~ facing entity ThuA'); await B.end(); await A.sleep(2500);
  B = await h.start('ThuX1'); await B.sleep(2500); const abB = tap(B);
  for (let i = 0; i < 10 && !(A.bot.players.ThuX1 && A.bot.players.ThuX1.entity); i++) await A.sleep(1000);
  A.log('ThuA / ThuX1 ở', (await P('ThuA')).map(Math.round), (await P('ThuX1')).map(Math.round));
  await R('effect clear ThuX1');
  const lookB = async () => { const e = A.bot.players.ThuX1 && A.bot.players.ThuX1.entity; if (e) await A.bot.lookAt(e.position.offset(0, 1.4, 0), true); await A.sleep(400); return !!e; };
  A.log('ThuX1 thấy được:', await lookB());
  await T('Hồi máu đồng đội', async () => { await R('minecraft:damage ThuX1 8 minecraft:generic'); await A.sleep(600); const h0 = await hp('ThuX1'); await hold(pp('hoi_mau_dong_doi', 2)); await lookB(); const m = await use(); A.log('Hồi máu đồng đội II:', m, '| ThuX1 máu', h0, '->', await hp('ThuX1'), '(mong +6) | ThuX1 thấy:', abB.slice(-1).join('')); });
  await T('Soi sáng', async () => { await hold(pp('soi_sang', 1)); const m = await use(); A.log('Soi sáng I:', m, '| ThuX1 có Glowing:', /glowing/.test(await R('data get entity ThuX1 active_effects'))); });
  await T('Câm lặng', async () => { await hold(pp('cam_lang', 2)); await lookB(); const m = await use(); await hold(pp('khien_phep', 1), B, 'ThuX1'); const mb = await use(B, abB); A.log('Câm lặng II:', m, '| ThuX1 thấy:', abB.slice(-3).join(' / '), '| ThuX1 thử Khiên phép:', mb, '| tim vàng', await R('data get entity ThuX1 AbsorptionAmount')); await A.sleep(3500); });
  await T('Giải khống chế khi bị câm', async () => { await hold(pp('cam_lang', 3)); await lookB(); await use(); await hold(pp('giai_khong_che', 1), B, 'ThuX1'); const mb = await use(B, abB); A.log('Giải khống chế khi đang Câm lặng:', mb); await A.sleep(1500); });
  await T('Hoá cừu', async () => {
    await R('minecraft:execute at ThuX1 run summon husk ^ ^ ^1.5 {Tags:["thusk","hc"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:max_health",base:200},{id:"minecraft:movement_speed",base:0},{id:"minecraft:follow_range",base:0}],Health:200f}');
    await R('minecraft:clear ThuX1'); await R('minecraft:give ThuX1 minecraft:diamond_sword'); await B.sleep(800); await B.bot.equip(B.bot.inventory.items().find(i => i.name === 'diamond_sword'), 'hand');
    const e = B.bot.nearestEntity(x => x.name === 'husk'); if (e) await B.bot.lookAt(e.position.offset(0, 1, 0), true);
    await hold(pp('hoa_cuu', 3)); await lookB(); const m = await use(); if (e) B.bot.attack(e); await B.sleep(500);
    A.log('Hoá cừu III:', m, '| ThuX1 thấy:', abB.slice(-1).join(''), '| ThuX1 chém husk ngay: 200 ->', await hhp('hc'), '(mong 200)');
  });
  await R('minecraft:clear ThuA'); await R('minecraft:clear ThuX1');
  await B.end(); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
