const h = require('./h');
const pp = (id, lv) => `minecraft:stick[custom_data={PublicBukkitValues:{"duckymomocore:phuphep_${id}":${lv}}}]`;
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const go = async (t, x, z) => { for (let i = 0; i < 4 && (Math.abs(t.bot.entity.position.x - x) > 3 || Math.abs(t.bot.entity.position.z - z) > 3); i++) { t = await h.moveTo(t, x, z); await t.sleep(2500); } return t; };
(async () => {
  let B = await go(await h.start('ThuB'), 12320, -2604); let A = await go(await h.start('ThuA'), 12320, -2610); const R = c => A.rc.cmd(c);
  const ab = []; for (const ev of ['action_bar', 'set_action_bar_text', 'system_chat']) A.bot._client.on(ev, p => { const v = txt(p.text ?? p.content ?? p).trim(); if (v && !/^❤ \[/.test(v)) ab.push(v); });
  for (let i = 0; i < 8 && !(A.bot.players.ThuB && A.bot.players.ThuB.entity); i++) await A.sleep(1000);
  A.log('ThuA thấy ThuB:', !!(A.bot.players.ThuB && A.bot.players.ThuB.entity), '| ThuB ở', (await R('data get entity ThuB Pos')).replace(/.*data: /, ''));
  await R('minecraft:clear ThuA'); await R(`minecraft:give ThuA ${pp('ho_gia', 2)}`); await A.sleep(900); await A.bot.equip(A.bot.inventory.items()[0], 'hand'); await A.sleep(400);
  const e = A.bot.players.ThuB.entity; await A.bot.lookAt(e.position.offset(0, 1.4, 0), true); await A.sleep(400);
  A.bot.activateItem(); await A.sleep(250); A.bot.deactivateItem(); await A.sleep(2500);
  A.log('báo:', ab.filter(x => !x.startsWith('▏') && x !== 'Bang Hội Chiến').join(' | '), '| tim vàng', (await R('data get entity ThuA AbsorptionAmount')).replace(/.*data: /, ''), (await R('data get entity ThuB AbsorptionAmount')).replace(/.*data: /, ''), '| ThuA ở', (await R('data get entity ThuA Pos')).replace(/.*data: /, ''));
  await R('minecraft:clear ThuA'); await B.end(); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
