// Món Vàng có hạn: cấp quyền tạm 70 giây (thay 30 ngày), bot dùng thử (/nick màu, /hat, /pp, chat màu), chờ hết hạn, xem còn sót gì.
// Phần đọc hạt (SQLite PlayerParticles) và quyền (MariaDB) làm ở script bash bên ngoài, đây chỉ in trạng thái game.
const h = require('./h');
const N = 'ThuVang', step = process.argv[2];
const PERMS = ['essentials.hat', 'essentials.chat.color', 'essentials.nick', 'essentials.nick.color', 'playerparticles.particles.max.1', 'playerparticles.effect.*', 'playerparticles.style.*'];
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c); await t.sleep(2500);
  const nick = async () => ((await R(`whois ${N}`)).match(/(Biệt danh|Nickname)[^\n]*/i) || [''])[0].trim();
  const head = async () => (await R(`data get entity ${N} equipment.head.id`)).replace(/.*data: /, '');
  if (step === 'cap') {
    for (const p of PERMS) await R(`lp user ${N} permission settemp ${p} true 70s`);
    await t.sleep(2500);
    let n = t.chat.length; t.bot.chat('/nick &aNickThu'); await t.sleep(1200); t.log('/nick:', t.chat.slice(n).join(' / ').slice(0, 120));
    await R(`minecraft:clear ${N}`); await R(`minecraft:give ${N} minecraft:pumpkin`); await t.sleep(800);
    await t.bot.equip(t.bot.inventory.items()[0], 'hand'); await t.sleep(300);
    n = t.chat.length; t.bot.chat('/hat'); await t.sleep(1200); t.log('/hat:', t.chat.slice(n).join(' / ').slice(0, 120));
    n = t.chat.length; t.bot.chat('/pp add flame normal'); await t.sleep(1500); t.log('/pp add:', t.chat.slice(n).join(' / ').slice(0, 160));
  } else if (step === 'thu') {   // sau khi hết hạn: thử dùng lại
    let n = t.chat.length; t.bot.chat('/nick &cNickLai'); await t.sleep(1200); t.log('/nick sau hết hạn:', t.chat.slice(n).join(' / ').slice(0, 120));
    n = t.chat.length; t.bot.chat('/pp add flame normal'); await t.sleep(1500); t.log('/pp add sau hết hạn:', t.chat.slice(n).join(' / ').slice(0, 160));
  }
  t.log(`[${step}] nick:`, await nick(), '| đầu:', await head());
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
