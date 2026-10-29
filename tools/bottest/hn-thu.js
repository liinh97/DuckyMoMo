// Hầm Ngục DuckyMoMo (29/10): bot vào Hầm Mộ Cổ, kiểm dựng (biển, lồng, cửa), quái ra từ lồng theo đợt, hạ bằng /damage by bot -> cửa mở, rơi đồ, vượt.
const h = require('./h');
const N = process.env.BOTN || 'ThuHamNguc';
const DG = process.env.DG || 'hammo';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  const W = 'minecraft:hamnguc';
  await R(`minecraft:clear ${N}`); await R(`effect give ${N} minecraft:resistance 900 4 true`); await R(`lp user ${N} permission set duckymomo.hamnguc.thu true`); await t.sleep(1500);
  await R(`execute in minecraft:overworld run minecraft:tp ${N} 12699 80 -2523`); await t.sleep(3000);
  t.bot.chat(`/hamnguc di ${DG}`);
  const dim = async () => (await R(`data get entity ${N} Dimension`)).replace(/.*data: /, '').replace(/"/g, '');
  for (let i = 0; i < 20 && !/hamnguc/.test(await dim()); i++) await t.sleep(1000);
  t.log('## vào:', await dim(), '|', msgs.filter(m => /Hầm|dựng|⚔/.test(m)).slice(-2).join(' / '));
  const pos = async () => (await R(`data get entity ${N} Pos`)).replace(/.*data: /, '');
  t.log('vị trí', await pos());
  const blk = async (x, y, z, b) => /passed/.test(await R(`execute in ${W} if block ${x} ${y} ${z} ${b}`));
  // 29/10 phòng to: cỡ ruột 21/29/33/29/41, hành lang 9 -> x tường trái 0, 32, 72, 116, 156; sàn y=-60
  const SZ = [21, 29, 33, 29, 41]; const RX = [0]; for (let i = 1; i < SZ.length; i++) RX.push(RX[i - 1] + SZ[i - 1] + 2 + 9);
  t.log('biển phòng đầu:', await blk(1, -57, 0, '#minecraft:wall_signs'), '| lồng phòng 1:', await blk(RX[1] + 5, -59, -13, 'minecraft:spawner'),
        '| cửa ra phòng 1 đóng:', await blk(RX[1] + SZ[1] + 1, -58, 0, 'minecraft:iron_bars'));
  for (let i = 1; i < SZ.length; i++) {
    const x0 = RX[i], s = SZ[i], hz = Math.floor(s / 2);
    const box = `x=${x0},y=-62,z=${-hz - 1},dx=${s + 1},dy=22,dz=${s + 1}`;
    await R(`execute in ${W} run minecraft:tp ${N} ${x0 + 4} -59 0`); await t.sleep(3000);
    let kills = 0;
    for (let k = 0; k < 50; k++) {
      const c = await R(`execute in ${W} if entity @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,${box}]`);
      const n = +(c.match(/Count: (\d+)/) || [0, 0])[1];
      if (n === 0) { await t.sleep(2000); const c2 = await R(`execute in ${W} if entity @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,${box}]`); if (!/Count: [1-9]/.test(c2)) break; }
      if (k === 0) t.log(`phòng ${i}: ${n} quái`, (await R(`execute in ${W} run data get entity @e[type=!minecraft:player,type=!minecraft:item,${box},limit=1] CustomName`)).replace(/.*data: /, '').slice(0, 80));
      await R(`execute in ${W} as @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,${box}] run damage @s 5000 minecraft:player_attack by ${N}`);
      kills += n; await t.sleep(1800);
    }
    const open = i < SZ.length - 1 ? !(await blk(x0 + s + 1, -58, 0, 'minecraft:iron_bars')) : null;
    t.log(`phòng ${i}: hạ ~${kills} | cửa ra mở: ${open} | tin:`, msgs.filter(m => /Đợt|sạch|VƯỢT|Vượt|Nhặt/.test(m)).slice(-3).join(' / '));
  }
  await t.sleep(2000);
  t.log('## tin rơi đồ:', msgs.filter(m => /Nhặt được/.test(m)).join(' / ') || '(không rơi)');
  t.log('## vượt:', msgs.filter(m => /Vượt|lần đầu|Đã mở/.test(m)).join(' / '));
  t.log('## túi:', t.bot.inventory.items().map(i => `${t.itemText(i).name}×${i.count}`).join(', '));
  t.bot.chat('/hamnguc thoat'); await t.sleep(3000);
  t.log('## sau thoát:', await dim(), await pos());
  await R(`effect clear ${N}`); await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
