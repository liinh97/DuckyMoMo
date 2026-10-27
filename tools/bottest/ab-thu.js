// Đọc thanh chỉ số (action bar) của bot: chữ + JSON thô (xem màu có thật hay chỉ là chữ mã). BOTN=ThuVL ./tools/bottest/run.sh ab-thu.js
const h = require('./h');
const N = process.env.BOTN || 'ThuVL';
(async () => {
  const t = await h.start(N);
  const raw = [];
  for (const ev of ['system_chat', 'action_bar', 'set_action_bar_text']) t.bot._client.on(ev, p => { if (ev !== 'system_chat' || p.isActionBar || p.overlay) raw.push(p); });
  await t.sleep(4000);
  t.log('## action bar:', JSON.stringify(raw.slice(-1)).slice(0, 1500));
  const me = t.bot.players[N]; t.log("## Tab:", me && me.displayName ? JSON.stringify(me.displayName.json ?? String(me.displayName)).slice(0, 300) : "-");
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
