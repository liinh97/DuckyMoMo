// cd14 Giảm giá Xưởng (bậc 1: cấp 20, 20.000 xu, -5%; bậc 2: cấp 50, 80.000 xu, -10%) + cd17 +5% bắt pet (cấp 30, 40.000 xu).
// Đọc giá 1 món /xuong và "xác suất X%" khi bắt heo trượt, trước và sau khi mua ở /capdo shop.
const { start, moveTo } = require('./h');
const N = process.argv[2] || 'ThuCD2';
function flat(c) { if (c == null) return ''; if (typeof c === 'string') { try { return flat(JSON.parse(c)); } catch (e) { return c; } } if (Array.isArray(c)) return c.map(flat).join(''); if (typeof c === 'object') { if (c.type === 'compound' || c.type === 'list' || c.type === 'string') return flat(c.value); let s = (c.text ?? c[''] ?? ''); if (typeof s === 'object') s = flat(s); return s + flat(c.extra); } return String(c); }
(async () => {
  let t = await start(N);
  const R = c => t.rc.cmd(c);
  const bal = async () => +((await R(`bal ${N}`)).replace(/[^\d.]/g, '') || 0);
  await R(`capdo dat ${N} 50`); await R(`eco set ${N} 300000`); await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`);
  t = await moveTo(t, 13200, -2900);
  const bar = [];
  const hookBar = () => t.bot._client.on('action_bar', p => { const x = t.strip(flat(p.text)); if (!x.includes('❤ [')) bar.push(x); });
  hookBar();
  // /xuong chỉ mở ở kinh đô / đất hoà bình (luật mua bán với server) -> về spawn đọc giá rồi quay lại đất hoang
  const xuongPrice = async () => {
    t = await moveTo(t, 12699, -2523); hookBar();
    const w = await t.openMenu('/xuong'); if (!w) return 'không mở /xuong';
    const items = w.slots.slice(0, w.inventoryStart).map((it, i) => it && { i, ten: t.itemText(it).name, gia: t.itemText(it).lore.filter(l => /xu/.test(l)).join(' / ') }).filter(x => x && /xu/.test(x.gia));
    t.bot.closeWindow(w); await t.sleep(400);
    t = await moveTo(t, 13200, -2900); hookBar();
    return items.slice(0, 2).map(x => `${x.ten}: ${x.gia}`).join(' || ').slice(0, 250);
  };
  const catchChance = async () => {
    for (let k = 0; k < 8; k++) {
      await R(`execute at ${N} run minecraft:kill @e[type=pig,distance=..12]`); await t.sleep(300);
      await R(`execute at ${N} run summon pig ~ ~ ~ {NoAI:1b,attributes:[{id:"minecraft:max_health",base:20}],Health:2f}`); await t.sleep(800);
      await R(`minecraft:item replace entity ${N} weapon.mainhand with minecraft:lead 8`); await t.sleep(300);
      const pig = t.bot.nearestEntity(e => e.name === 'pig'); if (!pig) continue;
      await t.bot.lookAt(pig.position.offset(0, 0.5, 0), true); const b0 = bar.length, c0 = t.chat.length;
      t.bot.attack(pig); await t.sleep(1500);
      const m = bar.slice(b0).join(' ').match(/xác suất (\d+)%/);
      if (m) return m[1] + '%';
      if (t.chat.slice(c0).some(l => /thuần hoá/.test(l))) { const nm = ((await t.say('/petinfo', 1200)).join(' ').match(/^\s*([^:]+):/) || [])[1]; if (nm) await t.say('/petrelease ' + nm.trim(), 1200); }
      await t.sleep(2200);
    }
    return '(8 lần không trượt lần nào) | tin cuối: ' + bar.slice(-3).concat(t.chat.slice(-3)).join(' | ').slice(0, 250);
  };
  const shopClick = async re => {
    let w = await t.openMenu('/capdo'); if (!w) return 'không mở /capdo';
    const dq = w.slots.slice(0, w.inventoryStart).findIndex(it => it && /Đặc quyền/.test(t.itemText(it).name)); if (dq >= 0) w = await t.click(dq);
    const i = w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
    if (i < 0) { t.bot.closeWindow(w); return 'không thấy món: ' + w.slots.slice(0, w.inventoryStart).filter(Boolean).map(x => t.itemText(x).name).join(', ').slice(0, 200); }
    const lore = t.itemText(w.slots[i]).lore.filter(l => /bậc|Bậc|xu|cấp|Cấp/.test(l)).join(' / ').slice(0, 160);
    const b0 = await bal(), n0 = t.chat.length;
    await t.click(i); await t.sleep(1200);
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
    return `[${lore}] -> ${t.chat.slice(n0).join(' | ').slice(0, 160)} | tiền ${b0} -> ${await bal()}`;
  };
  t.log('cd14 giá Xưởng trước:', await xuongPrice());
  t.log('cd17 xác suất bắt heo trước:', await catchChance());
  t.log('cd14 mua Giảm giá Xưởng lần 1:', await shopClick(/Giảm giá Xưởng/));
  t.log('cd14 giá Xưởng sau bậc 1:', await xuongPrice());
  t.log('cd14 mua Giảm giá Xưởng lần 2:', await shopClick(/Giảm giá Xưởng/));
  t.log('cd14 giá Xưởng sau bậc 2:', await xuongPrice());
  t.log('cd14 mua lần 3 (hết bậc):', await shopClick(/Giảm giá Xưởng/));
  t.log('cd17 mua +5% bắt pet:', await shopClick(/bắt pet/));
  t.log('cd17 xác suất bắt heo sau:', await catchChance());
  await R(`execute at ${N} run minecraft:kill @e[type=pig,distance=..12]`); await R(`minecraft:clear ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
