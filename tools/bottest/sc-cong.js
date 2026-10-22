// Sách cấm ở Công thành (22/10): cần cuộc vây giả đang có phiên đánh (cong-dung.js + cong-vay.js + /swa battlesession start; cờ ở
// x 13631 z -3150, vùng vây bán kính 200). Luật thử: thu_sc_kiem "Không dùng được", thu_sc_que "Không cho mang vào", phù phép Lướt tắt.
// ThuR2 (không thuộc thị trấn): 1) mang que, dịch chuyển vào vùng vây -> chặn; 2) mang que, đi bộ qua mép vùng -> chặn;
// 3) cất que -> vào được; 4) trong vùng: kiếm tự cất, không cầm được; Lướt tắt; que nhận bên trong không dùng được.
const h = require('./h');
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const N = 'ThuR2', FX = 13631, FZ = -3150;
(async () => {
  let t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; const hook = () => { for (const ev of ['system_chat', 'action_bar']) t.bot._client.on(ev, p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v && !/^❤ \[|^▏/.test(v)) msgs.push(v); }); };
  const since = n => msgs.slice(n).filter(x => /⛔|Sách cấm|không mang|Lướt/.test(x)).join(' / ').slice(0, 300);
  const pos = async () => (await R(`data get entity ${N} Pos`)).match(/\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/).slice(1).map(Number);
  const dist = p => Math.hypot(p[0] - FX, p[2] - FZ).toFixed(0);
  const tpTo = async (x, z, rot = '') => { await R(`forceload add ${x} ${z}`); await t.sleep(2500); const r = await R(`execute positioned ${x} 0 ${z} positioned over motion_blocking_no_leaves run minecraft:tp ${N} ~ ~ ~${rot ? ' ' + rot : ''}`); await R(`forceload remove ${x} ${z}`); return r.trim(); };
  await R(`lp user ${N} permission set grim.exempt true`); await R(`gamemode survival ${N}`);
  hook(); t.log('   đặt ngoài vùng:', await tpTo(FX - 230, FZ)); await t.sleep(1500);
  await R(`effect give ${N} minecraft:resistance 900 4 true`);
  await R(`minecraft:clear ${N}`); for (const id of ['thu_sc_kiem', 'thu_sc_que', 'thu_sc_luot']) await R(`dmm laydo ${id} ${N} 1`);
  await t.sleep(2500);
  // 1) dịch chuyển vào vùng vây khi mang que
  let n = msgs.length; let p0 = await pos();
  t.log('   lệnh tp:', await tpTo(FX - 70, FZ)); await t.sleep(1500);
  let p1 = await pos();
  t.log('1) mang que, tp vào vùng vây: cách cờ', dist(p0), '->', dist(p1), 'khối | báo:', since(n));
  // 2) đi bộ qua mép vùng (đứng cách cờ 202 khối, đi về phía cờ 4 giây)
  t.log('   lệnh tp mép:', await tpTo(FX - 204, FZ));
  for (let i = 0; i < 5; i++) { await t.end(); await t.sleep(1500); t = await h.start(N); hook(); await t.sleep(2500); if (Math.abs(t.bot.entity.position.x - (FX - 204)) < 3) break; await tpTo(FX - 204, FZ); }
  t.log('   bot tự thấy', t.bot.entity.position.floored());
  n = msgs.length; p0 = await pos();
  await t.bot.lookAt(new (require('vec3'))(FX, t.bot.entity.position.y + 1.6, FZ), true); t.bot.setControlState('forward', true); t.bot.setControlState('sprint', true); await t.sleep(4000); t.bot.clearControlStates(); await t.sleep(800);
  p1 = await pos();
  t.log('2) mang que, đi bộ về phía cờ: cách cờ', dist(p0), '->', dist(p1), 'khối | báo:', since(n));
  // 3) cất que rồi dịch chuyển vào
  await R(`minecraft:clear ${N}`); await R(`dmm laydo thu_sc_kiem ${N} 1`); await R(`dmm laydo thu_sc_luot ${N} 1`); await t.sleep(1500);
  n = msgs.length;
  t.log('   lệnh tp:', await tpTo(FX - 70, FZ)); await t.sleep(1500);
  p1 = await pos();
  t.log('3) cất que, tp vào: cách cờ', dist(p1), 'khối | báo:', since(n));
  await t.sleep(2000);
  // 4) trong vùng vây
  const find = name => t.bot.inventory.items().find(i => t.itemText(i).name.includes(name));
  const pick = async it => { if (!it) return; if (it.slot < 36) { await t.bot.moveSlotItem(it.slot, 44).catch(() => {}); await t.sleep(500); } t.bot.setQuickBarSlot(it.slot >= 36 ? it.slot - 36 : 8); await t.sleep(1500); };
  n = msgs.length; await pick(find('thu_sc_kiem'));
  t.log('4a) chọn cầm kiếm: tay', t.bot.heldItem ? t.itemText(t.bot.heldItem).name : 'trống', '| báo:', since(n));
  n = msgs.length; await pick(find('thu_sc_luot'));
  const q0 = t.bot.entity.position.clone(); t.bot.activateItem(); await t.sleep(300); t.bot.deactivateItem(); await t.sleep(1200);
  t.log('4b) bấm que Lướt: di chuyển', t.bot.entity.position.distanceTo(q0).toFixed(2), 'khối | báo:', since(n));
  n = msgs.length; await R(`dmm laydo thu_sc_que ${N} 1`); await t.sleep(2000); await pick(find('thu_sc_que'));
  t.log('4c) que nhận bên trong, chọn cầm: tay', t.bot.heldItem ? t.itemText(t.bot.heldItem).name : 'trống', '| báo:', since(n));
  await R(`minecraft:clear ${N}`); await R(`effect clear ${N}`); await R(`minecraft:tp ${N} 12699 80 -2523`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
