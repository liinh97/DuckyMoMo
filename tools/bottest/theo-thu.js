// Bot chạy theo member (1/11): bot test đi bộ / chạy / nhảy / rẽ ở chỗ vắng, một bot botfarm bám theo, in khoảng cách từng 2 giây
const h = require('./h');
const N = process.env.BOTN || 'ThuTheo', F = 'Thu_Trang';
const act = (body) => fetch('http://botfarm:8090/act', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(r => r.json());
const bots = () => fetch('http://botfarm:8090/bots').then(r => r.json());
(async () => {
  let t = await h.start(N);
  t = await h.moveTo(t, 12707, -2433); await t.sleep(5000);   // quảng trường spawn: đất phẳng, có bậc thang
  const b = t.bot;
  b.physicsEnabled = true;
  console.log('bat dau', b.entity.position.toString(), JSON.stringify(await act({ bot: F, action: 'theo', text: N })));
  await t.sleep(5000);
  let min = 1e9, max = 0, sum = 0, k = 0, stop = false;
  const watch = (async () => {
    while (!stop) {
      await t.sleep(2000);
      const d = await bots(); const f = d.bots.find(x => x.name === F); const p = b.entity.position;
      const dist = Math.hypot(f.x - p.x, f.y - p.y, f.z - p.z);
      if (dist > max) max = dist; if (dist < min) min = dist; sum += dist; k++;
      console.log(`me ${p.x.toFixed(0)} ${p.y.toFixed(0)} ${p.z.toFixed(0)} | bot ${f.x} ${f.y} ${f.z} | cach ${dist.toFixed(1)} | theo=${f.follow}`);
    }
  })();
  const go = async (yaw, ms, sprint, jump) => {
    await b.look(yaw, 0, true); b.setControlState('forward', true); b.setControlState('sprint', !!sprint);
    const end = Date.now() + ms;
    while (Date.now() < end) { if (jump) { b.setControlState('jump', true); await t.sleep(300); b.setControlState('jump', false); await t.sleep(900); } else await t.sleep(200); }
    b.clearControlStates();
  };
  await go(-Math.PI / 2, 8000); await t.sleep(3000); await go(Math.PI / 2, 6000, true); await t.sleep(3000);
  await go(0, 5000, false, true); await t.sleep(3000); await go(Math.PI, 8000, true, true); await t.sleep(6000);
  stop = true; await watch;
  console.log(`KQ cach min ${min.toFixed(1)} max ${max.toFixed(1)} tb ${(sum / k).toFixed(1)} (${k} mau)`);
  console.log(JSON.stringify(await act({ bot: F, action: 'theo', text: '' })));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
