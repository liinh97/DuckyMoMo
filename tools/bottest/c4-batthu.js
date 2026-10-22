// c4 Bắt Thú ở đất hoang: bò -> cow_spawn_egg, sói có chủ / bò đặt tên: bị từ chối. Đọc cả action bar.
const { start, moveTo } = require('./h');
const A = 'ThuX1';
function flat(c) { if (c == null) return ''; if (typeof c === 'string') { try { return flat(JSON.parse(c)); } catch (e) { return c; } } if (Array.isArray(c)) return c.map(flat).join(''); if (typeof c === 'object') { if (c.type === 'compound' || c.type === 'list' || c.type === 'string') return flat(c.value); let s = (c.text ?? c[''] ?? ''); if (typeof s === 'object') s = flat(s); return s + flat(c.extra); } return String(c); }
(async () => {
  let a = await start(A);
  const R = c => a.rc.cmd(c);
  await R(`gamemode survival ${A}`); await R(`effect give ${A} minecraft:resistance 600 4 true`);
  a = await moveTo(a, 13200, -2900);
  const bar = []; a.bot._client.on('action_bar', p => { const x = a.strip(flat(p.text)); if (!x.includes('❤ [')) bar.push(x); });
  await R(`minecraft:clear ${A}`); a.bot.setQuickBarSlot(0); await a.sleep(300);
  a.log('Bắt Thú:', await R(`xuong tang bat_thu ${A}`)); await a.sleep(800);
  a.log('tay cầm:', a.bot.heldItem ? a.itemText(a.bot.heldItem).name + ' | ' + a.itemText(a.bot.heldItem).lore.join(' / ').slice(0, 120) : 'trống');
  const tryCatch = async (summon, label, name) => {
    await R(`execute at ${A} run minecraft:kill @e[type=${name},distance=..8]`);
    await R(`execute at ${A} run summon ${summon}`); await a.sleep(1000);
    const e = a.bot.nearestEntity(x => x.name === name); if (!e) return a.log(`c4 ${label}: không thấy`);
    const n0 = a.chat.length, b0 = bar.length; await a.bot.lookAt(e.position.offset(0, 0.6, 0), true); await a.use(e); await a.sleep(1000);
    const eggs = ((await R(`minecraft:clear ${A} minecraft:${name}_spawn_egg 0`)).match(/Found (\d+)/) || [0, 0])[1];
    const still = /passed/.test(await R(`execute at ${A} if entity @e[type=${name},distance=..6]`));
    a.log(`c4 ${label}: còn con vật ${still} | ${name}_spawn_egg: ${eggs} | tin: ${a.chat.slice(n0).concat(bar.slice(b0)).join(' | ').slice(0, 150)}`);
    await R(`execute at ${A} run minecraft:kill @e[type=${name},distance=..8]`);
  };
  await tryCatch('cow ~1 ~ ~ {NoAI:1b}', 'bò', 'cow');
  await tryCatch('cow ~1 ~ ~ {NoAI:1b,CustomName:"Bo"}', 'bò đã đặt tên', 'cow');
  await tryCatch('wolf ~1 ~ ~ {NoAI:1b,Owner:[I;1,2,3,4]}', 'sói có chủ (người khác)', 'wolf');
  await tryCatch('sheep ~1 ~ ~ {NoAI:1b}', 'cừu', 'sheep');
  a.log('Bắt Thú còn lại:', a.bot.heldItem ? a.itemText(a.bot.heldItem).lore.filter(l => /lần|Lần|bền/.test(l)).join(' / ') : 'hết (trống tay)');
  await R(`minecraft:clear ${A}`); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
