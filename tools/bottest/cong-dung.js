// Công thành giả, bước 1: dựng 3 thị trấn + 2 nước bằng bot (thị trưởng phải online đứng tại chỗ cho /ta town new).
//   ThuNuocA (tấn công, 2 thị trấn: ThuThanhA1 của ThuW1 = vua, ThuThanhA2 của ThuW3) · ThuNuocB (phòng thủ: ThuThanhB của ThuW2).
// Chỗ: đất hoang x 13400..13640, z -3150 (trong vùng đã khai hoang; x 14000 ngoài vùng bị đẩy về). Wrapper tạm hạ town-min-hours,
// nation-min-active-residents về 0 rồi trả lại. Dọn: node cong-dung.js xoa
const { start, moveTo } = require('./h');
const SPOTS = [['ThuW1', '10.9.1.1', 'ThuThanhA1', 13400, -3150], ['ThuW3', '10.9.1.3', 'ThuThanhA2', 13496, -3150], ['ThuW2', '10.9.1.2', 'ThuThanhB', 13640, -3150]];
(async () => {
  let t = await start('ThuW1', '10.9.1.1');
  const R = c => t.rc.cmd(c);
  if (process.argv[2] === 'xoa') {
    for (const n of ['ThuNuocA', 'ThuNuocB']) t.log('xoá nước', n, (await R(`ta nation ${n} delete`)).slice(0, 100));
    for (const [, , town] of SPOTS) t.log('xoá thị trấn', town, (await R(`ta town ${town} delete`)).slice(0, 100));
    await t.end(); process.exit(0);
  }
  await t.end();
  for (const [name, ip, town, x, z] of SPOTS) {
    let b = await start(name, ip); await b.sleep(4000);
    await b.rc.cmd(`gamemode survival ${name}`); await b.rc.cmd(`eco set ${name} 200000`);
    b = await moveTo(b, x, z, 'minecraft:overworld', ip);
    b.log(`lập ${town}:`, (await b.rc.cmd(`ta town new ${town} ${name}`)).replace(/\s+/g, ' ').slice(0, 200));
    await b.sleep(1500);
    b.log('  ', (await b.rc.cmd(`ta town ${town} deposit 100000`)).slice(0, 100));
    await b.end(); await b.sleep(1500);
  }
  t = await start('ThuW1', '10.9.1.1');
  t.log('nước A:', (await R(`ta nation new ThuNuocA ThuThanhA1`)).replace(/\s+/g, ' ').slice(0, 200));
  t.log('  thêm A2:', (await R(`ta nation ThuNuocA transfer ThuThanhA2`)).replace(/\s+/g, ' ').slice(0, 200));
  t.log('nước B:', (await R(`ta nation new ThuNuocB ThuThanhB`)).replace(/\s+/g, ' ').slice(0, 200));
  for (const n of ['ThuNuocA', 'ThuNuocB']) t.log('  quỹ', n, (await R(`ta nation ${n} deposit 300000`)).slice(0, 100));
  for (const n of ['ThuNuocA', 'ThuNuocB']) t.log('  ', n, (await R(`ta nation ${n}`)).replace(/\s+/g, ' ').slice(0, 400));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
