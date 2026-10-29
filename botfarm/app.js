// Bot của trang quản trị › Bot (27/10, chủ server). Mỗi bot là tài khoản offline vào THẲNG server con qua Velocity modern forwarding
// (không qua proxy: không tính vào số người ở danh sách server / MOTD; đứng yên = AFK nên Activity không tính giờ chơi, LowPop không đếm).
// Bot không tự làm gì: chủ server bật / tắt, chọn server, cho nói / chạy lệnh / đi tới toạ độ từ trang quản trị (panel gọi HTTP :8090).
//   GET  /bots   -> {servers, bots: [{name, want, server, online, x, y, z, home, err}], events}
//   POST /act    {bot ("*" = mọi bot), action, server, text, x, z}
//        action: join (vào, kèm server), leave (thoát), server (đổi server), chat (nói), cmd (chạy lệnh như bot gõ),
//                move (đi tới x z), home (về chỗ mặc định), sethome (lấy chỗ đang đứng làm chỗ mặc định),
//                random (Bang Hội Chiến: khu ngẫu nhiên chưa bot nào đứng, đặt làm chỗ mặc định),
//                create (tạo bot mới tên b.bot, vào luôn server b.server), remove (xoá bot đã tạo thêm, phải thoát trước),
//                chatcfg (bot tự chat với nhau: on, min, max phút; lưu /state/chat.json), chatnow (cho chat một đoạn ngay)
// Bot tạo thêm (27/10) làm y như 20 bot gốc: quyền duckymomo.vaora.an (cấp TRƯỚC lần vào đầu cho khỏi báo [+]), Sáng tạo + /god mỗi lần
// xuất hiện, IP giả riêng, tự đăng ký AuthMe ở sảnh. Lưu trong bots.json (created, ipn = số IP giả).
// /bots có thêm spawn: true khi bot đang đứng trong 16 ô quanh điểm spawn của server đó.
// Trạng thái: /state/bots.json {name: {server, want, home: {server: {x,y,z}}, at: {server: {x,y,z}}}} (home = chỗ mặc định, at = chỗ đang đứng).
// Bang Hội Chiến: bot chưa có chỗ thì tự chọn một chỗ đứng khô ráo gần các điểm warp (mỗi bot một khu, không tụ một chỗ).
// Server khác: đứng ở chỗ vào game (spawn) trừ khi chủ server cho đi / đặt chỗ.
'use strict';
const http = require('http');
const net = require('net');
const fs = require('fs');
const crypto = require('crypto');
const mineflayer = require('mineflayer');
const { pathfinder, Movements, goals } = require('mineflayer-pathfinder');

const SECRET = process.env.VELOCITY_SECRET || '';
const RCON_PASS = process.env.RCON_PASSWORD || '';
const BOT_PASS = process.env.BOT_PASSWORD || '';
const STATE = '/state/bots.json';
const SERVERS = { 'banghoi-1': 'Bang Hội Chiến', lobby: 'Sảnh', 'practice-1': 'Practice', 'sangtao-1': 'Thái Ấp Sáng Tạo' };
const BASE = ['Minh_Tuan', 'HoangLong99', 'Thu_Trang', 'Duc_Anh', 'NamPro2k8', 'Linh_Chi', 'Bao_Khang', 'QuangHuy_VN', 'Tuan_Kiet', 'MaiAnh2k9',
  'KhoiNguyen', 'TrungHieu07', 'Phuong_Vy', 'GiaBao_VN', 'Hai_Dang', 'AnhThu_x', 'ThanhDat99', 'Khanh_Linh', 'VietHoang', 'Nhat_Minh'];
const NAMES = [...BASE];   // + bot tạo thêm ở trang quản trị (đọc từ bots.json bên dưới)
// Bang Hội Chiến: điểm gợi ý (spawn + điểm warp thành phố); bot chỉ đứng chỗ khô ráo tìm được trong 6 ô quanh đó
const SPOTS = [[12704, 85, -2436], [12689, 71, -2626], [12853, 85, -1966], [12942, 63, -1896], [12708, 85, -2428], [12926, 98, -1916],
  [12696, 100, -2708], [12258, 72, -2144], [12626, 110, -2232], [12542, 97, -2726], [12406, 73, -2668], [12308, 92, -2554], [12114, 121, -2502],
  [11826, 108, -2244], [12902, 68, -1670], [12908, 75, -1514], [12956, 90, -1426], [13046, 125, -1462], [12534, 63, -1380], [12748, 63, -1288],
  [13048, 81, -1646], [12202, 86, -2376], [12006, 63, -1644], [12408, 63, -1602]];

const sleep = ms => new Promise(r => setTimeout(r, ms));
const now = () => new Date().toISOString();
const log = (...a) => console.log(new Date().toLocaleTimeString('vi-VN', { hour12: false }), ...a);

// ---------------------------------------------------------------- trạng thái

let state = {};
let fresh = false;
try { state = JSON.parse(fs.readFileSync(STATE, 'utf8')); } catch (e) { state = {}; fresh = true; }
for (const n of Object.keys(state)) if (state[n] && state[n].created && !NAMES.includes(n)) NAMES.push(n);
for (const n of NAMES) state[n] = Object.assign({ server: 'banghoi-1', want: false, home: {}, at: {} }, state[n] || {});
for (const n of NAMES) delete state[n].bad;   // chỗ "xấu" của lượt chạy trước (thường do quái giết, nay bot bất tử): thử lại
if (fresh) NAMES.slice(0, 7).forEach(n => { state[n].want = true; });   // lần đầu: 7 bot đang đứng từ trước (tools/bottest/dung-spawn.js) bật sẵn
// chỗ mặc định trùng nhau (bản đầu chọn song song): giữ bot đầu, bot sau chọn lại
NAMES.forEach((n, k) => {
  const h = state[n].home['banghoi-1'];
  // 29/10: bot đã gom phòng ẩn (homeOld) đứng sát nhau là đúng ý: không coi là trùng (trước đây mỗi lần khởi động xoá chỗ của 22 bot)
  if (h && state[n].homeOld === undefined && NAMES.slice(0, k).some(o => { const g = state[o].home['banghoi-1']; return state[o].homeOld === undefined && g && Math.hypot(g.x - h.x, g.z - h.z) < 12; })) {
    delete state[n].home['banghoi-1']; delete state[n].at['banghoi-1'];
  }
});
let saveTimer = null;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => { try { fs.mkdirSync('/state', { recursive: true }); fs.writeFileSync(STATE + '.tmp', JSON.stringify(state, null, 1)); fs.renameSync(STATE + '.tmp', STATE); } catch (e) { log('không lưu được trạng thái:', e.message); } }, 500);
}
const RT = {};   // name -> {bot, connecting, fast, retryAt, err, deaths: [ts], placedAt}
const newRT = () => ({ bot: null, connecting: false, fast: false, retryAt: 0, err: '', deaths: [], placedAt: 0 });
for (const n of NAMES) RT[n] = newRT();
/** Số IP giả của bot (10.30.i.(10+i)): bot gốc theo thứ tự, bot tạo thêm giữ số được cấp lúc tạo (xoá bot khác không đổi IP). */
const ipIndex = n => state[n].ipn || (BASE.indexOf(n) + 1);
const EVENTS = [];
function event(e) { EVENTS.push(Object.assign({ ts: now() }, e)); if (EVENTS.length > 300) EVENTS.shift(); }

// ---------------------------------------------------------------- RCON (mỗi server một kết nối, tự nối lại)

const RCON = {};
function rcon(host) {
  if (RCON[host]) return RCON[host];
  let sock = null, buf = Buffer.alloc(0), id = 1, waiters = {}, ready = null;
  const pkt = (rid, type, body) => { const b = Buffer.from(body, 'utf8'); const p = Buffer.alloc(14 + b.length); p.writeInt32LE(10 + b.length, 0); p.writeInt32LE(rid, 4); p.writeInt32LE(type, 8); b.copy(p, 12); return p; };
  const connect = () => ready || (ready = new Promise((res, rej) => {
    sock = net.connect(25575, host, () => { const r = id++; waiters[r] = () => res(); sock.write(pkt(r, 3, RCON_PASS)); });
    sock.on('error', e => { ready = null; rej(e); });
    sock.on('close', () => { ready = null; for (const k of Object.keys(waiters)) { waiters[k]('(mất kết nối)'); delete waiters[k]; } });
    sock.on('data', d => {
      buf = Buffer.concat([buf, d]);
      while (buf.length >= 4) { const len = buf.readInt32LE(0); if (buf.length < len + 4) break; const rid = buf.readInt32LE(4); const body = buf.slice(12, 4 + len - 2).toString('utf8'); buf = buf.slice(4 + len); if (waiters[rid]) { const w = waiters[rid]; delete waiters[rid]; w(body); } }
    });
  }));
  const send = async c => {
    await connect();
    return new Promise(res => { const r = id++; waiters[r] = b => res(String(b).replace(/§./g, '').replace(/\x1b\[[0-9;]*m/g, '')); sock.write(pkt(r, 2, c)); setTimeout(() => { if (waiters[r]) { delete waiters[r]; res('(timeout)'); } }, 8000); });
  };
  // Từng lệnh một: RCON của server đọc mỗi lần một gói, hai lệnh gửi sát nhau dính chung một gói thì lệnh sau bị bỏ
  // (27/10: /gamemode lúc bot vào bị mất -> bot Sinh tồn chết vì quái / kẹt tường)
  let queue = Promise.resolve();
  const cmd = c => { const p = queue.then(() => send(c)); queue = p.catch(() => {}); return p; };
  return (RCON[host] = { cmd });
}

/** Điểm spawn thế giới của từng server (để trang Bot ghi "ở spawn"). Lệnh từ console chạy tại spawn: nạp chunk "~ ~",
 *  thả marker ở "~ ~ ~" rồi đọc toạ độ. Dò lúc chạy, server chưa lên thì thử lại sau. */
const SPAWN = {};
async function learnSpawn(host) {
  const R = rcon(host);
  await R.cmd('execute in minecraft:overworld run forceload add ~ ~');
  await sleep(2000);
  await R.cmd('execute in minecraft:overworld run summon minecraft:marker ~ ~ ~ {Tags:["botfarm_sp"]}');
  const r = await R.cmd('data get entity @e[type=minecraft:marker,tag=botfarm_sp,limit=1] Pos');
  await R.cmd('minecraft:kill @e[type=minecraft:marker,tag=botfarm_sp]');
  await R.cmd('execute in minecraft:overworld run forceload remove ~ ~');
  const m = r.match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/);
  if (m) SPAWN[host] = { x: +m[1], z: +m[3] };
}
const atSpawn = (host, p) => !!(p && SPAWN[host] && Math.hypot(p.x - SPAWN[host].x, p.z - SPAWN[host].z) <= 16);
setTimeout(function spawns() {
  Promise.all(Object.keys(SERVERS).filter(h => !SPAWN[h]).map(h => learnSpawn(h).catch(() => {})))
    .then(() => { if (Object.keys(SERVERS).some(h => !SPAWN[h])) setTimeout(spawns, 60000); });
}, 5000);

async function serverPos(name, host) {
  const r = await rcon(host).cmd(`data get entity ${name} Pos`);
  const m = r.match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/);
  return m ? { x: +m[1], y: +m[2], z: +m[3] } : null;
}

/** Chỗ đứng được gần (x, y, z): trong 6 ô ngang, trên mặt đất thật (độ cao lấy tại chỗ), sàn khô (không nước / dung nham / lá), lệch ≥2 ô khỏi điểm gốc. */
async function ground(host, x, y, z) {
  const R = rcon(host);
  const bx = Math.floor(x), by = Math.floor(y), bz = Math.floor(z);
  await R.cmd(`execute in minecraft:overworld run forceload add ${bx - 8} ${bz - 8} ${bx + 8} ${bz + 8}`);
  for (let k = 0; k < 20; k++) {   // khu xa nạp chậm: chưa nạp xong thì lệnh kiểm khối luôn trượt
    await sleep(500);
    if ((await R.cmd(`execute in minecraft:overworld if loaded ${bx - 8} ${by} ${bz - 8} if loaded ${bx + 8} ${by} ${bz + 8}`)).includes('passed')) break;
  }
  const cand = [];
  for (let dx = -6; dx <= 6; dx += 2) for (let dz = -6; dz <= 6; dz += 2) if (Math.abs(dx) + Math.abs(dz) >= 2) cand.push([dx, dz]);   // thưa 2 ô: ~300 lệnh / khu
  cand.sort((a, b) => Math.hypot(a[0], a[1]) - Math.hypot(b[0], b[1]));
  let out = null;
  for (const [dx, dz] of cand) {
    const X = bx + dx, Z = bz + dz;
    // 27/10: độ cao điểm warp có cái của map cũ (Thành Cổ ghi y 85, đất mới ~72): lấy độ cao mặt đất thật bằng marker thả lên khối cao
    // nhất (không tính lá; nước tính là mặt nên đứng trên nước sẽ bị loại ở bước dưới)
    await R.cmd(`execute in minecraft:overworld positioned ${X}.5 0 ${Z}.5 positioned over motion_blocking_no_leaves run summon minecraft:marker ~ ~ ~ {Tags:["botfarm_y"]}`);
    const r = await R.cmd(`data get entity @e[type=minecraft:marker,tag=botfarm_y,limit=1] Pos[1]`);
    // minecraft:kill: "kill" trơn là lệnh của Essentials (chỉ nhận người chơi) -> marker không bị xoá, sót lại hàng nghìn
    // và lần dò sau đọc nhầm độ cao marker cũ (27/10)
    await R.cmd(`minecraft:kill @e[type=minecraft:marker,tag=botfarm_y]`);
    const m = r.match(/(-?[\d.]+)d/);
    if (!m) continue;
    const Y = Math.round(+m[1]);
    if (Y < by - 40 || Y > by + 20) continue;   // vực sâu / đỉnh núi: bỏ
    // sàn: khối đặc, không phải nước / dung nham / lá
    const ok = await R.cmd(`execute in minecraft:overworld unless block ${X} ${Y - 1} ${Z} #minecraft:replaceable unless block ${X} ${Y - 1} ${Z} #minecraft:leaves unless block ${X} ${Y} ${Z} minecraft:water`);
    if (ok.includes('passed')) { out = { x: X + 0.5, y: Y, z: Z + 0.5 }; break; }
  }
  await R.cmd(`execute in minecraft:overworld run forceload remove ${bx - 8} ${bz - 8} ${bx + 8} ${bz + 8}`);
  return out;
}

/** Khu ngẫu nhiên chưa bot nào đứng (Bang Hội Chiến), có chỗ khô ráo; null nếu hết khu trống. */
async function randomHome(name) {
  const taken = NAMES.filter(n => n !== name).map(n => state[n].home['banghoi-1']).filter(Boolean);
  const cur = state[name].home['banghoi-1'];
  const free = SPOTS.filter(([x, , z]) => !taken.some(h => Math.hypot(h.x - x, h.z - z) < 12) && !(cur && Math.hypot(cur.x - x, cur.z - z) < 12));
  for (let k = free.length - 1; k > 0; k--) { const j = Math.floor(Math.random() * (k + 1)); [free[k], free[j]] = [free[j], free[k]]; }
  for (const [x, y, z] of free.slice(0, 10)) {
    const g = await ground('banghoi-1', x, y, z);
    if (g) return g;
  }
  // hết điểm gợi ý (27/10: 20 bot > số điểm): đứng cạnh một điểm đã có bot, lệch 4–6 ô (chủ server: 2 bot gần nhau cũng được).
  // Toạ độ ngẫu nhiên khắp vùng /tpr hay rơi xuống biển (map quần đảo) nên không dùng; bỏ 2 điểm sát spawn.
  const all = NAMES.filter(n => n !== name).map(n => state[n].home['banghoi-1']).filter(Boolean);
  const near = SPOTS.filter(([x, , z]) => Math.hypot(x - 12706, z + 2430) > 30);
  for (let k = near.length - 1; k > 0; k--) { const j = Math.floor(Math.random() * (k + 1)); [near[k], near[j]] = [near[j], near[k]]; }
  for (const [x, y, z] of near.slice(0, 8)) {
    const sx = Math.random() < 0.5 ? -1 : 1, sz = Math.random() < 0.5 ? -1 : 1;
    const g = await ground('banghoi-1', x + sx * (4 + Math.floor(Math.random() * 3)), y, z + sz * (4 + Math.floor(Math.random() * 3)));
    if (g && !all.some(h => Math.hypot(h.x - g.x, h.z - g.z) < 3)) return g;
  }
  return null;
}

/** Chỗ mặc định ở Bang Hội Chiến: điểm gợi ý chưa bot nào đứng gần (≥12 ô), có chỗ khô ráo. Chọn lần lượt từng bot (khoá),
 *  ghi ngay vào trạng thái, để 2 bot vào cùng lúc không chọn trùng chỗ. */
let homeLock = Promise.resolve();
function pickHome(name) {
  const run = homeLock.then(async () => {
    if (state[name].home['banghoi-1']) return state[name].home['banghoi-1'];
    // hết điểm gợi ý trống (bot tạo thêm): ra khu ngẫu nhiên như nút "Ngẫu nhiên", khỏi đứng mãi ở spawn
    const h = await pickHomeNow(name) || await randomHome(name);
    if (h) { state[name].home['banghoi-1'] = h; save(); }
    return h;
  });
  homeLock = run.catch(() => null);
  return run;
}
async function pickHomeNow(name) {
  const taken = NAMES.filter(n => n !== name).map(n => state[n].home['banghoi-1']).filter(Boolean);
  const bad = state[name].bad || [];
  for (const [x, y, z] of SPOTS) {
    if (taken.some(h => Math.hypot(h.x - x, h.z - z) < 12) || bad.some(b => Math.hypot(b[0] - x, b[1] - z) < 4)) continue;
    const g = await ground('banghoi-1', x, y, z);
    if (g) return g;
    state[name].bad = [...bad, [x, z]];
  }
  return null;
}

// ---------------------------------------------------------------- bot

function make(name, host, i) {
  // keepAlive tự trả lời ngay = ping 0 ms trong Tab, trông giả: tự trả lời sau 45–60 ms (ping ~52 ms như người chơi trong nước)
  // viewDistance tiny (2 chunk): bot đứng yên, không cần thấy xa. Mặc định 'far' (12, server cắt còn 8) thì 22 bot giữ ~6.000 chunk
  // trong RAM, botfarm phình tới 4 GB rồi sập (27/10 22:51, "JavaScript heap out of memory"); server cũng đỡ gửi chunk.
  const bot = mineflayer.createBot({ host, port: 25565, username: name, version: '1.21.4', auth: 'offline', hideErrors: true, keepAlive: false, viewDistance: 'tiny' });
  // Trả lời đúng thứ tự nhận: Paper ngắt kết nối khi câu trả lời sai thứ tự ("keepalive response out-of-order" -> "Timed out");
  // 27/10: trễ ngẫu nhiên từng gói làm 2 gói sát nhau đảo thứ tự, bot rớt ~3 lần / phút. Xếp hàng: gói sau chỉ gửi sau gói trước.
  let kaQ = Promise.resolve();
  bot._client.on('keep_alive', p => {
    const due = Date.now() + 45 + Math.random() * 15;
    kaQ = kaQ.then(() => sleep(Math.max(0, due - Date.now()))).then(() => { try { bot._client.write('keep_alive', { keepAliveId: p.keepAliveId }); } catch (e) { /* bỏ qua */ } });
  });
  bot._client.removeAllListeners('login_plugin_request');
  bot._client.on('login_plugin_request', p => {
    if (p.channel !== 'velocity:player_info') return bot._client.write('login_plugin_response', { messageId: p.messageId });
    const varint = n => { const b = []; do { let t = n & 0x7f; n >>>= 7; if (n) t |= 0x80; b.push(t); } while (n); return Buffer.from(b); };
    const str = s => { const d = Buffer.from(s, 'utf8'); return Buffer.concat([varint(d.length), d]); };
    const h = crypto.createHash('md5').update('OfflinePlayer:' + name).digest(); h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80;
    const body = Buffer.concat([varint(1), str(`10.30.${i}.${10 + i}`), h, str(name), varint(0)]);   // mỗi bot một IP giả (khỏi bị coi là cùng máy)
    const sig = crypto.createHmac('sha256', SECRET).update(body).digest();
    bot._client.write('login_plugin_response', { messageId: p.messageId, data: Buffer.concat([sig, body]) });
  });
  return bot;
}

async function connect(name) {
  const rt = RT[name], st = state[name], host = st.server, i = ipIndex(name);
  rt.connecting = true; rt.err = '';
  const bot = make(name, host, i);
  let tick = null, first = true;
  bot.on('error', e => { rt.err = e.message; });
  bot.on('kicked', r => { const t = typeof r === 'string' ? r : JSON.stringify(r); rt.err = 'bị đá: ' + t.replace(/\{"text":|"|\}/g, '').slice(0, 160); event({ type: 'kick', bot: name, text: rt.err }); });
  bot.on('end', () => {
    clearInterval(tick);
    if (rt.bot === bot) rt.bot = null;
    rt.connecting = false;
    rt.retryAt = Date.now() + (rt.fast ? 2500 : 30000 + i * 1500);
    rt.fast = false;
    log(name, 'thoát', rt.err ? '(' + rt.err + ')' : '');
  });
  bot.on('messagestr', (msg, pos) => {
    const m = String(msg);
    if (host === 'lobby' && BOT_PASS) {   // AuthMe ở sảnh: tự đăng ký / đăng nhập
      if (/\/register/i.test(m)) bot.chat(`/register ${BOT_PASS} ${BOT_PASS}`);
      else if (/\/login/i.test(m)) bot.chat(`/login ${BOT_PASS}`);
    }
    let w = m.match(/^\[(.+?) -> (.+?)\] (.*)$/);
    if (w && w[1] !== name && !/^(tôi|me)$/i.test(w[1])) { event({ type: 'msg', bot: name, server: host, from: w[1], text: w[3] }); return; }
    w = m.match(/^(\S+) đã yêu cầu (dịch chuyển đến chỗ bạn|bạn dịch chuyển đến chỗ họ)/);
    if (w) { event({ type: 'tpa', bot: name, server: host, from: w[1], text: m }); autoTpa(name, w[1]); return; }
    if (/đã (tự động )?(đồng ý|chấp nhận) yêu cầu dịch chuyển của bạn|Đang bắt đầu dịch chuyển/.test(m) && RT[name].fakeTpa) { dodgeTeleport(name); return; }
    if (pos === 'chat' && m.toLowerCase().includes(name.toLowerCase()) && !m.includes(name + ':') && !m.includes(name + ' »')) event({ type: 'nhac', bot: name, server: host, text: m.slice(0, 240) });
  });
  bot.on('death', () => { rt.deaths.push(Date.now()); log(name, 'chết'); event({ type: 'chet', bot: name, server: host, text: name + ' chết' }); });
  bot.on('spawn', async () => {
    if (first) {
      first = false;
      rt.bot = bot; rt.connecting = false;
      tick = setInterval(() => { try { bot._client.write('tick_end', {}); } catch (e) { /* bỏ qua */ } }, 50);   // Grim TickTimer (client 1.21.2+)
      log(name, 'vào', SERVERS[host] || host);
      // bất tử (Essentials /god) + chế độ Sáng tạo: bot đứng ngoài đất hoang ban đêm bị quái giết liên tục (27/10); Sáng tạo thì quái không
      // nhắm tới, người chơi đánh không mất máu / không bị đẩy (Tab không hiện chế độ chơi nên member không nhận ra)
    }
    rt.wander = null;
    rt.placing = true; rt.placedAt = Date.now();   // khỏi bị vòng "bị dời" nhận nhầm chỗ vào game (spawn) làm chỗ đứng
    await sleep(2500);
    if (!st.silent) {   // vào / ra không báo [+] [-] (DuckyMoMoCore DonateRanks: duckymomo.vaora.an), cấp một lần
      rcon(host).cmd(`lp user ${name} permission set duckymomo.vaora.an true`).then(() => { st.silent = true; save(); }).catch(() => {});
    }
    if (!st.grim) {   // 29/10 (chủ server đồng ý): GrimAC kéo bot lùi mỗi bước khi đi dạo + báo admin "failed Simulation" = lộ bot
      rcon(host).cmd(`lp user ${name} permission set grim.exempt true`).then(() => { st.grim = true; save(); }).catch(() => {});
    }
    creative(name, host).catch(() => {});   // mỗi lần xuất hiện: Sinh tồn, tắt /god (29/10)
    place(name, bot).catch(e => log(name, 'lỗi đặt chỗ:', e.message)).finally(() => { rt.placing = false; rt.placedAt = Date.now(); });
  });
  setTimeout(() => { if (rt.connecting && !rt.bot) { rt.err = 'vào không được (quá 40 giây)'; try { bot.quit(); } catch (e) { /* bỏ qua */ } rt.connecting = false; rt.retryAt = Date.now() + 60000; } }, 40000);
}

/** Sinh tồn, không /god (29/10 chủ server: bot ở phòng ẩn kín + chỉ ra khu an toàn spawn / trade / shop, không cần Sáng tạo + bất tử
 *  nữa; trước đó 27/10 bot đứng đất hoang bị quái giết nên đặt Sáng tạo). Đọc lại chế độ chơi cho chắc (thử tối đa 3 lần). */
async function creative(name, host) {
  const R = rcon(host);
  for (let i = 0; i < 3; i++) {
    await R.cmd(`gamemode survival ${name}`);
    await R.cmd(`god ${name} off`);
    if (/data: 0$/.test((await R.cmd(`data get entity ${name} playerGameType`)).trim())) return;
    await sleep(2000);
  }
  log(name, 'không chuyển được Sinh tồn');
}

/** Đưa bot về chỗ đang đứng (at) hoặc chỗ mặc định (home); chết 2 lần trong 3 phút sau khi đặt ở một chỗ thì bỏ chỗ đó. */
async function place(name, bot) {
  const rt = RT[name], st = state[name], host = st.server;
  if (rt.bot !== bot) return;
  // 29/10: đã gom vào phòng ẩn (homeOld có) thì chỗ mặc định LUÔN là phòng: lệch (chưa rõ chỗ nào xoá) thì đặt lại + ghi log để tìm
  if (host === 'banghoi-1' && st.homeOld !== undefined) {
    const r = roomSpot(NAMES.indexOf(name)), h = st.home[host];
    if (!h || Math.hypot(h.x - r.x, h.z - r.z) > 8 || Math.abs(h.y - r.y) > 4) {
      log(name, 'chỗ mặc định lệch khỏi phòng ẩn:', JSON.stringify(h || null), '-> đặt lại phòng');
      st.home[host] = r; delete st.at[host]; save();
    }
    rt.deaths = [];   // phòng ẩn an toàn: không áp luật "chết 2 lần thì bỏ chỗ"
  }
  const recent = rt.deaths.filter(t => Date.now() - t < 180000);
  if (recent.length >= 2) {
    rt.deaths = [];
    const tg = st.at[host] || st.home[host];
    if (tg) { st.bad = [...(st.bad || []), [tg.x, tg.z]]; }
    delete st.at[host];
    if (st.home[host] && tg && Math.hypot(st.home[host].x - tg.x, st.home[host].z - tg.z) < 3) delete st.home[host];
    save();
    event({ type: 'chet', bot: name, server: host, text: name + ' chết liên tục ở một chỗ, bỏ chỗ đó' });
  }
  if (host === 'banghoi-1' && !st.home[host]) {
    const h = await pickHome(name);
    if (h) { st.home[host] = h; save(); }
  }
  const tg = st.at[host] || st.home[host];
  if (!tg) return;
  const p = await serverPos(name, host);
  if (!p || Math.hypot(p.x - tg.x, p.z - tg.z) <= 3) return;
  const R = rcon(host);
  if (tg.y == null) {
    await R.cmd(`execute in minecraft:overworld run forceload add ${Math.floor(tg.x)} ${Math.floor(tg.z)}`); await sleep(1500);
    await R.cmd(`execute in minecraft:overworld positioned ${tg.x} 0 ${tg.z} positioned over motion_blocking_no_leaves run minecraft:tp ${name} ~ ~ ~`);
    await R.cmd(`execute in minecraft:overworld run forceload remove ${Math.floor(tg.x)} ${Math.floor(tg.z)}`);
    const np = await serverPos(name, host);
    if (np) { st.at[host] = np; save(); }
  } else {
    await R.cmd(`execute in minecraft:overworld run minecraft:tp ${name} ${tg.x} ${tg.y} ${tg.z}`);
  }
  rt.placedAt = Date.now();   // 27/10: bot tự theo lệnh tp (đã thử), không cần thoát ra vào lại
}

/** Bot vừa chạy lệnh có thể dịch chuyển (spawn, tpaccept, warp…): đợi, vị trí phía server đổi thì nhận làm chỗ đang đứng + vào lại cho khớp. */
async function followTeleport(name, before) {
  await sleep(8000);
  const rt = RT[name], st = state[name];
  if (!rt.bot || !before) return;
  const p = await serverPos(name, st.server);
  if (p && Math.hypot(p.x - before.x, p.z - before.z) > 3) {
    st.at[st.server] = p; save();
    log(name, 'đã dịch chuyển tới', Math.floor(p.x), Math.floor(p.y), Math.floor(p.z));
  }
}

// Bot bị dời bằng lệnh ngoài (admin /tp ở console, /tphere…): nhận chỗ mới làm chỗ đang đứng, vào lại cho client khớp toạ độ.
// Không thì lần vào lại sau (restart, rớt mạng) dịch vụ kéo bot về chỗ cũ đã lưu.
setInterval(async () => {
  for (const n of NAMES) {
    const rt = RT[n], st = state[n];
    if (!rt.bot || rt.placing || rt.wander || Date.now() - rt.placedAt < 20000) continue;
    const tg = st.at[st.server] || st.home[st.server];
    const p = await serverPos(n, st.server).catch(() => null);
    if (!p || (tg && Math.hypot(p.x - tg.x, p.z - tg.z) <= 3)) continue;
    if (!tg && st.server !== 'banghoi-1') continue;   // server khác chưa có chỗ: đứng đâu cũng được
    st.at[st.server] = p; save();
    log(n, 'bị dời tới', Math.floor(p.x), Math.floor(p.y), Math.floor(p.z), '-> nhận làm chỗ đang đứng');
    event({ type: 'doi', bot: n, server: st.server, text: n + ' được dời tới ' + Math.floor(p.x) + ' ' + Math.floor(p.y) + ' ' + Math.floor(p.z) });
    rt.placedAt = Date.now();
  }
}, 20000);

// vòng giữ: bot bật thì vào (thử lại khi rớt), bot tắt thì thoát. Vào rải rác: mỗi lần 1 bot, cách nhau 8–20 giây
// (27/10, chủ server: đừng để cả loạt thoát / vào cùng lúc)
let nextJoin = 0;
// Tắt dịch vụ (docker stop / dựng lại): KHÔNG cho mọi bot thoát cùng một giây (28/10: 21 bot "mất kết nối" cùng lúc 14:52:43 khi có người
// thật online = lộ bot). Bot thoát dần từng con, thứ tự ngẫu nhiên, cách 3–15 giây; compose stop_grace_period đủ dài để kịp.
let stopping = false;
/** Người THẬT đang online (thấy trong danh sách người chơi của bất kỳ bot nào, trừ tên bot). */
function realOnline() {
  const seen = new Set();
  for (const n of NAMES) {
    const b = RT[n].bot;
    if (!b || !b.players) continue;
    for (const x of Object.keys(b.players)) if (!NAMES.includes(x)) seen.add(x);
  }
  return [...seen];
}

async function gracefulStop(sig) {
  if (stopping) return;
  stopping = true;
  const on = NAMES.filter(n => RT[n].bot).sort(() => Math.random() - 0.5);
  const real = realOnline();
  // 29/10 chủ server: không có người thật thì thoát hết luôn; có người chơi mới thoát dần (không lộ bot)
  log('nhận', sig, '-', on.length, 'bot', real.length ? 'thoát dần (đang có ' + real.join(', ') + ')' : 'thoát hết (không có người thật)');
  for (const n of on) {
    try { RT[n].bot.quit(); } catch (e) { /* bỏ qua */ }
    if (real.length) await sleep(3000 + Math.random() * 12000);
  }
  await sleep(1500);
  process.exit(0);
}
process.on('SIGTERM', () => gracefulStop('SIGTERM'));
process.on('SIGINT', () => gracefulStop('SIGINT'));
setInterval(() => {
  if (stopping) return;
  for (const n of NAMES) {
    if (!state[n].want && RT[n].bot) { RT[n].fast = false; RT[n].bot.quit(); }
  }
  if (Date.now() < nextJoin) return;
  const n = NAMES.find(x => state[x].want && !RT[x].bot && !RT[x].connecting && Date.now() >= RT[x].retryAt);
  if (!n) return;
  const rt = RT[n];
  nextJoin = Date.now() + (rt.fast || !realOnline().length ? 2000 : 8000 + Math.random() * 12000);   // không có người thật: vào nhanh
  connect(n).catch(e => { rt.err = e.message; rt.connecting = false; });
}, 1000);

// Dơi tích dần trong hang quanh chỗ bot đứng (27/10: 4.400 con, riêng quanh Bao_Khang 2.188; vượt xa giới hạn 15 / người, chưa rõ vì
// sao). 10 phút một lần xoá dơi trong 160 ô quanh mỗi bot, trừ dơi trong 48 ô quanh người thật (khỏi thấy dơi biến mất).
setInterval(async () => {
  const by = {};
  for (const n of NAMES) if (RT[n].bot) (by[state[n].server] = by[state[n].server] || []).push(n);
  for (const [host, bots] of Object.entries(by)) {
    const R = rcon(host);
    try {
      const real = '@a[' + NAMES.map(n => 'name=!' + n).join(',') + ']';
      await R.cmd(`execute as ${real} at @s run minecraft:tag @e[type=minecraft:bat,distance=..48] add botfarm_keep`);
      let k = 0;
      for (const n of bots) {
        const r = await R.cmd(`execute as ${n} at @s run minecraft:kill @e[type=minecraft:bat,distance=..160,tag=!botfarm_keep]`);
        const m = r.match(/Killed (\d+) entities/); k += m ? +m[1] : /Killed /.test(r) ? 1 : 0;   // 1 con: "Killed Bat"
      }
      await R.cmd('minecraft:tag @e[type=minecraft:bat,tag=botfarm_keep] remove botfarm_keep');
      if (k) log('xoá', k, 'dơi quanh bot ở', SERVERS[host] || host);
    } catch (e) { log('lỗi xoá dơi:', e.message); }
  }
}, 600000);

// ---------------------------------------------------------------- bot tự chat với nhau (27/10, bật / tắt ở trang Bot)
// Cứ min–max phút chọn 2–3 bot đang cùng một server, nói một đoạn hội thoại soạn sẵn (gõ chậm như người, mỗi bot một kiểu gõ).
// Nội dung chỉ chuyện vặt / mẹo đúng của server (lệnh lấy theo huongdan.yml): không hẹn gặp, không rủ đi đâu, không câu hỏi bỏ lửng
// (người thật trả lời thì bot không đáp được), không nói gì về nạp tiền. KHÔNG câu gắn với giờ / ngày / thời tiết thật (ăn cơm, đêm,
// mưa, mai on…): nói sai lúc là lộ bot (27/10, chủ server). Đêm khuya (1–7 giờ) im.
const CHAT_CFG = '/state/chat.json';
const chatCfg = { on: false, min: 4, max: 9 };
try { Object.assign(chatCfg, JSON.parse(fs.readFileSync(CHAT_CFG, 'utf8'))); } catch (e) { /* chưa cài: tắt */ }
function saveChat() { try { fs.writeFileSync(CHAT_CFG, JSON.stringify(chatCfg)); } catch (e) { log('không lưu được chat.json:', e.message); } }
// Bot tự trả lời TPA (28/10, chủ server: "cả tp deny nữa"; "không được nhận, nhận thì người chơi biết là bot"): KHÔNG BAO GIỜ nhận.
// Mỗi bot một tính (state[n].tpa.tuchoi): sau 4–25 giây /tpdeny, còn lại lơ cho lời mời tự hết hạn. Bật/tắt chung: /state/tpa.json.
// Bot thỉnh thoảng /tpa một người chơi THẬT rồi tự /tpacancel (28/10, chủ server: "tpa người chơi ngẫu nhiên rồi tự huỷ, không thực sự tới
// người chơi để bị phát hiện, đừng tpa liên tục không lại bị nghi"). Hiếm: cả server ~1 lần / 40–90 phút, đêm 1–7 giờ im; một người thật
// tối đa 1 lần / 3 giờ, một bot 1 lần / 4 giờ. Người kia lỡ bấm nhận: Essentials chờ 5 giây, bot nhảy + /tpacancel là huỷ (dodgeTeleport).
const FTPA_CFG = '/state/tpa-gia.json';
const ftpaCfg = { on: false };
try { Object.assign(ftpaCfg, JSON.parse(fs.readFileSync(FTPA_CFG, 'utf8'))); } catch (e) { /* chưa cài: tắt */ }
function saveFtpa() { try { fs.writeFileSync(FTPA_CFG, JSON.stringify(ftpaCfg)); } catch (e) { log('không lưu được tpa-gia.json:', e.message); } }
const ftpaLast = { bot: {}, nguoi: {} };   // tên -> lúc tpa gần nhất
let ftpaNext = Date.now() + (20 + Math.random() * 40) * 60000;
function dodgeTeleport(name) {
  const rt = RT[name], b = rt.bot; if (!b) return;
  try {
    b.setControlState('jump', true); b.setControlState('forward', true);
    setTimeout(() => { try { b.setControlState('jump', false); b.setControlState('forward', false); b.chat('/tpacancel'); } catch (e) { /* bỏ qua */ } }, 700);
  } catch (e) { /* bỏ qua */ }
  log(name, 'né dịch chuyển (người kia đã nhận TPA giả)');
}
function fakeTpaOnce() {
  const h = new Date().getHours();
  if (h >= 1 && h < 7) return 'đêm: không tpa';
  const now = Date.now();
  const bots = NAMES.filter(n => RT[n].bot && state[n].server === 'banghoi-1' && now - (ftpaLast.bot[n] || 0) > 4 * 3600e3).sort(() => Math.random() - 0.5);
  for (const n of bots) {
    const b = RT[n].bot;
    const real = Object.keys(b.players || {}).filter(x => !NAMES.includes(x) && /^[A-Za-z0-9_.]{3,16}$/.test(x) && now - (ftpaLast.nguoi[x.toLowerCase()] || 0) > 3 * 3600e3);
    if (!real.length) continue;
    const who = real[Math.floor(Math.random() * real.length)];
    ftpaLast.bot[n] = now; ftpaLast.nguoi[who.toLowerCase()] = now;
    RT[n].fakeTpa = true;
    b.chat('/tpa ' + who);
    event({ type: 'tpa', bot: n, from: who, text: `${n} gửi /tpa tới ${who} (sẽ tự huỷ)` });
    setTimeout(() => {
      try { if (RT[n].bot) RT[n].bot.chat('/tpacancel ' + who); } catch (e) { /* bỏ qua */ }
      setTimeout(() => { RT[n].fakeTpa = false; }, 8000);   // còn canh thêm vài giây phòng người kia nhận sát lúc huỷ
    }, 3000 + Math.random() * 12000);
    return `${n} /tpa ${who} rồi tự huỷ`;
  }
  return 'không có người thật nào phù hợp';
}
setInterval(() => {
  if (stopping || !ftpaCfg.on || Date.now() < ftpaNext) return;
  ftpaNext = Date.now() + (40 + Math.random() * 50) * 60000;
  log('tpa giả:', fakeTpaOnce());
}, 60000);
const TPA_CFG = '/state/tpa.json';
const tpaCfg = { on: false };
try { Object.assign(tpaCfg, JSON.parse(fs.readFileSync(TPA_CFG, 'utf8'))); } catch (e) { /* chưa cài: tắt */ }
function saveTpa() { try { fs.writeFileSync(TPA_CFG, JSON.stringify(tpaCfg)); } catch (e) { log('không lưu được tpa.json:', e.message); } }
function tpaTrait(n) {
  if (!state[n].tpa || state[n].tpa.tuchoi == null) { state[n].tpa = { tuchoi: +(0.4 + Math.random() * 0.5).toFixed(2) }; save(); }
  return state[n].tpa;
}
function autoTpa(name, from) {
  if (!tpaCfg.on) return;
  const t = tpaTrait(name), rt = RT[name];
  if (Math.random() >= t.tuchoi) { log(name, 'lơ TPA của', from); return; }   // lơ: lời mời tự hết hạn
  setTimeout(() => {
    if (!rt.bot) return;
    rt.bot.chat('/tpdeny');
    event({ type: 'tpa', bot: name, from, text: `${name} tự từ chối TPA của ${from}` });
  }, 4000 + Math.random() * 21000);
}
// Lời thoại: talks.js. 28/10 chủ server: tầm 5 tiếng mới lặp lại một đoạn → nhớ đoạn đã nói (chat-used.json), chỗ trống {CA} {VUNG}… điền ngẫu nhiên
const { TALKS, SOLO, SLOTS } = require('./talks');
const CHAT_USED = '/state/chat-used.json';
let chatUsed = {};
try { chatUsed = JSON.parse(fs.readFileSync(CHAT_USED, 'utf8')); } catch (e) { /* chưa có */ }
const talkKey = t => t.join('|');
function pickFresh(list, key) {
  const now = Date.now(), gap = (chatCfg.norepeat || 5) * 3600e3;
  const fresh = list.filter(t => now - (chatUsed[key(t)] || 0) > gap);
  const pool = fresh.length ? fresh : list.slice().sort((x, y) => (chatUsed[key(x)] || 0) - (chatUsed[key(y)] || 0)).slice(0, Math.max(1, Math.ceil(list.length / 10)));
  const t = pool[Math.floor(Math.random() * pool.length)];
  chatUsed[key(t)] = now;
  for (const k of Object.keys(chatUsed)) if (now - chatUsed[k] > 48 * 3600e3) delete chatUsed[k];   // dọn đoạn đã xoá khỏi talks.js
  try { fs.writeFileSync(CHAT_USED, JSON.stringify(chatUsed)); } catch (e) { log('không lưu được chat-used.json:', e.message); }
  return t;
}
function fillSlots(lines) {
  const val = {};
  return lines.map(l => l.replace(/\{(CA|QUANG|VUNG|SO|MON)\}/g, (_, k) => val[k] || (val[k] = SLOTS[k][Math.floor(Math.random() * SLOTS[k].length)])));
}
const STYLE = n => [...n].reduce((s, c) => s + c.charCodeAt(0), 0) % 3;   // 0 có dấu, 1 không dấu, 2 có dấu + viết thường hết
const noAccent = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D');
const styled = (n, s) => STYLE(n) === 1 ? noAccent(s) : STYLE(n) === 2 ? s.toLowerCase() : s;
const vnHour = () => (new Date().getUTCHours() + 7) % 24;
let chatBusy = false;

async function chatOnce() {
  if (chatBusy) return 'đang có một đoạn chat dở';
  // server có nhiều bot online nhất (thường là Bang Hội Chiến)
  const by = {};
  for (const n of NAMES) if (RT[n].bot && !RT[n].placing) (by[state[n].server] = by[state[n].server] || []).push(n);
  const srv = Object.keys(by).sort((a, b) => by[b].length - by[a].length)[0];
  const pool = srv ? by[srv].slice() : [];
  if (!pool.length) return 'không có bot nào online';
  for (let k = pool.length - 1; k > 0; k--) { const j = Math.floor(Math.random() * (k + 1)); [pool[k], pool[j]] = [pool[j], pool[k]]; }
  const fit = TALKS.filter(t => new Set(t.map(l => l[0])).size <= pool.length);
  const talk = fillSlots(pool.length >= 2 && Math.random() < 0.85 && fit.length ? pickFresh(fit, talkKey) : ['A:' + pickFresh(SOLO, x => 'solo|' + x)]);
  const who = { A: pool[0], B: pool[1], C: pool[2] };
  chatBusy = true;
  try {
    let prev = null;
    for (const line of talk) {
      const n = who[line[0]], text = line.slice(2).replace(/\{([ABC])\}/g, (_, x) => who[x] || '');
      // 29/10 (chủ server: bot trả lời nhau nhanh quá): người khác đáp = đọc + nghĩ 3–9 giây, 30% đang bận (đánh quái, đào) thêm
      // 8–25 giây; cùng người nói tiếp 1–2 giây; gõ ~4 ký tự / giây (±20%)
      const react = prev === null ? 0 : n === prev ? 800 + Math.random() * 1500
        : 3000 + Math.random() * 6000 + (Math.random() < 0.3 ? 8000 + Math.random() * 17000 : 0);
      await sleep(react + Math.min(text.length * 250, 15000) * (0.8 + Math.random() * 0.4));
      prev = n;
      if (!RT[n] || !RT[n].bot) break;
      const msg = styled(n, text);
      RT[n].bot.chat(msg.startsWith('/') ? '.' + msg : msg);   // "./sell …": khoe lệnh trong chat như người thật, không chạy thành lệnh (28/10)
    }
  } finally { chatBusy = false; }
  return 'bot chat ở ' + (SERVERS[srv] || srv) + ': ' + talk.length + ' câu';
}

(function chatLoop() {
  const wait = (chatCfg.min + Math.random() * Math.max(0, chatCfg.max - chatCfg.min)) * 60000;
  setTimeout(() => {
    const h = vnHour();
    if (chatCfg.on && !(h >= 1 && h < 7)) chatOnce().then(r => log(r)).catch(e => log('lỗi bot chat:', e.message));
    chatLoop();
  }, wait);
})();


// ---------------------------------------------------------------- phòng ẩn + đi dạo (29/10)
// Chủ server: "đưa hết bot về spawn đứng nhưng member không nhìn thấy … thi thoảng cho bot chạy loanh quanh spawn, các warp nếu có member ở đấy".
// Bot đứng chung một phòng kín dưới đất xa mọi warp > 128 khối (DuckyMoMoCore bot-hide chặn gói hiện bot trong phòng: không ai thấy, tên
// vẫn ở TAB) → 23 bot dùng chung một vùng chunk. Thỉnh thoảng có người THẬT trong `radius` (35) khối quanh spawn / warp trade / warp shop (chủ
// server: chỉ 3 chỗ này; xuất hiện bằng /spawn /warp, hết lượt về luôn bằng /back như người chơi) thì 1–2 bot ra trong khu đó (cách người ≥ 12 khối), đi lang thang vài phút (đi, nhảy qua chướng ngại, nhìn quanh /
// nhìn người, giả vờ giao dịch với NPC: tới gần, nhìn, vung tay; không bấm mở menu thật), rồi về phòng.
const WANDER_CFG = '/state/wander.json';
const wanderCfg = { on: false, room: { x: 11150.5, y: -41, z: -3099.5 }, everyMin: 4, everyMax: 9, durMin: 90, durMax: 240, maxDur: 600, maxActive: 2, radius: 35,
  walk: false,   // đi lại: cần grim.exempt cho bot (chưa cấp thì Grim kéo lùi mỗi bước + báo admin), tạm chỉ đứng nhìn quanh / NPC sát cạnh
  // lộ trình (29/10 chủ server: "xác định lộ trình … về spawn thì ra npc già làng rồi đi qua các npc khác rồi ra nhìn user rồi back"):
  // npcs theo thứ tự (order 'fixed') hoặc 2–4 NPC gần nhau ('near'); không có NPC thì 2–3 điểm ngẫu nhiên quanh khu; cuối cùng tới nhìn người chơi
  points: [
    { name: 'spawn', cmd: 'spawn', x: 12707, z: -2432, order: 'fixed', npcs: [
      { ten: 'Già Làng', x: 12705.7, y: 85, z: -2441.7 }, { ten: 'Bác Thợ Rèn', x: 12707, y: 85, z: -2425 }, { ten: 'Người Gác Cổng', x: 12699, y: 85, z: -2433 }] },
    { name: 'trade', cmd: 'warp trade', x: 12689, z: -2626, order: 'near', npcs: [
      { ten: 'Rèn Chiến Binh', x: 12680.5, y: 70.5, z: -2638.5 }, { ten: 'Rèn Thợ Săn', x: 12721.5, y: 70.5, z: -2655.5 }, { ten: 'Thợ Kim Hoàn', x: 12708.5, y: 71, z: -2632.5 },
      { ten: 'Rèn Tinh Anh', x: 12681.5, y: 71, z: -2636.5 }, { ten: 'Thợ Rèn', x: 12714.5, y: 71, z: -2639.5 }, { ten: 'Rèn Huyền Thoại', x: 12680.5, y: 71, z: -2669.5 },
      { ten: 'Lão Khoáng', x: 12712.5, y: 70.5, z: -2634.5 }, { ten: 'Lão Nông', x: 12700.5, y: 70.5, z: -2631.5 }, { ten: 'Ngư Ông', x: 12705.5, y: 71, z: -2631.5 },
      { ten: 'Lão Săn Quái', x: 12676.5, y: 70.5, z: -2653.5 }, { ten: 'Rèn Tân Thủ', x: 12676.5, y: 70.5, z: -2649.5 }] },
    { name: 'shop', cmd: 'warp shop', x: 12792, z: -2641, order: 'near', npcs: [] }] };
try { const j = JSON.parse(fs.readFileSync(WANDER_CFG, 'utf8')); wanderCfg.on = !!j.on; wanderCfg.walk = !!j.walk; } catch (e) { /* chưa cài: tắt */ }   // chỉ lưu bật/tắt, điểm + phòng theo code
function saveWander() { try { fs.writeFileSync(WANDER_CFG, JSON.stringify({ on: wanderCfg.on, walk: wanderCfg.walk })); } catch (e) { log('không lưu được wander.json:', e.message); } }
function roomSpot(i) { const r = wanderCfg.room; return { x: r.x + (i % 7) - 3, y: r.y, z: r.z + (Math.floor(i / 7) % 7) - 3 }; }   // lưới 7×7 (35 bot, 29/10)
async function gatherRoom() {
  NAMES.forEach((n, i) => {
    const st = state[n];
    if (st.homeOld === undefined) st.homeOld = st.home['banghoi-1'] || null;   // giữ chỗ cũ (trả lại bằng thao tác "trave")
    st.home['banghoi-1'] = roomSpot(i); delete st.at['banghoi-1'];
  });
  save();
  let k = 0;
  const todo = NAMES.filter(n => RT[n].bot && state[n].server === 'banghoi-1' && !RT[n].wander);
  todo.forEach(n => { RT[n].placing = true; });   // vòng "bị dời" không nhận chỗ cũ làm chỗ đang đứng trong lúc chờ tới lượt (29/10)
  for (const n of todo) {
    const rt = RT[n];
    try { if (rt.bot) { await place(n, rt.bot); k++; } } catch (e) { log(n, e.message); } finally { rt.placing = false; rt.placedAt = Date.now(); }
    await sleep(400);
  }
  return 'gom ' + NAMES.length + ' bot vào phòng ẩn (' + k + ' bot đang online đã dời)';
}
async function backHomes() {
  let k = 0;
  for (const n of NAMES) {
    const st = state[n];
    if (st.homeOld === undefined) continue;
    if (st.homeOld) st.home['banghoi-1'] = st.homeOld; else delete st.home['banghoi-1'];
    delete st.homeOld; delete st.at['banghoi-1']; k++;
    if (RT[n].bot && st.server === 'banghoi-1' && !RT[n].wander) { await place(n, RT[n].bot).catch(() => {}); await sleep(400); }
  }
  save();
  return k + ' bot về lại chỗ cũ';
}
// staff (29/10 chủ server: "là staff thì bot không cần diễn"): panel đẩy danh sách tên (nhóm helper/mod/quanly/admin/builder) 5 phút một lần
const STAFF_FILE = '/state/staff.json';
let staffSet = new Set();
try { staffSet = new Set(JSON.parse(fs.readFileSync(STAFF_FILE, 'utf8')).map(x => String(x).toLowerCase())); } catch (e) { /* chưa có */ }
const isStaff = who => staffSet.has(String(who).toLowerCase());
async function wanderOnce() {
  const active = NAMES.filter(n => RT[n].wander).length;
  if (active >= wanderCfg.maxActive) return 'đang có đủ bot đi dạo';
  const R = rcon('banghoi-1'), cands = [];
  for (const who of realOnline().filter(w => !isStaff(w))) {
    const dim = await R.cmd(`data get entity ${who} Dimension`).catch(() => '');
    if (!/overworld/.test(dim)) continue;
    const p = await serverPos(who, 'banghoi-1').catch(() => null);
    if (!p) continue;
    const pt = wanderCfg.points.find(q => Math.hypot(q.x - p.x, q.z - p.z) <= wanderCfg.radius);
    if (pt) cands.push({ who, p, pt });
  }
  if (!cands.length) return 'không có người thật ở spawn / warp';
  const c = cands[Math.floor(Math.random() * cands.length)];
  const idle = NAMES.filter(n => RT[n].bot && state[n].server === 'banghoi-1' && !RT[n].wander && !RT[n].placing).sort(() => Math.random() - 0.5);
  const k = Math.min(idle.length, wanderCfg.maxActive - active, Math.random() < 0.25 ? 2 : 1);
  const out = [];
  for (const n of idle.slice(0, k)) {
    await startWander(n, c); out.push(n);
    await sleep(45000 + Math.random() * 75000);   // 29/10: bot thứ hai ra sau 45–120 giây (cùng /spawn thì rơi đúng một điểm)
  }
  return (out.join(', ') || 'không bot nào') + ' đi dạo ở ' + c.pt.name + ' (có ' + c.who + ')';
}
async function startWander(n, c) {
  // xuất hiện "chính thống" bằng lệnh như người thật (/spawn, /warp trade, /warp shop), rồi chạy lộ trình, hết thì /back
  const rt = RT[n], b = rt.bot;
  rt.wander = { going: true, hard: Date.now() + wanderCfg.maxDur * 1000, area: c.pt.name };
  rt.placedAt = Date.now();
  b.chat('/' + c.pt.cmd);
  let arrived = false;
  for (let i = 0; i < 12 && !arrived; i++) {   // chờ dịch chuyển xong (có thể có thời gian chờ theo danh phận)
    await sleep(1500);
    const p = await serverPos(n, 'banghoi-1').catch(() => null);
    arrived = !!(p && Math.hypot(p.x - c.pt.x, p.z - c.pt.z) < 30);
  }
  if (!arrived) { log(n, 'không tới được', c.pt.name, '- về phòng'); stopWander(n); return; }
  rt.wander.going = false;
  event({ type: 'doi', bot: n, server: 'banghoi-1', text: n + ' /' + c.pt.cmd + ' (đi dạo, gần ' + c.who + ')' });
  runRoute(n, c).catch(e => log(n, 'lộ trình lỗi:', e.message)).finally(() => { if (RT[n].wander) stopWander(n); });
}

/** Đi tới (x, y, z) bằng pathfinder: không đào / đặt khối / nhảy vực, rơi tối đa 2 khối; quá hạn thì bỏ chặng. */
async function walkTo(b, x, y, z, range, ms) {
  if (!b.pathfinder) b.loadPlugin(pathfinder);
  const mv = new Movements(b);
  mv.canDig = false; mv.allowParkour = false; mv.allow1by1towers = false; mv.scafoldingBlocks = []; mv.maxDropDown = 2; mv.allowSprinting = false;
  b.pathfinder.setMovements(mv);
  let done = false;
  await Promise.race([b.pathfinder.goto(new goals.GoalNear(x, y, z, range)).then(() => { done = true; }).catch(() => {}), sleep(ms)]);
  try { b.pathfinder.stop(); b.clearControlStates(); } catch (e) { /* bỏ qua */ }
  return done;
}

/** Đứng trước mục tiêu: nhìn đầu, thỉnh thoảng vung tay (giả vờ giao dịch) trong ms. */
async function faceFor(n, head, ms, swing) {
  const end = Date.now() + ms;
  while (Date.now() < end && RT[n].wander && RT[n].bot) {
    try { await RT[n].bot.lookAt(head); if (swing && Math.random() < 0.35) RT[n].bot.swingArm('right'); } catch (e) { /* bỏ qua */ }
    await sleep(600 + Math.random() * 600);
  }
}

/** Tới gần người chơi (cách ~4 khối), nhìn họ vài giây. */
async function meetPlayer(n, who, alive) {
  const rt = RT[n], find = () => Object.values(rt.bot.entities).find(e => e.type === 'player' && e.username === who);
  const e = find();
  if (!e || !alive()) return;
  await walkTo(rt.bot, e.position.x, e.position.y, e.position.z, 4, 20000);
  const e2 = find();
  if (e2 && alive()) await faceFor(n, e2.position.offset(0, 1.6, 0), 3000 + Math.random() * 3000, false);
}

async function runRoute(n, c) {
  const rt = RT[n], alive = () => rt.wander && rt.bot && Date.now() < rt.wander.hard;
  const V = (x, y, z) => rt.bot.entity.position.offset(x - rt.bot.entity.position.x, y - rt.bot.entity.position.y, z - rt.bot.entity.position.z);
  await sleep(1500 + Math.random() * 2500);   // vừa tới: đứng nhìn quanh một chút
  let stops = [];
  if (c.pt.npcs.length && c.pt.order === 'fixed') {   // 29/10 chủ server: không 1 màu, đảo thứ tự; thỉnh thoảng bỏ 1 NPC
    stops = c.pt.npcs.slice().sort(() => Math.random() - 0.5).slice(0, 1 + Math.floor(Math.random() * c.pt.npcs.length));   // 1..hết, không bắt buộc đi hết
  }
  else if (c.pt.npcs.length) {   // 1–4 NPC gần nhau, đi lần lượt cái gần nhất
    let at = rt.bot.entity.position, left = c.pt.npcs.slice();
    const k = Math.min(left.length, 1 + Math.floor(Math.random() * 4));   // 1–4 NPC
    for (let i = 0; i < k; i++) {
      left.sort((a, b) => Math.hypot(a.x - at.x, a.z - at.z) - Math.hypot(b.x - at.x, b.z - at.z));
      // 29/10: NPC đầu chọn ngẫu nhiên trong cả khu (trước luôn là cái gần điểm warp nhất: Lão Nông); sau đó ưu tiên gần, có lệch
      const pick = left.splice(i === 0 ? Math.floor(Math.random() * left.length) : Math.min(Math.floor(Math.random() * 3), left.length - 1), 1)[0];
      stops.push(pick); at = pick;
    }
  } else {   // shop (29/10 chủ server: tìm chỗ bán rồi ra đó thao tác): khung vật phẩm treo tường (chuột trái mua, phải bán)
    const me = rt.bot.entity.position;
    const frames = Object.values(rt.bot.entities).filter(e => /item_frame/.test(e.name || '') && e.position.distanceTo(me) < 20).sort(() => Math.random() - 0.5);
    const kf = 1 + Math.floor(Math.random() * 4);   // 1–4 khung cách nhau ≥ 3 khối
    for (const f of frames) {
      if (stops.length >= kf) break;
      if (stops.every(o => Math.hypot(o.x - f.position.x, o.z - f.position.z) >= 3)) stops.push({ x: f.position.x, y: f.position.y, z: f.position.z, khung: true });
    }
  }
  if (!stops.length) {   // không có NPC / khung: 2–3 điểm quanh khu
    for (let i = 0; i < 2 + Math.floor(Math.random() * 2); i++) {
      const a = Math.random() * Math.PI * 2, d = 4 + Math.random() * 10;
      stops.push({ x: c.pt.x + Math.cos(a) * d, y: rt.bot.entity.position.y, z: c.pt.z + Math.sin(a) * d, diem: true });
    }
  }
  // nhìn người chơi: ~35% lượt, vị trí ngẫu nhiên; trong một khu chỉ một bot làm (29/10: 2 bot cùng ra nhìn user)
  const other = NAMES.some(o => o !== n && RT[o].wander && RT[o].wander.area === c.pt.name && RT[o].wander.meet);
  if (!other && Math.random() < 0.35) {
    rt.wander.meet = true;
    stops.splice(Math.floor(Math.random() * (stops.length + 1)), 0, { nguoi: true });
  }
  for (const st of stops) {
    if (!alive()) return;
    if (st.nguoi) { await meetPlayer(n, c.who, alive); continue; }
    const ok = await walkTo(rt.bot, st.x, st.y, st.z, st.diem ? 2 : 2.5, 25000);
    if (!alive()) return;
    if (st.diem) { await faceFor(n, V(st.x + Math.random() * 6 - 3, st.y + 1.2, st.z + Math.random() * 6 - 3), 2000 + Math.random() * 3000, false); continue; }
    if (st.khung) { if (ok) await faceFor(n, V(st.x, st.y, st.z), 3000 + Math.random() * 4000, true); await sleep(500 + Math.random() * 1000); continue; }   // xem / giả vờ mua (chỉ vung tay, không đánh vào khung)
    if (ok) await faceFor(n, V(st.x, st.y + 1.6, st.z), 4000 + Math.random() * 5000, true);   // giả vờ giao dịch
    await sleep(500 + Math.random() * 1500);
  }
  await sleep(1000 + Math.random() * 2000);
}

/** Phía trước (1–2 khối theo hướng nhìn) đi được không: có nền trong 2 khối dưới chân, không phải nước / dung nham (29/10: bot đi
 *  ra NPC Già Làng rồi bước qua mép quảng trường rơi xuống). */
function safeAhead(b) {
  const p = b.entity.position, yaw = b.entity.yaw;
  const dx = -Math.sin(yaw), dz = -Math.cos(yaw);
  for (const d of [1, 2]) {
    const x = p.x + dx * d, z = p.z + dz * d;
    let ground = false;
    for (let dy = 0; dy >= -3; dy--) {
      const bl = b.blockAt(p.offset(x - p.x, dy, z - p.z));
      if (!bl) return false;   // chưa nạp: coi như nguy hiểm
      if (/water|lava|fire|magma|powder_snow|cactus|sweet_berry/.test(bl.name)) return false;
      if (dy <= -1 && bl.boundingBox === 'block') { ground = dy >= -2; break; }   // nền ở -1 hoặc -2 (bậc thang xuống 1 khối)
    }
    if (!ground) return false;
  }
  return true;
}
/** NPC Citizens: thực thể người chơi không có trong danh sách người chơi (TAB) hoặc UUID bản 2. */
function isNpc(b, e) {
  return e.type === 'player' && e.username && (!b.players[e.username] || (e.uuid && e.uuid[14] === '2'));
}
function stopWander(n) {
  const rt = RT[n], st = state[n];
  const w = rt.wander;
  rt.wander = null;
  try { if (rt.bot) { if (rt.bot.pathfinder) rt.bot.pathfinder.stop(); rt.bot.clearControlStates(); } } catch (e) { /* bỏ qua */ }
  delete st.at['banghoi-1']; save();
  if (!rt.bot) return;
  rt.placing = true;
  (async () => {
    try {
      if (w && !w.going) {   // 29/10 chủ server: về luôn như người chơi: /back = chỗ trước lệnh /spawn /warp, tức phòng ẩn
        rt.bot.chat('/back');
        for (let i = 0; i < 8; i++) {
          await sleep(1500);
          const p = await serverPos(n, 'banghoi-1').catch(() => null);
          const h = st.home['banghoi-1'];
          if (p && h && Math.hypot(p.x - h.x, p.z - h.z) <= 8) return;
        }
      }
      if (rt.bot) await place(n, rt.bot);   // /back không về được phòng thì dời thẳng
    } catch (e) { log(n, e.message); } finally { rt.placing = false; rt.placedAt = Date.now(); }
  })();
}
// (29/10) nhịp đi lang thang ngẫu nhiên đã bỏ: bot đi theo lộ trình runRoute
let wanderNext = Date.now() + 120000;
let wanderSeen = new Set();   // người thật đang ở khu lần kiểm trước: người MỚI tới thì cho bot ra sớm (29/10: đợi 4–9 phút thì người đã đi)
setInterval(async () => {
  if (stopping || !wanderCfg.on) return;
  if (Date.now() < wanderNext) {
    try {
      const now = new Set();   // "người|khu": đổi khu (trade → shop) cũng tính là mới tới (29/10)
      for (const who of realOnline().filter(w => !isStaff(w))) {
        const p = await serverPos(who, 'banghoi-1').catch(() => null);
        const pt = p && wanderCfg.points.find(q => Math.hypot(q.x - p.x, q.z - p.z) <= wanderCfg.radius);
        if (pt) now.add(who + '|' + pt.name);
      }
      const fresh = [...now].some(w => !wanderSeen.has(w));
      wanderSeen = now;
      if (!fresh) return;
    } catch (e) { return; }
  }
  wanderNext = Date.now() + (wanderCfg.everyMin + Math.random() * (wanderCfg.everyMax - wanderCfg.everyMin)) * 60000;
  const h = vnHour();
  if (h >= 1 && h < 7) return;
  wanderOnce().then(r => log('đi dạo:', r)).catch(e => log('lỗi đi dạo:', e.message));
}, 30000);


// ---------------------------------------------------------------- thay phiên online (29/10)
// Chủ server: "35 bot, duy trì đăng nhập tầm 25–31 bot online và thay phiên tránh số lượng online lúc nào cũng giống nhau".
// Mục tiêu ngẫu nhiên trong [min, max] đổi mỗi 15–50 phút (đêm 1–7 giờ nghiêng thấp, tối 19–23 giờ nghiêng cao); mỗi 2–8 phút chỉnh MỘT
// bot (thừa: bot online lâu nhất thoát; thiếu: bot nghỉ lâu nhất vào); đủ rồi thì thỉnh thoảng đổi ca (1 thoát, 1–4 phút sau 1 vào).
// Chỉ bot Bang Hội Chiến; bot đang đi dạo không bị cho thoát. Lưu /state/rotate.json (bật/tắt).
const ROT_CFG = '/state/rotate.json';
const rotCfg = { on: false, min: 25, max: 31 };
try { Object.assign(rotCfg, JSON.parse(fs.readFileSync(ROT_CFG, 'utf8'))); } catch (e) { /* chưa cài: tắt */ }
function saveRot() { try { fs.writeFileSync(ROT_CFG, JSON.stringify(rotCfg)); } catch (e) { log('không lưu được rotate.json:', e.message); } }
let rotTarget = null, rotRetarget = 0, rotStep = 0, rotJoinAt = 0;
function rotPick() {
  const h = vnHour();
  let lo = rotCfg.min, hi = rotCfg.max;
  if (h >= 1 && h < 7) hi = Math.min(hi, lo + 2);
  else if (h >= 19 && h < 23) lo = Math.max(lo, hi - 2);
  return lo + Math.floor(Math.random() * (hi - lo + 1));
}
function rotLeave(on) {
  const c = on.filter(n => !RT[n].wander && !RT[n].placing).sort((a, b) => (state[a].since || 0) - (state[b].since || 0));
  if (!c.length) return null;
  const n = c[Math.floor(Math.random() * Math.min(3, c.length))];   // một trong 3 bot online lâu nhất
  state[n].want = false; state[n].offSince = Date.now(); save();
  if (RT[n].bot) RT[n].bot.quit();
  return n;
}
function rotJoin(off) {
  const c = off.slice().sort((a, b) => (state[a].offSince || 0) - (state[b].offSince || 0));
  if (!c.length) return null;
  const n = c[Math.floor(Math.random() * Math.min(3, c.length))];   // một trong 3 bot nghỉ lâu nhất
  state[n].want = true; state[n].since = Date.now(); RT[n].retryAt = 0; save();
  return n;
}
setInterval(() => {
  if (stopping || !rotCfg.on) return;
  const now = Date.now();
  const bh = NAMES.filter(n => state[n].server === 'banghoi-1');
  const on = bh.filter(n => state[n].want), off = bh.filter(n => !state[n].want);
  if (rotJoinAt && !realOnline().length) rotJoinAt = Math.min(rotJoinAt, now + 15000);   // vắng người: đổi ca nhanh
  if (rotJoinAt && now >= rotJoinAt) { rotJoinAt = 0; const j = rotJoin(off); if (j) log('thay phiên: đổi ca,', j, 'vào'); return; }
  if (rotTarget == null || now >= rotRetarget) { rotTarget = rotPick(); rotRetarget = now + (15 + Math.random() * 35) * 60000; log('thay phiên: mục tiêu', rotTarget, 'bot'); }
  if (now < rotStep || rotJoinAt) return;
  rotStep = now + (realOnline().length ? (2 + Math.random() * 6) * 60000 : 10000 + Math.random() * 10000);   // không có người thật: chỉnh nhanh (29/10)
  if (on.length > rotTarget) { const n = rotLeave(on); if (n) log('thay phiên:', n, 'thoát (' + (on.length - 1) + '/' + rotTarget + ')'); }
  else if (on.length < rotTarget) { const n = rotJoin(off); if (n) log('thay phiên:', n, 'vào (' + (on.length + 1) + '/' + rotTarget + ')'); }
  else if (off.length && Math.random() < 0.4) {   // đổi ca
    const n = rotLeave(on);
    if (n) { rotJoinAt = now + (1 + Math.random() * 3) * 60000; log('thay phiên: đổi ca,', n, 'thoát'); }
  }
}, 10000);   // 10 giây kiểm một lần (bước chỉnh vẫn theo rotStep)

// ---------------------------------------------------------------- điều khiển

async function act(b) {
  if (b.action === 'chatcfg') {
    const min = Math.round(+b.min), max = Math.round(+b.max);
    if (b.on != null) chatCfg.on = !!b.on;
    if (b.min != null || b.max != null) {
      if (!(min >= 1 && max >= min && max <= 240)) throw new Error('khoảng phút không hợp lệ (1 ≤ từ ≤ đến ≤ 240)');
      chatCfg.min = min; chatCfg.max = max;
    }
    saveChat();
    return 'bot tự chat: ' + (chatCfg.on ? 'bật' : 'tắt') + ', ' + chatCfg.min + '–' + chatCfg.max + ' phút một lần';
  }
  if (b.action === 'ftpacfg') {
    ftpaCfg.on = !!b.on; saveFtpa();
    return 'bot thỉnh thoảng tpa người chơi rồi tự huỷ: ' + (ftpaCfg.on ? 'bật (~1 lần / 40–90 phút cả server)' : 'tắt');
  }
  if (b.action === 'tpacfg') {
    tpaCfg.on = !!b.on; saveTpa();
    return 'bot tự trả lời TPA: ' + (tpaCfg.on ? 'bật (không bao giờ nhận: từ chối hoặc lơ, mỗi bot một tỉ lệ)' : 'tắt');
  }
  if (b.action === 'wandercfg') { wanderCfg.on = !!b.on; saveWander(); return 'bot đi dạo khi có người ở spawn / warp: ' + (wanderCfg.on ? 'bật' : 'tắt'); }
  if (b.action === 'wanderwalk') { wanderCfg.walk = !!b.on; saveWander(); return 'bot đi dạo: ' + (wanderCfg.walk ? 'được đi lại (cần grim.exempt)' : 'chỉ đứng nhìn quanh'); }
  if (b.action === 'rotcfg') { rotCfg.on = !!b.on; saveRot(); rotTarget = null; return 'thay phiên online: ' + (rotCfg.on ? 'bật (' + rotCfg.min + '–' + rotCfg.max + ' bot)' : 'tắt'); }
  if (b.action === 'staff') {
    staffSet = new Set((Array.isArray(b.names) ? b.names : []).map(x => String(x).toLowerCase()));
    try { fs.writeFileSync(STAFF_FILE, JSON.stringify([...staffSet])); } catch (e) { /* bỏ qua */ }
    return 'staff: ' + staffSet.size + ' tên (bot không diễn cho staff)';
  }
  if (b.action === 'wandernow') return await wanderOnce();
  if (b.action === 'gomphong') return await gatherRoom();
  if (b.action === 'trave') return await backHomes();
  if (b.action === 'chatnow') { chatOnce().then(r => log(r)).catch(e => log('lỗi bot chat:', e.message)); return 'cho bot chat thử ngay (vài giây nữa mới nói)'; }
  if (b.bot === '*') {
    const out = [];
    for (const n of NAMES) out.push(await act(Object.assign({}, b, { bot: n })).catch(e => e.message));
    return out.length + ' bot: ' + (b.action === 'join' ? 'vào' : b.action === 'leave' ? 'thoát' : b.action);
  }
  if (b.action === 'create') return create(String(b.bot || '').trim(), b.server);
  const st = state[b.bot], rt = RT[b.bot];
  if (!st) throw new Error('không có bot ' + b.bot);
  const text = String(b.text || '').replace(/[\x00-\x1f\x7f§]/g, '').trim().slice(0, 240);
  switch (b.action) {
    case 'join':
      if (b.server && SERVERS[b.server]) st.server = b.server;
      st.want = true; rt.retryAt = 0; save();
      return b.bot + ' vào ' + SERVERS[st.server];
    case 'leave':
      st.want = false; save();
      if (rt.bot) rt.bot.quit();
      return b.bot + ' thoát';
    case 'server':
      if (!SERVERS[b.server]) throw new Error('server không hợp lệ');
      st.server = b.server; save();
      if (rt.bot) { rt.fast = true; rt.bot.quit(); }
      return b.bot + ' chuyển sang ' + SERVERS[b.server];
    case 'chat':
      if (!rt.bot) throw new Error(b.bot + ' đang không online');
      if (!text) throw new Error('chưa nhập nội dung');
      rt.bot.chat(text.startsWith('/') ? ' ' + text : text);
      return b.bot + ': ' + text;
    case 'cmd': {
      if (!rt.bot) throw new Error(b.bot + ' đang không online');
      const c = text.replace(/^\/+/, '');
      if (!c) throw new Error('chưa nhập lệnh');
      const before = await serverPos(b.bot, st.server).catch(() => null);
      rt.bot.chat('/' + c);
      followTeleport(b.bot, before);
      return b.bot + ' gõ /' + c;
    }
    case 'move': {
      const x = +b.x, z = +b.z;
      if (!Number.isFinite(x) || !Number.isFinite(z) || Math.abs(x) > 29000000 || Math.abs(z) > 29000000) throw new Error('toạ độ không hợp lệ');
      st.at[st.server] = { x: Math.floor(x) + 0.5, y: null, z: Math.floor(z) + 0.5 }; save();
      if (rt.bot) place(b.bot, rt.bot).catch(e => log(b.bot, e.message));
      return b.bot + ' đi tới ' + Math.floor(x) + ' ' + Math.floor(z);
    }
    case 'random': {
      if (st.server !== 'banghoi-1') throw new Error('chỗ ngẫu nhiên chỉ có ở Bang Hội Chiến');
      // tìm ở nền (dò chỗ khô ráo mất vài giây / khu), xong báo vào khung chat trang Bot
      randomHome(b.bot).then(h => {
        if (!h) { event({ type: 'doi', bot: b.bot, server: 'banghoi-1', text: b.bot + ': không tìm được khu trống nào' }); return; }
        st.home['banghoi-1'] = h; delete st.at['banghoi-1']; save();
        event({ type: 'doi', bot: b.bot, server: 'banghoi-1', text: b.bot + ' ra khu mới ' + Math.floor(h.x) + ' ' + Math.floor(h.y) + ' ' + Math.floor(h.z) });
        if (rt.bot) place(b.bot, rt.bot).catch(e => log(b.bot, e.message));
      }).catch(e => log(b.bot, 'lỗi tìm khu:', e.message));
      return b.bot + ': đang tìm khu ngẫu nhiên chưa bot nào đứng…';
    }
    case 'home':
      delete st.at[st.server]; save();
      if (rt.bot) place(b.bot, rt.bot).catch(e => log(b.bot, e.message));
      return b.bot + ' về chỗ mặc định';
    case 'sethome': {
      if (!rt.bot) throw new Error(b.bot + ' đang không online');
      const p = await serverPos(b.bot, st.server);
      if (!p) throw new Error('không đọc được vị trí');
      st.home[st.server] = p; delete st.at[st.server]; save();
      return b.bot + ': chỗ mặc định mới ' + Math.floor(p.x) + ' ' + Math.floor(p.y) + ' ' + Math.floor(p.z);
    }
    case 'remove':
      if (!st.created) throw new Error('chỉ xoá được bot tạo thêm (20 bot gốc giữ nguyên, muốn thì bấm Thoát)');
      if (st.want || rt.bot || rt.connecting) throw new Error('cho ' + b.bot + ' thoát trước rồi mới xoá');
      delete state[b.bot]; delete RT[b.bot]; NAMES.splice(NAMES.indexOf(b.bot), 1); save();
      return 'đã xoá bot ' + b.bot + ' khỏi danh sách (tài khoản trong game vẫn còn: /choilai admin ' + b.bot + ' moi nếu muốn xoá hẳn)';
    default:
      throw new Error('thao tác lạ');
  }
}

/** UUID offline (như server tính cho tài khoản offline) để cấp quyền LuckPerms trước khi bot vào lần đầu. */
function offlineUuid(name) {
  const h = crypto.createHash('md5').update('OfflinePlayer:' + name).digest(); h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex'); return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
}

async function create(name, server) {
  if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) throw new Error('tên bot 3–16 ký tự, chỉ chữ không dấu, số, gạch dưới');
  if (NAMES.some(n => n.toLowerCase() === name.toLowerCase())) throw new Error('đã có bot tên ' + name);
  const srv = SERVERS[server] ? server : 'banghoi-1';
  const ipn = Math.max(BASE.length, ...NAMES.map(n => state[n].ipn || 0)) + 1;
  // như bot gốc: vào / ra không báo [+] [-] — cấp trước lần vào đầu (bot gốc cấp sau khi vào nên lần đầu vẫn hiện [+])
  await rcon('banghoi-1').cmd(`lp user ${offlineUuid(name)} permission set duckymomo.vaora.an true`).catch(() => {});
  state[name] = { server: srv, want: true, home: {}, at: {}, created: now(), ipn };
  NAMES.push(name); RT[name] = newRT();
  if (NAMES.some(n => n !== name && state[n].homeOld !== undefined)) {   // đang dùng phòng ẩn: bot mới vào thẳng phòng (29/10)
    state[name].homeOld = null; state[name].home['banghoi-1'] = roomSpot(NAMES.indexOf(name));
  }
  save();
  event({ type: 'doi', bot: name, server: srv, text: 'đã tạo bot ' + name + ', đang vào ' + SERVERS[srv] });
  return 'đã tạo bot ' + name + ', sẽ vào ' + SERVERS[srv] + ' trong ít giây';
}

http.createServer((req, res) => {
  const send = (code, obj) => { res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(obj)); };
  if (req.method === 'GET' && req.url.startsWith('/bots')) {
    return send(200, {
      servers: SERVERS,
      bots: NAMES.map(n => {
        const rt = RT[n], st = state[n], e = rt.bot && rt.bot.entity;
        const p = e && e.position;
        const h = st.home[st.server];
        return { name: n, want: st.want, server: st.server, online: !!rt.bot, connecting: rt.connecting, err: rt.err,
          x: p ? Math.floor(p.x) : null, y: p ? Math.floor(p.y) : null, z: p ? Math.floor(p.z) : null, spawn: atSpawn(st.server, p),
          home: h ? [Math.floor(h.x), Math.floor(h.y), Math.floor(h.z)] : null, away: !!st.at[st.server], created: !!st.created };
      }),
      events: EVENTS.slice(-100),
      chat: chatCfg,
      tpa: tpaCfg,
      tpagia: ftpaCfg,
      thayphien: { on: rotCfg.on, min: rotCfg.min, max: rotCfg.max, muctieu: rotTarget },
      dido: { on: wanderCfg.on, phong: NAMES.some(n => state[n].homeOld !== undefined), dang: NAMES.filter(n => RT[n].wander) },
    });
  }
  if (req.method === 'POST' && req.url === '/act') {
    let body = '';
    req.on('data', d => { body += d; if (body.length > 4000) req.destroy(); });
    req.on('end', async () => {
      try { const r = await act(JSON.parse(body || '{}')); log('điều khiển:', r); send(200, { ok: true, message: r }); } catch (e) { send(400, { ok: false, message: e.message }); }
    });
    return;
  }
  send(404, { ok: false });
}).listen(8090, () => log('botfarm: điều khiển ở :8090,', NAMES.length, 'bot,', NAMES.filter(n => state[n].want).length, 'đang bật'));
