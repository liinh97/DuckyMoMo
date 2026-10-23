// Quầy đổi (23/10): NPC Lão Khoáng (đổi thô -> mảnh), Thợ Kim Hoàn (ghép tinh thể), mảnh GIẢ (cùng vật phẩm + tên + lấp lánh, không dấu) không được tính,
// mảnh thật không đặt / ném được.
const h = require('./h');
const N = process.env.BOTN || 'ThuQuay';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await R(`lp user ${N} parent add quanly`); await R(`minecraft:clear ${N}`); await R(`eco set ${N} 5000`);
  for (const [m, n] of [['coal', 64], ['coal', 64], ['coal', 64], ['raw_iron', 64], ['raw_iron', 64], ['raw_copper', 64], ['raw_copper', 32]]) await R(`minecraft:give ${N} minecraft:${m} ${n}`);
  await R(`minecraft:give ${N} minecraft:iron_nugget[minecraft:custom_name='{"text":"✦ Mảnh Sắt Nguội","color":"white"}',minecraft:enchantment_glint_override=true] 8`);
  await t.sleep(3000);
  const villager = () => Object.values(t.bot.entities).filter(e => e.name === 'villager').sort((a, b) => a.position.distanceTo(t.bot.entity.position) - b.position.distanceTo(t.bot.entity.position))[0];
  const show = w => w.slots.slice(0, w.inventoryStart).forEach((it, i) => { if (!it || /pane$/.test(it.name)) return; const x = t.itemText(it); t.log(`  [${i}] ${it.name} | ${x.name} | ${x.lore.join(' ¦ ').slice(0, 170)}`); });
  const slotOf = (w, re) => w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
  const openNpc = async () => { const v = villager(); if (!v) return null; const p = new Promise(res => { t.bot.once('windowOpen', w => res(w)); setTimeout(() => res(null), 4000); }); await t.use(v); return p; };
  t.log('## đặt Lão Khoáng:', (await t.say('/dmm thorennpc khoang', 2000)).join(' / '));
  let w = await openNpc(); if (!w) { t.log('KHÔNG MỞ ĐƯỢC NPC'); } else {
    t.log('## menu Lão Khoáng'); show(w);
    for (const [re, k] of [[/Than Hồng/, 3], [/Sắt Nguội/, 4], [/Đồng Cổ/, 2]]) for (let i = 0; i < k; i++) { await t.click(slotOf(t.bot.currentWindow, re)); await t.sleep(600); }
    t.log('   đổi xong:', msgs.filter(m => /Đã đổi/.test(m)).length, 'lượt');
    t.bot.closeWindow(t.bot.currentWindow);
  }
  await t.sleep(800);
  
  await t.say('/dmm thorennpc remove khoang', 1200); await t.sleep(800); t.log('## đặt Thợ Kim Hoàn:', (await t.say('/dmm thorennpc kimhoan', 2000)).join(' / ')); await t.sleep(1000);
  w = await openNpc();
  if (w) {
    const it = w.slots[slotOf(w, /Phôi Hợp Kim/)]; t.log('## Kim Hoàn · Phôi Hợp Kim (túi có 4 mảnh Sắt THẬT + 8 mảnh Sắt GIẢ):', it ? t.itemText(it).lore.join(' ¦ ') : '-');
    const n = msgs.length; await t.click(slotOf(w, /Phôi Hợp Kim/)); await t.sleep(1000);
    t.log('   bấm ghép ->', msgs.slice(n).join(' / '));
    t.bot.closeWindow(t.bot.currentWindow);
  } else t.log('KHÔNG MỞ ĐƯỢC KIM HOÀN');
  const real = t.bot.inventory.items().find(i => /Phôi Hợp Kim/.test(t.itemText(i).name));
  t.log('## túi:', t.bot.inventory.items().map(i => `${t.itemText(i).name}×${i.count}`).join(', '));
  // dùng sai: đặt Phôi Hợp Kim (heavy core), ném Lệ Ender
  await R(`dmm laydo le_ender 2 ${N}`); await t.sleep(1000);
  const pos = t.bot.entity.position.floored();
  if (real) { await t.bot.equip(real, 'hand'); const below = t.bot.blockAt(pos.offset(1, -1, 0)); await t.bot.placeBlock(below, new (require('vec3'))(0, 1, 0)).catch(e => {}); await t.sleep(800);
    t.log('## đặt Phôi Hợp Kim xuống ->', (await R(`execute if block ${pos.x + 1} ${pos.y} ${pos.z} minecraft:heavy_core`)).replace(/\x1b\[[0-9;]*m/g, '') || 'không có khối (bị chặn)'); }
  const le = t.bot.inventory.items().find(i => /Lệ Ender/.test(t.itemText(i).name));
  if (le) { await t.bot.equip(le, 'hand'); t.bot.activateItem(); await t.sleep(1500); const after = t.bot.inventory.items().filter(i => /Lệ Ender/.test(t.itemText(i).name)).reduce((a, i) => a + i.count, 0); t.log('## ném Lệ Ender: còn', after, '/ 2'); }
  t.log('## gỡ NPC:', (await t.say('/dmm thorennpc remove', 1500)).join(' / '));
  await R(`lp user ${N} parent remove quanly`); await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
