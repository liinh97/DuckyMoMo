// Lượt 5: Hoá cừu (quái trên mặt đất + đối chứng) và Chắn đạn (cùng 1 bộ xương: 4,5 giây không chắn rồi 4,5 giây có chắn).
const h = require('./h');
const pp = (id, lv, item = 'minecraft:stick') => `${item}[custom_data={PublicBukkitValues:{"duckymomocore:phuphep_${id}":${lv}}}]`;
const X = 12320, Z = -2610;
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const tap = t => { const ab = []; const push = p => { const v = txt(p.text ?? p.content ?? p).trim(); if (v && !/^❤ \[|GIAO TRANH/.test(v)) ab.push(v); }; for (const ev of ['action_bar', 'set_action_bar_text', 'system_chat']) t.bot._client.on(ev, push); return ab; };
const go = async (t, x, z) => { for (let i = 0; i < 4 && (Math.abs(t.bot.entity.position.x - x) > 3 || Math.abs(t.bot.entity.position.z - z) > 3); i++) { t = await h.moveTo(t, x, z); await t.sleep(2500); } return t; };
(async () => {
  let A = await go(await h.start('ThuA'), X, Z); const R = c => A.rc.cmd(c);
  const num = s => parseFloat(String(s).replace(/.*data: /, '').replace(/[^\d.\-]/g, ''));
  const hp = async n => num(await R(`data get entity ${n} Health`));
  const hold = async (give, t, n) => { await R(`minecraft:clear ${n}`); await R(`minecraft:give ${n} ${give}`); await t.sleep(900); const it = t.bot.inventory.items()[0]; if (it) await t.bot.equip(it, 'hand'); await t.sleep(400); };
  const use = async t => { t.bot.activateItem(); await t.sleep(250); t.bot.deactivateItem(); await t.sleep(600); };
  // Chắn đạn
  await R('gamemode survival ThuA'); await R('effect clear ThuA'); await R('effect give ThuA minecraft:instant_health 1 5');
  const yaw = A.bot.entity.yaw, fx = -Math.sin(yaw), fz = -Math.cos(yaw);
  await R(`minecraft:execute at ThuA positioned ~${(fx * 7).toFixed(2)} ~ ~${(fz * 7).toFixed(2)} positioned over motion_blocking_no_leaves run summon skeleton ~ ~ ~ {Tags:["thusk"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:movement_speed",base:0},{id:"minecraft:follow_range",base:20}],equipment:{mainhand:{id:"minecraft:bow",count:1}}}`);
  await A.sleep(1000); let h0 = await hp('ThuA'); await A.sleep(4500); A.log('Đối chứng không chắn: máu', h0, '->', await hp('ThuA'));
  await R('effect give ThuA minecraft:instant_health 1 5'); await hold(pp('chan_dan', 3), A, 'ThuA');
  { const e = A.bot.nearestEntity(x => x.name === 'skeleton'); if (e) await A.bot.lookAt(e.position.offset(0, 1.5, 0), true); }
  await use(A); h0 = await hp('ThuA'); await A.sleep(4500); A.log('Chắn đạn III nhìn về bộ xương: máu', h0, '->', await hp('ThuA'));
  await R('minecraft:kill @e[type=skeleton,tag=thusk]'); await R('minecraft:kill @e[type=arrow]');
  // Hoá cừu
  let B = await go(await h.start('ThuX1'), X, Z + 4); A = await go(A, X, Z);
  for (let i = 0; i < 8 && !(A.bot.players.ThuX1 && A.bot.players.ThuX1.entity); i++) await A.sleep(1000);
  await R('effect clear ThuX1');
  const sum = () => R('minecraft:execute at ThuX1 positioned ~1.5 ~ ~ positioned over motion_blocking_no_leaves run summon husk ~ ~ ~ {Tags:["thusk","hc"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:max_health",base:200},{id:"minecraft:movement_speed",base:0},{id:"minecraft:follow_range",base:0}],Health:200f}');
  const hc = async () => num(await R('data get entity @e[type=husk,tag=hc,limit=1] Health'));
  await sum(); await R('minecraft:clear ThuX1'); await R('minecraft:give ThuX1 minecraft:diamond_sword'); await B.sleep(900); await B.bot.equip(B.bot.inventory.items().find(i => i.name === 'diamond_sword'), 'hand');
  let e = B.bot.nearestEntity(x => x.name === 'husk'); await B.bot.lookAt(e.position.offset(0, 1, 0), true); await B.sleep(1500);
  const c0 = await hc(); B.bot.attack(e); await B.sleep(700); A.log('Đối chứng ThuX1 chém husk:', c0, '->', await hc());
  await R('minecraft:kill @e[type=husk,tag=hc]'); await sum(); await B.sleep(1200); e = B.bot.nearestEntity(x => x.name === 'husk'); await B.bot.lookAt(e.position.offset(0, 1, 0), true);
  await hold(pp('hoa_cuu', 3), A, 'ThuA'); { const pe = A.bot.players.ThuX1.entity; await A.bot.lookAt(pe.position.offset(0, 1.4, 0), true); await A.sleep(300); }
  const abA = tap(A); const x0 = await hp('ThuX1'); const c1 = await hc(); await use(A); A.log('  ThuA thấy:', abA.join(' | ')); B.bot.attack(e); await B.sleep(700);
  A.log('Hoá cừu III: ThuX1 máu', x0, '->', await hp('ThuX1'), '(đòn thử -1) | ThuX1 chém husk:', c1, '->', await hc(), '(mong không đổi)');
  await R('minecraft:kill @e[type=husk,tag=hc]'); await R('minecraft:clear ThuA'); await R('minecraft:clear ThuX1');
  await B.end(); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
