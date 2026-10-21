// n6 cá theo mùa: ép mùa mưa rồi mùa khô (/mua dat), bot câu ở ao tự đào (không phải biển) mỗi mùa N con, xem tên cá EMF.
// Cá mùa mưa: cá linh, cá lóc đồng, cá rô phi…; mùa khô: cá bống, cá kèo… Cá biển (cá cơm, cá nục) không được ra ở ao.
// Cuối: /mua dat tudong. Cần cần câu Lure III cho nhanh.
const { start, moveTo } = require('./h');
const N = 'ThuNa';
const PER = +(process.argv[2] || 12);
(async () => {
  let t = await start(N);
  t = await moveTo(t, 13200, -2900);
  const R = c => t.rc.cmd(c);
  await R(`gamemode survival ${N}`); await R(`effect give ${N} minecraft:resistance 1800 4 true`);
  const p = t.bot.entity.position.floored();
  await R(`fill ${p.x + 2} ${p.y - 3} ${p.z - 2} ${p.x + 6} ${p.y - 1} ${p.z + 2} minecraft:water`);
  await R(`fill ${p.x + 2} ${p.y} ${p.z - 2} ${p.x + 6} ${p.y + 3} ${p.z + 2} minecraft:air`);
  const fishSeason = async season => {
    t.log(`/mua dat ${season}:`, await R(`mua dat ${season}`));
    const got = {};
    for (let i = 0; i < PER; i++) {
      await R(`minecraft:item replace entity ${N} weapon.mainhand with minecraft:fishing_rod[minecraft:enchantments={lure:3}] 1`); await t.sleep(400);
      await t.bot.lookAt(new (require('vec3'))(p.x + 4, p.y - 1, p.z), true);
      const n0 = t.chat.length;
      const ok = await Promise.race([t.bot.fish().then(() => true).catch(e => 'lỗi ' + e.message.slice(0, 40)), t.sleep(45000).then(() => 'quá 45s')]);
      await t.sleep(800);
      const msg = t.chat.slice(n0).join(' | ');
      const it = t.bot.inventory.items().filter(x => x.name !== 'fishing_rod').map(x => t.itemText(x).name.replace(/\s+/g, ' ').trim());
      const name = (msg.match(/câu được ([^|!.]+)/i) || [])[1] || it.join(',') || String(ok);
      got[name] = (got[name] || 0) + 1;
      await R(`minecraft:clear ${N}`);
    }
    t.log(`n6 ${season}:`, JSON.stringify(got).slice(0, 600));
  };
  await fishSeason('mua');
  await fishSeason('kho');
  await R('mua dat tudong');
  await R(`fill ${p.x + 2} ${p.y - 3} ${p.z - 2} ${p.x + 6} ${p.y - 1} ${p.z + 2} minecraft:dirt`);
  await R(`fill ${p.x + 2} ${p.y - 1} ${p.z - 2} ${p.x + 6} ${p.y - 1} ${p.z + 2} minecraft:grass_block`);
  t.log('đã trả: mùa theo lịch, lấp ao');
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
