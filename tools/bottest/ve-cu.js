// Vé gacha cũ (mô tả "/hom") phải tự đổi theo Thư viện đồ khi vào lại game.
const { start } = require('./h');
const N = process.env.BOTN || 'ThuVeCu';
(async () => {
  let t = await start(N);
  const R = x => t.rc.cmd(x);
  await R(`minecraft:clear ${N}`);
  t.log('give:', await R(`minecraft:give ${N} minecraft:paper[custom_data={PublicBukkitValues:{"duckymomocore:thuvien_id":"chia_khoa_diemdanh"}},lore=['"Mở hòm: /hom hoặc cầm chìa bấm chuột phải"'],custom_name='"Vé cũ"'] 3`));
  await t.sleep(800);
  const show = tt => tt.bot.inventory.items().filter(i => i.name === 'paper').map(i => i.count + 'x ' + JSON.stringify(tt.itemText(i))).join(' ; ');
  t.log('trước:', show(t));
  await t.end(); await t.sleep(2500);
  t = await start(N); await t.sleep(1500);
  t.log('sau khi vào lại:', show(t));
  await t.rc.cmd(`minecraft:clear ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
