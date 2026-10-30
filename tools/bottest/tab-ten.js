// In tên hiện trong bảng TAB (có danh phận?) của vài người chơi.
const h = require('./h');
const N = process.env.BOTN || 'TabXem';
(async () => {
  const t = await h.start(N); await t.sleep(3000);
  for (const [k, p] of Object.entries(t.bot.players).slice(0, 6)) t.log('##', k, '=>', p.displayName ? p.displayName.toString() : '(không)');
  await t.end(); process.exit(0);
})();
