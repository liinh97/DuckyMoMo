// s10 giới hạn đơn Chợ đen (thu mua): Tị Nạn orders 2 (Cài đặt dh_max / play-ranks.tiers.tinan.orders), Lương Dân 4.
// Kiểm cả số lượng > max-amount, giá > max-price, tiền giữ + phí 2% (không hoàn khi huỷ). Xong: huỷ hết đơn, gỡ nhóm luongdan.
const { start, moveTo } = require('./h');
const N = process.argv[2] || 'ThuS10';
(async () => {
  let t = await start(N);
  const R = c => t.rc.cmd(c);
  const bal = async () => +((await R(`bal ${N}`)).replace(/[^\d.]/g, '') || 0);
  await R(`lp user ${N} parent remove luongdan`); await R(`eco set ${N} 100000`); await R(`gamemode survival ${N}`);
  t = await moveTo(t, 12699, -2523);
  const hand = async () => { await R(`minecraft:clear ${N}`); await R(`minecraft:item replace entity ${N} weapon.mainhand with minecraft:dirt 1`); t.bot.setQuickBarSlot(0); await t.sleep(500); };
  await hand();
  const ids = [];
  for (const id of (process.argv[3] || '').split(',').filter(Boolean)) t.log(`huỷ đơn cũ #${id}:`, (await t.say('/choden huy ' + id, 1000)).join(' | ').slice(0, 150));
  const dat = async (args, label) => { const b0 = await bal(); const r = await t.say('/choden dat ' + args, 1500); ids.push(...(r.join(' ').match(/đơn #(\d+)/g) || []).map(x => x.replace(/\D/g, ''))); t.log(`${label}: ${r.join(' | ').slice(0, 180)} | tiền ${b0} -> ${await bal()}`); return r.join(' '); };
  await dat('6401 1', 'số lượng 6401 (Cài đặt tối đa 6400)');
  await dat('10 100001', 'giá 100001 (Cài đặt tối đa 100000)');
  await dat('10 1', 'Tị Nạn đơn 1 (10 × 1 xu, phí 2% => trừ 10,2)');
  await dat('10 1', 'Tị Nạn đơn 2');
  await dat('10 1', 'Tị Nạn đơn 3 (Cài đặt tối đa 2 => phải chặn)');
  await R(`lp user ${N} parent add luongdan`); await t.sleep(1500);
  await t.end(); await t.sleep(2500); t = await start(N); await t.sleep(3000); await hand();
  await dat('10 1', 'Lương Dân đơn 3 (tối đa 4)');
  await dat('10 1', 'Lương Dân đơn 4');
  await dat('10 1', 'Lương Dân đơn 5 (phải chặn)');
  // huỷ các đơn vừa đăng (số đơn đọc từ câu báo "Đã đăng đơn #N")
  const b0 = await bal();
  for (const id of new Set(ids)) t.log(`huỷ #${id}:`, (await t.say('/choden huy ' + id, 1000)).join(' | ').slice(0, 150));
  t.log(`huỷ ${new Set(ids).size} đơn: tiền ${b0} -> ${await bal()} (kỳ vọng +10/đơn, phí 0,2 không hoàn)`);
  await R(`lp user ${N} parent remove luongdan`); await R(`minecraft:clear ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
