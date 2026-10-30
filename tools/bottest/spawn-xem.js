// Xem điểm spawn: vị trí + hướng nhìn của người mới vào (bot vừa reset), điểm spawn thế giới.
const h = require('./h');
const N = process.env.BOTN || 'SpawnXem';
(async () => {
  const t = await h.start(N); await t.sleep(2000);
  const e = t.bot.entity;
  t.log('## vị trí', e.position.toString(), 'yaw', (e.yaw * 180 / Math.PI).toFixed(1), 'spawnPoint', String(t.bot.spawnPoint));
  await t.end(); process.exit(0);
})();
