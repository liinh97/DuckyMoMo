// t1: lập thị trấn khi ĐỦ giờ (Cài đặt town_min_hours 3, giá towny_price_new_town 1.500): ThuR1 được cộng 200 phút chơi thật (/hoatdong them),
// 3.000 xu, đứng ở đất hoang, /t new ThuTranR1 -> lập được, trừ 1.500. Xong xoá thị trấn (dọn bằng panel app.rcon + confirm).
// m13: ThuR2 có quyền tạm essentials.tphere (2 phút) gõ /tphere ThuR3 -> ThuR3 tới chỗ ThuR2.
const h = require('./h');
(async () => {
  let a = await h.start('ThuR1'); const R = c => a.rc.cmd(c);
  a = await h.moveTo(a, 13400, -3300);
  await R('hoatdong them ThuR1 200'); await R('eco set ThuR1 3000'); await a.sleep(1500);
  a.log('t1 /t new:', (await a.say('/t new ThuTranR1', 3000)).join(' / ').slice(0, 200));
  a.log('t1 /confirm:', (await a.say('/confirm', 3000)).join(' / ').slice(0, 300));
  a.log('t1 số dư sau:', await R('bal ThuR1'));
  a.log('t1 /t ThuTranR1:', (await R('ta town ThuTranR1')).replace(/\n/g, ' ').slice(0, 200));
  await a.end();
  let b = await h.start('ThuR2'), c = await h.start('ThuR3');
  b = await h.moveTo(b, 13200, -2900);
  await b.rc.cmd('lp user ThuR2 permission settemp essentials.tphere true 3m'); await b.sleep(8000);
  const P = async n => (await b.rc.cmd(`data get entity ${n} Pos`)).replace(/.*data: /, '').replace(/d/g, '');
  b.log('m13 trước: ThuR2', await P('ThuR2'), '| ThuR3', await P('ThuR3'));
  b.log('m13 /tphere:', (await b.say('/tphere ThuR3', 2500)).join(' / ').slice(0, 200));
  b.log('m13 sau: ThuR3', await P('ThuR3'));
  await b.rc.cmd('lp user ThuR2 permission unset essentials.tphere');
  await b.end(); await c.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
