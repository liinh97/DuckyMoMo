// Server có gửi skin (textures) của NPC Citizens xuống máy người chơi không: bắt gói player_info lúc bot vào gần NPC.
const h = require('./h');
const N = process.env.BOTN || 'ThuSkin';
(async () => {
  let t = await h.start(N);
  await t.rc.cmd(`minecraft:tp ${N} 12708.5 66 -2512.5`); await t.end(); await t.sleep(2500);
  const bot = require('./vbot').make(N, 'banghoi-1');
  const seen = [];
  bot._client.on('player_info', p => {
    for (const d of (p.data || [])) {
      const name = d.player && d.player.name || d.name;
      const props = (d.player && d.player.properties) || d.properties || [];
      if (name && name !== N) seen.push(name + ' | textures: ' + (props.find(x => x.name === 'textures') ? 'CÓ' + (props.find(x => x.name === 'textures').signature ? ' + chữ ký' : ' (không chữ ký)') : 'KHÔNG'));
    }
  });
  await new Promise(r => bot.once('spawn', r)); await new Promise(r => setTimeout(r, 8000));
  for (const s of [...new Set(seen)]) console.log('##', s);
  bot.quit(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
