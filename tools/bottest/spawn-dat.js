// Đo mặt đất phía đông điểm spawn (z = -2433): khối rắn cao nhất y 80..100 theo từng x.
const h = require('./h');
const N = process.env.BOTN || 'SpawnXem';
(async () => {
  const t = await h.start(N); await t.sleep(3000);
  const { Vec3 } = require('vec3'); const out = [];
  for (let x = 12706; x <= 12730; x++) {
    let top = null;
    for (let y = 100; y >= 80; y--) { const b = t.bot.blockAt(new Vec3(x, y, -2433)); if (b && b.boundingBox === 'block') { top = y + ':' + b.name; break; } }
    out.push(x + '→' + top);
  }
  t.log('## ' + out.join(' | '));
  await t.end(); process.exit(0);
})();
