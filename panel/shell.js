// Khung chung trang quản trị: thanh điều hướng trái (thu thành nút menu trên điện thoại), tiêu đề trang.
// Trang dùng: <link rel="stylesheet" href="ui.css"> + <script src="shell.js"></script> ngay sau <body>.
// <body data-dozzle="__DOZZLE_URL__" data-title="…" data-sub="…">: link xem log, tiêu đề trang (trang chưa tự có .page-head).
(function () {
  const NAV = [
    ["Cấu hình", [["", "⚙️", "Cài đặt"], ["thuvien", "🗡️", "Thư viện đồ"], ["boss", "🐉", "Boss"], ["role", "👑", "Role & rank"]]],
    ["Theo dõi", [["hoatdong", "📈", "Hoạt động người chơi"], ["@log", "📜", "Xem log ↗"]]],
    ["Tài liệu", [["camnang", "📘", "Cẩm nang admin"], ["huongdan", "📖", "Cẩm nang trong game"]]],
  ];
  const body = document.body;
  const here = location.pathname.replace(/\/+$/, "").split("/").pop();
  const page = ["thuvien", "boss", "role", "hoatdong", "camnang", "huongdan"].includes(here) ? here : "";
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
      html += `<a class="item${href === page ? " on" : ""}" href="${href || "./"}"><span class="ic">${ic}</span>${label}</a>`;
    }
  }
  html += '<div class="foot">Lưu là áp dụng ngay lên server.<br>Đổi lớn: kiểm tra người online trước.</div>';
  nav.innerHTML = html;
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
