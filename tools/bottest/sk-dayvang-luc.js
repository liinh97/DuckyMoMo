// Đẩy văng I–III sau khi giảm lực (22/10: 1,4 / 1,6 / 1,8), có và không có Rơi chậm, sàn kính tạm y 150 dài 44 khối, xong xoá sàn.
const h = require('./h');
const pp = (id, lv) => `minecraft:stick[custom_data={PublicBukkitValues:{"duckymomocore:phuphep_${id}":${lv}}}]`;
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const X = 13200, Y = 150, Z = -2900;
(async () => {
  let A = await h.start('ThuA'); const R = c => A.rc.cmd(c);
  await R(`forceload add ${X - 8} ${Z - 42} ${X + 8} ${Z + 4}`); await A.sleep(1500);
  A.log('dựng sàn:', await R(`fill ${X - 3} ${Y} ${Z - 40} ${X + 3} ${Y} ${Z + 3} minecraft:glass`));
  const place = async (t, n, x, z, face) => { for (let i = 0; i < 4; i++) { await t.rc.cmd(`minecraft:tp ${n} ${x} ${Y + 1} ${z} ${face} 0`); await t.end(); await A.sleep(1500); t = await h.start(n); await t.sleep(1500); if (Math.abs(t.bot.entity.position.x - x) < 3 && Math.abs(t.bot.entity.position.z - z) < 3) break; } return t; };
  A = await place(A, 'ThuA', X + 0.5, Z + 0.5, 180); const R2 = c => A.rc.cmd(c);
  let B = await h.start('ThuX1'); B = await place(B, 'ThuX1', X + 0.5, Z - 5.5, 0);
  const ab = []; for (const ev of ['action_bar', 'set_action_bar_text', 'system_chat']) A.bot._client.on(ev, p => { const v = txt(p.text ?? p.content ?? p).trim(); if (v && !/^❤ \[|GIAO TRANH|^▏|^\[[+-]\]/.test(v)) ab.push(v); });
  const P = async n => (await R2(`data get entity ${n} Pos`)).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  const d = (a, b) => Math.hypot(a[0] - b[0], a[2] - b[2]).toFixed(1);
  A.log('ThuA', (await P('ThuA')).map(v => v.toFixed(1)), 'ThuX1', (await P('ThuX1')).map(v => v.toFixed(1)));
  await R2('effect clear ThuX1'); await R2('effect give ThuX1 minecraft:resistance 120 4 true'); await A.sleep(10000);
  for (const [id, lv, sf] of [['day_vang', 1, 0], ['day_vang', 2, 0], ['day_vang', 3, 0], ['day_vang', 3, 1]]) {
    for (let i = 0; i < 8 && !(A.bot.players.ThuX1 && A.bot.players.ThuX1.entity); i++) await A.sleep(1000);
    await R2('minecraft:clear ThuA'); await R2(`minecraft:give ThuA ${pp(id, lv)}`); await A.sleep(900); await A.bot.equip(A.bot.inventory.items()[0], 'hand'); await A.sleep(300);
    if (sf) { await R2('effect give ThuX1 minecraft:slow_falling 120 0 true'); await A.sleep(500); }
    const e = A.bot.players.ThuX1 && A.bot.players.ThuX1.entity; if (e) await A.bot.lookAt(e.position.offset(0, 1.4, 0), true); await A.sleep(400);
    const a = await P('ThuA'), b = await P('ThuX1'); const n0 = ab.length; A.bot.activateItem(); await A.sleep(250); A.bot.deactivateItem(); await A.sleep(2200);
    A.log(id, lv, sf ? 'rơi chậm' : 'thường', ': ThuX1 cách ThuA', d(a, b), '->', d(a, await P('ThuX1')), '| báo:', ab.slice(n0).join(' / '));
    B = await place(B, 'ThuX1', X + 0.5, Z - 5.5, 0); await R2('effect clear ThuX1'); await R2('effect give ThuX1 minecraft:resistance 120 4 true'); await A.sleep(8000);
  }
  await R2('minecraft:clear ThuA'); await R2('effect clear ThuX1');
  await R2(`minecraft:tp ThuA ${X} 70 ${Z}`); await R2(`minecraft:tp ThuX1 ${X} 70 ${Z}`);
  A.log('xoá sàn:', await R2(`fill ${X - 3} ${Y} ${Z - 40} ${X + 3} ${Y} ${Z + 3} minecraft:air replace minecraft:glass`)); await R2(`forceload remove ${X - 8} ${Z - 42} ${X + 8} ${Z + 4}`);
  await B.end(); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
