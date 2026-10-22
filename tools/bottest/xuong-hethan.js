// Đồ Xưởng hết hạn (22/10): /xuong tang, đổi dấu xuong_het còn 20 giây (item modify set_custom_data), một món cầm tay một món để túi;
// sau hạn: bấm dùng món cầm tay -> phải biến mất; món trong túi -> lượt quét 60 giây phải xoá; cả hai có câu báo.
const h = require('./h');
const N = 'ThuR3';
(async () => {
  let t = await h.start(N); t = await h.moveTo(t, 13200, -2900); const R = c => t.rc.cmd(c); await t.sleep(2500);
  const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
  const msgs = []; for (const ev of ['system_chat', 'action_bar']) t.bot._client.on(ev, p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(((Date.now() - (globalThis.T0 || 0)) / 1000).toFixed(0) + 's ' + v); });
  await R(`minecraft:clear ${N}`); await R(`effect give ${N} minecraft:resistance 400 4 true`); await R(`minecraft:kill @e[type=!player,type=!item,distance=..40,x=13200,y=66,z=-2900]`);
  t.log((await R(`xuong tang khoan_dao ${N}`)).trim()); t.log((await R(`xuong tang gat_lua ${N}`)).trim()); await t.sleep(800);
  t.log('dấu:', (await R(`data get entity ${N} Inventory[0].components."minecraft:custom_data".PublicBukkitValues`)).replace(/.*data: /, '').slice(0, 300));
  const until = Date.now() + 20000;
  for (const s of ['hotbar.0', 'hotbar.1']) t.log(s, (await R(`minecraft:item modify entity ${N} ${s} {"function":"minecraft:set_custom_data","tag":"{PublicBukkitValues:{\\"duckymomocore:xuong_het\\":${until}L}}"}`)).trim());
  t.log('dấu sau sửa:', (await R(`data get entity ${N} Inventory[0].components."minecraft:custom_data".PublicBukkitValues."duckymomocore:xuong_het"`)).replace(/.*data: /, ''));
  t.bot.setQuickBarSlot(0); const n0 = t.chat.length; globalThis.T0 = until - 20000;
  for (let i = 0; i < 5; i++) { await t.sleep(5000); t.log(`+${(i + 1) * 5}s cuốc:`, (await R(`minecraft:clear ${N} minecraft:diamond_pickaxe 0`)).replace(/.*Found /, ''), '| liềm:', (await R(`minecraft:clear ${N} minecraft:diamond_hoe 0`)).replace(/.*Found /, '')); }
  const b = t.bot.blockAt(t.bot.entity.position.offset(0, -1, 0)); t.log('đào khối dưới chân bằng món hết hạn:', b && b.name);
  const P = t.bot.entity.position.floored(); await R(`setblock ${P.x + 1} ${P.y} ${P.z} minecraft:stone`); await t.sleep(800);
  const st = t.bot.blockAt(P.offset(1, 0, 0)); t.log('khối cạnh:', st && st.name);
  const loc = { x: P.x + 1, y: P.y, z: P.z }; t.bot._client.write('block_dig', { status: 0, location: loc, face: 1, sequence: 1 }); await t.sleep(1200); t.bot._client.write('block_dig', { status: 2, location: loc, face: 1, sequence: 2 }); await t.sleep(1500); t.log('khối sau đào:', t.bot.blockAt(P.offset(1, 0, 0)).name);
  t.log('sau khi dùng:', (await R(`minecraft:clear ${N} minecraft:diamond_pickaxe 0`)).replace(/.*Found |.*No items/, '$&'), '| cuốc/liềm trong túi:', (await R(`minecraft:clear ${N} minecraft:diamond_hoe 0`)));
  await t.sleep(65000);
  t.log('sau lượt quét 60 giây: liềm', (await R(`minecraft:clear ${N} minecraft:diamond_hoe 0`)));
  t.log('báo:', msgs.filter(x => !/❤ \[/.test(x)).join(' | ').slice(0, 1500));
  t.log('túi còn:', t.bot.inventory.items().map(i => i.name).join(', '));
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
