// q9 Lữ khách: bot qua Via chuột phải NPC Citizens không mở được hội thoại, nên wrapper (scratchpad q9.sh) thêm tạm action
// "lk_thu_<mã>: conversation lk_<mã>" và mở bằng /bq action (IO simple: chọn bằng số trong chat). Args: <mã lữ khách> <lần: picks,...>
// Mỗi lần: mở hội thoại, gõ lần lượt các số đã cho, in lời thoại + tiền + tag.
const { start } = require('./h');
const code = process.argv[2] || 'hoinuoc';
const rounds = process.argv.slice(3).map(s => s.split(',').filter(Boolean));
(async () => {
  const A = 'ThuQ7';
  const t = await start(A, '10.9.0.71');
  const R = c => t.rc.cmd(c);
  const bal = async () => (await R(`bal ${A}`)).replace(/.*:\s*/, '').trim();
  await t.sleep(3000);
  for (const [i, picks] of rounds.entries()) {
    const n0 = t.chat.length, b0 = await bal();
    t.log(`  mở: ${(await R(`bq action ${A} cotruyen>lk_thu_${code}`)).trim().slice(0, 120)}`);
    await t.sleep(2000);
    for (const k of picks) { t.bot.chat(k); await t.sleep(2000); }
    await t.sleep(1000);
    t.log(`q9 lần ${i + 1} (chọn ${picks.join(',') || '-'}): ${t.chat.slice(n0).join(' | ').replace(/\s+/g, ' ').slice(0, 900)} | tiền ${b0} -> ${await bal()}`);
    t.log('  tag:', (await R(`bq tag ${A} list`)).replace(/\s+/g, ' ').slice(0, 300));
  }
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
