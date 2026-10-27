// Bot của trang quản trị › Bot (27/10, chủ server). Mỗi bot là tài khoản offline vào THẲNG server con qua Velocity modern forwarding
// (không qua proxy: không tính vào số người ở danh sách server / MOTD; đứng yên = AFK nên Activity không tính giờ chơi, LowPop không đếm).
// Bot không tự làm gì: chủ server bật / tắt, chọn server, cho nói / chạy lệnh / đi tới toạ độ từ trang quản trị (panel gọi HTTP :8090).
//   GET  /bots   -> {servers, bots: [{name, want, server, online, x, y, z, home, err}], events}
//   POST /act    {bot ("*" = mọi bot), action, server, text, x, z}
//        action: join (vào, kèm server), leave (thoát), server (đổi server), chat (nói), cmd (chạy lệnh như bot gõ),
//                move (đi tới x z), home (về chỗ mặc định), sethome (lấy chỗ đang đứng làm chỗ mặc định),
//                random (Bang Hội Chiến: khu ngẫu nhiên chưa bot nào đứng, đặt làm chỗ mặc định),
//                create (tạo bot mới tên b.bot, vào luôn server b.server), remove (xoá bot đã tạo thêm, phải thoát trước)
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
  if (h && NAMES.slice(0, k).some(o => { const g = state[o].home['banghoi-1']; return g && Math.hypot(g.x - h.x, g.z - h.z) < 12; })) {
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
  const bot = mineflayer.createBot({ host, port: 25565, username: name, version: '1.21.4', auth: 'offline', hideErrors: true, keepAlive: false });
  bot._client.on('keep_alive', p => setTimeout(() => { try { bot._client.write('keep_alive', { keepAliveId: p.keepAliveId }); } catch (e) { /* bỏ qua */ } }, 45 + Math.random() * 15));
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
    if (w) { event({ type: 'tpa', bot: name, server: host, from: w[1], text: m }); return; }
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
    rt.placing = true; rt.placedAt = Date.now();   // khỏi bị vòng "bị dời" nhận nhầm chỗ vào game (spawn) làm chỗ đứng
    await sleep(2500);
    if (!st.silent) {   // vào / ra không báo [+] [-] (DuckyMoMoCore DonateRanks: duckymomo.vaora.an), cấp một lần
      rcon(host).cmd(`lp user ${name} permission set duckymomo.vaora.an true`).then(() => { st.silent = true; save(); }).catch(() => {});
    }
    creative(name, host).catch(() => {});   // mỗi lần xuất hiện (cả sau hồi sinh / đổi thế giới), sau khi đã vào hẳn
    place(name, bot).catch(e => log(name, 'lỗi đặt chỗ:', e.message)).finally(() => { rt.placing = false; rt.placedAt = Date.now(); });
  });
  setTimeout(() => { if (rt.connecting && !rt.bot) { rt.err = 'vào không được (quá 40 giây)'; try { bot.quit(); } catch (e) { /* bỏ qua */ } rt.connecting = false; rt.retryAt = Date.now() + 60000; } }, 40000);
}

/** Sáng tạo + bất tử, đọc lại chế độ chơi cho chắc (thử tối đa 3 lần). */
async function creative(name, host) {
  const R = rcon(host);
  for (let i = 0; i < 3; i++) {
    await R.cmd(`gamemode creative ${name}`);
    await R.cmd(`god ${name} on`);
    if (/data: 1$/.test((await R.cmd(`data get entity ${name} playerGameType`)).trim())) return;
    await sleep(2000);
  }
  log(name, 'không chuyển được Sáng tạo');
}

/** Đưa bot về chỗ đang đứng (at) hoặc chỗ mặc định (home); chết 2 lần trong 3 phút sau khi đặt ở một chỗ thì bỏ chỗ đó. */
async function place(name, bot) {
  const rt = RT[name], st = state[name], host = st.server;
  if (rt.bot !== bot) return;
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
    if (!rt.bot || rt.placing || Date.now() - rt.placedAt < 20000) continue;
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
setInterval(() => {
  for (const n of NAMES) {
    if (!state[n].want && RT[n].bot) { RT[n].fast = false; RT[n].bot.quit(); }
  }
  if (Date.now() < nextJoin) return;
  const n = NAMES.find(x => state[x].want && !RT[x].bot && !RT[x].connecting && Date.now() >= RT[x].retryAt);
  if (!n) return;
  const rt = RT[n];
  nextJoin = Date.now() + (rt.fast ? 2000 : 8000 + Math.random() * 12000);
  connect(n).catch(e => { rt.err = e.message; rt.connecting = false; });
}, 1000);

// ---------------------------------------------------------------- điều khiển

async function act(b) {
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
  NAMES.push(name); RT[name] = newRT(); save();
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
