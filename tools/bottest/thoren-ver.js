// Món set phát từ trước (không có dấu phiên bản thoren_ver) tự đổi theo Thư viện, giữ độ mòn (23/10).
const h = require('./h');
const N = process.env.BOTN || 'ThuVer';
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  await t.sleep(2000); await R(`minecraft:clear ${N}`);
  if (process.env.KIEU === 'laydo') { await R(`dmm laydo tt_mu_4 1 ${N}`); await t.sleep(800); await R(`item modify entity ${N} container.0 {function:"minecraft:set_damage",damage:0.8}`); }
  else await R(`minecraft:give ${N} minecraft:iron_helmet[minecraft:custom_data={PublicBukkitValues:{"duckymomocore:thuvien_id":"tt_mu_4"}},minecraft:damage=37,minecraft:custom_name='"mũ cũ"'] 1`);
  await t.sleep(1000);
  t.log('## trước:', (await R(`data get entity ${N} Inventory[0].components`)).replace(/.*data: /, '').slice(0, 220));
  for (let i = 0; i < 30; i++) { await t.sleep(5000); const s = await R(`data get entity ${N} Inventory[0].components."minecraft:custom_data".PublicBukkitValues`); if (/thoren_ver/.test(s)) break; }
  const pbv = await R(`data get entity ${N} Inventory[0].components."minecraft:custom_data".PublicBukkitValues`);
  const dmg = await R(`data get entity ${N} Inventory[0].components."minecraft:damage"`);
  const name = await R(`data get entity ${N} Inventory[0].components."minecraft:custom_name"`);
  const tip = await R(`data get entity ${N} Inventory[0].components."minecraft:tooltip_display"`);
  t.log('## sau: dấu phiên bản:', /thoren_ver/.test(pbv), '| độ mòn:', dmg.replace(/.*data: /, ''), '| tên:', name.replace(/.*data: /, '').slice(0, 80), '| ẩn chỉ số:', tip.replace(/.*data: /, '').slice(0, 80));
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
