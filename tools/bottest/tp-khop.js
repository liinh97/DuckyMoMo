// Bot có tự theo lệnh tp phía server không (không vào lại): so toạ độ client với server ngay sau tp và sau 15 giây.
const { start } = require('./h');
const N = 'ThuTpKhop';
(async () => {
  const t = await start(N);
  const pos = async () => (await t.rc.cmd(`data get entity ${N} Pos`)).replace(/.*data: /, '');
  await t.sleep(2000);
  console.log('đầu: client', t.bot.entity.position.floored().toString(), '| server', await pos());
  await t.rc.cmd(`execute in minecraft:overworld run minecraft:tp ${N} 12700.5 85 -2440.5`);
  await t.sleep(2500);
  console.log('sau tp: client', t.bot.entity.position.floored().toString(), '| server', await pos());
  await t.sleep(15000);
  console.log('sau 15 giây: client', t.bot.entity.position.floored().toString(), '| server', await pos());
  await t.end(); process.exit(0);
})();
