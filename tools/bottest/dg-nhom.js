// Dungeon theo nhóm (23/10). (1) không nhóm: A và B tự vào Đỉnh Vách Đá -> 2 instance riêng?
// (2) nhóm A+B (/em party create|invite|accept): A /dungeon vao -> plugin tự sẵn sàng hộ, cả 2 vào chung, tự bắt đầu trận, hạ 3 mục tiêu -> MỖI người nhận chìa + quà.
// (3) nhóm A+B+C, A /dungeon vao Hang Thánh Địa: C chưa có chìa -> báo rõ "ThuNhomC: cần Chìa khoá…", không ai vào.
const h = require('./h');
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const NAMES = ['ThuNhomA', 'ThuNhomB', 'ThuNhomC'];
(async () => {
  const B = {};
  for (const n of NAMES) { const t = await h.start(n); t.msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v && !/^HP |No class/.test(v)) t.msgs.push(v); }); B[n] = t; await t.sleep(1500); }
  const A = B.ThuNhomA, Bb = B.ThuNhomB, C = B.ThuNhomC; const R = c => A.rc.cmd(c);
  const dim = async n => (await R(`data get entity ${n} Dimension`)).replace(/.*data: /, '').replace(/"/g, '');
  const home = async () => { for (const n of NAMES) await R(`execute in minecraft:overworld run minecraft:tp ${n} 12699 80 -2523`); await A.sleep(4000); };
  const since = (t, k) => t.msgs.slice(k).join(' / ').slice(0, 400);
  for (const n of NAMES) { await R(`minecraft:clear ${n}`); await R(`effect give ${n} minecraft:resistance 1200 4 true`); }
  await home();
  const vao = async (t, id) => { const w = await t.openMenu('/dungeon vao ' + id); if (w) { await t.click(2); await t.sleep(800); if (t.bot.currentWindow) await t.click(2); } await t.sleep(1500); if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); };
  // (1) không nhóm
  await vao(A, 'the_climb_dungeon'); await vao(Bb, 'the_climb_dungeon');
  for (let i = 0; i < 20 && !(/em_/.test(await dim('ThuNhomA')) && /em_/.test(await dim('ThuNhomB'))); i++) await A.sleep(1000);
  A.log('## (1) không nhóm: A ở', await dim('ThuNhomA'), '| B ở', await dim('ThuNhomB'));
  for (const n of ['ThuNhomA', 'ThuNhomB']) B[n].bot.chat('/em quit'); await A.sleep(3000); await home();
  // (2) nhóm A+B
  let ka = A.msgs.length, kb = Bb.msgs.length;
  A.bot.chat('/em party create'); await A.sleep(1500); A.bot.chat('/em party invite ThuNhomB'); await A.sleep(1500); Bb.bot.chat('/em party accept'); await A.sleep(2000);
  A.log('## (2) lập nhóm | A:', since(A, ka), '| B:', since(Bb, kb));
  ka = A.msgs.length; kb = Bb.msgs.length;
  await vao(A, 'the_climb_dungeon'); await A.sleep(1500);
  A.log('   sau /dungeon vao | A:', since(A, ka), '| B:', since(Bb, kb));
  for (let i = 0; i < 15 && !(/em_/.test(await dim('ThuNhomA')) && /em_/.test(await dim('ThuNhomB'))); i++) await A.sleep(1000);
  const D = await dim('ThuNhomA');
  A.log('   vào: A ở', D, '| B ở', await dim('ThuNhomB'), '| B thấy:', since(Bb, kb));
  if (/em_/.test(D)) {
    ka = A.msgs.length; kb = Bb.msgs.length;
    await A.sleep(15000);   // KHÔNG gõ /em start: plugin tự bắt đầu (auto-start)
    for (const [x1, z1] of [[160, 0], [400, 0], [160, 240], [400, 240]]) await R(`execute in ${D} run forceload add ${x1} ${z1} ${x1 + 239} ${z1 + 239}`);
    await A.sleep(8000);
    for (let r = 0; r < 12; r++) {
      for (const ty of ['skeleton', 'wither_skeleton', 'stray', 'zombie', 'husk']) await R(`execute in ${D} run minecraft:kill @e[type=minecraft:${ty}]`);
      await A.sleep(5000);
      if (/Chìa khoá/.test(since(A, ka)) && /Chìa khoá/.test(since(Bb, kb))) break;
    }
    const pick = (t, k) => t.msgs.slice(k).filter(m => /Chìa khoá|Lần đầu|Quà|ĐẦU TIÊN|KỶ LỤC/.test(m)).join(' / ');
    A.log('   vượt xong | A nhận:', pick(A, ka)); A.log('              | B nhận:', pick(Bb, kb));
    for (const n of ['ThuNhomA', 'ThuNhomB']) A.log('   túi', n, B[n].bot.inventory.items().map(i => B[n].itemText(i).name).join(', '));
    await R(`execute in ${D} run forceload remove all`);
  }
  for (const n of ['ThuNhomA', 'ThuNhomB']) B[n].bot.chat('/em quit'); await A.sleep(3000); await home();
  // (3) nhóm A+B+C vào Hang Thánh Địa (C chưa có chìa)
  ka = A.msgs.length; let kc = C.msgs.length;
  A.bot.chat('/em party invite ThuNhomC'); await A.sleep(1500); C.bot.chat('/em party accept'); await A.sleep(2000);
  A.log('## (3) mời C | C:', since(C, kc));
  ka = A.msgs.length;
  A.bot.chat('/dungeon vao the_cave_sanctum'); await A.sleep(3000); if (A.bot.currentWindow) A.bot.closeWindow(A.bot.currentWindow);
  A.log('   A /dungeon vao the_cave_sanctum ->', since(A, ka), '| A ở', await dim('ThuNhomA'), '| túi A còn chìa:', A.bot.inventory.items().some(i => /Chìa khoá/.test(A.itemText(i).name)));
  A.bot.chat('/em party disband'); await A.sleep(1000);
  for (const n of NAMES) await R(`effect clear ${n}`);
  for (const n of NAMES) await B[n].end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
