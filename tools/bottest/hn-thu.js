// Hầm Ngục DuckyMoMo (29/10): bot vào Hầm Mộ Cổ, kiểm dựng (biển, lồng, cửa), quái ra từ lồng theo đợt, hạ bằng /damage by bot -> cửa mở, rơi đồ, vượt.
const h = require('./h');
const N = process.env.BOTN || 'ThuHamNguc';
const DG = process.env.DG || 'hammo';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  const W = 'minecraft:hamnguc';
  await R(`minecraft:clear ${N}`); await R(`effect give ${N} minecraft:resistance 900 4 true`);
  await R(`execute in minecraft:overworld run minecraft:tp ${N} 12699 80 -2523`); await t.sleep(3000);
  t.bot.chat(`/hamnguc di ${DG}`);
  const dim = async () => (await R(`data get entity ${N} Dimension`)).replace(/.*data: /, '').replace(/"/g, '');
  for (let i = 0; i < 20 && !/hamnguc/.test(await dim()); i++) await t.sleep(1000);
  t.log('## vào:', await dim(), '|', msgs.filter(m => /Hầm|dựng|⚔/.test(m)).slice(-2).join(' / '));
  const pos = async () => (await R(`data get entity ${N} Pos`)).replace(/.*data: /, '');
  t.log('vị trí', await pos());
  const blk = async (x, y, z, b) => /passed/.test(await R(`execute in ${W} if block ${x} ${y} ${z} ${b}`));
  // ô 0: x0 phòng i = i*26; sàn y=-60; lồng phòng 1 ở x 32/38, z ±7, y -59
  t.log('biển phòng đầu:', await blk(1, -57, 0, '#minecraft:wall_signs'), '| sàn:', (await R(`execute in ${W} run data get block 5 -60 0`)).slice(0, 60));
  t.log('lồng phòng 1:', await blk(32, -59, -7, 'minecraft:spawner'), await blk(38, -59, 7, 'minecraft:spawner'),
        '| cửa ra phòng 1 đóng:', await blk(44, -58, 0, 'minecraft:iron_bars'));
  const rooms = 5;
  for (let i = 1; i < rooms; i++) {
    const x0 = i * 26;
    await R(`execute in ${W} run minecraft:tp ${N} ${x0 + 4} -59 0`); await t.sleep(2500);
    let kills = 0;
    for (let k = 0; k < 40; k++) {
      const c = await R(`execute in ${W} if entity @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,x=${x0},y=-62,z=-10,dx=18,dy=16,dz=20]`);
      const n = +(c.match(/Count: (\d+)/) || [0, 0])[1];
      if (n === 0) { await t.sleep(1500); const c2 = await R(`execute in ${W} if entity @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,x=${x0},y=-62,z=-10,dx=18,dy=16,dz=20]`); if (!/Count: [1-9]/.test(c2)) break; }
      if (k === 0) t.log(`phòng ${i}: ${n} quái đang có`, (await R(`execute in ${W} run data get entity @e[type=!minecraft:player,type=!minecraft:item,x=${x0},y=-62,z=-10,dx=18,dy=16,dz=20,limit=1] CustomName`)).replace(/.*data: /, '').slice(0, 90));
      await R(`execute in ${W} as @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,x=${x0},y=-62,z=-10,dx=18,dy=16,dz=20] run damage @s 5000 minecraft:player_attack by ${N}`);
      kills += n; await t.sleep(1800);
    }
    const open = i < rooms - 1 ? !(await blk(x0 + 18, -58, 0, 'minecraft:iron_bars')) : null;
    t.log(`phòng ${i}: hạ ~${kills} lượt | cửa ra mở: ${open} | tin:`, msgs.filter(m => /Đợt|sạch|VƯỢT|Vượt|Nhặt/.test(m)).slice(-3).join(' / '));
  }
  await t.sleep(2000);
  t.log('## tin rơi đồ:', msgs.filter(m => /Nhặt được/.test(m)).join(' / ') || '(không rơi)');
  t.log('## vượt:', msgs.filter(m => /Vượt|lần đầu|Đã mở/.test(m)).join(' / '));
  t.log('## túi:', t.bot.inventory.items().map(i => `${t.itemText(i).name}×${i.count}`).join(', '));
  t.bot.chat('/hamnguc thoat'); await t.sleep(3000);
  t.log('## sau thoát:', await dim(), await pos());
  await R(`effect clear ${N}`); await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
