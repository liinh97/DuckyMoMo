// h1 vào Liên minh Hoà Bình (luật 21/10): lập thị trấn cách mép đất kinh đô 6 ô (chunk x 818, kinh đô tới 812, z -158),
// /hoabinh vao -> "chờ nối đất"; chiếm 5 ô nối sang (817..813) -> nối liền -> hoà bình có hiệu lực sau join-wait-days
// (muốn thấy ngay: tạm đặt join-wait-days 0 + /dmm reload trước khi chạy, xong trả lại); rồi /hoabinh roi, vào lại bị chặn.
// Xong: xoá thị trấn ThuTranH (townyadmin town ThuTranH delete + confirm cùng một phiên RCON).
const { start, moveTo } = require('./h');
const H = 'ThuH';
const Z = -158 * 16 + 8;
const cx = x => x * 16 + 8;
(async () => {
  let h = await start(H);
  const R = c => h.rc.cmd(c);
  await R(`hoatdong them ${H} 240`); await R(`eco set ${H} 40000`); await R(`gamemode survival ${H}`);
  await R(`effect give ${H} minecraft:water_breathing 1800 0 true`); await R(`effect give ${H} minecraft:resistance 1800 4 true`);
  h = await moveTo(h, cx(818), Z);
  h.log('h1 /t new (cách 6 ô):', (await h.say('/t new ThuTranH', 4000)).join(' | ').slice(0, 300));
  h.log('h1 /confirm:', (await h.say('/confirm', 3000)).join(' | ').slice(0, 300));
  h.log('h1 /hoabinh vao:', (await h.say('/hoabinh vao', 2500)).join(' | ').slice(0, 500));
  h.log('h1 /t deposit 2000:', (await h.say('/t deposit 2000', 2000)).join(' | ').slice(0, 200));
  await h.sleep(2000);
  h.log('h1 danhsach:', (await R('hoabinh danhsach')).replace(/\n/g, ' / '));
  h.log('h1 /hoabinh:', (await h.say('/hoabinh', 1500)).slice(-1).join(''));
  for (let x = 817; x >= 813; x--) {
    h = await moveTo(h, cx(x), Z);
    const cl = (await h.say('/t claim', 2500)).join(' | ');
    h.log(`h1 /t claim ô ${x}:`, cl.slice(0, 200), /Xác nhận|confirm/i.test(cl) ? '| ' + (await h.say('/confirm', 2500)).join(' | ').slice(0, 200) : '');
  }
  h.log('h1 sau khi nối:', (await h.say('/hoabinh', 1500)).slice(-1).join(''));
  await h.sleep(70000);   // tick mỗi 60 giây
  h.log('h1 sau 70 giây:', (await R('hoabinh danhsach')).replace(/\n/g, ' / '));
  h.log('h1 /hoabinh roi:', (await h.say('/hoabinh roi', 2500)).join(' | ').slice(0, 300));
  await h.sleep(2000);
  h.log('h1 /hoabinh vao lại:', (await h.say('/hoabinh vao', 2500)).join(' | ').slice(0, 300));
  h.log('h1 danhsach cuối:', (await R('hoabinh danhsach')).replace(/\n/g, ' / '));
  await h.end();
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
