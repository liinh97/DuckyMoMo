// Đường tân thủ (29/10): bot mới tinh vào -> tự bắt đầu, thanh boss bar; bước 1 Già Làng (giả hội thoại bằng rcon dmm viecnpc),
// bước 2 /diemdanh sớm -> chờ -> tự điểm danh, 3 /rtp <khu>, 4 bỏ qua, 5 chế bàn chế tạo, 6 bỏ qua, 7 /sethome, 8 hạ 3 quái.
const h = require('./h');
const N = process.env.BOTN || 'TanThuX';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  let bar = ''; t.bot._client.on('boss_bar', p => { if (p.title) bar = txt(typeof p.title === 'string' ? (() => { try { return JSON.parse(p.title); } catch (e) { return p.title; } })() : p.title); });
  const show = async (lbl, ms = 1500) => { const n = msgs.length; await t.sleep(ms); t.log('##', lbl, '| thanh:', bar, '| chat:', msgs.slice(n).filter(m => /Tân thủ|Xong|Bước|✔|⏳|tân thủ|Điểm danh|★/.test(m)).join(' / ').slice(0, 400)); };
  await t.sleep(4000);
  t.log('## lúc vào | thanh:', bar, '| chat:', msgs.filter(m => /tân thủ|Bước/.test(m)).join(' / '));
  await R(`dmm viecnpc ${N} gialang`); await show('1 Già Làng', 2500);
  t.bot.chat('/diemdanh'); await show('2 gõ /diemdanh sớm');
  t.log('## chờ đủ 5 phút…'); for (let i = 0; i < 30; i++) { t.bot.look(i % 2 ? 0 : 1.5, 0); await t.sleep(10000); }
  await show('2 sau 5 phút', 2000);
  t.bot.chat('/rtp ' + (process.env.KHU || 'taybac')); await show('3 rtp', 8000);
  t.bot.chat('/tanthu boqua'); await show('4 bỏ qua');
  await R(`minecraft:give ${N} minecraft:oak_planks 4`); await t.sleep(1000);
  const reg = require('minecraft-data')(t.bot.version); const rec = t.bot.recipesFor(reg.itemsByName.crafting_table.id, null, 1, null)[0];
  if (rec) { await t.bot.craft(rec, 1, null); } else t.log('## không thấy công thức');
  await show('5 chế bàn', 2000);
  t.bot.chat('/tanthu boqua'); await show('6 bỏ qua');
  t.bot.chat('/sethome nha'); await show('7 sethome', 2500);
  for (let i = 0; i < 3; i++) {
    await R(`execute at ${N} run summon minecraft:zombie ^ ^ ^2 {Health:1f,NoAI:1b,Tags:["tt"]}`); await t.sleep(800);
    const z = t.bot.nearestEntity(e => e.name === 'zombie'); if (z) { await t.bot.lookAt(z.position.offset(0, 1.6, 0)); t.bot.attack(z); } await t.sleep(1200);
  }
  await show('8 hạ quái', 3000);
  t.bot.chat('/tanthu'); await show('/tanthu', 1500);
  await R(`kill @e[tag=tt]`); await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
