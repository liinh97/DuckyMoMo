// q5 chuỗi chìa (the_cave_sanctum cần chìa của the_climb_dungeon; key-tradeable false) + q6 vé (knights_castle_lair 4.500/vé, tối đa 5,
// không vào kịp thì hoàn vé) + góp quỹ (fireworks_lair). Wrapper (dung.sh) tạm: knights_castle_lair days thêm t5, window-seconds 15,
// fireworks_lair goal 2000; xong trả lại. Bot ở spawn (kinh đô).
const { start } = require('./h');
(async () => {
  const a = await start('ThuQ5', '10.9.2.1'); const b = await start('ThuQ5b', '10.9.2.2');
  const R = c => a.rc.cmd(c);
  const bal = async n => +((await R(`bal ${n}`)).replace(/.*:\s*/, '').replace(/[^\d.]/g, '') || 0);
  await a.sleep(4000);
  for (const n of ['ThuQ5', 'ThuQ5b']) { await R(`eco set ${n} 50000`); await R(`minecraft:clear ${n}`); }
  const say = async (t, c, w = 1800) => (await t.say(c, w)).join(' | ').replace(/\s+/g, ' ').slice(0, 260);
  const closeWin = t => { if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); };
  // ---- q5
  a.log('q5 không có chìa:', await say(a, '/dungeon vao the_cave_sanctum'));
  await R('dungeon cho the_cave_sanctum ThuQ5'); await a.sleep(800);
  await R('minecraft:item replace entity ThuQ5b weapon.mainhand from entity ThuQ5 weapon.mainhand'); await a.sleep(800);
  b.log('q5 ThuQ5b cầm chìa ghi tên ThuQ5:', b.bot.heldItem ? b.itemText(b.bot.heldItem).name + ' / ' + b.itemText(b.bot.heldItem).lore.join(' / ').slice(0, 120) : 'trống');
  b.log('q5 ThuQ5b dùng chìa của người khác:', await say(b, '/dungeon vao the_cave_sanctum')); closeWin(b);
  a.log('q5 ThuQ5 dùng chìa của mình:', await say(a, '/dungeon vao the_cave_sanctum', 2500), '| menu mở:', !!a.bot.currentWindow); closeWin(a);
  a.log('  chìa còn trong túi ThuQ5:', (await R('minecraft:clear ThuQ5 minecraft:trial_key 0')).trim());
  a.log('q5 vào lại lần 2 (không cần chìa nữa):', await say(a, '/dungeon vao the_cave_sanctum', 2500), '| menu mở:', !!a.bot.currentWindow); closeWin(a);
  const ds = (await say(a, '/dungeon', 2000)); a.log('  /dungeon:', ds.match(/Hang Thánh Địa[^|]*/) ? ds.match(/Hang Thánh Địa[^|]*/)[0] : ds.slice(0, 200));
  await R('minecraft:clear ThuQ5b');
  // ---- q6 vé
  let m0 = await bal('ThuQ5');
  a.log('q6 mua 6 vé:', await say(a, '/dungeon mua knights_castle_lair 6'), `| tiền ${m0} -> ${await bal('ThuQ5')}`);
  a.log('q6 mua thêm 1:', await say(a, '/dungeon mua knights_castle_lair'));
  a.log('q6 mở cổng (vé):', await say(a, '/dungeon vao knights_castle_lair', 2500), '| menu mở:', !!a.bot.currentWindow); closeWin(a);
  const vé = async () => ((await say(a, '/dungeon', 2000)).match(/Lâu Đài Hiệp Sĩ[^|]*/) || ['?'])[0];
  a.log('  ngay sau khi mở cổng:', await vé());
  const n0 = a.chat.length; await a.sleep(20000);
  a.log('q6 sau 20 giây không vào:', a.chat.slice(n0).filter(l => /Cổng|vé|Vé/.test(l)).join(' | ').slice(0, 200), '|', await vé());
  // ---- q6 góp quỹ
  m0 = await bal('ThuQ5');
  a.log('q6 góp 1500:', await say(a, '/dungeon gop fireworks_lair 1500'));
  b.log('q6 ThuQ5b góp 1000 (chỉ còn thiếu 500):', await say(b, '/dungeon gop fireworks_lair 1000', 2500));
  b.log('  ThuQ5b vào Lễ Hội Pháo Hoa:', await say(b, '/dungeon vao fireworks_lair', 2500), '| menu mở:', !!b.bot.currentWindow); closeWin(b);
  b.log('  /dungeon:', ((await say(b, '/dungeon', 2000)).match(/Lễ Hội Pháo Hoa[^|]*/) || ['?'])[0]);
  await a.end(); await b.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
