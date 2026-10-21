// q3 bản đồ kho báu: /khobau give -> đọc vị trí trong dấu PDC của bản đồ -> dò lạnh/ấm/nóng (thanh hành động) ở 200/60/15/0 khối
// -> Shift đào -> lính canh ra -> hạ hết (lệnh kill) -> nhận quà; lần 2 không hạ, để quá guard-minutes (wrapper tạm đặt 1 phút) -> trả bản đồ.
const { start, moveTo } = require('./h');
const N = 'ThuKB';
function flat(c) { if (c == null) return ''; if (typeof c === 'string') { try { return flat(JSON.parse(c)); } catch (e) { return c; } } if (Array.isArray(c)) return c.map(flat).join(''); if (typeof c === 'object') { if (c.type === 'compound' || c.type === 'list' || c.type === 'string') return flat(c.value); let s = (c.text ?? c[''] ?? ''); if (typeof s === 'object') s = flat(s); return s + flat(c.extra); } return String(c); }
(async () => {
  let t = await start(N);
  const R = c => t.rc.cmd(c);
  const bar = [];
  const hook = tt => tt.bot._client.on('action_bar', p => { const x = tt.strip(flat(p.text)); if (!x.includes('❤ [')) bar.push(x); });
  hook(t);
  await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`);
  const getMap = async () => {
    t.log('/khobau give:', await R(`khobau give ${N}`));
    await t.sleep(800);
    const raw = await R(`data get entity ${N} SelectedItem.components."minecraft:custom_data".PublicBukkitValues`);
    const m = raw.match(/"([^"]*);(\w+);(-?\d+);(-?\d+);(-?\d+)"/);
    return m ? { tier: m[1].split(':').pop(), x: +m[3], y: +m[4], z: +m[5] } : (t.log('không đọc được bản đồ:', raw.slice(0, 200)), null);
  };
  const go = async (x, z) => { t = await moveTo(t, x, z); hook(t); await R(`effect give ${N} minecraft:resistance 600 4 true`); await t.sleep(2500); return bar.slice(-1)[0] || '(không có thanh)'; };
  let mp = await getMap();
  if (!mp) process.exit(1);
  t.log('q3 bản đồ:', JSON.stringify(mp));
  for (const d of [200, 60, 15]) t.log(`q3 cách ${d} khối:`, await go(mp.x + d, mp.z));
  t.log('q3 tới nơi:', await go(mp.x, mp.z));
  const n0 = t.chat.length;
  t.bot.setControlState('sneak', true); await t.sleep(600); t.bot.setControlState('sneak', false); await t.sleep(1500);
  t.log('q3 Shift đào:', t.chat.slice(n0).join(' | ').slice(0, 250));
  t.log('   lính canh:', (await R(`execute at ${N} if entity @e[distance=..15,name="☠ Lính canh kho báu"]`)).slice(-12), '| tên:', await R(`execute at ${N} run data get entity @e[distance=..15,type=!player,type=!item,limit=1,sort=nearest] CustomName`).then(x => x.slice(-60)));
  const b0 = (await R(`bal ${N}`)).replace(/\s+/g, ' ');
  await R(`execute at ${N} run minecraft:kill @e[distance=..15,type=!player,type=!item]`);
  await t.sleep(3000);
  t.log('q3 hạ hết lính canh:', t.chat.slice(n0).filter(l => /rương|Mở|kho báu/i.test(l)).join(' | ').slice(0, 250), '| tiền', b0, '->', (await R(`bal ${N}`)).replace(/\s+/g, ' '));
  // lần 2: không hạ, để hết giờ
  await R(`minecraft:clear ${N}`);
  mp = await getMap(); if (!mp) process.exit(1);
  await go(mp.x, mp.z);
  const n1 = t.chat.length;
  t.bot.setControlState('sneak', true); await t.sleep(600); t.bot.setControlState('sneak', false);
  await t.sleep(75000);
  t.log('q3 để hết giờ:', t.chat.slice(n1).filter(l => /kho báu|bản đồ/i.test(l)).join(' | ').slice(0, 300), '| bản đồ trong túi:', (await R(`minecraft:clear ${N} minecraft:paper 0`)).slice(0, 50));
  await R(`execute at ${N} run minecraft:kill @e[distance=..15,type=!player,type=!item]`);
  await R(`minecraft:clear ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
