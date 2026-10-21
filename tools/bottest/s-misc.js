// s8 đồ giả bằng đe (đặt tên giống món Tinh Luyện), o6 /taikhoanphu, o5 /choilai (bot ThuS), s16 sách cực dài, s17 200 con bò 1 ô.
const { start, moveTo } = require('./h');
const N = 'ThuS';
(async () => {
  let t = await start(N);
  const R = c => t.rc.cmd(c);
  const bal = async () => +((await R(`bal ${N}`)).replace(/[^\d.]/g, '') || 0);
  await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`); await R(`eco set ${N} 5000`);
  // s8: than đổi tên giống "Tinh Luyện Than" (đe = custom_name)
  await R(`minecraft:item replace entity ${N} weapon.mainhand with minecraft:coal[minecraft:custom_name='"Tinh Luyện Than"'] 64`); await t.sleep(500);
  let b0 = await bal();
  t.log('s8 /tinhluyen ban (than giả):', (await t.say('/tinhluyen ban', 1500)).join(' | ').slice(0, 200), '| tiền', b0, '->', await bal());
  t.log('s8 /sell hand (than giả):', (await t.say('/sell hand', 1500)).join(' | ').slice(0, 200), '| tiền ->', await bal());
  t.log('s8 /tinhluyen (menu đổi):', (await t.say('/tinhluyen', 1200)).join(' | ').slice(0, 120));
  // Xưởng: món cần nguyên liệu Tinh Luyện -> mở /xuong, xem có nhận than giả không (chỉ in tin)
  const w = await t.openMenu('/xuong'); t.log('s8 /xuong mở:', !!w); if (w) t.bot.closeWindow(w);
  await R(`minecraft:clear ${N}`);
  // o6
  t.log('o6 /taikhoanphu:', (await t.say('/taikhoanphu', 1500)).join(' | ').slice(0, 400));
  // s16 sách: viết 100 trang x 2000 ký tự (vượt page-max 2560 của Paper? mỗi trang 2000 < 2560 nhưng tổng lớn)
  await R(`minecraft:give ${N} minecraft:writable_book 1`); await t.sleep(800);
  const slot = t.bot.inventory.items().find(i => i.name === 'writable_book');
  if (slot) {
    const pages = Array.from({ length: 100 }, (_, i) => ('Trang ' + i + ' ' + '§k'.repeat(10) + 'ẤẦẨẪẬ'.repeat(380)).slice(0, 2000));
    let kicked = null; t.bot.once('kicked', r => { kicked = JSON.stringify(r).slice(0, 200); });
    await t.bot.writeBook(slot.slot, pages).catch(e => t.log('  writeBook lỗi:', e.message.slice(0, 80)));
    await t.sleep(2500);
    t.log('s16 sách 100 trang x 2000 ký tự:', kicked ? 'BỊ ĐÁ: ' + kicked : 'không bị đá', '| sách trong túi:', (await R(`data get entity ${N} Inventory[{id:"minecraft:writable_book"}].components`)).replace(/.*data: /, '').slice(0, 120));
  }
  if (!t.bot.player) t = await start(N);
  await R(`minecraft:clear ${N}`);
  // s17: 200 bò 1 ô ở đất hoang
  t = await moveTo(t, 13200, -2900);
  const p = t.bot.entity.position.floored();
  const tps0 = (await R('tps')).replace(/\s+/g, ' ').slice(0, 120);
  for (let i = 0; i < 200; i++) await R(`summon cow ${p.x + 3} ${p.y} ${p.z} {Tags:["thus17"]}`);
  await t.sleep(5000);
  const left = (await R('execute if entity @e[tag=thus17]')).replace(/.*Count: /, '');
  t.log('s17 triệu hồi 200 bò 1 ô -> còn', left, '| tps trước:', tps0, '| sau:', (await R('tps')).replace(/\s+/g, ' ').slice(0, 120));
  await R('minecraft:kill @e[tag=thus17]');
  // o5 /choilai
  await R(`minecraft:give ${N} minecraft:diamond 5`); await R(`eco set ${N} 3000`);
  t.log('o5 /choilai:', (await t.say('/choilai', 1500)).join(' | ').slice(0, 300));
  t.log('o5 /choilai xacnhan:', (await t.say('/choilai xacnhan', 4000)).join(' | ').slice(0, 300));
  await t.sleep(2000);
  t.log('o5 sau: tiền', await bal(), '| kim cương', (await R(`minecraft:clear ${N} minecraft:diamond 0`)).replace(/.*Found /, ''), '| danh phận', (await t.say('/danhphan', 1500)).join(' ').slice(0, 80));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
