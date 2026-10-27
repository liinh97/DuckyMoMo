// Chương 1 xong: chạy hành động c1_huongdan (chỉ việc tiếp theo) cho bot, in tin nhận được.
const h = require('./h');
const N = process.env.BOTN || 'ThuGl';
(async () => {
  const t = await h.start(N);
  const n = t.chat.length;
  t.log('bq:', await t.rc.cmd(`bq action ${N} cotruyen>c1_huongdan`));
  await t.sleep(2500);
  t.log(t.since(n).join('\n'));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
