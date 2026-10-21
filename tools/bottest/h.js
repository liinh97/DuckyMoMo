process.on('uncaughtException', e => console.log('(bỏ qua lỗi bot:', e.message.slice(0, 60) + ')'));
// Bộ test: rcon tới banghoi-1 + bot (vbot.js) + tiện ích chờ / bấm menu.
const net = require('net');
const { make } = require('./vbot');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const strip = s => String(s).replace(/§./g, '');
function rconClient(host, pass) {
  let sock, buf = Buffer.alloc(0), id = 1, waiters = {};
  const pkt = (rid, type, body) => { const b = Buffer.from(body, 'utf8'); const p = Buffer.alloc(14 + b.length); p.writeInt32LE(10 + b.length, 0); p.writeInt32LE(rid, 4); p.writeInt32LE(type, 8); b.copy(p, 12); return p; };
  const connect = () => new Promise((res, rej) => {
    sock = net.connect(25575, host, () => { const r = id++; waiters[r] = () => res(); sock.write(pkt(r, 3, pass)); });
    sock.on('error', rej);
    sock.on('data', d => { buf = Buffer.concat([buf, d]); while (buf.length >= 4) { const len = buf.readInt32LE(0); if (buf.length < len + 4) break; const rid = buf.readInt32LE(4); const body = buf.slice(12, 4 + len - 2).toString('utf8'); buf = buf.slice(4 + len); if (waiters[rid]) { const w = waiters[rid]; delete waiters[rid]; w(body); } } });
  });
  const cmd = c => new Promise(res => { const r = id++; waiters[r] = b => res(strip(b)); sock.write(pkt(r, 2, c)); setTimeout(() => { if (waiters[r]) { delete waiters[r]; res('(timeout)'); } }, 8000); });
  return { connect, cmd, close: () => sock.end() };
}
// host: server vào thẳng (banghoi-1 mặc định; practice-1, sangtao-1… cùng khoá forwarding, cùng mật khẩu RCON)
async function start(name, ip, host = 'banghoi-1') {
  const rc = rconClient(host, process.env.RCONPASS); await rc.connect();
  const bot = make(name, host, ip);
  const chat = [];
  bot.on('messagestr', m => chat.push(m));
  bot.on('kicked', r => console.log('KICK', JSON.stringify(r).slice(0, 300)));
  bot.on('error', e => console.log('ERR', e.message));
  await new Promise((res, rej) => { bot.once('spawn', res); setTimeout(() => rej(new Error('không vào được')), 40000); });
  await sleep(3000);
  const t = {
    bot, rc, chat, sleep, strip,
    log: (...a) => console.log(...a),
    since(n) { return chat.slice(n); },
    async say(c, wait = 1200) { const n = chat.length; bot.chat(c); await sleep(wait); return chat.slice(n); },
    async openMenu(c) { const p = new Promise((res) => { bot.once('windowOpen', w => res(w)); setTimeout(() => res(null), 5000); }); bot.chat(c); const w = await p; await sleep(400); return w; },
    async click(slot, shift = false) { const p = new Promise(res => { bot.once('windowOpen', w => res(w)); setTimeout(() => res(bot.currentWindow), 2500); }); await bot.clickWindow(slot, 0, shift ? 1 : 0).catch(e => console.log('clickErr', e.message)); const w = await p; await sleep(300); return w; },
    itemText(it) { if (!it) return null; let name = it.customName || it.displayName || it.name; let lore = []; try { const comps = it.components || []; for (const c of comps) { if (c.type === 'custom_name') name = flat(c.data); if (c.type === 'lore') lore = c.data.map(flat); } } catch (e) {} return { name: strip(flat(name)), lore: lore.map(strip) }; },
    async attr(a) { return rc.cmd(`attribute ${name} ${a} get`); },
    async end() { bot.quit(); await sleep(1000); rc.close(); },
  };
  return t;
}
function flat(c) { if (c == null) return ''; if (typeof c === 'string') { try { return flat(JSON.parse(c)); } catch (e) { return c; } } if (Array.isArray(c)) return c.map(flat).join(''); if (typeof c === 'object') { if (c.type === 'compound' || c.type === 'list' || c.type === 'string') return flat(c.value); let s = (c.text ?? c[''] ?? '') ; if (typeof s === 'object') s = flat(s); return s + flat(c.extra); } return String(c); }
module.exports = { start, sleep };
// đưa bot tới (x,z) trên mặt đất: dịch chuyển phía server, rồi bot thoát vào lại để nhận đúng tọa độ (qua Via bot không theo được lệnh tp)
module.exports.moveTo = async (t, x, z, dim = 'minecraft:overworld', ip) => {
  const N = t.bot.username;
  await t.rc.cmd(`execute in ${dim} run forceload add ${x} ${z}`); await t.sleep(2500);
  const r = await t.rc.cmd(`execute in ${dim} positioned ${x} 0 ${z} positioned over motion_blocking_no_leaves run minecraft:tp ${N} ~ ~ ~`);
  await t.rc.cmd(`execute in ${dim} run forceload remove ${x} ${z}`);
  await t.end(); await t.sleep(2500);
  const t2 = await module.exports.start(N, ip); await t2.sleep(2000);
  t2.log('moveTo', r, '-> bot ở', t2.bot.entity.position.floored(), 'dưới chân', (t2.bot.blockAt(t2.bot.entity.position.offset(0, -1, 0)) || {}).name);
  return t2;
};
