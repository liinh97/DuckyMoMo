// Set Chuyên Cần: chờ đủ 5 phút online rồi /diemdanh, đếm Phiếu Chuyên Cần trong túi, xem dòng phiếu online + nút đổi set.
const h = require('./h');
const N = process.env.BOTN || 'ThuCC';
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const phieu = () => t.bot.inventory.items().filter(i => /Phiếu Chuyên Cần/.test(t.itemText(i).name)).reduce((a, i) => a + i.count, 0);
  await R(`minecraft:clear ${N}`);
  for (let i = 0; i < 31; i++) { t.bot.look(i % 2 ? 0 : 1.5, 0); await t.sleep(10000); }   // cử động để không tính đứng im
  const m = await t.say('/diemdanh', 4000);
  t.log('## /diemdanh:', m.filter(x => /điểm danh|Phiếu|phiếu|nhận|xu|☀/i.test(x)).join(' | ').slice(0, 800));
  await t.sleep(1000);
  t.log('## túi:', t.bot.inventory.items().map(i => t.itemText(i).name + ' x' + i.count).join(', '), '| phiếu =', phieu());
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
