// Bot vào thẳng banghoi-1 bằng Velocity modern forwarding (bỏ qua proxy/Sonar) để test.
const mineflayer = require('mineflayer');
const crypto = require('crypto');
const SECRET = process.env.VSECRET;
function varint(n) { const b = []; do { let t = n & 0x7f; n >>>= 7; if (n) t |= 0x80; b.push(t); } while (n); return Buffer.from(b); }
function str(s) { const d = Buffer.from(s, 'utf8'); return Buffer.concat([varint(d.length), d]); }
function offlineUuid(name) { const h = crypto.createHash('md5').update('OfflinePlayer:' + name).digest(); h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80; return h; }
// ip: địa chỉ giả gửi qua forwarding (2 bot khác ip thì không bị tính là cùng máy, vd Đấu Trường cùng IP = giao hữu)
function make(name, host, ip = '127.0.0.1') {
  const bot = mineflayer.createBot({ host, port: 25565, username: name, version: '1.21.4', auth: 'offline' });
  bot._client.removeAllListeners('login_plugin_request');
  bot._client.on('login_plugin_request', p => {
    if (p.channel !== 'velocity:player_info') return bot._client.write('login_plugin_response', { messageId: p.messageId });
    const body = Buffer.concat([varint(1), str(ip), offlineUuid(name), str(name), varint(0)]);
    const sig = crypto.createHmac('sha256', SECRET).update(body).digest();
    bot._client.write('login_plugin_response', { messageId: p.messageId, data: Buffer.concat([sig, body]) });
  });
  return bot;
}
module.exports = { make };
if (require.main === module) {
  const bot = make(process.argv[2] || 'ThuCapDo', process.argv[3]);
  bot.on('messagestr', m => console.log('CHAT', m));
  bot.on('kicked', r => console.log('KICK', JSON.stringify(r).slice(0, 400)));
  bot.on('end', r => { console.log('END', r); process.exit(0); });
  bot.on('error', e => console.log('ERR', e.message));
  bot.once('spawn', () => { console.log('SPAWN', bot.entity.position, bot.health); setTimeout(() => bot.quit(), 8000); });
  setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 45000);
}
