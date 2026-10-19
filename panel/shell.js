// Khung chung trang quản trị: thanh điều hướng trái (thu thành nút menu trên điện thoại), tiêu đề trang.
// Trang dùng: <link rel="stylesheet" href="ui.css"> + <script src="shell.js"></script> ngay sau <body>.
// <body data-dozzle="__DOZZLE_URL__" data-title="…" data-sub="…">: link xem log, tiêu đề trang (trang chưa tự có .page-head).
// Dùng chung: mcHtml("&c[Admin] &#FFCC33…") -> HTML có màu như trong game (&mã màu, &#hex, &l/&o/&n/&m, %placeholder%).
window.mcHtml = function (text, opts) {
  const C = { 0: "#000000", 1: "#0000AA", 2: "#00AA00", 3: "#00AAAA", 4: "#AA0000", 5: "#AA00AA", 6: "#FFAA00", 7: "#AAAAAA",
    8: "#555555", 9: "#5555FF", a: "#55FF55", b: "#55FFFF", c: "#FF5555", d: "#FF55FF", e: "#FFFF55", f: "#FFFFFF" };
  const esc = (s) => s.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const base = (opts && opts.base) || "#FFFFFF";
  let color = base, st = {}, out = "";
  const parts = String(text ?? "").split(/([&§]#[0-9a-fA-F]{6}|[&§][0-9a-fk-orA-FK-OR]|%[A-Za-z0-9_:.\-]+%)/);
  for (const p of parts) {
    if (!p) continue;
    let m;
    if ((m = /^[&§]#([0-9a-fA-F]{6})$/.exec(p))) { color = "#" + m[1]; st = {}; continue; }
    if ((m = /^[&§]([0-9a-fk-or])$/i.exec(p))) {
      const k = m[1].toLowerCase();
      if (C[k]) { color = C[k]; st = {}; } else if (k === "r") { color = base; st = {}; } else st[k] = true;
      continue;
    }
    const css = `color:${color};${st.l ? "font-weight:700;" : ""}${st.o ? "font-style:italic;" : ""}${st.n || st.m ? `text-decoration:${st.n ? "underline " : ""}${st.m ? "line-through" : ""};` : ""}`;
    if (/^%[^%]+%$/.test(p)) out += `<span class="mc-ph" style="${css}">${esc(p)}</span>`;
    else out += `<span style="${css}">${esc(st.k ? p.replace(/\S/g, "▒") : p)}</span>`;
  }
  return out || '<span class="mc-empty">(dòng trống)</span>';
};
// Bỏ dấu để tìm: "kiem" khớp "Kiếm"
window.fold = (s) => String(s || "").normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/g, "d").replace(/Đ/g, "D").toLowerCase();

// Tên tiếng Việt cho đồ hay dùng (vũ khí, giáp, dụng cụ…): vnMat("DIAMOND_SWORD") = "Kiếm kim cương"; không biết thì null
(function () {
  const PART = { SWORD: "Kiếm", AXE: "Rìu", PICKAXE: "Cúp", SHOVEL: "Xẻng", HOE: "Cuốc", SPEAR: "Giáo", HELMET: "Mũ", CHESTPLATE: "Áo giáp",
    LEGGINGS: "Quần giáp", BOOTS: "Giày", HORSE_ARMOR: "Giáp ngựa", INGOT: "Thỏi", BLOCK: "Khối", ORE: "Quặng", NUGGET: "Mảnh" };
  const TIER = { WOODEN: "gỗ", STONE: "đá", IRON: "sắt", GOLDEN: "vàng", GOLD: "vàng", DIAMOND: "kim cương", NETHERITE: "netherite", COPPER: "đồng",
    LEATHER: "da", CHAINMAIL: "xích", TURTLE: "rùa", EMERALD: "ngọc lục bảo", COAL: "than", LAPIS: "lưu ly", REDSTONE: "đá đỏ", DEEPSLATE_IRON: "sắt (đá sâu)",
    DEEPSLATE_DIAMOND: "kim cương (đá sâu)", DEEPSLATE_GOLD: "vàng (đá sâu)" };
  const ONE = { BOW: "Cung", CROSSBOW: "Nỏ", TRIDENT: "Đinh ba", MACE: "Chuỳ", ELYTRA: "Cánh (elytra)", SHIELD: "Khiên", FISHING_ROD: "Cần câu",
    SHEARS: "Kéo", FLINT_AND_STEEL: "Bật lửa", CARROT_ON_A_STICK: "Cần câu cà rốt", WARPED_FUNGUS_ON_A_STICK: "Cần câu nấm", BRUSH: "Chổi",
    ENCHANTED_BOOK: "Sách phù phép", BOOK: "Sách", TURTLE_HELMET: "Mũ rùa", WOLF_ARMOR: "Giáp sói", GOLDEN_APPLE: "Táo vàng",
    ENCHANTED_GOLDEN_APPLE: "Táo vàng phù phép", DIAMOND: "Kim cương", EMERALD: "Ngọc lục bảo", NETHERITE_SCRAP: "Mảnh vụn netherite",
    TOTEM_OF_UNDYING: "Vật tổ bất tử", NAME_TAG: "Thẻ tên", CARVED_PUMPKIN: "Bí ngô khắc", EXPERIENCE_BOTTLE: "Bình kinh nghiệm",
    ENDER_PEARL: "Ngọc ender", COOKED_BEEF: "Bít tết", BREAD: "Bánh mì", ARROW: "Mũi tên", COBBLESTONE: "Đá cuội", DIRT: "Đất", OAK_LOG: "Gỗ sồi",
    AMETHYST_SHARD: "Mảnh thạch anh tím", NETHER_STAR: "Ngôi sao Nether", BEACON: "Đèn hiệu", SADDLE: "Yên ngựa", TORCH: "Đuốc",
    CHEST: "Rương", SHULKER_BOX: "Hộp shulker", FIREWORK_ROCKET: "Pháo hoa", EMERALD_BLOCK: "Khối ngọc lục bảo", COAL: "Than", STICK: "Que" };
  window.vnMat = function (m) {
    m = String(m || "").toUpperCase();
    if (ONE[m]) return ONE[m];
    for (const [part, vn] of Object.entries(PART)) {
      if (m.endsWith("_" + part)) { const t = m.slice(0, -part.length - 1); return TIER[t] ? `${vn} ${TIER[t]}` : null; }
    }
    return null;
  };
  window.prettyMat = (m) => String(m || "").toLowerCase().split("_").map(w => w ? w[0].toUpperCase() + w.slice(1) : "").join(" ");
  // "Kiếm kim cương · Diamond Sword"
  window.matLabel = (m) => { const vn = vnMat(m); return vn ? `${vn} · ${prettyMat(m)}` : prettyMat(m); };
})();

// Ô chọn có ô tìm kiếm riêng. input gốc bị ẩn nhưng vẫn giữ giá trị (đọc/ghi .value, .disabled như cũ).
// combo(input, items(query) -> [{value, label, sub, dim}], onPick(value), {label: v -> [chữ, chữ phụ], search: "gợi ý ô tìm"})
window.combo = function (input, items, onPick, opts) {
  opts = opts || {};
  const wrap = document.createElement("div"); wrap.className = "cb";
  input.parentNode.insertBefore(wrap, input); wrap.appendChild(input);
  input.hidden = true;
  const trig = document.createElement("button"); trig.type = "button"; trig.className = "cb-trig";
  trig.innerHTML = '<span class="cb-v"><b></b><small></small></span><span class="cb-arrow">▾</span>';
  const pop = document.createElement("div"); pop.className = "cb-pop"; pop.hidden = true;
  pop.innerHTML = '<div class="cb-search"><input type="search" autocomplete="off"></div><div class="cb-list"></div><div class="cb-foot"></div>';
  wrap.append(trig, pop);
  const q = pop.querySelector("input"), list = pop.querySelector(".cb-list"), foot = pop.querySelector(".cb-foot");
  q.placeholder = opts.search || "Gõ để tìm (có dấu hoặc không)…";
  let act = 0, cur = [];
  const desc = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value");
  function sync() {
    const v = desc.get.call(input).trim();
    let lab = v, sub = "";
    if (opts.label) [lab, sub] = opts.label(v); else { const f = items("").find(x => x.value === v); if (f) { lab = f.label; sub = f.sub || ""; } }
    trig.querySelector("b").textContent = v ? lab : (input.placeholder || "Chọn…");
    trig.querySelector("small").textContent = v ? (sub || "") : "";
    trig.classList.toggle("ph", !v);
  }
  Object.defineProperty(input, "value", { get() { return desc.get.call(this); }, set(v) { desc.set.call(this, v); sync(); } });
  new MutationObserver(() => { trig.disabled = input.disabled; if (input.disabled) close(); }).observe(input, { attributes: true, attributeFilter: ["disabled"] });
  trig.disabled = input.disabled;
  function draw() {
    cur = items(q.value.trim());
    const v = input.value.trim();
    list.innerHTML = cur.length ? "" : '<div class="cb-empty">Không tìm thấy. Thử tên tiếng Anh hoặc mã (vd diamond_sword).</div>';
    cur.forEach((it, i) => {
      const d = document.createElement("div");
      d.className = "cb-it" + (it.dim ? " dim" : "") + (i === act ? " act" : "") + (it.value === v ? " sel" : "");
      d.innerHTML = `<b></b>${it.sub ? "<small></small>" : ""}`;
      d.querySelector("b").textContent = it.label;
      if (it.sub) d.querySelector("small").textContent = it.sub;
      d.onmousedown = (e) => { e.preventDefault(); pick(it.value); };
      d.onmousemove = () => { if (act !== i) { act = i; mark(); } };
      list.appendChild(d);
    });
    foot.textContent = cur.length ? `${cur.length} kết quả · ↑↓ chọn · Enter xác nhận · Esc đóng` : "";
    mark();
  }
  function mark() { [...list.children].forEach((c, i) => c.classList.toggle("act", i === act)); const a = list.children[act]; if (a && a.scrollIntoView) a.scrollIntoView({ block: "nearest" }); }
  function open() {
    if (input.disabled) return;
    document.querySelectorAll(".cb-pop").forEach(p => { if (p !== pop) p.hidden = true; });
    pop.hidden = false; q.value = "";
    cur = items(""); act = Math.max(0, cur.findIndex(x => x.value === input.value.trim()));
    draw(); q.focus();
  }
  function close() { pop.hidden = true; }
  function pick(v) { input.value = v; close(); trig.focus(); onPick && onPick(v); }
  trig.onclick = () => (pop.hidden ? open() : close());
  trig.onkeydown = (e) => { if (e.key === "ArrowDown" || (e.key.length === 1 && e.key !== " " && !e.ctrlKey && !e.metaKey)) { open(); if (e.key.length === 1) { q.value = e.key; act = 0; draw(); e.preventDefault(); } } };
  q.oninput = () => { act = 0; draw(); };
  q.onkeydown = (e) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") { e.preventDefault(); act = Math.max(0, Math.min(cur.length - 1, act + (e.key === "ArrowDown" ? 1 : -1))); mark(); }
    else if (e.key === "Enter") { e.preventDefault(); if (cur[act]) pick(cur[act].value); }
    else if (e.key === "Escape") { close(); trig.focus(); }
  };
  document.addEventListener("mousedown", (e) => { if (!pop.hidden && !wrap.contains(e.target)) close(); });
  sync();
  return { refresh: () => { sync(); if (!pop.hidden) draw(); }, open };
};

// Hộp xác nhận theo giao diện trang (thay confirm/prompt của trình duyệt). Trả Promise:
//   await ask("Xoá boss?", { title: "Xoá boss", ok: "Xoá", danger: true })      -> true / false
//   await ask("Mã boss mới:", { input: "rong_moi" })                           -> chuỗi đã nhập / null
// Enter = đồng ý, Esc hoặc bấm ra ngoài = huỷ.
window.ask = function (message, opts) {
  opts = opts || {};
  return new Promise((resolve) => {
    const wrap = document.createElement("div");
    wrap.className = "ask-wrap";
    wrap.innerHTML = `<div class="ask" role="dialog" aria-modal="true"><h3></h3><p></p>${opts.input !== undefined ? '<input class="ask-in">' : ""}
      <div class="ask-btns"><button type="button" class="ghost ask-no"></button><button type="button" class="primary ask-ok"></button></div></div>`;
    const box = wrap.querySelector(".ask");
    box.querySelector("h3").textContent = opts.title || (opts.danger ? "Xác nhận" : "Xác nhận");
    box.querySelector("p").textContent = message;
    const ok = box.querySelector(".ask-ok"), no = box.querySelector(".ask-no"), inp = box.querySelector(".ask-in");
    ok.textContent = opts.ok || "Đồng ý";
    no.textContent = opts.cancel || "Huỷ";
    if (opts.danger) ok.classList.add("danger");
    if (inp) inp.value = opts.input || "";
    const prev = document.activeElement;
    const done = (v) => { document.removeEventListener("keydown", key, true); wrap.remove(); if (prev && prev.focus) prev.focus(); resolve(v); };
    const yes = () => done(inp ? inp.value.trim() || null : true);
    const key = (e) => { if (e.key === "Escape") { e.preventDefault(); done(inp ? null : false); } else if (e.key === "Enter" && e.target !== no) { e.preventDefault(); yes(); } };
    ok.onclick = yes;
    no.onclick = () => done(inp ? null : false);
    wrap.onmousedown = (e) => { if (e.target === wrap) done(inp ? null : false); };
    document.addEventListener("keydown", key, true);
    document.body.appendChild(wrap);
    (inp || ok).focus();
    if (inp) inp.select();
  });
};

// Thông báo nổi góc dưới: toast("Đã lưu", "ok" | "err" | ""); kind "" = đang xử lý (không tự tắt).
// Nút ⓘ: bấm hiện giải thích chi tiết (dùng chung mọi trang). infoBtn(text) trả HTML; text xuống dòng bằng \n.
window.infoBtn = function (text, label) {
  if (!text) return "";
  const a = String(text).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  return `<button type="button" class="ib" aria-label="${label ? "Giải thích: " + String(label).replace(/"/g, "&quot;") : "Giải thích"}" data-info="${a}">i</button>`;
};
(function () {
  let pop = null, from = null;
  const close = () => { if (pop) { pop.remove(); pop = null; from = null; } };
  document.addEventListener("click", (e) => {
    const b = e.target.closest && e.target.closest(".ib");
    if (!b) { if (pop && !pop.contains(e.target)) close(); return; }
    e.preventDefault(); e.stopPropagation();   // nút nằm trong <label> công tắc: không bật/tắt công tắc
    if (from === b) return close();
    close();
    pop = document.createElement("div");
    pop.className = "ib-pop"; pop.setAttribute("role", "dialog");
    // mỗi dòng "Nhãn: nội dung" -> nhãn in đậm (Làm gì, Cách dùng, Gọi tắt, Có ở, Ví dụ…)
    const escIb = (t) => t.replace(/[&<>]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));
    pop.innerHTML = String(b.dataset.info).split("\n").map(line => {
      const m = line.match(/^([^:]{1,22}):\s(.*)$/);
      return `<div class="ib-l">${m ? `<b>${escIb(m[1])}:</b> ${escIb(m[2])}` : escIb(line)}</div>`;
    }).join("");
    document.body.appendChild(pop);
    from = b;
    const r = b.getBoundingClientRect(), w = pop.offsetWidth, h = pop.offsetHeight;
    let left = Math.min(Math.max(8, r.left + r.width / 2 - w / 2), innerWidth - w - 8);
    let top = r.bottom + 8;
    if (top + h > innerHeight - 8 && r.top - h - 8 > 8) top = r.top - h - 8;
    pop.style.left = left + "px"; pop.style.top = top + "px";
  }, true);
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") close(); });
  addEventListener("scroll", close, true);
  addEventListener("resize", close);
})();

window.toast = function (msg, kind) {
  let t = document.getElementById("toast");
  if (!t) { t = document.createElement("div"); t.id = "toast"; t.className = "toast"; document.body.appendChild(t); }
  t.textContent = msg; t.className = "toast show " + (kind || "");
  clearTimeout(t._h);
  if (kind) t._h = setTimeout(() => t.classList.remove("show"), kind === "err" ? 9000 : 4000);
};
(function () {
  const NAV = [
    ["Cấu hình", [["", "⚙️", "Cài đặt"], ["thuvien", "🗡️", "Thư viện đồ"], ["boss", "🐉", "Boss"], ["role", "👑", "Role & rank"], ["donate", "💳", "Donate"]]],
    ["Theo dõi", [["hoatdong", "📈", "Hoạt động người chơi"], ["hethong", "🖥️", "Hệ thống"], ["media", "📺", "Sứ Giả (livestream)"], ["@log", "📜", "Xem log ↗"]]],
    ["Tài liệu", [["camnang", "📘", "Cẩm nang admin"], ["huongdan", "📖", "Cẩm nang trong game"]]],
  ];
  const body = document.body;
  const here = location.pathname.replace(/\/+$/, "").split("/").pop();
  const page = ["thuvien", "boss", "role", "donate", "hoatdong", "hethong", "media", "camnang", "huongdan"].includes(here) ? here : "";
  const dozzle = body.dataset.dozzle && !body.dataset.dozzle.startsWith("__") ? body.dataset.dozzle : "";
  const nav = document.createElement("aside");
  nav.className = "sidenav";
  let html = '<a class="brand" href="./"><span class="logo">🦆</span><span><b>DuckyMoMo</b><small>Trang quản trị</small></span></a>';
  for (const [sec, items] of NAV) {
    html += `<div class="sec">${sec}</div>`;
    for (const [href, ic, label] of items) {
      if (href === "@log") {
        if (dozzle) html += `<a class="item" href="${dozzle}" target="_blank" rel="noopener"><span class="ic">${ic}</span>${label}</a>`;
        continue;
      }
      html += `<a class="item${href === page ? " on" : ""}" data-page="${href || "caidat"}" href="${href || "./"}"><span class="ic">${ic}</span>${label}</a>`;
    }
  }
  html += '<div class="foot">Lưu là áp dụng ngay lên server.<br>Đổi lớn: kiểm tra người online trước.</div>';
  nav.innerHTML = html;
  // Staff đăng nhập bằng tài khoản game (qua trang web công khai): chỉ hiện trang mình có quyền, thêm tên + link về tài khoản
  fetch("api/toi").then(r => r.json()).then(t => {
    if (!t.game) return;
    const can = new Set(t.tinh_nang || []);
    const page = { caidat: ["caidat"], thuvien: ["thuvien", "thuvien-dua"], boss: ["boss"], role: ["role", "role-quyen", "rank", "rank-quyen"], donate: ["donate"], hoatdong: ["hoatdong"], hethong: ["hethong"], media: ["media"],
      camnang: ["camnang"], huongdan: ["huongdan"] };
    nav.querySelectorAll("a.item[data-page]").forEach(a => {
      if (!t.toan_quyen && !(page[a.dataset.page] || []).some(f => can.has(f))) a.remove();
    });
    nav.querySelectorAll(".sec").forEach(s => { const n = s.nextElementSibling; if (!n || n.classList.contains("sec") || n.classList.contains("foot")) s.remove(); });
    const who = document.createElement("div");
    who.className = "foot";
    who.innerHTML = `Đăng nhập: <b></b><br><a href="../taikhoan.html">← Tài khoản của tôi</a><br>Mọi thao tác được ghi lại kèm tên bạn.`;
    who.querySelector("b").textContent = t.ten;
    nav.querySelector(".foot").replaceWith(who);
    nav.querySelector(".brand").setAttribute("href", "../taikhoan.html");
  }).catch(() => {});
  const bar = document.createElement("div");
  bar.className = "navtoggle";
  bar.innerHTML = '<button type="button" aria-label="Menu">☰</button><span>🦆 DuckyMoMo</span>';
  bar.querySelector("button").onclick = () => body.classList.toggle("nav-open");
  body.classList.add("shell");
  body.prepend(nav);
  // nội dung (mọi phần tử còn lại) gói vào một cột để lưới 2 cột không vỡ
  document.addEventListener("DOMContentLoaded", () => {
    const content = document.createElement("div");
    content.className = "content";
    for (const el of [...body.children]) {
      if (el !== nav && el.tagName !== "SCRIPT" && (el.tagName !== "HEADER" || el.classList.contains("keep")) && el.tagName !== "DATALIST") content.appendChild(el);
    }
    content.prepend(bar);
    body.appendChild(content);
    if (body.dataset.title && !content.querySelector(".page-head")) {
      const head = document.createElement("div");
      head.className = "page-head shell-head";
      head.innerHTML = `<div><h1></h1>${body.dataset.sub ? '<p class="sub"></p>' : ""}</div>`;
      head.querySelector("h1").textContent = body.dataset.title;
      if (body.dataset.sub) head.querySelector(".sub").textContent = body.dataset.sub;
      const main = content.querySelector("main");
      const keep = content.querySelector("header.keep");
      if (keep) keep.before(head); else (main || content).prepend(head);
      if (main && getComputedStyle(main).display === "grid") { head.style.gridColumn = "1 / -1"; }
    }
    document.addEventListener("click", (e) => { if (body.classList.contains("nav-open") && !nav.contains(e.target) && !bar.contains(e.target)) body.classList.remove("nav-open"); });
  });
})();
