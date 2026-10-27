// Chuỗi việc lữ khách (28/10) + khối ô khảm. Bot không mở được hội thoại BetonQuest nên gọi /dmm viecnpc (lời NPC ra sau 60 giây
// dự phòng vì không có sự kiện đóng hội thoại). Kỹ Sư: nộp 64 đá đỏ (bước 1) -> thưởng + giao bước 2; Thợ Cống: hạ 10 nhện (đếm, báo xong);
// mũ Thần Thánh +3 lấy bằng laydo -> vào lại game -> lore có "KHẢM TINH LUYỆN [0/4]".
// Chạy: BOTN=ThuLK ./tools/bottest/run.sh vl-lk.js   Dọn: /choilai admin ThuLK moi
const h = require('./h');
const N = process.env.BOTN || 'ThuLK';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  let t = await h.start(N);
  const msgs = [];
  const hook = t => t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v && !(p.isActionBar || p.overlay)) msgs.push(v); });
  hook(t);
  const R = x => t.rc.cmd(x);
  const show = (name) => { t.log('## ' + name + ':\n  ' + msgs.join('\n  ').slice(0, 2500)); msgs.length = 0; };
  await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`); await R(`eco set ${N} 0`);
  await R(`effect give ${N} minecraft:resistance 600 4 true`);
  // Kỹ Sư: nhận bước 1, nộp đá đỏ
  await R(`dmm viecnpc ${N} lk_hoinuoc`); await t.sleep(62000); show('Kỹ Sư lần 1 (giao bước 1)');
  await R(`minecraft:give ${N} minecraft:redstone 64`); await t.sleep(500);
  await R(`dmm viecnpc ${N} lk_hoinuoc`); await t.sleep(62000); show('Kỹ Sư lần 2 (nộp đá đỏ, giao bước 2)');
  t.log('## đá đỏ còn:', await R(`clear ${N} minecraft:redstone 0`), '| tiền:', await R(`eco balance ${N}`) || '');
  // Thợ Cống: nhận bước 1, hạ 10 nhện
  await R(`dmm viecnpc ${N} lk_cong`); await t.sleep(62000); show('Thợ Cống (giao bước 1)');
  for (let i = 0; i < 10; i++) {
    await R(`execute at ${N} run summon minecraft:spider ~3 ~ ~ {Tags:["vltest"],NoAI:1b}`);
    await t.sleep(200);
    await R(`damage @e[type=minecraft:spider,tag=vltest,limit=1,sort=nearest] 100 minecraft:player_attack by ${N}`);
    await t.sleep(400);
  }
  await t.sleep(1500); show('sau khi hạ 10 nhện');
  await R(`minecraft:kill @e[tag=vltest]`);
  t.bot.chat('/nhiemvu'); await t.sleep(2000); show('/nhiemvu');
  // mũ Thần Thánh: laydo rồi vào lại
  t.log('laydo:', await R(`dmm laydo dv_mu_3 1 ${N}`));
  await t.end(); await t.sleep(3000);
  t = await h.start(N); await t.sleep(3000);
  const mu = t.bot.inventory.items().find(i => /helmet/.test(i.name));
  t.log('## lore mũ:', mu ? JSON.stringify(t.itemText(mu)).slice(0, 1500) : '(không có mũ)');
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
