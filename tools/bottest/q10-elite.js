// q10 tiền rơi từ quái EliteMobs (Cài đặt em_vault, em_coin_mult) + Xu Quái từ quái tinh nhuệ (tl_elite 40%, tối đa 3).
// Bot ở đất hoang hạ N con elite cấp L (/em spawnelite), nhặt tiền rơi, đo xu trước/sau. Args: [số con] [cấp]
const { start, moveTo } = require('./h');
const N = 'ThuQ10', K = +process.argv[2] || 5, LV = +process.argv[3] || 10;
(async () => {
  let t = await start(N, '10.9.3.10');
  const R = c => t.rc.cmd(c);
  const bal = async () => +((await R(`bal ${N}`)).replace(/.*:\s*/, '').replace(/[^\d.]/g, '') || 0);
  await t.sleep(4000);
  await R(`lp user ${N} permission set grim.exempt true`); await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`); await R(`eco set ${N} 1000`);
  t = await moveTo(t, 13200, -2900, 'minecraft:overworld', '10.9.3.10');
  await R(`effect give ${N} minecraft:resistance 600 4 true`); await R(`effect give ${N} minecraft:strength 600 9 true`);
  await R(`minecraft:item replace entity ${N} weapon.mainhand with minecraft:netherite_sword`); t.bot.setQuickBarSlot(0);
  await R('minecraft:time set 1000');
  await R(`lp user ${N} permission settemp elitemobs.* true 15m`); await t.sleep(1500);   // bot tự gõ /em spawn elite (lệnh chỉ chạy từ người chơi)
  const b0 = await bal();
  let killed = 0;
  for (let i = 0; i < K; i++) {
    t.log('  spawn:', (await t.say(`/em spawn elite zombie ${LV}`, 1200)).join(' | ').slice(0, 120));
    await t.sleep(1200);
    for (let h = 0; h < 25; h++) {
      const z = t.bot.nearestEntity(e => e.name === 'zombie' && e.position.distanceTo(t.bot.entity.position) < 8);
      if (!z) { killed++; break; }
      await t.bot.lookAt(z.position.offset(0, 1, 0), true); t.bot.attack(z); await t.sleep(650);
    }
    await t.sleep(1200); await R(`execute at ${N} run minecraft:tp @e[type=item,distance=..10] ${N}`); await t.sleep(1500);
  }
  await t.sleep(2000);
  const xq = (await R(`minecraft:clear ${N} minecraft:gold_nugget 0`)).trim();
  t.log('  tin EliteMobs:', t.chat.filter(l => /EM\]|EliteMobs|Elite Coins|coins|xu/i.test(l)).slice(-6).join(' | ').slice(0, 500));
  t.log(`q10 hạ ${killed}/${K} elite cấp ${LV}: xu ${b0} -> ${await bal()} | Xu Quái (gold_nugget): ${xq} | đồ khác: ${t.bot.inventory.items().map(i => i.name + '×' + i.count).join(', ').slice(0, 200)}`);
  await R(`execute at ${N} run minecraft:kill @e[type=zombie,distance=..12]`); await R(`minecraft:clear ${N}`); await R(`lp user ${N} permission unset grim.exempt`); await R(`lp user ${N} permission unsettemp elitemobs.*`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
