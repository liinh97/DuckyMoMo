// Mảnh ấn rơi thật (23/10): bot vào Đỉnh Vách Đá, kéo quái EliteMobs (kể cả trùm 『』, phó trùm 〖〗) tới cạnh, tự chém bằng kiếm Sắc bén 255.
const h = require('./h');
const N = process.env.BOTN || 'ThuAn';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await R(`minecraft:clear ${N}`); await R(`effect give ${N} minecraft:resistance 900 4 true`); await R(`effect give ${N} minecraft:strength 900 9 true`);
  const dim = async () => (await R(`data get entity ${N} Dimension`)).replace(/.*data: /, '').replace(/"/g, '');
  await R(`execute in minecraft:overworld run minecraft:tp ${N} 12699 80 -2523`); await t.sleep(3000);
  const w = await t.openMenu('/dungeon vao the_climb_dungeon'); if (w) { await t.click(2); await t.sleep(800); if (t.bot.currentWindow) await t.click(2); }
  for (let i = 0; i < 25 && !/em_/.test(await dim()); i++) await t.sleep(1000);
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  const D = await dim(); t.log('## vào', D); if (!/em_/.test(D)) { t.log('không vào được', msgs.slice(-3)); await t.end(); process.exit(0); }
  await R(`minecraft:give ${N} minecraft:netherite_sword[minecraft:enchantments={"minecraft:sharpness":255}] 1`); await t.sleep(15000);   // chờ tự /em start
  for (const [x1, z1] of [[160, 0], [400, 0], [160, 240], [400, 240]]) await R(`execute in ${D} run forceload add ${x1} ${z1} ${x1 + 239} ${z1 + 239}`);
  await t.sleep(6000);
  const sw = t.bot.inventory.items().find(i => i.name === 'netherite_sword'); if (sw) await t.bot.equip(sw, 'hand');
  let kills = 0;
  for (let round = 0; round < 8; round++) {
    for (const ty of ['skeleton', 'wither_skeleton', 'stray', 'zombie', 'husk']) await R(`execute in ${D} as @e[type=minecraft:${ty},limit=6] at ${N} run minecraft:tp @s ^ ^ ^1.5`);
    await t.sleep(1500);
    for (let i = 0; i < 40; i++) {
      const e = Object.values(t.bot.entities).filter(x => x.type === 'hostile' || ['skeleton', 'wither_skeleton', 'stray', 'zombie', 'husk'].includes(x.name)).filter(x => x.position.distanceTo(t.bot.entity.position) < 5)[0];
      if (!e) break;
      await t.bot.lookAt(e.position.offset(0, 1, 0)); t.bot.attack(e); kills++; await t.sleep(650);
    }
    t.log(`vòng ${round}: đã chém ${kills} đòn | rơi:`, msgs.filter(m => /Nhặt được/.test(m)).length);
  }
  t.log('## tin rơi:', msgs.filter(m => /Nhặt được|Chìa khoá|Lần đầu/.test(m)).slice(0, 8).join(' / '));
  t.log('## túi:', t.bot.inventory.items().map(i => `${t.itemText(i).name}×${i.count}`).filter(x => /Ấn|Lõi|Xu Quái|Chìa/.test(x)).join(', '));
  await R(`execute in ${D} run forceload remove all`); await R(`execute in minecraft:overworld run minecraft:tp ${N} 12699 80 -2523`); await t.sleep(2000);
  await R(`effect clear ${N}`); await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
