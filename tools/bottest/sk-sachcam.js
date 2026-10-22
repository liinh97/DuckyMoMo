// Sách cấm: chạy theo bước (đặt luật bằng panel trước mỗi bước). node sk-sachcam.js a|b|c
//  a: luật Đấu trường kiem_rong = vo-hieu  -> dòng ⛔, nhóm hồi chiêu, vào sảnh đấu trường: không cầm được, đang cầm thì bị cất, /sachcam
//  b: luật = chan-vao                      -> /dautruong vao 1v1 bị chặn
//  c: luật đã trả về                       -> dòng ⛔ biến mất
const h = require('./h');
const X = 12320, Z = -2610, step = process.argv[2];
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const tap = t => { const ab = []; const push = p => { const v = txt(p.text ?? p.content ?? p).trim(); if (v && !/^❤ \[/.test(v)) ab.push(v); }; for (const ev of ['action_bar', 'set_action_bar_text', 'system_chat']) t.bot._client.on(ev, push); return ab; };
const go = async (t, x, z) => { for (let i = 0; i < 4 && (Math.abs(t.bot.entity.position.x - x) > 3 || Math.abs(t.bot.entity.position.z - z) > 3); i++) { t = await h.moveTo(t, x, z); await t.sleep(2500); } return t; };
(async () => {
  let A = await go(await h.start('ThuA'), X, Z); const R = c => A.rc.cmd(c);
  const sel = async () => (await R('data get entity ThuA SelectedItem')).replace(/.*data: /, '');
  const swordSlot = () => A.bot.inventory.items().find(i => i.name === 'diamond_sword' || i.name === 'netherite_sword');
  if (step === 'a' || step === 'c') {
    await R('minecraft:clear ThuA'); await R('minecraft:give ThuA minecraft:stick'); await R('dmm laydo kiem_rong ThuA 1'); await A.sleep(1200);
    const sw = A.bot.inventory.items().find(i => i.name !== 'stick'); A.log('món thử:', sw && sw.name, 'ô', sw && sw.slot);
    if (sw) await A.bot.equip(sw, 'hand'); await A.sleep(800);
    const lore = (await R('data get entity ThuA SelectedItem.components."minecraft:lore"')).replace(/.*data: /, '');
    const cd = (await R('data get entity ThuA SelectedItem.components."minecraft:use_cooldown"')).replace(/.*data: /, '');
    A.log(`[${step}] cầm trên tay: dòng ⛔:`, (lore.match(/⛔[^"'}]*/g) || ['(không có)']).join(' / '), '| hồi chiêu:', cd.slice(0, 120));
  }
  if (step === 'a') {
    const st = A.bot.inventory.items().find(i => i.name === 'stick'); await A.bot.equip(st, 'hand'); await A.sleep(500);
    A.log('tp:', await R('execute in minecraft:dautruong run minecraft:tp ThuA 0.5 65 -130.5')); const ab = tap(A); await A.sleep(2500);   // không vào lại: Đấu trường đưa người lạc về kinh đô lúc đăng nhập
    A.log('đang ở:', (await R('data get entity ThuA Dimension')).replace(/.*data: /, ''), (await R('data get entity ThuA Pos')).replace(/.*data: /, ''));
    const sw = A.bot.inventory.items().find(i => i.name !== 'stick');
    if (sw) { await A.bot.equip(sw, 'hand').catch(e => A.log('equip lỗi', e.message)); } await A.sleep(1200);
    A.log('thử cầm kiếm lên tay -> tay đang cầm:', ((await sel()).match(/id: "[^"]+"/) || ['(trống)'])[0], '| báo:', ab.slice(-2).join(' / '));
    // ép kiếm vào tay: túi trống, ô đang cầm trống, phát kiếm (vào thẳng ô đang cầm) rồi chờ BanBook cất vào túi trong
    await R('minecraft:clear ThuA'); await A.sleep(300); await R('dmm laydo kiem_rong ThuA 1'); await A.sleep(300);
    A.log('vừa phát, tay cầm:', ((await sel()).match(/id: "[^"]+"/) || ['(trống)'])[0]); await A.sleep(1800);
    A.log('ép kiếm vào tay, 1,8 giây sau tay cầm:', ((await sel()).match(/id: "[^"]+"/) || ['(trống)'])[0], '| báo:', ab.slice(-2).join(' / '));
    const n = ab.length; A.bot.chat('/sachcam'); await A.sleep(1500); A.log('/sachcam:\n  ' + ab.slice(n).join('\n  '));
    await R('minecraft:clear ThuA'); A = await go(A, X, Z);
  }
  if (step === 'b') {
    await R('minecraft:clear ThuA'); await R('dmm laydo kiem_rong ThuA 1'); await A.sleep(800); const ab = tap(A);
    const n = ab.length; A.bot.chat('/dautruong vao 1v1'); await A.sleep(1500); A.log('[b] /dautruong vao 1v1 khi mang kiem_rong:', ab.slice(n).join(' / '));
    A.bot.chat('/dautruong roi'); await A.sleep(800);
    await R('minecraft:clear ThuA'); const m = ab.length; A.bot.chat('/dautruong vao 1v1'); await A.sleep(1500); A.log('[b] khi KHÔNG mang:', ab.slice(m).join(' / ')); A.bot.chat('/dautruong roi'); await A.sleep(800);
  }
  await R('minecraft:clear ThuA'); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
