// x4 trang tài khoản + s20 đoán mã + s21 giả tên + s22 trang quản trị không lộ. Bot lấy mã /web trong game rồi gọi thẳng site:8080
// (cùng mạng docker). Tên thử: ThuWeb (đoán sai mã khoá tên này 15 phút) và ThuWebSai (dùng cho đoán mã).
const { start } = require('./h');
const N = 'ThuWeb';
const SITE = 'http://site:8080';
const post = (p, body, cookie) => fetch(SITE + p, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) }, body: JSON.stringify(body) });
(async () => {
  const t = await start(N);
  const R = c => t.rc.cmd(c);
  await R(`eco set ${N} 1234`);
  const out = await t.say('/web', 2000);
  const code = (out.join(' ').match(/\b(\d{3}\s?\d{3})\b/) || [])[1];
  t.log('/web:', out.join(' | ').slice(0, 200), '| mã', code ? 'có' : 'KHÔNG THẤY');
  // x4: đăng nhập
  let r = await post('/api/dangnhap', { ten: N, ma: code });
  const ck = (r.headers.get('set-cookie') || '').split(';')[0];
  t.log('x4 đăng nhập:', r.status, JSON.stringify(await r.json()).slice(0, 120), '| cookie', ck ? 'có' : 'không');
  r = await fetch(SITE + '/api/toi', { headers: { Cookie: ck } });
  const me = await r.json();
  t.log('x4 /api/toi khoá:', Object.keys(me).join(','));
  t.log('x4 khoá hoso:', Object.keys(me.hoso || {}).join(','));
  t.log('x4 caidat:', JSON.stringify(me.caidat).slice(0, 600));
  // đổi một cài đặt cá nhân trên web rồi xem trong game (/caidat)
  const cd = Array.isArray(me.caidat) ? me.caidat[0] : (me.caidat && (me.caidat.muc || me.caidat.list || [])[0]);
  if (cd) {
    const ma = cd.ma || cd.id || cd.key, bat = !(cd.bat ?? cd.on ?? cd.gia_tri);
    let w = await t.openMenu('/caidat'); const before = w ? w.slots.slice(0, w.inventoryStart).filter(Boolean).map(i => t.itemText(i).name + ':' + t.itemText(i).lore.slice(-1)).join(' / ').slice(0, 300) : '(không mở)'; if (w) t.bot.closeWindow(w);
    r = await post('/api/caidat', { ma, bat }, ck);
    t.log('x4 đổi', ma, '->', bat, ':', r.status, JSON.stringify(await r.json()).slice(0, 150));
    await t.sleep(1500);
    w = await t.openMenu('/caidat'); const after = w ? w.slots.slice(0, w.inventoryStart).filter(Boolean).map(i => t.itemText(i).name + ':' + t.itemText(i).lore.slice(-1)).join(' / ').slice(0, 300) : '(không mở)'; if (w) t.bot.closeWindow(w);
    t.log('x4 /caidat trước:', before); t.log('x4 /caidat sau  :', after);
    r = await post('/api/caidat', { ma, bat: !bat }, ck); t.log('x4 trả lại:', r.status);
  }
  // dùng lại mã đã dùng
  r = await post('/api/dangnhap', { ten: N, ma: code });
  t.log('x4 dùng lại mã:', r.status, JSON.stringify(await r.json()).slice(0, 150));
  // s21: giả tên trong yêu cầu đổi cài đặt / chơi lại
  r = await post('/api/caidat', { ten: 'ThuK', ma: 'khong_ton_tai', bat: true }, ck);
  t.log('s21 caidat gửi ten=ThuK:', r.status, JSON.stringify(await r.json()).slice(0, 200));
  r = await post('/api/caidat', { ten: 'ThuK' });
  t.log('s21 caidat không cookie:', r.status, JSON.stringify(await r.json()).slice(0, 120));
  r = await post('/api/choilai', { ten: 'ThuK' }, 'dmm_phien=gia-mao-123');
  t.log('s21 choilai cookie giả:', r.status, JSON.stringify(await r.json()).slice(0, 120));
  // s22: trang quản trị
  for (const p of ['/quantri', '/quantri/', '/quantri/api/caidat', '/quantri/api/config', '/api/web/hoso?ten=ThuK']) {
    r = await fetch(SITE + p, { redirect: 'manual' });
    const txt = (await r.text()).replace(/\s+/g, ' ');
    t.log('s22', p, r.status, r.headers.get('location') || '', txt.slice(0, 100));
  }
  // s20: đoán mã cho ThuWebSai (6 lần sai)
  for (let i = 1; i <= 7; i++) {
    r = await post('/api/dangnhap', { ten: 'ThuWebSai', ma: String(100000 + i) });
    t.log(`s20 lần ${i}:`, r.status, (await r.json()).message.slice(0, 90));
  }
  // đăng xuất
  r = await post('/api/dangxuat', {}, ck); t.log('đăng xuất:', r.status);
  r = await fetch(SITE + '/api/toi', { headers: { Cookie: ck } }); t.log('sau đăng xuất /api/toi:', JSON.stringify(await r.json()).slice(0, 80));
  await t.end();
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
