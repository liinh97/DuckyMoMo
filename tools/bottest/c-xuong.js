// c4 Rìu Bán (đánh vào rương: rương mình bán được, rương người khác bị chặn) + Bắt Thú (bò -> trứng; sói có chủ, zombie: không),
// c7 Xu Quái rơi (~5% quái thường), c8 món Xưởng cần thêm Tinh Luyện (wrapper c-xuong.sh tạm đặt khoan_dao cần 1 Tinh Luyện Sắt).
// ThuX1 tạm làm dân KinhDo (đất kinh đô ngoài vùng spawn WorldGuard, x 12950), ThuX2 người ngoài. Xong: kick khỏi KinhDo.
const { start, moveTo } = require('./h');
const A = 'ThuX1', B = 'ThuX2';
(async () => {
  let a = await start(A);
  const R = c => a.rc.cmd(c);
  const bal = async n => +((await R(`bal ${n}`)).replace(/[^\d.]/g, '') || 0);
  const PX = 12950, PZ = -2520;
  for (const n of [A, B]) { await R(`gamemode survival ${n}`); await R(`minecraft:clear ${n}`); await R(`eco set ${n} 100000`); await R(`effect give ${n} minecraft:resistance 1200 4 true`); }
  a.log('dân KinhDo:', await R(`townyadmin town KinhDo add ${A}`));
  a = await moveTo(a, PX, PZ);
  const p = a.bot.entity.position.floored();
  // ---- c4 Rìu Bán
  await R(`setblock ${p.x + 2} ${p.y} ${p.z} minecraft:chest`);
  await R(`minecraft:item replace block ${p.x + 2} ${p.y} ${p.z} container.0 with minecraft:wheat 64`);
  await R(`minecraft:item replace block ${p.x + 2} ${p.y} ${p.z} container.1 with minecraft:iron_ingot 32`);
  a.log('Rìu Bán:', await R(`xuong tang ban_ruong ${A}`)); await a.sleep(800);
  const chestHit = async (t, label) => {
    const ch = t.bot.blockAt(new (require('vec3'))(p.x + 2, p.y, p.z));
    const n0 = t.chat.length, b0 = await bal(t.bot.username);
    const bar = []; const ab = q => bar.push(JSON.stringify(q.text).replace(/"type":"[a-z]+",|"value":|[{}\[\]"]|text:/g, '').slice(0, 100)); t.bot._client.on('action_bar', ab);
    await t.bot.lookAt(ch.position.offset(0.5, 0.5, 0.5), true);
    t.bot._client.write('block_dig', { status: 0, location: ch.position, face: 1, sequence: 1 }); await t.sleep(300);
    t.bot._client.write('block_dig', { status: 1, location: ch.position, face: 1, sequence: 2 }); await t.sleep(1500);
    t.bot._client.removeListener('action_bar', ab);
    const left = (await R(`data get block ${p.x + 2} ${p.y} ${p.z} Items`)).replace(/.*data: /, '').slice(0, 90);
    t.log(`c4 ${label}: tiền ${b0} -> ${await bal(t.bot.username)} | rương còn: ${left} | tin: ${t.chat.slice(n0).concat(bar.filter(x => !x.includes('❤'))).join(' | ').slice(0, 200)}`);
  };
  await chestHit(a, 'A (dân KinhDo) đánh Rìu Bán vào rương trong đất kinh đô');
  await R(`minecraft:item replace block ${p.x + 2} ${p.y} ${p.z} container.0 with minecraft:wheat 64`);
  let b = await start(B); await b.sleep(5000); await R(`minecraft:tp ${B} ${A}`); await b.end(); await b.sleep(2500); b = await start(B); await b.sleep(2000);
  await R(`xuong tang ban_ruong ${B}`); await b.sleep(800);
  await chestHit(b, 'B (người ngoài) đánh Rìu Bán vào cùng rương');
  await R(`setblock ${p.x + 2} ${p.y} ${p.z} minecraft:air`);
  // ---- c4 Bắt Thú
  await R(`minecraft:clear ${A}`); a.log('Bắt Thú:', await R(`xuong tang bat_thu ${A}`)); await a.sleep(800);
  const tryCatch = async (summon, label, name) => {
    await R(`execute at ${A} run summon ${summon}`); await a.sleep(1000);
    const e = a.bot.nearestEntity(x => x.name === name); if (!e) return a.log(`c4 ${label}: không thấy`);
    const n0 = a.chat.length; await a.bot.lookAt(e.position.offset(0, 0.6, 0), true); await a.use(e); await a.sleep(800);
    // đếm bằng clear ... 0 (data get Inventory qua rcon bị cắt "..." khi đồ có components)
    const eggs = ((await R(`minecraft:clear ${A} minecraft:${name}_spawn_egg 0`)).match(/Found (\d+)/) || [0, 0])[1];
    const still = /passed/.test(await R(`execute at ${A} if entity @e[type=${name},distance=..6]`));
    a.log(`c4 ${label}: còn con vật ${still} | trứng ${name}_spawn_egg trong túi: ${eggs} | tin: ${a.chat.slice(n0).join(' | ').slice(0, 150)}`);
    await R(`execute at ${A} run minecraft:kill @e[type=${name},distance=..6]`);
  };
  await tryCatch('cow ~1 ~ ~ {NoAI:1b}', 'bò', 'cow');
  await tryCatch('zombie ~1 ~ ~ {NoAI:1b}', 'zombie', 'zombie');
  await tryCatch(`wolf ~1 ~ ~ {NoAI:1b,Owner:[I;1,2,3,4]}`, 'sói có chủ (người khác)', 'wolf');
  // ---- c7 Xu Quái: 200 zombie thường, A hạ (dọn đồ rơi cũ quanh đó trước)
  await R(`minecraft:clear ${A}`); await R(`execute at ${A} run minecraft:kill @e[type=item,distance=..16]`);
  const KILLS = 200;
  for (let i = 0; i < KILLS; i++) {
    await R(`execute at ${A} run summon zombie ~2 ~ ~ {NoAI:1b,Tags:["thuc7"],IsBaby:0b}`);
    await R(`execute at ${A} run damage @e[tag=thuc7,limit=1] 100 minecraft:player_attack by ${A}`);
    if (i % 10 === 9) { await a.sleep(600); await R(`execute at ${A} run minecraft:tp @e[type=item,distance=..8] ${A}`); await a.sleep(1200); await R(`minecraft:clear ${A} minecraft:rotten_flesh`); }
  }
  await a.sleep(1500);
  // Xu Quái = gold_nugget có item_model riêng (zombie thường không rơi gold_nugget); đếm bằng clear ... 0
  const xq = +(((await R(`minecraft:clear ${A} minecraft:gold_nugget 0`)).match(/Found (\d+)/) || [0, 0])[1]);
  const pct = (100 * xq / KILLS).toFixed(1), se = (100 * Math.sqrt(0.05 * 0.95 / KILLS)).toFixed(1);
  a.log(`c7 hạ ${KILLS} zombie thường: ${xq} Xu Quái = ${pct}% (Cài đặt chance 5% => kỳ vọng ${KILLS * 0.05}, sai số chuẩn ±${se}%)`);
  if (!process.argv.includes('c8')) { a.log('bỏ khỏi KinhDo:', await R(`townyadmin town KinhDo kick ${A}`)); process.exit(0); }
  // ---- c8 /xuong món Khoan Đào cần thêm Tinh Luyện (ở spawn mới mở /xuong)
  a = await moveTo(a, 12699, -2523);
  let w = await a.openMenu('/xuong');
  const it = w ? w.slots.slice(0, w.inventoryStart).find(x => x && /Khoan Đào/.test(a.itemText(x).name)) : null;
  a.log('c8 Khoan Đào trong /xuong:', it ? a.itemText(it).lore.join(' / ').slice(0, 300) : 'không thấy');
  if (it) { const i = w.slots.indexOf(it); w = await a.click(i); const it2 = w && w.slots.slice(0, w.inventoryStart).find(x => x && /Khoan Đào/.test(a.itemText(x).name)); a.log('c8 trang công thức:', it2 ? a.itemText(it2).lore.join(' / ').slice(0, 300) : '(không đọc được)'); }
  if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  a.log('bỏ khỏi KinhDo:', await R(`townyadmin town KinhDo kick ${A}`));
  await R(`minecraft:clear ${A}`); await R(`minecraft:clear ${B}`);
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
