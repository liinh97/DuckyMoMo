// Rank ủng hộ hết hạn (22/10): cấp Bá Tước tạm 100 giây (LuckPerms parent addtemp, như /rank give nhưng ngắn), dùng nick màu, /hat,
// hạt /pp, mở balo, đo chờ /spawn; đứng ONLINE chờ hết hạn + 40 giây rồi kiểm lại: rank, nick, mũ, hạt, số balo, chờ dịch chuyển.
const h = require('./h');
const N = process.env.BOTN || 'ThuR1';
const G = process.env.RANK || 'batuoc';
(async () => {
  let t = await h.start(N); const R = c => t.rc.cmd(c); await t.sleep(2500);
  const nick = async () => ((await R(`whois ${N}`)).match(/Biệt danh[^\n]*|Nick[^\n]*/i) || ['(không)'])[0].trim();
  const head = async () => (await R(`data get entity ${N} equipment.head.id`)).replace(/.*data: /, '');
  const say = async (c, ms = 1500) => (await t.say(c, ms)).map(x => x.trim()).filter(Boolean);
  const balo = async () => { const w = await t.openMenu('/balo'); if (!w) return '(không mở)'; const s = w.title ? t.strip(JSON.stringify(w.title)).slice(0, 80) : ''; const n = w.slots.slice(0, w.inventoryStart).filter(Boolean).length; t.bot.closeWindow(w); return `${s} | ${n} ô có món/nút`; };
  const tpWait = async () => { await R(`forceload add 12700 -2400`); await R(`execute positioned 12700 0 -2400 positioned over motion_blocking_no_leaves run minecraft:tp ${N} ~ ~ ~`); await t.sleep(2500); const a = await R(`data get entity ${N} Pos`); const n0 = t.chat.length; const t0 = Date.now(); t.bot.chat('/spawn'); for (let i = 0; i < 40; i++) { await t.sleep(250); if ((await R(`data get entity ${N} Pos`)) !== a) return `${((Date.now() - t0) / 1000).toFixed(1)} giây | ${t.chat.slice(n0).join(' / ').slice(0, 140)}`; } return 'không dịch chuyển | ' + t.chat.slice(n0).join(' / ').slice(0, 140); };
  const tab = () => { const p = t.bot.players[N]; return p && p.displayName ? t.strip(p.displayName.toString()) : '?'; };
  const check = async label => {
    t.log(`--- ${label}`);
    t.log('/rank:', (await say('/rank')).filter(x => /Rank của bạn|hết hạn|chưa có|Bạn đang/i.test(x)).join(' / ') || '(không thấy dòng rank của mình)');
    t.log('nick:', await nick(), '| đầu:', await head(), '| tab:', tab());
    t.log('balo:', await balo());
    t.log('chờ /spawn:', await tpWait());
  };
  await check('trước khi có rank');
  await R(`lp user ${N} parent addtemp ${G} 100s`); await t.sleep(3000);
  t.bot.chat('/nick &cBaThu'); await t.sleep(1000);
  await R(`minecraft:clear ${N}`); await R(`minecraft:give ${N} minecraft:pumpkin`); await t.sleep(800); await t.bot.equip(t.bot.inventory.items()[0], 'hand'); await t.sleep(300);
  t.bot.chat('/hat'); await t.sleep(1000);
  t.log('/pp add:', (await say('/pp add flame normal')).join(' / ').slice(0, 120));
  await check('đang có rank ' + G);
  if (process.env.OFFLINE) { await t.end(); t.log('thoát game, chờ hết hạn lúc offline'); await t.sleep(120000); t = await h.start(N); await t.sleep(8000); }
  const n0 = process.env.OFFLINE ? 0 : t.chat.length; if (!process.env.OFFLINE) await t.sleep(130000);
  await check('hết hạn ~40 giây');
  t.log('báo hết hạn:', t.chat.slice(n0).filter(x => /hết hạn|hết|trở lại/i.test(x)).join(' / '));
  t.log('bí ngô trong túi:', (await R(`minecraft:clear ${N} minecraft:pumpkin 0`)).replace(/.*Found /, ''));
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
