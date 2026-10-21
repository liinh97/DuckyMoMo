// p3 chọn hệ pet: tạo pet Sói, mở /petchooseskilltree, in mọi ô (tên + dòng mô tả đầu) để xem có bị đúp; chọn 1 hệ, lên cấp 15 xem tin.
const { start } = require('./h');
const N = 'ThuP3';
(async () => {
  const t = await start(N);
  const R = c => t.rc.cmd(c);
  const info = async () => (await t.say('/petinfo', 1500)).join(' ').replace(/\s+/g, ' ').slice(0, 200);
  let i0 = await info(); const nm = (i0.match(/^\s*([^:]+):/) || [])[1];
  if (nm && !/không có/.test(i0)) await t.say('/petrelease ' + nm.trim(), 1500);
  t.log('tạo pet:', await R(`petadmin create ${N} Wolf`)); await t.sleep(2500);
  const w = await t.openMenu('/petchooseskilltree');
  if (!w) { t.log('không mở được menu'); process.exit(1); }
  t.log('tiêu đề:', t.strip(JSON.stringify(w.title)).replace(/"type":"[a-z]+",|"value":|[{}\[\]"]/g, '').slice(0, 120), '| số ô:', w.inventoryStart);
  const items = w.slots.slice(0, w.inventoryStart).map((it, i) => it && { i, ten: t.itemText(it).name, lore: t.itemText(it).lore.slice(0, 2).join(' / ') }).filter(Boolean);
  for (const x of items) t.log(`  ô ${x.i}: ${x.ten} | ${x.lore.slice(0, 120)}`);
  const names = items.map(x => x.ten); const dup = names.filter((n, k) => names.indexOf(n) !== k);
  t.log('p3 số hệ:', items.length, '| trùng:', dup.length ? [...new Set(dup)].join(', ') : 'không');
  // chọn ô đầu, lên cấp 15
  const n0 = t.chat.length;
  await t.click(items[0].i); await t.sleep(1000);
  if (t.bot.currentWindow) { const w2 = t.bot.currentWindow; const yes = w2.slots.slice(0, w2.inventoryStart).findIndex(it => it && /có|xác nhận|đồng ý|yes/i.test(t.itemText(it).name)); if (yes >= 0) await t.click(yes); if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); }
  t.log('p3 chọn hệ:', t.chat.slice(n0).join(' | ').slice(0, 300));
  const n1 = t.chat.length;
  for (const c of [`petadmin exp ${N} 2000 add`, `petadmin exp ${N} 2000`]) { t.log('lên cấp:', c, '->', await R(c)); await t.sleep(1500); if (/level (1[5-9]|[2-9]\d)/i.test(await R(`petadmin exp ${N} 0 add`))) break; }
  await t.sleep(2000);
  t.log('p3 tin khi lên cấp:', t.chat.slice(n1).join(' | ').slice(0, 600));
  t.log('p3 /petinfo:', await info());
  const sk = (await t.say('/petskill', 1500)).join(' | ');
  t.log('p3 /petskill:', sk.slice(0, 500));
  const i2 = await info(); const nm2 = (i2.match(/^\s*([^:]+):/) || [])[1]; if (nm2) await t.say('/petrelease ' + nm2.trim(), 1500);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
