// Đồ set vs quái dungeon tầng 1 (23/10, chủ server: "đồ tân thủ vào dungeon lv1 bất khả thi"): gọi quái Đỉnh Vách Đá thật
// (custom boss normalizedCombat, /em spawn custom) ra bãi test 13300,-2400; mỗi bộ đồ: % máu quái mất mỗi đòn + máu mình mất mỗi đòn quái.
const h = require('./h');
const N = process.env.BOTN || 'ThuDg';
const MOB = process.env.MOB || 'the_climb_undead.yml';
const LOADS = [
  ['Đồ sắt thường', ['mc:iron_helmet', 'mc:iron_chestplate', 'mc:iron_leggings', 'mc:iron_boots'], 'mc:iron_sword'],
  ['Tân Thủ +0', ['tt_mu_0', 'giap', 'tt_quan_0', 'tt_giay_0'], 'tt_kiem_0'],
  ['Tân Thủ +7', ['tt_mu_7', 'giap_tan_thu_7', 'tt_quan_7', 'tt_giay_7'], 'tt_kiem_7'],
  ['Tân Thủ +9', ['tt_mu_9', 'giap_tan_thu_9', 'tt_quan_9', 'tt_giay_9'], 'tt_kiem_9'],
  ['Chiến Binh +7', ['cb_mu_7', 'cb_ao_7', 'cb_quan_7', 'cb_giay_7'], 'cb_kiem_7'],
].concat((process.env.LOAD || '').split(';').filter(Boolean).map(x => { const [l, ids] = x.split(':'); const a = ids.split(','); return [l, a.slice(0, 4), a[4]]; }))
 .filter(l => process.env.LOAD ? process.env.LOAD.includes(l[0] + ':') : !process.env.CHI || process.env.CHI.split(',').includes(l[0]));
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  let t = await h.start(N); let R = c => t.rc.cmd(c);
  await R(`lp user ${N} parent add admin`); await t.sleep(2500);
  t = await h.moveTo(t, 13300, -2400); R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await R('forceload add 13296 -2404 13304 -2396'); await R(`gamemode survival ${N}`);
  const give = async id => id.startsWith('mc:') ? R(`minecraft:give ${N} minecraft:${id.slice(3)} 1`) : R(`dmm laydo ${id} 1 ${N}`);
  const killAll = async () => { await R(`execute at ${N} run minecraft:kill @e[type=!minecraft:player,type=!minecraft:item,distance=..40]`); await R('minecraft:kill @e[tag=dmgt]'); await t.sleep(500); };
  const spawn = async () => {
    const k = msgs.length; let n = 0;
    for (let a = 0; a < 4 && !n; a++) {
      t.bot.chat(`/em spawn boss ${MOB} ${process.env.LV || 10}`); await t.sleep(2500);
      await R(`execute at ${N} as @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:armor_stand,type=!minecraft:text_display,distance=..40] if data entity @s CustomName run minecraft:tag @s add dmgt`);
      n = parseInt((await R('execute if entity @e[tag=dmgt]')).replace(/\D/g, '')) || 0;
    }
    if (!n) t.log('   (không gọi được quái:', msgs.slice(k).join(' / ').slice(0, 200), ')');
    return n;
  };
  const hp = async sel => parseFloat((await R(`data get entity ${sel} Health`)).replace(/.*data: /, ''));
  for (const [label, armor, weapon] of LOADS) {
    await R(`minecraft:clear ${N}`); await R(`effect clear ${N}`); await t.sleep(400);
    for (const a of armor) await give(a);
    await give(weapon); await t.sleep(1500);
    for (const it of t.bot.inventory.items()) {
      const s = /helmet$/.test(it.name) ? 'head' : /chestplate$/.test(it.name) ? 'torso' : /leggings$/.test(it.name) ? 'legs' : /boots$/.test(it.name) ? 'feet' : 'hand';
      try { await t.bot.equip(it, s); } catch (e) { } await t.sleep(300);
    }
    await t.sleep(3000);
    const maxMe = parseFloat((await R(`attribute ${N} minecraft:max_health get`)).replace(/.*is /, ''));
    // (1) đánh: quái đứng im trước mặt, 3 lần
    const out = [];
    for (let k = 0; k < 3; k++) {
      await killAll(); if (!await spawn()) break;
      await R('execute as @e[tag=dmgt] run data merge entity @s {NoAI:1b}');
      await R(`execute as @e[tag=dmgt] at ${N} run minecraft:tp @s ^ ^ ^1.6`); await t.sleep(1500);
      const z = Object.values(t.bot.entities).filter(e => e.type === 'mob' || e.type === 'hostile').sort((a, b) => a.position.distanceTo(t.bot.entity.position) - b.position.distanceTo(t.bot.entity.position))[0];
      const h0 = await hp('@e[tag=dmgt,limit=1]'); const mx = parseFloat((await R('attribute @e[tag=dmgt,limit=1] minecraft:max_health get')).replace(/.*is /, ''));
      if (!z || isNaN(h0)) { out.push(NaN); continue; }
      await t.bot.lookAt(z.position.offset(0, 1.4, 0)); t.bot.attack(z); await t.sleep(1200);
      const h1 = await hp('@e[tag=dmgt,limit=1]');
      out.push(100 * (isNaN(h1) ? h0 : h0 - h1) / mx);
      await t.sleep(1500);
    }
    // (2) chịu đòn: thả 1 con (có AI) cạnh bot 10 giây, cộng máu mất từng lần
    await killAll(); await R(`effect give ${N} minecraft:instant_health 1 10 true`); await t.sleep(800);
    const drops = []; let last = t.bot.health;
    const onH = () => { if (t.bot.health < last) drops.push(last - t.bot.health); last = t.bot.health; };
    if (await spawn()) {
      await R(`execute as @e[tag=dmgt] at ${N} run minecraft:tp @s ^ ^ ^1.2`);
      last = t.bot.health; t.bot.on('health', onH);
      for (let s = 0; s < 10 && t.bot.health > 4; s++) await t.sleep(1000);
      t.bot.removeListener('health', onH);
    }
    await killAll(); await R(`effect give ${N} minecraft:instant_health 1 10 true`);
    const med = a => { const s = a.filter(x => !isNaN(x)).sort((x, y) => x - y); return s.length ? s[Math.floor(s.length / 2)] : NaN; };
    const dmg = med(out), hit = drops.length ? drops.reduce((a, b) => a + b, 0) / drops.length : NaN;
    t.log(`## ${label.padEnd(14)} đánh: ${out.map(x => isNaN(x) ? '?' : x.toFixed(0) + '%').join('/')} (≈${isNaN(dmg) ? '?' : Math.ceil(100 / dmg)} nhát) | bị đánh ${drops.length} lần, mất ${isNaN(hit) ? '?' : hit.toFixed(1)} máu/lần (máu tối đa ${maxMe}, ≈${isNaN(hit) ? '?' : Math.floor(maxMe / hit)} đòn thì chết)`);
  }
  await killAll(); await R('forceload remove 13296 -2404 13304 -2396');
  await R(`lp user ${N} parent remove admin`); await R(`minecraft:clear ${N}`); await R(`effect clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
