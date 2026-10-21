// /xemdo + phím F trong túi đồ (practice-1 hoặc banghoi-1: HOST=...). Món dài (25 dòng lore) bấm F -> in chat, không đổi tay;
// món ngắn bấm F -> đổi sang tay phụ như thường.
const h = require('./h');
const HOST = process.env.HOST || 'practice-1', N = process.env.BOTN || 'ThuPA';
(async () => {
  const A = await h.start(N, undefined, HOST);
  const R = c => A.rc.cmd(c);
  await R(`minecraft:clear ${N}`);
  const lore = Array.from({ length: 25 }, (_, i) => `'{"text":"Dòng mô tả ${i + 1}","italic":false}'`).join(',');
  A.log((await R(`minecraft:give ${N} minecraft:diamond_chestplate[lore=[${lore}],enchantments={protection:4,blast_protection:3,fire_protection:2,unbreaking:3}]`)).trim());
  await R(`minecraft:give ${N} minecraft:stick`); await A.sleep(1200);
  const inv = () => A.bot.inventory.slots.map((s, i) => s ? i + ':' + s.name : null).filter(Boolean).join(' ');
  A.log('túi:', inv());
  // /xemdo với món đang cầm
  await A.bot.equip(A.bot.inventory.items().find(i => i.name === 'diamond_chestplate'), 'hand'); await A.sleep(600);
  let n0 = A.chat.length; A.bot.chat('/xemdo'); await A.sleep(1500);
  const got = A.chat.slice(n0); A.log('/xemdo nhận', got.length, 'dòng:', got.slice(0, 3).join(' | '), '...', got.slice(-2).join(' | '));
  // F trong túi: món dài
  const slotOf = name => A.bot.inventory.slots.findIndex(s => s && s.name === name);
  // phím F trên ô (mineflayer không cho mode 2 nút 40): gửi gói window_click thô, cửa sổ 0 = túi đồ
  const pressF = slot => A.bot._client.write('window_click', { windowId: 0, stateId: A.bot.inventory.stateId ?? 0, slot, mouseButton: 40, mode: 2, changedSlots: [], cursorItem: { itemCount: 0 } });
  await A.sleep(1000);
  n0 = A.chat.length; pressF(slotOf('diamond_chestplate')); await A.sleep(1500);
  A.log('F món dài: chat', A.chat.slice(n0).length, 'dòng | tay phụ (server):', (await R(`data get entity ${N} equipment.offhand.id`)).replace(/.*data: /, ''));
  await A.sleep(1000);
  n0 = A.chat.length; pressF(slotOf('stick')); await A.sleep(1500);
  A.log('F món ngắn: chat', A.chat.slice(n0).length, 'dòng | túi:', inv(), '| tay phụ (server):', (await R(`data get entity ${N} equipment.offhand.id`)).replace(/.*data: /, ''));
  await R(`minecraft:clear ${N}`); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
