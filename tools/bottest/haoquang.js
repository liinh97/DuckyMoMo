// Hào quang ngọc khảm: bot B đứng cạnh A đếm hạt nhận được (A mặc giày + cầm kiếm có ngọc), B tắt "hat" (/caidat) thì không thấy;
// tháo ngọc tới khi vỡ: B thấy bùng hạt + kiếm của A vẫn nguyên (còn là đồ set, ô trống lại).
const { start, moveTo } = require('./h');
const A = 'ThuHaoA', B = 'ThuHaoB';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  let a = await start(A); let R = x => a.rc.cmd(x);
  await R(`minecraft:clear ${A}`); await R(`gamemode survival ${A}`); await R(`eco set ${A} 50000`); await R(`lp user ${B} meta unset dmmcaidat.hat`);
  await R(`dmm laydo ht_giay_0 1 ${A}`); await R(`dmm laydo ht_kiem_0 1 ${A}`); await R(`tinhluyen give netherite 2 ${A}`); await R(`tinhluyen give vang 1 ${A}`); await a.sleep(800);
  const kham = async (re, gems) => { await a.bot.equip(a.bot.inventory.items().find(i => re.test(i.name)), 'hand'); await a.sleep(300);
    let w = await a.openMenu('/thoren kham'); for (const g of gems) { w = await a.click(find(a, w, /Ô trống/)); w = await a.click(find(a, w, g)); a.log('khảm:', a.chat.slice(-1)[0]); }
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow); await a.sleep(300); };
  await kham(/boots/, [/^Netherite$/]); await kham(/sword/, [/^Netherite$/, /^Vàng$/]);
  await a.bot.equip(a.bot.inventory.items().find(i => /boots/.test(i.name)), 'feet'); await a.sleep(400);
  a = await moveTo(a, 12280, -2236); R = x => a.rc.cmd(x); await a.sleep(1500);
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(500);
  const count = async (b, ms) => { let n = 0; const h = p => { n += p.amount || p.particles || 1; }; b.bot._client.on('world_particles', h); await b.sleep(ms); b.bot._client.removeListener('world_particles', h); return n; };
  let b = await start(B); b = await moveTo(b, 12280, -2236); await b.sleep(1500);
  b.log('B thấy (hat bật, 5 giây):', await count(b, 5000), 'hạt');
  await b.rc.cmd(`lp user ${B} meta set dmmcaidat.hat tat`); await b.end(); await b.sleep(1500);
  b = await start(B); await b.sleep(1500);
  b.log('B thấy (hat tắt, 5 giây):', await count(b, 5000), 'hạt');
  await b.rc.cmd(`lp user ${B} meta unset dmmcaidat.hat`); await b.end(); await b.sleep(1500);
  b = await start(B); await b.sleep(1500);
  // tháo ngọc kiếm tới khi vỡ, B đếm hạt trong lúc đó
  const sounds = []; b.bot._client.on('sound_effect', s => sounds.push(s.soundId ?? s.sound ?? JSON.stringify(s).slice(0, 60)));
  let burst = 0; const hb = p => { if ((p.amount || 0) >= 15) burst++; }; b.bot._client.on('world_particles', hb);
  let vo = false;
  for (let k = 0; k < 8 && !vo; k++) {
    let w = await a.openMenu('/thoren kham'); const i = find(a, w, /◆/); if (i < 0) break;
    w = await a.click(i); w = await a.click(find(a, w, /Tháo ngọc/)); const m = a.chat.slice(-1)[0]; vo = /vỡ/.test(m);
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow); await a.sleep(500);
  }
  await b.sleep(800);
  b.log('vỡ ngọc:', vo, '| B nhận gói hạt lớn (≥15):', burst, '| âm thanh B nghe:', sounds.length);
  a.log('kiếm A sau khi vỡ:', a.itemText(a.bot.heldItem).name, '|', a.itemText(a.bot.heldItem).lore.filter(l => /KHẢM|◆|◇|Sắc|sát thương/i.test(l)).slice(0, 4).join(' / '));
  await R(`minecraft:clear ${A}`); await R(`eco set ${A} 0`);
  await b.end(); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
