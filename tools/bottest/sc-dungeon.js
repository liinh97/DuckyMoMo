// Sách cấm ở Hầm ngục (22/10): luật thử dungeon = thu_sc_kiem "Không dùng được", thu_sc_que "Không cho mang vào", phù phép riêng Lướt tắt.
// 1) dòng ⛔ trên đồ; 2) mang que vào dungeon (Đỉnh Vách Đá, /dungeon vao + chọn độ khó) -> bị chặn; 3) cất que -> vào được em_id_…;
// 4) trong dungeon: không cầm được kiếm, que Lướt bấm không lướt; que đưa vào bên trong -> như Không dùng được. 5) ra lại thế giới thường.
const h = require('./h');
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const N = 'ThuR1';
(async () => {
  let t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; const hook = b => { for (const ev of ['system_chat', 'action_bar']) b._client.on(ev, p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v && !/^❤ \[|^▏/.test(v)) msgs.push(v); }); };
  hook(t.bot);
  const dim = async () => (await R(`data get entity ${N} Dimension`)).replace(/.*data: /, '');
  const since = n => msgs.slice(n).filter(x => /⛔|Sách cấm|không mang|Lướt/.test(x)).join(' / ').slice(0, 300);
  await R(`minecraft:clear ${N}`); await R(`effect give ${N} minecraft:resistance 600 4 true`);
  for (const id of ['thu_sc_kiem', 'thu_sc_que', 'thu_sc_luot']) await R(`dmm laydo ${id} ${N} 1`);
  await t.sleep(2500);
  for (const it of t.bot.inventory.items()) t.log('1) mô tả', t.itemText(it).name, '->', t.itemText(it).lore.filter(l => /⛔/.test(l)).join(' / '));
  const enter = async label => {
    const n = msgs.length;
    const w = await t.openMenu('/dungeon vao the_climb_dungeon');
    if (w) { await t.click(2); await t.sleep(800); if (t.bot.currentWindow) await t.click(2); }   // cấp độ, rồi "Bắt đầu độ khó thường"
    for (let i = 0; i < 20 && !/em_/.test(await dim()); i++) await t.sleep(1000);
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
    t.log(label, '-> thế giới', await dim(), '| báo:', since(n));
  };
  await enter('2) mang que (Không cho mang vào)');
  await R(`minecraft:clear ${N} minecraft:stick[minecraft:custom_name]`);   // cất que thử (que Lướt cũng mất -> đưa lại sau)
  await R(`minecraft:clear ${N} minecraft:stick`); await t.sleep(800);
  await enter('3) đã cất que');
  if (!/em_/.test(await dim())) { t.log('không vào được dungeon, dừng'); await t.end(); process.exit(0); }
  await t.sleep(3000);
  t = await (async () => t)();
  // 4) trong dungeon
  await R(`minecraft:clear ${N}`); await R(`dmm laydo thu_sc_kiem ${N} 1`); await R(`dmm laydo thu_sc_luot ${N} 1`); await t.sleep(2000);
  const slotOf = name => t.bot.inventory.items().find(i => t.itemText(i).name.includes(name));
  const pick = async it => { if (!it) return; if (it.slot < 36) { await t.bot.moveSlotItem(it.slot, 44).catch(() => {}); await t.sleep(500); } t.bot.setQuickBarSlot(it.slot >= 36 ? it.slot - 36 : 8); await t.sleep(1500); };
  let n = msgs.length;
  const k = slotOf('thu_sc_kiem');
  await pick(k);
  t.log('4a) chọn ô kiếm: tay đang cầm', t.bot.heldItem ? t.itemText(t.bot.heldItem).name : 'trống', '| server:', (await R(`data get entity ${N} SelectedItem.components."minecraft:custom_name"`)).replace(/.*data: /, '').slice(0, 60), '| báo:', since(n));
  n = msgs.length;
  const l = slotOf('thu_sc_luot');
  await pick(l);
  const p0 = t.bot.entity.position.clone(); t.bot.activateItem(); await t.sleep(300); t.bot.deactivateItem(); await t.sleep(1200);
  t.log('4b) bấm que Lướt: di chuyển', t.bot.entity.position.distanceTo(p0).toFixed(2), 'khối | báo:', since(n));
  n = msgs.length; await R(`dmm laydo thu_sc_que ${N} 1`); await t.sleep(2000);
  const q = slotOf('thu_sc_que');
  await pick(q);
  t.log('4c) que đưa vào trong dungeon, chọn cầm: tay', t.bot.heldItem ? t.itemText(t.bot.heldItem).name : 'trống', '| báo:', since(n));
  // 5) ra
  await R(`minecraft:clear ${N}`); await R(`execute in minecraft:overworld run minecraft:tp ${N} 12699 80 -2523`); await t.sleep(3000);
  t.log('5) ra lại:', await dim());
  await R(`effect clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
