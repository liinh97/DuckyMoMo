// Bot trưng bày hào quang ở spawn cho chủ server xem (đứng ~30 phút, quay mặt nhìn người gần nhất):
//  ThuHaoQuang: bộ Huyền Thoại đủ 4 giáp + kiếm, mỗi món 3 ngọc (đủ 9 màu), năng lượng đầy.
//  ThuHaoMo: kiếm 3 ngọc đã rút còn ~10% năng lượng (hạ "trùm" tên 『N』, -10/con) để so độ mờ.
const { start } = require('./h');
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
const PHUT = +(process.env.LV || 30);
const LOAD = { ht_mu_0: ['Kim Cương', 'Lục Bảo', 'Netherite'], ht_ao_0: ['Vàng', 'Đá Đỏ', 'Lưu Ly'], ht_quan_0: ['Sắt', 'Đồng', 'Than'],
  ht_giay_0: ['Kim Cương', 'Netherite', 'Lục Bảo'], ht_kiem_0: ['Vàng', 'Đá Đỏ', 'Netherite'] };
const CODE = { 'Sắt': 'sat', 'Kim Cương': 'kimcuong', 'Netherite': 'netherite', 'Than': 'than', 'Đá Đỏ': 'dado', 'Lục Bảo': 'lucbao', 'Lưu Ly': 'lapis', 'Vàng': 'vang', 'Đồng': 'dong' };
const libId = it => { try { const cd = (it.components || []).find(c => c.type === 'custom_data'); return JSON.stringify(cd ? cd.data : '').match(/thuvien_id":\{"type":"string","value":"([a-z_0-9]+)"/)?.[1]; } catch (e) { return null; } };
async function trang(t, load) {
  const R = x => t.rc.cmd(x), N = t.bot.username;
  await R(`minecraft:clear ${N}`); await R(`gamemode survival ${N}`);
  const need = {}; Object.values(load).flat().forEach(g => need[CODE[g]] = (need[CODE[g]] || 0) + 1);
  for (const [k, n] of Object.entries(need)) await R(`tinhluyen give ${k} ${n} ${N}`);
  for (const id of Object.keys(load)) await R(`dmm laydo ${id} 1 ${N}`);
  await t.sleep(1200);
  for (const [id, gems] of Object.entries(load)) {
    const it = t.bot.inventory.items().find(i => libId(i) === id); if (!it) continue;
    await t.bot.equip(it, 'hand'); await t.sleep(300);
    let w = await t.openMenu('/thoren kham');
    for (const g of gems) { w = await t.click(find(t, w, /Ô trống/)); w = await t.click(find(t, w, new RegExp('^' + g + '$'))); t.log(N, id, t.chat.slice(-1)[0]); }
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); await t.sleep(300);
  }
}
async function veSpawn(t) {
  const N = t.bot.username; await t.rc.cmd(`spawn ${N}`); await t.end(); await t.sleep(2500);
  const t2 = await start(N); await t2.sleep(1500); return t2;
}
(async () => {
  let a = await start('ThuHaoQuang');
  await trang(a, LOAD);
  for (const [id, slot] of [['ht_mu_0', 'head'], ['ht_ao_0', 'torso'], ['ht_quan_0', 'legs'], ['ht_giay_0', 'feet']]) {
    const it = a.bot.inventory.items().find(i => libId(i) === id); if (it) { await a.bot.equip(it, slot); await a.sleep(300); } }
  let m = await start('ThuHaoMo');
  await trang(m, { ht_kiem_0: ['Kim Cương', 'Lục Bảo', 'Netherite'] });
  for (let i = 0; i < 450; i++) {   // rút kiếm của ThuHaoMo còn ~10%
    await m.rc.cmd(`execute at ThuHaoMo run summon minecraft:zombie ~3 ~ ~ {NoAI:1b,Silent:1b,Health:1f,Tags:["haoxem"],CustomName:'"『5』 Trùm thử"'}`);
    await m.rc.cmd(`damage @e[tag=haoxem,limit=1,sort=nearest] 50 minecraft:player_attack by ThuHaoMo`);
  }
  await m.rc.cmd('execute at ThuHaoMo run kill @e[type=minecraft:item,distance=..8]'); await m.rc.cmd('kill @e[tag=haoxem]'); await m.rc.cmd('xp set ThuHaoMo 0 levels');
  a = await veSpawn(a); m = await veSpawn(m);
  for (const t of [a, m]) { const k = t.bot.inventory.items().find(i => libId(i) === 'ht_kiem_0'); if (k) await t.bot.equip(k, 'hand'); }
  a.log('ThuHaoQuang ở', a.bot.entity.position.floored(), '| ThuHaoMo ở', m.bot.entity.position.floored());
  a.log('ngọc kiếm ThuHaoMo:', await m.rc.cmd('data get entity ThuHaoMo SelectedItem.components."minecraft:custom_data".PublicBukkitValues."duckymomocore:dmm_kham"'));
  a.log('SẴN SÀNG, đứng', PHUT, 'phút');
  const end = Date.now() + PHUT * 60000;
  while (Date.now() < end) {
    for (const t of [a, m]) { const p = t.bot.nearestEntity(e => e.type === 'player' && !/^ThuHao/.test(e.username || '')); if (p) t.bot.lookAt(p.position.offset(0, 1.6, 0)).catch(() => {}); }
    await a.sleep(500);
  }
  for (const t of [a, m]) { await t.rc.cmd(`minecraft:clear ${t.bot.username}`); await t.end(); }
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
