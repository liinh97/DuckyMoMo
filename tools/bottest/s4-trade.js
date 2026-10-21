// s4 nhân đồ qua /trade (TradePro): A đưa 10 kim cương, B đưa 10 vàng, cả hai bấm đồng ý (đếm ngược 5 giây chống lừa) rồi A rút mạng
// giữa đếm ngược / đúng lúc hết đếm ngược; đối chứng: để xong bình thường. Đếm lại đồ cả hai bên (tổng phải luôn 10 + 10).
const { start } = require('./h');
const A = 'ThuTA', B = 'ThuTB';
(async () => {
  let a = await start(A), b = await start(B);
  const R = c => b.rc.cmd(c);
  const cnt = async (n, item) => { const m = (await R(`minecraft:clear ${n} minecraft:${item} 0`)).match(/Found (\d+)/); return m ? +m[1] : 0; };
  const state = async () => `A: ${await cnt(A, 'diamond')} kc ${await cnt(A, 'gold_ingot')} vàng | B: ${await cnt(B, 'diamond')} kc ${await cnt(B, 'gold_ingot')} vàng`;
  const round = async (label, quitAt) => {
    for (const n of [A, B]) { await R(`minecraft:clear ${n}`); await R(`gamemode survival ${n}`); }
    await R(`minecraft:give ${A} minecraft:diamond 10`); await R(`minecraft:give ${B} minecraft:gold_ingot 10`);
    await R(`minecraft:tp ${A} ${B}`); await a.end(); await a.sleep(2000); a = await start(A); await a.sleep(1500);
    const wa = new Promise(r => { a.bot.once('windowOpen', r); setTimeout(() => r(null), 6000); });
    const wb = new Promise(r => { b.bot.once('windowOpen', r); setTimeout(() => r(null), 6000); });
    a.log(label, 'mời:', (await a.say(`/trade ${B}`, 1500)).join(' | ').slice(0, 160));
    b.log(label, 'nhận:', (await b.say(`/trade ${A}`, 1500)).join(' | ').slice(0, 160));
    const [winA, winB] = await Promise.all([wa, wb]);
    if (!winA || !winB) { a.log(label, 'không mở được menu trao đổi', !!winA, !!winB); return; }
    const put = async (t, w, item) => { const s = w.slots.findIndex((it, i) => i >= w.inventoryStart && it && it.name === item); await t.bot.clickWindow(s, 0, 1); await t.sleep(700); };
    await put(a, winA, 'diamond'); await put(b, winB, 'gold_ingot');
    a.log(label, 'ô của A trong menu B:', winB.slots.slice(0, winB.inventoryStart).filter(Boolean).map(i => i.name + 'x' + i.count).join(',').slice(0, 160));
    await a.bot.clickWindow(0, 0, 0); await b.bot.clickWindow(0, 0, 0);
    if (quitAt == null) { await a.sleep(8000); }
    else { await a.sleep(quitAt); a.bot._client.socket.destroy(); await a.sleep(5000); a = await start(A); await a.sleep(1500); }
    if (b.bot.currentWindow) b.bot.closeWindow(b.bot.currentWindow);
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
    await a.sleep(1000);
    a.log(label, '->', await state(), '| tin B:', b.chat.slice(-4).join(' | ').slice(0, 200));
  };
  await round('s4 xong bình thường', null);
  await round('s4 A rút mạng giữa đếm ngược (2s)', 2000);
  await round('s4 A rút mạng lúc hết đếm ngược (5s)', 5000);
  for (const n of [A, B]) await R(`minecraft:clear ${n}`);
  await b.end(); await a.end();
  process.exit(0);   // socket bot rút mạng còn treo tiến trình
})().catch(e => { console.log('FAIL', e); process.exit(1); });
