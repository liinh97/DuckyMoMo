// s19 lọc chat: A gửi, B xem nhận được gì. Lặp 1 câu 6 lần, chữ IN HOA, từ tục, quảng cáo tên miền / IP / "chấm net",
// và câu bình thường có số (1.000.000 xu), tên miền server mình (goc6.io.vn) không được chặn nhầm.
const { start } = require('./h');
const A = 'ThuChatA', B = 'ThuChatB';
(async () => {
  const a = await start(A), b = await start(B);
  await a.sleep(2000);
  // gói chat thô: plainMessage = câu gốc đã ký; unsignedChatContent = bản server đã sửa (client thật HIỆN bản này nếu có)
  const raw = [];
  b.bot._client.on('player_chat', p => raw.push({ goc: p.plainMessage, sua: p.unsignedChatContent ? b.strip(JSON.stringify(p.unsignedChatContent)).replace(/"type":"[a-z]+",|"value":|[{}\[\]"]|text:/g, '') : null }));
  if (process.argv.includes('tho')) {
    for (const msg of ['MOI NGUOI OI VAO DAY XEM CAI NAY HAY LAM', 'thang nay dm ngu qua', 'web server o goc6.io.vn nhe ban']) { a.bot.chat(msg); await a.sleep(3500); }
    for (const r of raw) a.log('s19 gói chat: gốc =', JSON.stringify(r.goc), '| bản server sửa =', JSON.stringify(r.sua));
    await b.end(); await a.end(); process.exit(0);
  }
  const send = async (msg, wait = 1500) => { const n = b.chat.length, m = a.chat.length; a.bot.chat(msg); await a.sleep(wait); return { b: b.chat.slice(n).filter(l => l.includes(A) || l.includes(msg.slice(0, 8))).join(' | '), a: a.chat.slice(m).filter(l => !l.includes(A + ':')).join(' | ') }; };
  const show = (label, r) => a.log(`${label}\n    B thấy: ${r.b.slice(0, 200) || '(không)'}\n    A nhận: ${r.a.slice(0, 200) || '(không)'}`);
  let seen = 0;
  for (let i = 0; i < 6; i++) { const r = await send('chao moi nguoi minh moi vao', 400); if (r.b) seen++; }
  await a.sleep(2000); a.log('s19 lặp 6 lần cùng câu -> B thấy', seen, 'lần');
  await a.sleep(3500);
  const cases = [
    ['IN HOA', 'MOI NGUOI OI VAO DAY XEM CAI NAY HAY LAM'],
    ['từ tục', 'thang nay dm ngu qua'],
    ['tên miền lạ', 'vao play.abcxyz.net choi vui hon'],
    ['tên miền viết cách', 'vao play abcxyz chấm net nhe'],
    ['IP', 'ip server 123.45.67.89:25565 nha'],
    ['tên miền mình', 'web server o goc6.io.vn nhe ban'],
    ['số tiền có chấm', 'ban kiem 1.000.000 xu chua'],
    ['link shop', 'mua o shopee.vn re lam'],
  ];
  for (const [label, msg] of cases) { show(`s19 ${label}: "${msg}"`, await send(msg)); await a.sleep(3500); }
  await b.end(); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
