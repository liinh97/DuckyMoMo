// Việc dân làng nhờ (28/10): chưa mở nhánh hỏi Gác Cổng -> hỏi Già Làng (mở nhánh, nhận việc) -> /nhiemvu -> /diemdanh (xong việc)
// -> về Già Làng nhận thưởng -> bấm NPC quầy Ngư Ông (nói, không mở quầy) -> bấm lần nữa (mở quầy). Kèm đọc thanh chỉ số (AuraSkills,
// %dmmcap_mau_mm% / %dmmcap_hud_mm%) và tên trong Tab (%dmmcap_mau%). Hội thoại BetonQuest bot không bấm được nên gọi thẳng /dmm viecnpc.
// Chạy: BOTN=ThuVL ./tools/bottest/run.sh vl-thu.js   Dọn: /choilai admin ThuVL moi
const h = require('./h');
const N = process.env.BOTN || 'ThuVL';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  let t = await h.start(N);
  const msgs = [], bars = new Set();
  const hook = (t) => t.bot._client.on('system_chat', p => {
    const v = txt(p.content ?? p.text ?? p).trim();
    if (!v) return;
    if (p.isActionBar || p.overlay) bars.add(v); else msgs.push(v);
  });
  hook(t);
  const step = async (name, fn, wait = 2500) => { msgs.length = 0; await fn(); await t.sleep(wait); t.log('## ' + name + ':\n  ' + msgs.join('\n  ').slice(0, 2500)); };
  await t.sleep(3000);
  await step('Gác Cổng khi chưa mở nhánh', () => t.rc.cmd(`dmm viecnpc ${N} gaccong`));
  await step('/nhiemvu trước khi mở', () => t.bot.chat('/nhiemvu'), 2000);
  await step('Già Làng (mở nhánh)', () => t.rc.cmd(`dmm viecnpc ${N} gialang`));
  await step('/nhiemvu sau khi nhận', () => t.bot.chat('/nhiemvu'), 2000);
  await step('/diemdanh', () => t.bot.chat('/diemdanh'), 3000);
  if (t.bot.currentWindow) { t.log('## cửa sổ điểm danh:', t.bot.currentWindow.title); t.bot.closeWindow(t.bot.currentWindow); }
  await step('Già Làng (trả thưởng)', () => t.rc.cmd(`dmm viecnpc ${N} gialang`));
  await step('Già Làng hỏi lại (không có gì mới)', () => t.rc.cmd(`dmm viecnpc ${N} gialang`));
  // thanh chỉ số + Tab
  t.log('## thanh chỉ số:', [...bars].slice(-4).join(' || '));
  const me = t.bot.players[N];
  t.log('## tên trong Tab:', me && me.displayName ? JSON.stringify(me.displayName.json ?? me.displayName.toString()).slice(0, 400) : '(không có)');
  // NPC quầy Ngư Ông (125, ~12706 71 -2632): dịch chuyển phía server rồi vào lại (qua Via bot không theo lệnh tp)
  await t.rc.cmd(`minecraft:tp ${N} 12704.5 71 -2629.5`); await t.end(); await t.sleep(2500);
  t = await h.start(N);
  hook(t);
  await t.sleep(2500);
  const npc = Object.values(t.bot.entities).filter(e => e.type === 'player' && e.username !== N)
    .sort((a, b) => a.position.distanceTo({ x: 12706, y: 71, z: -2632 }) - b.position.distanceTo({ x: 12706, y: 71, z: -2632 }))[0];
  t.log('## NPC gần quầy Ngư Ông:', npc && npc.username, npc && npc.position, '| bot ở', t.bot.entity.position.floored());
  if (npc) {
    await t.bot.lookAt(npc.position.offset(0, 1.6, 0));
    msgs.length = 0; await t.use(npc); await t.sleep(2500);
    t.log('## bấm lần 1:\n  ' + msgs.join('\n  ').slice(0, 1500), '| cửa sổ:', t.bot.currentWindow ? t.bot.currentWindow.title : 'không');
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
    msgs.length = 0; await t.use(npc); await t.sleep(2500);
    t.log('## bấm lần 2:\n  ' + msgs.join('\n  ').slice(0, 800), '| cửa sổ:', t.bot.currentWindow ? JSON.stringify(t.bot.currentWindow.title).slice(0, 120) : 'không');
  }
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
