// Khảm: chỉ số + trần (bộ Huyền Thoại +0), hút máu/đốt/sát thương (kiếm), tốc đánh có trần (rìu), chậm mòn (giáo 3 Đồng vs giáo trơn),
// giữ ngọc khi vào lại game (đồ lấy bằng laydo chưa có dấu phiên bản -> Thợ Rèn dựng lại lúc vào game).
const { start, moveTo } = require('./h');
const N = process.env.BOTN || 'ThuKhamCS';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
const LOAD = { ht_mu_0: ['Sắt', 'Sắt', 'Sắt'], ht_ao_0: ['Sắt', 'Sắt', 'Sắt'], ht_quan_0: ['Kim Cương', 'Netherite', 'Than'],
  ht_giay_0: ['Đá Đỏ', 'Lục Bảo', 'Lưu Ly'], ht_kiem_0: ['Than', 'Lưu Ly', 'Netherite'], ht_riu_0: ['Vàng', 'Đá Đỏ', 'Đá Đỏ'], ht_giao_0: ['Đồng', 'Đồng', 'Đồng'] };
const CODE = { 'Sắt': 'sat', 'Kim Cương': 'kimcuong', 'Netherite': 'netherite', 'Than': 'than', 'Đá Đỏ': 'dado', 'Lục Bảo': 'lucbao', 'Lưu Ly': 'lapis', 'Vàng': 'vang', 'Đồng': 'dong' };
(async () => {
  let t = await start(N);
  let R = x => t.rc.cmd(x);
  await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`); await R(`eco set ${N} 0`); await R(`effect clear ${N}`);
  const need = {}; Object.values(LOAD).flat().forEach(g => need[CODE[g]] = (need[CODE[g]] || 0) + 1);
  for (const [k, n] of Object.entries(need)) await R(`tinhluyen give ${k} ${n} ${N}`);
  for (const id of Object.keys(LOAD)) await R(`dmm laydo ${id} 1 ${N}`);
  await R(`dmm laydo ht_giao_0 1 ${N}`);   // giáo trơn để so độ mòn
  await t.sleep(1200);
  const libId = it => { try { const cd = (it.components || []).find(c => c.type === 'custom_data'); return JSON.stringify(cd ? cd.data : '').match(/thuvien_id":\{"type":"string","value":"([a-z_0-9]+)"/)?.[1]; } catch (e) { return null; } };
  for (const [id, gems] of Object.entries(LOAD)) {
    const it = t.bot.inventory.items().find(i => libId(i) === id && !(t.itemText(i).lore.join('').includes('◆')));
    if (!it) { t.log('không thấy', id); continue; }
    await t.bot.equip(it, 'hand'); await t.sleep(300);
    let w = await t.openMenu('/thoren kham');
    for (const g of gems) { w = await t.click(find(t, w, /Ô trống/)); w = await t.click(find(t, w, new RegExp('^' + g + '$'))); }
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
    await t.sleep(300);
    t.log(id, '->', t.itemText(t.bot.heldItem).lore.filter(l => /◆|◇|KHẢM/.test(l)).join(' / '));
  }
  const byId = id => t.bot.inventory.items().find(i => libId(i) === id && t.itemText(i).lore.join('').includes('◆'));
  for (const [id, slot] of [['ht_mu_0', 'head'], ['ht_ao_0', 'torso'], ['ht_quan_0', 'legs'], ['ht_giay_0', 'feet']]) { await t.bot.equip(byId(id), slot); await t.sleep(300); }
  await t.bot.equip(byId('ht_kiem_0'), 'hand'); await t.sleep(500);
  t = await moveTo(t, 12280, -2236); R = x => t.rc.cmd(x);   // ra đất hoang: spawn là vùng an toàn (không nhận sát thương, không có quái)
  await t.sleep(6000);                 // hết bất tử sau dịch chuyển
  await t.bot.equip(byId('ht_kiem_0'), 'hand'); await t.sleep(2500);
  const mod = async (attr, k) => (await R(`attribute ${N} ${attr} modifier value get duckymomocore:${k}`)).replace(/.*is /, '');
  t.log('máu (trần 10%):', await mod('minecraft:max_health', 'kham_mau'), '| tốc chạy:', await mod('minecraft:movement_speed', 'kham_tocchay'));
  // giảm sát thương: out_of_world bỏ qua giáp -> mất đúng 10 × (1 - 4%)
  await R(`effect clear ${N}`); await R(`minecraft:data merge entity ${N} {}`);
  const hp = async () => +((await R(`data get entity ${N} Health`)).match(/([\d.]+)f/) || [0, 0])[1];
  const maxHp = +((await R(`attribute ${N} minecraft:max_health get`)).match(/([\d.]+)$/) || [0, 0])[1];
  await R(`effect give ${N} minecraft:instant_health 1 5`); await t.sleep(600);
  let h0 = await hp(); await R(`damage ${N} 10 minecraft:out_of_world`); await t.sleep(400); let h1 = await hp();
  t.log('giảm st: máu', h0, '->', h1, '= mất', (h0 - h1).toFixed(2), '(mong 9.6)');
  // hồi máu: instant_health I hồi 4 -> +6% = 4.24
  await R(`effect give ${N} minecraft:instant_health 1 0`); await t.sleep(600); const h2 = await hp();
  t.log('hồi máu:', h1, '->', h2, '= +', (h2 - h1).toFixed(2), '(mong 4.24) max', maxHp);
  // kinh nghiệm: quả 100 điểm -> +5% = 105
  await R(`xp set ${N} 0 levels`); await R(`xp set ${N} 0 points`);
  await R(`execute at ${N} run summon minecraft:experience_orb ~ ~ ~ {Value:100}`); await t.sleep(2000);
  t.log('kinh nghiệm XpTotal:', (await R(`data get entity ${N} XpTotal`)).replace(/.*data: /, ''), '(mong 105)');
  // kiếm: đốt + hút máu + sát thương
  await R(`execute at ${N} run summon minecraft:husk ^ ^ ^2 {NoAI:1b,Silent:1b,Health:1000f,attributes:[{id:"minecraft:max_health",base:1000}],Tags:["thukham"]}`);
  await t.sleep(800);
  await R(`damage ${N} 8 minecraft:out_of_world`); await t.sleep(300);
  const hBefore = await hp();
  const mob = Object.values(t.bot.entities).find(e => e.name === 'husk');
  let fires = 0;
  for (let i = 0; i < 30 && mob; i++) {
    await t.bot.lookAt(mob.position.offset(0, 1, 0)); t.bot.attack(mob); await t.sleep(650);
    const f = (await R(`data get entity @e[tag=thukham,limit=1] Fire`)).match(/(-?\d+)s/); if (f && +f[1] > 0) fires++;
  }
  const hAfter = await hp();
  t.log('kiếm 30 nhát: lần thấy husk cháy', fires, '| máu bot', hBefore, '->', hAfter, '(hút máu nên tăng)', '| husk còn', (await R(`data get entity @e[tag=thukham,limit=1] Health`)).replace(/.*data: /, ''));
  // rìu: tốc đánh 3+4+4 = 11 -> trần 10
  await t.bot.equip(byId('ht_riu_0'), 'hand'); await t.sleep(1800);
  t.log('tốc đánh rìu (trần 10%):', await mod('minecraft:attack_speed', 'kham_tocdanh'));
  // giáo: chậm mòn 3 Đồng (60% -> trần 50%) so giáo trơn, mỗi giáo 200 nhát
  const dmgOf = it => { const c = (it.components || []).find(c => c.type === 'damage'); return c ? c.data : 0; };
  const spears = t.bot.inventory.items().filter(i => libId(i) === 'ht_giao_0');
  for (const sp of spears) {
    await t.bot.equip(sp, 'hand'); await t.sleep(300);
    for (let i = 0; i < 200 && mob; i++) { t.bot.attack(mob); await t.sleep(60); }
    await t.sleep(500);
    const now = t.bot.heldItem;
    t.log('giáo', t.itemText(now).lore.join('').includes('◆') ? '3 Đồng' : 'trơn', ': độ mòn sau 200 nhát =', dmgOf(now));
  }
  await R(`kill @e[tag=thukham]`);
  // vào lại game: ngọc phải còn
  await t.end(); await t.sleep(3000);
  t = await start(N); await t.sleep(2000);
  const all = [...t.bot.inventory.items(), ...Object.values(t.bot.inventory.slots).filter(Boolean)];
  const kept = new Set(all.filter(i => t.itemText(i).lore.join('').includes('◆')).map(i => t.itemText(i).name));
  t.log('sau vào lại, món còn ngọc:', [...kept].join(', '));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
