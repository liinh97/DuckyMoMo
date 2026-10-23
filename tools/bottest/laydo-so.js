// /dmm laydo <mã> [số] [người] với món xếp chồng (com_nam = bánh mì).
const h = require('./h');
const N = process.env.BOTN || 'ThuDg';
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  await R(`lp user ${N} parent add quanly`); await t.sleep(3000); await R(`minecraft:clear ${N}`); await t.sleep(800);
  const total = () => t.bot.inventory.items().reduce((a, i) => a + i.count, 0);
  for (const c of ['/dmm laydo com_nam', '/dmm laydo com_nam 5', `/dmm laydo com_nam 3 ${N}`, `/dmm laydo com_nam ${N} 2`]) {
    const b = total(); t.bot.chat(c); await t.sleep(1800); t.log(`## ${c}: +${total() - b}`);
  }
  await R(`lp user ${N} parent remove quanly`); await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
