// Lượt 2 test phù phép riêng mới: quái triệu hồi TRÊN MẶT ĐẤT (lượt 1 husk kẹt trong cồn cát bị ngạt, số liệu sai), bắt action bar bằng gói tin.
const h = require('./h');
const pp = (id, lv, item = 'minecraft:stick') => `${item}[custom_data={PublicBukkitValues:{"duckymomocore:phuphep_${id}":${lv}}}]`;
const X = 12320, Z = -2610;
(async () => {
  let A = await h.start('ThuA'); for (let i = 0; i < 3 && Math.abs(A.bot.entity.position.x - X) > 5; i++) { A = await h.moveTo(A, X, Z); await A.sleep(3000); }
  const R = c => A.rc.cmd(c);
  const abOf = t => { const ab = []; const push = p => { try { ab.push(h.flatText ? h.flatText(p) : JSON.stringify(p).replace(/\\"/g, '"').replace(/.*?"text":"([^"]*)".*/, '$1')); } catch (e) {} };
    t.bot._client.on('action_bar', push); t.bot._client.on('set_action_bar_text', push); t.bot._client.on('system_chat', p => { if (p.isActionBar || p.overlay) push(p); }); return ab; };
  const ab = abOf(A);
  const num = s => parseFloat(String(s).replace(/.*data: /, '').replace(/[^\d.\-]/g, ''));
  const P = async n => (await R(`data get entity ${n} Pos`)).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  const hp = async n => num(await R(`data get entity ${n} Health`));
  const hold = async give => { await R('minecraft:clear ThuA'); await R(`minecraft:give ThuA ${give}`); await A.sleep(900); const it = A.bot.inventory.items()[0]; if (it) await A.bot.equip(it, 'hand'); await A.sleep(400); };
  const use = async (t = A, abl = ab) => { const n = abl.length; t.bot.activateItem(); await A.sleep(250); t.bot.deactivateItem(); await A.sleep(500); return abl.slice(n).join(' | ').slice(0, 200); };
  const husk = async (dx, dz, tag, speed = 0) => { await R(`minecraft:execute at ThuA positioned ~${dx} ~ ~${dz} positioned over motion_blocking_no_leaves run summon husk ~ ~ ~ {Tags:["thusk","${tag}"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:max_health",base:200},{id:"minecraft:movement_speed",base:${speed}},{id:"minecraft:follow_range",base:0}],Health:200f}`); await A.sleep(1000); };
  const sel = tag => `@e[type=husk,tag=${tag},limit=1]`;
  const hhp = async tag => num(await R(`data get entity ${sel(tag)} Health`));
  const hpos = async tag => (await R(`data get entity ${sel(tag)} Pos`)).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  const look = async (dy = 1.2) => { const e = A.bot.nearestEntity(x => x.name === 'husk'); if (e) await A.bot.lookAt(e.position.offset(0, dy, 0), true); await A.sleep(400); };
  const d = (a, b) => Math.hypot(a[0] - b[0], a[2] - b[2]).toFixed(1);
  const T = async (name, f) => { try { await f(); } catch (e) { A.log(name, 'LỖI', e.message); } await R('minecraft:kill @e[type=husk,tag=thusk]'); await A.sleep(400); };

  await T('Thu nhỏ', async () => { await hold(pp('thu_nho', 3)); await use(); A.log('Thu nhỏ III:', await A.attr('minecraft:scale'), '(mong 0,5; jar cũ ra 0,45 nếu chưa build lại)'); await R('attribute ThuA minecraft:scale modifier remove duckymomocore:phuphep_kichthuoc'); });
  await T('Móc kéo', async () => { await husk(0, 8, 'm', 0.0001); await hold(pp('moc_keo', 2)); await look(); const a = await P('ThuA'), b = await hpos('m'); const m = await use(); await A.sleep(1500); A.log('Móc kéo II:', m, '| husk cách', d(a, b), '->', d(a, await hpos('m')), '| máu', await hhp('m')); });
  await T('Hố đen', async () => { await husk(1, 7, 'o', 0.0001); await hold(pp('ho_den', 2)); await look(0); const b = await hpos('o'); const m = await use(); await A.sleep(400); await R('minecraft:execute at ThuA run summon husk ~3 ~ ~7 {Tags:["thusk","o2"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:movement_speed",base:0.0001},{id:"minecraft:follow_range",base:0}]}'); await A.sleep(2000); A.log('Hố đen II:', m, '| husk tâm dời', d(b, await hpos('o')), 'khối'); });
  await T('Tia sáng', async () => { await husk(0, 7, 't'); await hold(pp('tia_sang', 2)); await look(); const m = await use(); await A.sleep(1200); A.log('Tia sáng II:', m, '| 200 ->', await hhp('t'), '(6 - giáp ≈ 5,5)'); });
  await T('Lốc xoáy', async () => { await husk(1.5, 0, 'l'); await hold(pp('loc_xoay', 2)); const m = await use(); await A.sleep(3200); A.log('Lốc xoáy II:', m, '| 200 ->', await hhp('l'), '(6 × 1,84 ≈ 11)'); });
  await T('Pháo kích', async () => { await husk(0, 7, 'p'); await hold(pp('phao_kich', 2)); await look(0.1); const m = await use(); await A.sleep(2300); A.log('Pháo kích II:', m, '| 200 ->', await hhp('p')); });
  await T('Sét nảy', async () => { await husk(0, 6, 's1'); await husk(2, 6, 's2'); await husk(-2, 7, 's3'); await hold(pp('set_nay', 2)); await look(); const m = await use(); await A.sleep(800); A.log('Sét nảy II:', m, '| máu', await hhp('s1'), await hhp('s2'), await hhp('s3'), '(3,68 / 2,94 / 2,36 trừ giáp)'); });
  await T('Rút bài', async () => {
    await husk(0, 2.5, 'r', 0.23); await hold(pp('rut_bai', 2, 'minecraft:diamond_sword')); const m1 = await use(); await A.sleep(1300); const m2 = await use(); await look();
    const e = A.bot.nearestEntity(x => x.name === 'husk'); A.bot.attack(e); await A.sleep(500);
    A.log('Rút bài II:', m1, '||', m2, '| husk máu', await hhp('r'), '(kiếm 7,36; Đỏ thêm 3 + chậm)', '| tốc', await R(`attribute ${sel('r')} minecraft:movement_speed get`), '| hiệu ứng', (await R(`data get entity ${sel('r')} active_effects`)).slice(-70), '| máu ThuA', await hp('ThuA'));
  });
  await T('Khiêu khích', async () => { await R('minecraft:execute at ThuA positioned ~4 ~ ~4 positioned over motion_blocking_no_leaves run summon zombie ~ ~ ~ {Tags:["thusk"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:follow_range",base:0}]}'); await A.sleep(800); await hold(pp('khieu_khich', 1)); const m = await use(); A.log('Khiêu khích I:', m); await R('minecraft:kill @e[type=zombie,tag=thusk]'); });
  await T('Tua thời gian', async () => {
    await R('effect give ThuA minecraft:instant_health 1 5'); await A.sleep(400); await R('minecraft:damage ThuA 6 minecraft:generic'); await A.sleep(600);
    await hold(pp('tua_thoi_gian', 3)); const a = await P('ThuA'); const h0 = await hp('ThuA'); const m = await use();
    await R(`minecraft:tp ThuA ${a[0] + 5} ${a[1]} ${a[2]}`); await R('minecraft:damage ThuA 6 minecraft:generic'); await A.sleep(400); const h1 = await hp('ThuA');
    await A.sleep(4200); A.log('Tua thời gian III:', m, '| cách mốc', d(a, await P('ThuA')), '| máu', h0, '->', h1, '->', await hp('ThuA'), '(mong về', h0, ')');
  });
  await T('Chắn đạn', async () => {
    await R('effect give ThuA minecraft:instant_health 1 5'); await A.sleep(400);
    await hold(pp('chan_dan', 2)); const m = await use(); const p = await P('ThuA'); const h0 = await hp('ThuA');
    const yaw = A.bot.entity.yaw, fx = -Math.sin(yaw), fz = -Math.cos(yaw);
    for (const [name, sx] of [['trước mặt', 1], ['sau lưng', -1]]) {
      const h1 = await hp('ThuA');
      await R(`minecraft:summon arrow ${(p[0] + fx * 4 * sx).toFixed(2)} ${(p[1] + 1.5).toFixed(2)} ${(p[2] + fz * 4 * sx).toFixed(2)} {Motion:[${(-fx * 2 * sx).toFixed(2)},0.05,${(-fz * 2 * sx).toFixed(2)}],damage:3d,Tags:["thuar"]}`);
      await A.sleep(700); A.log('Chắn đạn II, tên bắn từ', name, ':', m, '| máu', h1, '->', await hp('ThuA'));
    }
    await R('minecraft:kill @e[type=arrow,tag=thuar]');
  });

  // ----- chiêu nhắm người chơi: ThuX1 đứng cách 4 khối, quay mặt về phía ThuA
  let B = await h.start('ThuX1'); await B.sleep(1000);
  const a = await P('ThuA'); await R(`minecraft:execute at ThuA positioned ~ ~ ~4 positioned over motion_blocking_no_leaves run tp ThuX1 ~ ~ ~ facing entity ThuA`); await B.end(); await A.sleep(2500);
  B = await h.start('ThuX1'); await B.sleep(2500); const abB = abOf(B);
  await R('effect give ThuX1 minecraft:instant_health 1 5');
  const lookB = async () => { const e = A.bot.players.ThuX1 && A.bot.players.ThuX1.entity; if (e) await A.bot.lookAt(e.position.offset(0, 1.4, 0), true); await A.sleep(400); return !!e; };
  const holdB = async give => { await R('minecraft:clear ThuX1'); await R(`minecraft:give ThuX1 ${give}`); await B.sleep(900); const it = B.bot.inventory.items()[0]; if (it) await B.bot.equip(it, 'hand'); await B.sleep(400); };
  A.log('ThuX1 thấy được:', await lookB(), '| ThuX1 cách', d(a, await P('ThuX1')));
  await T('Hồi máu đồng đội', async () => { await R('minecraft:damage ThuX1 8 minecraft:generic'); await A.sleep(600); const h0 = await hp('ThuX1'); await hold(pp('hoi_mau_dong_doi', 2)); await lookB(); const m = await use(); A.log('Hồi máu đồng đội II:', m, '| ThuX1 máu', h0, '->', await hp('ThuX1'), '(mong +6) | ThuX1 thấy:', abB.slice(-1).join('')); });
  await T('Soi sáng', async () => { await hold(pp('soi_sang', 1)); const m = await use(); A.log('Soi sáng I:', m, '| ThuX1 hiệu ứng:', (await R('data get entity ThuX1 active_effects')).replace(/.*data: /, '').slice(0, 120)); });
  await T('Câm lặng', async () => { await hold(pp('cam_lang', 1)); await lookB(); const m = await use(); await holdB(pp('khien_phep', 1)); const mb = await use(B, abB); A.log('Câm lặng I:', m, '| ThuX1 thử dùng Khiên phép:', mb); await B.sleep(2200); });
  await T('Lá chắn phép', async () => {
    await holdB(pp('la_chan', 2)); const mb = await use(B, abB); await hold(pp('troi', 2)); await lookB(); const m = await use();
    A.log('Lá chắn phép II (ThuX1) chặn Trói (ThuA):', mb, '||', m, '| ThuX1 tốc', await R('attribute ThuX1 minecraft:movement_speed get'));
  });
  await T('Ánh mắt hoá đá', async () => { await R('execute as ThuX1 at ThuX1 run tp ThuX1 ~ ~ ~ facing entity ThuA'); await A.sleep(300); await hold(pp('hoa_da', 2)); await lookB(); const m = await use(); A.log('Ánh mắt hoá đá II:', m, '| ThuX1 tốc', await R('attribute ThuX1 minecraft:movement_speed get'), '(mong 0 nếu PvP được)'); await A.sleep(1600); });
  await T('Hoá cừu', async () => {
    await hold(pp('hoa_cuu', 3)); await lookB(); const m = await use();
    await R('minecraft:execute at ThuX1 run summon husk ^ ^ ^1.5 {Tags:["thusk","hc"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:max_health",base:200},{id:"minecraft:movement_speed",base:0},{id:"minecraft:follow_range",base:0}],Health:200f}'); await B.sleep(700);
    await R('minecraft:give ThuX1 minecraft:diamond_sword'); await B.sleep(600); await B.bot.equip(B.bot.inventory.items().find(i => i.name === 'diamond_sword'), 'hand');
    const e = B.bot.nearestEntity(x => x.name === 'husk'); if (e) { await B.bot.lookAt(e.position.offset(0, 1, 0), true); B.bot.attack(e); } await B.sleep(500);
    A.log('Hoá cừu III:', m, '| ThuX1 chém husk: 200 ->', await hhp('hc'), '(mong 200 nếu hoá cừu trúng)');
  });
  await T('Hoảng sợ', async () => { await A.sleep(1500); await hold(pp('hoang_so', 1)); await lookB(); const b0 = await P('ThuX1'); const m = await use(); await A.sleep(600); A.log('Hoảng sợ I:', m, '| ThuX1 bị đẩy', d(b0, await P('ThuX1')), 'khối | hiệu ứng', (await R('data get entity ThuX1 active_effects')).replace(/.*data: /, '').slice(0, 100)); });
  await T('Hộ giá', async () => { await hold(pp('ho_gia', 1)); await lookB(); const m = await use(); A.log('Hộ giá I:', m, '(ThuA, ThuX1 không cùng thị trấn thì mong bị từ chối)'); });
  await R('minecraft:clear ThuA'); await R('minecraft:clear ThuX1');
  await B.end(); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
