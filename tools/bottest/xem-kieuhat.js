// Đứng cạnh nhóm Pham_ đếm hạt nhận được theo kiểu trong 4 giây.
const { start } = require('./h');
(async () => {
  let b = await start('ThuHaoB');
  await b.rc.cmd('execute in minecraft:overworld run minecraft:tp ThuHaoB 12693.5 85 -2429.5'); await b.end(); await b.sleep(2500);
  b = await start('ThuHaoB'); await b.sleep(1500);
  const c = {}; const h = p => { const t = p.particle && p.particle.type; if (t) c[t] = (c[t] || 0) + (p.amount ?? 1); };
  b.bot._client.on('world_particles', h); await b.sleep(4000); b.bot._client.removeListener('world_particles', h);
  console.log('hạt theo kiểu (4 giây):', JSON.stringify(c));
  await b.end(); process.exit(0);
})();
