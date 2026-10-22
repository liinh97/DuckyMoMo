// Công thành giả, bước 2: vua ThuW1 (ThuNuocA) cắm cờ màu (cờ trắng = đầu hàng) ở đất hoang sát ThuThanhB -> bắt đầu cuộc vây (SiegeWar conquest).
// Wrapper đã bỏ miễn vây thị trấn mới (/swa siegeimmunity town ThuThanhB 0). Cờ phải sát mép đất thị trấn (cách tối đa 1 khối):
// ThuThanhB bắt đầu ở x 13632 -> cờ ở x 13631.
const { start, moveTo } = require('./h');
const A = 'ThuW1';
(async () => {
  let t = await start(A, '10.9.1.1');
  const R = c => t.rc.cmd(c);
  await R(`lp user ${A} permission set grim.exempt true`); await R(`gamemode survival ${A}`);
  t = await moveTo(t, 13630, -3150, 'minecraft:overworld', '10.9.1.1');
  const tin = []; t.bot.on('messagestr', m => tin.push(m));
  t.log('kẻ thù:', (await t.say('/n enemy add ThuNuocB', 2000)).join(' | ').slice(0, 300)); tin.length = 0;
  await R(`minecraft:clear ${A}`); await R(`minecraft:item replace entity ${A} weapon.mainhand with minecraft:red_banner`); t.bot.setQuickBarSlot(0); await t.sleep(600);
  // vị trí theo SERVER (bot qua Via có thể lệch 1 khối theo chiều cao)
  const pos = (await R(`data get entity ${A} Pos`)).match(/\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/).slice(1).map(Number);
  const p = { x: Math.floor(pos[0]), y: Math.floor(pos[1]), z: Math.floor(pos[2]) };
  t.log('server: bot ở', p, '| bot tự thấy', t.bot.entity.position.floored());
  // san phẳng 2 ô bên cạnh ngang chân bot (nền cỏ, trên trống) để chắc chắn đặt được cờ (đất thử, ghi trong HANDOFF)
  const gx = p.x + 1, gz = p.z, gy = p.y - 1;
  await R(`fill ${gx} ${gy} ${gz - 1} ${gx + 1} ${gy} ${gz + 1} minecraft:grass_block`);
  await R(`fill ${gx} ${p.y} ${gz - 1} ${gx + 1} ${p.y + 3} ${gz + 1} minecraft:air`); await t.sleep(800);
  t.log('khối nền server:', (await R(`execute if block ${gx} ${gy} ${gz} #minecraft:dirt`)).trim(), '| ô trên trống:', (await R(`execute if block ${gx} ${gy + 1} ${gz} minecraft:air`)).trim());
  await t.bot.lookAt(new (require('vec3'))(gx + 0.5, gy + 1, gz + 0.5), true);
  t.bot._client.write('block_place', { location: { x: gx, y: gy, z: gz }, direction: 1, hand: 0, cursorX: 0.5, cursorY: 1, cursorZ: 0.5, insideBlock: false, sequence: 77, worldBorderHit: false });
  await t.sleep(3000);
  const placed = /passed/.test(await R(`execute if block ${gx} ${gy + 1} ${gz} minecraft:red_banner`));
  t.log('cờ đã đứng:', placed, '| tin:', tin.join(' | ').replace(/\s+/g, ' ').slice(0, 900));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
