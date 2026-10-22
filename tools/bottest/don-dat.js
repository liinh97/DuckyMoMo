// Bot ThuCA đặt đơn mua 10 đá cuội giá 3 ở kinh đô (dùng để test đơn hết hạn: lùi mốc "tao" lúc server tắt).
const { start } = require('./h');
(async () => {
  const a = await start('ThuCA'); const R = x => a.rc.cmd(x);
  await R('minecraft:tp ThuCA 12699 80 -2523'); await R('eco set ThuCA 1000'); await R('minecraft:clear ThuCA');
  await R('minecraft:item replace entity ThuCA weapon.mainhand with minecraft:cobblestone 1'); await a.sleep(1500);
  a.log((await a.say('/choden dat 10 3', 2000)).filter(x => /Đơn|đơn|xu/.test(x)).join(' / ').slice(0, 300));
  a.log('bal:', await R('bal ThuCA'));
  await R('minecraft:clear ThuCA'); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
