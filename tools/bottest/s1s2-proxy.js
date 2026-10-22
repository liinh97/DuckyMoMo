// s1/s2: bot vào QUA PROXY như người chơi thật (Velocity + Sonar + AuthMe), KHÔNG /register, KHÔNG nhập mật khẩu.
// s1: chưa /login thử đi lại, mở túi đồ, /server banghoi-1, /msg, chat, /spawn -> chỉ được /login /register.
// s2: tên Java bắt đầu bằng "." (giả tiền tố Bedrock của Floodgate) -> phải bị từ chối hoặc vẫn bị hỏi mật khẩu.
// Chạy: ./tools/bottest/run.sh s1s2-proxy.js <tên>
const mineflayer = require('mineflayer');
const name = process.argv[2] || 'ThuS1';
const bot = mineflayer.createBot({ host: 'proxy', port: 25577, username: name, version: '1.21.4', auth: 'offline' });
const chat = []; const log = (...a) => console.log(...a);
// Sonar đòi client brand (máy thật gửi "vanilla" ở pha configuration)
const brand = Buffer.concat([Buffer.from([7]), Buffer.from('vanilla')]);
bot._client.on('state', st => { if (st === 'configuration') { try { bot._client.write('custom_payload', { channel: 'minecraft:brand', data: brand }); } catch (e) { log('brand lỗi', e.message); } } });
bot.on('messagestr', m => chat.push(m));
const flat = c => c == null ? '' : typeof c === 'string' ? c : Array.isArray(c) ? c.map(flat).join('') : (c.type && c.value !== undefined) ? flat(c.value) : (flat(c.text) + flat(c['']) + flat(c.extra));
bot.on('kicked', r => { let t = r; try { t = flat(typeof r === 'string' ? JSON.parse(r) : r); } catch (e) {} log('KICK', String(t).replace(/\s+/g, ' ').slice(0, 400)); });
bot.on('end', r => { log('END', r, '| tin:', chat.join(' | ').slice(0, 500)); process.exit(0); });
bot.on('error', e => log('ERR', e.message));
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.once('spawn', async () => {
  await sleep(6000);   // Sonar kiểm (rơi tự do) rồi mới vào sảnh
  log('vào, vị trí', bot.entity.position.floored(), '| tin đầu:', chat.join(' | ').replace(/\s+/g, ' ').slice(0, 400));
  await sleep(4000);
  const p0 = bot.entity.position.clone();
  bot.setControlState('forward', true); bot.setControlState('sprint', true); await sleep(2500); bot.clearControlStates(); await sleep(1000);
  log(`s1 đi lại 2,5 giây: dịch ${p0.distanceTo(bot.entity.position).toFixed(2)} khối (server kéo lại thì ~0)`);
  for (const c of ['/server banghoi-1', '/msg ThuX1 xin chao', 'xin chao moi nguoi', '/spawn', '/menu', '/tpa ThuX1']) {
    const n0 = chat.length; bot.chat(c); await sleep(1800);
    log(`s1 "${c}" ->`, chat.slice(n0).join(' | ').replace(/\s+/g, ' ').slice(0, 200) || '(không phản hồi)');
  }
  log('  game:', bot.game && bot.game.dimension, '| máy chủ hiện (theo tin):', chat.filter(l => /Bang Hội|sảnh|lobby/i.test(l)).slice(-1).join(''));
  bot.quit(); await sleep(1000); process.exit(0);
});
setTimeout(() => { log('TIMEOUT | tin:', chat.join(' | ').slice(0, 500)); process.exit(1); }, 90000);
