// Mảnh ấn rơi trong dungeon (23/10): bot vào Đỉnh Vách Đá (tầng 1 -> Chiến Ấn · Khôi Giáp). Thả 3 "trùm" (tên 『5』), 4 "phó trùm" (〖5〗),
// 50 quái thường cạnh bot, bot hạ bằng /damage ... by bot. Rồi rèn Mũ Chiến Binh +0 từ Mũ Tân Thủ +9 + mảnh vừa rơi.
const h = require('./h');
const N = process.env.BOTN || 'ThuAn';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await R(`minecraft:clear ${N}`); await R(`effect give ${N} minecraft:resistance 600 4 true`);
  const dim = async () => (await R(`data get entity ${N} Dimension`)).replace(/.*data: /, '').replace(/"/g, '');
  await R(`execute in minecraft:overworld run minecraft:tp ${N} 12699 80 -2523`); await t.sleep(3000);
  const w = await t.openMenu('/dungeon vao the_climb_dungeon'); if (w) { await t.click(2); await t.sleep(800); if (t.bot.currentWindow) await t.click(2); }
  for (let i = 0; i < 25 && !/em_/.test(await dim()); i++) await t.sleep(1000);
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  const D = await dim(); t.log('## vào', D); if (!/em_/.test(D)) { t.log('không vào được', msgs.slice(-3)); await t.end(); process.exit(0); }
  await t.sleep(3000);
  // instance EliteMobs xoá quái lạ -> thả ở thế giới thường (spawn đã forceload); bot vẫn ở trong dungeon nên plugin tính theo dungeon của bot
  await R('forceload add 12690 -2530 12700 -2520');
  const spawn = async (name, n) => { for (let i = 0; i < n; i++) await R(`execute in minecraft:overworld run summon minecraft:zombie 12695 70 -2525 {NoAI:1b,Silent:1b,Tags:["thuan"],CustomName:'"${name}"'}`); };
  const kill = async () => R(`execute in minecraft:overworld as @e[tag=thuan] run damage @s 1000 minecraft:player_attack by ${N}`);
  let k = msgs.length; await spawn('『5』 Trùm thử', 3); await kill(); await t.sleep(1500);
  t.log('## 3 trùm ->', msgs.slice(k).filter(m => /Nhặt được/.test(m)).length, 'lần rơi |', msgs.slice(k).filter(m => /Nhặt/.test(m)).slice(0, 2).join(' / '));
  k = msgs.length; await spawn('〖5〗 Phó trùm thử', 4); await kill(); await t.sleep(1500);
  t.log('## 4 phó trùm (25%) ->', msgs.slice(k).filter(m => /Nhặt được/.test(m)).length, 'lần rơi');
  k = msgs.length; await spawn('Quái thử', 50); await kill(); await t.sleep(2000);
  t.log('## 50 quái thường (2%) ->', msgs.slice(k).filter(m => /Nhặt được/.test(m)).length, 'lần rơi');
  await R('forceload remove 12690 -2530 12700 -2520');
  const an = t.bot.inventory.items().filter(i => /Chiến Ấn/.test(t.itemText(i).name)); t.log('## túi:', an.map(i => `${t.itemText(i).name}×${i.count}`).join(', '));
  await R(`execute in minecraft:overworld run minecraft:tp ${N} 12699 80 -2523`); await t.sleep(3000);
  // rèn Mũ Chiến Binh +0
  await R(`eco set ${N} 10000`);
  for (const [id, n] of [['tt_mu_9', 1], ['hop_kim', 1], ['huyet_tinh', 1], ['cb_an_khoi', 2]]) await R(`dmm laydo ${id} ${n} ${N}`);
  await t.sleep(1500);
  let m = await t.openMenu('/thoren'); const sl = (w, re) => w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
  t.log('## menu chính:', m.slots.slice(0, m.inventoryStart).filter(it => it && !/pane|barrier|anvil/.test(it.name)).map(it => t.itemText(it).name).join(' | '));
  await t.click(sl(m, /^Chiến Binh/)); await t.sleep(1200); m = t.bot.currentWindow;
  const mu = m.slots[sl(m, /^Mũ Chiến Binh/)]; t.log('## Mũ Chiến Binh +0:', t.itemText(mu).lore.join(' ¦ '));
  k = msgs.length; await t.click(sl(m, /^Mũ Chiến Binh/)); await t.sleep(1500);
  t.log('   rèn ->', msgs.slice(k).join(' / '), '| túi:', t.bot.inventory.items().map(i => t.itemText(i).name).filter(n => /Chiến Binh|tân thủ/i.test(n)).join(', '));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await R(`effect clear ${N}`); await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
