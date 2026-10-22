// Mở các menu thông tin (thay hướng dẫn qua chat, 22/10) bằng bot, in tiêu đề + tên / mô tả từng ô (bỏ ô kính khung).
const h = require('./h');
const CMDS = (process.env.CMDS || '/tochuc,/hoidong,/linhdanhthue,/hoabinh,/bantron,/canbang,/kholuong,/khobau,/bxhpet').split(',');
(async () => {
  const t = await h.start('ThuMenu'); await t.sleep(2000);
  for (const c of CMDS) {
    const w = await t.openMenu(c);
    if (!w) { t.log(`## ${c}: KHÔNG MỞ MENU | chat: ${t.chat.slice(-3).join(' / ').slice(0, 200)}`); continue; }
    const title = t.strip(JSON.stringify(w.title)).match(/"(?:text|value)":"([^"]+)"/g);
    t.log(`## ${c}: ${w.slots.slice(0, w.inventoryStart).length} ô | ${(title || []).join('').slice(0, 80)}`);
    w.slots.slice(0, w.inventoryStart).forEach((it, i) => {
      if (!it || /pane$/.test(it.name)) return;
      const x = t.itemText(it);
      t.log(`  [${i}] ${it.name} | ${x.name} | ${x.lore.join(' ¦ ').slice(0, 160)}`);
    });
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); await t.sleep(600);
  }
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
