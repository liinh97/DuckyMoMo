// Chuỗi Thợ Cống bước 1 (sau vl-lk.js): bot tự đấm 10 nhện 1 máu (/damage … by không tính người giết) -> đếm 10/10 + báo xong.
const h = require('./h');
const N = process.env.BOTN || 'ThuLK';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  let t0 = await h.start(N);
  await t0.rc.cmd('execute in minecraft:overworld run minecraft:tp ' + N + ' 12280.5 83 -2229.5'); await t0.end(); await t0.sleep(2500);
  const t = await h.start(N);   // đất hoang (Rừng Lam): khu an toàn Kinh Đô không sinh quái
  t.log('bot ở', t.bot.entity.position.floored());
  const msgs = [], bars = [];
  t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) (p.isActionBar || p.overlay ? bars : msgs).push(v); });
  const R = x => t.rc.cmd(x);
  for (let i = 0; i < 10; i++) {
    await R(`execute at ${N} run summon minecraft:spider ^ ^ ^2 {Tags:["vltest"],NoAI:1b,Health:1f}`);
    await t.sleep(600);
    const sp = Object.values(t.bot.entities).filter(e => e.name === 'spider').sort((a, b) => a.position.distanceTo(t.bot.entity.position) - b.position.distanceTo(t.bot.entity.position))[0];
    if (i === 0) t.log('nhện thấy:', !!sp);
    if (sp) { await t.bot.lookAt(sp.position.offset(0, 0.4, 0)); t.bot.swingArm('right'); t.bot._client.write('use_entity', { target: sp.id, mouse: 1, sneaking: false }); }
    await t.sleep(700);
  }
  await t.sleep(1500);
  await R(`minecraft:kill @e[tag=vltest]`);
  t.log('## thanh dưới:', bars.filter(b => /nhện/.test(b)).slice(-2).join(' || '));
  t.log('## chat:\n  ' + msgs.filter(m => /nhện|Xong|Thợ Cống/.test(m)).join('\n  '));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
