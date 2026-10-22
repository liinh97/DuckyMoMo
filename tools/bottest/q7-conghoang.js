// q7 Cổng Hoang + q9 Lữ khách. Wrapper (scratchpad q7.sh) tạo cổng bằng /conghoang tao, tạm đặt BetonQuest default_io: simple (chọn bằng số
// trong chat; bot 1.21.4 qua Via không hiện bảng dialog), rồi trả lại. Args: gx gy gz (ô giữa cổng) nx ny nz (lữ khách của nơi cổng dẫn tới).
// ThuQ7: nói chuyện lữ khách trước (nghe chuyện), Shift ở ô giữa (tìm ra + vào dungeon), về kể (nhận thưởng), nói lần nữa (đã xong).
// ThuQ7b (IP khác): Shift ngay sau -> bị chặn vì thời gian ưu tiên; thử phá khối cổng -> không phá được.
const { start } = require('./h');
const [gx, gy, gz, nx, ny, nz] = process.argv.slice(2, 8).map(Number);
const A = 'ThuQ7', B = 'ThuQ7b', IPA = '10.9.0.71', IPB = '10.9.0.72';
const tpTo = async (t, x, y, z, ip) => { const N = t.bot.username; await t.rc.cmd(`minecraft:tp ${N} ${x} ${y} ${z}`); await t.end(); await t.sleep(2500); const t2 = await start(N, ip); await t2.sleep(1500); return t2; };
(async () => {
  let a = await start(A, IPA);
  const R = c => a.rc.cmd(c);
  const bal = async n => +((await R(`bal ${n}`)).replace(/[^\d,]/g, '').replace(',', '.') || 0);
  for (const n of [A]) { await R(`gamemode survival ${n}`); await R(`effect give ${n} minecraft:resistance 600 4 true`); }
  await a.sleep(4000);
  // ---- q9 nói chuyện lữ khách (trước khi qua cổng)
  const talk = async (label, picks) => {
    a = await tpTo(a, nx + 2, ny, nz, IPA);
    const npc = a.bot.nearestEntity(e => e.type === 'player' && e.username !== A);
    if (!npc) return a.log(`q9 ${label}: không thấy NPC gần (${nx},${ny},${nz})`);
    await a.bot.lookAt(npc.position.offset(0, 1.6, 0), true);
    const n0 = a.chat.length, b0 = await bal(A);
    await a.use(npc); await a.sleep(2000);
    for (const k of picks) { a.bot.chat(String(k)); await a.sleep(1800); }
    a.log(`q9 ${label} (NPC ${npc.username}): ${a.chat.slice(n0).join(' | ').replace(/\s+/g, ' ').slice(0, 700)} | tiền ${b0} -> ${await bal(A)}`);
  };
  await talk('lần 1, chưa qua cổng (chọn 1 = "Có chuyện gì vậy?" rồi 1 = hỏi đường)', [1, 1]);
  a.log('  tag sau lần 1:', (await R(`bq tag ${A} list`)).replace(/\s+/g, ' ').slice(0, 300));
  // ---- q7 tìm cổng: đứng ô giữa, Shift
  a = await tpTo(a, gx + 0.5, gy + 1, gz + 0.5, IPA);
  a.log('q7 dưới chân:', (a.bot.blockAt(a.bot.entity.position.offset(0, -1, 0)) || {}).name, '| tin gần cổng:', a.chat.slice(-3).join(' | ').slice(0, 200));
  let n0 = a.chat.length;
  a.bot.setControlState('sneak', true); await a.sleep(3000); a.bot.setControlState('sneak', false);
  a.log('q7 A Shift ở ô giữa:', a.chat.slice(n0).join(' | ').slice(0, 400));
  a.log('  A đang ở:', (await R(`data get entity ${A} Dimension`)).replace(/.*data: /, ''), (await R(`data get entity ${A} Pos`)).replace(/.*data: /, ''));
  // ---- B tới cổng ngay sau: thời gian ưu tiên
  let b = await start(B, IPB); await b.sleep(4000); await R(`gamemode survival ${B}`); await R(`effect give ${B} minecraft:resistance 600 4 true`);
  await R(`lp user ${B} permission set grim.exempt true`);
  b = await tpTo(b, gx + 0.5, gy + 1, gz + 0.5, IPB);
  n0 = b.chat.length;
  b.bot.setControlState('sneak', true); await b.sleep(3000); b.bot.setControlState('sneak', false);
  b.log('q7 B Shift (người khác, trong thời gian ưu tiên):', b.chat.slice(n0).join(' | ').slice(0, 300));
  // ---- B thử phá khối giữa cổng
  const blk = b.bot.blockAt(b.bot.entity.position.offset(0, -1, 0));
  if (blk) {
    b.bot._client.write('block_dig', { status: 0, location: blk.position, face: 1, sequence: 5 }); await b.sleep(200);
    b.bot._client.write('block_dig', { status: 2, location: blk.position, face: 1, sequence: 6 }); await b.sleep(1500);
    const still = await R(`execute if block ${blk.position.x} ${blk.position.y} ${blk.position.z} minecraft:${blk.name}`);
    b.log(`q7 B phá khối cổng ${blk.name} tại ${blk.position}: còn nguyên? ${/passed/.test(still)} | tin: ${b.chat.slice(-2).join(' | ').slice(0, 150)}`);
  }
  await R(`lp user ${B} permission unset grim.exempt`);
  await b.end();
  // ---- A ra khỏi dungeon rồi về kể cho lữ khách
  a.log('  tag sau khi qua cổng:', (await R(`bq tag ${A} list`)).replace(/\s+/g, ' ').slice(0, 300));
  await R(`execute in minecraft:overworld run minecraft:tp ${A} ${nx + 2} ${ny} ${nz}`); await a.sleep(1500);
  await talk('lần 2, đã qua cổng (chọn 1 = kể)', [1]);
  await talk('lần 3', []);
  await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
