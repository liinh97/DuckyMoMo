// a5–a8 Thái Ấp Sáng Tạo. Bot vào thẳng server (không qua proxy) nên lệnh chuyển server (/thaiap ve, cổng) chỉ ghi vé + báo tin,
// bot tự vào server kia để giả lập: A (có Thái Ấp) bước vào cổng -> có vé -> vào sangtao-1 = sáng tạo ở đúng mảnh đất;
// F (không được mời) bước vào cổng bị đẩy ra; vào thẳng sangtao-1 không vé bị chặn; đồ chế gửi bằng gói sáng tạo bị đổi bản trơn;
// TNT không nổ; về Bang Hội Chiến túi đồ sinh tồn còn nguyên.
const { start } = require('./h');
const A = 'ThuA', F = 'ThuF';
const rejoin = async (t, name, host) => { await t.end(); await t.sleep(2500); const t2 = await start(name, undefined, host); await t2.sleep(1500); return t2; };
const walkIn = async t => { t.bot.setControlState('forward', true); await t.sleep(1800); t.bot.setControlState('forward', false); await t.sleep(1500); };
(async () => {
  let a = await start(A);
  const R = c => a.rc.cmd(c);
  await R(`minecraft:clear ${A}`); await R(`minecraft:give ${A} minecraft:apple 7`); await R(`gamemode survival ${A}`);
  // a5: A tới cổng, bước vào
  a.log('a5 /thaiap sangtao:', (await a.say('/thaiap sangtao', 2500)).join(' | '));
  a = await rejoin(a, A);
  a.log('   trước cổng:', a.bot.entity.position.floored(), 'yaw', a.bot.entity.yaw.toFixed(2));
  let n = a.chat.length; await walkIn(a);
  a.log('a5 bước vào cổng:', a.chat.slice(n).join(' | '));
  await a.end(); await a.sleep(1500);
  // F không được mời
  let f = await start(F); await f.rc.cmd(`gamemode survival ${F}`);
  f.log('a6 F /thaiap sangtao:', (await f.say('/thaiap sangtao', 2500)).join(' | '));
  f = await rejoin(f, F);
  n = f.chat.length; await walkIn(f);
  f.log('a6 F bước vào cổng:', f.chat.slice(n).join(' | '));
  await f.end(); await f.sleep(1500);
  // A vào sangtao-1 bằng vé
  let s = await start(A, undefined, 'sangtao-1'); await s.sleep(2500);
  const S = c => s.rc.cmd(c);
  s.log('a5 A ở sangtao-1:', s.chat.join(' | ').slice(-500));
  s.log('   chế độ', (await S(`data get entity ${A} playerGameType`)).replace(/.*data: /, ''), '| vị trí', (await S(`data get entity ${A} Pos`)).replace(/.*data: /, ''), '| túi:', s.bot.inventory.items().map(i => i.name + 'x' + i.count).join(','));
  s.log('   /thaiap ở sangtao:', (await s.say('/thaiap moi ThuF', 1500)).join(' | '));
  // a8: gửi đồ chế qua gói sáng tạo
  const reg = s.bot.registry;
  const send = async (slot, comp, label) => {
    s.bot._client.write('set_creative_slot', { slot, item: { itemCount: 1, itemId: reg.itemsByName.diamond_sword.id, addedComponentCount: comp.length, removedComponentCount: 0, components: comp, removeComponents: [] } });
    await s.sleep(1200);
  };
  // mã phù phép 1.21.4 (registry xếp theo tên): sharpness = 32
  const ench = { id: 32 };
  n = s.chat.length;
  await send(36, [{ type: 'enchantments', data: { enchantments: [{ id: ench ? ench.id : 13, level: 255 }], showTooltip: true } }], 'sắc bén 255');
  await send(37, [{ type: 'unbreakable', data: true }], 'không hỏng');
  await send(38, [{ type: 'enchantments', data: { enchantments: [{ id: ench ? ench.id : 13, level: 1 }], showTooltip: true } }], 'cấp 1');
  s.log('a8 tin:', s.chat.slice(n).join(' | '));
  for (const sl of [0, 1, 2]) s.log(`   ô ${sl}:`, (await S(`data get entity ${A} Inventory[{Slot:${sl}b}]`)).replace(/.*data: /, '').slice(0, 200));
  // TNT
  const pos = (await S(`data get entity ${A} Pos`)).match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/);
  const [x, y, z] = pos.slice(1).map(v => Math.floor(+v));
  const dim = (await S(`data get entity ${A} Dimension`)).replace(/.*data: /, '').replace(/"/g, '');
  const W = c => S(`execute in ${dim} run ${c}`);
  s.log('a8 thế giới', dim, await W(`setblock ${x + 3} ${y - 1} ${z} minecraft:stone`));
  s.log('a8 TNT:', await W(`summon tnt ${x + 3} ${y} ${z} {fuse:20}`)); await s.sleep(2500);
  s.log('   sau 2,5s: còn TNT', await W('execute if entity @e[type=tnt]'), '| đá dưới còn', await W(`execute if block ${x + 3} ${y - 1} ${z} minecraft:stone`));
  // a7 về
  s.log('a7 /thaiap ve:', (await s.say('/thaiap ve', 1500)).join(' | '));
  await s.end(); await s.sleep(2000);
  // F vào thẳng sangtao-1 không vé
  let g = await start(F, undefined, 'sangtao-1'); await g.sleep(2500);
  g.log('a6 F vào thẳng sangtao-1:', g.chat.join(' | ').slice(-400), '| chế độ', (await g.rc.cmd(`data get entity ${F} playerGameType`)).replace(/.*data: /, ''));
  await g.end(); await g.sleep(1500);
  // A về banghoi-1
  a = await start(A); await a.sleep(1500);
  a.log('a7 A về Bang Hội Chiến: chế độ', (await R(`data get entity ${A} playerGameType`)).replace(/.*data: /, ''), '| túi:', a.bot.inventory.items().map(i => i.name + 'x' + i.count).join(','), '| vị trí', a.bot.entity.position.floored());
  await R(`minecraft:clear ${A}`);
  await a.end();
})().catch(e => { console.log('FAIL', e); process.exit(1); });
