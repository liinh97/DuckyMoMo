# Bàn giao dự án DuckyMoMo Network

> Dùng file này để tiếp tục ở session hoặc tài khoản Claude khác. Cập nhật: 21/10/2026 tối. ĐỌC MỤC 000000000 TRƯỚC, rồi 00000000, 0000000, 000000, 00000, 0000, 000, 00, 0.
> Đọc kèm: `README.md`, `docs/minecraft-server-plan.md` (kế hoạch, quyết định), `docs/network-architecture.md` (kiến trúc).

---

## 000000000. MỚI NHẤT — 21/10/2026 tối muộn (tiếp test bằng bot, tài khoản lần 5 quay lại)

Trang test: https://claude.ai/artifact/YY55Wn87HYuMzL898MT8a7 (tài khoản này; đã nạp đủ kết quả, 162 mục có dữ liệu). Nguồn: `docs/lo-trinh-test-ketqua.json`.
Kịch bản bot mới trong `tools/bottest/` (đọc chú thích đầu file): p1-batpet, d-dautruong, d3-practice (practice-1), a-thaiap, a-sangtao
(sangtao-1), h-hoabinh, s3-balo, s4-trade, s5-choden (`run.sh s5-choden.js do` = chỉ dò menu), s6-pettrade, s11-tienam, x4-web (gọi site:8080),
s24-ve. `start(tên, ip, host)` vào được practice-1 / sangtao-1. Mẹo: Essentials chiếm /item, /kill -> dùng minecraft:item / minecraft:kill;
triệu hồi sinh vật ở `~ ~ ~` (lệch ra dễ kẹt trong đất chết ngạt); bot mới vào lần đầu bị đưa về spawn sau vài giây (chờ 5 giây rồi mới tp);
sau dịch chuyển có vài giây bất tử (đợi 5 giây rồi mới /damage); bot rút mạng (socket.destroy) làm treo tiến trình -> process.exit(0) cuối kịch bản.

### Đã sửa trong phiên
- Đấu Trường: người thua vào trận từ spawn bị KẸT trong sân (PvPManager gắn giao tranh, chặn dịch chuyển về khu an toàn) -> PvPManager
  World Exclusions thêm `dautruong` (repo + data, đã /pmr reload).
- Practice: Việt hoá menu Duels (`modes/practice/server/plugins/Duels/GUI/*.yml`, config.yml đếm ngược, lang.yml '$' -> xu), lời chào
  Essentials (motd.txt repo + data), câu "Welcome ... to the server" + bỏ kit dụng cụ đá lần đầu (CHỈ sửa data/practice-1/plugins/Essentials/config.yml).
- Vé cổng Sáng Tạo chỉ dùng 1 lần (ThaiAp.useTicket). Đã build; ĐÃ restart sangtao-1. banghoi-1/lobby/practice-1 nhận jar mới ở lần restart sau
  (kèm jar Thăng Cấp đang chờ của tài khoản #6).

### ĐÃ SỬA (người dùng chọn "cho vào khi ở gần"): không thị trấn nào vào được Liên minh Hoà Bình (h1)
- Nguyên nhân: /hoabinh vao bắt đất chạm liên minh; vòng spawn 300 block nằm gọn trong đất kinh đô (KinhDo tới ~310 block, chunk x 774..812,
  z -177..-139) nên newTownInRing không dùng được; ngoài vòng Towny min_plot_distance_from_town_plot 5 chặn (chỉ bỏ qua cho cùng quốc gia).
- Giờ (PeaceAlliance): vùng liên minh nối liền = loang từ đất kinh đô qua đất thị trấn thành viên (connectedPeace, cache 30 giây); thị trấn
  cách vùng đó ≤ peace-alliance.join-max-distance (8, schema peace_join_dist) ô thì vào được; vào rồi /t claim nối sang; tick chỉ bật hoà bình khi
  nối liền + đủ join-wait-days; /hoabinh danhsach ghi "(chờ nối đất)". Test trọn bằng tools/bottest/h1-vao.js (tạm join-wait-days 0).
- banghoi-1 đã restart 18:42 với jar mới (gồm cả Thăng Cấp của tài khoản #6 - hết chờ). sangtao-1 restart 18:3x. lobby, practice-1 CHƯA restart
  (jar mới chỉ khác ThaiAp bản sao + PeaceAlliance, không ảnh hưởng 2 server đó).

### Dữ liệu test cần dọn (thêm vào danh sách ở mục 00000000)
- Bot mới: ThuPet ThuDA ThuDB ThuPA ThuPB (practice-1) ThuS ThuTA ThuTB ThuCA ThuCB ThuCC ThuPetA ThuPetB ThuH ThuK ThuWeb ThuWebSai -> /choilai admin <tên> moi.
- ThuA có Thái Ấp 70×70 (đã mua + mở rộng) -> xoá đất trong thaiap.yml nếu cần; ThuCapDo cũng có 1 mảnh.
- Chợ đen còn các món treo cũ Cobblestone, Book, Kiếm rồng, Bread (không rõ của ai, xem /choden trước khi gỡ).
- Vụ án thử V1 đã xoá (an.yml để trống, bản cũ /tmp/an-test-V1.yml). Pet thử đã thả hết.

### Ghi nhận nhỏ (chưa sửa)
- `/pay B 1,5` = 15 xu (Essentials hiểu dấu phẩy là phân cách nghìn). `/choden ban NaN` hiện cú pháp gốc `/ah sell <price>`.
- /petinfo ghi tên thức ăn tiếng Anh. Balo: sập server đột ngột ngay sau khi cất đồ có thể đôi đồ (balo ghi file ngay, túi lưu khi thoát).

---

## 00000000. (cũ hơn) — 21/10/2026 tối (chuyển tài khoản Claude lần 6)

Phiên này: làm lại Thăng Cấp, rồi Claude TỰ TEST lộ trình bằng bot. Mọi thứ đã commit (CHƯA push). Server: cả 4 server chạy bản mới nhất
TRỪ 1 jar nhỏ (LevelUp: admin reset thì xoá khỏi bảng xếp hạng web) đã build, chờ lần restart banghoi tới.

### Lộ trình test — trang của tài khoản lần 5 KHÔNG dùng được nữa
- Kết quả mới nhất đã chép về `docs/lo-trinh-test-ketqua.json` (138 mục: 96 ok, 2 err, 2 lai, 38 chỉ ghi chú "CẦN BẠN TEST").
- Tài khoản mới: đăng lại `docs/lo-trinh-test.html` (Artifact publish, capabilities {"db": {}}, icon "checklist"), nạp json vào
  collection `ketqua` (mỗi mục 1 doc {tt, ghi_chu, luc, sua?}; ArtifactData batch set, ≤50/lần, dùng file_path). Gửi link cho người dùng.
- Quy ước ghi: "Claude tự test: …" (tt ok/err), "CẦN BẠN TEST: …" (tt trống), "ĐÃ SỬA …" (tt lai). Người dùng muốn MỌI mục được điền.
- Lỗi còn mở: k2 (người thường không /sell hand: nhóm default thiếu essentials.sell.hand + essentials.sell.bulk server=banghoi-1 —
  Claude bị chặn cấp quyền nhóm, người dùng phải chạy lp), p3 (menu chọn hệ MyPet hiện mỗi hệ 2 lần, chưa rõ vì sao).
- CHƯA TEST (~70 mục, xem json: mục không có hoặc tt trống): pet p1 p4 p5 p6 p8, quái q1 q5 q6 q8 q10, mùa n2–n6, chiến tranh w4 w5 w9–w12
  (+ w6 phần cướp = vua ĐẶT RƯƠNG cạnh cờ, w7 phí thuộc địa 150/ngày chạy lúc 0 giờ), hoà bình h1–h5, Thái Ấp a2–a8, Đấu Trường d1–d3,
  web x3–x13 x15, thử phá s1–s9 s12–s18 s21 s23 s24, vận hành o3 o5–o7. Đang định làm tiếp (người dùng ngắt trước khi chạy): p1 bắt pet
  (summon heo NoAI Health:1, bot cầm dây xích đánh), rồi Đấu Trường, Thái Ấp, Hoà Bình, s3/s4/s5 nhân đồ (bot thoát ngang), web x4 (bot
  /web lấy mã, Playwright đăng nhập site), panel x13/x15 (Playwright, mật khẩu panel trong .env).

### Bot test (tools/bottest) — đọc trước khi dùng
- `./tools/bottest/run.sh <kịch bản>.js` (BOTN=tên để đổi). mineflayer 1.21.4 qua ViaBackwards, vào THẲNG banghoi-1 (Velocity forwarding
  secret trong data/banghoi-1/config/paper-global.yml) => không qua Sonar/AuthMe/proxy. Chạy trong container node:22-alpine mạng
  duckymomo_default (Docker Desktop không vào được IP container). h.js: start(tên) -> {bot, rc (rcon), say, openMenu, click, itemText, attr,
  end}; moveTo(t,x,z) = tp phía server rồi bot vào lại (qua Via bot không theo được tp). Kịch bản cũ ở scratchpad đã mất; viết lại theo
  tools/bottest/vidu-capdo.js.
- Mẹo: lệnh `kill`/`tp`/`execute` bị Essentials chiếm -> dùng `minecraft:kill`, `minecraft:tp`, `minecraft:execute`. Towny hỏi /confirm.
  Đặt khối cần quyền `grim.exempt` cho bot (GrimAC chặn vì gói tin bot). Bot không đọc được tin chat người chơi (prismarine lỗi, đã bỏ qua).
- Lệnh admin mới: `/hoatdong them <tên> <phút>` cộng giờ chơi (dựng tình huống: lập thị trấn 3 g, quân hàm 5 g).
- Cấu hình hay phải tạm đổi (SAO LƯU rồi TRẢ LẠI ngay, dmm reload / swa reload): DuckyMoMoCore `active-play.new-member-days` (3),
  SiegeWar `allowed_siege_start_days` (thursday), level-up hours-base/kill-base. Phiên đánh: `swa battlesession start|end`;
  `swa siegeimmunity town <t> 0`; `swa siege <t> setbalance 500` / `end`; `townyadmin newday`.

### ⚠ DỮ LIỆU TEST CẦN DỌN (khi test xong)
- Bot: ThuCapDo, ThuPet2 (đã reset), ThuA ThuB ThuC ThuD ThuE ThuF (chưa). Dọn: `/choilai admin <tên> moi` từng bot (console).
- Towny: thị trấn ThuTranA (ThuA, dân ThuB ThuE), ThuTranD (ThuD, đang là THUỘC ĐỊA của ThuNuoc), ThuTranF (ThuF, phá sản), quốc gia
  ThuNuoc. Xoá: `townyadmin nation ThuNuoc delete`, `townyadmin town <tên> delete` (+ /confirm). Cờ banner (đỏ/xanh/trắng) quanh
  x 11808 y 102 z -2700; vùng 11500 -2700.
- Quyền grim.exempt của ThuA, ThuF: `lp user ThuA permission unset grim.exempt` (ThuF tương tự). Thái Ấp của ThuCapDo (đã reset, kiểm).

### Đã làm phiên này (chi tiết: git log 21/10 chiều-tối)
- Thăng Cấp làm lại (LevelUp.java): giờ 0,04 × N², xu 250 × N^0,6, hạ gục N²; điểm thuộc tính (Máu/Sức mạnh/Bền bỉ/Nhanh nhẹn, bậc k tốn k,
  cấp 100 max 2 cái, tắt Đấu Trường, AttributeModifier tạm, tẩy điểm 10.000); 8 đặc quyền mua bằng xu (SellLimit duckymomo.sell.them.<n>,
  Workshop giam/.giam.2, PetCapture batgioi, PetPerks.java: +15% kinh nghiệm MyPet, hồi sinh nhanh gấp đôi). Đã test kỹ (cd1–cd22).
- Gói tài nguyên Việt hoá mọi chữ do máy người chơi vẽ (tên đồ/khối/sinh vật, phù phép, chỉ số giáp): tools/fontpack/lang-vi.py đè en_us +
  en_gb (3.453 dòng); build-font-pack.sh -> up -d 4 server.
- Sửa nhờ bot: /ce giá menu, Chợ đen give-item true + câu báo, OCM "Sát thương:" + bỏ "[OCM] chế độ đánh", CMILib giờ/phút/giây, /kit
  "Gói Gói", MyPet bỏ thẻ </gold>, chặn quảng cáo chat (chat-filter.ads), /hd phong quân hàm + công thành (thứ Năm, 2 thị trấn, rương=cướp),
  Thư viện đồ ô Hình riêng, Quần tân thủ (mã quan). Phát hiện nhưng KHÔNG sửa (chờ người dùng): xem "Câu hỏi" dưới.

### Câu hỏi đang chờ người dùng (mới)
1. Cấp quyền /sell hand + bulk cho nhóm default (lệnh ở trên) — người dùng phải tự chạy.
2. Rời nghề xong vào ngay nghề khác được (chờ 1 ngày chỉ áp nghề vừa rời): có chặn mọi nghề 1 ngày không?
3. Towny `default_neutral: 'true'` cho quốc gia: nước mới bị trừ thêm 300 xu/ngày "duy trì hoà bình", vô tác dụng trong SiegeWar. Đổi false?
4. Chặn quảng cáo chặn cả link thường (shopee.vn…): có cho phép thêm trang nào (chat-filter.ads.allow)?
5. Giá ô đất Towny không tăng dần (lộ trình ghi tăng dần); thuế dân tính % số dư/ngày (5% = 2.500 xu/ngày với 50k) có nặng không.
6. SiegeWar cảnh báo cướp 120 xu/ô "quá cao" so với tính toán 1,31 (do định giá ×3).
7. Câu hỏi cũ mục 0000000 (lương nghề, mất 30% cấp nghề, Ruins, /pro_command…) vẫn chưa trả lời.
- site/config/site.yml có số tài khoản MB người dùng tự điền: CHƯA commit (hỏi người dùng muốn commit hay chuyển .env).

---

## 0000000. MỚI NHẤT — 21/10/2026 chiều (chuyển tài khoản Claude lần 5)

Phiên tài khoản trước (sáng → chiều 21/10). Mọi thứ đã build + triển khai: lần restart cuối banghoi 13:25, cả 4 server chạy bản mới nhất,
KHÔNG còn gì chờ restart. Chưa push (người dùng chưa bảo). Commit cuối: xem `git log`.

### 21/10 tối: Claude tự test bằng bot (tools/bottest) + sửa lỗi tìm ra
- **Bot test**: `tools/bottest/run.sh <kịch bản>.js` (mineflayer trong container node, vào THẲNG banghoi-1 bằng Velocity forwarding secret,
  không qua Sonar/AuthMe). Bot ThuCapDo, ThuPet2 (đã /choilai admin ... moi). Không test được: proxy/đăng nhập, Bedrock, sang server khác,
  thứ cần nhìn (màu, hình). Kết quả ghi vào trang lộ trình (ghi chú "Claude tự test" / "CẦN BẠN TEST").
- Đã sửa: /ce menu ghi giá cũ (×3 quên dòng Giá); Chợ đen mua vào hộp "Đã mua" không ai biết -> give-item true; câu hết suất treo lộ
  "zauctionhouse.<số>"; OCM "OCM Damage" -> "Sát thương", bỏ "[OCM] Đã đặt chế độ đánh old"; Jobs/CMILib giờ phút giây; /kit "Gói Gói";
  MyPet chọn hệ lộ </gold> (sinh lại 230 cây); /hd thêm phong quân hàm (r10); chặn quảng cáo tên miền/IP trong chat (chat-filter.ads);
  Thăng Cấp giờ chơi < 1 giờ ghi phút; Thư viện đồ ô Hình riêng báo lỗi tại chỗ; gói tài nguyên Việt hoá tên đồ/khối/sinh vật/phù phép/
  chỉ số (tools/fontpack/lang-vi.py, đè en_us/en_gb). Quần tân thủ (mã quan) trong Thư viện đồ.
- **Chờ người dùng**: cấp quyền `essentials.sell.hand` + `.bulk` cho nhóm default server=banghoi-1 (Claude bị chặn cấp quyền) — người
  thường KHÔNG /sell hand được; đổi nghề tức thì sang nghề khác được (chờ 1 ngày chỉ áp nghề vừa rời) có chặn không; MyPet menu chọn hệ
  hiện mỗi hệ 2 lần (chưa rõ nguyên nhân); NPC cửa hàng Kinh Đô không thấy quanh spawn bằng lệnh (k13 người dùng đã test OK trước).
- Jar mới nhất (LevelUp xoá bảng xếp hạng web khi reset) đã build, CHƯA nạp (lần restart banghoi tới).

### Lộ trình test (QUAN TRỌNG khi đổi tài khoản)
- Trang test HIỆN TẠI (tài khoản lần 5, đã nạp 53 kết quả cũ + cụm mới "Thăng Cấp" cd1–cd20): https://claude.ai/artifact/XNwzopyMJVbRoYasBw5XeU
  Trang cũ YY55Wn87HYuMzL898MT8a7 thuộc tài khoản lần 4 (không dùng nữa). Chép kết quả mới về `docs/lo-trinh-test-ketqua.json` trước khi đổi tài khoản. Cụm Thăng Cấp: cd1–cd22.
  Đổi tài khoản lần nữa: đăng lại từ `docs/lo-trinh-test.html` (Artifact publish, capabilities {"db": {}}, icon "checklist"), rồi nạp kết quả từ
  `docs/lo-trinh-test-ketqua.json` vào collection `ketqua` (mỗi mục 1 doc {tt, ghi_chu, luc, sua?}; ArtifactData batch set).
- Kết quả tới 21/10 chiều (đã chép vào json): 53/187 mục có kết quả: 52 đạt (cb1 cb3, v1–v5 v8–v12, m1–m9, r1–r9 r11, k1–k10 k12–k22),
  1 LỖI: r10 "Phong quân hàm như nào" (cần hướng dẫn/menu cho /n rank add, quân hàm, min-rank-hours).
- Chưa test: m10–m17 (gói quà /kit, quà lần đầu, menu/tên tiếng Việt, /tprandom, đất hoang, /tpr tán cây, thanh tiến độ, /hd menu), k11,
  toàn bộ cụm 6 trở đi (Chế đồ, Mùa & câu cá, Quái/dungeon, Pet, Thị trấn, Chiến tranh, Hoà Bình, Thái Ấp, Đấu Trường, Web x1–x15,
  Thử phá s1–s24, Vận hành o1–o7). Danh sách test chi tiết: TEST-CHECKLIST mục 4x (xu), 4y (/hd), 4z (nghề + luật hoà bình).
- Trang định giá xu: https://claude.ai/artifact/SeZHthvXRXHusvgPqknXsh (nguồn `docs/dinh-gia-xu.html`, đã áp dụng, DB duyet2 trống).

### Đã làm trong phiên (21/10)
1. **Định lại xu toàn server** theo giờ công 1.400 xu/giờ (commit 27eefdd; chi tiết mục 00000 › VIỆC TIẾP THEO 2 + docs/dinh-gia-xu.html):
   phí ×3, quà định kỳ/một lần ×1,5 (cả Gacha, điểm danh…), giữ Thăng Cấp/Xưởng, Tinh Luyện 70% nguyên liệu, tiền khởi đầu 500.
   **Giới hạn bán cho server 8.000 xu/người/ngày** (SellLimit.java; /sell, nút bán /cuahang, Rìu Bán, Tinh Luyện).
2. **/hd = menu rương 18 nhóm** (Guide.java + huongdan.yml › menu): rê chuột thấy lệnh, bấm mở sách chỉ chương đó; /hd sach cả cuốn.
3. **Nghề: uJobs → Jobs Reborn 5.2.6.7 + CMILib, mỗi người 1 nghề** (8 nghề; tools/jobs/chuyen-ujobs.py sinh cấu hình + trang Cài đặt
   › Nghề 238 mục; Words_vi 1.970 tên; CMILib tiếng Việt tools/jobs/cmilib-vi.py; /jobs trơn mở menu (JobsMenu); đổi nghề chờ 1 ngày;
   vào lại nghề cũ mất 30% cấp (old-job.level-loss-percentage); +0,5% tiền/cấp, tối đa cấp 50. Jobs tự in "đã vào nghề" cả khi bị chặn
   -> đã tắt câu đó (messages_vi command.join.success = ''), DuckyMoMoCore tự báo khi vào được thật.
4. **Luật khu hoà bình** (PeaceAlliance): mua bán với server (/sell, /emf shop, /ce, /xuong, /tinhluyen, Rìu Bán, nút /menu gọi lệnh đó)
   và VÀO/RỜI nghề chỉ khi đứng trong đất Liên minh / vòng spawn; **đặc quyền dân hoà bình: ở đâu cũng được** (ghi ở /hoabinh, /hd, web,
   TAB). Xem /jobs ở đâu cũng được. /choilai vẫn rời nghề (Shared.JOBS_FREE). Cài đặt › Liên minh Hoà Bình: công tắc + danh sách lệnh.
   **Chợ đen /choden mở ở MỌI NƠI** (người dùng: "chợ đen vốn phi chính phủ"; auction-only-in-peace: false).
5. **Liên minh Hoà Bình ĐÃ LẬP** (LienMinhHoaBinh, thủ đô KinhDo, 1.521 ô quanh spawn, đang hoà bình). Sửa: TownRules không bắt kinh đô
   đủ 3 dân hoạt động; miễn phí duy trì quốc gia liên minh (quỹ có sẵn 300.000 xu); PeaceStatus.java đọc trạng thái hoà bình của SiegeWar 3.7
   (metadata siegewar_peaceSetting, KHÔNG phải neutral của Towny — trước đây tưởng chưa hoà bình mãi; cũng sửa RegionSpecialty, WarBalance).
6. **Shop rương (QuickShop)**: GIỮ (đã so với Chợ đen: khác — đặt ở thị trấn mình, không hết hạn, bán cả rương). Tạo: nhìn rương
   `/qs create <giá>` (chế độ sinh tồn); bấm trái shop = menu chọn số lượng, Shift+trái = mua 1 chồng (interaction.yml chế độ GUI).
7. **Đăng nhập giữ 30 ngày** đang test (AuthMe, trang Cài đặt, trang tài khoản) + phiên web lưu ra file — xem mục "KHI MỞ SERVER" bên dưới.
8. Khác: Thư viện đồ ô Mã tự sửa (iPhone tự viết hoa), PaySelfGuard chặn /pay cho chính mình, Towny phế tích (thị trấn xoá thành
   "Ruins" 24–72 giờ; xoá ngay: /townyadmin town <tên> delete rồi /confirm), lệnh admin xu: /eco give|take|set|reset <tên> <số>.

### Thăng Cấp (/capdo) ĐÃ LÀM LẠI (21/10 tối) — jar đã build, ⚠ CHƯA restart banghoi (Linh + ~b0ybn2013 online lúc build)
Code: LevelUp.java viết lại (config level-up mới, data + repo đã ghi; settings.schema + settings.yml nhóm Thăng Cấp viết lại, không trùng khoá),
SellLimit (duckymomo.sell.them.<n>, sell-limit.extra-per-perk), Workshop (giam / giam.2), PetCapture (duckymomo.pet.batgioi +5%).
Thuộc tính = AttributeModifier TẠM (addTransientModifier, khoá capdo_mau / capdo_nhanh) nên cộng dồn với AuraSkills (AuraSkills dùng khoá riêng,
force_base_health false); Sức mạnh/Bền bỉ qua sự kiện sát thương. Lần đầu chạy: migrate() gỡ quyền cửa hàng điểm cũ (phienban 2).
Người dùng: "cứ làm như nào cho hợp lý, khó vừa phải" -> đã hạ: xu 250 × N^0,6 (tổng tới cấp 10 ≈ 6.600 ≈ 4,7 giờ công, khớp 4 giờ chơi;
tới 100 ≈ 250.000), hạ gục 1 × N² (cấp 10 = 100, cấp 100 = 10.000), tẩy điểm 10.000. Giữ giờ chơi 0,04 × N². Giá đặc quyền: Xưởng cấp 20/20k + 50/80k;
bán thêm 15/15k, 35/40k, 60/100k; nghề, kỹ năng +10% cấp 25/30k; bắt pet 30/40k; +15% kinh nghiệm pet 20/30k; pet hồi sinh nhanh gấp đôi 35/40k;
Lão Luyện 75/80k. Pet: PetPerks.java (MyPetExpEvent qua phản chiếu; respawnTime giảm nửa 2 tick sau khi pet chết). Web (site + panel) đã dựng lại. Việc tiếp: kiểm online RIÊNG → restart banghoi-1 → xem log "Thăng Cấp:" → test cụm cd.

### (cũ) Thiết kế Thăng Cấp người dùng đã chốt
Người dùng: "shop toàn lợi ích trùng rank/dân… lv up có điểm cộng máu, tốc độ, sức mạnh", "theo hướng cày cuốc", "chỉ max được 2 thuộc tính".
- **Điểm thuộc tính**: 1 điểm/cấp + 1 điểm thưởng mỗi mốc 10 cấp = 110 điểm ở cấp 100 (max-level 100).
- **4 thuộc tính, mỗi cái 10 bậc, bậc k tốn k điểm (max 1 thuộc tính = 55 điểm) => cấp 100 max đúng 2 thuộc tính**:
  Máu +½ tim/bậc (max +5 tim, MAX_HEALTH); Sức mạnh +1%/bậc sát thương (max +10%); Bền bỉ −1%/bậc sát thương nhận (max −10%,
  EntityDamageEvent); Nhanh nhẹn +1%/bậc tốc chạy (max +10%, MOVEMENT_SPEED ADD_SCALAR). Tắt trong Đấu Trường (như AuraSkills);
  làm lại mỗi mùa cùng cấp. Áp AttributeModifier khi vào game / hồi sinh / đổi thế giới.
- **Đặc quyền mua bằng XU + cần đủ cấp** (không dùng điểm): giảm 5% giá Xưởng (2 lần), +1.000 xu giới hạn bán/ngày (3 lần, SellLimit đọc
  quyền), +10% kinh nghiệm nghề (Jobs: kiểm định dạng quyền jobs.boost.*), +10% kinh nghiệm kỹ năng (AuraSkills: kiểm quyền multiplier),
  +5% tỉ lệ bắt pet, danh hiệu Lão Luyện. Giá xu/cấp tối thiểu: tự đề xuất (vd giảm Xưởng: cấp 20 + 30.000 xu), báo người dùng.
  (+15% kinh nghiệm pet, pet hồi sinh nhanh: tuỳ có làm được không.) Chưa xác minh Jobs/AuraSkills có cộng kinh nghiệm theo quyền.
- **Bỏ** khỏi shop (trùng rank ủng hộ / danh phận): /craft, /anvil, /disposal, /hat, hiệu ứng hạt (hat2), pet mèo.
- **Lên cấp kiểu cày**: tổng giờ chơi thật cần tới cấp N = 0,04 × N² giờ (cấp 10: 4 g, 25: 25 g, 50: 100 g, 100: 400 g) — cần code
  (hiện hours-per-level tuyến tính 0,5); điểm hạ gục cũng tăng dần; xu mỗi cấp 500 × N^0,6 (tổng tới cấp 10: 13k, 25: 56k, 100: 500k).
  Lương Dân (cần cấp 10) / Hào Kiệt (cấp 25) vẫn đạt trong giờ chơi danh phận yêu cầu (24 g / 72 g).
- File: LevelUp.java (566 dòng), config level-up (data + repo), settings.schema nhóm "Thăng Cấp (/capdo)", huongdan + camnang + web.

### Câu hỏi đang chờ người dùng trả lời
- Lương nghề: giữ (≈170–210 xu/giờ, ~10% thu nhập) hay ×2 cả 8 nghề.
- Thoát nghề: giữ "vào lại mất 30% cấp" hay đổi (mất trắng / không trừ).
- Towny phế tích (Ruins 24–72 g): giữ, tắt hay rút ngắn.
- zMenu lệnh mẫu /pro_command (64 kim cương lấy 500 xu, quyền zmenu.pro, người thường không có): có gỡ commands.yml mẫu không.
- r10 hướng dẫn phong quân hàm; các đề nghị cũ ở mục 000000 (tắt kiểm tra cập nhật 403, staff bỏ chờ dịch chuyển, /daugia).

---

## 000000. (cũ hơn) — 21/10/2026 rạng sáng (chuyển tài khoản Claude lần 4)

Tóm cả phiên 21/10 (chi tiết từng việc: mục 00000 bên dưới, các dòng "- 21/10:"; test: TEST-CHECKLIST mục 4w). Commit cuối: xem `git log`.
Nhánh làm việc: claude/happy-ritchie-2p60v7. CHƯA push (người dùng chưa bảo).

### ⚠ TRẠNG THÁI: Bang Hội CHƯA khởi động lại sau các commit cuối
Lần restart banghoi cuối lúc ~02:40 (21/10). Sau đó đã build + commit nhưng CHƯA restart (Linh, vanlinh online, chờ người dùng bảo):
- Gacha thay vòng quay (jar mới + config) — trước restart, trong game vẫn là /hom + /vongquay cũ.
- Gỡ /doimanh (jar mới; config event-fragments.enabled false đã ghi).
- /nhiemvu tên nhiệm vụ hiện đúng số khi server vắng.
Panel + site ĐÃ dựng lại (đang hiện Gacha). Việc đầu tiên: hỏi người dùng rồi `docker compose restart banghoi-1` (kiểm online RIÊNG trước).
Sảnh (lobby) chưa restart từ lâu nhưng không bật các module này; restart khi tiện để nhận jar mới.

### Người dùng quyết trong phiên (giữ)
- MỘT tính năng = MỘT lệnh, KHÔNG tên phụ thừa ("bỏ quay với vongquay đi cho đỡ thừa nhiều lệnh"). Chợ: /choden (+ /cho). Gacha: chỉ /gacha.
- Chợ đen: /choden ban [số món] <giá CẢ LÔ>; /choden dat <số món> <giá MỖI MÓN>. Rút đơn thu mua chỉ ở "Đơn của tôi" (chuột phải) — người
  dùng nói KHÔNG cần sửa cho rút ở màn Thu mua (đã revert).
- Tiền hiện "xu", không "$".
- Đất hoang: cho đào, xây, phá tự do (world, nether, end).
- Bỏ /doimanh (mảnh sự kiện): boss chỉ cần rơi đồ thư viện (trang Boss › Đồ rơi).
- Bỏ /vongquay, giữ hệ hòm, đổi tên thành Gacha, "chìa" → "vé".
- /trade: nút đồng ý bê tông đỏ (chưa) / xanh lá (đã); đếm ngược 5 giây.
- Nghề (uJobs) KHÔNG cần chọn nghề (tự tính tất cả); thanh tiến độ có chữ đầu "Nghề ·" / "Kỹ năng ·".

### Đã làm 21/10 (build + triển khai; xem trạng thái restart ở trên; CHƯA thử hết bằng người)
- Gacha `/gacha` (Crates.java): máy Gacha Điểm Danh (vé chia_khoa_diemdanh trong Quà mỗi ngày /diemdanh) + Gacha Chăm Chỉ (vé chia_khoa_online,
  60 phút chơi thật, giữ tối đa 5, rank nhanh hơn qua duckymomo.vongquay.nhanh.<%>). Vé = món Thư viện đồ công dụng "Vé gacha" (mã chia-khoa).
  Cài đặt › Thưởng & sự kiện › Gacha (type crates). Vé vòng quay cũ tự đổi thành vé khi vào game. %dmmhom_chia%, %dmmhom_con%.
- Chợ đen /choden: hub 6 nút + lệnh con; /donhang bỏ; /ah ẩn Tab, gõ /ah trống mở hub; nút "← Về Chợ đen" ở màn Đồ đang bán / Hết hạn /
  Lịch sử / Đã mua (pattern zauctionhouse-choden); /ah xác nhận mua gần như cả màn là nút Mua.
- Tiền "xu": Essentials currency-format (data), zAH economies.yml (data), UJobs, AuraSkills, CE, Towny, nút /menu.
- Essentials tiếng Việt: "Đã gửi 10 xu đến …", "Đã nhận được 10 xu từ …", /near.
- /trade (TradePro) Việt hoá hết: gui.yml + config.yml (nút xu/XP, lời nhắc), đưa vào repo; /tradeplus → /tradepro.
- /tpr tránh tán cây (MOTION_BLOCKING phải trùng mặt đất), max-attempts 30.
- Towny: dịch lại thông báo cấm xây/phá; mở đất hoang 3 thế giới (data). Sau toggle phải `townyadmin reload townyperms` (cache quyền cũ
  làm chặn im lặng — đã gặp thật).
- /jobs: dòng "Tự tính khi làm, không cần chọn nghề".
- /nhiemvu: tên đúng chỉ tiêu khi LowPop giảm (title()).
- Gỡ /doimanh: event-fragments.enabled false, lệnh khỏi plugin.yml, nhóm Cài đặt, loại quà "manh" trong panel, nút chia mảnh trang Boss,
  hướng dẫn, web. FragmentShop.java còn nằm (không chạy).

### 21/10 sáng (phiên tài khoản mới, sau mục trên)
- **Định lại xu đã áp dụng** (xem mục 2 dưới) + **giới hạn bán cho server 8.000 xu/ngày** (SellLimit.java). Commit 27eefdd.
- **/hd thành menu rương 18 nhóm** (Guide.java + huongdan.yml › menu): rê chuột thấy lệnh + mô tả ngắn, bấm mở sách chỉ chương đó;
  /hd sach cả cuốn; Bedrock bảng nút. Người dùng: "dài như này ai mà muốn đọc", làm theo menu lệnh CherryMC. Commit 634a584.
- **Nghề: uJobs → Jobs Reborn 5.2.6.7 + CMILib** (người dùng: "chỉ cho chọn 1 nghề, nếu không member quá lợi"; uJobs không có chọn
  nghề, không có API chặn). SPIGET_RESOURCES 87610,4216 trong modes/banghoi/compose.yaml (bỏ ujobs ở MODRINTH). Cấu hình sinh bằng
  `tools/jobs/chuyen-ujobs.py` (bản mặc định Jobs ở tools/jobs/goc/): 8 nghề Thợ mỏ, Tiều phu, Thợ đào đất, Nông dân (+chăn nuôi),
  Thợ săn, Ngư dân, Phù thuỷ (phù phép theo món + pha thuốc), Thợ chế tạo; giá lấy đúng bảng uJobs đã định giá 21/10; max-jobs 1,
  rejoinCooldown 1 ngày, +0,5%/cấp tối đa 50, tắt điểm nghề/shop/nhiệm vụ nghề, không trả gần lồng quái/sáng tạo, locale vi
  (messages_vi.yml dịch phần người chơi thấy, còn lại tiếng Anh). Trang Cài đặt › Nghề (238 mục) trỏ vào plugins/Jobs/jobs/*.yml.
  DuckyMoMoCore: JobsMenu (gõ /jobs trơn → /jobs browse), PlayerReset dùng `jobs fireall`. Jar uJobs đã cất ở
  data/banghoi-1/plugins-da-go/ (thư mục dữ liệu UJobs còn). Cấp nghề cũ của uJobs KHÔNG chuyển (giai đoạn test). Thử trên server tạm
  trước khi đưa lên (8 nghề nạp, không sinh nghề mặc định). CHƯA thử bằng người: TEST-CHECKLIST 4z, lộ trình test k1, m16.
  Việt hoá thêm (21/10 trưa): tiêu đề menu nghề dùng [jobfullname] (Thợ mỏ, không còn Miner), Jobs/TranslatableWords/Words_vi.yml
  (1.970 tên khối/quái/phù phép/thuốc lấy từ vi_vn.json của game), CMILib Locale_VI.yml + config Language: VI (tools/jobs/cmilib-vi.py;
  CMILib CẦN KHỞI ĐỘNG LẠI banghoi mới nhận, chưa restart vì có người chơi khác online). PaySelfGuard (DuckyMoMoCore): chặn /pay cho
  chính mình (Linh gõ /pay Linh 10000 tưởng lỗi: trừ rồi cộng lại); jar đã build, CHƯA nạp (cần restart banghoi).
  Lưu ý: Spiget luôn lấy bản MỚI NHẤT (không ghim) — nếu một ngày Jobs/CMILib mới làm lỗi, ghim lại bằng link tải cụ thể.

### Liên minh Hoà Bình ĐÃ LẬP (21/10 13:16)
- Linh /dmm hoabinh setup tạo KinhDo, nhưng bước lập quốc gia bị TownRules chặn ("cần 3 dân hoạt động") -> đã miễn cho đúng kinh đô + tên liên minh,
  lập lại bằng console: townyadmin nation new LienMinhHoaBinh KinhDo; KinhDo 1.521 ô (claim 19), setpeaceful true.
- Phí duy trì quốc gia 300/ngày: đã nạp 300.000 xu vào quỹ liên minh để an toàn; code miễn phí duy trì quốc gia (onNationUpkeep) đã build,
  CHƯA nạp (cần restart banghoi). Thị trấn KinhDo vốn được miễn phí duy trì.

### ⚠ KHI MỞ SERVER CHÍNH THỨC: trả lại thời hạn đăng nhập (21/10 đặt 30 ngày cho giai đoạn test)
- Trong game (AuthMe, nhớ phiên theo IP): Cài đặt › Toàn mạng › Đăng nhập › Thời hạn phiên = 60 (phút) (đang 43200).
- Trang Cài đặt (admin): panel/app.py SESSION_HOURS mặc định 720 -> 12 (hoặc PANEL_SESSION_HOURS=12 trong .env).
- Trang tài khoản người chơi: site/app.py SITE_SESSION_DAYS 30 -> 7, SITE_ADMIN_HOURS 720 -> 12 (env hoặc sửa mặc định).
- Phiên web giờ lưu ra file (data/panel/phien-quantri.json, data/site/phien.json; chỉ mã băm) nên dựng lại panel/site không bị đăng xuất.

### Việc tiếp theo / chờ người dùng
1. Restart banghoi (xem trên) rồi người dùng test mục 4w.
2. ~~Định lại xu toàn server~~ **ĐÃ ÁP DỤNG 21/10 ~03:40** (người dùng: "cứ làm như vậy, có người chơi rồi điều chỉnh sau; check mọi chỗ
   dùng xu kể cả gacha, điểm danh"). Bảng + cách tính: `docs/dinh-gia-xu.html` (artifact SeZHthvXRXHusvgPqknXsh, db `duyet2` nếu người
   dùng sửa dòng). Giờ công 1.400 xu (1 giờ chơi chăm; /sell mới đã cho 1.400–1.600/giờ). Phí/dịch vụ ×3; quà định kỳ + một lần ×1,5
   (cả Gacha, điểm danh, nhiệm vụ, kit, vote, thành tích, kho báu, Lệnh săn, dungeon, thi câu, cốt truyện, AuraSkills, treo thưởng tự động);
   nghề theo nghề (lumberjack/traveler/trader ×1, miner/producer/witcher ×2, farmer/fisher/digger/husbandry/guardian ×3, hunter ×5);
   GIỮ Thăng Cấp money-base 500 (cấp 10 đã = 31 giờ chơi) và Xưởng; vé dungeon ×1,5; Thái Ấp/khai hoang/quỹ dungeon ×3; Tinh Luyện = 70%
   giá nguyên liệu; tiền khởi đầu 500; EliteMobs coin 0,6; giá cá ×2; thưởng bán kinh đô 20→10%. Vòng quay cũ + đổi mảnh (đã tắt) không đổi.
   Áp dụng bằng script chạy trong container panel (app.save_and_apply cho mục Cài đặt + ruamel sửa thẳng data VÀ bản repo: thành tích,
   kho báu, Lệnh săn, dungeon first-rewards, vote, Towny outpost/reclaim/bonus/takeover/neutrality/spawn travel/bail, CE LostBook, EMF thicau).
   SỰ CỐ ĐÃ SỬA: lần đầu chạy 2 lần → nhân chồng; viết lại tính từ giá gốc git (6e9ccf4) nên chạy lại không nhân; đã đối chiếu từng tỉ lệ.
   **Giới hạn bán cho server 8.000 xu/người/ngày** (SellLimit.java, config sell-limit, Cài đặt › Kinh tế, quyền bỏ qua
   duckymomo.sell.khonggioihan = op): chặn TRƯỚC khi bán (ước lượng theo worth.yml + giá Tinh Luyện; không bao giờ lấy đồ mà không trả),
   đếm sau khi bán (UserBalanceUpdateEvent COMMAND_SELL ở MONITOR + record() từ Xưởng/Tinh Luyện); DecorShop dùng performCommand (không qua
   PlayerCommandPreprocessEvent) nên hỏi giới hạn ngay trong DecorShop.sell. Dữ liệu banchoserver.yml. CHƯA thử bằng người (TEST-CHECKLIST 4x).
   Phát hiện chưa xử lý: zMenu ví dụ `/pro_command` (quyền zmenu.pro) cho 64 kim cương lấy 500 xu — người thường không có quyền, nên gỡ
   các lệnh ví dụ trong data/banghoi-1/plugins/zMenu/commands/commands.yml khi tiện.
3. Đề nghị chưa trả lời: tắt kiểm tra cập nhật làm hiện "Bad response status: 403" (packetevents/HoloMobHealth/GrimAC hỏi GitHub bị chặn);
   staff bỏ chờ dịch chuyển; đấu giá thật /daugia; lập thị trấn KinhDo + Liên minh Hoà Bình; hướng dẫn r10 "Phong quân hàm".
4. Món "Mảnh Lâm Nguy" (lamnguy) còn trong Thư viện đồ — người dùng có thể xoá.

### File chỉ nằm ở data/ (KHÔNG trong git) đã sửa 21/10
Essentials/config.yml (currency), zAuctionHouse/config.yml (alias), zAuctionHouse/economies.yml (xu), Towny/data/worlds/*.txt (đất hoang),
DuckyMoMoCore/thuvien-do.yml (tên vé). Bản trước khi sửa Essentials/TradePro: scratchpad phiên cũ (không còn) — muốn hoàn tác thì sửa tay.

---

## 00000. 20/10/2026 khuya → 21/10 (chuyển tài khoản Claude lần 3; chi tiết từng việc 21/10) (chuyển tài khoản Claude lần 3)

Hai phiên Claude chạy song song cùng repo trong tối 20/10 (một ở tài khoản cũ, một ở tài khoản mới). Commit mới nhất xem `git log`; việc
của phiên tài khoản cũ khuya 20/10 tóm ở đây (chi tiết: các dòng "- 20/10 tối/khuya:" cuối file + TEST-CHECKLIST mục 4v).

### Đã làm (đã build + triển khai, CHƯA thử hết bằng người)
- Tiền tố chỉ hiện MỘT (nhóm weight cao nhất), bỏ &l ở tiền tố rank ủng hộ.
- Khung tooltip chung `Tip.java` cho mọi menu DuckyMoMoCore + menu plugin ngoài (/ah, /jobs, /ce, QuickShop, mô tả hệ pet). Quy tắc: menu
  mới dựng bằng Tip (xem memory menu-tooltip-style).
- `/tprandom` của Essentials đã tắt (đưa người ra ngoài bản đồ làm /tphere kéo sai chỗ), dùng /tpr của server.
- `/ah` mở trong vòng kinh đô quanh spawn (chưa lập Liên minh Hoà Bình / thị trấn KinhDo; VIỆC CÒN: admin lập KinhDo + LienMinhHoaBinh).
- **Cửa hàng Kinh Đô = chợ server đầy đủ** (người dùng gửi ảnh CherryMC): 10 danh mục, 764 món, mỗi lần mua 1 món, giá theo từng món,
  quặng/kim loại/đá quý chỉ bán; trái mua / phải bán 1 / Shift+phải bán hết (qua /sell Essentials). Bảng giá: `tools/shop/bang-gia.py`
  (ghi config repo + data, settings.yml decor_categories, data/.../Essentials/worth.yml 772 giá /sell). Cài đặt: mỗi danh mục một khung,
  dòng mẫu `*_WOOL`. Sửa lỗi: trùng mã món (bấm xương ra đá cuội), Essentials sell-multipliers ×3 cho admin (đã bỏ double/triple).
- 21/10: `/ah` màn xác nhận mua gần như cả màn là nút Mua (plugin không cho bỏ màn này); Vòng quay bố cục mới (4 hàng phần thưởng).
- 21/10: **Chợ đen** `/choden` (/cho): gộp chợ người chơi `/ah` (giá cố định, không phải đấu giá) + thu mua `/donhang` vào một trang.
  Đấu giá thật (/daugia) để sau nếu người dùng muốn.
  Sau đó (21/10) chỉ còn MỘT lệnh /choden (+ /cho): lệnh con mua, ban, cuatoi, hethan, lichsu, nhantien, thumua, dat, don, nhan, huy.
  /donhang đã bỏ; /ah ẩn khỏi Tab (người chơi thường), gõ /ah trống mở Chợ đen (Orders.onAhTyped; Chợ đen gọi /ah nội bộ qua market()).
  zAuctionHouse config.yml (CHỈ có ở data/, không trong git): alias chính chỉ còn `ah`, sell chỉ `sell`.
- 21/10: Tiền hiện "xu" thay "$" khắp server: Essentials config.yml (CHỈ ở data/) currency-symbol ' ' + suffix true +
  currency-format "#,##0.##' xu'" (Essentials chỉ nhận ký hiệu 1 ký tự nên chữ xu nằm trong mẫu định dạng; Vault dùng chung → Towny,
  SiegeWar, QuickShop… theo). zAuctionHouse economies.yml (data) format "%price% xu"; UJobs symbols.money ' xu'; AuraSkills, CE, Towny
  sửa chữ $ viết cứng. /choden ban [số món] <giá CẢ LÔ> (người dùng chốt), /choden dat <số món> <giá MỖI MÓN>.
- 21/10: /tpr không thả dưới tán cây (thử 30 chỗ); dịch lại thông báo Towny cấm xây/phá ở đất hoang.
- 21/10: ĐẤT HOANG MỞ (người dùng: "phải cho đào, không thì làm nhiệm vụ, jobs kiểu gì"): /townyworld <w> toggle wildernessuse on cho
  world, world_nether, world_the_end (xây, phá, bật/mở, dùng đồ). Lưu ở data/.../Towny/data/worlds/*.txt (không trong git).
  em_adventurers_guild, thaiap, dautruong giữ khoá. Mặc định config.yml unclaimed_zone_* vẫn false (thế giới mới tạo sau vẫn được khoá).
  Spawn được bảo vệ riêng bằng vùng WorldGuard "spawn" (build deny), không dựa vào luật đất hoang.
- 21/10: **GACHA `/gacha` thay hẳn /vongquay** (người dùng: bỏ một, giữ hòm; đổi tên vì "hòm" khó hiểu; chỉ MỘT lệnh /gacha, không tên phụ).
  LuckySpin.java đã xoá, lucky-spin.enabled false, nhóm Cài đặt Vòng quay gỡ. Chìa → "vé" (mã nội bộ vẫn chia-khoa / chia_khoa_*; vật phẩm
  đổi sang PAPER, đồ cũ dạng móc vẫn dùng được vì nhận theo mã). Vé Chăm Chỉ: 60 phút, giữ tối đa 5 (online-key.max), rank nhanh hơn qua
  quyền duckymomo.vongquay.nhanh.<%> (giữ tên). Vé vòng quay tồn đổi thành vé Chăm Chỉ khi vào game. Placeholder %dmmhom_chia%/%dmmhom_con%
  (TAB, /menu). Web: thẻ từng máy gacha + tỉ lệ; trang tài khoản: số lần đã quay.
- (cũ, trước khi đổi tên) 21/10: **Hòm quay** `/hom` (Crates.java, config `crates`, Cài đặt › Thưởng & sự kiện › Hòm quay): nhiều hòm, mỗi hòm một chìa = món
  Thư viện đồ công dụng `chia-khoa` (chọn chìa bằng ô chọn; quà chọn Vật phẩm thường hoặc Món thư viện). Mặc định Hòm Điểm Danh (chìa
  `chia_khoa_diemdanh`, nằm trong Quà mỗi ngày của /diemdanh) + Hòm Chăm Chỉ (chìa `chia_khoa_online`, chơi thật 90 phút = 1 chìa,
  đếm phút ở hom.yml). Cầm chìa bấm chuột phải mở đúng hòm. "Chìa lấy từ đâu" tự dò điểm danh / mốc / nhiệm vụ / chìa online.
  /vongquay (vé theo giờ chơi) VẪN chạy song song: trùng nguồn "chơi online" với Hòm Chăm Chỉ, chờ người dùng chọn giữ hay tắt.

### VIỆC TIẾP THEO (theo thứ tự)
1. **Định lại xu toàn server theo bảng giá mới** — người dùng đồng ý làm, nhắc "check kỹ đừng sót phần nào dùng xu". Claude đã gửi BẢNG
   ĐỀ XUẤT (chờ người dùng duyệt / sửa dòng): mốc 1 giờ chơi chăm ≈ 1.500–2.000 xu; phần lớn ×5 (nghề UJobs mọi action, tiền khởi đầu
   Essentials starting-balance + player-reset 200→1.000, điểm danh 100→500 và mốc 500/1.000/3.000→×5, nhiệm vụ, vòng quay, gói quà, thành tích,
   kho báu, Lệnh săn, dungeon first-rewards/weekly, vote, thi câu EMF MONEY, BetonQuest c1_thuong_*/lk_thuong, Thăng Cấp money-base + mốc,
   Người Tiên Phong, đổi mảnh, Đấu Trường win-reward/max-stake, bounty min/streak/per-kill, mercenary min/max, Towny new town/nation/outpost/
   claim/upkeep, SiegeWar warchest/plunder per plot, colony upkeep, siege-supply cost, peace plot/fee, tổ chức weekly-fee, justice floor-fine,
   danh hiệu cửa hàng, vé + goal dungeon, wild-gates max-toll, QuickShop fee-for-price-change/cost-per-shop, zAH price max); NGOẠI LỆ: EliteMobs
   currencyShowerTierMultiplier ×3 (0,3→0,9, shop EM giá cố định), Thái Ấp ×4 (20k/32k/60k), Xưởng ×2, mốc quỹ khai hoang ×4, giá cá EMF
   worth-multiplier ×3, giá bán món Tinh Luyện = 70% giá trị nguyên liệu theo worth mới; KHÔNG đổi: % phí, Vàng/giá rank (tiền thật).
   Danh sách đầy đủ chỗ dùng xu đã quét: settings.schema banghoi (+_network), DuckyMoMoCore config (rewards "xu N", cost/price/fee…),
   Towny config (price_*), SiegeWar, Essentials (starting-balance, sell-multipliers), EliteMobs EconomySettings, QuickShop, zAuctionHouse,
   EvenMoreFish (rarities worth-multiplier, competitions MONEY), BetonQuest package constants, UJobs jobs.yml, MyPet EconomyCost (0), PvPManager (0).
   Áp dụng: sửa settings.yml + data configs (panel apply-all) và cấu hình repo; kiểm lại bằng script quét như trên để không sót.
   **20/10 khuya (tài khoản mới): quét lại + đăng TRANG DUYỆT https://claude.ai/artifact/SeZHthvXRXHusvgPqknXsh** (nguồn `docs/dinh-gia-xu.html`,
   db collection `duyet`, mỗi dòng {tt: ok|sua|bo, gia, ghi_chu, luc}; 70 dòng t01–t28 thu nhập, c01–c30 chi phí, l01–l12 Tinh Luyện 70%).
   So với bảng cũ: BỎ kho lương (siege-supply là điểm lương thực, không phải xu); THÊM AuraSkills as_xu5–20, giới hạn Shopkeepers 1.000/ngày,
   sách/cuộn/Lost Book /ce, MyPet hồi sinh 1 xu/giây, Towny reclaim/takeover/neutrality/spawn travel/bail, /ah min 10; sửa ô đất 25→125
   (bảng cũ 100), đất Hoà Bình 125 (+2,5). Lệch: settings reset_money = 0 nhưng data player-reset.starting-money = 200.
   Bước sau: đọc `duyet` → áp dụng vào settings.yml (mục có key) + data/repo config (mục không có key: thành tích, kho báu, Lệnh săn,
   dungeon first-rewards, vote, EMF thicau, Towny chuỗi, CE LostBook) → dmm reload / khởi động lại banghoi-1 (kiểm online trước).
2. Người dùng đang thử trên trang lộ trình test CŨ (HFv53…, tài khoản claude.ai cũ). Kết quả mới nhất đã gộp vào `docs/lo-trinh-test-ketqua.json`
   (cụm Danh phận r1–r10): nạp lại vào trang của tài khoản mới (YY55… hoặc đăng lại). r4 đã sửa ("lai"). **r10 LỖI "Phong quân hàm như nào"**:
   cần hướng dẫn / làm cách phong quân hàm dễ hiểu (Towny nation rank: `/n rank add <tên> <quân hàm>`, quân hàm trong town-rules.military-ranks,
   cần min-rank-hours giờ chơi, tài khoản phụ không được) — có thể thêm menu hoặc giải thích trong /hd + lộ trình test.
   **ĐÃ GỘP (20/10 khuya, tài khoản mới):** r1–r10 đã nạp vào YY55 (Version 4); repo JSON nhận m1/m3/m7/m8 = ok từ YY55 → hai nơi khớp (30 mục).
   Trang thêm 12 mục mới (mã mới, mã cũ giữ nguyên): m10 /kit theo rank, m11 quà lần đầu, m12 tiêu đề menu + tên đồ tiếng Việt, m13 /tprandom /tphere,
   r11 một tiền tố, k13 chợ /cuahang, k14 khoáng sản chỉ bán, k15 menu plugin ngoài, x12 Bộ (tag) thư viện, x13 Cài đặt xếp gọn, x14 Reset người chơi,
   x15 hết hộp thoại trình duyệt. Trang cũ HFv53 KHÔNG dùng nữa; phiên tài khoản cũ chỉ sửa `docs/lo-trinh-test*` rồi báo người dùng.
3. Thử bằng người: cửa hàng mới (mua/bán đúng món, đúng giá, phân trang, Bedrock), tooltip mọi menu, /jobs dòng thừa khi nghề ít việc,
   tiền tố, /tprandom.

---

## 0000. BẮT ĐẦU Ở ĐÂY — tình trạng 20/10/2026 tối (chuyển tài khoản Claude lần 2)

### Trạng thái
- Nhánh `claude/happy-ritchie-2p60v7`, commit mới nhất `67b0872`. Khoảng **200 commit CHƯA push** (người dùng chưa bảo push).
  `site/config/site.yml` đang sửa (số tài khoản ngân hàng): KHÔNG commit (luôn `git add -A -- . ':!site/config/site.yml'`).
- Mọi thứ trong mục này ĐÃ build + triển khai: banghoi-1, lobby, practice-1, sangtao-1 chạy DuckyMoMoCore mới; panel đã build lại;
  proxy chạy DuckyMoMoSkin (Tủ skin). Kiểm `docker compose config` trong worktree tạm: OK.
- **Giai đoạn thử nghiệm (người dùng 20/10):** "cứ reset đi, đang test thôi, không phải check online" → được khởi động lại KHÔNG cần hỏi.
  Khi mở cửa chính thức thì quay lại quy tắc kiểm online (mục 000, bài học 1). Proxy khởi động lại thì VẪN phải tạo lại relay.
- Người dùng đang tự thử toàn bộ theo **lộ trình test** (trang artifact có lưu tích Đạt/Lỗi/Bỏ qua + ghi chú).
  - Bản gốc: https://claude.ai/artifact/HFv53FeegS1PvHQCmxUTBL (thuộc tài khoản claude.ai CŨ; tài khoản mới không sửa được).
  - **Đã đăng lại cho tài khoản mới (20/10): https://claude.ai/artifact/YY55Wn87HYuMzL898MT8a7** (db, đã nạp 21 kết quả cũ). Dùng link này.
  - Đã chép vào repo: `docs/lo-trinh-test.html` (nguồn trang, 17 cụm) + `docs/lo-trinh-test-ketqua.json` (kết quả lúc 20/10 tối:
    17 Đạt, 3 Lỗi, 1 Bỏ qua). Trang lưu kết quả ở database artifact, collection `ketqua`, mỗi mục 1 document id = mã mục
    (`{tt: ok|err|skip, ghi_chu, luc}`).
  - **Tài khoản mới: đăng lại trang** từ `docs/lo-trinh-test.html` (Artifact publish, capabilities {"db": {}}, icon "checklist") → được
    URL mới, rồi nạp lại kết quả cũ bằng ArtifactData batch (set `ketqua/<mã>` từ file json). Báo người dùng URL mới.
  - Sửa lộ trình (thêm mục cho tính năng mới): sửa mảng PLAN trong html (giữ nguyên id cũ để kết quả không lệch), đăng lại cùng URL.
  - **3 mục LỖI người dùng ghi: ĐÃ SỬA 20/10 tối, gắn nhãn "Cần test lại" trên trang (chi tiết dưới, TEST-CHECKLIST 4u):**
    1. m1 Quà tân thủ: "Mặc định vào không có gì, phải nhận từ kits mới có. Cài đặt có mục kits chưa, bao lâu nhận lại được. Mỗi rank
       một bộ kits khác nhau. Trong kits có đồ mặc định, đồ trong thư viện, xu…" → làm hệ kit theo rank trên Cài đặt, chọn đồ từ Thư viện đồ.
    2. m7 Lệnh nhà/warp/tpa: "Bổ sung /back cho người chơi" (hiện người chơi không có /back).
    3. m8 /caidat: "Tắt nhận TPA nhưng vẫn nhận được" → lỗi, kiểm PlayerSettings chặn tpa (có thể Essentials /tpa đi đường khác, vd /tpahere, /etpa).
- Danh sách tay: `docs/TEST-CHECKLIST.md` (mục 4t mới cho 20/10).
- **Người dùng test lại 20/10 tối:** m7 (/back), m8 (TPA) ĐẠT. m3 người dùng ghi "Bỏ qua tính năng này" (bấm Lỗi) → đã TẮT bảo vệ người mới
  (pvp_newbie_enabled false qua API Lưu của panel, PvPManager reload), web trang chủ ẩn dòng "15 phút được bảo vệ" (`data-hide0`), sách /hd trang
  "Mới vào" sửa, m3 gắn "cần test lại". Mọi hộp thoại trong panel/site dùng `ask()` của shell.js, KHÔNG dùng confirm/alert/prompt (người dùng yêu cầu).
- **Bộ (set) trong Thư viện đồ (20/10 khuya):** mục `bo: [..]` của món trong thuvien-do.yml (panel lib_extra, tối đa 8 bộ, sửa được cả đồ lấy
  từ game; ItemLibrary bỏ qua). thuvien.html: ô Bộ (chip + gợi ý bộ có sẵn), nút lọc theo bộ trên danh sách (nhớ localStorage dmm-tv-set).
  index.html: libSelect dùng combo (tìm theo tên/mã/đầu mục/bộ, vẫn có .value/.dataset.v như select), rewardsEditor có "+ Thêm cả bộ…".
- **Reset người chơi (20/10 tối):** trang Hệ thống › Reset người chơi → API hethong action "reset" → RCON banghoi `choilai admin <tên> [moi] [giurank]`
  (PlayerReset.adminReset). Module có dữ liệu theo người chơi đăng ký `Shared.FORGET` (Shared.forget xoá mọi khoá = UUID trong file của module):
  DailyCheckin, DailyQuests, LuckySpin, Achievements, LevelUp, Kits. Online: doReset(admin) → quên module → LuckPerms (meta dmmcaidat.*, nhóm bậc
  Danh phận; không giurank thì mọi nhóm trừ default) → kick → PlayerQuit +2s xoá world/players/{data,stats,advancements}, Essentials, AuraSkills.
  Sao lưu đã sửa đường dẫn 26.x (world/players/data, trước đây chép nhầm world/playerdata nên KHÔNG có bản sao playerdata).
  Hệ thống quyền Claude Code từng CHẶN Claude tự dừng server + xoá file người chơi: reset người thật để người dùng bấm trên trang.
- **Gói quà tách theo rank (20/10 tối):** Kits.allowed() xét NHÓM THẬT trong LuckPerms (getNodes(INHERITANCE) + getInheritedGroups),
  không dùng hasPermission (admin có * từng thấy mọi gói nhận được). Nhóm Danh phận (default=Tị Nạn, dan, luongdan, haokiet; đọc từ
  play-ranks.tiers) chỉ tính bậc HIỆN TẠI; nhóm khác phải gán trực tiếp. Ô "gói quà" của rank (play-ranks … kit) cũng trỏ gói. Thêm gói
  `tinan`; dan/luongdan/haokiet mỗi gói đúng 1 rank. Menu xếp gói nhận được lên trước. Ô "lần đầu" (goiqua_landau) người dùng đã để TRỐNG
  khi thử trang (17:xx) → người mới không tự nhận quà; đã báo, chưa tự đổi lại.
- **Tiêu đề menu (GUI) chữ tối:** nền rương xám sáng nên tiêu đề dùng &0 (chính) + &8 (phụ), KHÔNG dùng &6/&e/&7/&a/&b. Đã sửa 20 tiêu đề trong
  DuckyMoMoCore + menu chính (config player-menu.title) + tiêu đề menu Shopkeepers (lang/language-vi.yml).
- **Trang Cài đặt xếp gọn (20/10 tối, panel/index.html):** mỗi cài đặt là thẻ nhỏ trong lưới nhiều cột (`.gbody` grid auto-fill 290px);
  công tắc chữ ngắn nằm cùng dòng tên; chữ dài/danh sách rộng gấp đôi; trình soạn lớn trải hết. Nhãn dạng "Chủ thể: mục" lặp ở ≥2 chủ thể và
  ≥2 mục tự gộp thành BẢNG so sánh (`matrix()`/`matrixEl()`, ô dùng chung `fillCtl()`), vd Danh phận 4 bậc × 10 mục, rank khác, mùa, sách CE.
  Muốn nhóm nào hiện thành bảng: đặt nhãn đúng dạng "Chủ thể: mục" trong schema.
- **Sửa 20/10 tối (đã build + khởi động lại 4 server + panel):**
  - m1 → module **Gói quà** `Kits.java` (config `goi-qua`, dữ liệu `goiqua.yml`): lệnh `goiqua`, alias `kit`, `kits` trong plugin.yml.
    Server KHÔNG bật goi-qua thì DuckyMoMoCore.setup xoá kit/kits của mình khỏi CommandMap.getKnownCommands() (lệnh plugin.yml đăng ký trước
    onEnable, DuckyMoMoCore bật trước Duels/Essentials) để Practice giữ `/kit edit` của Duels (đã thử RCON: banghoi /kit = Gói quà, practice = Duels).
    Đổi hướng bằng PlayerCommandPreprocessEvent.setMessage KHÔNG ăn với người chơi trên Paper 26 (chỉ console) → đã bỏ.
    Gói: quà dạng Rewards, `cho` giây (-1 một lần), `rank` nhóm LuckPerms (trống = mọi người; quyền essentials.kits.<mã> từ ô "gói quà"
    của rank cũng được). Gói lan-dau (tanthu) phát khi `!hasPlayedBefore()` (Essentials newbies.kit đã tắt). Cài đặt: kiểu panel mới `kits`
    (trình soạn trong panel/index.html, kiểm ở app.py). /choilai gọi "kit tanthu <tên>" → đổi hướng sang gói. Admin: /goiqua <mã> <tên>, /goiqua lamlai.
  - m7 → PlayRanks `back`, `back-ondeath` (Cài đặt › Thế giới › Lệnh /back cho người chơi): đặt essentials.back(.ondeath) cho 4 nhóm Danh phận,
    ngữ cảnh banghoi-1 (đã kiểm trong DB LuckPerms; quyền staff global giữ nguyên).
  - m8 → KHÔNG phải lỗi: log 15:15 cho thấy /tpa gửi từ Linh (admin có duckymomo.caidat.bypass qua *). Đã thêm: staff vượt công tắc thì thấy
    dòng xám báo; TPA tìm tên kiểu Essentials (một phần tên). Phải test bằng 2 tài khoản thường.
  - Trang lộ trình test có trạng thái mới `lai` (nhãn "Đã sửa · cần test lại", bộ lọc, trường `sua` ghi đã sửa gì). Claude sửa xong mục nào thì
    `ArtifactData update ketqua/<mã> {tt: "lai", sua: "...", luc: <ms hiện tại>}` (luc phải lớn hơn bản trên máy người dùng).

### Quy tắc mới 20/10 (người dùng yêu cầu, giữ)
- **KHÔNG chữ in đậm** (&l, §l, <bold>, <b>) ở mọi chữ người chơi thấy: font tiếng Việt vẽ đậm bị nhòe như 3D. Thêm plugin/bản dịch:
  `python3 tools/bo-in-dam.py --ghi`.
- **Mọi vật phẩm đặc biệt chỉ tạo/sửa ở Thư viện đồ**; chỗ khác chỉ CHỌN món + giữ thông số riêng (chi tiết: ghi chú 20/10 cuối file
  và cẩm nang "Thư viện đồ"). Thêm tính năng có vật phẩm mới thì làm công dụng mới trong thư viện (ItemLibrary.STAMPERS), đừng tự dựng đồ.
- Kiếm tiền: tiền thật → **Vàng** (1.000đ = 1 Vàng, chỉnh ở Cài đặt) → mua rank ủng hộ / đồ trang trí / tên. Không chuyển Vàng, không đổi ra xu,
  không pay-to-win, không bán gì Pokémon.
- Giới hạn tài khoản: TỔNG tối đa 2 tài khoản / IP (vĩnh viễn), miễn theo tên thành viên hoặc IP (Cài đặt › Toàn mạng › Giới hạn đăng ký).
- Đổi tên đã chốt: rank Media → **Sứ Giả** (/sugia, nhóm LuckPerms vẫn `media`); Bảng truy nã quái → **Lệnh săn** (/lenhsan);
  Quảng trường Vinh danh → **Quảng trường Danh vọng** (/danhvong).

### Làm trong 20/10 (theo thứ tự; chi tiết ở các dòng "- 20/10:" cuối file + git log)
- Trang chủ: mục Giới thiệu, sửa chữ hero, ô chế độ (Thái Ấp Sáng Tạo, Skyblock, Lifesteal sắp có, Pokémon dự kiến), bảng Cổng Dungeon chia tab.
- Mùa (Seasons): xương rồng, mía, tre, tảo bẹ lớn đúng tốc độ mùa; dưa/bí thêm sản lượng; Lũ lúa 0.5; bột xương KHÔNG bị ảnh hưởng (người dùng chốt).
- Panel: đăng nhập bằng form + cookie (bỏ hộp thoại Basic Auth).
- Bỏ chữ đậm toàn server (repo, data, prefix LuckPerms, menu sảnh).
- Tủ skin `/tuskin` (plugin proxy `network-core/core-velocity`, DuckyMoMoSkin): số ô theo danh phận/rank trên Cài đặt; web tải PNG/link/tên bản quyền.
- AuthMe: mật khẩu phải có chữ + số, sai không bị đá, 90 giây, tắt captcha chữ, chào tiếng Việt. Sonar: captcha bản đồ cho người mới
  (kiểu modern, nhớ 7 ngày, 5 lần sai chặn IP), tối đa 5 người online / IP. Tin nhắn Velocity tiếng Việt.
- Giới hạn đăng ký 2 tài khoản / IP + whitelist theo tên/IP.
- Font tiếng Việt tuỳ chọn từng người (/caidat + web). Menu chính `/menu` (Shift+F) ở Bang Hội Chiến.
- Essentials luôn tiếng Việt (per-player-locale false). QR VietQR tự vẽ (segno) vì img.vietqr.io trả SVG.
- **Vàng**: ví MariaDB `network.vang_vi/vang_log` (panel/vang.py), /vang, /rank mua, QR đặt thẳng vào tay, trang tài khoản thẻ Vàng,
  trang Donate · Vàng (duyệt = cộng Vàng, cộng/trừ tay, sửa món bán lẻ), %dmmvang_so_du% trên bảng bên phải.
- **Thư viện đồ trung tâm** (commit 67b0872): đầu mục + công dụng; Xưởng/bếp/Tinh Luyện/mảnh/bùa/boss/phần thưởng chọn từ thư viện;
  đồ rơi boss (BossDrops, `boss-roi.yml`, trang Boss mục 6); 41 món cũ thành đồ mẫu `thuvien-mau.yml`.

### Chưa thử bằng người (người dùng thử, gửi ảnh thì sửa)
- Thư viện đồ trong game: chế đồ Xưởng (kỹ năng chạy?), nấu + ăn món (/nauan), `/buabatpet give`, Xu Quái rơi, `/tinhluyen`, mảnh
  `/doimanh give`, đồ rơi boss (đặt đồ rơi cho 1 boss trên trang Boss, gọi boss, đánh chết). Đã kiểm: 42 món dựng được, panel lưu/chặn đúng.
- /vang menu + QR trên tay + thẻ Vàng web; Tủ skin trong game; câu thông báo Lệnh săn; công tắc Font tiếng Việt; /menu.

### Việc chờ người dùng quyết
- Lệnh `/rank` và `/pet`, `/pp` trùng giữa sảnh và Bang Hội Chiến: đã hỏi có xoá ở sảnh không, CHƯA trả lời.
- Đã gợi ý (chưa làm): nút từ nhóm Vàng trên Cài đặt sang trang Donate.
- Các mục cũ ở "Việc tiếp theo" của mục 000 vẫn còn.

---

## 000. BẮT ĐẦU Ở ĐÂY — tình trạng 19/10/2026 sáng (chuyển sang tài khoản Claude khác)

### Người dùng và cách làm việc (giữ nguyên)
- Chủ server: Linh (admin trong game). Trả lời **tiếng Việt**, ngắn gọn, nói rõ chỗ chưa chắc. Người dùng hay gửi ảnh chụp server khác
  (UniMC, CherryMC) để lấy Ý TƯỞNG / LOGIC: chỉ học logic, KHÔNG chép y hệt chữ, hình, tên của họ.
- Người dùng thích: cái gì cũng chỉnh được trên **trang Cài đặt** (panel); có gì mới thì ghi vào **cẩm nang** (`modes/_network/camnang.yml`),
  lệnh vào `lenh-con.yml` + `lenh-vi.yml` (+ `lenh-hay-dung.yml`), người chơi cần biết thì `huongdan.yml` + `./ops/huongdan.sh`,
  và có mặt trên **web** (trang chủ khi chưa đăng nhập + trang tài khoản khi đăng nhập). Việc chưa thử ghi vào `docs/TEST-CHECKLIST.md`.
- Commit theo từng việc (kèm dòng Co-Authored-By theo hệ thống), **push chỉ khi người dùng bảo**. `site/config/site.yml` (số tài khoản ngân hàng)
  KHÔNG commit. Nhánh: `claude/happy-ritchie-2p60v7`. Lúc ghi mục này còn ~18 commit CHƯA push (người dùng chưa bảo push).
- Mọi việc (build, khởi động lại, RCON, Docker) làm trên máy nhà: WSL Ubuntu-22.04, repo `/home/developer/works/DuckyMoMo`.

### BÀI HỌC (đã gây sự cố thật — làm đúng)
1. **Kiểm tra người online TRƯỚC khi khởi động lại, bằng một lệnh RIÊNG** (`for s in lobby banghoi-1 practice-1; do docker compose exec -T $s
   rcon-cli minecraft:list; done`), đọc kết quả, có người thì hỏi người dùng. KHÔNG gộp lệnh kiểm tra với lệnh restart (18/10 đã đá Linh ra).
2. **Proxy (Velocity, itzg mc-proxy):** thêm plugin vào `PLUGINS` hoặc thêm biến `CFG_*` thì phải `docker compose up -d --force-recreate proxy`
   (restart KHÔNG nhận env mới). File trong `proxy/config/` có `${CFG_…}` chỉ được điền khi chép sang `data/proxy`; bản chép cũ mới hơn thì KHÔNG
   chép lại → lỡ chép thiếu biến thì xoá file trong `data/proxy/plugins/…` rồi force-recreate.
3. **Tạo lại proxy thì PHẢI tạo lại relay** (cổng VPS dùng chung mạng với proxy): `docker compose --profile tunnel up -d --force-recreate relay`,
   rồi kiểm `docker exec duckymomo-relay-1 wg show` có "latest handshake". Quên là người ngoài KHÔNG vào được server (19/10 đã bị).
4. **Sảnh và Practice không có PlaceholderAPI / Towny / MyPet:** lớp nào có thể nạp ở đó thì KHÔNG được tham chiếu PlaceholderExpansion hay Towny
   (tách lớp *Papi riêng như SeasonsPapi, LevelUpPapi; tham chiếu chéo qua `Shared.java`). Sau khi sửa DuckyMoMoCore, khởi động lại sảnh và
   xem log có "Enabling DuckyMoMoCore" mà không NoClassDefFoundError.
5. **Trang Cài đặt:** thêm khoá cài đặt thì kiểm trùng khoá bằng ruamel trong container panel (`docker exec -i duckymomo-panel-1 python3` +
   `ruamel.yaml.YAML().load`); trùng khoá là hỏng cả trang. Chuỗi trong YAML có dấu " hay ": " thì dùng «» hoặc đặt trong ngoặc kép.
6. **Sửa JS của site/panel:** tách khối <script> chạy `node --check`, rồi mở bằng Playwright (`/usr/bin/google-chrome`) bắt `pageerror`.
   Site và panel là image build: sửa `site/` hoặc `panel/` xong phải `docker compose up -d --build site panel`.
7. **Lệnh kiểm tra trên server thật chỉ dùng loại ĐỌC** (`execute if block`…), không `fill`/`setblock` (19/10 lỡ chạy một lệnh fill, may không mất gì).
9. **Citizens 2.0.44 b4265 (26.2) làm MẤT NPC sau mỗi lần khởi động lại**: lưu `type: minecraft:player` nhưng nạp chỉ hiểu `PLAYER`
   (log "NPC type minecraft:player was not recognized", "Loaded 0 NPCs"). DuckyMoMoCore.onLoad sửa `plugins/Citizens/saves.yml` trước khi
   Citizens đọc (log "Citizens saves.yml: sửa kiểu NPC…"). Nâng Citizens lên bản mới thì kiểm lại: nếu Citizens đã sửa thì bỏ đoạn onLoad.
8. **Không chạy hai lệnh nối nhau bằng `;` khi lệnh sau phụ thuộc lệnh trước thành công** (19/10 commit lỡ một file YAML hỏng vì kiểm tra lỗi
   nhưng commit vẫn chạy): dùng `&&`.

### Hạ tầng hiện tại (19/10)
- Người chơi (cả chủ server) vào qua **VPS** 125.212.218.74 / `mc.goc6.io.vn` (Java 25565, Bedrock 19132), relay WireGuard giữ IP thật.
  **playit ĐÃ BỎ** (19/10: container + `tunnel/` đã xoá; `PLAYIT_SECRET_KEY` trong .env không còn dùng).
- Server: proxy (Velocity + Geyser/Floodgate, AuthMe-Velocity, SkinsRestorer, **LuckPerms-Velocity** mới 19/10), lobby (tối đa 200 người),
  banghoi-1 (100, RAM 6G), practice-1 (100). MariaDB + Redis dùng chung (LuckPerms, AuthMe…).
- AuthMe: **phiên đăng nhập BẬT 1 ngày** (cùng IP vào lại khỏi gõ mật khẩu; Cài đặt › Sảnh › Đăng nhập). Chỉ an toàn vì mọi người vào qua VPS
  (IP thật); mở lại đường vào chung IP (như playit) thì PHẢI tắt. Người có bản quyền: `/premium` vào thẳng.
- EliteMobs **10.9.10**, tiếng Việt (`/em language vietnamese`), preset Content-Only, đã cài 11 gói miễn phí (18 dungeon). itzg KHÔNG tự nâng
  EliteMobs (tên file không đổi): nâng tay bằng jar Modrinth và kiểm lại các chỗ DungeonGate đọc bằng reflection (javap) trước khi thay.

### Làm trong 18–19/10 (chi tiết ở các mục ### bên dưới; tên lớp trong network-core/core-paper/src/main/java/vn/duckymomo/core/paper/)
- **Cổng Dungeon `/dungeon`** (DungeonGate): khoá từng gói EliteMobs bằng quyền riêng; kiểu chain (10 tầng, **chìa khoá thật** do trùm cuối rơi,
  dùng 1 lần mở vĩnh viễn, cài đặt cho trao đổi hay không), hidden (Thánh Đường Bóng Tối: **server tự giấu lối vào ngẫu nhiên**, admin xem
  `/dungeon ds` có nút [Tới]), gate (Cống Ngầm, Bắc Cực chỉ vào qua Cổng Hoang), ticket, server (quỹ chung); điều kiện phụ (nhóm, pet, ngày,
  giờ, mùa, Bá chủ, thành tích), Mảnh Chìa, Dungeon của tuần, kỷ lục tốc độ. Kiểu luck hiện KHÔNG có dungeon nào (Vé Vận Mệnh không rơi).
- **Cổng Hoang `/conghoang`** (WildGates): 4 cổng/mùa (= 4 đích), 7 ngày thêm 1, chỉ khi đã có `leaderboard.season-start`; 14 kiểu ngụy trang
  (cả dưới nước), độ khó nhận ra theo bậc dungeon, tin đồn theo ngữ cảnh; thị trấn giữ cổng thu phí, chiếm thị trấn là có cổng.
- **Skin theo rank**: LuckPerms trên proxy; SkinsRestorer bật quyền từng skin; `/rank skin them <bậc> <tên> <link>` khoá skin + nút tạo skin.
- **Trang quản trị › Hệ thống** (`panel/hethong.html`): TPS/RAM/online từng server, lưu, dọn rác, nạp plugin, khởi động lại có đếm ngược + huỷ.
  Proxy KHÔNG có nút (người dùng chưa quyết thêm docker-socket-proxy).
- **Xưởng chế tạo `/xuong` làm lại** (Workshop): logic hiệu ứng + độ bền + thời hạn, 20 món, 19 hiệu ứng, bảng chế 3×3, bảng sửa từng món trên Cài đặt.
- **Cài đặt cá nhân `/caidat`** (PlayerSettings): 19 công tắc, lưu LuckPerms meta `dmmcaidat.<mã>`, đổi được trên trang tài khoản (web).
- **Thăng Cấp `/capdo`** (LevelUp, LevelUpPapi): 100 cấp, xu + giờ chơi + điểm hạ gục (quái +1, người +10), điểm đổi đặc quyền tiện ích,
  `[Cấp N]` trên Tab, mặc định làm lại mỗi mùa.
- Nhỏ: bảng bên phải gọn 8 dòng ngắn; dọn rác báo 2 lần, im khi 0 món; mẹo boss bar mới; giới hạn người mỗi server; lệnh hay dùng.
- **Việt hoá toàn server (19/10 tối)**: mọi plugin người chơi gặp đã tiếng Việt (ép cho cả người để máy tiếng Anh). Chi tiết: mục
  "### 19/10: Việt hoá toàn server"; thử: TEST-CHECKLIST 4l. Vài chỗ chỉ có hiệu lực sau khi khởi động lại (proxy, QuickShop, SimplePets…).
- **Kỹ năng nghề AuraSkills `/skills` (19/10)**: mức cơ bản, chạy song song uJobs, làm lại mỗi mùa. Chi tiết: mục "### 19/10: Kỹ năng nghề"; thử: TEST-CHECKLIST 4k.
- **Lữ khách `/lukhach` (19/10, đã triển khai, chưa thử)**: 4 NPC Citizens lang thang ở đất hoang, mỗi người kể về một nơi phía sau Cổng Hoang,
  chỉ đường theo tình trạng cổng, đi qua cổng rồi về kể có thưởng. Chi tiết: mục "### 19/10: Lữ khách"; thử: TEST-CHECKLIST 4j.
- **Cốt truyện (19/10, đã triển khai, chờ đặt NPC + thử)**: Citizens (NPC) + BetonQuest (hội thoại, nhiệm vụ) ở Bang Hội Chiến; chương 1 «Điềm Báo».
  Chi tiết ở mục "### 19/10: Cốt truyện" bên dưới; cần thử: TEST-CHECKLIST mục 4i.

### Việc tiếp theo
1. **Push** khi người dùng bảo (kiểm tra trước khi push trong worktree tạm: xem mục "Kiểm tra trước khi push" của CLAUDE.md; KHÔNG chạy
   `./ops/init.sh` trong repo thật vì `.env` và `data/` thật đang dùng).
2. **Người dùng test trong game** theo `docs/TEST-CHECKLIST.md` mục 4d, 4d+, 4e, 4f, 4g, 4h, 4i → sửa lỗi theo ảnh họ gửi.
   Người dùng 19/10: "cứ làm tính năng mới trước, phần thử/kiểm tra cho vào test sau" → làm xong ghi vào TEST-CHECKLIST, chưa triển khai.
3. **Chờ quyết (đã note):** bảng xếp hạng dán tường (thuộc hệ thống vinh danh); mảnh dungeon → đổi đồ + bảng đồ rơi/tỉ lệ trên Cài đặt
   (đồ EliteMobs lấy vào thư viện bằng `/dmm luudo`); Đảo Hỗn Mang (khu boss) làm đích Cổng Hoang; mua thêm gói dungeon; sách CE trong `/ce`
   có làm Xưởng mất giá không; tắt kỹ năng class EliteMobs ngoài dungeon (`allowClassAbilitiesOutsideEliteMobsWorlds`); bảng sửa đặc quyền
   Thăng Cấp trên web; nút khởi động lại proxy trên trang Hệ thống.
4. **Hoãn từ trước:** ngày mở mùa 1 (`leaderboard.season-start`; đặt ngày thì Cổng Hoang tự chạy và Thăng Cấp làm lại), đặt Quảng trường
   Vinh danh ở sảnh, đặc quyền top, voice chat.

## 00. Đọc trước nhất — tình trạng 15/10/2026 tối (chuyển sang session cloud)

> Mục này tóm tắt mọi thứ làm trong ngày 14–15/10 và những việc còn dở. Mục 0 trở xuống vẫn đúng (cách làm việc, lịch sử), chi tiết từng tính năng
> nằm rải trong mục 0 (tìm theo tên). Nhánh làm việc hiện tại: `claude/happy-ritchie-2p60v7` (trước đó `claude/confident-thompson-q939lp`).

### Session cloud làm được gì, không làm được gì
- Session cloud chỉ có **repo** (không có `data/`, `.env`, mật khẩu, không có Docker của máy nhà, không SSH được VPS). Sửa code/cấu hình/tài liệu
  được; **build, triển khai, khởi động lại server, xem log, RCON thì phải làm trên máy nhà** (WSL Ubuntu-22.04, repo ở
  `/home/developer/works/DuckyMoMo`). Ghi rõ cho người dùng các lệnh cần chạy ở nhà sau khi pull.
- Các file plugin trong `data/<server>/...` (giá trị thật mà panel đã ghi) KHÔNG có trong Git. Repo chỉ có bản mẫu ở `modes/<mode>/server/...`,
  `lobby/...`; itzg chỉ chép từ repo khi file repo MỚI hơn → sửa file mẫu trong repo có thể đè giá trị đã chỉnh trên web (xem cuối mục 4).
- Triển khai thường dùng (máy nhà, thư mục repo):
  - Plugin DuckyMoMoCore: `./ops/build-core.sh` rồi khởi động lại lobby / banghoi-1 / practice-1 (KIỂM TRA AI ONLINE TRƯỚC:
    `docker compose exec -T <srv> rcon-cli list`).
  - Panel (trang quản trị): `docker compose up -d --build panel` (không ảnh hưởng người chơi). Schema/settings/camnang/ce-vi đọc trực tiếp, không cần build.
  - Đổi env/PLUGINS trong compose của server: `docker compose up -d --no-deps <srv>` (restart thường KHÔNG nạp env mới).
  - Kiểm tra trước khi push: `./ops/init.sh && docker compose config --quiet && docker compose --profile pokemon config --quiet`.

### Đang chạy (15/10 tối)
- Mọi container chạy bình thường; 3 server Paper khởi động lại lúc 19:26 (không ai online) với plugin mới nhất.
- Người chơi vào qua VPS **Vinahost CheapSSD 1** (Viettel TP.HCM, IP 125.212.218.74, `mc.goc6.io.vn`, Java 25565 / Bedrock 19132, vote 8192).
  Relay WireGuard nhà → VPS, giữ IP thật; BBR trên đường về. (playit đã bỏ 19/10.) Chi tiết: `relay/`, `relay/vps/README.md`, mục 0.
- Ping giật khi chạy: nguyên nhân là mất gói trên đường Viettel nhà; Viettel khởi động lại modem từ xa thì hết (0% loss).

### Làm trong 14–15/10 (theo thứ tự commit)
1. **Tự sửa sau khi Windows/WSL khởi động lại**: `ops/tu-sua-sau-khoi-dong.sh` + `ops/windows/dang-ky-tu-sua.ps1` (người dùng đã đăng ký task,
   chạy 1 lần lúc đăng nhập, KHÔNG chạy định kỳ).
2. **VPS relay**: BKNS (cấm game) → Vinahost (nhân viên đã đồng ý cho dùng relay). DNS Cloudflare qua API (token trong `be_goc6/.env`, không in ra).
3. **Vote**: AzuVotifier 3.3.6 ở banghoi-1 (cổng 8192 qua relay), thưởng trong `vote` của DuckyMoMoCore, mục vote trên site. Người dùng còn phải
   tự đăng ký server trên các trang vote rồi gửi link để điền `vote.sites`.
4. **8 tính năng mùa 1 (ý tưởng từ CherryMC)**: Thư viện đồ, điểm danh `/diemdanh`, nhiệm vụ ngày `/nhiemvu`, vòng quay miễn phí `/vongquay`
   (vé theo giờ chơi thật, không bán vé), mảnh sự kiện `/doimanh` (trao đổi được, giới hạn/ngày), boss bar bảo hộ người mới, sửa boss EliteMobs
   trên web, sửa cửa hàng Kinh Đô trên web. Mô tả từng cái: tìm trong mục 0. **Chưa thử trong game.**
5. **Trang quản trị làm lại toàn bộ** (`panel/`): khung chung `ui.css` + `shell.js` (thanh trái, tiêu đề, `combo()` ô chọn có ô tìm riêng,
   `mcHtml()` xem trước mã màu, `vnMat()`/`matLabel()` tên đồ tiếng Việt, `toast()`), trang Cài đặt (tìm kiếm không dấu, danh mục, công tắc,
   danh sách dạng thẻ `style: tags`, dòng chữ có mã màu + xem trước, thanh lưu nổi, nhớ `#chế_độ/danh_mục`), Role (danh sách + chi tiết, công tắc
   quyền), Thư viện đồ / Boss (khối đánh số, xem trước cột phải khi màn rộng), Hoạt động (nhóm theo ngày), Cẩm nang (chỉ phần lệnh là mã).
   Toàn bộ trang dùng hết bề ngang. Kiểm tra bằng bản xem thử chỉ đọc (container `panel-xemthu`, cổng 8899, script ở scratchpad máy nhà) + Chrome headless.
6. **Boss có sẵn của EliteMobs sửa được**: lưu chỉ ghi mục đã đổi (giữ mọi thiết lập khác), lần sửa đầu sao lưu bản gốc vào
   `data/panel/boss-goc/`, nút Khôi phục, nút Chép thành boss mới (`dmm_<mã>.yml`). Boss do kỹ năng khác gọi ra có cảnh báo.
7. **SkinsRestorer cài cả ở lobby/banghoi/practice** (trước chỉ ở proxy nên `/skins` không mở menu). Cùng link 15.12.6 trong `PLUGINS`.
   Người dùng CHƯA báo lại menu đã mở chưa.
8. **`/dmm` có gợi ý Tab + tên dài `/duckymomo`** (dmm = DuckyMoMo), `/dmm` trống in hướng dẫn nhiều dòng.
9. **Thư viện đồ có phù phép CrazyEnchantments**: `CeBridge.java` (reflection, không cần jar CE khi build), `thuvien-meta.json` có `ce`,
   món web lưu `ce-enchants`; tên + mô tả tiếng Việt 105 phù phép CE ở `modes/_network/ce-vi.yml` (cũng dùng cho trang Cài đặt > Phù phép).
   Người dùng tạo `kiem_rong` (kiếm kim cương, Sắc bén 10). Lấy đồ: `/dmm laydo <mã> [người] [số]` hoặc nút Đưa trên web.

10. **Đăng nhập web bằng tài khoản game (15/10 tối, session cloud, nhánh `claude/happy-ritchie-2p60v7`, CHƯA TRIỂN KHAI)**:
   - Plugin: lệnh `/web` (`WebLogin.java`) cấp mã 6 số trong bộ nhớ (1 lần, 5 phút, sai 5 lần huỷ); `/dmm webma <tên> <mã>` chỉ
     console/RCON. Cấu hình (không bắt buộc, có mặc định): `web-login.url`, `web-login.code-minutes`. KHÔNG build thử được ở cloud (mạng chặn kho Maven).
   - CHỈ đăng nhập bằng mã /web (người dùng chốt 15/10: bỏ đăng nhập bằng mật khẩu, web không nhận mật khẩu game).
   - Panel: `panel/taikhoan.py` (xác minh mã qua RCON, tính quyền LuckPerms giống LP: đúng tên > a.b.* > *,
     gán thẳng > nhóm nặng hơn, có kế thừa và server; hồ sơ người chơi). `app.py`: khoá `PANEL_SITE_KEY` (header `X-DMM-Key`), `/api/web/xacminh`,
     `/api/web/hoso`, `/api/toi`; staff vào qua web chỉ thấy/làm phần có quyền (`modes/_network/web-quyen.yml`, quyền `duckymomo.web.*` trong
     `quyen.yml`), cấm đụng role admin/mod và cấp `*`/quyền web nếu không toàn quyền; nhật ký `data/panel/web-thaotac.jsonl`; món thư viện ghi `nguoi-luu`.
   - Site: `/api/dangnhap|dangxuat|dangxuat-het|toi`, phiên trong bộ nhớ (cookie HttpOnly, SameSite=Lax, 7 ngày; khởi động lại site = đăng nhập lại),
     khoá 5 lần sai/tên/15 phút + 20/IP, chặn CSRF (Origin/Sec-Fetch-Site), `/quantri/*` chuyển sang panel CHỈ khi phiên đã nhập mã /web
     (12 giờ; hết thì nhập mã mới, vẫn xem được tài khoản trong 7 ngày). Trang `site/public/taikhoan.html`, nút Đăng nhập trên trang chủ. Mỗi lần đăng nhập web báo vào game cho chủ tài khoản.
   - Báo lỗi đăng nhập rõ: `/dmm webma` trả OK / KHAC (mã của tài khoản khác, không huỷ mã đó) / HET / SAI / KHONG; web báo từng trường hợp.
   - Chơi lại từ đầu trên web (15/10 tối): thẻ trên trang tài khoản đọc `choilai.yml` + `player-reset` (số lần, chờ, quà, vua/thị trưởng);
     nút gọi `/dmm choilaiweb <tên>` (console/RCON) → `PlayerReset.webRequest` mở bước xác nhận /choilai TRONG GAME (người chơi phải online ở
     banghoi-1). Web không tự xoá gì. Bảng lệnh trên trang tài khoản: tên lệnh dài tự xuống dòng (trước bị đè cột mô tả).
   - Đã thử ở cloud: panel + site thật, LuckPerms/AuthMe/RCON giả lập, 45 kiểm tra (phân quyền, giả danh header, CSRF, khoá sai) + ảnh chụp 1400px/375px.
   - Triển khai ở nhà: `git pull` → `./ops/init.sh` (thêm `PANEL_SITE_KEY` vào .env) → `./ops/build-core.sh` → kiểm tra online rồi
     `docker compose up -d --no-deps lobby banghoi-1 practice-1` → `docker compose up -d --build panel site`. Cấp quyền web cho role ở trang Role.

11. **Bang Hội Chiến mở cho mọi người (15/10 tối)**: `modes/banghoi/mode.yml` `status: beta` → `open`. Trước đó tài khoản mới vào sảnh bấm la bàn
   chỉ thấy ô "Chưa có chế độ nào mở" (beta chỉ hiện cho quyền `network.beta`). Menu đọc mode.yml mỗi lần mở (mount `../modes:/modes:ro`):
   git pull là có hiệu lực, không cần khởi động lại. Practice vẫn `beta` (chưa ai thử Duels).

12. **Đợt sửa tối 15/10 (sau khi người dùng chạy thử)**:
   - Lệnh trên trang tài khoản: lệnh ghi tay (`lenh-con.yml`) chỉ hiện ở server mà lệnh gốc đang chạy (trước: sảnh hiện /diemdanh, /cuahang…);
     DuckyMoMoCore `CommandDump` ghi `"off": true` cho lệnh có đăng ký nhưng module tắt (cần build plugin + 30 giây sau khởi động);
     thêm tab "Mọi server" (lệnh proxy), lệnh Thị trưởng/Vua theo chức Towny thật, bỏ lệnh Console, nhãn "nhờ đâu" (Từ helper, Thị trưởng, Admin).
   - Donate qua web: chọn gói → QR VietQR (`site/config/site.yml` mục `donate`: ngân hàng, số tài khoản, chủ tài khoản — CÒN TRỐNG, người dùng điền),
     "Tôi đã chuyển khoản" → báo admin online (quyền duckymomo.rank.admin) + dòng Hoạt động; trang quản trị `/donate` (panel/donate.html) cấp rank bằng
     `/rank give` hoặc huỷ. Chưa tự động đối soát (người dùng chốt: chưa cần). Dữ liệu `data/panel/donate.json`.
   - Trang Role: quyền có sẵn (kế thừa hoặc qua `*`) hiện công tắc BẬT mờ, khoá (trước hiện tắt, admin tưởng không có quyền). Cấp/gỡ role báo vào game.
   - Bật/tắt quyền role (trang Role): `sync_perm_change` báo người trong role đang online; mất quyền trạng thái thì chạy lệnh tắt
     (`STATE_REVOKE` trong panel/app.py: fly/god/vanish off, speed 1, gamemode về mặc định của server). Gỡ role cũng tắt. Thêm quyền trạng thái khác: sửa `STATE_REVOKE`.
   - Trang trí trên web (trang tài khoản): đổi tên, hiệu ứng hạt, pet, danh hiệu, có xem trước (canvas cho hạt). Áp dụng bằng RCON
     `sudo <tên> <lệnh>` (Essentials) ở server người đó đang online (banghoi-1/practice-1), web lọc lệnh theo quyền trước
     (`taikhoan.cosmetics`/`cosmetic_command`), plugin kiểm lại. Kết quả: đổi tên kiểm bằng `/whois`, danh hiệu đọc bxh.yml; hạt/pet
     không đọc được nên web báo "đã gửi, xem lỗi trong chat game". Độ dài tên theo `max-nick-length` + `ignore-colors-in-max-nick-length`
     của Essentials (đọc từ data/banghoi-1), đếm ký tự ngay khi gõ. Hiệu ứng hạt đang bật: đọc SQLite PlayerParticles
     (`plugins/PlayerParticles/*.db`, bảng *_group name='active' + *_particle; chỉ đọc), hiện "Đang bật" + nút gỡ (`pp remove <id>`),
     thêm/tắt/gỡ được kiểm lại bằng file đó. Pet: chưa đọc được pet đang gọi (SimplePets).
   - Chat lặp role/rank ([Mod] [Công Tước] hai lần) ở banghoi: do Essentials `add-prefix-suffix` + format có {PREFIX}. Cài đặt > Toàn mạng >
     Chat > nhóm "Tên hiển thị và định dạng chat (Essentials)" (mới, có giá trị khuyên dùng điền sẵn: add-prefix-suffix false, format có
     {SUFFIX} cho danh hiệu, nick tối đa 16, không tính mã màu, tiền tố ~). Schema hỗ trợ `default:` (index.html điền sẵn thành thay đổi chưa lưu).
   - Lệnh hay dùng: `modes/_network/lenh-hay-dung.yml` (nhóm Người chơi/Staff/Admin, khớp theo tên lệnh bỏ tham số, `cmd_key` trong panel);
     trang tài khoản hiện mục "⭐ Hay dùng" trước, lệnh khác gom theo plugin, thu gọn (tìm kiếm thì mở hết).
   - Sảnh KHÔNG cài EssentialsX: /fly, /kick… ở sảnh báo "Unknown command" với mọi người. Mod dùng ở banghoi/practice. Chưa quyết có cài Essentials ở sảnh không.

13. **Role Manager (mã nhóm `quanly`; tên tiếng Anh cho đồng bộ Admin/Mod/Helper/Builder, rank donate vẫn tiếng Việt) (15/10 tối, CHƯA tạo trong LuckPerms)**: `tools/quanly-role.cmds` (weight 90, prefix &e&l[Manager], kế thừa mod,
   track staff chèn vị trí 3). Được vận hành (phạt, điều tra, rollback, rank donate, phần lớn web); CẤM rõ bằng `false` mọi thứ đụng phân quyền,
   /sudo, điều khiển server/plugin/luật thế giới, /dmm, xoá log CoreProtect. ĐƯỢC (người dùng chốt): kinh tế, đồ, WorldEdit, fill/clone/
   setblock, vượt vùng bảo vệ, towny.admin, sáng tạo (CoreProtect ghi lại nên vẫn khôi phục được).
   Một người giữ nhiều role được; mâu thuẫn thì role weight cao thắng (Quản lý 90 > Mod 70; Admin 100 + * thắng hết). Panel: `STAFF_GROUPS` có quanly; Quản lý cấp/gỡ được tới Mod (`PROTECTED_FOR_QUANLY`),
   người khác có quyền role chỉ dưới Mod. Đã thử bằng cách đọc chính file .cmds thành bộ quyền (test ở scratchpad).

14. **Role Builder** (CHƯA chạy): `tools/builder-role.cmds`. WorldEdit + vượt vùng + /gamemode + /tp chỉ ở lobby (lệnh gốc vì lobby không có
   Essentials); cấm ở banghoi-1; cấm mọi nơi fill/clone/setblock, towny.admin, give/item/eco.

15. **Nút bộ quyền khuyên dùng (trang Role)**: role có file trong `ROLE_PRESETS` (quanly, builder) hiện nút "Tạo role + bộ quyền khuyên dùng"
   / "Áp bộ quyền khuyên dùng" (chỉ toàn quyền), chạy các dòng `lp ...` của `tools/<role>-role.cmds` qua RCON lobby. Panel mount `../tools:/repo/tools:ro`.
   Role chưa có trong LuckPerms hiện cảnh báo đỏ (`exists` từ bảng luckperms_groups).
   Trong preset và Cài đặt, prefix Manager được đặt bằng `meta clear prefix` (xoá MỌI prefix cũ, vd [Quản lý]) rồi mới setprefix 90. Trang Role hiện
   prefix có priority cao nhất (như game) và cảnh báo khi role còn nhiều prefix.

16. **Mùa mưa / mùa khô + câu cá theo mùa (15/10 tối, CHƯA build, CHƯA thử)**. Ý tưởng từ bài tuyển mem "4 mùa, nông sản, cá theo mùa".
   Người dùng chọn tự làm mùa + plugin câu cá (KHÔNG dùng CustomCrops: bắt buộc CraftEngine/ItemsAdder + resource pack ép tải, Bedrock thấy lỗi).
   - DuckyMoMoCore `Seasons.java`, config `seasons` (banghoi): mùa theo ngày thật giờ VN (`start-date` 2026-10-12 = mùa mưa, 7 ngày/mùa, lặp mua → kho).
     Hệ số lớn của cây theo khối mọc ra (BlockGrowEvent: <1 huỷ bớt, >1 cây có tuổi lên thêm 1 bậc), được mùa (% rơi thêm khi gặt cây chín),
     mưa theo mùa (mỗi 5 phút đổ/tạnh theo %), title + tin khi đổi mùa, tin khi vào game, `/mua` (+ `/mua dat <id|tudong>` quyền duckymomo.mua.admin,
     ghi đè lưu ở mua.yml). Placeholder `%dmmmua_id|ten|ten_tron|con|sau%`. Trang Cài đặt > Bang Hội Chiến > Thế giới > Mùa mưa / mùa khô.
   - **EvenMoreFish** (Modrinth `evenmorefish`, GPL, miễn phí, có module 26.2; thay cho CustomFishing vì CustomFishing trả phí ~12 USD, không có
     bản tải miễn phí): config ở `modes/banghoi/server/plugins/EvenMoreFish/` (bản 2.5.1 sửa: locale vn, only-fish true giữ kho báu gốc,
     chống câu treo, sqlite, cá ăn/nướng được, chỉ world). 24 cá Việt trong `rarities/` (Common/Rare/Epic/Legendary), cá theo mùa bằng
     `requirements.placeholder: "%dmmmua_id% == mua|kho"`, cá biển `biome-set: oceans`. Thi câu T7, CN 20:00 (`competitions/thicau.yml`, thưởng xu),
     2 mồi (`baits/`). Thư mục rarities/competitions/baits có sẵn nên plugin KHÔNG tạo bộ mặc định tiếng Anh (rods/ thì có, không ảnh hưởng).
     Chưa rõ: uJobs nghề Ngư dân có còn tính tiền với cá EMF (vẫn là COD/SALMON…), bản tiếng Việt `messages_vn` có thiếu dòng nào không.
   - **Chống xray** (Paper có sẵn): `modes/banghoi/server/config/paper-world-defaults.yml`, engine-mode 1, dưới y 64. Cần khởi động lại banghoi-1.
   - Triển khai ở nhà: git pull → `./ops/build-core.sh` → kiểm tra không ai online → `docker compose up -d --no-deps banghoi-1` (nạp PLUGINS mới)
     → thử `/mua`, `/mua dat kho`, câu cá ở sông và biển, `/emf shop`, `/papi parse me %dmmmua_id%`.
   - **Trang web công khai** (site): mục "Mùa & câu cá" (`/api/mua`: mùa hiện tại tính giống Seasons.java + ghi đè mua.yml, cây nhanh/chậm,
     được mùa, danh sách cá đọc `data/banghoi-1/plugins/EvenMoreFish/rarities` (chưa có thì đọc bản mẫu trong repo) có lọc theo mùa/biển,
     thi câu + đếm ngược) và mục "Hằng ngày" (`/api/hangngay`: điểm danh, nhiệm vụ, vòng quay có tỉ lệ, mảnh sự kiện, đọc config DuckyMoMoCore).
     Luật thêm chống x-ray, hỏi đáp thêm "Mùa mưa, mùa khô" và "Có voice chat không". Triển khai: `docker compose up -d --build site`.

18. **Bảng bên phải 2 trang (15/10 tối, CHƯA build)**: ý từ UniMC. TAB `animations.yml` SbTieuDe + SbDong1..4 (change-interval 8000, đổi đồng thời):
   trang 1 Kinh tế & mùa (tiền, vé quay, mùa + số ngày còn), trang 2 Chiến tranh (thị trấn, quốc gia, giết/chết, KDA). DuckyMoMoCore
   `PlayerStatsPlaceholders` (%dmmtoi_giet|chet|kda%, thống kê gốc PLAYER_KILLS/DEATHS của server). settings.yml `tab_sb_lines` đã khớp.
   Triển khai cùng lần build plugin (mục 16), TAB tự nạp khi khởi động lại banghoi-1 (hoặc /tab reload).

19. **Luật vũ khí nổ (15/10 tối, CHƯA build)**: DuckyMoMoCore `ExplosionRules`, config `explosive-pvp` (banghoi): hệ số sát thương lên NGƯỜI CHƠI
   end crystal 0, respawn anchor 0, nổ giường 0, xe goòng TNT 0.3 (0 = huỷ sự kiện, không hất). Anchor/giường nhận qua
   `EntityDamageByBlockEvent#getDamagerBlockState()` (Paper). Cài đặt > PvP & combat > Vũ khí nổ. Towny world: Explosions ON nhưng
   Entity/Block Explosion Revert ON (người dùng chụp /townyworld 15/10) → nổ ở đất hoang tự hồi khối.

20. **Sàn thu mua /donhang (15/10 tối, CHƯA build)**: DuckyMoMoCore `Orders`, config `orders`, dữ liệu `donhang.yml` (don.<id>: owner, ten, vat,
   can, gia, da, kho, tao, dong; tiep). Tiền giữ trước (can x gia) + phí 2% không hoàn; giao hàng → trả tiền người giao ngay, hàng vào "kho"
   của đơn; người mua nhận ở /donhang cuatoi (trái nhận, phải huỷ; Bedrock dùng /donhang huy <số>). Hết 7 ngày đóng, trả tiền phần chưa giao
   (deposit cả khi offline). Chỉ đồ trơn (isSimilar với ItemStack mới). Web: thẻ "Sàn thu mua" trong mục Hằng ngày (/api/hangngay → donhang).

21. **"Hạng của bạn" trên hologram (15/10 tối, CHƯA build)**: BxhPlaceholders thêm %dmm_toi_chienthan%, %dmm_toi_bachu% (danh sách cache 5 giây,
   quốc gia lấy từ Towny Resident). Hologram đã đặt thì admin chạy 2 lệnh /dh line add (ghi trong cẩm nang, /bxh hologram cũng in ra).

22. **Khai hoang vùng mới /khaihoang (15/10 tối, CHƯA build)**: DuckyMoMoCore `Frontier`, config `frontier`, dữ liệu `khaihoang.yml`
   (quy, dat, gop.<uuid>). Quỹ tăng khi /sell (UserBalanceUpdateEvent COMMAND_SELL qua reflection, MONITOR, sell-percent %; người bán không mất)
   và /khaihoang gop (rút tiền). Đủ mốc: chạy lệnh console của mốc (mặc định nới ChunkyBorder: Mã Lai 300k, Philippines 800k, Borneo+Java 2 triệu
   xu; con số đoán, chỉnh theo kinh tế thật). SAU KHI MỞ phải sửa border_* ở Cài đặt cho khớp (ghi chú từng mốc). Placeholder %dmmkh_*%.
   Web: thẻ Khai hoang đầu mục Hằng ngày (/api/hangngay → khaihoang). Sửa lỗi YAML camnang.yml (ngoặc kép lồng) từ commit trước.

23. **QR ủng hộ trong game /rank mua (15/10 tối, CHƯA build)**: DuckyMoMoCore `DonateQr` (lobby, banghoi, practice; config `donate-qr`),
   menu chọn bậc/gói (chỉ số bậc = thứ tự tiers, gói = thứ tự prices, khớp donate_tiers() của panel) → POST panel /api/web/donate (X-DMM-Key,
   env PANEL_URL + PANEL_SITE_KEY thêm vào 3 compose) → panel trả thêm `qr` (VietQR qr_only) + `bank` (đọc site/config/site.yml, mount ro mới
   ../site/config:/repo/site-config) → tải ảnh, đen trắng 128x128, MapRenderer, tấm bản đồ có PDC qr_het (không vứt/cất rương, chết không rơi,
   hết hạn tự xoá). [Tôi đã chuyển khoản] → action bao → panel báo admin như web. Chưa rõ: QR 128 điểm ảnh quét có dễ không (UniMC làm được).

24. **Treo thưởng /treothuong (15/10 tối, CHƯA build)**: DuckyMoMoCore `Bounty`, config `bounty`, dữ liệu `treothuong.yml`. Người chơi treo
   (phí 5%), người hạ nhận hết (tiền chính mình treo thì trả lại); chuỗi 5 mạng → hệ thống treo 500 (+100/mạng, tiền tạo thêm); không tính
   tài khoản phụ (WarRules.isAlt), cùng IP, dân Hoà Bình; hết 3 ngày trả tiền người treo. Riêng với truy nã của Hội Bàn Tròn (Justice).
   Web: bảng Treo thưởng trong mục Hội Bàn Tròn (/api/bantron → treothuong).

25. **Xưởng chế tạo /xuong (15/10 tối, CHƯA build)**: DuckyMoMoCore `Workshop`, config `workshop.items` (5 món: tui_hu, nuoc, hut_do,
   cung_mau, bua_ta). PDC xuong_id / xuong_luot / xuong_het; quét túi mỗi phút xoá đồ hết hạn; món pvp bị chặn trong vùng vây đang đánh
   (SiegeWarHook). Dây hút đồ chỉ hút đồ từ khối mình đào (BlockDropItemEvent) và quái mình giết (EntityDeathEvent), không hút đồ người chơi.

17. **Voice chat: Simple Voice Chat (15/10 tối, CHƯA cài; người dùng chốt: ghi lại, CHƯA vội triển khai)**. Chỉ người chơi Java có cài mod; Bedrock chưa có cách tốt (xem dưới).
   - Server con lobby/banghoi-1/practice-1: `simple-voice-chat` trong MODRINTH_PROJECTS (bản bukkit 2.6.x, release, hỗ trợ 26.2),
     cấu hình `plugins/voicechat/voicechat-server.properties` (40/20 block, codec VOIP, nhóm bật, cấm ghi âm, không bắt buộc mod, cổng 24454 trong Docker).
   - Proxy Velocity: jar KHÔNG tải tự động (Modrinth chỉ có bản alpha cho Velocity, tải lỗi thì proxy không lên) → `./ops/voicechat.sh` tải vào
     `proxy/plugins/voicechat-velocity.jar` (không commit). Cấu hình `proxy/plugins/voicechat/voicechat-proxy.properties`: port 24454, voice_host trống
     (mod dùng đúng tên miền người chơi nhập). proxy/compose.yaml mở 24454/udp cho LAN.
   - Đường ngoài: VPS DNAT UDP 24454 → 10.66.0.2:24454 (`relay/vps/relay-up.sh`; áp ngay không khởi động lại WireGuard: `./ops/voicechat.sh vps`,
     có thêm `ufw route allow`), relay thêm `ip rule … udp sport 24454` (pref 303) → cần `--build` lại relay. playit KHÔNG có voice.
   - Quyền mặc định ai cũng có (voicechat.listen/speak/groups); phạt cấm nói: `voicechat.speak false` (trang Role có nhóm "Voice chat").
   - Bedrock: không có cách dùng mod. Đã xem: SimpleVoice-Geyser (trang web voice, bản 0.0.x, cần HTTPS + WSS qua reverse proxy, âm thanh không nén,
     lỗi với reverse proxy; điện thoại phải để trình duyệt chạy song song với game, iOS hay tắt micro khi chuyển app), cầu nối Discord
     (voicechat-discord: mỗi người Bedrock nói cần một bot Discord, chưa rõ còn bảo trì). Khuyên: lập server Discord (site.yml `discord` đang trống).
   - Triển khai: git pull → `./ops/voicechat.sh` → `./ops/voicechat.sh vps` → kiểm tra không ai online → `docker compose up -d --no-deps proxy lobby banghoi-1 practice-1`
     → `docker compose --profile tunnel up -d --build --force-recreate playit relay`. Thử: vào bằng mc.goc6.io.vn có mod, phím V phải hiện đã kết nối.
   - Người chơi PHẢI tự cài mod (Fabric/Forge + Simple Voice Chat đúng phiên bản game của họ; server không đẩy mod xuống được).
     Simple Voice Chat "All Rights Reserved": KHÔNG tự host file jar. Ý tưởng để sau (người dùng chưa chốt):
     (1) modpack `.mrpack` DuckyMoMo (chỉ liệt kê link Modrinth: Fabric + Simple Voice Chat + Sodium), cần thử SKLauncher/TLauncher có mở được không;
     (2) trang "Hướng dẫn cài voice chat" trên web + lệnh `/voice` hiện link; (3) nhắc người chưa có mod 1 lần/ngày khi vào server.
     Hỏi người dùng: người chơi dùng TLauncher hay SKLauncher nhiều hơn.

### Tình trạng triển khai (16/10 ~02:30)
- ĐÃ build + khởi động lại lobby/banghoi-1/practice-1 với bản có mục 16–25. Bang Hội Chiến lên ổn: EvenMoreFish nạp 4 độ hiếm / 24 cá,
  1 file thi câu (2 lịch), voicechat v2.6.24 chạy cổng 24454 (proxy CHƯA có jar voice, VPS CHƯA mở cổng → voice chưa dùng được, đúng ý để sau).
- Lỗi build 1: import sai `org.bukkit.entity.ExplosiveMinecart` → đúng là `org.bukkit.entity.minecart.ExplosiveMinecart` (đã sửa).
- Lỗi chạy 1 (NGHIÊM TRỌNG, đã sửa, CHỜ build lại + restart lobby, kiểm tra practice): DuckyMoMoCore KHÔNG nạp ở sảnh
  (`NoClassDefFoundError: me/clip/placeholderapi/expansion/PlaceholderExpansion`, sảnh không có PlaceholderAPI) → mất menu chọn server.
  Nguyên nhân: `statsPapi = new PlayerStatsPlaceholders()` gán lớp con vào biến PlaceholderExpansion → JVM nạp PAPI khi kiểm tra lớp DuckyMoMoCore.
  Sửa: `PlayerStatsPlaceholders.create()` (hàm tĩnh trả PlaceholderExpansion). QUY TẮC: trong DuckyMoMoCore.java đừng `new` lớp con của
  lớp plugin tuỳ chọn (PAPI, Towny, Vault…) rồi gán vào biến kiểu lớp cha; tạo qua hàm tĩnh/phương thức của lớp module.
- La bàn sảnh dịch chuyển thay vì mở menu (với admin): gậy dịch chuyển WorldEdit (navigation-wand) gắn theo PHIÊN của người chơi.
  Đã đổi `data/lobby/plugins/WorldEdit/config.yml` navigation-wand.item → minecraft:stick + `we reload`; người đã gắn thì cầm la bàn gõ `//none`.
  LobbyMenu bắt PlayerInteractEvent ở LOWEST + setUseItemInHand(DENY) + mở menu tick sau (chặn WorldEdit). Người dùng đã //none: hết dịch chuyển.
- Trang Donate có 1 yêu cầu thử của Linh (Nam Tước 3 tháng, "Chưa báo chuyển", tạo 21:49 15/10 từ web) — yêu cầu thử, huỷ được.
- Rà 16/10 (người dùng hỏi "đã có ở Cài đặt, web chưa đăng nhập, đã đăng nhập chưa"): đã bổ sung
  Cài đặt: Xưởng (bán/giá/ngày/lượt từng món, loại Shop), Câu cá EvenMoreFish (trọng số + giá/cm từng độ hiếm, thi câu: phút, số người, giờ T7/CN;
  apply `emfa reload`), QR ủng hộ (Toàn mạng > Rank donate: bật, phút hết hạn, link web). Đã có từ trước: Mùa, Khai hoang, Sàn thu mua,
  Treo thưởng, Vũ khí nổ, bảng bên phải. Web công khai: thẻ Xưởng (/api/hangngay → xuong), dòng /rank mua ở mục Ủng hộ, SỬA hỏi đáp voice
  (trước ghi "Có" sai → "Sắp có"; sách /huongdan cũng sửa). Trang tài khoản (đăng nhập): ô Giết/Chết/KDA (world/stats/<uuid>.json), ô Mùa,
  thẻ Sàn thu mua (đơn mở, hàng chờ nhận), Khai hoang (đã góp), Treo thưởng (đang bị treo, mình đang treo); panel taikhoan.profile thêm
  pvp/mua/donhang/khaihoang/treothuong. Cần `docker compose up -d --build panel site`.
- Sau MỖI lần build: xem log cả 3 server (`docker compose logs <srv> --since 5m | grep -iE "Could not load|Exception|DuckyMoMoCore"`),
  không chỉ banghoi-1. Chỉ đổi jar → `docker compose restart <srv>`; đổi compose (env, MODRINTH_PROJECTS, PLUGINS) → `up -d --no-deps <srv>`.

### 17/10: trang Role theo quyền + cấp dưới; tách role / rank
- 4 quyền web (web-quyen.yml, quyen.yml): `duckymomo.web.role` (cấp/gỡ role chức vụ), `.role.quyen` (sửa quyền role chức vụ + nhóm Dân),
  `duckymomo.web.rank` (cấp/gỡ rank donate), `.rank.quyen` (sửa quyền rank). Staff đăng nhập bằng tài khoản game chỉ thao tác role chức vụ
  CẤP DƯỚI: weight nhỏ hơn role chức vụ cao nhất của mình (`staff_weight`, `group_weights` đọc meta weight trong DB; Admin 100, Manager 90,
  Mod 70, Helper 50, Builder 40, Dân 0). Bật quyền: chỉ quyền chính mình đang có, không bao giờ `*`/web/admin/luckperms (PROTECTED_PERM_RE).
  Toàn quyền (`*` hoặc duckymomo.web.toanquyen) làm được hết. Máy chủ kiểm ở `Handler.role_rights` + `scoped_post_ok`; `/api/toi` trả `role_quyen`
  {nhóm: {nguoi, quyen}} để trang Role chỉ hiện role/rank quản lý được và ẩn phần không có quyền (bỏ PROTECTED_GROUPS cũ "trừ admin, mod").
- Manager (17/10): đã chạy `lp group quanly permission set duckymomo.web.rank true` (giữ quyền cấp rank như trước khi tách), `.rank.quyen false`
  (cũng có trong tools/quanly-role.cmds). Manager thấy Mod, Helper, Builder, 5 rank; không thấy phần Quyền (role.quyen false).
- Trang tài khoản (site): khung Quản trị chỉ còn một nút "Mở trang quản trị" (mở mục đầu tiên được phép; thanh trái panel tự ẩn mục không có quyền).

### 17/10: nút ⓘ giải thích chi tiết (trang quản trị)
- `shell.js` `infoBtn(text, label)` trả nút i; bấm hiện hộp giải thích (đóng khi bấm ngoài / Esc / cuộn); nằm trong `<label>` vẫn không bật công tắc.
  CSS `.ib`, `.ib-pop` trong `ui.css`. Văn bản xuống dòng bằng `\n`.
- Danh mục quyền `modes/_network/quyen.yml`: phần tử thứ 3 = giải thích (94/102 quyền có, trừ pet từng loại). Trang Role: nút i cạnh từng quyền,
  weight, kế thừa, Thành viên, Quyền, Toàn mạng/server, Quyền khác.
- Trang Cài đặt: khoá `info:` cho mục và nhóm trong `settings.schema.yml` (đã viết ~44 mục + 9 nhóm dễ hiểu nhầm: rank donate, prefix role, đất đai,
  hoà bình, bảng xếp hạng, án, công thành, PvPManager, dọn rác, GrimAC). Thêm dần khi người dùng thấy chỗ nào mơ hồ.

### 17/10: danh mục quyền tự động trên trang Role
- `panel/app.py` `command_catalog()`: thêm vào catalog các nhóm "Lệnh <plugin> (tự động)" (`auto: true`) từ commands.json của 3 server
  (qua `all_commands`): mỗi quyền một dòng `/lệnh (/gọi tắt…) — mô tả`, nút i ghi đủ tên gọi tắt + server. Bỏ quyền đã có trong quyen.yml.
  17/10: 24 nhóm, 382 quyền (Essentials 129, WorldEdit 114, Minecraft gốc 85…). Plugin không khai quyền cho lệnh (Towny, SimplePets, zAuctionHouse…)
  KHÔNG có ở đây (quyền của chúng xem Cẩm nang hoặc thêm tay ở "Quyền khác"). Rank donate không thấy nhóm tự động (chỉ trang trí).
- Trang Role: nhóm tự động thu gọn, bấm tiêu đề mở; ô Lọc quyền tìm cả tên gọi tắt (vd "sun" → essentials.weather).

### 17/10: chờ 3 giây trước khi dịch chuyển (Bang Hội Chiến)
- EssentialsX `teleport-delay: 3` (data/banghoi-1, đã `essentials reload`): /home, /spawn, /warp, /back, /tpa. Practice vẫn 0 (chưa hỏi).
- `/tpr`: `RandomTeleport` thêm `rtp.warmup-seconds` (mặc định 3): đứng yên, đếm trên thanh hành động, di chuyển khỏi khối / bị đánh = huỷ;
  bỏ qua nếu có essentials.teleport.timer.bypass hoặc duckymomo.rtp.bypass. Đã build + khởi động lại banghoi-1 (0 người online), CHƯA thử trong game.
- Trang Cài đặt › Thế giới: ô chờ của /tpr + nhóm mới "Dịch chuyển bằng lệnh" (teleport-delay, teleport-cooldown, teleport-invulnerability).

### 17/10: mô tả lệnh chỉ tiếng Việt
- `modes/_network/lenh-vi.yml` có ~460 mô tả: khoá thường = tên lệnh; khoá `<namespace>:<lệnh>` (vd `worldedit:/set`, `minecraft:fill`,
  `essentials:tree`) khi trùng tên giữa plugin — panel tra khoá có namespace trước. 17/10: MỌI lệnh trên lobby/banghoi/practice đều có tiếng Việt.
- Không còn hiện mô tả tiếng Anh của plugin ở đâu (Cẩm nang › Tất cả lệnh, trang tài khoản, danh mục quyền tự động): thiếu thì để trống.
  Plugin mới cài thêm lệnh → thêm dòng vào lenh-vi.yml (trang Cẩm nang sẽ hiện ô mô tả trống).
- Nút i: mỗi dòng "Nhãn: nội dung" in đậm nhãn (shell.js). Danh mục quyền tự động: Làm gì / Cách dùng (tên tham số dịch: <người>, <số>…) / Gọi tắt / Có ở.
- quyen.yml thêm nhóm Di chuyển: essentials.teleport.timer.bypass, .timer.move, .cooldown.bypass, duckymomo.rtp.bypass, duckymomo.rtp.free.

### 17/10: tên Bedrock trong lệnh LuckPerms; khung giải thích thống nhất
- LuckPerms (allow-invalid-usernames mặc định false) TỪ CHỐI `lp user .ten …` (tên Bedrock có dấu chấm). Trang Role trước báo "Đã gửi"
  nhưng không có tác dụng. Sửa: panel `lp_uuid()` tra UUID trong `luckperms_players` rồi gửi `lp user <uuid> parent add|remove`; chưa từng vào
  thì báo lỗi rõ. Plugin: Leaderboard (danh hiệu) dùng `p.getUniqueId()`, Organizations (giấy phép) dùng `lpTarget()` (UUID nếu tên không hợp lệ).
  QUY TẮC: lệnh `lp user` luôn dùng UUID. `/nick` không liên quan (chỉ đổi tên hiển thị).
- Mọi giải thích (quyen.yml 107/107 quyền, trang Role, settings `info:`) theo khung dòng "Nhãn: nội dung" (Làm gì / Cách dùng / Có ở / Ví dụ / Lưu ý).
- Người online: panel dùng `minecraft:list` (tên thật). `list` của Essentials hiện tên /nick (vd `~b0ybn2013`) → trước đó Trang trí trên web
  và nút Đưa đồ không thấy người đã đổi tên. QUY TẮC: tìm người chơi theo tên thật/UUID, không theo `list` của Essentials.
- LỖI ĐÃ SỬA: khoá cài đặt mới `tp_delay/tp_cooldown/tp_invuln` trùng tiền tố `tp_` của TradePro (tp_cooldown trùng hẳn) → đổi thành
  `tele_delay/tele_cooldown/tele_invuln`. Thêm khoá mới: kiểm tra trùng trong settings.schema.yml và settings.yml trước khi commit.

### 17/10: Thư viện đồ — ký tự đặc biệt, dòng mẫu, hướng dẫn lấy đồ
- `panel/thuvien.html`: dưới bảng màu có bảng ký tự 10 nhóm (chiến đấu, sao, vương giả, thiên nhiên, tim/nhạc, mũi tên, dấu/khung,
  đường kẻ, số/tiền, đồ vật) + "Dòng mẫu" (đường kẻ, độ hiếm, dòng chỉ số, tên kiểu…), bấm là chèn vào ô đang gõ; thêm &m gạch ngang, &k chữ nhảy.
  Chỉ ký tự trong BMP (font Minecraft có), KHÔNG dùng emoji ngoài BMP (🔥🏹…) vì game hiện ô vuông. Bedrock có thể thiếu vài ký tự hiếm.
- Khung "Lấy đồ trong game": lệnh điền sẵn mã đang xem + nút Chép: /dmm laydo <mã>, /dmm laydo <mã> <người> [số], /dmm luudo <mã>,
  dòng quà `do <mã> [số]`.

### 17/10: hiệu ứng hạt — tên theo hình dạng thật, tốc độ theo kiểu
- Trang tài khoản (site/public/taikhoan.html `EFFECTS`): tên đặt theo hình dạng trong game (angry_villager = "Mây giông", electric_spark =
  "Tia lửa điện" dấu cộng trắng xanh, happy_villager = "Lấp lánh xanh lá"…) + dòng mô tả "trông như gì" dưới danh sách, màu xem trước sát hơn.
- Tốc độ: PlayerParticles KHÔNG cho từng người chỉnh; chỉnh theo kiểu, cả server: Cài đặt › Toàn mạng › Rank donate › Hiệu ứng hạt
  (Chậm/Vừa/Nhanh cho Bay quanh, Xoay trên đầu, Xoắn 4 dải, Song sinh, Cuộn xoáy, Lốc xoáy, Sóng toả, Đôi cánh, Pháo hoa + mật độ chung
  ticks-per-particle). Ghi vào plugins/PlayerParticles/styles/<kiểu>.yml ở lobby + banghoi-1, CẦN khởi động lại 2 server mới có hiệu lực.
- PlayerParticles có sẵn `locale: vi_VN`, EssentialsX có `messages_vi` — chưa bật (đã hỏi người dùng).

### 18/10: gom tin nhắn lúc vào game (JoinNotes)
- JoinNotes.java (không phụ thuộc gì): Guide (hướng dẫn), Seasons (mùa + sự kiện), PetCombat (phiên công thành), DailyCheckin (điểm danh),
  Orders (đơn hàng) gọi JoinNotes.add(p, thứ tự, dòng) thay vì tự gửi; gửi gộp 1 khung 5 giây sau khi vào. Tiêu đề: join-notes-title
  (banghoi "Bang Hội Chiến", lobby "DuckyMoMo"). Tắt Updater của HoloMobHealth, Debug của HoloMobHealth-PacketEvents (data).
- Thông báo chỉ admin thấy: EliteMobs (cập nhật plugin, tin tác giả, "Setup required" — hết sau /em initialize; dòng Language có Tiếng Việt).

### 18/10: kho báu, truy nã, Thái Ấp + việc chờ
- TreasureHunt.java /khobau (treasure): xem camnang. WantedBoard.java /truyna (wanted). ThaiAp.java /thaiap (thai-ap): thế giới "thaiap"
  tạo bằng ChunkGenerator phẳng (y 64) + BiomeProvider PLAINS, gamerule không quái, Towny tắt bằng TownyWorld.setUsingTowny(false) (100 tick
  sau khi bật), MyPet worldgroups default thêm thaiap (data). Dishes.kitchenHere/kitchenBonus (PDC dmm_bep). Achievements thêm khobau, truyna,
  thaiap (4 mốc mới, danh hiệu sankhobau, sandau). Cài đặt: nhóm Bản đồ kho báu (kbo_*), Bảng truy nã (tna_*), Thái Ấp (tap_*).
- Đoàn buôn: BỎ (người dùng chốt 18/10, không làm).
- Chưa có ngày mở mùa (server chưa mở cho người chơi). Quảng trường Danh vọng: chưa cần đặt, nhớ làm khi gần mở.
- Dungeon: ĐÃ đăng nhập Nightbreak trên banghoi-1 (18/10 16:14, token lưu trong data, không trong repo; "Account token loaded successfully").
  CHỜ người dùng vào game /em setup cài gói miễn phí. Thiết kế đã đề xuất (chờ duyệt): "Cổng Dungeon" /dungeon của DuckyMoMoCore thay lối vào
  gốc của EliteMobs (EliteMobs không có quyền theo từng dungeon; sự kiện PlayerJoinDungeonEvent/DungeonCompleteEvent không huỷ được) với 5 kiểu:
  (1) chuỗi cấp + chìa khoá, (2) dungeon ẩn (bản đồ hầm ẩn, theo giờ/mùa/sự kiện, ghép mảnh chìa), (3) may mắn (cổng thời không ngẫu nhiên,
  vé vận mệnh rơi hiếm, tung xúc xắc lúc vào), (4) mua vé cá nhân bằng xu (lượt / 7 ngày), (5) mở chung toàn server bằng quỹ như khai hoang.
  Điều kiện phụ: cấp pet, thành tích, nhóm 2–4 người, cuối tuần, chỉ nước Bá chủ. Chống lạm phát: lượt/ngày, lockout boss của EliteMobs.

### 19/10 khuya: Chương trình Media (/media) — kiểm soát rank livestream (code xong, CHƯA thử với streamer thật)
- Người dùng: "làm như nào để kiểm soát người chơi xứng đáng cấp rank", "có cách nào tự kiểm tra", chỉ tiêu "cả hai" (buổi + giờ), "chủ yếu TikTok".
- MediaProgram.java + panel/media.html (trang quản trị › Media, quyền duckymomo.web.media; web-quyen.yml, quyen.yml) + Cài đặt › Người mới ›
  Chương trình Media (md_*). Chi tiết: camnang "Chương trình Media". TikTok api-live thử 19/10: gọi được từ máy nhà, kênh không live trả status 4;
  CHƯA bắt được kênh đang live để xác nhận status 2 → thử với streamer thật (TEST 4s). YouTube cần YOUTUBE_KEY trong .env (chưa có).
- SỰ CỐ 23:30: panel sập vòng lặp vài phút vì panel/Dockerfile COPY liệt kê TỪNG file mà quên media.html → thêm trang HTML mới cho panel
  thì PHẢI thêm vào dòng COPY, build xong kiểm `docker ps` (Up, không Restarting) + tải trang.
- Web (20/10): trang chủ mục Bang Hội Chiến có "Danh phận: rank chơi miễn phí" (4 thẻ bậc: yêu cầu + quyền lợi) và thẻ "◆ Rank Media"
  (yêu cầu, chỉ tiêu, quyền lợi) — site/app.py danhphan_info() đọc thẳng config (đổi Cài đặt là web đổi theo). Trang tài khoản: ô "🏷️ Danh phận"
  (bậc hiện tại, quyền lợi, bậc sau cần gì, hạn Media) — panel play_rank() trong /api/web/hoso.
- Thuật ngữ: "Auction House" = chợ đấu giá /ah (đã ghi thống nhất "chợ đấu giá /ah" ở menu, cẩm nang, Cài đặt, web).

### 19/10 khuya: Danh phận (/danhphan) + Balo (/balo) — CODE + CONFIG XONG, CHỜ KHỞI ĐỘNG LẠI banghoi-1 (người dùng đang online)
- Người dùng gửi ảnh "Cửa hàng rank" server khác (Rank Member: 2 nhà, 2 balo, 2 slot chợ, miễn phí, cần mở khoá + online 6 giờ, bấm nhận):
  "mới vào là dân tị nạn, member tương ứng là dân", "cho cài đặt mục này ở rank". Chốt: 4 bậc, Dân = 6 giờ + xong chương 1, bấm nhận, mức cân bằng.
- PlayRanks.java (play-ranks.tiers.<mã> map: tinan=default, dan, luongdan, haokiet; Cài đặt › Người mới › Danh phận): menu /danhphan
  (capbac, rankchoi, member), chỉ nhận bậc kế tiếp, kiểm giờ chơi thật (Activity.totalHours), Thăng Cấp (LevelUp.levelOf), quyền perm.
  sync() lúc bật/dmm reload: LuckPerms API tạo nhóm, xoá node PREFIX/WEIGHT + quyền quản lý (essentials.sethome.multiple.dmm*, zauctionhouse.max.*,
  duckymomo.balo.*) rồi đặt lại theo config (ngữ cảnh server=banghoi-1). Nhóm default đổi tiền tố [Dân] → &8[Tị Nạn], zauctionhouse.max.5 global → 2.
- Essentials (data) sethome-multiple: default 1 (trước 3), thêm dmm1..dmm20. zAuctionHouse (data) permissions.auction/rent/bid: max.20 → max.1
  (giảm dần). Bỏ mục Cài đặt ah_max_listings (lệnh lp cho default, đá nhau với sync).
- BetonQuest chương 1: c1_ket thêm c1_quyen "command lp user %player% permission set duckymomo.cotruyen.c1 true".
- Số ô balo (người dùng: "cài đặt số ô balo trong toàn bộ rank"): quyền duckymomo.balo.o.<9..54> cao nhất; play-ranks.tiers.*.balo-slots
  (Tị Nạn/Dân 27, Lương Dân 36, Hào Kiệt 45) + play-ranks.other-ranks.<nhóm> {balo, balo-slots} cho rank ủng hộ & staff (mặc định 0 = theo
  danh phận; rank ủng hộ để 0 vì luật Mojang), backpack.default-slots 27. Cài đặt › Người mới › Balo theo rank khác. Giảm ô không mất đồ.
- Rank Media (ảnh "Rank Media" server khác; người dùng: "quyền lợi chỉ là ví dụ, cứ cho vào cài đặt"): play-ranks.other-ranks.media
  (create: tạo nhóm media + tiền tố; card: thẻ trong /danhphan, info + contact). Quyền lợi chung mọi rank (Perks): homes, balo, balo-slots, ah,
  orders (duckymomo.donhang.<N>, Orders lấy cao nhất > max-per-player 2), spin-faster (duckymomo.vongquay.nhanh.<%>, LuckySpin.minutesPerKey(p)),
  kit (essentials.kits.<kit>, chỉ kit ghi trong config bị quản). /kit media: Essentials kits/media.yml (3 ngày). Cài đặt › Người mới ›
  Quyền lợi theo rank khác (rk_*), danh phận thêm dp_*_orders/_spin. dh_max (Sàn thu mua) = mức thấp nhất 2.
- Backpack.java (backpack.*): /balo [số] (backpack, bp), số ô theo quyền, 1 Inventory dùng chung mỗi balo, lưu file sau mỗi thao tác, chặn khi vừa đánh người
  15 giây / vùng vây giờ đánh, saveAll + đóng khi dmm reload/tắt; PlayerReset gọi wipe. Admin /balo xem <tên> [số].
- Sau khi khởi động lại banghoi-1: xem log "Danh phận: đã đồng bộ 4 nhóm", thử mục 4r TEST-CHECKLIST. Cần kiểm: tiền tố hiện đúng (meta stack
  highest_not_on_track_donate), Essentials nhận dmmN, zAH nhận max.N.

### 19/10 khuya: Máu dưới tên + dòng chỉ số; bot dò quét
- TAB (banghoi-1, repo modes/banghoi/server/plugins/TAB/config.yml): belowname-objective BẬT ("20 ❤" dưới tên, trong 10 khối). `tab reload` OK.
- AuraSkills messages/global.yml (MỚI đưa vào repo): thanh trạng thái lúc rảnh "❤ [máu/tối đa] | ⚔ [Cấp %dmmcap_cap%] | ✦ [mana/tối đa]"
  (⚔ = cấp Thăng Cấp; dùng ✦ vì font gốc không có emoji 💎). `skills reload`. Chỉ Bang Hội Chiến (AuraSkills chỉ có ở đó). CHƯA nhìn trong game.
- Người dùng hỏi "chưa public mà có người vào": là BOT DÒ QUÉT (máy quét cả Internet tìm cổng 25565, IP VPS bị phát hiện trong vài giờ):
  1 IP 176.65.148.253, ~33 lần, tên ngẫu nhiên Tính-từ+Con-vật+4 số, đứng ~30 giây rồi bị AuthMe đá; đăng ký được 2 tài khoản (SwiftWolf2464,
  GrandElk8200) rồi bị giới hạn 2 đăng ký/IP/ngày chặn; KHÔNG gõ lệnh, không chat, không sang server khác. Đã `authme unregister` 2 tài khoản,
  `ban-ip` ở sảnh (data/lobby/banned-ips.json). Đề xuất: bật whitelist tới ngày mở (chờ người dùng).

### 19/10 khuya: Tinh Luyện (/tinhluyen) — ĐÃ TRIỂN KHAI banghoi-1 22:23, CHƯA thử trong game
- Người dùng chụp menu "Danh mục nguyên liệu" của server khác (Tinh Luyện Đá/Than/Đồng/Sắt/Đá Đỏ/Lapis/Vàng/Kim Cương/Lục Bảo/Netherite, Địa Linh,
  Xu Quái 2) để làm trade đa dạng. Chốt: Xu Quái rơi từ quái theo tỉ lệ; KHÔNG đổi ngược; dùng cho trade + nguyên liệu Xưởng + bán /sell.
- Refining.java (config tinh-luyen, Cài đặt › Shop › Tinh Luyện): món = mảnh gốm/hạt vàng (không đặt được, không có giá trong worth.yml) + PDC
  dmm_tinhluyen; chặn chế tạo/rèn/đe/đặt. Menu bấm=1, Shift=tối đa (64). Bán: /tinhluyen ban [tatca] và chặn trước /sell hand|all của Essentials
  (giá riêng ~70% giá trị nguyên liệu theo worth.yml, góp quỹ khai hoang, không +20% kinh đô). Xu Quái: Enemy do người chơi giết, không từ SPAWNER,
  5% / EliteMobs (PDC namespace elitemobs) 40% ×1–3. Admin: /tinhluyen give <mã> [số] [người], /tinhluyen ds.
- Xưởng: workshop.items.<mã>.tinh-luyen {sat: 1} (Workshop.Recipe.refined, kiểm + trừ qua Shared.refining); panel giữ khoá này + ô nhập
  "Món Tinh Luyện cần thêm". Chưa gắn cho món nào (đồ 7 ngày, để người dùng quyết).
- Web: thẻ 🔮 Tinh Luyện (api/hangngay → tinhluyen). Cẩm nang game: 2 trang ở chương Tiền & buôn bán. camnang/lenh-con/lenh-hay-dung.
- Chưa chắc: Shopkeepers nhận món (so khớp đồ) — thử đặt trade; Bedrock menu rương; /sell all có in "không bán được" cho mảnh gốm không.
- Locator Bar (vạch chấm màu dưới máu/thức ăn = hướng người chơi khác): tính năng gốc Minecraft 1.21.6+, gamerule locator_bar ĐANG BẬT cả 4 server
  (mặc định). Chưa đổi gì; chờ người dùng quyết có giới hạn ở Bang Hội Chiến không.

### 19/10 tối: Rà hiệu năng + bảo mật (người dùng: "tối ưu hệ thống và chống bị hack… trong game lẫn website")
- ĐÃ SỬA:
  1. Web (site/app.py): IP lấy từ CF-Connecting-IP. Trước đó qua Cloudflare→tunnel→Traefik mọi người chung IP 172.20.0.1 → 20 lần sai mã
     /web là KHOÁ ĐĂNG NHẬP WEB CỦA CẢ SERVER 15 phút, nhật ký mất IP thật. Đã thử: log ghi IP thật. Lỗi 500 không trả chi tiết cho trình duyệt nữa.
  2. (Sau đó người dùng chốt: GIỮ phiên nhưng 60 phút thay vì 1 ngày; mã /web 60 phút thay vì 5 — Cài đặt › Đăng nhập trang web, web-login.code-minutes
     ở cả 4 server.) AuthMe (lobby, repo + data + settings.yml): ban đầu TẮT phiên (CGNAT 4G/quán net chung IP + offline mode → gõ đúng tên
     admin là vào thẳng), captcha sau 3 lần sai, cấm IP 30 phút sau 10 lần sai, danh sách mật khẩu cấm dài hơn. Đã `authme reload`.
  3. Bỏ OP của Linh ở sảnh (thừa: Linh có nhóm admin `*` toàn mạng; OP + phiên = chiếm được là toàn quyền).
  4. Sáng Tạo: ThaiAp lọc InventoryCreativeEvent (chỉ số tự chế, phù phép vượt cấp, trứng có dữ liệu sinh vật, thuốc tự chế, unbreakable,
     > 8 KB → bản trơn + log).
  5. Hiệu năng Bang Hội Chiến: paper-world-defaults (optimize-explosions, giới hạn mũi tên/kinh nghiệm/ngọc ender mỗi chunk,
     ALTERNATE_CURRENT, grass-spread 4, tắt dò mèo trên rương, chặn đi vào chunk chưa tải), SYNC_CHUNK_WRITES false, spigot merge-radius
     item 2 / exp 3 (sửa data/banghoi-1/spigot.yml, file này KHÔNG có trong repo). Khởi động lại 4 server lúc không ai online.
- ĐÃ KIỂM, ỔN: /quantri chuyển nguyên đường dẫn sang panel nhưng panel chặn /api/web/* khi có X-DMM-Player và chặn người không có quyền;
  tên luôn lấy từ phiên phía server; giá donate server tự tính; CSRF (Origin/Sec-Fetch-Site); cookie HttpOnly/SameSite; đường dẫn file tĩnh không
  thoát được thư mục; mã /web SecureRandom; mọi chỗ HTML chèn dữ liệu người chơi đều esc(); mật khẩu .env mạnh, chưa từng commit; server con chỉ
  nghe trong Docker; DuckyMoMoCore không có xử lý nặng mỗi tick (PlayerMove đều thoát sớm). Lúc rảnh: TPS 20, 0,4 ms/tick.
- CÒN LẠI (chưa làm, cần người dùng): (a) ĐÃ LÀM: cổng bảng Traefik 8091 của Trạm 6 chỉ mở 127.0.0.1 (sửa ~/works/tram6/docker-compose.yml,
  tạo lại gateway; CHƯA commit bên tram6 vì repo đó có thay đổi dở của phiên khác); (b) ĐÃ LÀM: người dùng xoá khoá trên playit, mình xoá PLAYIT_SECRET_KEY khỏi .env;
  (c) proxySharedSecret AuthMe đã hiện trong log phiên Claude (chỉ lưu ở máy) → muốn chắc thì đổi AUTHME_SECRET (khởi động lại proxy+lobby+relay);
  (d) SAO LƯU: phát hiện PAUSE_IF_NO_PLAYERS "true" → server vắng là KHÔNG sao lưu (bản mới nhất 07:06, cả chiều 19/10 chưa lưu) → đổi "false" ở
  mọi compose, đã `backup now` cả 4 server 21:57. Ngoài máy: dịch vụ backup-offsite (rclone, profile offsite, infra/backup-offsite.sh) + ./ops/saoluu-ngoai.sh;
  CHƯA cấu hình tài khoản đám mây (chờ người dùng chọn: Google Drive / OneDrive / B2 / MEGA); (e) đo lag thật cần spark profiler LÚC ĐÔNG NGƯỜI
  (`/spark profiler start --timeout 300`, link tải lên spark.lucko.me — hỏi người dùng trước khi gửi); (f) tối ưu mạnh hơn nhưng đổi lối chơi
  (entity-activation-range, villager không hoạt động, despawn gần hơn, giảm view-distance) chờ quyết; (g) khoá 5 lần sai theo TÊN trên web cho phép
  người khác cố tình khoá web của một người 15 phút (chấp nhận).

### 19/10 tối: Thái Ấp Sáng Tạo — server sangtao-1, chỉ vào qua cổng trong Thái Ấp (ĐÃ TRIỂN KHAI 16:38, CHƯA THỬ BẰNG NGƯỜI)
- Người dùng: Thái Ấp cho creative để giải trí → mình khuyên KHÔNG bật creative trong thế giới sinh tồn (chung túi đồ/rương ender/kinh tế = đường
  đưa đồ ra), làm server riêng. Người dùng chốt: "vào thái ấp thì sẽ cổng chuyển tới server sáng tạo, chỉ ở thái ấp mới có".
- modes/sangtao (compose include, backup-sangtao-1, Paper 2 GB, creative + force-gamemode, không Vault/Essentials/WorldEdit), mode.yml status closed
  = ẩn khỏi menu sảnh. velocity.toml thêm sangtao-1 (repo + data/proxy). Panel: GAME_SERVERS/SRV_VI/… thêm sangtao-1. ops: build-core, huongdan, tu-sua.
- DuckyMoMoCore ThaiAp: thai-ap.sang-tao ở Bang Hội Chiến → cổng tự dựng (0,64,6) thế giới thaiap, /thaiap sangtao, /thaiap congsangtao (admin),
  vé sangtao-ve.yml; ở sangtao-1 có sang-tao.mirror-file → chế độ bản sao (đọc thaiap.yml qua mount :ro /banghoi-dmm, không ghi, kiểm vé lúc vào,
  không vé → về sảnh; chặn ném đồ, kích nổ, wither/rồng, giới hạn 40 sinh vật/48 khối; /thaiap ve). Đã khởi động lại banghoi-1 + proxy + relay
  (không ai online), kiểm: cổng có khối + chữ nổi, sangtao-1 "BẢN SAO SÁNG TẠO", panel thấy sangtao-1.
- Chưa chắc: Bedrock qua cổng (BungeeCord Connect) chạy đúng không; LuckPerms prefix ở sangtao-1; 4 server cùng DuckyMoMoCore mới chỉ khởi động lại
  banghoi-1 + sangtao-1 (lobby/practice vẫn chạy jar cũ tới lần khởi động lại sau — không ảnh hưởng). Để sau: WorldEdit giới hạn, "bản vẽ".
- **GẤP (thấy trong log proxy 19/10):** Geyser 2.12.0 cảnh báo "NetherNet support is NOT enabled… Players will not be able to connect starting
  October 27th" → người chơi Bedrock (điện thoại) sẽ KHÔNG vào được từ 27/10 nếu chưa bật NetherNet (geysermc.org/blog/preparing-for-nethernet).
  **ĐÃ XỬ LÝ 19/10 16:43:** Geyser `transport: both` (RakNet + NetherNet), `webrtc-port: 19133`, signaling builtin TCP 19132,
  `advertise-addresses: ["125.212.218.74"]` (ĐỔI VPS THÌ SỬA Ở ĐÂY). VPS: `./ops/nethernet.sh vps` (DNAT + ufw TCP 19132, UDP 19133, relay-up.sh mới).
  Relay: ip rule tcp sport 19132 (pref 304), udp sport 19133 (305). Proxy publish thêm 19132/tcp, 19133/udp (LAN). repo proxy/plugins/Geyser-Velocity/config.yml
  = bản đang chạy (config-version 10). Kiểm: signaling qua VPS trả HTTP 404 (có phục vụ), Bedrock RakNet + Java qua VPS bình thường.
  CHƯA thử bằng điện thoại thật (WebRTC UDP 19133 chỉ thử được bằng client 26.40+). Người chơi vẫn nhập mc.goc6.io.vn cổng 19132;
  lần đầu vào bằng IP/tên miền không HTTPS sẽ hiện hỏi "tin tưởng máy chủ" (bấm đồng ý). Muốn bỏ hỏi: chứng chỉ HTTPS cho signaling (để sau).

### 19/10 tối: Rà soát toàn bộ + cẩm nang chi tiết (người dùng: "check lại toàn bộ tính năng rồi bổ sung, chỉnh sửa web, cẩm nang hướng dẫn thật chi tiết cho admin lẫn người chơi", "lệnh có ví dụ")
- Cẩm nang trong game (modes/_network/huongdan.yml, đã ./ops/huongdan.sh): viết lại thành 11 chương (mục lục chỉ 1 trang, tối đa ~11):
  batdau, luat, banghoi (cơ bản), thitran (thị trấn, quốc gia, hoà bình, Hội đồng, Tổ chức, /quangcao, /sukien, Bàn Tròn, Kiến Trúc Sư), congthanh,
  kinhte, khampha, pet, hangngay (quà hằng ngày + rank), practice, hotro. Bỏ chương rank/pokemon (gộp). Mỗi lệnh có "Vd:" và "Lưu ý:".
  Guide.java TỰ ngắt dòng + sang trang 13 dòng: trang dài đã tách thành "X (tiếp)" (script mô phỏng font, 124 trang). Mã batdau/banghoi/practice giữ nguyên.
- Đối chiếu số liệu với config thật: SỬA sai Kiến Trúc Sư (tuần 12 đăng ký / 13 bình chọn, cẩm nang + web ghi 9/10), web còn ghi mùa 10 tuần
  → 13 tuần (site/app.py season.weeks/register_week/vote_week/title_before_week; thanh lịch mùa vẽ 13 ô, tuần nhân đôi = double-week).
  /back KHÔNG có cho người chơi (chỉ nhóm quanly) → không đưa vào cẩm nang.
- Web: mục mới "Lệnh hay dùng, có ví dụ" (#lenh, 6 thẻ + khung Nhớ kỹ), danh sách sự kiện của Tổ chức Sự kiện (api/bantron → events, thẻ Tổ chức).
- camnang.yml: thêm quy trình admin (Trước khi khởi động lại, Mở Mùa 1, mất đồ/phá nhà với CoreProtect, nghi hack, phát quà nhanh,
  sửa cẩm nang game, Hội đồng & Tổ chức (admin), Việt hoá tools/vi); mục người chơi mới thêm 4 dòng. lenh-con: menu, web; lenh-hay-dung bổ sung.
- Chưa chắc: voice chat — proxy đã chạy voicechat (cổng 24454) và relay có ip rule, nhưng chưa ai thử bằng mod; cẩm nang game ghi "báo admin nếu
  không kết nối được"; camnang mục Simple Voice Chat vẫn ghi "CHƯA BẬT" (sửa sau khi thử).
- Người dùng chốt 19/10 tối: voice chat "từ từ bổ sung sau"; giá, phí, lịch sẽ sửa trên Cài đặt rồi chỉnh cẩm nang (huongdan.yml + web) sau.

### 19/10: Cốt truyện — Citizens + BetonQuest (ĐÃ TRIỂN KHAI 19/10 11:10, CHƯA THỬ TRONG GAME, CHƯA ĐẶT NPC)
- Nguồn ý tưởng: bài tuyển người Hydronic KSMP (NPC + nhiệm vụ). Người dùng chọn "cách A" = plugin có sẵn (không tự viết trong DuckyMoMoCore);
  bỏ ý quặng mới; vũ khí có skin tính sau; bài tuyển người chưa làm (server chưa xong).
- Plugin (chỉ banghoi-1): `betonquest:Sfp4oOVr` (3.2.0, MODRINTH_PROJECTS) + Citizens 2.0.44 build 4265 (Jenkins chính chủ, link ghim ở PLUGINS;
  Jenkins chặn /api/json nhưng tải artifact được; jar có nms v26_2_R1). BetonQuest `config.yml` trong repo: tắt updater (itzg ghim bản),
  ngôn ngữ vi-VN (lang/vi-VN.yml đã dịch nốt phần tiếng Anh), `conversation.default_io: dialog`, chặn /home /tpa /warp /rtp khi đang hội thoại.
- Gói `QuestPackages/cotruyen/`: package.yml (NPC theo TÊN `citizens "Già Làng" byName`, constants số lượng + thưởng,
  `package.version: 3.2.0-QUEST-2` — thiếu thì BetonQuest tự ghi và LƯU ĐÈ cả gói), chuong1/logic.yml (tag c1_b1 → c1_b1x → … → c1_xong,
  mục tiêu mobkill/block, thưởng money + experience, nhật ký), chuong1/hoithoai.yml (Già Làng, Bác Thợ Rèn, Người Gác Cổng).
  Placeholder `%constant.x%` được thay vào chuỗi TRƯỚC khi đọc số (đã đọc mã DefaultArgument) nên `-%constant…%`, `+%constant…%` chạy.
  Báo cả server dùng `command broadcast` (notifyall điền %player% theo từng người nhận).
- Cài đặt › Cốt truyện (category mới): kiểu bảng hội thoại, số quái/sắt/quặng, thưởng từng bước; apply `bq reload`.
- Web: `modes/banghoi/cotruyen.yml` mô tả chương cho web (thêm chương mới thì thêm ở đây). Site `story_info()` (/api/info → cotruyen; trang chủ thẻ
  📜 Cốt truyện trong mục Dungeon & Boss), panel `story()` (hồ sơ → cotruyen; trang tài khoản thẻ "Chương 1" bước N/5). Cả hai đọc
  `plugins/BetonQuest/database.db` chỉ đọc (bảng betonquest_player.active_profile + betonquest_tags, tag có tiền tố gói `cotruyen.` hoặc `cotruyen>`).
  Đã chạy thử site bản mới trên cổng tạm + Playwright (không lỗi JS), panel story() thử với database giả.
- Cẩm nang: camnang.yml (BetonQuest, Citizens), lenh-con.yml, lenh-vi.yml (bỏ khoá `compass` vì trùng Essentials), lenh-hay-dung.yml (journal),
  huongdan.yml (trang CỐT TRUYỆN trong chương Bang Hội Chiến; CHƯA chạy ./ops/huongdan.sh).
- Chưa chắc: NPC tên có dấu cách theo byName (cú pháp ngoặc kép của BetonQuest 2.1+); kiểu dialog trên Bedrock/Geyser và Java cũ; objective `fish`
  không dùng vì EvenMoreFish có thể đổi cá. Chương sau: nối vào sự kiện "Trái Đất lâm nguy".

### 19/10: Việt hoá toàn server
- Người dùng: "toàn server có mục nào chưa việt hoá thì làm nốt". Nguyên tắc: dịch mọi thứ NGƯỜI CHƠI gặp; lệnh/thông báo CHỈ ADMIN thấy
  (Citizens, /bq, /duels admin, zMenu admin, Sonar thống kê, GrimAC cảnh báo, nhật ký console Towny flatfile_*) giữ tiếng Anh.
- Công cụ `tools/vi/`: `yamlvi.py` (dump / apply theo đường dẫn / replace câu khớp nguyên văn; giữ chú thích bằng ruamel; kiểm biến
  {x} %x% <x>, `--no-angle` khi <x> chỉ là chữ cú pháp; khớp được khoá có dấu chấm), bản dịch JSON ở `tools/vi/bandich/`, script riêng:
  essentials.py, towny.py (Towny + SiegeWar), quickshop.py, evenmorefish.py, crazyenchantments.py (tên/mô tả 105 phù phép từ ce-vi.yml),
  auraskills-vi.py. Chạy trong image panel (có ruamel): `docker run --rm -e PANEL_PASSWORD=x -v "$PWD/tools":/t:ro -v <thư mục>:/f
  --entrypoint python3 duckymomo-panel /t/vi/yamlvi.py apply /f/<file> /t/vi/bandich/<x>.json`. Nâng plugin: dump lại, dịch câu mới.
- Đã làm (repo = file bản dịch; file do trang Cài đặt sửa thì chỉ sửa ở data, không đưa vào repo):
  EssentialsX (messages/messages_vi.properties đủ 1634 câu, config locale: vi — banghoi + practice; panel đọc whois/gc cả tiếng Việt),
  Towny 1842 câu + SiegeWar 412 câu (lang/override/global.yml đè MỌI ngôn ngữ, Towny language: vi-VN.yml; bỏ msg_ptw_warning_* vì Towny cấm),
  QuickShop-Hikari (overrides/vi_vn 238 câu, game-language vi_vn + enabled-languages [vi_vn]), EvenMoreFish (+80 câu + mọi menu gui/),
  Duels 772 câu (Practice), zAuctionHouse (messages + mọi menu + mô tả món + kiểu sắp xếp; config.yml chỉ ở data vì Cài đặt quản lý),
  Shopkeepers (lang/language-vi.yml 657 câu, language: vi; người chơi CÓ quyền tự mở shop nên dịch cả trình sửa), ShopkeepersAddon,
  TradePro, CrazyEnchantments (Messages + tên/mô tả phù phép trên đồ + menu /ce, cuộn giấy, bụi, pha lê, thợ rèn/tái chế; CE lưu phù phép
  bằng PDC nên đổi tên an toàn; Enchantments.yml/config.yml chỉ ở data), SimplePets (sảnh + banghoi), Sonar (proxy, màn kiểm tra chống bot),
  SkinsRestorer (locale vi, perIssuerLocale false, proxy + 3 server), PlayerParticles vi_VN, Geyser default-locale vi_vn, Chunky vi,
  OldCombatMechanics, GrimAC (câu đá người chơi), zMenu (câu người chơi gặp), uJobs (1 dòng sót), WorldGuard (deny-message vùng spawn).
  AuraSkills, BetonQuest đã làm trước đó. HoloMobHealth tự lấy tên quái theo ngôn ngữ máy (không cần), squaremap đã lang-vi.
- CHƯA CÓ HIỆU LỰC tới khi khởi động lại: proxy (Sonar, SkinsRestorer proxy, Geyser; tạo lại proxy thì PHẢI tạo lại relay), QuickShop
  (có thể), SimplePets (chỉ đọc thông báo lúc bật), PlayerParticles + SkinsRestorer ở server con (lệnh nạp lại không chạy từ console).
- Không dịch: DiscordSRV (đang tự tắt vì chưa có token bot), voicechat (chữ nằm ở mod phía người chơi).
- Lưu ý: bản Việt có sẵn của vài plugin có lỗi đã sửa: Towny (3 câu sai %s, trong đó 1 câu thừa %s dễ lỗi), Essentials (2 câu thiếu {1}),
  AuraSkills (viết cho cấu trúc cũ, khoảng chục mục bị bỏ qua).

### 19/10: Kỹ năng nghề — AuraSkills «mức cơ bản»
- Từ bài tuyển người Hydronic ("Phát triển kỹ năng"). Đã so: AuraSkills (chọn), mcMMO (Spigot trả phí, không có Modrinth), StormMMO/orbit/rpgskills (mới, ít người),
  ValhallaMMO/Fabled (không có 26.x). Người dùng chốt: trần 20 có Cài đặt; khả năng mana trùng Xưởng chỉ bậc 1; chỉ số chiến đấu rất ít có trần;
  chạy song song uJobs; làm lại mỗi mùa; Đấu Trường tắt chỉ số; không trả tiền theo kinh nghiệm; không bán tăng tốc cày.
- LƯU Ý đã phát hiện: mặc định kỹ năng NGHỀ cũng cộng chỉ số chiến đấu (Trồng trọt: Máu + Sức mạnh…) → đã viết lại toàn bộ rewards/*.yml.
- File repo `modes/banghoi/server/plugins/AuraSkills/` (sinh từ bản gốc 2.4.0 bằng script, pyyaml nên mất comment gốc): config.yml (vi, anti_afk bật,
  jobs tắt, crit tắt, disabled_worlds [dautruong] = gỡ máu/sát thương cộng thêm (đã đọc HpTrait/DamageListener), blocked_worlds [thaiap],
  WorldGuard blocked_regions [spawn], update_loot_tables false, check_for_updates false), skills.yml (6 kỹ năng, max_level 20), abilities.yml
  (tắt 15 khả năng đánh nhau/đồ rơi; còn lại max_level 4: may mắn 2/bậc, kinh nghiệm 5%/bậc…), mana_abilities.yml (replenish/treecapitator/
  speed_mine/terraform: unlock 10, max_level 1, 5 giây, hồi 300 giây; tắt 4 cái đánh nhau), stats.yml (tắt regeneration/toughness/crit/speed +
  trait tương ứng, hp health_scaling false), rewards/*.yml (mốc 5/10/15/20 lệnh `eco give {player} N` ở phần tử 0; chỉ số: Máu +1 ở Trồng trọt 20
  và Câu cá 20; Sức mạnh +1 ở Đốn gỗ 10 & 20, Đào mỏ 20, Đào đất 20, Pha chế 20 → tổng +1 tim, +2% sát thương), loot/*.yml base_chance 0.
- Panel: mục cài đặt có thêm `targets` (ghi một giá trị vào nhiều file/đường dẫn) và `template` ("eco give {player} {value}"). Nhóm Nghề › Kỹ năng nghề.
- DuckyMoMoCore `SkillSeason` (config aura-skills.reset-each-season, kynang-mua.yml): AuraSkills chỉ reset được người ONLINE (`skills skill reset <tên>`),
  nên mùa mới thì reset lúc từng người vào game. Web: site `skills_info()` (/api/info → kynang, trang chủ thẻ ⚒), panel `skill_levels()` (tài khoản);
  dữ liệu đọc plugins/AuraSkills/userdata/<uuid>.yml (không có tên → usercache.json).
- Tiếng Việt: messages/messages_vi.yml dựng lại theo cấu trúc bản tiếng Anh 2.4.0 (bản Việt có sẵn viết cho cấu trúc cũ, vài mục bị bỏ qua) + dịch 187 câu
  còn thiếu, 777/777 câu; tên kỹ năng thống nhất cẩm nang. Nâng AuraSkills: chạy tools/auraskills-vi.py (xem đầu file) rồi dịch câu mới.
- Chưa chắc: setlevel có phát thưởng mốc không; khả năng mana trên Bedrock.

### 19/10: Lữ khách — NPC lang thang kể chuyện theo Cổng Hoang (ĐÃ TRIỂN KHAI, CHƯA THỬ)
- Người dùng: "các npc xuất hiện ngẫu nhiên trên bản đồ và lấy nội dung câu chuyện dựa trên tên các cổng" → mỗi NPC gắn một dungeon đích
  của Cổng Hoang. Chốt: tên = nơi phía sau cổng; chưa mở mùa vẫn xuất hiện, chỉ kể truyền thuyết; giữ chương 1 làm mở đầu. "Phần phụ nghiên cứu sau".
- DuckyMoMoCore: `Wanderers` (config wander-npcs, lukhach.yml/json, lệnh /lukhach), `CitizensBridge` (MỌI tham chiếu API Citizens, chỉ gọi khi
  Citizens bật; LookClose/SkinTrait qua reflection vì nằm ở jar chính), `WanderersPapi` (%dmmlk_<mã>_<trường>%). WildGates thêm `zoneLabel`, `gateInfo`,
  và gọi `Shared.wanderers.onGatePass` sau passThrough. gradle: `net.citizensnpcs:citizensapi:2.0.44-SNAPSHOT` (maven.citizensnpcs.co); plugin.yml softdepend Citizens.
- BetonQuest: gói cotruyen/lukhach/lukhach.yml (sinh bằng script Python từ bảng lời thoại 4 người; sửa tay được). NPC byName. Tình trạng cổng qua
  `%ph.dmmlk_…%` + `variable … forceSync`. **Tag BetonQuest 3.x có tiền tố gói bằng dấu `>`**: `cotruyen>lk_cong_qua` (lệnh /bq tag phải ghi đủ;
  đã sửa các hướng dẫn cũ ghi thiếu). Thưởng: constants.lk_thuong.
- Phần phụ chưa làm: làm lại theo mùa (Cổng Hoang đặt lại mỗi mùa nhưng tag lk_*_xong giữ mãi), thêm lữ khách cho dungeon khác, skin, hologram "?" trên đầu.

### 19/10: NOTE chờ quyết
- (Người dùng 19/10: bảng này thuộc hệ thống VINH DANH, cứ note, làm cùng Quảng trường Danh vọng) Bảng xếp hạng dán TƯỜNG kiểu UniMC (TextDisplay áp sát mặt tường, dòng cuối riêng cho từng người nhìn: "Số lần bạn chết: Linh 0 (#175)")
  → làm được (TextDisplay + showEntity riêng từng người); chờ chọn bảng nào (giết, chết, giàu, câu cá…) và đặt ở đâu.
- "Cấp" cạnh tên trên bảng Tab (UniMC: "Linh [Cấp 0]"): chưa hiện; đề xuất dùng cấp chiến đấu EliteMobs %elitemobs_player_combat_level%
  (TAB groups.yml tabsuffix), chờ người dùng chọn loại cấp.
- Xưởng chế tạo: ĐÃ LÀM LẠI (commit 1d92c14), xem mục 4f trong TEST-CHECKLIST.

### 19/10: NOTE Xưởng chế tạo (CHƯA LÀM, chờ người dùng vào lại server UniMC xem menu "Server Tools")
- Người dùng: Xưởng hiện "sai logic": muốn đồ mang PHÙ PHÉP kiểu UniMC (Server Tools), không phải món tính năng tự chế
  (Túi Hư, Nước Nguồn, Dây Hút Đồ, Cung Máu, Búa Tạ). Chỉ lấy ý tưởng, không chép chữ/hình của họ.
- Server có sẵn CrazyEnchantments (94 phù phép bật: VeinMiner, AutoSmelt, TreeFeller, Harvester/Tiller/Planter, Experience, Haste…)
  và Enchantio (telepathy, smelting…). Workshop.java hiện chỉ gắn phù phép gốc (Registry minecraft:) → cần thêm "ce:<Tên>" (CeBridge)
  và "enchantio:<tên>" (NamespacedKey.fromString).
- Phác đã đưa: Cuốc Thợ Mỏ (VeinMiner + AutoSmelt), Rìu Tiều Phu (TreeFeller), Cuốc Nhà Nông (Harvester/Tiller/Planter),
  Cuốc Kinh Nghiệm (Experience + Haste), Telepathy thay Dây Hút Đồ.
- 19/10 người dùng chụp ĐỦ menu UniMC (chỉ lấy LOGIC "hiệu ứng + độ bền + thời hạn", không làm y hệt). 20 món + cách làm bên mình:
  Khoan Đào (3x3, CE Blast) · Đào Hầm (5x5, tự viết; chặn vùng vây) · Xẻng Đất (xúc 3x3, tự viết: Blast chỉ cho cuốc chim) ·
  Rìu Tiều (cả cây, CE TreeFeller) · Tự Nung (quặng → thỏi 100%, tự viết; CE AutoSmelt chỉ có tỉ lệ) · Đa Năng (3 trong 1, tự đổi
  cuốc/rìu/xẻng theo khối) · Gặt Lúa (thu 3x3 + gieo lại: CE Harvester + tự viết gieo) · Hút Đồ (bán kính 16, tự viết; không hút đồ
  người khác/quanh chỗ chết) · Rìu Bán (đánh vào rương là bán cả rương theo giá /sell, tự viết) · Túi Hư (CÓ) · Nước Nguồn (CÓ) ·
  Dung Nham vô tận (tự viết; chỉ đổ ở đất mình/đất hoang, không vùng vây) · Bắt Thú (trứng sinh vật; đề xuất chỉ thú hiền, tránh va
  hệ bắt pet MyPet) · Cờ Lê (lấy lồng quái 100%; ảnh hưởng kinh tế, đề xuất độ bền thấp + giá cao hoặc bỏ) · Móc Bay (móc câu kéo bay;
  chặn vùng vây) · Khiên Thần (phản đòn, giảm 50% sát thương; tự viết) · Cung Máu (CÓ, chỉnh +1 tim) · Nỏ Tỏa (3 mũi: Multishot gốc /
  CE MultiArrow) · Búa Tạ (CÓ: Wind Burst + Density) · Kiếm Nhật (lướt chém xuyên; tự viết hoặc giáo + Lunge) · Rìu Chiến (chí mạng +
  chảy máu; tự viết). Món đánh nhau: không tác dụng trong vùng vây (cờ pvp sẵn có).
- Cân nhắc: CE mở cho người chơi qua /ce (sách vĩnh viễn) → nếu lấy được Blast/TreeFeller… vĩnh viễn thì món Xưởng có hạn mất giá;
  chọn: tắt các hiệu ứng công cụ đó trong sách CE, hoặc để CE đắt/hiếm. Chưa kiểm tra /ce đang bán gì.
- Còn hỏi người dùng: (1) danh sách/ảnh phù phép của UniMC để tìm tương đương; (2) giữ lượt dùng + hạn 7 ngày hay vĩnh viễn;
  (3) bỏ hết hay giữ vài món tự chế cũ.

### 18/10 23:30: EliteMobs 10.9.10 + tiếng Việt
- Nâng EliteMobs 10.9.8 → 10.9.10: itzg (MODRINTH_PROJECTS elitemobs) KHÔNG tự nâng vì file mới cùng tên EliteMobs.jar → tải jar Modrinth (sha512 khớp),
  kiểm tra bằng javap: EMPackage.getEmPackages/getContentPackagesConfigFields/isInstalled, ContentPackagesConfigFields.permission, các sự kiện,
  PartyManager.getDungeonEntryMemberIds, lệnh dungeontp vẫn còn → thay jar, khởi động lại. Lỗi NoClassDefFoundError chỉ lúc TẮT bản cũ
  (jar bị thay khi đang chạy), sau khi bật: 0 lỗi, 18/18 gói. Bản cũ lưu ở scratchpad phiên (không giữ lâu): muốn lùi thì tải 10.9.8 từ Modrinth.
- /em language vietnamese: translations/vietnamese.csv (35.303 dòng, 50 chưa dịch). config.yml language: vietnamese.
- Dungeon ẩn: Thánh Đường Bóng Tối tự giấu tại 12723 63 -1229 (xem /dungeon ds).

### 18/10 tối: Chìa khoá tầng thật + NOTE dungeon (người dùng: "cho vào note")
- Đã cài 11 gói EliteMobs miễn phí (18/18 dungeon). Primis, Oasis PHẢI MUA (ô vàng chỉ là "bấm để kiểm tra quyền").
- Chìa khoá tầng (chain): trùm cuối rơi chìa thật cho từng người, dùng 1 lần mở vĩnh viễn tầng sau (theo NGƯỜI, không phải cả server),
  key-tradeable (Cài đặt, mặc định false = chìa ghi tên). Thay chìa "ảo" cũ (dữ liệu qua.<uuid> vẫn dùng cho lần đầu vượt/thống kê).
- NOTE TEST: (1) EliteMobs AdvancedCombatSystem.yml allowClassAbilitiesOutsideEliteMobsWorlds: true → kỹ năng class dùng được ngoài dungeon
  (công thành, PvP). Đề xuất tắt, chờ người dùng thử rồi quyết. (2) Xu rơi trong dungeon (EconomySettings: useVault true, currencyShowerTierMultiplier
  0.3) cộng thẳng vào xu chung → đo lượng thật khi thử, có thể thành nguồn in tiền.
- NOTE Ý TƯỞNG (CHƯA LÀM, người dùng mới hỏi có làm được không): quái trong dungeon rơi MẢNH (đồ vật, trao đổi được) → đổi đồ hoàn chỉnh.
  Làm được trên FragmentShop (/doimanh, event-fragments: types + shop). Phác: Mảnh Đá Cổ (tầng 1–3), Bạc Rỉ (4–6), Hắc Thiết (7–10 + ẩn),
  Tinh Hồn (vé/quỹ/cổng); quái thường ~15%, trùm nhỏ 1–2, trùm cuối 3–5 + "Lõi" hiếm; Hard ×1,5, Mythic ×2. Đồ đổi: đề xuất đồ vanilla có
  phù phép + tên/lore riêng (bộ theo dungeon); EliteMobs KHÔNG có lệnh phát custom item theo tên (đã dò 84 lệnh) → đồ EliteMobs phải qua API.
- EliteMobs hiện tại: mỗi dungeon toàn quái custom (Mỏ Hoang 38, Cống Ngầm 29…), trùm rơi đồ class riêng theo độ khó (Đỉnh Vách Đá 37 món),
  đồ ngẫu nhiên flatDropRateV3 0.2, khoá nhận đồ mỗi trùm 3 ngày (dungeonLockoutMinutes 4320).

### 18/10 tối: Cổng Hoang (/conghoang) — ĐÃ LÀM, CHƯA THỬ VỚI NGƯỜI CHƠI
- Người dùng chốt: thị trấn giữ cổng thu phí người ngoài; 6 cổng/mùa, 7 ngày thêm 1; chiếm được thị trấn là SỞ HỮU cổng (không giới hạn
  số cổng mỗi nước, Bá chủ vẫn giữ được); phí duy trì + khởi nghĩa sớm hơn đưa vào Cài đặt, mặc định 0; ngụy trang KHÔNG làm khó quá
  (hạt tím 16 khối + dòng nhắc khi đứng gần 6 khối, ngày đêm đều thấy).
- 18/10 tối: người dùng chốt TẠM 4 cổng/mùa (= 4 đích: Cống Ngầm, Bắc Cực chỉ qua cổng; Lâu Đài Hiệp Sĩ, Xưởng Hơi Nước lối tắt); đích ưu tiên dungeon ít cổng nhất nên 4 cổng 4 nơi. Thêm đích (mua gói / Đảo Hỗn Mang) thì tăng max-gates.
- Chỉ tự đặt cổng khi leaderboard.season-start có ngày (chưa mở mùa → không đặt). Cổng dẫn tới wild-gates.dungeons (tạm thời là các dungeon
  EliteMobs: Lâu Đài Hiệp Sĩ, Xưởng Hơi Nước, Bắc Cực, Cống Ngầm).
- NOTE (người dùng: "cứ ghi note lại"): sau này đổi đích Cổng Hoang sang "Đảo Hỗn Mang" — khu boss riêng (world riêng ~400×400, kiểu ảnh
  đảo nhiều mảng: rừng nấm, pha lê, nghĩa địa, sa mạc, tuyết, núi lửa…), mỗi mảng 1 boss (trang Boss của panel) + đồ rơi riêng, đường mòn
  theo độ khó, núi lửa là trùm cuối; mỗi Cổng Hoang dẫn tới 1 mảng. Cần người xây đảo (WorldPainter + schematic) trước. Ảnh map người dùng
  gửi là để làm VÙNG dungeon/boss, KHÔNG phải map thế giới.

### 18/10 tối: Cổng Dungeon (/dungeon) — ĐÃ LÀM, CHƯA THỬ TRONG GAME
- EliteMobs: preset Content-Only (không quái elite tự sinh). Người dùng đang chọn gói miễn phí trong /em setup.
- SỬA nhận định cũ: EliteMobs 10.9.8 CÓ quyền theo từng gói (trường "permission" của content package, hỏi ở setupDynamicDungeon,
  setupInstancedDungeon, vào nhóm). DungeonGate.java đặt trường này = duckymomo.dungeon.<mã> bằng reflection (mỗi phút, tắt module thì trả lại)
  + PermissionAttachment cho người qua cổng; vào bằng /dungeon vao → p.performCommand("em dungeontp <mã>.yml") (menu chọn độ khó/cấp của EliteMobs).
- 5 kiểu: chain (10 tầng Story Mode, requires), hidden (Cống Ngầm, Thánh Đường Bóng Tối night-only; CHƯA đặt lối vào: admin /dungeon datan <mã>),
  luck (Bắc Cực, Vé Vận Mệnh 0,05% quái / 0,3% cá, 1/ngày, roll 70%), ticket (Lâu Đài Hiệp Sĩ 3000, Xưởng Hơi Nước 5000), server (Pháo Hoa 100k/7 ngày,
  Đấu Trường Ma 150k/7 ngày, Kẻ Trói Buộc Thế Giới 1M tới hết mùa). Giá là ĐỀ XUẤT, sửa ở Cài đặt › Thế giới › Cổng Dungeon (dgt_*).
- Story Mode trong bản này là Dynamic Dungeon (người chơi tự chọn cấp lúc vào) → "cấp" trong /dungeon chỉ là gợi ý; chuỗi chỉ ép THỨ TỰ.
- Thành tích dg1, dg2 (danh hiệu chinhphuc), dga (timan). Web: thẻ Cổng Dungeon (trang chủ), thẻ Dungeon (tài khoản). dungeon.yml, dungeon.json.
- Thêm (theo người dùng): điều kiện phụ từng dungeon (min-pet-level, party "a-b" theo nhóm /em party, days, hours, seasons, hegemon-only,
  min-achievements; dungeon có điều kiện thì không cấp quyền sẵn, mỗi lần vào phải qua /dungeon vao, kiểm từng người trong nhóm),
  Mảnh Chìa cho dungeon ẩn (fragments; Thánh Đường Bóng Tối cần 3), Dungeon của tuần (weekly, +1500 xu, 3 lần/tuần), Kỷ lục tốc độ
  (top 5, web top 3), kho báu huyền thoại reveal-hidden 40% chỉ đường tới lối vào ẩn. BỎ ý giới hạn đồ EliteMobs khi công thành (người dùng chốt).
- CHƯA chắc: khoá trong EMPackage.getEmPackages() là "<mã>.yml" (log "đã gắn EliteMobs (x/18 gói đã cài)" sẽ cho biết; 0 dù đã cài = sai khoá).

### 18/10 (sửa): tham chiếu dùng chung qua Shared.java
- Lỗi: teardown gán Frontier.INSTANCE/Leaderboard.INSTANCE = null làm nạp lớp Frontier (có PlaceholderExpansion) ở sảnh/practice →
  NoClassDefFoundError LÚC TẮT, phần dọn dẹp dừng giữa chừng (dữ liệu có thể không lưu, /dmm reload hỏng). Sửa: Shared.java (không phụ
  thuộc gì) giữ frontier/leaderboard, Shared.unlocked(), Shared.clear(). Đã thử: khởi động lại 3 server + /dmm reload ở sảnh, practice: 0 lỗi.

### 18/10 03:30: khai hoang có nội dung, lính đánh thuê, sổ thành tích, server vắng
- Khai hoang: rtp.zones thêm malai/philippines/borneo/java có "requires: <mốc>" (toạ độ tính từ 1° ≈ 119,47 khối; X = kinh × 119,47,
  Z = −vĩ × 119,47) → /tpr ẩn tới khi mốc mở (RandomTeleport.Zone.open → Frontier.isUnlocked), đặc sản (region-specialty) chỉ tính khi mở,
  /dacsan ghi "chưa khai hoang". Người Tiên Phong: frontier.pioneer {count 10, rewards [xu 2000, danhhieu tienphong]}, quét mỗi 5 giây,
  lưu tienphong.<mốc>. Quà mới "danhhieu <mã>" (Rewards → Leaderboard.INSTANCE.grant; panel REWARD_RES + RW_KINDS). Giá mốc vẫn 300k/800k/2M
  (CHƯA đổi, chờ cân kinh tế). Dungeon: chờ người dùng tải map EliteMobs, muốn dungeon cấp tăng dần + chìa khoá cấp trước mở cấp sau.
- Mercenary.java /linhdanhthue (mercenary): đăng ký (không thuộc nước, không Liên minh, không acc phụ, >= min-hours), thuê (vua/hire-ranks,
  lính online, nhan|tuchoi, ngân khố trả, fee %), hợp đồng contract-hours: attachment siegewar.immune.to.war.nausea, SiegeSupply ăn kho thủ đô
  nước thuê (Mercenary.supplyTown), chặn đánh nhau với nước thuê (Culprit). Lưu linhdanhthue.yml. Không giữ cờ, không điểm Chiến Thần.
- Achievements.java /thanhtich (achievements.list, 18 mốc, 7 danh hiệu mới trong leaderboard.titles): khampha (vùng rtp, quét 5 giây),
  cauca/loaica (PlayerFishEvent, tên cá EMF đọc tick sau — CHƯA chắc EMF gắn PDC trước lúc đó), quai (Monster), petcap (MyPetBridge.level),
  batpet (pets.db), dautruong (dautruong.yml thắng), nauan (Dishes), kholuong (SiegeSupply), giochoi (Activity). thanhtich.yml.
- LowPop.java (low-population): quy mô = người có weekHours > 0, chốt theo ngày; dưới threshold: DailyQuests need() × quest-scale,
  PetCapture +capture-bonus, WarBalance: bên tấn công được điểm mà phe phòng thủ 0 người online → trừ (100 − absent%) %.
- Web: thẻ Lính đánh thuê (Công thành), Sổ thành tích + ghi chú server vắng (Hằng ngày), khai hoang có Người Tiên Phong; tài khoản: thẻ
  Sổ thành tích (tiến độ) + Lính đánh thuê (hợp đồng). Cài đặt: Lính đánh thuê, Server vắng, Sổ thành tích, ô Người Tiên Phong.

### 18/10 03:10: rà soát cẩm nang/web + sửa logic
- Rà 19 tính năng mới trên camnang, /huongdan, Cài đặt, web chưa/đã đăng nhập (script đối chiếu từ khoá); bổ sung chỗ thiếu: bùa trong
  /huongdan, trả nợ dần (camnang, /huongdan, web), mùa 3 tháng (camnang), lồng quái không cho exp (web). Mọi lệnh có mô tả tiếng Việt
  (all_commands: 0 thiếu ở 4 server).
- Sửa logic: Liên minh Hoà Bình + thị trấn trung lập KHÔNG tính Bá chủ (cả mẫu số), không phí thuộc địa, không làm chủ vùng đặc sản;
  "bên yếu" so dân cả nước với cả nước (trước so nước với 1 thị trấn); /kholuong noptat bỏ qua cá câu và món ăn (chỉ nộp khi cầm tay);
  bắt pet trượt hoặc đang chờ → setDamage(0) (con vật đã ≤10% máu không chết oan vì cú quăng dây xích).
- Trang tài khoản: thẻ "<thị trấn>: hậu cần & chiến tranh" (kho lương + thanh, nước: thị trấn, thuộc địa/tối đa, Bá chủ, nợ, % quỹ thêm),
  chức vụ Towny dịch tiếng Việt (taikhoan.ROLE_VI), dòng cho pet ăn cá. Đã thử bằng dữ liệu giả lập + Playwright.

### 18/10: mùa 3 tháng + Quảng trường Danh vọng
- Mùa = 3 tháng (13 tuần): leaderboard double-week 13, register-week 12, vote-week 13, title-member-before-week 12 (config + settings.yml).
  leaderboard.season-start vẫn TRỐNG (chưa mở mùa chính thức): đặt ngày khi mở server cho người chơi.
- Hết mùa: làm lại từ đầu với BẢN ĐỒ MỚI (người dùng chốt 18/10). Giữ: tài khoản, rank donate, đồ trang trí, danh hiệu, Quảng trường Danh vọng.
- Thư mục dùng chung data/shared (mount /shared: banghoi-1 ghi, lobby đọc ro, site đọc qua ../data). ops/init.sh tạo data/shared/vinhdanh.
- HallOfFameSnapshot (banghoi, hall-of-fame.snapshot): /shared/vinhdanh/S<leaderboard.season>.json, tạm tính 30 phút/lần, /vinhdanh tam (ghi ngay),
  /vinhdanh chot [ghide] (khoá). Bá Chủ: nước + vua + thị trấn + thị trưởng + thành viên với chức vụ (Vua, Thị trưởng, rank nation/town dịch
  ROLE_VI); Á Chủ 2–3; Chiến Thần, Đại Phú Hộ (topRich), Kiến Trúc Sư (architectVotes, đã mở package-private), Đấu Trường (dautruong.yml
  stats elo, có trận), Pet (pets.db).
- HallOfFameDisplay (lobby, hall-of-fame.display): /vinhdanh dat <mùa|moinhat> <tieude|bachu|chienthan|phuho|kientrucsu|dautruong|pet> ở chỗ
  admin đứng (bảng quay về phía admin), TextDisplay + ItemDisplay đầu top 3 (profile.complete lấy da Mojang theo tên), không lưu vào world,
  forceload chunk, làm mới khi file đổi (mỗi phút). Điểm đặt: data/lobby/plugins/DuckyMoMoCore/vinhdanh.yml. CHƯA đặt điểm nào (cần admin
  trong game chọn chỗ ở map sakura). Web: mục Vinh danh (#vinhdanh, api/vinhdanh).
- LỖI ĐÃ SỬA 18/10 02:54: DuckyMoMoCore TẮT HẲN ở sảnh vì Seasons.INSTANCE làm nạp lớp Seasons có anonymous PlaceholderExpansion
  (sảnh/practice không có PlaceholderAPI → NoClassDefFoundError lúc enable). Đã tách sang SeasonsPapi. QUY TẮC: lớp nào có thể bị nạp ở
  sảnh/practice (kể cả chỉ gọi static) thì KHÔNG được chứa PlaceholderExpansion; để expansion trong lớp riêng. (Arena/Frontier/LuckySpin vẫn
  chứa nhưng chỉ nạp ở banghoi.) Sau khi build nên khởi động lại cả lobby và xem log "Enabling DuckyMoMoCore" không kèm lỗi.

- LỖI ĐÃ SỬA 18/10 ~03:00: JS trang chủ hỏng từ commit d6ba60e (~01:20, dòng báo sự kiện mùa chen giữa if/else trong loadMua) → cả
  trang không tải dữ liệu. Sau khi sửa site/public/*.html hoặc panel/*.html: tách <script> ra và chạy `node --check`, rồi mở trang bằng
  Playwright xem pageerror. Web bảng bắt pet giờ cộng cả capture-bonus của sự kiện mùa (khớp PetCapture).

### 18/10: cân bằng chiến tranh (/canbang) + trả dần tiền cướp
- SiegeWar plunder_paid_over_time: true, 7 ngày (data/banghoi-1/plugins/SiegeWar/config.yml, đã swa reload); ô siege_plunder_* trên Cài đặt.
- WarBalance.java (war-balance), SiegeWar qua phản chiếu (registerEvent theo tên lớp): PreSiegeWarStartEvent (chặn khi nước còn nợ phí thuộc
  địa hoặc ngân khố không đủ quỹ đã tăng), SiegeWarStartEvent (thu thêm / hoàn lại phần chênh, setWarChestAmount), PreInvadeEvent (chặn quá
  max-colonies; 2 giây sau đặt TownMetaDataController.setRevoltImmunityEndTime theo số thuộc địa), BannerControlPointsEvent (bên được điểm
  là phòng thủ yếu hoặc đang đánh Bá chủ → adjustDefender/AttackerBattlePoints thêm %, tick sau). Mỗi 10 phút: tính Bá chủ (>= hegemon-percent
  % thị trấn và >= hegemon-min-towns), thu phí thuộc địa 1 lần/ngày (giờ VN, nợ lưu canbang.yml "no.<uuid nước>", trả dần), ghi canbang.json.
  Thuộc địa đếm bằng TownOccupationController.getOccupiedForeignTowns. RegionSpecialty: Bá chủ chỉ hưởng hegemon-max-regions vùng.
- Chưa thử với cuộc vây thật (cần 2 nước). Điểm cộng dựa vào BannerControlPointsEvent: nếu SiegeWar bắn sự kiện trước khi cộng điểm thì vẫn đúng
  (mình cộng thêm ở tick sau).

### 18/10 rạng sáng: kho lương, món ăn ra trận, đặc sản vùng, bùa bắt pet, sự kiện mùa (bỏ món đổi cá mặc định)
- Người dùng tự đặt món đổi bằng cá ở /doimanh: đã BỎ 6 món mặc định; vẫn giữ giá ca:<độ hiếm> + quà petexp để họ tự thêm.
- SiegeSupply.java /kholuong [nop|noptat|top] (config siege-supply): kho điểm theo UUID thị trấn (kholuong.yml, kholuong.json cho web).
  Mỗi 15 giây lúc battleActive, người SURVIVAL trong vùng vây: kho thị trấn mình >= cost/4 → trừ, +3 đói +2 no; không → HUNGER.
  Điểm: points.<VẬT_LIỆU>, fish.<độ hiếm>, dish-points; đồ có CustomModelData không nộp; sự kiện mùa supply-multiplier nhân.
- Dishes.java /nauan (bep, monan) (config dishes): GUI bếp, nguyên liệu {VẬT_LIỆU|ca:<độ hiếm>: số}, món PDC duckymomocore:dmm_mon,
  ăn (PlayerItemConsumeEvent) → hiệu ứng "TÊN cấp giây", cooldown-minutes giữa 2 món (bộ nhớ, mất khi khởi động lại).
- RegionSpecialty.java /dacsan (config region-specialty, vùng = rtp.zones): chủ vùng = phe ("n:<nước>"/"t:<thị trấn>") nhiều thị trấn
  nhất (spawn hoặc homeblock) trong bán kính; hoà = không chủ. Seasons.extraHarvest cộng % được mùa đặc sản cho dân phe chủ (gặt ở đâu
  cũng được, chỉ WHEAT/CARROTS/POTATOES/BEETROOTS/COCOA). Ghi dacsan.json.
- Seasons: events.<id> (season, from-day/to-day âm = từ cuối, rain on/off, growth nhân, harvest-bonus, capture-bonus.<nhóm>,
  supply-multiplier); Seasons.eventNow(); /mua sukien <id|tudong> (mua.yml ghi-de-sukien); %dmmmua_sukien%; /mua hiện tốc độ lớn gộp.
  Mặc định: lu (ngày cuối mùa mưa), han (ngày 3–4 mùa khô). 18/10 đang là Lũ (mùa mưa còn 1 ngày).
- PetCapture: Bùa bắt pet (NAUTILUS_SHELL, PDC dmm_bua=batpet) tay trái +charm-bonus%, mất 1/lần thử; /buabatpet [give <người> [số]]
  (duckymomo.pet.admin). EvenMoreFish thicau.yml: COMMAND:buabatpet give {player} 3/2/1 cho hạng 1/2/3 (chưa thử phát thật).
  capture-bonus của sự kiện mùa cộng vào bonusNow.
- Cài đặt: nhóm Kho lương, Món ăn ra trận (PvP & combat), Sự kiện mùa, Đặc sản vùng (Thế giới), ô Bùa bắt pet. Web: thẻ Kho lương,
  Món ăn ra trận (mục Công thành), banner sự kiện + Đặc sản vùng (mục Mùa); site/app.py season_event(), core_json().
- 18/10: siege-supply.spoil-percent-per-day (hao hụt theo giờ thật, mốc hao-hut-luc trong kholuong.yml; mặc định 0). Panel kiểu
  cài đặt "dishes" (dishesEditor trong panel/index.html + validate t == "dishes", DISH_FOODS/DISH_EFFECTS trong app.py phải khớp JS),
  ô mon_list → dishes.list. Đã thử bảng trong trình duyệt và hàm validate trong container panel.
- Chưa thử trong game.

### 17/10 khuya: mùa + câu cá gắn với pet và công thành
- MyPetBridge.java: gọi MyPet qua phản chiếu (active/of/type/entity/addExp/saturation), setAccessible vì lớp cài đặt có thể không public.
  Arena.addPetExp dùng bridge. Seasons.INSTANCE + Seasons.now()/nowName() cho module khác hỏi mùa.
- PetFish.java (pet-fish): chuột phải pet của mình bằng cá EvenMoreFish (PDC evenmorefish:emf-fish-rarity = Common/Rare/Epic/Legendary)
  → điểm no, % máu, kinh nghiệm; kinh nghiệm giới hạn/người/ngày (petfish.yml, chỉ giữ hôm nay). Huỷ PlayerInteractEntityEvent để MyPet
  không xử lý cú bấm. Không no/không đau/hết giới hạn thì không ăn.
- PetCapture: season-bonus.<mùa>.<nhóm> cộng % (mùa mưa thuytoc 15, nhay 10; mùa khô bay 10, thusan 5); /bxhpet tyle + web hiện.
- PetCombat siege-weather (mỗi 20 tick, chỉ lúc battleActive, người trong vùng vây, bỏ qua spectator/creative): đêm = giờ game
  13000–23000 → DARKNESS trừ ai có pet night-eyes trong pet-range (được NIGHT_VISION); mưa (world.hasStorm) → SLOWNESS nếu không
  ngồi trên vật cưỡi; nắng = ban ngày, không mưa, đúng heat-season → HUNGER trừ ai dẫn fire-pets. onPetWeather (NORMAL): pet đánh
  trong vùng vây lúc đánh: mắt đêm +night-pet-bonus ban đêm, nước +rain-water-bonus / lửa −rain-fire-malus khi mưa, lửa +heat-bonus
  nắng gắt. Báo người chơi bằng chat khi điều kiện đổi.
- FragmentShop /doimanh: giá "ca:<độ hiếm>" (cá câu), quà Rewards "petexp N" (món có petexp phải đang dẫn pet). (6 món mẫu đã bỏ 18/10.) Panel: FISH_COST_RE, REWARD_RES petexp, RW_KINDS "Kinh nghiệm
  pet"; settings.yml fm_shop đã có 6 món (nguồn khi lưu trang Cài đặt).
- Cài đặt: nhóm "Bắt pet theo mùa", "Cho pet ăn cá câu", các ô sw_* trong Pet chiến đấu. Web: thẻ "Trời đất trong trận công thành",
  công dụng cá ở mục Câu cá, % theo mùa ở bảng bắt pet. site/app.py season_id() (KHÔNG đặt tên season_now: trùng hàm cũ).
- Chưa thử trong game (người dùng để test sau).

### 17/10 tối: bắt pet theo xác suất, trao đổi pet, kinh nghiệm pet ở Đấu Trường, bảng xếp hạng pet
- PetCapture.java (pet-capture): MyPet bắt ở EntityDamageByEntityEvent MONITOR KHÔNG ignoreCancelled, cờ ChanceFlag có sẵn quay mỗi
  cú đánh (spam = chắc bắt) nên tự làm: pet-config.yml mọi loài thêm "Permission:duckymomo.pet.batduoc" (PermissionFlag), module ở
  HIGHEST xét đủ điều kiện (dây xích, MyPet.leash.<Loài>, không đang dẫn pet, ≤10% máu sau cú đánh như LowHpFlag) = 1 lần thử, trúng
  thì addAttachment quyền tạm, tick sau gỡ. Loài "Tamed" (Camel, Cat, Donkey, Horse, Llama, Mule, Parrot, Sniffer, TraderLlama, Wolf):
  con mình đã thuần hoá thì cấp luôn. Số lần thử lưu PDC duckymomocore:batpet_lanthu trên con vật; chờ giữa 2 lần theo người.
  % theo nhóm loài đọc MobTypes + DmmNhom từ skilltrees. plugin.yml: quyền default false (op không tự có; ai có * vẫn luôn trúng).
  pet-config.yml giờ nằm trong repo modes/banghoi/server/plugins/MyPet/ (bản cũ: scratchpad phiên này).
- Trao đổi: /pettrade <người> [giá] (Vault, xu game), accept/reject/cancel; quyền MyPet.command.trade.offer(.*)/receive(.*) cho default
  (tools/mypet.cmds, đã chạy 17/10).
- Arena.addPetExp: thắng trận còn được thưởng (daily-reward-cap, không giao hữu) → arena.pet-exp-win (30) cho pet đang dẫn.
- PetTop.java /bxhpet [hệ|tyle] (aliases toppet, petbxh): đọc pets.db bằng sqlite-jdbc có sẵn của Paper (chạy nền, open_mode=1).
  Đã thử câu SQL bằng driver đó trong container. Web: site/app.py pet_capture(), pet_top(), mypet_level(); trang chủ mục Pet có
  bảng xác suất + top 10; trang tài khoản hiện hạng từng pet (taikhoan.combat_pets "hang").
- Cài đặt: nhóm "Bắt pet theo xác suất (MyPet)" (petcap_*), arena_pet_exp.
- Chặn kinh nghiệm pet từ quái lồng (người dùng chốt): MyPet.Exp.Gain.PreventFromSpawnReason = SPAWNER, TRIAL_SPAWNER,
  SPAWNER_EGG, DISPENSE_EGG (data, ô mypet_exp_nofarm). MyPet đánh dấu bằng metadata lúc sinh: quái có trước lần khởi động lại vẫn cho.

### 17/10 tối: pet lớn theo bản tính loài (hệ × nhóm loài)
- Người dùng chốt: lúc bắt mọi loài như nhau, lớn lên phải theo bản tính loài (tank trâu đánh yếu, loài bay mới cưỡi bay...).
- MyPet: cây có "Inheritance": {"Skilltree": X} thì cộng toàn bộ nâng cấp của X (Skilltree.getUpgrades đệ quy); MobTypes [] = không
  loài nào chọn được. Khoá lạ trong JSON bị bỏ qua (dùng "DmmNhom" để web đọc). Notifications KHÔNG kế thừa (script tự gộp).
- tools/mypet/he.json = 5 hệ gốc (đã bỏ "%5>10" máu/sát thương chung và bay). tools/mypet/sinh-cay-ky-nang.py: NHOM (10 nhóm, tăng
  máu/sát thương mỗi 5 cấp sau cấp 10, kỹ năng thêm, bay), LOAI (đặc tính ~35 loài), TEN (tên Việt). Ghi 230 file vào
  modes/banghoi/server/plugins/MyPet/skilltrees/ (itzg chép sang data khi khởi động); script báo lỗi nếu loài trong pet-config.yml
  chưa thuộc nhóm nào. ID cây "<Hệ>_<nhóm>[_<Loài>]", Display = tên hệ → trong game mỗi loài chỉ thấy 5 hệ.
- Đổi nhóm/đặc tính: sửa script, chạy lại, khởi động lại banghoi-1. Pet đang dùng cây bị đổi ID sẽ mất hệ. 17/10 chưa có pet nào.
- Web: site/app.py pet_trees() trả (5 hệ, nhóm loài); index.html thẻ "Pet lớn theo bản tính loài"; panel/taikhoan.py hiện "Hệ · Nhóm".
- Cài đặt (PvP & combat › Pet chiến đấu): petc_nofly / petc_nofly_h → pet-combat.siege-no-fly(-height), có hiệu lực bằng dmm reload;
  mypet_ride_hunger → MyPet.Skill.Ride.HungerPerMeter (0 = quãng bay vô hạn, cần khởi động lại). Trang tài khoản: tên Việt + nhóm loài
  từng pet lấy từ DmmNhom.loai_vi trong cây kỹ năng (taikhoan.combat_pets).
- locale/MyPet_vi.properties (data, không trong repo): tên kỹ năng khớp mô tả (Hộ chủ, Dậm đất, Héo mòn, Sét đánh) + thêm Chảy máu
  (bản gốc MyPet thiếu cả tiếng Anh). Có hiệu lực ở lần khởi động lại banghoi-1 tiếp theo.
- Cân bằng (chưa thử): cấp 100 Thú lớn +72 máu +4,5 sát thương, Thú săn +27 máu +13,5 sát thương (trước: mọi loài +36/+9).

### 17/10 tối: MyPet — mốc kỹ năng cấp 15–50, bay của Thú Cưỡi, cấm cưỡi pet ở Đấu Trường
- Đọc mã MyPet 3.14.3 (giải nén CFR): khoá trong skilltree không phân biệt hoa/thường; quy tắc cấp "%5>10", "20", "1,5,9".
  Có khoá "Notifications" theo cấp (tin báo cho chủ khi pet đạt mốc) — đã dùng.
- Mốc mới (data/banghoi-1/plugins/MyPet/skilltrees, không trong repo; bản cũ ở scratchpad phiên này):
  Chiến Binh: Hộ chủ (Shield) 15/40, Dậm đất (Stomp) 20/50, Chảy máu 25, hồi máu 30.
  Đấu Sĩ: Héo mòn (Wither) 15, làm chậm 20, Chảy máu 25, Sét (Lightning) 30/50, đốt 40. Hào quang Sức mạnh GIỮ NGUYÊN (người dùng chốt).
  Thú Cưỡi: (sau đó chuyển bay sang nhóm Loài bay, từ cấp 5) bay cấp 10 (flylimit 40, regen 0,5), tốc độ 15/30/50, flylimit +40 (20), +70 (30), +100 (50); hào quang nhìn đêm/nhảy cao 25,
  chạy nhanh/kháng lửa 40; Dậm đất 35.
  Hậu Cần: nhặt kinh nghiệm 15, hào quang Đào nhanh I + nhìn đêm 20, máu 25, Đào nhanh II + may mắn 30, Hộ chủ 35, nhặt xa 40, kháng lửa + thở nước 50.
  Nông Trại: bắn tên 15/40/50, độc 20, hào quang hồi máu + may mắn 25, nhặt kinh nghiệm 30, Dậm đất 45.
- Bay (EntityMyPet.a_): đang ở trên không giữ phím nhảy → bay lên 0,2 khối/tick (4 khối/giây, giảm theo độ no nếu Affect-Ride-Speed).
  flylimit = QUÃNG ĐƯỜNG (mét, cả ngang lẫn dọc) bay được mỗi lần; trừ dần khi bay (chỉ khi HungerSystem bật + HungerPerMeter > 0, hiện bật);
  hồi flyregenrate mỗi tick khi pet chạm đất; bắt đầu từ 0 nên vừa gọi pet phải đứng đất chút mới bay. flylimit = 0 là bay vô hạn (trước đây đang như vậy).
  Không có trần độ cao riêng. Đang bay pet không mất máu khi rơi. Chặn bay theo vùng: cờ WorldGuard `mypet-fly`. Towny không chặn.
  Quyền MyPet.extended.ride.fly không cần (Permissions.Extended: false). Mọi loài cưỡi được (chuột phải bằng dây xích, cần hệ có Ride).
- Arena.onMount: thế giới dautruong hủy EntityMountEvent khi vật cưỡi là pet MyPet (tránh bay ra ngoài sân).
- PetCombat.noSiegeFlight (mỗi 5 tick, chỉ lúc battleActive): người cưỡi pet trong vùng vây, pet không chạm đất, không ở trong nước,
  3 khối dưới trống → leaveVehicle + Rơi chậm 6 giây + nhắc (5 giây/lần). Không chặn được bằng cờ mypet-fly vì vùng vây không phải vùng WorldGuard.
- Mọi loài MyPet cùng 20 máu, tốc độ 0.3 (pet-config.yml): sức mạnh chỉ theo cấp + hệ. Khác nhau theo loài: hình dáng/kích thước, 12 loài
  tự bay theo chủ (CanGlide), vài tiện ích sẵn (cừu xén lông, bò cho sữa, bò nấm cho súp, gà đẻ trứng, người đồng bị oxy hoá...), đồ ăn.

### 17/10: pet trên web
- Đã thử 38/38 loài trong PASSIVE_PETS gọi thật (`pet summon target b0ybn <loài>` → có thực thể quanh người chơi), cất lại hết.
- 17/10 tối: BỎ gọi/cất pet trên web (người dùng quyết định). Trang tài khoản › Trang trí › Pet trang trí chỉ LIỆT KÊ tên loài rank được dùng
  (không hình, không nút); gọi pet làm trong game bằng /pet. Đã xoá `web_pet`/`PET_SERVERS` (panel/app.py) và nhánh pet trong
  `taikhoan.cosmetic_command`; `taikhoan.cosmetics()` vẫn trả `pet.loai` để hiện danh sách.
- Pet theo rank (LuckPerms): Dân, Nam Tước, Tử Tước không có; Bá Tước 13 loài; Hầu Tước +14; Công Tước pet.type.passive (mọi pet hiền).
  Pet SimplePets chỉ là bản trang trí gọi ra, không bắt/thuần hoá.

### 17/10: MyPet — pet chiến đấu bắt trong game (Bang Hội Chiến)
- MyPet Legacy 3.14.3 (LGPL, Modrinth `mypet:mkMAIOff` trong MODRINTH_PROJECTS banghoi). Bản 3 "hết cập nhật" (tác giả chuyển sang
  MyPet 4 trả phí) nhưng có bản 26.2. Hook: Towny, PvPManager, Vault, PlaceholderAPI.
- Bắt: đánh tới yếu máu (LowHp) rồi chuột phải bằng dây xích. Quyền cho nhóm default server=banghoi-1: `tools/mypet.cmds` (79 lệnh, đã chạy):
  MyPet.leash.<loài> trừ Warden, Wither, EnderDragon, ElderGuardian, Ravager, IronGolem, Ghast, Giant, Illusioner + vài MyPet.command.*.
- data/banghoi-1/plugins/MyPet/config.yml (không có trong repo): DisablePetVersusPlayer: true (CHỜ người dùng quyết pet có đánh người chơi
  trong chiến tranh không), OverwriteLanguages: vi, OP-Notification: false. KHÔNG có lệnh reload: đổi config phải khởi động lại banghoi-1.
- Cẩm nang (camnang, lenh-con, lenh-vi), sách /huongdan chương banghoi thêm 2 trang. CHƯA thử bắt pet trong game.
- KHÔNG bán MyPet qua rank donate (pet chiến đấu = lợi thế chơi).

### 17/10: pet chiến đấu đánh người + công thành; phân biệt 2 loại pet
- MyPet config: `DisablePetVersusPlayer: false`, tên pet `Name.Tag.Prefix: <red>⚔ <aqua>`. Mọi loài sẵn 20 máu / tốc 0.3 (pet-config.yml) →
  sức mạnh do cây kỹ năng: thêm vào cả 5 skilltrees/*.st.json luật `"%5>10"` (mỗi 5 cấp sau cấp 10: Damage +0.5, Life +2). Chỉ ở data/.
- DuckyMoMoCore `PetCombat` (config `pet-combat`, softdepend MyPet): pet (lớp có interface de.Keyle.MyPet.api.entity.MyPetBukkitEntity,
  kể cả mũi tên pet bắn) đánh người chơi bị giảm `player-damage-reduce-percent` (50). Mỗi phiên công thành bốc thăm cố định
  `floorMod(hash(seed, phút bắt đầu), 100) < siege-pet-chance` (30) → biết trước; phiên không cho: huỷ sát thương pet lên người trong vùng vây
  lúc đang đánh. Báo cả server trước `announce-minutes` (60, 10), lúc bắt đầu phiên, khi vào game; lệnh `/lichcongthanh` (congthanh, lichvay).
  Cài đặt > Bang Hội Chiến > PvP & combat > Pet chiến đấu. Đã chạy: log "Pet chiến đấu: giảm 50%…", /lichcongthanh trả phiên 20:00 17/10.
- SimplePets display_name đổi thành "&d✿ &f<tên loài tiếng Việt> &7(%player%)" (repo + data, lobby + banghoi). Một người: 1 pet MyPet +
  tối đa 3 pet SimplePets cùng lúc (`pet-spawn-limit: 3`).
- CHƯA thử trong game: bắt pet, pet đánh người, giảm sát thương, tin báo phiên.

### 17/10: số pet giữ được theo rank; thanh máu quái
- MyPet `getMaxPetCount` = quyền `MyPet.petstorage.limit.N` LỚN NHẤT người chơi có (N = số pet CẤT trong kho, không tính con đang dẫn;
  không có quyền = 0). Cài đặt > Bang Hội Chiến > PvP & combat > Pet chiến đấu: "Số pet giữ được" cho Dân + 5 rank (tổng số pet);
  lưu = gỡ limit.1..19 rồi set limit.{minus1} cho nhóm, server=banghoi-1 (panel thêm biến {minus1} cho lệnh cài đặt).
  Giá trị tạm 17/10: Dân 2, Nam/Tử 3, Bá/Hầu 4, Công 5. Mỗi lúc chỉ 1 pet ra ngoài (MyPet 3 không cho nhiều).
- Thanh máu: PacketEvents 2.14.0 + HoloMobHealth 2026.1.3 + HoloMobHealth-PacketEvents 1.1.1 (banghoi). Chỉ hiện khi bị đánh (5 giây),
  tầm 20, chữ "tên máu/tối đa ❤ (%)"; số sát thương bay lên. Cài đặt > Hiển thị > Thanh máu. Boss EliteMobs: thanh máu riêng của EliteMobs
  (displayVisualHealthBars, displayBossBarForHighMultiplier đang bật). CHƯA xem trong game (có thể chồng chữ với tên boss EliteMobs).

### 17/10: Đấu Trường người + pet (/dautruong)
- DuckyMoMoCore `Arena` (config `arena`, dữ liệu `dautruong.yml`): thế giới trống "dautruong" plugin tự tạo mỗi lần khởi động
  (ChunkGenerator rỗng; keepInventory, không quái, giờ cố định). Towny ĐÃ TẮT ở thế giới này (`townyworld dautruong toggle claimable`
  + `toggle usingtowny`, lưu trong data Towny). MyPet worldgroups.yml: thêm dautruong vào nhóm default (pet theo người qua thế giới).
- Luồng: hàng chờ theo `<cỡ>:<cược>` (1v1/2v2), thách đấu 1v1 (/dautruong thachdau, dongy); khi đủ người và có sân trống: trừ cược,
  dịch chuyển a1/a2 vs b1/b2, petcall, đếm ngược (đứng yên), đánh fight-seconds; đòn chí mạng bị huỷ → loại (chuyển SPECTATOR);
  hết giờ: so % máu. Kết thúc: chia cược (trừ fee-percent), thưởng win-reward (tối đa daily-reward-cap/ngày), ELO k=32, trả về chỗ cũ.
  Cùng IP = giao hữu. Thoát giữa trận = loại + về chỗ cũ; khôi phục lưu `khoiphuc.<uuid>` để đưa về khi vào lại. /dmm reload, tắt
  server: huỷ trận + trả cược.
- Sát thương trong thế giới đấu trường: Arena quyết định hết (HIGHEST, setCancelled(false) ghi đè PvPManager/WorldGuard), pet bị giảm
  `pet-damage-reduce-percent`; PetCombat bỏ qua thế giới này. Đang trận/xem chỉ dùng /dautruong, /petinfo, /petskill, /msg.
- CHƯA có sân: chờ người dùng tải map (.schem, BuiltByBit) → plugins/WorldEdit/schematics/ → /dautruong admin den, //schem load, //paste,
  /dautruong admin dat <sân> a1|a2|b1|b2|xem. CHƯA thử trận thật (cần 2-4 người).

### 17/10: sân đấu + sảnh PvP (/warp pvp)
- 3 map MineSchematic (người dùng tải .schem, KHÔNG tải tự động: điều khoản trang cấm scrape; giấy phép không ghi rõ → ghi tên tác giả trên
  bảng chữ nổi). File ở data/banghoi-1/plugins/WorldEdit/schematics/ (không commit). Đặt bằng tools/place-schem.py (thêm Y_MIN/Y_MAX)
  với tiền tố `execute in minecraft:dautruong run`:
  san1 Abandoned Altar (mercuryceres) gốc (0,64,0) + nền cỏ y63 + tường/trần barrier x -8..76 z -8..68;
  san2 Battle Arena (RougeRex) gốc (300,64,0) (có sẵn tường barrier);
  san3 PVP 2 Obsidian Coven (Miguel302) gốc (600,13,0) chỉ lớp 51..128 của file (file gồm cả đất/hang bên dưới; lớp 128 là trần barrier).
  Điểm a1/a2/b1/b2/xem tính tự động (ô đứng được, hai đội nhìn nhau) — ghi ở dautruong.yml mục san.
- Sảnh: tools/build-pvp-lobby.py (tâm 0,64,-150; xuất hiện 0.5,65,-130.5; 3 cổng z -172 x -14/0/14, màu xanh/cam/tím; tường+trần barrier).
  dautruong.yml: sanh, cong.<sân> (ô bước vào), ten.<sân>. Bước vào cổng → tin bấm được: vào 1v1/2v2 (hàng chờ RIÊNG sân đó, khoá
  `<cỡ>:<cược>:<sân>`), có cược (gợi ý lệnh), Xem trận. Bảng chữ nổi DecentHolograms dt_cong_san1..3 + dt_sanh (file trong data,
  placeholder %dmmdt_<sân>_trangthai|cho|xem|ten%; 17/10 hạ xuống ngang tầm mắt trước cổng: y 70.6, z -168.5; dt_sanh y 68.4 z -146.5; chữ sáng, không dùng &8). Rơi khỏi sảnh → về sảnh. Warp Essentials `pvp` (data/.../warps/pvp.yml,
  world uid 0ec3615c-3436-4f3c-b530-1a87a3dc883a).
- LỖI ĐÃ SỬA: Arena.shutdown() từng save() ghi đè dautruong.yml (sửa tay rồi /dmm reload thì mất). Giờ không lưu lúc tắt.
- NPC Đấu Trường ở spawn Kinh Đô: (12704.5, 65, -2522.5) nhìn về spawn (hướng 90), dân làng WEAPONSMITH + 4 armor stand chữ, PDC
  `duckymomo:dautruong_npc`; bấm = `performCommand("warp pvp")` (có chờ 3 giây). Đặt/xoá: /dautruong admin npc [x y z hướng] | npc xoa.
  Phía tây đối xứng (12694, 65, -2523) để trống cho NPC /tpr (/dmm rtpnpc) nếu đặt.
- 17/10 tối: bước vào cổng = VÀO THẲNG SÂN chờ (waitSan; chỗ cũ = sảnh), mặc định 1v1 không cược; nút [Đổi sang 2v2] (/dautruong doi),
  [Cược…] (/dautruong cuoc <xu>), [Thoát về sảnh] (/dautruong roi); thanh hành động nhắc mỗi 2 giây. Sân đang đấu → vào xem + nút thoát.
  Trận khác bắt đầu trên sân → người chờ chuyển xem, xong trận về lại chỗ chờ. Thế giới đấu trường cấm phá/đặt khối (trừ admin).
- CHƯA thử trong game: đi qua cổng, bảng chữ, trận thật, góc nhìn điểm xem.

### 17/10 tối: pet — luật giết, Việt hoá, cài đặt
- `Culprit.of(damager)` / `Culprit.killer(victim)`: người chơi, mũi tên, mây thuốc, TNT, thú thuần hoá, pet MyPet (kể cả mũi tên pet) → chủ.
  Dùng ở Justice (đòn đánh tự vệ + giết dân Hoà Bình), Bounty, Leaderboard (Chiến Thần), WarRules (tài khoản phụ). Trước đó pet giết = không ai.
- MyPet có 21 kỹ năng (Backpack Beacon Behavior Bleed Control Damage Fire Heal Knockback Life Lightning Pickup Poison Ranged Ride Shield Slow
  Sprint Stomp Thorns Wither); Mining/Lumberjack/Fishing/Toolbox… chỉ có ở MyPet 4 (trả phí). 5 cây (data/.../skilltrees/*.st.json) đã Việt hoá
  Name/Display/Description: Combat=Chiến Binh, Utility=Hậu Cần, PvP=Đấu Sĩ, Ride=Thú Cưỡi, Farm=Nông Trại (ID giữ nguyên). Chưa dùng: Bleed,
  Lightning, Shield, Stomp, Wither (khoá: chance/damage/duration/interval/redirect). Đáng lo: Ride cấp 10 CanFly (bay qua tường thành), PvP Beacon
  Strength +2 cho chủ — chờ người dùng quyết.
- data/banghoi-1/plugins/MyPet/locale/MyPet_vi.properties: bản vi gốc + 130 dòng tự dịch (thiếu/còn tiếng Anh).
- Cài đặt > PvP & combat > "Pet chiến đấu - lên cấp, hồi sinh, đói (MyPet)": Exp.Modifier.Global, LevelCap, Exp.Loss.Percent, Respawn.Time.Default
  Factor/Fixed, Respawn.EconomyCost Fixed/Factor, HungerSystem Active/Time, Skilltree.SwitchFee.Percent, Leash.Consume. Cần khởi động lại banghoi-1.

### 17/10 tối: web cho pet, Đấu Trường, lịch có pet
- Plugin: Arena ghi plugins/DuckyMoMoCore/dautruong-live.json mỗi 5 giây (sân: tên, trạng thái, đang đấu, cho, xem; top 10 ELO).
- Site (site/app.py): /api/dautruong (cache 5s, cũ >60s = ok:false); /api/info thêm siege.upcoming (12 phiên trong 14 ngày theo lịch
  SiegeWar + cờ pet, `java_objects_hash` khớp Java Objects.hash(seed, phút) — đã đối chiếu bằng javac), siege.pet_chance, pet (reduce, slots theo
  petc_slots_* của settings.yml, trees từ skilltrees, level_cap, respawn), arena (luật), player.tp_delay/tpr_warmup. Bỏ qua tuần chẵn/lẻ
  (allowed_siege_battle_session_weeks) khi tính phiên sắp tới.
- Trang công khai: mục #pet, #dautruong (trực tiếp, 10 giây/lần), bảng "Phiên sắp tới & pet tham chiến" trong #congthanh, luật dịch chuyển
  3 giây + pet tính cho chủ, 2 hỏi đáp. Thanh menu trên cùng đã dài (Hỏi đáp bị che ở 1300px) — cân nhắc gom bớt.
- Trang tài khoản: thẻ "Pet chiến đấu" (taikhoan.combat_pets: pets.db chỉ đọc, JOIN players theo mojang_uuid, cấp = pet_level(exp) theo công
  thức exp.js; giữ tối đa = quyền MyPet.petstorage.limit.N lớn nhất + 1) và "Đấu Trường" (ELO, thắng/thua, thưởng hôm nay).

### DANH SÁCH CẦN THỬ (người dùng chốt 15/10: làm xong hết rồi thử một lượt; mục 16–25)
Triển khai một lần: `git stash && git pull && git stash pop` → `./ops/build-core.sh` (lỗi biên dịch thì gửi ảnh, Java viết ở cloud CHƯA biên
dịch) → kiểm tra không ai online → `docker compose up -d --no-deps lobby banghoi-1 practice-1` → `docker compose up -d --build panel site`.
Log: `docker compose logs banghoi-1 --since 5m | grep -iE "DuckyMoMoCore|Mùa:|Vũ khí nổ|Xưởng|EvenMoreFish|ERROR|Exception"`.
- [ ] Mùa: `/mua`, `/mua dat kho` rồi `/mua dat tudong`, `/papi parse me %dmmmua_id%` = mua/kho; cây lớn nhanh/chậm; mưa theo mùa.
- [ ] Câu cá: câu ở sông và biển ra cá Việt có độ dài; cá theo mùa; sách phù phép vẫn câu ra; `/emf shop`; thi câu T7/CN 20:00.
- [ ] Chống x-ray: dùng texture xray thử dưới y 64 không thấy quặng.
- [ ] Bảng bên phải 2 trang đổi mỗi 8 giây; giết/chết/KDA đúng.
- [ ] Vũ khí nổ: end crystal / respawn anchor / nổ giường không trừ máu người chơi; xe goòng TNT còn ~30%.
- [ ] Sàn thu mua: `/donhang tao 64 1` khi cầm đất, nick khác `/donhang` giao, người mua `/donhang cuatoi` nhận; huỷ (chuột phải, `/donhang huy <số>`)
      trả lại tiền phần chưa giao; web mục Hằng ngày hiện đơn.
- [ ] Hạng của bạn: thêm 2 dòng `/dh line add …` (cẩm nang) → hologram hiện "Bạn: … (#…)".
- [ ] Khai hoang: `/khaihoang`; bán `/sell` thấy quỹ tăng; `/khaihoang gop 100`; thử `/khaihoang them <xu>` cho đủ mốc → viền nới
      (sau đó sửa border_* ở Cài đặt); web thẻ Khai hoang.
- [ ] QR ủng hộ: `/rank mua` → chọn gói → tấm bản đồ QR quét được bằng app ngân hàng; [Tôi đã chuyển khoản] → admin nhận tin; trang Donate có yêu cầu;
      bản đồ không vứt/cất rương được, hết 10 phút tự mất. Cần điền ngân hàng ở site/config/site.yml.
- [ ] Treo thưởng: `/treothuong dat <người> 100`, người khác hạ → nhận tiền; giết liền 5 mạng → hệ thống treo; không treo được dân Hoà Bình; web mục Hội Bàn Tròn.
- [ ] Xưởng: `/xuong` chế Túi Hư (chuột phải mở rương ender), Nước Nguồn (đổ không cạn, lượt giảm), Dây Hút Đồ (đào đá → vào túi), Cung Máu
      (bắn trúng hồi máu), Búa Tạ; `/xuong tang <id>` để thử nhanh; món PvP bị chặn trong vùng vây đang đánh.
- [ ] Prefix Manager: Role > Manager > Áp bộ quyền khuyên dùng → [Manager].
- [ ] Trang web: mục Mùa & câu cá, Hằng ngày (Khai hoang, Sàn thu mua), Hội Bàn Tròn (Treo thưởng).
- [ ] Sảnh sau bản sửa PAPI: la bàn mở menu, chọn Bang Hội Chiến vào được; log sảnh có "Menu chọn chế độ: bật", không có "Could not load".
- [ ] Practice: log không có "Could not load plugin 'DuckyMoMoCore.jar'".

### Người dùng phải tự làm (nhắc lại khi phù hợp)
- Thêm `[boot] command = modprobe tcp_bbr` vào `/etc/wsl.conf` (để BBR có sau mỗi lần khởi động WSL):
  `wsl -d Ubuntu-22.04 -u root -- sh -c "printf '\n[boot]\ncommand = modprobe tcp_bbr\n' >> /etc/wsl.conf"`
- Đổi mật khẩu root VPS Vinahost (mật khẩu từng lộ trong ảnh chụp; Claude KHÔNG dùng mật khẩu, chỉ SSH key `~/.ssh/duckymomo_vps`).
- Huỷ/hoàn tiền BKNS (đừng tắt VPS BKNS khi chưa hỏi). Trả lời đóng ticket Vinahost #244254.
- Đăng ký server lên các trang vote, gửi link để điền `vote.sites`.
- Thử trong game: `/vongquay`, `/diemdanh`, `/nhiemvu`, `/dmm luudo`, nút Đưa, gọi boss, chia mảnh, `/doimanh`, `/skins` (menu), Tab sau `/dmm`,
  món thư viện có phù phép CE, sửa boss có sẵn rồi gọi ra xem.

### Còn dở / hoãn (chờ người dùng quyết)
- Voice chat (mục 17) + server Discord: người dùng chốt làm từ từ. Cần hỏi người chơi dùng TLauncher hay SKLauncher.
- Ý tưởng chưa làm (từ bài tuyển mem / UniMC): thưởng theo mốc giờ chơi, rương quay đặt ở spawn làm hình ảnh cho vòng quay (vé không bán),
  AuraSkills (chỉ nếu tắt cộng chỉ số chiến đấu), balo chế tạo. Không làm: bán chìa khoá rương, coinflip/cá cược, ngân hàng lãi, prestige,
  minion, khu AFK, đồ VIP tăng sức mạnh, nhiều loại tiền, xô dung nham vô tận.
- Số liệu cần chỉnh theo kinh tế thật sau vài ngày chạy: mốc Khai hoang (300k/800k/2tr), giá đồ Xưởng, giá cá (worth-multiplier), thưởng thi câu.
- Shopkeepers (dân làng bán hàng) chưa sửa được trên web vì plugin tự ghi đè file lưu khi đang chạy; cây giao dịch dân làng chờ quyết định.
- Thiết kế sự kiện boss "Trái Đất lâm nguy". Cân bằng kinh tế. Ký hiệu tiền Essentials `$` hay "xu" (đã hỏi, chưa trả lời).
- Chuyển Docker Desktop → Docker Engine trong WSL (mạng Docker Desktop là nút cổ chai băng thông) — chưa làm.
- Nhờ người chơi miền Nam đo ping qua VPS.
- Cấp phù phép > 10 hiện "enchantment.level.N" trong tooltip (client chỉ có chữ La Mã tới 10). Có thể làm: ẩn dòng phù phép gốc + tự ghi
  "Sắc bén XX" vào lore — chưa làm, chưa hỏi.
- Phù phép CE nói "đồng minh" (Commander, Angel, Charge, Revenge, Doctor, Leadership): chưa rõ CE có nhận Towny làm đồng minh.
- EliteMobs có ghi đè file boss mặc định khi cập nhật plugin hay không: chưa rõ (khuyên dùng "Chép thành boss mới" cho boss lâu dài).

---

## 0. Đọc trước — tình trạng 13/10/2026 (chuyển sang account Claude mới)

### Cách làm việc với người dùng (đã thống nhất, phải giữ)
- Trả lời **tiếng Việt**, thực tế, nói rõ chỗ chưa chắc. Không tự chốt thay người dùng.
- **Chỉ Docker Compose.** Không commit mật khẩu (để `.env`, file cấu hình dùng `${CFG_...}`). Mật khẩu router/modem/Wi-Fi KHÔNG ghi vào repo.
- **Trước khi khởi động lại server, kiểm tra có ai online** (`docker exec duckymomo-<srv>-1 rcon-cli list`).
- **DuckyMoMo, tram6, goc6 phải luôn chạy** (cùng máy, WSL Ubuntu, Docker Desktop).
- Người dùng (chủ server, tên trong game **Linh**) **cố ý vào game bằng địa chỉ playit**, đừng khuyên dùng localhost.
- **Cài plugin / thêm lệnh mới → ghi ngay vào cẩm nang** `modes/_network/camnang.yml` (+ `huongdan.yml` nếu người chơi cần, chạy `./ops/huongdan.sh`) và lệnh con vào `modes/_network/lenh-con.yml`. Trang `/camnang` báo jar "chưa có trong cẩm nang" nếu quên.
- Kiếm tiền đúng luật Mojang: rank donate CHỈ trang trí; không bán đồ vượt cấp, không bán cape, không bán gì Pokémon.
- Commit/push chỉ khi người dùng bảo. Trước khi push: `./ops/init.sh && docker compose config --quiet && docker compose --profile pokemon config --quiet` trong bản sao sạch rồi xoá `.env`/`data/` tạo ra.

### Đang chạy
- Proxy Velocity (cổng 25565 → 25577, Bedrock 19132/udp; Geyser, Floodgate, AuthMe, SkinsRestorer, **Sonar**), `lobby`, `banghoi-1` (Bang Hội Chiến mùa 1, map Trái Đất 1:1000 Đông Nam Á), `practice-1` (Duels, beta), MariaDB, panel Cài đặt (`127.0.0.1:8889`, qua Trạm 6 `https://app.goc6.io.vn/mc/caidat/`), Dozzle log (8888), squaremap (8890), trang giới thiệu `site` (8892), restic backup ra `/mnt/f/DuckyMoMo-backups`.
- Vào game (từ 15/10 11:15): **chính** VPS Vinahost — Java `mc.goc6.io.vn` (A → 125.212.218.74, SRV → 25565), Bedrock `mc.goc6.io.vn` cổng **19132** (mặc định); người dùng đã vào
  game thật qua VPS 15/10 03:18 (proxy ghi IP thật). **Dự phòng** playit — Java `pgsql-youths.tun.ply.gg` (SRV, cổng 28438), Bedrock `pgsql-mississauga.tun.ply.gg:21185`. `playit-watchdog` tự khởi động lại agent khi kẹt. **Tạo lại proxy thì phải** `docker compose --profile tunnel up -d --force-recreate playit`.
- DuckyMoMoCore (plugin riêng, `network-core/core-paper`, build `./ops/build-core.sh`): dọn đồ rơi, lọc chat, chặn job đồ đặt, lớp thị trấn trên map, menu Bedrock, rơi void, định dạng chat, rank donate `/rank`, sách `/huongdan`, đồ vượt cấp `/dmm enchant|sach`, xuất lệnh `/dmm lenh`, dịch chuyển ngẫu nhiên `/tpr`.

### Làm trong ngày 12–13/10 (chi tiết ở các mục bên dưới và git log)
- Trang Cài đặt: phân loại, Nghề (uJobs), Chống hack (Grim), Phù phép, Shop, Người mới (kit `tanthu`), trang **Role** (cấp/gỡ role + rank donate, sửa quyền từng role theo danh mục `modes/_network/quyen.yml`), trang **Cẩm nang** và **Cẩm nang game** (sửa sách `/huongdan` trên web).
- **Cẩm nang: mục "Tất cả lệnh"** — mỗi server tự xuất mọi lệnh ra `data/<srv>/plugins/DuckyMoMoCore/commands.json` (30 giây sau khi bật, hoặc `/dmm lenh`); panel gộp với `lenh-con.yml` (lệnh proxy + lệnh con Towny/CoreProtect/LP/WG/WE/AuthMe/Duels/shop…, ghi tay) và mô tả tiếng Việt `lenh-vi.yml`; cột "ai dùng được" suy từ quyền LuckPerms. Còn ~57 lệnh "?" (plugin không khai quyền); lệnh con ghi tay CHƯA thử từng cái trong game (Duels, SiegeWar dễ sai nhất).
- Plugin mới: Enchantio, CrazyEnchantments, Shopkeepers(+Addon), SimplePets (`/pet`, `/pets` mở menu), PlayerParticles, Sonar (proxy).
- **`/tpr` dịch chuyển ngẫu nhiên (13/10)**: `RandomTeleport.java` (+ `VaultEconomy.java`, Vault API 1.7.1 từ jitpack). Config `rtp` ở `modes/banghoi/server/plugins/DuckyMoMoCore/config.yml` (đã chép vào data/): 8 khu (taybac, lao, trungbo, nambo, thailan, myanmar, namtq + ngaunhien = cả vùng mùa), tâm theo kinh/vĩ độ thật ×119,47, bán kính 230. Chỉ thả xuống đất hoang Towny (8 điểm quanh cách 32 block cũng phải hoang), không nước/dung nham/biome ocean-river-beach, ngoài khu spawn (x 12556..12801, z -2623..-2298), chunk chưa tạo thì bỏ qua. Menu rương (Java) / form (Bedrock). Thời gian chờ (mặc định 300 giây, nhớ qua `/dmm reload`) và phí (mặc định 0, trừ sau khi tìm được chỗ, hỏng thì trả lại) trên trang Cài đặt > Thế giới. Quyền: `duckymomo.rtp` (mọi người), `.bypass`, `.free` (op). Essentials tự nhường `/tpr` (đã thử: RCON `tpr` trả lời của DuckyMoMoCore). PvPManager chặn lệnh khi đang đánh nhau. NPC: `/dmm rtpnpc` đặt dân làng (thợ bản đồ, không AI, bất tử) + chữ nổi bằng armor stand ẩn tại chỗ admin đứng; bấm mở menu; `/dmm rtpnpc remove` xoá. **Chưa thử trong game** (lúc làm không có ai online): chờ người dùng đặt NPC ở spawn và thử vài khu.
- **Dọn rác thông minh hơn (13/10)**: `ItemCleaner` không dọn đồ trong 8 block quanh chỗ người chơi chết trong 10 phút (`death-protect-*`); bỏ qua lượt dọn và đếm ngược trong giờ phiên đánh (đọc thẳng `battle_session_scheduler` trong `plugins/SiegeWar/config.yml` mỗi lần kiểm tra, theo lịch chứ không xét có cuộc vây thật); `exclude-areas` (mỗi dòng `x1 z1 x2 z2`) và `skip-towns` (mặc định tắt). Tất cả trên trang Cài đặt > Hệ thống > Dọn rác. Chưa thử trong game.
- **Luật mùa 1 chốt một phần (14/10)**, xem `docs/banghoi-luat-mua1.md` (đầu file ghi rõ đã chốt / còn treo). Đã code `WarRules.java`:
  `pvp-rules.wild-pvp` (mặc định TRUE, người dùng chốt: PvP đất hoang luôn bật, không theo lịch công thành; false = chỉ đánh trong vùng vây lúc có phiên), chặn `/t|/n rank add` quân hàm (guard, private…general)
  khi người nhận chơi < `min-rank-hours` (5) hoặc là tài khoản phụ, `/dmm phu add|remove|list` (`taikhoanphu.yml`), tài khoản phụ không gây sát thương
  trong vùng vây lúc có phiên. `SiegeWarHook.java` gọi `SiegeWarAPI` qua reflection (`isBattleSessionActive`, `isLocationInActiveSiegeZone`):
  dùng cho dọn rác (nghỉ khi có phiên thật, thay cho đọc lịch), `/tpr` (không thả vào vùng vây). Trang Cài đặt > Chiến tranh > "Giai đoạn mùa và PvP":
  công tắc vây thành (đang BẬT để thử), PvP đất hoang, giờ phong quân hàm. **Chưa thử trong game.** Nether/End: đã CẤM lập thị trấn (`claimable=false` trong `data/.../Towny/data/worlds/world_nether.txt`, `world_the_end.txt`, không có trong repo: mùa mới phải tắt lại bằng `townyworld <world> toggle claimable`). Còn treo: bán kính thu phục
  thị trấn hoà bình, bảng xếp hạng (thiết kế ở mục 8, chờ duyệt rồi code module Leaderboard).
- **Liên minh Hoà Bình + Hội Bàn Tròn + tự quản (bàn 14/10, CHƯA CODE)**: `docs/banghoi-luat-mua1.md` mục 10 ghi rõ phần [chốt] / [treo].
  Ý chính của chủ server: một liên minh hoà bình duy nhất; đất hoà bình lan rộng liền kề, giá tăng dần; vùng vây được trùm lên đất hoà bình
  (dân hoà bình di tản, cần báo trước); giết dân hoà bình bị Hội Bàn Tròn (chủ các thành lớn) xử, họp trực tiếp tại chỗ trong vùng hoà bình;
  vi phạm gần giờ công thành thì cấm vào vùng vây; giấy phép tổ chức cho dân hoà bình; admin chỉ quan sát, xử hack. Tạm cho 2 tài khoản/người.
- **Bảng chốt 45 mục (14/10)**: `docs/banghoi-luat-mua1.md` mục 11 (đè lên các chỗ [treo] ở mục 8, 10). Còn hỏi: mục 3 (tắt thu phục,
  đã giải thích lại), mục 40 (quốc gia cần bao nhiêu thị trấn). Nhiều con số là cài đặt (mức phạt, giờ họp, báo trước công thành, đại chiến).
  Thứ tự code đề xuất: (1) đất đai + giờ chơi thật + tài khoản + `/choilai`; (2) Liên minh Hoà Bình; (3) Hội Bàn Tròn + án; (4) xếp hạng.
- **Đợt code 1 (14/10, chưa thử trong game)**: `Activity.java` (giờ chơi thật, `hoatdong.yml`), `TownRules.java` (Towny events: PreNewTownEvent,
  TownPreClaimEvent, PreNewNationEvent, TownPreAddResidentEvent/TownRemoveResidentEvent; đất theo dân hoạt động = `setBonusBlocks` âm, số đã áp
  lưu ở `datdai.yml`; PreSiegeWarStartEvent qua `registerEvent` + reflection), `PlayerReset.java` + `Integrations.java` (/choilai: QuickShop
  `getAllShops(UUID)`, zAuctionHouse `getItemsListedForSale`/EXPIRED/PURCHASED qua reflection, Essentials homes đọc userdata + `delhome`,
  uJobs `ujobs setlevel <p> <job> 0` cho mọi job trong jobs.yml, `spawn`, `kit tanthu`, `newbie add`; sao lưu playerdata + userdata vào
  `choilai-backup/` 7 ngày), `/taikhoanphu`. Lệnh console của Essentials/PvPManager chưa thử thật. Tắt thu phục SiegeWar, khoảng cách
  nhà chính 10 ô đã áp dụng (cả hai trên trang Cài đặt; đường dẫn Towny: `claiming.distance_rules.min_distance_between_homeblocks`).
- **Đợt code 2 (14/10, chưa thử trong game): Liên minh Hoà Bình** `PeaceAlliance.java` (chi tiết: `docs/banghoi-luat-mua1.md` cuối mục 11).
  Dùng Towny events NationAdd/RemoveTownEvent, TownToggleNeutral/PVPEvent, TownBlockClaimCostCalculationEvent, TownUpkeepCalculationEvent,
  NewTownEvent; Essentials `UserBalanceUpdateEvent` (cause COMMAND_SELL) và QuickShop `ShopTaxEvent` qua `registerEvent` + reflection;
  `SiegeWarHook.activeSieges()/nextBattleSessionStart()`; kinh đô chiếm đất bằng `new TownBlock(wc)` + `TownyUniverse.addTownBlock`.
  **Việc của người dùng**: vào game đứng giữa spawn gõ `/dmm hoabinh setup`, rồi `/dmm hoabinh claim 14`. Cú pháp `ta town new <tên> npc`
  chưa thử thật. Dữ liệu: `plugins/DuckyMoMoCore/hoabinh.yml`. SiegeWar `allow_peaceful_capitals` đã đổi thành true (file trong data/).
- **Đợt code 3-4 + mục 14 (14/10, CHƯA THỬ TRONG GAME, người dùng muốn làm hết rồi test một lượt)**: `PeaceCouncil`, `Organizations`,
  `Leaderboard` (+ `BxhPlaceholders`, PlaceholderAPI 2.12.3 compileOnly từ repo.extendedclip.com), `Justice`, `DecorShop`, `Members` (hàm chung).
  Chi tiết + việc admin phải làm: `docs/banghoi-luat-mua1.md` cuối mục 11. Dữ liệu: `hoidong.yml`, `tochuc.yml`, `bxh.yml`, `an.yml`.
  Đã đổi cấu hình plugin khác (trong data/, không có trong repo): QuickShop `limits.use: true, default 10, duckymomo.giayphep.thuonghoi: 30`;
  Towny `keep_inventory/experience_on_death_in_arena: true`; Essentials `jail-online-time: true`; SiegeWar `allow_peaceful_capitals: true`,
  `subvert_enabled: false`. `SiegeWarHook` lấy từng hàm riêng (getBattleSession ở lớp `objects.BattleSession`).
- **Trang giới thiệu công khai (14/10)**: dịch vụ `site` (`site/`, compose include), `127.0.0.1:${SITE_PORT:-8892}`. Python stdlib + PyYAML,
  CHỈ ĐỌC `data/` và `modes/` (mount ro), API `/api/info|status|bantron|bxh` (cache 20–30 giây; ping Velocity `proxy:25577` bằng Server List Ping;
  không trả UUID/IP). Trang một tệp `site/public/index.html` (mount thẳng, sửa là thấy, trình duyệt giữ cache 5 phút); địa chỉ vào game, link
  Discord/Zalo/bản đồ: `site/config/site.yml`. Đã xem trên trình duyệt (máy tính + điện thoại 375px, không tràn ngang).
  **CÔNG KHAI từ 14/10: https://app.goc6.io.vn/mc/web/** (người dùng đồng ý, dặn tránh bị lần ngược về app). Route Trạm 6 `mc-web`
  (priority 80, không bắt đăng nhập, middleware `mc-web-slash|strip|headers`: bỏ header Server, `X-Robots-Tag: noindex`, `no-referrer`) →
  `host.docker.internal:8892`. Trang không có link nào về Trạm 6.
  Giao diện toàn khung (14/10 tối, theo yêu cầu): logo vịt người dùng gửi (`site/public/logo.png` 512px đã xoá nền, `favicon.png` 96px),
  linh vật vịt ở góc bản đồ có lời thoại đổi mỗi 6 giây, nền aurora, dải chữ chạy, số đếm nhảy, hiện dần khi cuộn; tắt hết khi máy bật
  "giảm chuyển động". Đã xem ở 1600px, ~800px, 375px (không tràn ngang).
- **Trạm 6 portal (14/10, theo yêu cầu)**: chưa đăng nhập chỉ thấy form đăng nhập; bỏ tab Đăng ký (API đăng ký vẫn khoá bằng
  `ALLOW_REGISTER=false`, thử trả 403); danh sách mục chuyển khỏi mã JS sang `apps/portal/rieng/modules.json`, phục vụ ở `/_rieng/`
  qua route `portal-rieng` có `require-login` (chưa đăng nhập: 401). Thêm/bớt mục: sửa file JSON đó rồi build lại portal.
  Dòng phụ đề "đầu tư, giải trí…" của trang chủ vẫn còn (chưa được yêu cầu bỏ). Các thay đổi trong repo tram6 CHƯA commit
  (repo đó lẫn thay đổi khác của người dùng — hỏi trước khi commit).
- **Cửa hàng Kinh Đô (14/10 tối)**: `DecorShop` 3 mục (vật liệu/trang trí/danh hiệu), NPC `/dmm shopnpc` cho mọi người, `/cuahang` cho rank ủng hộ
  chỉ trong kinh đô (đất thủ đô liên minh; chưa lập liên minh thì vòng `reserved-spawn-radius` quanh spawn). LP: `lp group namtuoc permission set
  duckymomo.cuahang true server=banghoi-1` (đã chạy; có trong `tools/donate-ranks.cmds`). Quyền lợi Nam Tước thêm "/cuahang khi ở kinh đô" (trang
  Cài đặt `tier0_perks` + config lobby/banghoi/practice). Dân làng đổi đồ: chờ người dùng tham khảo, xem `docs/banghoi-luat-mua1.md` cuối mục 11.
  Trang web: bỏ câu hứa "sẽ chuyển máy chủ Việt Nam" (người dùng yêu cầu). Trang vote: để tới khi lên VPS (cần cổng Votifier 8192).
- **Gửi tin vào game từ trang Hoạt động (14/10 tối)**: panel `/hoatdong` có thanh gửi (Cả mạng / Bang Hội Chiến / Sảnh / Practice; Chat admin /
  Thông báo / Thông báo giữa màn hình = title), API `POST /api/say` (Basic auth panel, tối đa 200 ký tự, bỏ ký tự điều khiển và §) gửi `tellraw`/`title`
  qua RCON. Tin gửi ghi vào `data/panel/admin.jsonl` (activity.jsonl thuộc service activity chạy root, panel uid 1000 không ghi được) và gộp vào dòng
  hoạt động với loại `admin` (nút lọc "Admin"). Đã thử gửi thật vào banghoi-1 lúc 0 người online.
- **Boss & Dungeon (14/10, người dùng: "cứ bổ sung plugin, test sau")**: ý tưởng (1) **Dungeon** cày đồ, cày tiền; (2) **"Trái Đất lâm nguy"**:
  boss sự kiện, các bang hội chung tay, nhiều bối cảnh. Đã cài **EliteMobs 10.9.8** (Modrinth `elitemobs`, GPL, có bản 26.2/26.3; MythicMobs có bản
  26.2 nhưng không có dungeon miễn phí). Đã chỉnh trong `data/banghoi-1/plugins/EliteMobs/`: `MobCombatSettings.yml doNaturalEliteMobSpawning: false`
  (không có elite tự sinh, chỉ dungeon/sự kiện/boss), `EconomySettings.yml useVault: true` + `currencyShowerTierMultiplier: 0.3` (xu chung, tác giả
  cảnh báo lạm phát). Trang Cài đặt > Thế giới > Boss & Dungeon. **Việc của người dùng**: tạo tài khoản Nightbreak lấy mã, vào game `/em setup`
  tải dungeon (khoảng nửa miễn phí). CHƯA làm: thiết kế boss "Trái Đất lâm nguy" (boss tuỳ chỉnh EliteMobs `custombosses/` + sự kiện
  `customevents/`), bản tiếng Việt (plugin chưa có), FreeMinecraftModels (tuỳ chọn). Không cài ResourcePackManager (đụng gói font).
- **Vote server (15/10)**: AzuVotifier 3.3.6 bản Paper ở banghoi-1 (link Modrinth trong `PLUGINS` của `modes/banghoi/compose.yaml`; KHÔNG cài
  ở proxy vì proxy là Velocity 4.2.0, AzuVotifier build cho Velocity 3). Đường vote: trang vote → VPS `42.96.4.244:8192` (BKNS không chặn;
  DNAT trong `relay-up.sh` + `ufw route allow` 8192) → wg → relay `socat TCP-LISTEN:8192 → banghoi-1:8192` (ip rule `tcp sport 8192` → bảng 66).
  Thưởng: DuckyMoMoCore `VoteRewards` (sự kiện `com.vexsoftware.votifier.model.VotifierEvent` qua reflection; config `vote` ở banghoi-1:
  100 xu/lượt, 7 ngày liền +500, mỗi 30 lượt cả server vote party 200 xu/người online; lưu `vote.yml`; `/vote`, `/vote top`; người chưa từng vào
  bị bỏ qua; tên Bedrock thử thêm dấu chấm đầu). Trang web: mục `#vote` + `/api/vote` (link từ `vote.sites` có `https://`, top tháng).
  **Đã áp dụng 15/10 03:39** (lúc 0 người online). Bẫy: `docker compose restart` KHÔNG nạp env mới (PLUGINS) → phải `docker compose up -d
  --no-deps banghoi-1`. Đã gửi thử vote v2 thật qua `42.96.4.244:8192` (token đọc từ `plugins/Votifier/config.yml`, KHÔNG in ra): Votifier
  `status ok`, Linh +100, web top tháng hiện. Lượt thử ghi `ThuNghiem` trong vote.yml (party.count 1). Khoá công khai v1:
  `plugins/Votifier/rsa/public.key` (đã gửi người dùng). CHỜ NGƯỜI DÙNG: đăng ký server trên các trang vote (tài khoản của họ) rồi gửi link →
  điền `vote.sites` (repo + data) + `/dmm reload`. Tiền hiện "$100" vì Essentials `currency-symbol: '$'` (cả server, có từ trước) trong khi web/cẩm
  nang gọi là "xu" — đã hỏi người dùng có đổi không.
- **Logo trong danh sách server (15/10)**: `proxy/config/server-icon.png` (64×64, cắt từ `site/public/logo.png`, nền trong suốt); itzg chép sang
  `data/proxy/` lúc khởi động. Velocity chỉ đọc icon khi khởi động (proxy không có console/rcon, không có LuckPerms để dùng `/velocity reload`)
  → đổi icon phải khởi động lại proxy lúc 0 người online, rồi `docker compose --profile tunnel up -d --no-deps --force-recreate playit relay`.
- **Tự sửa sau khi Windows/WSL khởi động lại (15/10)**: `ops/tu-sua-sau-khoi-dong.sh` (bash trong WSL; `--kiem-tra` = chỉ báo; nhật ký
  `data/ops/tu-sua.log`; khoá `data/ops/tu-sua.pid`). Tự dò MỌI container đang chạy (DuckyMoMo, tram6, be_goc6, ql_goc6, fe_goc6): (1) cổng tcp publish
  thử từ Windows bằng `curl.exe telnet://` + `time_connect` > 0 (telnet:// qua WSL không tự đóng nên mã lỗi luôn 28, phải dựa time_connect);
  cổng chết → `docker restart` container đó. (2) bind mount: tạo file đánh dấu ở thư mục nguồn, `docker exec test -e` trong container, không thấy →
  `docker compose -p <proj> -f <config_files> up -d --no-deps --force-recreate <svc>` trong working_dir (nhãn compose; container tram6 tạo từ
  Windows có nhãn `\\wsl.localhost\...` → hàm `unc2wsl`). (3) proxy bị đụng hoặc playit không chạy → tạo lại playit. Server MC (hoặc proxy khi
  cả mạng) có người online thì BỎ QUA. Đã thử 15/10: 41 thư mục gắn, mọi cổng sống, compose `--dry-run` đúng cho cả 4 dự án; nhánh sửa thật CHƯA
  gặp lỗi thật để thử. **Đừng dùng `docker info`** trong script: Docker Desktop sinh 2 `dbus-daemon` mỗi lần gọi, chúng giữ phiên WSL làm
  `wsl.exe` không thoát (và giữ khoá flock). Đăng ký chạy khi đăng nhập: `ops/windows/dang-ky-tu-sua.ps1` (tác vụ "DuckyMoMo tu sua", chờ 2 phút,
  `conhost --headless wsl.exe ...`; file có BOM vì PowerShell 5.1) — **người dùng tự chạy** (cài đặt hệ thống), cùng với tự đăng nhập Windows
  và giờ hoạt động Windows Update.
- **VPS làm cửa ngõ (15/10, người dùng chọn: server chính vẫn ở nhà, VPS chỉ chuyển tiếp IP)**: đo từ nhà (Viettel, Bắc Ninh; mạng trong nhà 1–2 ms):
  BKNS Hà Nội ~7–12 ms, Viettel IDC HCM 26 ms, FPT HCM (VPSSIEUTOC) ~45–50 ms. Đề xuất **BKNS VPS – Cheap 1** (70k/tháng, tối thiểu 1 tháng, 1C/1G/20G,
  IPv4 VN, chống DDoS cơ bản); cần hỏi BKNS: cho WireGuard/chuyển tiếp game? mở UDP? Đã tạo khoá SSH trên WSL `~/.ssh/duckymomo_vps` (ed25519, không
  passphrase; chỉ đưa khoá công khai cho người dùng).
  **ĐÃ CHUYỂN SANG VINAHOST (15/10 11:15)** — xem mục "VPS Vinahost" ngay dưới; đoạn BKNS sau đây là lịch sử.
  **ĐÃ DỰNG (15/10 03:05)**: VPS BKNS "VPS MMO1" (gói Cheap, 3 tháng, hạn 15/01/2027) **`42.96.4.244`**, SSH **cổng 26266**, `root`, vào bằng khoá
  `~/.ssh/duckymomo_vps` (người dùng đã đổi mật khẩu root; SSH bằng mật khẩu ĐÃ TẮT 15/10: `/etc/ssh/sshd_config.d/00-duckymomo.conf`, root chỉ vào bằng khoá; console web BKNS vẫn dùng mật khẩu). Ubuntu 24.04, KVM,
  ping từ nhà ~9,7 ms. **BKNS chặn chiều vào TCP 25565/25566/25577 và UDP 19132/19133/25565** (mạng nhà không chặn; các cổng khác thông) →
  dùng **Java TCP 25570, Bedrock UDP 19130** (DNAT cả 25565/19132 sẵn, BKNS mở là chạy). Chưa hỏi BKNS gói này có cho chạy game không.
  Cách chạy: VPS `wg-quick@wg0` (10.66.0.1, UDP 51820, MTU 1380, `relay-up.sh` DNAT về 10.66.0.2, không SNAT; mẫu ở `relay/vps/`). Nhà: service
  **`relay`** (`relay/compose.yaml`, profile tunnel, `network_mode: service:proxy`, NET_ADMIN, image tự build alpine + iproute2 + wg) dựng wg0
  10.66.0.2 trong mạng proxy; chiều về dùng **luật theo cổng nguồn** (`ip rule ipproto tcp sport 25577` / `udp sport 19132` → bảng 66 default dev
  wg0, trừ đích 127/8, 10/8, 172.16/12, 192.168/16, 100.64/10) — không dùng connmark vì UDP của Geyser (socket 0.0.0.0) chọn IP nguồn theo
  route ban đầu. Khoá nhà `RELAY_WG_PRIVATE_KEY` trong `.env`; khoá công khai VPS + endpoint là mặc định trong compose. Proxy `rp_filter=0` (cần).
  Đã thử từ nhà qua VPS: Java 25570 OK ~40 ms, Bedrock 19130 OK ~19 ms (đi vòng nhà→VPS→nhà), Velocity ghi **IP thật** `116.98.36.229`.
  Proxy không phải khởi động lại; tạo lại proxy thì tạo lại cả `playit relay` (script tự sửa đã làm). **DNS đã tạo 15/10** qua API (token
  `CF_API_TOKEN` trong `be_goc6/.env`, quyền tunnel + DNS): A `mc.goc6.io.vn` → 42.96.4.244 (DNS only, TTL 300) + SRV `_minecraft._tcp.mc` →
  0 5 25570 mc.goc6.io.vn; đã phân giải đúng qua 1.1.1.1/8.8.8.8, ping Java/Bedrock bằng tên miền OK. Người chơi: Java `mc.goc6.io.vn`,
  Bedrock `mc.goc6.io.vn` cổng 19130. **Xong 15/10**: địa chỉ mới trên web (`site/config/site.yml`
  có `du-phong` = playit, trang tự hiện dòng dự phòng) + cẩm nang; tường lửa VPS ufw (xem `relay/vps/README.md`); giới hạn đăng ký
  **2 tài khoản/IP/24 giờ** bằng DuckyMoMoCore `RegisterLimit` ở lobby (sự kiện AuthMe `AuthMeAsyncPreRegisterEvent.setCanRegister(false)` qua
  reflection, lưu `plugins/DuckyMoMoCore/dangky-ip.yml`, bỏ qua IP loopback/nội bộ; config `register-limit` trong
  `lobby/plugins/DuckyMoMoCore/config.yml`). KHÔNG dùng `maxRegPerIp` của AuthMe (tính tổng vĩnh viễn → khoá cả quán net/4G chung IP; AuthMe tự
  bỏ qua IP loopback). Sonar `max-online-per-ip` vẫn 20 (playit chung IP 127.x; chưa rõ Sonar có bỏ qua loopback). CHƯA: theo dõi băng thông
  VPS, đánh dấu nhiều tài khoản cùng IP cho staff. playit giữ làm dự phòng.
- **VPS Vinahost (15/10 11:15, thay BKNS)**: BKNS cấm chạy game (người dùng hỏi); VPSTTT, Cloudfly từ chối; P.A, DPTCloud (TP.HCM, ~31 ms) chưa/không
  phù hợp; **Vinahost đồng ý bằng văn bản** trong chat (nhân viên Đinh Thịnh: "được anh nhé", người dùng giữ ảnh chụp). Gói **CheapSSD 1**
  (1 vCPU, 1 GB, 25 GB, 100 Mbps trong nước, 1–10 Mbps quốc tế), 3 tháng 280.500đ gồm VAT (85k/tháng; 61k chỉ khi trả 2–3 năm), hạn 15/01/2027,
  DC **Viettel TP.HCM** (Cheap chỉ có TP.HCM; VPS NVMe ~161k mới có Hà Nội). Người dùng nói đa số người chơi ở miền Nam → chọn TP.HCM.
  Host `60880.vpsvinahost.vn`, **`125.212.218.74`**, SSH **cổng 22** (Vinahost dặn giữ SSH mặc định, không đổi cấu hình mạng), khoá
  `~/.ssh/duckymomo_vps` (người dùng thêm), SSH mật khẩu ĐÃ TẮT (`00-duckymomo.conf`); mật khẩu root lộ trong ảnh chụp → đã dặn đổi.
  Lúc đầu Virtualizor báo **Suspended** (không ping/SSH được), sau ~30 phút tự hết. Cổng: **mọi cổng thông** (TCP 25565/25570/8192, UDP 19132/
  19130/51820) từ nhà và từ BKNS. Ping nhà → VPS 31,8 ms; VPS ↔ TP.HCM 2 ms. Cài giống BKNS (`relay/vps/`: wg-quick@wg0 + ufw, thêm
  `ufw allow 22/tcp`); khoá công khai VPS `235WvjBPc9ZmDyV3B1whgnH+wgXukWRUJi5m3mIL1S8=` (mặc định trong `relay/compose.yaml`). Đã tạo lại relay
  lúc 0 người online; thử qua IP mới: Java 25565/25570, Bedrock 19132/19130, Votifier 8192 đều OK, proxy ghi IP thật. DNS: A → 125.212.218.74,
  SRV → 25565 (qua API). Ping ước tính: người chơi miền Nam ~30–40 ms, miền Bắc (kể cả chủ server) ~55–65 ms. VPS BKNS: vẫn bật nhưng không còn
  nhà nối vào; người dùng sẽ xin huỷ/hoàn tiền (đừng tắt hộ khi chưa hỏi).
- **Kế hoạch tính năng (15/10, người dùng chốt thứ tự)**: (1) Thư viện đồ ✅ → (2) điểm danh ✅ (giữ chuỗi khi bỏ ngày, ngày nào điểm danh nhận thưởng
  ngày đó, mốc 7/14/30, cài đặt trên web) → (3) boss bar đếm ngược bảo vệ người mới ✅ → (4) nhiệm vụ hằng ngày ✅ → (5) chìa khoá theo giờ online +
  vòng quay hiện tỉ lệ (chỉ phát miễn phí) ✅ → (6) mảnh sự kiện (ĐỒ VẬT, trao đổi được, giới hạn nhận/ngày; nguồn: boss sự kiện theo sát thương,
  dungeon, mốc điểm danh, đủ nhiệm vụ ngày, vote/vòng quay tỉ lệ thấp; mỗi sự kiện loại mảnh riêng + hạn đổi) + cửa hàng đổi mảnh trên web ✅ →
  (7) tạo boss trên web (EliteMobs custombosses + em reload) ✅ → (8) cửa hàng trên web (Kinh Đô, đổi mảnh; Shopkeepers cần xem kỹ vì tự ghi đè) ✅. Cả 8 bước xong 15/10.
- **Thư viện đồ (15/10)**: DuckyMoMoCore `ItemLibrary` (mọi server; dùng ở banghoi-1): `plugins/DuckyMoMoCore/thuvien-do.yml`, mục `items.<mã>`:
  `nguon: web` (material, amount, name/lore mã &, enchants {khoá: cấp} cho phép vượt cấp + khoá plugin `enchantio:...`, unbreakable,
  hide-enchants, glint) hoặc `nguon: game` (`data` = base64 `ItemStack.serializeAsBytes()`, kèm `preview` cho web; web KHÔNG sửa).
  Tên/lore: mặc định trắng/xám, không nghiêng. Lệnh `/dmm luudo|laydo|thuvien [nap]`. Lúc bật ghi `thuvien-meta.json` (registry phù phép thật:
  54 gồm Enchantio; 1.536 vật liệu). Panel: trang `/thuvien` (`panel/thuvien.html`), API GET/POST `/api/thuvien` (save/delete/give; ghi YAML bằng
  ruamel rồi RCON `dmm thuvien nap`; give = `dmm laydo`). Đã thử API thật (lưu món vượt cấp + Enchantio, chặn trùng mã/vật liệu sai, xoá) và
  giao diện trên bản giả lập dữ liệu thật; CHƯA thử `/dmm luudo` và nút Đưa trong game (cần người online). Các module sau dùng `ItemLibrary.build(mã, số)`.
- **Điểm danh (15/10)**: DuckyMoMoCore `DailyCheckin` (`/diemdanh`, `/diemdanh xem`; config `daily-checkin` ở banghoi-1): đếm TỔNG số ngày đã
  điểm danh (`diemdanh.yml` players.<uuid>.count/last), ngày theo giờ VN; quà ngày `daily-rewards` + quà mốc `milestones` {ngày: [quà]} theo
  `cycle-days` (30: lần 37 nhận mốc 7); phải online `min-online-minutes` (5) trong phiên; nhắc khi vào (bấm được); báo cả server khi chạm mốc.
  **Quà dùng chung** `Rewards`: `xu <số>` | `do <mã thư viện> [số]` | `vat <VẬT_LIỆU> [số]` | `lenh <lệnh {player}>` | `manh <loại> <số>` (chờ
  module mảnh); túi đầy thì rơi dưới chân. Panel: kiểu ô mới `rewards` (danh sách quà, chọn loại; đồ thư viện chọn từ danh sách) và
  `milestones` (mốc ngày + quà), kiểm tra bằng `REWARD_RES` trong `panel/app.py`; nhóm mới "Thưởng & sự kiện". Đã thử: lưu thật qua panel
  (ghi mốc dạng map, `dmm reload`), chặn quà sai; giao diện trên bản xem thử. CHƯA thử /diemdanh trong game (cần người chơi).
- **Nhiệm vụ hằng ngày (15/10)**: DuckyMoMoCore `DailyQuests` (`/nhiemvu`, alias `/nv`; config `daily-quests` ở banghoi-1): mỗi người `per-day`
  (3) nhiệm vụ ngẫu nhiên từ `pool` (seed = uuid + ngày VN, cố định trong ngày); loại break/place/kill/fish/harvest, `targets` (tên, `*_ORE`,
  `OAK_*`, `*`, `@HOSTILE`), `amount`, `rewards`; xong hết `complete-all-rewards`. Nghe sự kiện MONITOR + ignoreCancelled nên khối người chơi
  đặt rồi phá (PlacedBlockGuard huỷ) không tính. Tiến độ `nhiemvu.yml`, ghi mỗi phút + khi thoát + trong `teardown()` (reload). Panel: kiểu ô
  `quests` (trình soạn nhiệm vụ, kiểm tra `QUEST_TYPES`/`QUEST_TARGET_RE`). Mặc định 7 nhiệm vụ 100–150 xu + 300 xu xong hết. `Rewards.describe()`
  cho người chơi đọc. Đã thử: lệnh nạp, lưu thật qua panel (sửa câu cá 10→12→10), chặn loại sai, giao diện. CHƯA thử tiến độ trong game.
  Kinh tế: ~650 xu/ngày từ nhiệm vụ + 100 điểm danh + vote; chỉnh nếu lạm phát.
- **Vòng quay (15/10)**: DuckyMoMoCore `LuckySpin` (`/vongquay`, alias vq/quay; config `lucky-spin` ở banghoi-1): mỗi `minutes-per-key` (60)
  phút CHƠI THẬT (`Activity.activeNow`: có thao tác trong 60 giây) +1 vé, tối đa `max-keys` (5; đầy thì ngừng tích). Vé là số trong
  `vongquay.yml` (không phải đồ: không bán, không trao đổi được). Menu 6 hàng: phần thưởng (icon từ quà/`icon`, lore "Tỉ lệ x%" = weight/tổng
  + mô tả quà), nút Quay ô 49; quà phát NGAY khi quay, hiệu ứng 16 bước chỉ để xem; `rare` báo cả server. Placeholder PAPI `dmmvq`:
  `%dmmvq_ve%`, `%dmmvq_con%`; dòng "🔑 Vé quay" thêm vào scoreboard TAB (repo + data). Admin: `/vongquay give <người> <số>`.
  Mặc định 10 phần thưởng (xu, sắt, bánh mì, ngọc lục bảo, táo vàng, kim cương; hiếm: 2000 xu, khối kim cương, đĩa nhạc), không đồ mạnh.
  Panel: kiểu ô `spin` (trình soạn: tên, trọng số, % tự tính, hiếm, quà). Lobby không có PAPI vẫn khởi động bình thường (đã thử).
  Đã tặng Linh 1 vé để thử. CHƯA thử quay trong game.
- **Mảnh sự kiện (15/10)**: DuckyMoMoCore `FragmentShop` (`/doimanh`, alias `/manh`; config `event-fragments` ở banghoi-1), gắn vào `Rewards` làm
  FragmentGiver cho quà `manh <loại> <số>`. Mảnh = đồ vật (material, name, lore, ánh lấp lánh) + PDC `duckymomocore:manh` = loại; đếm/trừ mảnh
  trong túi theo PDC. `daily-cap` (20) mảnh/người/ngày cho quà thường (`doimanh.yml` nhan.<uuid>); `/doimanh give <người> <loại> <số>` (admin,
  dùng cho boss/dungeon/sự kiện) không tính giới hạn. Loại `types.<mã>` có `until` (yyyy-MM-dd): quá hạn thì món đổi dùng loại đó ẩn khỏi
  cửa hàng. `shop`: [{label, cost {loại: số}, limit lần/người, rewards}]. Menu 6 hàng, lore giá đỏ/xanh theo số mảnh đang có. Mặc định loại
  `lamnguy` (Mảnh Lâm Nguy) + 3 món (5000 xu, 16 kim cương, đĩa nhạc). Panel: kiểu ô `fragtypes`, `fragshop`. Đã thử: lệnh, chặn giá/hạn sai,
  giao diện. CHƯA thử đổi trong game. Chưa có nguồn mảnh tự động (chờ boss "Trái Đất lâm nguy": EliteMobs onDeathCommands gọi /doimanh give).
- **Boss trên web (15/10)**: panel trang `/boss` (`panel/boss.html`), API GET/POST `/api/boss` (`boss_json`/`boss_action` trong `panel/app.py`).
  Boss web = `data/banghoi-1/plugins/EliteMobs/custombosses/dmm_<mã>.yml` (có dòng chú thích đầu). Boss có sẵn (file không có dmm_) sửa được từ 15/10: lưu chỉ gửi các mục đã đổi (`boss_clean` partial, ruamel giữ mọi mục khác: trails, onDamageMessages, kỹ năng có tham số…); lần sửa đầu chép bản gốc vào `data/panel/boss-goc/` (action `restore` trả lại, rồi xoá bản sao lưu); action `copy` chép nguyên file sang `dmm_<mã>.yml`. Boss có sẵn không xoá trên web. Một số con do kỹ năng/vật phẩm khác gọi ra (summonable_wolf, the_returned, necronomicon_*, zombie_parents_*, zombie_friends_friend, transport_cannon_seat): trang hiện cảnh báo + hỏi lại. CHƯA rõ EliteMobs có ghi đè file mặc định khi cập nhật plugin hay không.
  Trường: isEnabled, entityType (danh sách `BOSS_ENTITIES`), name (& hoặc `<g:#a:#b>`), level (dynamic/1–200), healthMultiplier (0,1–1000),
  damageMultiplier (0,1–100), powers (lọc theo file có trong `EliteMobs/powers`, 71 cái), 6 ô trang bị, spawnMessage, deathMessage,
  deathMessages, onDeathCommands (bỏ "/" đầu), announcementPriority, timeout (phút), dropsEliteMobsLoot. Lưu/xoá → RCON `em reload`.
  Gọi: `em spawn bossAt <file> <world> <x> <y> <z> [level]` (đọc từ jar; console được); "cạnh người chơi" lấy `data get entity <tên> Pos`
  + `Dimension` (phải ở overworld "world"), lệch x +4. Chia mảnh: DuckyMoMoCore `/doimanh chia <loại> <tổng> $players` (EliteMobs thay $players
  = danh sách tên) chia đều cho người online, không tính giới hạn ngày. Đã thử API: lưu (file đúng định dạng), chặn loại sai, gọi (lệnh chạy,
  EliteMobs KHÔNG in kết quả qua RCON → CHƯA xác nhận boss xuất hiện), xoá; giao diện trên bản xem thử. CHƯA thử đánh boss/chia mảnh trong game.
- **Cửa hàng trên web (15/10)**: Cửa hàng Kinh Đô (`DecorShop`) sửa trên Cài đặt > Shop: kiểu ô `shopitems` (vật liệu, đồ trang trí: mỗi món
  `material` HOẶC `library: <mã thư viện đồ>`, amount, price) và `shoptitles` (id, text, price); kiểm tra trong `panel/app.py`. DecorShop
  nhận `ItemLibrary` (dựng ở đầu `setup()`), bán đồ thư viện (icon/tên lấy từ món; món bị xoá khỏi thư viện thì bỏ khi nạp, lúc mua thì hoàn
  tiền). Đã thử: lưu thật (thêm BELL → 32 món → trả lại 31), chặn giá 0 / vật liệu sai, giao diện. Shopkeepers (dân làng NPC) KHÔNG làm trên
  web: plugin tự lưu `save.yml` khi đang chạy nên ghi từ web dễ bị đè; cây trade dân làng vẫn chờ người dùng chốt.
- **Thanh đếm ngược bảo vệ người mới**: đã có sẵn ở PvPManager (`Newbie Protection.Boss Bar`); thêm ô bật/chữ/màu vào Cài đặt > PvP & combat.
- **Ping nhảy khi chạy nhanh (15/10, đã sửa bằng BBR)**: người dùng thấy ping 900+ ms khi chạy (TPS vẫn 20). Đo: tuyến **VPS Vinahost →
  nhà mất ~2% gói** ở mọi tốc độ (UDP iperf 5M/20M: 2,2%/1,7%), không phải trần băng thông (4 luồng TCP = 23,6 Mbps). TCP cubic 1 luồng chỉ
  ~3–5 Mbps → chunk dồn khi chạy, gói keep-alive xếp sau gói gửi lại → ping nhảy. BBR 1 luồng: 92 Mbps (VPS gửi), **trên đúng đường người
  chơi (proxy → wg → VPS → nhà) cubic 2,7 → BBR 27,3 Mbps**. Docker Desktop bỏ qua `--sysctl net.ipv4.tcp_congestion_control` → gắn theo
  tuyến: `ip route ... table 66 congctl bbr` trong `relay/wg-relay.sh` (vòng lặp tự thử lại mỗi phút nếu chưa có module). Module `tcp_bbr`
  (kernel WSL `CONFIG_TCP_CONG_BBR=m`, WSL không có systemd) phải nạp khi WSL khởi động: **người dùng tự thêm** `[boot] command = modprobe
  tcp_bbr` vào `/etc/wsl.conf` (15/10 module đang nạp sẵn). Người chơi phải vào lại mới dùng BBR. Cũng thấy: mạng trong Docker Desktop tải
  xuống ~37 Mbps (Windows 103, WSL 132), lên VPS qua đường hầm ~57 Mbps — đông người thì là nút thắt; về lâu dài cân nhắc Docker Engine trong
  WSL thay Docker Desktop.
  **Nguyên nhân thật (15/10 chiều)**: đo thêm thì mất gói có cả từ BKNS (UDP 1,4%) và ping tới Cloudflare → lỗi ở **đường Viettel vào nhà**,
  không riêng Vinahost; trong nhà (2 router ZTE, kể cả lúc tải 94 Mbps) 0% mất gói. Người dùng nhắn Zalo "Viettel Telecom", Viettel
  **khởi động lại modem từ xa** (~13:14, IP nhà đổi 116.98.36.229 → 171.255.251.82; đường hầm + playit tự nối lại) → **0% mất gói** (ping 100
  gói tới Vinahost/BKNS/Cloudflare, UDP 16.000 gói), TCP cubic VPS → nhà 92 Mbps. BBR vẫn để bật làm dự phòng. Nếu ping lại nhảy: đo ping 100
  gói tới 1.1.1.1 trước; mất gói thì nhờ Viettel kiểm tra / khởi động lại modem. Vinahost ticket #244254 trả lời chung chung (đã gửi kết quả
  ping). Chưa làm: nhờ người chơi miền Nam đo.
- **Ý tưởng từ CherryMC (15/10, người dùng gửi ảnh; ĐỂ SAU, xử lý mạng trước)**: họ có điểm danh `/diemdanh` (chuỗi ngày, thưởng tiền +
  "mảnh sự kiện" đổi quà), nhiệm vụ `/quest`, `/menukit`, chìa khoá miễn phí theo giờ online (scoreboard đếm ngược) + vòng quay "VQ FREE" hiện tỉ
  lệ từng món, boss bar đếm ngược "Bảo vệ PvP" cho người mới + boss bar mẹo xoay vòng, lệnh bấm được trong chat, nhắc chống lừa tpa, /ah báo
  lên chat kèm thuế, rank người mới [SEED], nhãn giới tính, ghép đôi (LOVE), claim kiểu GriefPrevention, `/mhome` menu nhiều nhà, thanh máu/mana.
  Đề xuất cho DuckyMoMo (chưa làm): (1) điểm danh hằng ngày + mốc 7/14/30 ngày (DuckyMoMoCore, giống VoteRewards); (2) nhiệm vụ hằng ngày gắn
  nghề; (3) boss bar đếm ngược bảo vệ người mới; (4) chìa khoá miễn phí theo giờ online + vòng quay ghi rõ tỉ lệ, chỉ tài nguyên/trang trí, KHÔNG
  bán chìa; (5) mảnh sự kiện cho "Trái Đất lâm nguy". Không làm: nhãn giới tính (dễ bị quấy rối), chỉ số máu/mana (lệch PvP công thành).
- **IP thật người chơi (14/10): KHÔNG có** — qua playit mọi kết nối hiện `127.21.235.67`. Không giới hạn theo IP được (AuthMe, Sonar).
  Có IP thật khi chuyển sang VPS (**người dùng chốt: khi chạy thật thì đổi sang VPS**) hoặc bật Proxy Protocol trên playit + Velocity `haproxy-protocol`.
- Font tiếng Việt (resource pack Be Vietnam Pro, host trên Trạm 6 `/mc/pack/`), bảng TAB, warp "Miền xa" (Côn Đảo, Luang Namtha…).

### Mạng nhà và ping (13/10)
- Modem Viettel ONT `192.168.1.1` (IP public `116.98.x.x`, PPPoE, không CGNAT, IP đổi khi khởi động lại) → ZTE H196A **chính** `192.168.2.253` (MAC …aa:df) → ZTE **phụ** `192.168.2.2` (…ab:eb, mesh) → máy server `192.168.2.3` (MAC `CC:28:AA:89:62:00`, cắm dây vào ZTE phụ). Hai tầng NAT.
- Người dùng đã vào được trang quản trị cả ONT và ZTE chính (mật khẩu do người dùng giữ). ZTE phụ đã reset rồi tự ghép lại mesh.
- **Việc dở:** ZTE phụ đang nối cục chính bằng **Wi-Fi** (Topology hiện biểu tượng Wi-Fi sau khi người dùng bấm Wireless BackHaul) → rút điện ZTE phụ 10 giây, kiểm tra dây WAN, xem lại Topology. Người dùng hẹn làm buổi tối.
- **Chưa mở cổng modem** (người dùng chọn không, vì lộ IP nhà → DDoS cả nhà mất mạng). Nếu sau này mở: ONT chuyển 25565/tcp + 19132/udp → IP WAN của ZTE chính (192.168.1.x); ZTE chính (Internet › Security › Port Forwarding) → 192.168.2.3; tường lửa Windows mở 2 cổng.
- Ping trong game qua playit **150–300 ms** (đo tới edge playit 147.185.221.x ~63 ms qua NTT, phải đi 2 lượt ra/về). Server không lag (MSPT banghoi 1–2 ms). playit **không có máy chủ Việt Nam**; danh sách Region (Premium 3 USD/tháng) có Asia / Asia India / Asia Japan — "Asia" có lẽ là Singapore (chưa xác nhận). Người dùng: "tạm thế".
- Web `goc6.io.vn` đi qua Cloudflare (IP nhà đã giấu).

### Kế hoạch tiếp theo (người dùng đã đồng ý hướng, chưa làm)
1. **VPS Việt Nam làm lá chắn** (người dùng hỏi "thuê VPS rẻ nhất được không"): điều kiện — ở Việt Nam (tốt nhất cùng Viettel), IPv4 riêng, cho UDP, KVM, băng thông đủ (~100–200 MB/người/giờ); 1 vCPU, 512 MB–1 GB là đủ. Không cần Anti-DDoS lúc đầu (bị tấn công thì VPS bị khoá tạm, nhà vẫn an toàn). Dựng: WireGuard nhà↔VPS bằng Docker Compose, chuyển 25565/tcp + 19132/udp về proxy, giữ IP thật người chơi (Velocity `haproxy-protocol` hoặc định tuyến qua wg; kiểm tra AuthMe/Sonar thấy IP đúng), tên miền `mc.goc6.io.vn` (Cloudflare DNS only) trỏ VPS, giữ playit dự phòng. **Đã so giá (13/10), người dùng HOÃN thuê tới khi server làm xong; 14/10 chốt: khi chạy thật (mở cho người chơi) thì chuyển sang VPS:** VPSSIEUTOC GOLD CHEAP 1 50k/tháng (trả tháng, FPT HCM, IPv4 riêng, hoàn tiền 1 ngày, **cấm "dùng làm VPN"** → dùng frp thay WireGuard hoặc hỏi trước); ZoneCloud Starter 50k/tháng nhưng **bắt trả 6 tháng** (324k gồm VAT), quảng cáo cho VPN/game, chưa rõ có IPv4 riêng (IP tĩnh thêm 100k/tháng); Viettel IDC VPS PT 1 80k/tháng (Viettel↔Viettel có thể ping tốt nhất); VPSMMO loại (ghi "không nhận Server Game"; 44k chỉ khi trả 12 tháng). Khi thuê: thuê 1 tháng thử ping trước, dùng khoá SSH (không nhận mật khẩu qua chat).
2. Tên miền cho playit trong lúc chờ VPS (nếu người dùng muốn): Cloudflare SRV `_minecraft._tcp.mc` → `pgsql-youths.tun.ply.gg:28438`; Bedrock CNAME `bedrock` → `pgsql-mississauga.tun.ply.gg` (vẫn phải nhập cổng 21185).
3. Chờ người dùng: token bot Discord; nơi sao lưu đám mây; trang vote; chốt bộ đồ dân làng Shopkeepers; biển mua bán spawn lên trang Cài đặt (đã đề nghị); đặt NPC `/tpr` ở spawn + thử; chốt luật Bang Hội Chiến mùa 1 (bản đề xuất `docs/banghoi-luat-mua1.md`, 13/10: lịch 10 tuần có 2 tuần hoà bình, PvP đất hoang A/B/C, tài khoản phụ, xếp hạng, phần thưởng trang trí).
4. Repo Trạm 6 (`~/works/tram6`) có thay đổi route `mc-map`, `mc-pack`, thẻ hub CHƯA commit (repo đó lẫn thay đổi khác của người dùng — hỏi trước khi commit).
5. Việc riêng của người dùng (không thuộc repo, đã hướng dẫn): Garena trỏ lại `D:\Game\LOL\32787` rồi xoá bản trên F; bật lại "Power On By RTC" 7:00 trong BIOS ASUS (ErP tắt).


## 1. Người làm dự án và cách làm việc

- Chủ quán đồ ăn vặt (nem chua rán, bánh rán…), đồng thời là **dev**, đang tự làm app quản lý bán hàng (đơn hàng, sản phẩm, khách hàng, báo cáo, thuế, kết nối bên thứ 3, đa cửa hàng).
- Từng chơi Minecraft khoảng 10 năm trước, thích **Factions**, sinh tồn, xây dựng, cày tiến độ, nhập vai. Có **hơn 15 giờ mỗi tuần** cho dự án. Chưa tính đến làm content.
- **Trả lời bằng tiếng Việt**, thực tế, nói rõ chỗ nào chưa chắc hoặc cần kiểm tra lại. Không tự chốt thay người dùng những thứ chưa được chốt.
- Người dùng muốn **build từng phần một**, chưa cần tối ưu cho đông người. Giai đoạn đầu **chạy trên máy nhà**.
- **Bắt buộc dùng Docker Compose** (không dùng Pterodactyl).

## 2. Repo

- GitHub: `liinh97/DuckyMoMo`, nhánh **`claude/confident-thompson-q939lp`**. Repo trước đó trống, nên nhánh này hiện là nhánh mặc định. Người dùng có thể muốn đổi sang `main` (chưa quyết).
- Claude GitHub App đã được cấp quyền cho repo này.

## 3. Các quyết định đã chốt (tóm tắt)

1. Server **Java (Paper)**, cho **Bedrock (điện thoại)** vào qua **Geyser + Floodgate**.
2. **Network nhiều chế độ** (lobby rồi chọn chế độ). **Build từng chế độ một**, dựng khung network ngay từ đầu.
3. **Chế độ đầu tiên: Bang Hội Chiến**, tức Factions kiểu mới. Chiến tranh theo lịch, chạy theo mùa, combat kiểu 1.8, không cướp lúc người chơi offline. *Luật chơi chi tiết chưa thiết kế.*
4. **Pokémon (Cobblemon, Fabric)** sẽ thêm sau. Chỉ người chơi Java có modpack vào được. **Không bán gì liên quan Pokémon.**
5. **Kiếm tiền đúng luật Mojang**: chỉ đồ trang trí và tiện ích nhỏ, không pay-to-win, **không bán cape**.
6. Chạy máy nhà trước, cho bạn bè vào qua **playit.gg**. Thuê máy chủ khi mở công khai.
7. **Docker Compose**: mỗi thành phần và mỗi chế độ một file `compose.yaml`, file gốc gom lại bằng `include`.
8. **Cho người chơi không bản quyền vào** (chốt 07/10/2026, vì phần lớn người chơi Việt dùng bản không bản quyền). Proxy chạy `online-mode = false`. **AuthMe** bảo vệ tên bằng `/register`, `/login` ở lobby. Người có bản quyền gõ `/premium` để lần sau vào thẳng. **SkinsRestorer** lo skin.

## 4. Trạng thái hiện tại

### Đã có trong repo
| Thành phần | File chính |
|---|---|
| File gốc | `compose.yaml` (danh sách include), `.env.example`, `.gitignore`, `.gitattributes` |
| MariaDB + Redis | `infra/compose.yaml`, `infra/db-init/01-databases.sh`, `02-network-schema.sql`, `03-mode-databases.sql` |
| Proxy | `proxy/compose.yaml`, `proxy/config/velocity.toml`, `proxy/config/forwarding.secret` (mẫu chứa biến), `proxy/config/plugins/authmevelocity/config.yml`, `proxy/plugins/Geyser-Velocity/config.yml` |
| Lobby (Paper) | `lobby/compose.yaml`, `lobby/config/paper-global.yml`, `lobby/plugins/LuckPerms/config.yml`, `lobby/plugins/AuthMe/config.yml` |
| Bang Hội Chiến (Paper) | `modes/banghoi/` (compose, mode.yml, README, server/config, server/plugins) |
| Pokémon (Fabric, profile `pokemon`) | `modes/pokemon/` (compose, mode.yml, README, FabricProxy-Lite.toml) |
| Khuôn mẫu chế độ Paper | `modes/_template-paper/` (dùng placeholder `__MODE_ID__`) |
| Script | `ops/init.sh` (tạo `.env` với mật khẩu ngẫu nhiên), `ops/new-mode.sh <id>` |

### Đã kiểm chứng (chạy thật trong session trước)
- `docker compose config`: hợp lệ cả mặc định lẫn `--profile pokemon`.
- MariaDB tạo đúng database `network`, `luckperms`, `mode_banghoi`, `mode_pokemon`, các bảng `players`, `modes`, `servers` và dữ liệu khởi tạo. User có quyền tạo database `mode_%`.
- Redis bắt buộc mật khẩu, healthcheck chạy.
- Điền biến `CFG_*` vào `forwarding.secret`, `paper-global.yml`, LuckPerms `config.yml` (thử bằng `mc-image-helper` trong image).
- `ops/init.sh`, `ops/new-mode.sh` (kể cả kiểm tra id sai).

### Chạy lần đầu trên máy nhà (07/10/2026, WSL Ubuntu 22.04, Docker 29.5, Compose v5.1)
- `docker compose up -d`: cả 5 service `healthy`. Paper **26.2** (build 132), Velocity **4.2.0**, Geyser **2.11.3**, Floodgate 2.2.5, LuckPerms 5.5.71, WorldEdit 7.4.5, Chunky 1.5.3.
- Slug Modrinth `luckperms`, `worldedit`, `chunky` tải được. LuckPerms nối được MariaDB và Redis.
- Secret Velocity khớp giữa `.env`, `forwarding.secret` và `paper-global.yml` của lobby và banghoi-1.
- Ping được từ máy: Java `127.0.0.1:25565`, Bedrock `127.0.0.1:19132` (dùng `itzg/mc-monitor`).
- Velocity tự nâng `velocity.toml` từ `config-version` 2.7 lên 2.9, giữ đủ các key. `ping-passthrough = "DISABLED"` được đổi thành bảng `[ping-passthrough]`, mọi mục `false`.
- Geyser mặc định `auth-type: online`, **đã sửa** thành `floodgate` bằng file `proxy/plugins/Geyser-Velocity/config.yml`.
- RAM thực dùng: proxy ~0.7 GB, lobby ~2.2 GB, banghoi-1 ~3.8 GB.
- Lobby log `ERROR: No key layers in MapLike[{}]` vì `generator-settings={}` khi `LEVEL_TYPE=minecraft:flat`. Server vẫn chạy (Minecraft dùng preset phẳng mặc định), **chưa xác nhận bằng mắt** thế giới lobby trông thế nào.

### Chuyển sang offline mode + AuthMe (07/10/2026)
- Proxy: `online-mode = false`, `force-key-authentication = false`. Paper (lobby, banghoi-1, khuôn mẫu): `proxies.velocity.online-mode: false`.
- Plugin proxy (ghim bằng link Modrinth trong `PLUGINS`): **AuthMe-Velocity 6.0.1**, **SkinsRestorer 15.12.6** (15/10: thêm cùng link vào PLUGINS của lobby, banghoi, practice; thiếu ở server con thì menu /skins không mở). Lobby: `authmereloaded` qua `MODRINTH_PROJECTS` (đang là 6.0.1).
- Không cần plugin AuthMeVelocity riêng: từ bản 6, AuthMeReloaded có sẵn plugin Velocity.
- AuthMe chỉ cài ở **lobby**. Proxy chặn người chưa đăng nhập sang server khác (`serverSwitch.requiresAuth`), nên banghoi-1 không cần AuthMe.
- Tài khoản lưu ở bảng `network.authme` (MariaDB), mật khẩu băm BCRYPT, tin nhắn tiếng Việt (`vn`).
- Secret giữa proxy và lobby: `AUTHME_SECRET` trong `.env` (`ops/init.sh` tự thêm vào `.env` cũ nếu còn thiếu).
- `maxRegPerIp: 0` (không giới hạn), vì trong mạng nhà và qua playit.gg mọi người có thể chung một IP. **Xem lại khi mở công khai.**
- `allowedNicknameCharacters: '\.?[a-zA-Z0-9_]*'` để tên Bedrock có dấu chấm ở đầu vẫn vào được.
- Đã kiểm chứng bằng log: AuthMe lobby nối được MariaDB, tạo bảng `authme`, hook LuckPerms. Proxy nhận `authServers: lobby`, premium verification, secret khớp với lobby.
- Cảnh báo bỏ qua được: `protectInventory` cần PacketEvents (lobby là adventure, chưa cần). GeoIP tắt vì không có tài khoản MaxMind.
- **Bẫy**: mc-proxy chỉ điền `${CFG_...}` cho file trong `/config`, không điền cho `/plugins`. Cấu hình plugin proxy nào có secret thì đặt ở `proxy/config/plugins/<plugin>/`.

### Tunnel playit.gg (07/10/2026)
- **Sonar 2.1.52 (chống bot, proxy, 13/10)**: link Modrinth ghim trong `proxy/compose.yaml`, cấu hình `proxy/plugins/sonar/config.yml` (max-online-per-ip 20 vì qua playit có thể chung IP 127.x; verification DURING_ATTACK; Bedrock bỏ qua). Lệnh `/sonar` chỉ chạy ở console proxy (chưa LuckPerms ở proxy). Không chống được DDoS băng thông: giấu IP nhà (playit / Cloudflare cho web; chưa mở cổng modem). Ping thử Java qua playit TỪ TRONG NHÀ báo EOF (vòng ngược), người chơi thật vào bình thường (Linh vào được 13/10) — đừng dùng phép thử đó để kết luận tunnel hỏng.
- **Mạng nhà (13/10)**: modem Viettel ONT 192.168.1.1 (IP public, không CGNAT, PPPoE) → ZTE H196A chính 192.168.2.253 (MAC …aa:df) → máy server 192.168.2.3 (MAC CC:28:AA:89:62:00, cắm vào ZTE phụ 192.168.2.2 …ab:eb). Hai tầng NAT: mở cổng phải làm ở cả ONT và ZTE chính. Người dùng chọn CHƯA mở cổng (lộ IP nhà → DDoS). Mật khẩu thiết bị không ghi ở đây. Backhaul ZTE phụ đang là Wi-Fi (sau khi bấm Wireless BackHaul), cần đưa về dây.
- playit edge `147.185.221.x` (anycast), ping từ nhà ~63 ms qua NTT; playit không có máy chủ ở Việt Nam (chưa xác nhận chính thức).
- `tunnel/compose.yaml`: service `playit` (`ghcr.io/playit-cloud/playit-agent:1.0`, đang là 1.0.12), profile `tunnel`, key ở `PLAYIT_SECRET_KEY` trong `.env`.
- `network_mode: "service:proxy"`: dùng chung mạng với proxy, nên trên dashboard playit, tunnel trỏ tới `127.0.0.1:25577` (Java, TCP) và `127.0.0.1:19132` (Bedrock, UDP). `depends_on.restart: true` để proxy khởi động lại thì playit cũng khởi động lại.
- Bật: `docker compose --profile tunnel up -d playit`. Agent đã kết nối được (agent tên `duckymomo`).
- Docker Desktop + WSL: `docker pull` từ ghcr.io có thể lỗi `error getting credentials`. Image công khai thì tải bằng `DOCKER_CONFIG` trỏ tới thư mục có `config.json` rỗng (`{}`).
- Tunnel Java `duckymomo-java`: `pgsql-youths.tun.ply.gg` (SRV trỏ cổng 28438), máy chủ Singapore. **Đã vào game thật qua tunnel** (07/10, 23:11): Linh vào được lobby.
- Tunnel Bedrock `duckymomo-bedrock`: `pgsql-mississauga.tun.ply.gg` cổng **21185** (Bedrock không đọc SRV, phải nhập cả cổng). Ping Geyser qua tunnel có phản hồi (`mc-monitor status-bedrock`). Geyser để `transport: raknet`, đúng loại tunnel Bedrock (RakNet) của gói miễn phí.
- **Tự sửa playit kẹt (12/10/2026, sửa 17:51)**: service `playit-watchdog` (`tunnel/compose.yaml`, script `tunnel/playit-watchdog.sh`, profile tunnel): mỗi 30 giây đếm số `New TCP Client` của playit SAU dòng `/127.` cuối cùng của proxy (xét tối đa 2 giờ, bỏ 20 giây gần nhất cho proxy kịp ghi); ≥ 2 thì `docker restart` playit; nghỉ 2 phút giữa hai lần. Velocity mặc định KHÔNG ghi lượt ping danh sách server (chỉ ghi lúc vào game) → 18:23–18:27 ngày 12/10 watchdog khởi động lại nhầm 3 lần khi người dùng mở màn hình Multiplayer; đã bật `show-ping-requests = true` trong `proxy/config/velocity.toml` nên mọi kết nối qua playit (ping hay vào game) đều để lại dòng `/127.`. Ngưỡng 2 để an toàn. Quy tắc cũ (≥ 2 kết nối trong 180 giây) bỏ sót khi người chơi thử lại cách nhau lâu (17:34 và 17:50 ngày 12/10). Xem: `docker compose logs playit-watchdog`. Người dùng (chủ server) cố ý vào bằng domain playit như người chơi ngoài, để thấy lỗi tunnel giống họ: đừng khuyên dùng localhost.
- **Đừng kiểm tra tunnel bằng `mc-monitor status`**: nó luôn báo `EOF` qua playit. Thử bằng ping tự viết (Python, handshake + status request tới `147.185.221.214:28438`, host `pgsql-youths.tun.ply.gg`; 12/10 trả 799 byte) hoặc client thật, xem log proxy `[initial connection] ... is pinging` / `[connected player]`.
- Proxy thấy người chơi qua playit với IP dạng `127.x.x.x` (ví dụ `127.135.13.102`). Có vẻ playit gán mỗi người chơi một địa chỉ riêng, nhưng **chưa xác nhận** với hai người khác nhau. Chưa bật Proxy Protocol.
- Secret key đã lộ trong ảnh chụp ở session 07/10. **Nên tạo lại key** trên dashboard rồi sửa `.env`.

### Nhiều phiên bản client (07/10/2026)
- **ViaVersion 5.12.0 + ViaBackwards 5.12.0 + ViaRewind 4.2.0**, cài qua `MODRINTH_PROJECTS` trên **lobby, banghoi-1 và `_template-paper`**. Không cài ở proxy, vì Velocity tự nhận client từ 1.7 trở lên, và để server Pokémon (Fabric, cần đúng phiên bản) không bị ảnh hưởng.
- Server vẫn là 26.2. Về lý thuyết client 1.7 tới 26.x đều vào được. **Chưa thử** bằng client cũ thật.
- **Rủi ro chưa kiểm chứng**: hộp thoại đăng ký/đăng nhập của AuthMe (dialog) chỉ có trên client 1.21.6 trở lên. Client cũ có thể không thấy hộp thoại. Nếu vậy, thử gõ `/register`, `/login` trong chat, hoặc tắt `settings.registration.dialog.preJoin.enable`.
- **Bẫy**: dòng `#` **bên trong** khối `MODRINTH_PROJECTS: |` không phải comment YAML. Image itzg đọc nó thành tên plugin (`viabackwards: client…` thành project kèm phiên bản) và server khởi động lỗi liên tục. Comment phải đặt **trên** dòng `MODRINTH_PROJECTS:`.

### network-core: bắt đầu với `core-paper` (08/10/2026)
- `network-core/` (Gradle Kotlin DSL, Java 25). Module đầu tiên `core-paper` tạo plugin **DuckyMoMoCore**.
- Build: `./ops/build-core.sh`. Script chạy `gradle:9.6-jdk25` trong Docker (cache ở volume `duckymomo-gradle-cache`) rồi copy `DuckyMoMoCore.jar` vào `lobby/plugins/`. File `.jar` không commit, nên máy mới phải build trước khi `up`.
- Phụ thuộc: `paper-api:26.2.build.132-stable` và `fr.xephi:authme-core:6.0.1` (`compileOnly`, không lấy phụ thuộc kéo theo). Nâng Paper thì sửa phiên bản trong `core-paper/build.gradle.kts`.
- **Tính năng 1: Bedrock auto-login** (`BedrockAuthListener`). Người chơi Bedrock (UUID Floodgate có nửa đầu bằng 0 và tên bắt đầu bằng `.`) được tự đăng ký AuthMe bằng mật khẩu ngẫu nhiên, rồi `forceLoginFromProxy` ở pha configuration, trước hộp thoại pre-join của AuthMe. Dự phòng: `forceLogin` 1 giây sau khi vào. Lý do: Bedrock đã xác thực qua Xbox, và app điện thoại bị văng mỗi khi chuyển app nên gõ lại mật khẩu rất phiền.
- **Chưa kiểm chứng bằng người thật**: đã build và nạp được trên lobby, chưa thấy Bedrock vào lại mà không hiện hộp thoại.
- Kế hoạch network-core cũ (mục 6 phần "Việc tiếp theo") vẫn giữ: thêm `core-api`, `core-common`, `core-velocity` khi cần.

### Bang Hội Chiến: đã cài plugin (08/10/2026)
- `modes/banghoi/compose.yaml`. Modrinth: `towny` (0.103.2.0), `vaultunlocked` (hiện tên Vault 2.20.3), `coreprotect` (24.1), `worldguard` (7.0.19). `PLUGINS` (ghim link): SiegeWar 3.7.0 (GitHub), OldCombatMechanics 2.7.0 (GitHub), EssentialsX + Chat + Spawn bản dev #1832 `2.22.1-dev+27` (ci.ender.zone, vì bản chính thức 2.22.0 mới tới 26.1.2; dấu `+` trong link phải viết `%2B`).
- Đã chạy `/swa install`, `/ta reload all`. Công thành: bật ở `world`, tắt ở `world_nether`, `world_the_end` (`/townyworld <world> toggle warallowed on|off`).
- Towny đang lưu **flatfile** (chưa chuyển sang MariaDB `mode_banghoi`). CoreProtect đang dùng SQLite mặc định. Cân nhắc chuyển trước khi mở công khai.
- Towny CÓ bản vi-VN (19/10 đã bật, ép cho mọi người qua lang/override/global.yml; xem mục Việt hoá toàn server).
- OldCombatMechanics dùng "modeset": `old` (1.8), `new`. `worlds.__default__` quyết định ai được dùng kiểu nào; người chơi tự đổi bằng `/ocm mode` nếu danh sách có nhiều kiểu.

### Sao lưu tự động (08/10/2026)
- Mỗi server có thế giới có service sao lưu đi kèm **trong chính compose của nó**: `backup-lobby` (lobby/), `backup-banghoi-1` (modes/banghoi/), `backup-pokemon-1` (profile `pokemon`), `backup-__MODE_ID__-1` trong `_template-paper` (cùng profile với chế độ). Image `itzg/mc-backup`, tar.gz, `PAUSE_IF_NO_PLAYERS=true`, bỏ `*.jar,cache,logs`.
- Database: `backup-db` trong `infra/compose.yaml`, script `infra/backup-db.sh` (`mariadb-dump --all-databases --single-transaction`, gzip, xoá bản cũ hơn `BACKUP_KEEP_DAYS`). Mật khẩu root qua `MYSQL_PWD`.
- `.env`: `BACKUP_DIR` (bỏ trống = `backups/` trong repo, có trong `.gitignore`; đặt thì phải là đường dẫn **tuyệt đối**, vì đường dẫn tương đối tính theo từng file compose con), `BACKUP_INTERVAL` (6h), `BACKUP_KEEP_DAYS` (7).
- **RCON dùng chung**: `RCON_PASSWORD` trong `.env` (`ops/init.sh` tự sinh), đặt cho mọi server Paper/Fabric. itzg ghi mật khẩu này ra `data/<server>/.rcon-cli.env`, nên trang Cài đặt vẫn đọc được.
- Đã kiểm chứng: `backup now` tạo `.tar.gz` có `world/level.dat` và cấu hình plugin; dump database có đủ 5 database, có bảng `authme`. **Chưa diễn tập khôi phục thật** (lệnh khôi phục ở README mục "Sao lưu và khôi phục").
- Bản sao lưu vẫn nằm trên **cùng ổ C** với server. Nên đẩy thêm một bản ra ngoài (ổ F, cloud bằng rclone/restic) khi mở công khai.

### Đổi sang map Trái Đất 1:1000 (11/10/2026)
- Tạo bằng `XMX=30g MEM_LIMIT=33g ./tools/earthmap/build-earth.sh 40`: **3 giờ 32 phút** (tạo dữ liệu 80 phút, ghi file ~2 giờ), container lên tới **31 GB** (19g heap không đủ: lần đầu 18,5 GB sau 10 phút). Lúc tạo đã tạm tắt DuckyMoMo và phần Trạm 6 không liên quan quán (giữ goc6 + gateway/auth/portal/ShopeeFood/Grab/Zalo/SePay). Kết quả `tools/earthmap/work/out/earth_1-1000`: 17 GB, 3.528 region, 43.008 × 21.504 block. Chunk WorldPainter **không có heightmap** (server tính khi nạp), nên script warp chỉ chạy được sau lượt trồng cây.
- Lắp bằng `./ops/doi-map-banghoi.sh <map> <nhãn>` (người dùng tự chạy; Claude bị chặn quyền khi đổi tên thư mục data). Script không xoá gì: world cũ `data/banghoi-1/world-1-2000-20261011`, warp cũ `plugins/Essentials/warps-world-1-2000-20261011`, tiến độ trang trí/công trình cũ `*.world-1-2000-20261011`; dời vùng WorldGuard `spawn` theo `spawn_x/spawn_z`.
- Thông số mới: viền 21504 × 10752, spawn Hà Nội (12646, 63, -2512), vùng spawn x 12582..12710, z -2576..-2448; 1 độ ≈ 119,47 block (X = kinh độ × 119,47; Z = −vĩ độ × 119,47). `make-warps.py` tự lấy tỉ lệ từ `border_x` trong `settings.yml`.
- Trồng cây chạy lại từ 19:03 11/10: 3.612.672 chunk, ~138 chunk/s, dự kiến ~7 giờ, TPS 20. banghoi-1 dùng ~7,5 GB khi trang trí → `mem_limit` 8g → **10g**. Còn lại sau khi xong: `/dmm structures start` (đặt công trình), rồi tạo lại warp + biển tên (warp cũ đã gỡ khỏi repo).

### AuthMe: tắt hộp thoại trước khi vào (12/10/2026)
- Người dùng muốn vào game rồi gõ `/login` trong chat, không dùng hộp thoại. `lobby/plugins/AuthMe/config.yml`: `settings.registration.dialog.preJoin.enable: false` và `postJoin.enable: false`; `settings.perPlayerLocale: false` (đang bật thì client tiếng Anh nhận thông báo tiếng Anh). Đã sửa cả file đang chạy + `/authme reload`.

### Khu spawn Hà Nội + việc tự động sau khi trồng cây (12/10/2026)
- Nội thành Hà Nội trong dữ liệu map (mảng đá cuội cạnh sông Hồng) ở **(12699, -2523)**, lệch ~55 block đông so với toạ độ tính từ kinh/vĩ độ. Spawn dời về đó: `spawn_x/z` 12699/-2507, vùng WorldGuard `spawn` x 12635..12763, z -2587..-2459 (128 x 128 quanh tâm quảng trường).
- `tools/build-spawn.py <x> <y> <z>` sinh lệnh xây (chạy: `python3 tools/build-spawn.py 12699 62 -2523 > /tmp/s.cmds; docker exec -i duckymomo-banghoi-1-1 rcon-cli < /tmp/s.cmds`): quảng trường 81x81, Hồ Gươm + Tháp Rùa, đền Ngọc Sơn + cầu Thê Húc, cổng kiểu Khuê Văn Các (nam), chợ 8 sạp biển `[Buy]/[Sell]` (tây, giá tạm, sửa `SHOPS`), tường 36 biển `[Warp]` (đông). Đã xây 12/10 ~00:50, 1.315 lệnh không lỗi. **Chưa ai vào game kiểm tra biển [Buy]/[Sell]/[Warp] đặt bằng setblock có được Essentials nhận không.**
- Essentials `enabledSigns`: bật balance, buy, sell, warp, disposal (sửa thẳng `data/banghoi-1/plugins/Essentials/config.yml`, chưa có trong Git). Quyền `essentials.signs.use.{buy,sell,warp,balance,disposal}` cho nhóm default, `server=banghoi-1`.
- `tools/render-top.py <region> <x> <z> <bán kính> <png> [phóng]`: vẽ ảnh nhìn từ trên xuống từ region file (Pillow). Đường tím/xám mảnh trên map là **biên giới quốc gia** (WorldPainter vẽ bằng `iron_bars`).
- Vùng loại trừ trang trí cũ (tâm 12646,-2512, bán kính 300 = 300 km ở 1:1000) làm cả miền Bắc không có cây. Đã đổi thành tâm quảng trường, bán kính 70 (repo + data) và thêm `/dmm decorate area x1 z1 x2 z2` (trang trí bù một vùng, không đụng tiến độ lượt chính); jar mới đã build, **nạp khi banghoi-1 khởi động lại**.
- **Đã làm xong 12/10 04:39–04:53**: trồng cây chính XONG (1.636.395 cây, 3.036.414 cỏ/hoa); `/dmm decorate area 12346 -2812 12946 -2212` bù vùng Hà Nội (1.482 chunk, +2.043 cây, 9 giây); `/dmm structures start`: 4.696 điểm lưới, **đặt 1.103** (503 hầm mỏ, 131 tàu đắm, 176 di tích biển, 64 phòng thử thách, 23 stronghold, 27 làng, 5 tiền đồn, 1 dinh thự…), lỗi 51 (mép map + kim tự tháp/đền biển/đền rừng bị từ chối); 136 warp (`make-warps.py` tìm đất liền trong 50 block; `hanoi` chỉnh về quảng trường spawn) + 134 biển tên; restic snapshot `a4ce7963` (world 19 GB, kho banghoi-1 37,8 GB vì đã đổi gần như mọi chunk).
- **Phiên bản ghim 26.2** (12/10): `MC_VERSION=LATEST` làm lobby tự nâng lên 26.3 khi tạo lại container (WorldEdit 7.4.5 sập). Đã ghim `MC_VERSION=26.2` trong `.env`, `.env.example`, mặc định trong compose; tạo lại banghoi-1 + lobby. Nâng phiên bản sau này phải kiểm tra plugin trước.
- **Gamerule 26.x dùng tên mới** (snake_case): `random_tick_speed`, `advance_time`, `advance_weather`…
- **WorldEdit 7.4 trên server này**: `//schem load` phải ghi cả đuôi (`ten.schem`); file chép vào khi server đang chạy không hiện trong danh sách tới khi khởi động lại (bind mount Docker Desktop không báo thay đổi file). Dán khung quá lớn (744x289x588) hết RAM dù heap 6G.
- **Shop/warp ở spawn GlassCup (12/10)**: 24 dãy x 5 biển gỗ sồi quanh vòng quảng trường (y 45) ghi bằng `data merge block … is_waxed:1b`: tây 8 dãy `[Buy]` (lương thực, vật liệu, công cụ, nông nghiệp, chiến đấu, giáp sắt, trang trí, tiện ích), đông 8 dãy `[Warp]` (3 miền VN, ĐNÁ, Đông Á, Nam Á & Trung Đông, châu Âu, Mỹ/Phi/Úc; 32 warp), nam 4 dãy `[Sell]`, bắc 4 dãy thông tin (hướng dẫn, kinh tế, luật, ghi công). Biển đầu mỗi dãy là tên quầy. 74 biển gỗ vân sam (lan can lối bắc) để nguyên. Giá tạm.
- **Rừng lượt 2 (12/10)**: cây lượt 1 thưa (lá phủ 17–24% ở miền Bắc VN, đa số `sparse_jungle` 3 cây/chunk, rải đều). MapDecorator: mật độ mới (jungle 30, sparse_jungle 14, bamboo_jungle 16 + bụi tre thật, forest/taiga 14, dark_forest 20…), `patch-noise` (nhiễu giá trị 2 tầng ô 8 và 3 chunk → nhân 0,25–3,0: mảng rừng rậm + trảng trống), `plants: false` (lượt 2 chỉ thêm cây). Thử 256 chunk tây bắc Hà Nội: +2.874 cây, tán phủ dày. Vùng loại trừ đổi thành tâm (12699,-2465) bán kính 190 (phủ công trình GlassCup). Chạy cả map: bắt đầu từ z -3008 (sửa `decorate-progress.yml` khi server **đã tắt**, vì plugin ghi đè file lúc tắt) xuống nam (~7,5 giờ), xong tự chạy `/dmm decorate area -21504 -10752 21503 -3009` cho nửa bắc (~4 giờ; area không tiếp tục sau khi server khởi động lại). Tiến độ lượt 1 cất ở `decorate-progress.yml.luot1-xong`. Vùng thử đầu (12230..12470, -2895..-2655) chạy với mật độ cũ hơn nên sẽ được trồng thêm lần nữa ở nửa bắc.
- **Role (12/10, LuckPerms, chung toàn network, lưu DB)**: `default` `[Dân]` xám; **Rank donate là track riêng `donate`, KHÔNG phải role** (chốt 12/10; nhóm `vip` đã xoá, thay bằng 5 tước vị, xem mục "Rank donate"); prefix chat ghép role + rank donate (`meta-formatting` trong `LuckPerms/config.yml` của lobby/banghoi/practice/template: `highest_not_on_track_donate` rồi `highest_on_track_donate`) → `[Admin] [Công Tước] Linh`; cấp: `/rank give <tên> <bậc> <tháng>`; `builder` `[Builder]` (chưa cấp quyền xây: chỉ nên có trên server xây riêng); `helper` `[Helper]` (mute, kick, seen, socialspy, coreprotect inspect/lookup); `mod` `[Mod]` kế thừa helper (ban, tempban, unban, tp, tphere, vanish, invsee, enderchest.others, fly, coreprotect rollback/restore, worldguard.region.info.*); `admin` `*` kế thừa mod. Track `staff`: helper → mod → admin (`lp user <tên> promote staff`). Chat banghoi: Essentials `format: '{PREFIX}{DISPLAYNAME}&7: &f{MESSAGE}'` (sửa thẳng data, chưa có trong Git). Lobby: DuckyMoMoCore `ChatFormat` (config `chat-format.enabled`, LuckPerms API `net.luckperms:api:5.4`) hiện cùng dạng `[Role] [Rank] Tên: …`; practice dùng EssentialsX Chat cùng format với banghoi.
- **Trade (12/10, banghoi-1)**: Modrinth `quickshop-hikari` (cửa hàng rương), `zauctionhouse` (/ah, cần `zmenu` + `placeholderapi`), `tradepro` (/trade, mặc định không cần quyền). Quyền cấp cho `default` (server=banghoi-1): quickshop.create.{sell,buy,changeprice,changeamount}, zauctionhouse.{use,sell,selling,expired,purchased,history,search,claim,economy.vault,max.5}. **Chưa ai thử trong game** (tên quyền zAuctionHouse đoán theo config, có thể thiếu). Bảng KINH TẾ ở spawn + MOTD đã ghi /ah, /trade, cửa hàng rương.
- **Chế độ Practice (12/10)**: `modes/practice` (tạo bằng `ops/new-mode.sh`, bỏ profile: chạy mặc định), server `practice-1` (khai trong `velocity.toml`), DB `mode_practice`, `mode.yml` status beta (menu lobby chỉ hiện cho quyền `network.beta`). Paper 26.2, world hư không, adventure, không quái, RAM tối đa 3G. Plugin: `duels-optimised` 8.2 (GPL-3.0; tắt `check-for-updates`), OldCombatMechanics (khoá `__default__: [ "old" ]`), EssentialsX + Chat + Spawn (bản dev như banghoi), LuckPerms, Vault, Via*. `tools/build-practice.py build` (sảnh 41x41 ở 0,100,0 + 6 đấu trường 33x33 + bục Sumo có hồ nước bên dưới) và `… duels <thư mục Duels>` (ghi kits.json/arenas.json/queues.json; TẮT server trước). 7 kit (NoDebuff, Gapple, Classic, Archer, Boxing, Combo, Sumo), 7 hàng chờ xếp hạng 1v1, đấu trường `DauTruong1..6` (tên chỉ chữ+số) + `Sumo` (gắn riêng kit Sumo). Kit dùng JSON cũ của Duels: thuốc ghi `HEALING-strong` (không nhận `STRONG_HEALING`), phù phép ghi tên hiện đại (SHARPNESS, PROTECTION…; tên cũ DAMAGE_ALL có thể bị bỏ qua im lặng). Quyền default (server=practice-1): duels.default, queue, party, spectate + essentials cơ bản. **Chưa ai vào thử**: chưa xác nhận phù phép/thuốc trong kit, ghép trận, Sumo.
- **Dân làng đổi đồ (ý tưởng, CHƯA làm)**: người dùng muốn dân làng NPC ở spawn với bộ trao đổi cố định, nhưng **chưa chốt các bộ** → đừng đặt. Bản nháp `tools/villagers.py` (8 dân làng quanh quảng trường tròn nam, tiền tệ ngọc lục bảo, giá cố định, lượt vô hạn). Lưu ý khi làm: lệnh summon quá dài cho RCON ("command too long") → phải chia nhỏ (summon rồi `data modify … Offers.Recipes append`); vùng WorldGuard `spawn` chặn summon (`mob-spawning deny`), script tạm mở rồi đóng; `kill` bị Essentials ghi đè, dùng `minecraft:kill`.
- **Spawn protection vanilla** (`spawn-protection=16` trong server.properties banghoi-1) chồng lên vùng WorldGuard: trong 16 block quanh điểm spawn chỉ OP tương tác được. Cân nhắc đặt 0 (WorldGuard đã bảo vệ).
- **Map trang trí tay thiếu sinh thái (12/10)**: lấy mẫu 675 chunk vùng mùa 1: cây gần như chỉ lá jungle + sồi, cỏ phủ 0,4% mặt đất, hoa vài chục, không mía/bí/dưa/nấm/san hô, **0 động vật** (Minecraft chỉ rải động vật lúc tạo chunk; map WorldPainter ghi chunk `full` nên bỏ qua). Người dùng chọn **cách B: map Trái Đất + để Minecraft tự trang trí**.
- **Cách B đã thử được (12/10)**: `POPULATE=1 ./tools/earthmap/build-earth.sh <scale>` gọi `setPopulate(true)` cho mọi dimension → WorldPainter ghi chunk `Status: liquid_carvers`; Paper 26.2 nạp vẫn nhận (có bước "upgrade world" lúc khởi động), khi chunk được nạp thì chạy features + light + spawn → `full`. Thử 1:4000 (13 phút, 1 GB, ra `out/earth_1-4000-populate`), 121 chunk quanh Hà Nội: cỏ ngắn ~13% mặt đất, dương xỉ, tre, dưa hấu, tulip, rạn san hô, động vật (vẹt, lợn, cừu, sói). Không có công trình vanilla (structure_starts đã qua) → vẫn dùng `/dmm structures`. Chunk chỉ được trang trí khi nạp → cần chạy Chunky trong viền mùa trước khi mở.
- **XONG (12/10 15:25): banghoi-1 chạy map 1:1000 cách B, mùa 1 Đông Nam Á.** Map `out/earth_1-1000-populate` (213 phút, 18 GB). World cũ giữ: `world-1000-trangtritay-20261012` (trang trí tay), `world-1-2000-20261011`. Các bước đã làm: viền mùa 1 (trang Cài đặt có thêm `border_center_x/z`: 12500/-2000, bán kính 1300x1200); `chunky start` trong viền: 24.915 chunk trong 1 phút 58 (Minecraft tự trang trí: cỏ ngắn ~8%, dương xỉ, tre, mía, dưa, động vật lợn/cừu/vẹt/gà/bò/sói); đặt lại spawn GlassCup (0 lỗi), spawn (12699, 43, -2523), WorldGuard `spawn` phủ công trình, biển ghi công, 120 biển (`tools/spawn-signs.py`, warp mùa 1); `/dmm structures` (biên vùng mùa 1, lưới 24): 68 công trình (33 hầm mỏ, 8 tàu đắm, 8 di tích biển, 7 đền rừng, 6 phòng thử thách, 5 cổng đổ nát, 1 stronghold) nhưng **không có làng** (biome rừng nhiệt đới) → đặt thêm **27 làng** (plains/savanna) theo lưới 16 chunk trên đất liền, cách viền ≥150 và cách spawn ≥340 (bằng RCON: forceload ±112 → chờ 4 giây → `place structure`), ghi vào `structures-placed.csv`; 34 warp trong vùng (`make-warps.py`: tỉ lệ lấy `MAP_HALF_WIDTH` mặc định 21504, KHÔNG lấy `border_x`; bỏ qua chunk chưa có heightmap) + 33 biển tên; restic `fc63c195`. Sao lưu: `EXCLUDES` đổi thành `world-*` (loại mọi world cũ đã cất).
- Đã dọn ổ (12/10, người dùng đồng ý): xoá `world-1000-trangtritay-20261012`, `world-1-2000-20261011`, `out/earth_1-1000`, `out/earth_1-4000-populate`; ổ 69 → 111 GB trống. Giữ `out/earth_1-1000-populate` (map gốc để reset mùa).
- **Làng đặt bằng lệnh bị lơ lửng (12/10)**: `place structure` cho làng (jigsaw) tính độ cao nhà theo bộ sinh địa hình GỐC của game (seed), không theo địa hình Trái Đất → nhà lơ lửng/chìm 30–50 block. Đã gỡ cả 27 làng + 2 làng thử bằng `tools/restore-chunks.py` (trả chunk về map gốc, xoá entities/poi; TẮT server trước), rồi forceload + Chunky để Minecraft trang trí lại (Chunky bỏ qua chunk đã tồn tại nhưng chưa xong → forceload từng vùng trước). Đã sửa (12/10 16:45) bằng `tools/copy-villages.py`: dựng 27 làng trên world phẳng tạm (server `dmm-vill`, đã xoá), tìm 22 chỗ đất bằng trong vùng mùa 1 (`sites`), chép **17 làng** sang (`place`: san nền, block, bảng loot rương, 218 dân làng/golem/mèo có PersistenceRequired). Danh sách chỗ + làng: `tools/villages/villages-mua1-*.txt`. Đã kiểm ảnh + đếm dân làng; restic `dc337e43`. Công trình một khối (đền, tàu đắm, cổng đổ nát…) không bị; cổng đổ nát có thể lệch (chưa kiểm).
- **Khu spawn = toàn bộ công trình GlassCup, bỏ đất đảo (12/10)**: `tools/place-glasscup.py` (bỏ block đất/đá/cỏ của đảo và đèn ẩn `light`; tách 64 cụm công trình theo cột "cao" (>2 block trên mặt đảo hoặc không có đất), cột thấp sát công trình ≤3 ô theo công trình, còn lại (đường, đá rải) bám mặt đất từng cột; 162 cây anh đào theo gốc; mỗi cụm nâng/hạ theo trung vị mặt đất thật (mặt nước tính là mặt đất), đào phần đất chen vào, dọn cây phía trên, đắp móng stone_bricks tới đất/đáy biển). Đo mặt đất từ map gốc. Đã trả vùng x 12447..13254, z -2812..-2161 về map gốc rồi chạy 1,42 triệu lệnh theo 12 ô 192x192 (forceload → chờ 25 giây → lệnh), 0 lỗi, ~13 phút. Quảng trường nay NGANG mặt đất (dy 57; trước là trũng), khu đền lớn giữa vịnh Bắc Bộ trên móng, tượng phượng hoàng hạ xuống đất phía bắc. Độ nâng từng cột lưu ở scratch `glass-dy.npy` (tạo lại bằng cách chạy lại script). Spawn (12699, 65, -2523); biển ghi công (12699, 65, -2520); 120 biển `spawn-signs.py … <dy.npy>` (y = y trong file + dy); 25 vùng WorldGuard `glasscup-N` (build deny, use allow, priority 5) cho cụm ≥150 cột + vùng `spawn` cũ; 34 warp + biển tên; restic `c58571e0`.
- **Spawn chỉ giữ phần giữa (12/10 17:10)**: theo yêu cầu "chỉnh lại spawn như cũ, giữ lại ở giữa". `place-glasscup.py` có thêm tham số cắt `x1 z1 x2 z2` (toạ độ trong file; dùng `120 160 298 470`): cụm công trình/cây có tâm trong vùng giữ cả khối, trang trí mặt đất giữ khi trong vùng VÀ công trình gần nhất được giữ. Đã trả 1.019 chunk bản đặt cũ về map gốc (`restore-chunks.py`, không chạm chunk làng), forceload cho Minecraft trang trí lại, đặt phần giữa (độ cao từng cụm giống hệt bản trước, spawn vẫn (12699, 65, -2523)), ghi lại 120 biển, xoá 25 vùng WG `glasscup-N`, vùng `spawn` nới thành x 12556..12801, z -2623..-2298 (bản cũ: `regions.yml.truoc-spawn-giua`). Lệnh đầy đủ:
  `python3 tools/place-glasscup.py data/banghoi-1/plugins/WorldEdit/schematics/oriental_japanese_spring_lobby1.21.schem tools/earthmap/work/out/earth_1-1000-populate/dimensions/minecraft/overworld/region 12479 -2780 glass.cmds glass-dy.npy 120 160 298 470`

- **Lobby = đảo bay Sakura Spawn (12/10 17:47)**: map của ArtillexStudios (người dùng tải về `F:\Downloads\Sakura_Spawn_-_ArtillexStudios`, bản 1.12.2). Paper 26.2 KHÔNG có `--forceUpgrade` ("not yet implemented") nhưng tự chuyển chunk cũ khi nạp → nạp vùng đảo (x -160..159, z -288..223) trên server tạm, lưu, chép vào `data/lobby/sakura`, `LEVEL: sakura`. Spawn (0, 89, 0) (block bedrock giữa quảng trường tròn), viền 360 quanh (0, -40), `advance_time`/`advance_weather` false. DuckyMoMoCore thêm `VoidTeleport` (config `void-teleport.below-y: 0`, `spawn-on-join: true`): rơi khỏi đảo về spawn, mỗi lần vào đứng giữa ô bedrock (0.5, 89, 0.5); gamerule `respawn_radius 0`. Hướng nhìn lấy từ `/setworldspawn 0 89 0 <yaw>`. World cũ giữ: `world` (phẳng), `lobby-hub`, `lobby-sanh`. Restic lobby `ca175cb5`.
- **Hologram (12/10)**: DecentHolograms 2.10.1 (Modrinth `decentholograms`) ở lobby, banghoi, practice, template. Lệnh `/dh create <tên> <chữ>`.
- **Nhiều phiên bản client (12/10)**: ViaVersion/ViaBackwards/ViaRewind 5.12.1 đã chạy trên lobby, banghoi, practice → client **1.13 tới 26.3** vào được. Client **1.7–1.12 bị Velocity chặn** ("only compatible with Minecraft 1.13 and above") vì `player-info-forwarding-mode = "modern"`; muốn mở phải đổi sang forwarding legacy + BungeeGuard (kém an toàn hơn, Pokémon Fabric dùng FabricProxy-Lite cần modern) → người dùng CHỐT (12/10): chỉ hỗ trợ 1.13 → mới nhất, giữ forwarding modern, không mở 1.7–1.12. Pokémon (Fabric) chỉ nhận đúng phiên bản Cobblemon.

- Còn để ngỏ: ~~`/tpr`~~ (xong 13/10, xem mục 0); dân làng đổi đồ (chờ chốt bộ). Đã xoá snapshot restic của world cũ (12/10): kho banghoi-1 53 → 12,7 GB, chỉ giữ `fc63c195` (world mùa 1).
- **Map spawn tải về**: "Oriental Japanese Spring Lobby" của GlassCup (BuiltByBit, Free EULA: dùng trên server, sửa được; không chia sẻ lại file, không xoá ghi công). File ở `data/banghoi-1/plugins/WorldEdit/schematics/oriental_japanese_spring_lobby1.21.schem` (không commit). Khung 744x289x588: đảo lễ hội trái (x<460, y 0–250, 4,7 triệu block) + khu đền phải. Người dùng muốn dùng làm spawn Bang Hội Chiến (không phải lobby; lobby đã trả về world phẳng `world`, các world thử `lobby-hub`, `lobby-sanh` còn trong data/lobby). **Chờ người dùng chọn**: (1) cắt phần trung tâm ~200x310 đặt ở Hà Nội, (2) cả đảo ra biển Đông, (3) cả đảo + đền ra biển. Đặt bằng console (đổi .schem thành lệnh fill theo từng vùng), không dùng WorldEdit paste.
- **Đã đặt (12/10 ~05:10, phương án 1)**: cắt x 120–320, z 160–470 của file (quảng trường hào nước + đại lộ + quảng trường tròn), đặt bằng `tools/place-schem.py <schem> 120 160 320 470 12479 35 -2780 100` (gốc file ở world 12479, 35, -2780; mặt đất đảo y≈27 → world 62): 181.645 lệnh fill/setblock `strict`, ~2 phút, 0 lỗi. Vùng world x 12599..12799, z -2620..-2310 (góc đông nam chạm vịnh Bắc Bộ, mép cắt thẳng). Spawn (12699, 43, -2523) giữa quảng trường (sàn trũng ~20 block dưới mặt đất); vùng WorldGuard `spawn` = cả vùng công trình. Biển ghi công GlassCup cạnh spawn + dòng trong MOTD. Khu spawn tự xây bản 1 (`tools/build-spawn.py`) đã bị thay: **chợ [Buy]/[Sell] và tường [Warp] không còn**; 194 biển trống của công trình (quanh vòng quảng trường, cách spawn ~50) để dành gắn shop/warp.

### Đợt 12/10/2026 tối: chống hack, combat log, kinh tế, chat, bản đồ, Discord, Bedrock, sao lưu ra F:
- **Plugin mới ở banghoi** (`modes/banghoi/compose.yaml`): `grimac:nKI7MWZj` (GrimAC 2.3.74 ALPHA, ghim id; cũng ở practice), `pvpmanager` 4.0.22, `ujobs` 1.0.8, `squaremap` 1.3.15, `tab-was-taken` (TAB 6.2.0), `discordsrv` 1.30.5; Floodgate (Spigot, link GeyserMC) ở lobby/banghoi/practice; `decentholograms` 2.10.1 ở lobby/banghoi/practice/template.
- **GrimAC**: để mặc định (punishments.yml chỉ `[alert]`/`[log]`, KHÔNG kick/ban) vì bản alpha. Nhóm `mod` có `grim.alerts`, `grim.alerts.enable-on-join`. Khi chắc không bắt nhầm thì thêm kick vào punishments.yml.
- **PvPManager** (config + `messages.properties` tiếng Việt, dạng `\uXXXX`, trong repo): gắn cờ đánh nhau 15 giây, thoát game khi đang đánh = chết + rơi đồ + `broadcast`; bảo vệ người mới 900 giây (boss bar "BẢO VỆ NGƯỜI MỚI"); người thường KHÔNG có `/pvp` (quyền `pvpmanager.command.pvp` mặc định false) → PvP do Towny/SiegeWar quyết; `Player Drop Mode: ALWAYS` (không can thiệp, SiegeWar lo giữ đồ trong vùng vây).
- **uJobs** (`UJobs/jobs.yml`, `config.yml` trong repo): nghề tự động (không cần /jobs join), tên nghề tiếng Việt; tiền mặc định quá nhỏ nên nhân: quặng x5, đá/đất x2, quái x2.5, gỗ x3, nông sản x5, chăn nuôi x3, câu cá x0.5 (treo máy), giết người x0. `silktouch_blacklist` = mọi quặng. **Chống đặt-phá**: DuckyMoMoCore `PlacedBlockGuard` (config `job-guard`): block trong danh sách do người đặt được đánh dấu trong PDC của chunk; phá lại thì huỷ sự kiện + tự `breakNaturally` (uJobs MONITOR bỏ qua sự kiện huỷ → không trả tiền), ghi CoreProtect qua API `logRemoval`.
- **Kinh tế**: `Essentials/worth.yml` VIẾT LẠI (file cũ là mẫu của EssentialsX, tên vật phẩm kiểu cũ, giá lộn xộn): giá một món, không mua đá cuội/đá/đất/cát/sỏi; khớp biển [Sell] ở spawn (`tools/spawn-signs.py` SELL: bỏ đá cuội/cát/đất, thay bằng 4 loại gỗ; sắt 4, vàng 6, than 1). Tiền khởi đầu 100 → **200** (settings.yml, đã "Áp dụng lại tất cả" qua API panel). Luật thị trấn/công thành GIỮ như settings.yml (500/1000/25, upkeep 10/100, sat+CN 20:00 & 21:00, 4 phiên, tối đa 2 cuộc vây, quỹ 20/ô, miễn vây 7 ngày).
- **Dọn rác**: DuckyMoMoCore `ItemCleaner` (config `item-cleaner`, chỉ banghoi): mỗi 10 phút, báo trước 60/30/10/5/3/2/1 giây, xoá vật phẩm dưới đất (trừ vật phẩm < 30 giây tuổi và đã đổi tên) + mũi tên cắm đất (trừ đinh ba). `/dmm clean` dọn ngay.
- **Lọc chat**: DuckyMoMoCore `ChatFilter` (config `chat-filter`, lobby/banghoi/practice; practice giờ cũng có DuckyMoMoCore, `ops/build-core.sh` chép jar cho cả 3): che từ tục bằng `*`; `words-accented` khớp có dấu (lồn/cặc/buồi/đéo/đĩ… vì bỏ dấu trùng lớn/các/buổi/đeo/đi), `words-plain` khớp sau khi bỏ dấu (đm, vcl, dcm…); chống spam: cách 1,5 giây, không lặp câu trong 30 giây, viết hoa ≥70% thì hạ chữ thường. Quyền `duckymomo.chat.bypass` (op) bỏ qua chống spam. Đã thử logic trên câu mẫu (không che nhầm lớn/các/buổi/đeo/đi/admin/vlog).
- **TAB** (`TAB/config.yml`, `animations.yml` trong repo): bảng bên phải (tên, role, tiền `%vault_eco_balance_formatted%`, thị trấn/quốc gia Towny, online, ping; `/sb` ẩn), boss bar mẹo xoay vòng 8 giây (`animation:MeoChoi`), header/footer Tab. Cần phần mở rộng PAPI `Vault`, `Essentials` (đã `papi ecloud download`, nằm trong data/, không trong Git).
- **Bảng xếp hạng**: DecentHolograms `bxh_tien` (top 5 giàu nhất, `%essentials_baltop_player_0..4%` – đánh số từ 0) ở (12699.5, 69.5, -2517.5), bản lưu trong repo `DecentHolograms/holograms/`. Thị trấn mạnh nhất: chưa làm. Nghề: `/jobs` có bảng xếp hạng riêng.
- **Bản đồ web squaremap**: chỉ vẽ vùng mùa 1 (`visibility-limits` rectangle 11200..13800 x -3200..-800; fullrender 24.613 chunk ~8 giây: `squaremap fullrender minecraft:overworld`), tắt Nether/End (khoá `minecraft:the_nether`, `minecraft:the_end`), **tắt player-tracker** (server chiến tranh). Cổng `127.0.0.1:${SQUAREMAP_PORT:-8890}`; Trạm 6: route `mc-map` `/mc/map/` (require-login) + thẻ "Bản đồ" trong `apps/mc/public/index.html`. Lớp "Thị trấn": DuckyMoMoCore `TownyMapLayer` (config `towny-map`), gộp ô đất thành hình chữ nhật, màu theo quốc gia; CHƯA thấy trên web vì chưa có thị trấn nào (squaremap bỏ lớp rỗng?) → kiểm lại khi có thị trấn. `/squaremap reload` làm mất lớp này tới lần khởi động lại.
- **Discord**: DiscordSRV cài sẵn, token/kênh từ `.env` (`DISCORD_BOT_TOKEN`, `DISCORD_CHANNEL_ID`; trống → plugin tự tắt), tắt lệnh console từ Discord. Thông báo công thành: `ops/siege-discord.sh` điền `DISCORD_SIEGE_WEBHOOK_URL` vào config SiegeWar trong data (không vào Git) rồi reload.
- **Bedrock form**: Floodgate ở lobby/banghoi/practice dùng khoá proxy gắn vào `/run/floodgate/key.pem` (gắn vào `plugins/floodgate/` thì Docker tạo thư mục root → Floodgate lỗi "Failed to write to path"); config trong repo `*/plugins/floodgate/config.yml`; proxy `send-floodgate-data: true` (`proxy/plugins/floodgate/config.yml`). DuckyMoMoCore `BedrockMenu`: người Bedrock mở menu chọn chế độ thấy form nút bấm (Cumulus SimpleForm). Chưa thử bằng điện thoại thật.
- **Sao lưu ra ổ F:** `BACKUP_DIR=/mnt/f/DuckyMoMo-backups` trong `.env` (đã chép kho restic cũ sang bằng container root vì thư mục restic thuộc root). Backup thử 18:39: banghoi `966a89d7`, lobby `dd7f2548`, practice `5d20534f`. Kho cũ `backups/` trong repo (17 GB, ổ C) CHƯA xoá. Ra hẳn ngoài máy (cloud) chưa làm: cần tài khoản của người dùng.

### Rank donate: 5 tước vị (12/10/2026 tối)
- Người dùng chốt: gói 1/3/6/12 tháng, **admin cấp tay** (chuyển khoản), quyền: prefix + màu tên, chat màu, /nick, /hat, thông báo khi vào (mọi người đều có dòng vào/ra), hiệu ứng hạt, pet. Tên bậc do tôi đề xuất (tước vị phong kiến, hợp Bang Hội Chiến) — người dùng chưa phản hồi về tên/giá.
- LuckPerms (`tools/donate-ranks.cmds`, chạy qua rcon; dữ liệu DB dùng chung network): `namtuoc` Nam Tước &a (w5) < `tutuoc` Tử Tước &b < `batuoc` Bá Tước &9 < `hautuoc` Hầu Tước &d < `congtuoc` Công Tước &6&l (w9); bậc cao kế thừa bậc thấp; track `donate`. Quyền: Nam: essentials.chat.color, hat, duckymomo.rank.join; Tử: nick, playerparticles.effect.*/style.*, particles.max.1; Bá: nick.color, particles.max.2, 13 pet thường (`pet.type.<loài>.*`); Hầu: chat.format, nick.format, particles.max.3, 14 pet hiếm; Công: chat.rgb, nick.rgb, particles.max.6, `pet.type.passive`, duckymomo.rank.join.special.
- DuckyMoMoCore `DonateRanks` (config `donate-ranks` ở lobby/banghoi/practice, giữ giống nhau; danh sách bậc, quyền lợi, **giá đề xuất** 20k–150k/tháng): `/rank` (alias donate, tuocvi) cho người chơi xem bậc/giá/rank của mình + hạn; admin `/rank give <tên> <bậc> <1-24 tháng>` (1 tháng = 30 ngày, `TemporaryNodeMergeStrategy.ADD_NEW_DURATION_TO_EXISTING` → gia hạn cộng dồn; đổi bậc thì bỏ bậc cũ và báo số ngày còn lại để admin tự bù), `/rank remove`, `/rank check`. Ghi log "Rank donate: X cấp ...". Đã thử qua rcon: 3+1 tháng = 120 ngày, nâng lên congtuoc bỏ batuoc (còn 119 ngày), gỡ sạch. Thông báo vào/ra: mọi người `[+] {prefix}{tên}` / `[-]`; có rank thêm "✦ <bậc> <tên> vừa giá lâm!", Công Tước thêm tiếng chuông cho cả server.
- PlayerParticles 8.13 (`/pp`) + SimplePets R5-B315 (`/pet`) ở lobby + banghoi. PlayerParticles: `max-particles: 0` (không rank thì không có), `toggle-on-combat: true` (đánh nhau ẩn hạt, khỏi lộ vị trí), `display-when-invisible: false`. SimplePets: **mọi pet `"mount": false`, `"fly": false`** (cưỡi/bay = lợi thế di chuyển, trái luật Mojang) — 90 file JSON trong repo `*/plugins/SimplePets/Pets/`; không cấp pet boss/quái to.
- Chưa làm: thanh toán tự động (SePay), cập nhật bảng INFO ở spawn về rank.

### Trang Cài đặt mở rộng + phân loại (12/10/2026 tối)
- Hai trang: **Toàn mạng** (`modes/_network/settings.schema.yml` + `settings.yml`; thư mục `_` nên menu chọn chế độ và trang Hoạt động bỏ qua) và **Bang Hội Chiến**. Mỗi nhóm có `category`; giao diện có thanh chọn loại (nút "Tất cả"), loại có mục sửa chưa lưu hiện dấu •.
  - Toàn mạng: Chat (lọc chat: bật/tắt, từ cấm có dấu/không dấu, chống spam, tin báo; thông báo vào/ra), Rank donate (hướng dẫn mua; mỗi bậc: tên, prefix → lệnh `lp group … meta setprefix`, giá, quyền lợi hiển thị; PlayerParticles), Sảnh (độ cao rơi về spawn, vào là về spawn).
  - Bang Hội Chiến thêm: PvP & combat (PvPManager: thời gian gắn cờ, chết khi thoát, cấm dịch chuyển, bảo vệ người mới), Hệ thống (dọn rác), Kinh tế (giá /sell 16 món), Hiển thị (TAB: boss bar mẹo, bảng bên phải, đầu/cuối Tab).
- `panel/app.py`: kiểu `list` (phẩy, `items: int|number`) và `lines` (mỗi dòng 1 mục); `path` có chỉ số danh sách (`donate-ranks.tiers.0.prices`); `servers: [...]` ghi cùng giá trị vào nhiều server, lệnh `apply` của nhóm chạy trên từng server có mục đổi. `panel/index.html`: textarea, ô danh sách, thanh loại. Panel phải build lại khi sửa app.py/index.html (`docker compose up -d --build panel`); schema/settings đọc trực tiếp (không cần build).
- Loại **Nghề** (trang Bang Hội Chiến): 13 nhóm (mỗi nghề uJobs một nhóm), 254 ô tiền `jobs.<nghề>.actions.<hành động>.<đối tượng>.money` (sinh từ jobs.yml 12/10, tên mob/block phổ biến dịch tiếng Việt; khoá số như phù phép cấp 1 chạy được), áp dụng bằng `jobs reload` (đã thử sửa rồi trả lại: file y nguyên). Thêm/bớt hành động thì sửa jobs.yml rồi thêm mục vào schema.
- Loại **Chống hack** (trang Bang Hội Chiến, ghi cho banghoi-1 + practice-1): 9 nhóm xử phạt GrimAC (`Punishments.<nhóm>.commands`), mỗi nhóm chọn Chỉ báo (mặc định) / Báo + kick ở ngưỡng vừa / Báo + kick sớm (ngưỡng /2); dòng kick: `N:0 kick %player% Hệ thống chống hack: ...`. Chung: thời gian quên vi phạm (Simulation, Reach), chặn cú đánh quá xa, kiểm tra thử nghiệm, ghi log, chặn Forge cấm. Áp dụng `grim reload` (đã thử bật/tắt kick nhóm Reach trên 2 server). Kick ở server con thì Velocity có thể đưa người chơi về lobby thay vì ngắt hẳn.
- **Cẩm nang** (`/camnang` trong trang Cài đặt, link trên thanh đầu): nội dung ở `modes/_network/camnang.yml` (sửa không cần build): hướng dẫn nhanh (việc admin hay làm, người chơi mới, địa chỉ vào) + 30 mục plugin (công dụng, lệnh người chơi, lệnh admin, chỉnh ở đâu, lưu ý), lọc theo server và loại, ô tìm kiếm. `/api/camnang` quét `data/<server>/plugins|mods/*.jar` để đánh dấu đang cài ở đâu và liệt kê jar chưa có mục (cài plugin mới thì thêm mục). **Mục "Tất cả lệnh"**: DuckyMoMoCore `CommandDump` xuất MỌI lệnh đang đăng ký (mọi plugin + vanilla) ra `data/<srv>/plugins/DuckyMoMoCore/commands.json` 30 giây sau khi server bật và khi gõ `/dmm lenh` (lobby ~266, banghoi ~485, practice ~285); panel gộp theo plugin, tên gọi tắt, server, tìm kiếm (ô tìm ở mọi loại cũng hiện "Lệnh khớp"). "Ai dùng được" suy từ quyền của lệnh (Essentials không khai báo → `essentials.<lệnh>`) + bảng `luckperms_group_permissions` (default=true → Mọi người; nhóm thấp nhất có quyền → "Từ …"; còn lại Chỉ admin; "?" = plugin không khai báo quyền). Mô tả tiếng Việt: `modes/_network/lenh-vi.yml` (khoá = tên lệnh, không trùng khoá) + tự lấy từ dòng lệnh trong camnang.yml. Lệnh proxy (Velocity, SkinsRestorer, Geyser) và lệnh con của plugin lớn (Towny /t /n /plot /res, SiegeWar, CoreProtect, LuckPerms, WorldGuard, WorldEdit, AuthMe, Duels, shop, CE, Grim…; ~260 dòng) ghi tay ở `modes/_network/lenh-con.yml` (`[lệnh, mô tả, ai]`, servers theo nhóm; trùng tên lệnh tự xuất thì ghi đè mô tả + ai). Panel bỏ tiền tố `plugin:` trong tên (ujobs:jobs, grimac:grim) và dòng trùng tên gọi tắt. Plugin cập nhật đổi lệnh con thì sửa file này.
- **Cẩm nang trong game** `/huongdan [chương]` (alias hd, guide, camnang, cn; DuckyMoMoCore `Guide`): Java mở sách (trang 1 mục lục bấm được, mỗi trang có "« Mục lục"), Bedrock thấy form nút (mỗi chương một nút). Bản gốc `modes/_network/huongdan.yml` (7 chương: batdau, luat, banghoi 14 trang, practice, rank, pokemon, hotro), chép bằng `./ops/huongdan.sh` (repo + data, đọc lại mỗi lần mở). Tự ngắt dòng/chia trang theo bề rộng font (114 px, 13 dòng + dòng mục lục), giữ mã màu. `guide.default-chapter` (lobby batdau, banghoi banghoi, practice practice), `guide.join-hint` (dòng nhắc bấm được, 3 giây sau khi vào). Số liệu trong sách ghi tay: đổi trên trang Cài đặt thì sửa sách. **Sửa trên web**: trang Cài đặt > "Cẩm nang game" (`/huongdan` của panel, `panel/huongdan.html`, API `/api/huongdan`): bìa, mục lục, thêm/xoá/đổi thứ tự chương và trang, xem trước giống sách (cùng thuật toán ngắt dòng, viền đỏ = bị tách trang), bảng mã màu; lưu = ghi bản gốc (ruamel, giữ chú thích, trang dạng khối |) + chép 6 chỗ (repo banghoi/practice/lobby — panel gắn thêm `../lobby/plugins/DuckyMoMoCore:/repo/lobby-dmm` — và data của 3 server). Boss bar thêm mẹo /huongdan.
- **Ping (12/10 21:50)**: TPS 20, tick 1,8 ms (server không lag). Ping 160–190 ms là do đường playit VN → Singapore → nhà (người chơi ngồi ngay máy chủ vẫn đi vòng); vọt ~500 ms khi bay vì gửi dồn chunk qua tunnel → `chunk-loading-basic.player-max-chunk-send-rate: 30` (mặc định 75) trong `modes/banghoi/server/config/paper-global.yml`. Muốn ping thấp hẳn: mở cổng trực tiếp (nếu mạng nhà không CGNAT) hoặc thuê máy chủ ở VN.
- **Bảng bên phải mới** (TAB, qua trang Cài đặt): tiêu đề gradient, icon ❤ ⛃ ⚑ ♛ ☺ ⚡, ping đổi màu theo điều kiện TAB `pingmau`/`pingcao` (<120 xanh, <250 vàng, còn lại đỏ); header/footer có icon.
- **Gói font tiếng Việt** (font pixel gốc vẽ chữ có dấu khó đọc): `ops/build-font-pack.sh` đóng gói Be Vietnam Pro Medium (SIL OFL, `tools/fontpack/src/`, giấy phép kèm trong gói) thay font `default` (TTF size 6.5, shift 0, oversample 8 — 12/10: size 10 rồi 7.5 vẫn to; shift 1.5 làm chữ thấp hơn icon; ký hiệu lấy từ font gốc qua reference include/default, include/unifont), pack_format 88 (26.2) + supported_formats 15–88, chép lên Trạm 6 `apps/mc/public/pack/duckymomo-font-<sha8>.zip` (route CÔNG KHAI `mc-pack` `/mc/pack/` + middleware `mc-strip`), ghi `FONT_PACK_URL`/`FONT_PACK_SHA1` vào .env; lobby/banghoi/practice: `RESOURCE_PACK*` (không bắt buộc, có lời nhắc). Bedrock không dùng gói Java. CHƯA xem trong game: cỡ chữ/độ cao dòng có thể cần chỉnh (size/shift trong script), ngắt trang sách tính theo bề rộng font pixel.
- **Enchantio 1.16.0** (Modrinth `enchantio`, banghoi): phù phép mới đăng ký như gốc (registry) → hàng hiếm để đổi bán. Config `modes/banghoi/server/plugins/Enchantio/config.yml`: tags `#tradeable #on_random_loot #treasure` (không ra từ bàn phù phép); TẮT soulbound, homecoming, cloaking, ward (phá luật chiến tranh); executioner, volley tối đa II; tunnelling chỉ đá tự nhiên + quặng. Lỗi config mặc định: `#minecraft:ores` không tồn tại → thay bằng `#minecraft:<loại>_ores`. Đổi config phải khởi động lại (registry). Lệnh admin dùng `minecraft:give` / `minecraft:enchant` (Essentials đè /give). Đã ghi cẩm nang admin + trang "Phù phép hiếm" trong huongdan.
- **Đồ vượt cấp** (người dùng chọn hướng C: Sắc bén 20–30… nhưng CHỈ admin tạo): DuckyMoMoCore `OverEnchant` (config `over-enchant`, banghoi, max-level 50): `/dmm enchant <phù phép> <cấp>` (món đang cầm, addUnsafeEnchantment), `/dmm sach <phù phép> <cấp> [người]` (sách); đe: ghép sách vượt cấp vào đồ giữ cấp của sách (PrepareAnvilEvent, giá 30 cấp), hai sách với nhau theo luật gốc. Nhắc người dùng: không bán lấy tiền thật (pay-to-win). Đã ghi cẩm nang. Chưa thử với người chơi online.
- **CrazyEnchantments** (BETA `crazyenchantments:QUo33hOY`, banghoi; người dùng chọn hướng C): shop `/ce` chỉ bán Thường 300 xu / Hiếm 800 xu bằng Vault, Huyền thoại/Thần thoại InGUI false (admin phát `/ce book ...`); tắt 11 phù phép (Wings, SystemReboot, Cyborg, Boom, SelfDestruct, Guards, Necromancer, Infestation, Disarmer + Telepathy, Furnace trùng Enchantio); 50 phù phép mạnh chỉ ở Legendary/Mythical; GKitz tắt; quyền default: crazyenchantments.gui/info/blacksmith/tinker, gkitz=false. Config + README trong `modes/banghoi/server/plugins/CrazyEnchantments/`. Chưa thử mua/ghép trong game.
- **Trang Cài đặt: loại Shop + Phù phép (12/10 23:40)**. Shop: giá /sell (chuyển từ Kinh tế), shop /ce (giá, loại tiền, mô tả từng độ hiếm; bán/không bán Huyền thoại/Thần thoại; tỉ lệ thành công/vỡ đồ/cấp; giá cuộn giấy, bụi, pha lê), cửa hàng rương QuickShop (thuế, phí mở, phí đổi giá, giới hạn số cửa hàng, phí duy trì, giới hạn giá — `qs reload`), chợ /ah (số món: lệnh LuckPerms zauctionhouse.max.N; thời gian đăng, giá min/max, thuế, thông báo — `zauctionhouse admin reload`), /trade TradePro (khoảng cách, chờ, đếm ngược chống lừa, đổi kèm tiền/XP, thuế, cùng IP — `tradepro reload`). Phù phép: bật/tắt 105 phù phép CE (`ce reload`), Enchantio bật/tắt + cấp tối đa (cần khởi động lại). Script điền giá trị: `docker cp ops/panel/seed-settings.py duckymomo-panel-1:/tmp/ && docker exec duckymomo-panel-1 python3 /tmp/seed-settings.py banghoi banghoi-1` (đọc file plugin, điền mục chưa có). Đã thử lưu/trả lại từng shop. Biển mua bán ở spawn CHƯA lên trang (giá nằm trong tools/spawn-signs.py).
- **Shopkeepers 2.28.1** (link ghim forgecdn trong PLUGINS; Spigot 80756 ghi đã thử 26.2) + **ShopkeepersAddon** (Modrinth `shopkeepers-addon`, tiền Vault, giới hạn bán cho shop admin 1.000 xu/ngày, ký hiệu ' xu' sau số): dân làng NPC bán hàng cho ý tưởng "set đồ ở spawn" (chưa tạo NPC nào, chờ người dùng chốt set). Người chơi tự mở shop dân làng: TẮT (`lp group default permission set shopkeeper.player false`). Cẩm nang + trang Cài đặt (Shop > Dân làng bán hàng). Tạo NPC: `/shopkeeper admin` rồi chuột phải (cúi) để sửa.
- **Trang Role** (panel `/role`, `panel/role.html`, API `/api/roles`): đọc LuckPerms trong MariaDB bằng pymysql (panel có DB_USER/DB_PASSWORD), liệt kê role chức vụ + rank donate kèm thành viên/hạn; cấp/gỡ qua RCON ở lobby (`lp user … parent add/remove`, rank donate dùng `/rank give|remove`). Prefix role chức vụ: trang Toàn mạng > Role (lệnh lp meta setprefix). Đã thử cấp/gỡ Builder và Nam Tước cho Linh. **Quyền từng role/rank**: chọn role, bật/tắt công tắc từ danh mục `modes/_network/quyen.yml` (68 quyền, 8 nhóm; rank donate chỉ thấy nhóm trang trí + cảnh báo luật Mojang), chọn áp dụng toàn mạng hoặc 1 server (`server=…`), hiện quyền kế thừa từ nhóm cha, thêm/gỡ quyền tuỳ ý (`lp group … permission set|unset`). Đã thử set/unset essentials.gamemode (global) và .creative (banghoi-1) cho builder.
- **Thư viện đồ có phù phép CrazyEnchantments (15/10)**: `CeBridge.java` gọi API CE bằng reflection (CrazyEnchantments.getStarter().getCrazyManager(): getRegisteredEnchantments, getEnchantmentFromName, addEnchantment) nên build không cần jar CE. `thuvien-meta.json` có thêm `ce` (105 phù phép: name, display, max, active, type, items, desc). Món web lưu `ce-enchants: {Tên: cấp}`; khi dựng đồ CE tự ghi dòng mô tả. Web: mã `ce:<tên thường>` trong ô phù phép, nhãn "[CE]", cảnh báo khi phù phép CE đang tắt (Enabled: false) hoặc vượt cấp. Tên + mô tả tiếng Việt 105 phù phép CE: `modes/_network/ce-vi.yml` (panel `ce_vi()` gắn vào meta.ce: vi, vi_desc; trang Cài đặt > Phù phép cũng đã đổi nhãn/ghi chú sang tiếng Việt; bản dịch theo mô tả gốc của CE, chưa thử từng phù phép trong game). Đã thử dựng món Trap 2 qua `/dmm thuvien nap` (không lỗi); CHƯA nhìn món CE trong game. Khởi động lại 3 server 19:26 (không ai online): banghoi có SkinsRestorer + plugin mới.
- **/dmm có gợi ý Tab (15/10)**: `dmmTab` trong `DuckyMoMoCore.java` (lệnh con, phù phép từ registry, mã thư viện đồ, người online); alias `/duckymomo`; `/dmm` trống in hướng dẫn nhiều dòng. Đã nạp ở cả 3 server.
- **Giao diện trang quản trị (15/10, lần 2)**: thành phần dùng chung trong `panel/shell.js` + `panel/ui.css`: `combo()` (ô chọn có ô tìm riêng, không dấu vẫn tìm được; input gốc ẩn nhưng giữ `.value`/`.disabled`), `mcHtml()` (xem trước mã màu &, &#hex, %placeholder%), `vnMat()`/`matLabel()` (tên đồ tiếng Việt), `toast()`. Trang Cài đặt: `lines` có mã màu = từng dòng + đổi thứ tự + xem trước như trong game; `lines` có `style: tags` (từ cấm) và `list` = thẻ nhỏ, Enter để thêm, dán nhiều dòng được; ô vật phẩm trong quà/cửa hàng/mảnh dùng `combo`; địa chỉ nhớ `#chế_độ/danh_mục`. Trang Role: danh sách trái + chi tiết phải (thành viên, công tắc quyền theo nhóm, lọc, chọn server; quyền `*` hiện "có qua quyền *"). Thư viện đồ và Boss chia khối đánh số; Boss: kỹ năng dạng thẻ, loại quái/trang bị dùng `combo`, xem trước thông báo. Hoạt động: nhóm theo ngày, nhãn màu, khung gửi tin luôn ở dưới. Cẩm nang: chỉ phần lệnh hiện dạng mã. Kiểm tra bằng bản xem thử chỉ đọc (`panel-xemthu`, cổng 8899) + Chrome headless; KHÔNG bấm thật trên server.
- **Kit quà tân thủ** (13/10): Essentials `kits.yml` kit `tanthu` (delay -1: công cụ đá, giáp da, 16 bánh mì, 16 đuốc, giường, 32 gỗ sồi, bàn chế tạo, tin nhắn nhắc /huongdan); `newbies.kit: tanthu` (trước là kit mẫu `tools`), câu chào người mới tiếng Việt. Trang Cài đặt: Bang Hội Chiến > Người mới. Sửa thẳng data (Essentials/kits.yml, config.yml không có trong repo).
- **Warp sát spawn** (13/10): ở 1:1000 haiphong/halong/ninhbinh rơi VÀO khu spawn (56–109 block) → `/delwarp` 3 warp này; thêm `condao` (12784,-1049, đảo, không có biển tên) và `luangnamtha` (12114,-2503) bằng `ONLY=condao,luangnamtha python3 tools/earthmap/make-warps.py …` (tuỳ chọn ONLY mới). Biển spawn: dãy "Đồng bằng Bắc Bộ" → "Miền xa" (luangnamtha, condao, naypyidaw, phuket). Còn 33 warp. `tools/glassc-dy.npy` (độ nâng từng cột của spawn, cần cho spawn-signs.py) đã lưu vào repo.
- **Sức chứa mùa 1** (đo 13/10): viền 24.300 chunk, đất 13.212 (54%), núi cao y≥130: 896, khu spawn ~336. Towny: 8 ô/người, cách thị trấn khác ≥5 ô.
- DuckyMoMoCore `/dmm reload`: đọc lại config.yml, gỡ listener + task rồi bật lại mọi phần (trang trí map đang chạy dở thì dừng, lưu tiến độ).
- Lệnh nạp lại dùng: `dmm reload`, `pmr reload` (PvPManager), `tab reload`, `essentials reload`, `swa reload`, `ta reload config`, `ocm reload`. **`pp reload` KHÔNG chạy qua RCON** (PlayerParticles ép người gửi thành Player) → đổi PlayerParticles phải khởi động lại server.
- Đã thử "Áp dụng lại tất cả" cả hai trang (12/10 20:47): mọi lệnh OK, file YAML vẫn hợp lệ, prefix LuckPerms đúng.
- **Lưu ý file vừa có trong repo vừa sửa qua panel** (DuckyMoMoCore, PvPManager, TAB, worth.yml): panel ghi vào `data/`; itzg chỉ chép từ repo khi file repo MỚI hơn → sửa file repo sau đó sẽ đè giá trị panel. Sửa repo thì sửa luôn settings.yml cho khớp (hoặc bấm "Áp dụng lại tất cả" sau khi khởi động lại).
- WorldGuard banghoi: `mobs.block-plugin-spawning: false` (SimplePets báo lỗi không tạo được pet khi true). File WG không có trong repo.

### Sao lưu chuyển sang restic + dọn ổ (11/10/2026)
- Map 1:1000 làm mỗi bản `.tar.gz` nặng 17 GB (gần như không nén được; 6 giờ/lần × 7 ngày sẽ ~480 GB). `backup-banghoi-1`, `backup-lobby`, pokemon, mẫu `_template-paper`: `BACKUP_METHOD=restic`, kho ở `backups/<server>/restic` (thư mục root-only, xem bằng container), `RESTIC_HOSTNAME`=tên server, giữ `--keep-within ${BACKUP_KEEP_DAYS}d --keep-daily 30`, `EXCLUDES` thêm `world-cu-*,world-1-2000-*,world-tu-nhien-*`. `RESTIC_PASSWORD` trong `.env` (`ops/init.sh` tự sinh) — **mất là không khôi phục được**; người dùng đã cất thêm một bản ngoài máy (11/10).
- Đo: lần đầu banghoi-1 17,0 GB → 12,1 GB lưu, 49 giây; lần hai 107 KB; khôi phục thử `level.dat` khớp file hiện tại. Lệnh khôi phục: README "Sao lưu và khôi phục". `backup-db` vẫn là `mariadb-dump` `.sql.gz`.
- Lưu ý: khi `backup now` server `save-off` vài giây–1 phút; trong lúc trang trí map nên `/dmm decorate pause` trước (chưa chắc chunk bị gỡ lúc `save-off` có được lưu).
- Đã dọn: 12 bản tar.gz 1:2000 cũ của banghoi-1 (giữ `banghoi-1-20261011-150128.tar.gz`, bản 1:2000 cuối), tar.gz lobby cũ (giữ bản cuối), map gốc 1:2000/1:4000, image Chrome. Ổ: 112 → 150 GB trống. Còn giữ: world 1:2000 `data/banghoi-1/world-1-2000-20261011` (4,8 GB, xoá khi map mới ổn), map gốc 1:1000 `tools/earthmap/work/out/earth_1-1000` (17 GB, để reset mùa).

### Map Trái Đất 1:2000 cho banghoi-1 (09/10/2026)
- Người dùng không muốn mua map → **tự tạo** bằng WorldPainter 2.27.1 + script DerMattinger/MinecraftEarthMap (MIT). Công cụ và hướng dẫn: `tools/earthmap/` (`build-earth.sh`, `README.md`); file tải về và map tạo ra ở `tools/earthmap/work/` (không commit).
- Đã lắp vào `data/banghoi-1/world` (21.504 × 10.752 block, 4,2 GB). Thế giới tự nhiên cũ giữ ở `data/banghoi-1/world-tu-nhien-20261009`.
- Viền: **ChunkyBorder** (Modrinth `chunkyborder`) hình chữ nhật 10752 × 5376, viền vanilla nới tối đa. Spawn: Hà Nội (6323, 63, -1256). Đều là setting trên trang Cài đặt (`border_x`, `border_z`, `spawn_x`, `spawn_z`).
- Tạo 1:2000 cần ~8 GB heap: lần này đã tắt cả network DuckyMoMo ~1 giờ. **Người dùng yêu cầu (09/10): DuckyMoMo, tram6, goc6 phải chạy liên tục, không tắt cái nào.** Lần sau muốn tạo lại map phải có đủ RAM trước (nâng WSL/nâng RAM máy), không tắt dịch vụ.
- **Giảm RAM (09/10)**: lobby `INIT_MEMORY 512M / MAX_MEMORY 1G` (mem_limit 2g), banghoi-1 `1G / 3G` (mem_limit 4g), proxy `MEMORY 512M`. **Không dùng `USE_AIKAR_FLAGS`**: nó có `AlwaysPreTouch`, chiếm sẵn toàn bộ heap (banghoi-1 lên 3,6/4 GB, sát mem_limit). Kết quả: lobby 1,1 GB, banghoi-1 1,8 GB, WSL trống từ ~2 lên ~4 GB. Đông người thì tăng MAX_MEMORY và mem_limit.
- **Nâng RAM (11/10)**: máy lên **64 GB** (lắp thêm 2×16 GB, mã `UD848 CL18`; cả 4 thanh chạy 3466 MHz). `.wslconfig` (`C:\Users\linh_\.wslconfig`) đổi `memory=16GB` → `40GB` (bản cũ: `.wslconfig.bak-16GB-20261011`). banghoi-1: `MEMORY 6G` + `USE_AIKAR_FLAGS` (giữ sẵn cả heap), `mem_limit 8g`; lobby `MAX_MEMORY 2G`, `mem_limit 3g`; proxy `MEMORY 1G`; mẫu `_template-paper` `MAX_MEMORY 4G`, `mem_limit 6g`. **Chỉ có hiệu lực sau `wsl --shutdown` rồi `docker compose --profile tunnel up -d`** (không áp dụng khi WSL còn 16 GB: banghoi-1 giữ sẵn 6 GB sẽ làm WSL thiếu RAM). Sau khi WSL khởi động lại phải kiểm tra bind mount (xem "Sự cố 11/10"). **Đã áp dụng 11/10 ~15:00**: WSL 39 GB, banghoi-1 chạy `-Xms6G -Xmx6G` + AlwaysPreTouch (dùng ~6,8/8 GB), TPS 20, Windows còn trống ~27 GB; bind mount world và thư mục sao lưu đều ghi đúng ra ổ cứng.

### 20/10: đổi tên
- Quảng trường Vinh danh → **Quảng trường Danh vọng** (web, cẩm nang, trong game); lệnh mới /danhvong (vinhdanh vẫn chạy).
- Rank Media → **Sứ Giả** (tên + tiền tố trong Cài đặt, tin nhắn, cẩm nang, web, trang quản trị). Nhóm LuckPerms vẫn là `media`, khoá cài đặt rk_media_*/md_*, lệnh /media vẫn chạy; lệnh chính cho người chơi: /sugia.
- Trang chủ: mục Giới thiệu gameplay; ô "Các chế độ" thêm Thái Ấp Sáng Tạo (mở), Skyblock + Lifesteal (sắp có, người dùng từng nêu), Pokémon (dự kiến).
- Plugin đã build, CHƯA khởi động lại lobby/banghoi-1/practice-1/sangtao-1 (cần để /danhvong, /sugia và chữ mới trong game có hiệu lực).

### 20/10: trang quản trị đăng nhập bằng form
- Panel bỏ hộp thoại Basic Auth của trình duyệt (người dùng thấy như alert): chưa đăng nhập thì trang HTML trả form (LOGIN_HTML trong panel/app.py),
  API trả 401 JSON (không WWW-Authenticate). POST /dangnhap {ten, matkhau} = PANEL_USER/PANEL_PASSWORD -> cookie dmm_panel (HttpOnly, SameSite=Strict,
  Path theo X-Forwarded-Prefix = /mc/caidat, 12 giờ, giữ trong bộ nhớ: restart panel là đăng nhập lại). Sai 5 lần/10 phút/IP -> 429.
  POST /dangxuat; shell.js có nút Đăng xuất và tự tải lại trang khi API trả 401. Basic auth (curl -u) vẫn nhận cho script.
  Vẫn còn lớp đăng nhập Trạm 6 (require-login) phía trước: hai lần đăng nhập là cố ý (Cài đặt đổi được server qua RCON).
- 20/10: tắt `showCombatLevelDisplay` của EliteMobs (data/banghoi-1/plugins/EliteMobs/skills.yml, KHÔNG có trong repo/Cài đặt): dòng "Cấp Chiến Đấu N"
  trên đầu người chơi đè bảng tên và dễ nhầm với [Cấp N] của Thăng Cấp. Tiền trong tin nhắn DuckyMoMoCore dùng "4.000 xu" (VaultEconomy.format -> Rewards.xu),
  quà "vat" có tên Việt (Rewards.vi). Essentials vẫn để ký hiệu $ (chỉ cho 1 ký tự) nên /bal, /pay của Essentials còn hiện $.
- 20/10: KHÔNG dùng chữ in đậm (&l, §l, <bold>, <b>) trong chữ hiện cho người chơi: font tiếng Việt (Be Vietnam Pro TTF trong gói tài nguyên)
  vẽ đậm thành chữ nhòe như 3D. Đã bỏ toàn bộ bằng tools/bo-in-dam.py (repo + data/*/plugins + tiền tố LuckPerms; sao lưu trước khi sửa
  ở scratchpad phiên 20/10). Plugin mới cài / file dịch mới: chạy `python3 tools/bo-in-dam.py --ghi` (+ thư mục data của plugin đó khi server dừng).
  Guide.java giữ chuỗi "&l" (đo độ rộng chữ đậm). Nhấn mạnh bằng màu.
- 20/10: Tủ skin /tuskin chạy thật (proxy plugin DuckyMoMoSkin, cổng nội bộ 8095). Đã thử: thêm bằng tên bản quyền, ảnh PNG tải lên qua MineSkin, mặc, xoá, khoá sai 403.
- 20/10: VÀNG (tiền ủng hộ) thay cho mua rank thẳng bằng tiền. Nạp (web chọn số tiền / /rank mua) -> admin Duyệt ở trang Donate · Vàng
  = cộng Vàng (ví MariaDB network.vang_vi, sổ vang_log) -> người chơi tự mua ở /vang hoặc web (rank = giá donate-ranks ÷ tỉ giá, món lẻ vang.mon).
  Không chuyển Vàng, không đổi ra xu. Đơn kiểu cũ đang chờ cũng duyệt thành Vàng; rank đã cấp kiểu cũ giữ tới hết hạn.
  QR tự vẽ ở panel (segno, NAPAS) vì img.vietqr.io đổi sang SVG. QR trong game đặt thẳng vào tay, món đang cầm giấu trong bản đồ (PDC), cất là trả.
  Đã thử: cộng tay, mua mũ (quyền 30 ngày thật), mua rank, thiếu Vàng bị chặn, tài khoản chưa vào server bị chặn trước khi trừ, nạp tạo QR.
  CHƯA thử bằng người: menu /vang, /rank mua QR trên tay, thẻ Vàng trên trang tài khoản (cần đăng nhập /web).
- 20/10: THƯ VIỆN ĐỒ LÀ NƠI DUY NHẤT định nghĩa vật phẩm đặc biệt (người dùng: "boss thì chỉ cài số liệu boss, đồ drop chọn ở thư viện").
  Món có `nhom` (đầu mục) + `cong-dung`: `ky-nang` {feature, value, text, uses, days, pvp} (Workshop), `mon-an` + `hieu-ung` (Dishes),
  `bua-bat-pet` (PetCapture), `tinh-luyen` (Refining), `manh` (FragmentShop). ItemLibrary.build = hình dạng + dấu PDC `thuvien_id` + module
  đóng dấu riêng qua `ItemLibrary.STAMPERS`, nên món thư viện phát ở đâu (boss, phần thưởng `do <mã>`) cũng đủ công dụng.
  Mã Xưởng / món ăn / Tinh Luyện / loại mảnh = mã thư viện; config chỉ còn thông số riêng: workshop.items.<mã>{cost, grid, tinh-luyen, enabled},
  dishes.list.<mã>{need}, tinh-luyen.items.<mã>{recipe, sell}, event-fragments.types.<mã>{until}, pet-capture.charm-item.
  41 món cũ chuyển thành đồ mẫu `network-core/core-paper/src/main/resources/thuvien-mau.yml` (phien 1): banghoi bật lần đầu thì thêm món còn thiếu
  (chỉ server có mục workshop). Đổi mẫu thì TĂNG `phien`. Thư viện đọc lại khi file đổi (không cần nạp lại).
  Đồ rơi boss: module BossDrops (reflection EliteMobDeathEvent, CustomBossEntity -> tên file), file plugins/DuckyMoMoCore/boss-roi.yml
  {<file boss>: [{do, ti-le, so, cho: top1|top3|tat-ca|ngau-nhien}]}, sửa ở trang Boss › 6. Trang Thư viện: chia đầu mục, tìm, "Đang dùng ở";
  món đang dùng không xoá / không đổi công dụng được (panel lib_usage). Cài đặt: ô chọn món (libSelect) thay ô tự tạo đồ; kiểu mới `libitem`.
  NGOÀI phạm vi: loot riêng của EliteMobs, kit tân thủ Essentials. Shopkeepers đã đặt món Tinh Luyện cũ: món vẫn nhận theo mã (dmm_tinhluyen).
- 20/10 tối: tiền tố CHỈ HIỆN MỘT (người dùng báo lỗi hiện cả [Dân] lẫn rank): LuckPerms `meta-formatting.prefix.format: ["highest"]`
  ở lobby/banghoi/practice/sangtao/template (+ data). Thứ tự theo weight: Admin 100 > Manager 90 > Mod 70 > Helper 50 > Builder 40 >
  Sứ Giả 20 > rank ủng hộ 5–9 > Hào Kiệt 3 > Lương Dân 2 > Dân 1 > Tị Nạn 0. Ghi chú cũ "prefix ghép role + rank donate" (mục Role 12/10) KHÔNG còn đúng.
  Bỏ &l khỏi tiền tố 5 rank ủng hộ (DB + Cài đặt tier*_prefix); namtuoc trước đó mất prefix, đã đặt lại.
- 20/10 tối: KHUNG TOOLTIP CHUNG `Tip.java` (người dùng gửi ảnh CherryMC: "mô tả, sắp xếp đẹp, đừng đơn điệu"). Bố cục: tên có ký hiệu +
  (phụ đề), ▍ mô tả, vạch &m, "• Nhãn: giá trị", "• Nhóm:" + "▸ mục", ✔/✘ điều kiện, "➜ Bấm để …" / "✘ lý do". Đã đổi toàn bộ:
  /menu (nút thêm sub, gom, bam, %placeholder% qua PapiText; ô 4 = hồ sơ đầu người chơi), /kit, /xuong, /nauan, /tinhluyen, /doimanh,
  /capdo (+ cửa hàng, xếp hạng), /caidat, /treothuong, /cuahang, /donhang, /vongquay, /tpr, /danhphan, /vang + QR, menu sảnh, chìa/vé/mảnh
  dungeon, bản đồ kho báu, lore đồ Xưởng và món ăn. Tiêu đề menu: "&0<ký hiệu> Tên" (không viết hoa cả câu). Menu mới: dùng Tip.
  CHƯA làm: GUI plugin ngoài (zAuctionHouse /ah, Jobs, MyPet, CrazyEnchantments /ce, QuickShop) vì là cấu hình riêng của từng plugin.
- 20/10 tối: `/tphere` kéo người "ra chỗ khác": do admin dùng `/tprandom` = dịch chuyển ngẫu nhiên của ESSENTIALS (không giới hạn trong bản đồ,
  đưa tới X -19 triệu; world border 60 triệu). Sửa: data/banghoi-1/plugins/Essentials/config.yml `disabled-commands` thêm tprandom, randomtp,
  rtp, tpr (file chỉ có ở data, không có trong repo) + /tpr của DuckyMoMoCore nhận thêm alias tprandom, randomtp. Ngoài ra Essentials
  teleport-delay 3 giây áp cho cả admin: gõ /spawn rồi /tphere ngay thì người kia bị kéo tới chỗ CŨ của admin (đã đề xuất cấp
  essentials.teleport.timer.bypass cho staff, chờ người dùng đồng ý). Sự cố phụ: lỡ chạy `setworldspawn ~ ~ ~` qua RCON lúc 20:01:53,
  spawn giữ nguyên ô (12699, 65, -2523) nhưng hướng nhìn về 0.
- 20/10 tối (lộ trình test r4): `/ah` bị chặn cả ở spawn vì CHƯA lập Liên minh Hoà Bình / thị trấn KinhDo (Towny chưa có thị trấn nào) nên
  không có "đất hoà bình". Sửa: PeaceAlliance.spawnRing = vòng town-rules.reserved-spawn-radius quanh town-rules.spawn, /ah luôn mở trong vòng
  này. VIỆC CÒN: admin lập thị trấn KinhDo + quốc gia LienMinhHoaBinh ở spawn trước khi mở cửa (xem mục Liên minh Hoà Bình).
  r2 (lên Dân bằng chơi thật) không phải lỗi: người dùng chưa đủ thời gian; thử nhanh = hạ giờ ở Cài đặt › Danh phận + cấp quyền chương 1.
- 20/10 tối: KHUNG TOOLTIP cho GUI plugin ngoài (cùng kiểu Tip.java: ký hiệu + tên, ▍ mô tả, vạch &m, • nhãn, ▸ mục, ➜ bấm):
  zAuctionHouse (repo inventories/*.yml + patterns/back, pagination; data config.yml item-lore + status, CHỈ có ở data), UJobs config.yml (gui,
  expinfo, leaderboard), CrazyEnchantments config.yml (sửa bản data đã Việt hoá rồi chép đè repo; giá trong mô tả lấy đúng Costs; dòng mô tả
  sách cũng ghi vào settings.yml ce_*_lore), QuickShop overrides/vi_vn/messages.yml (gui.*), MyPet mô tả hệ (tools/mypet/sinh-cay-ky-nang.py
  hàm khung(), sinh lại 230 cây; site/app.py bỏ ký hiệu khung khi hiện web). Sửa kèm: nút tăng giá /ah ghi +5000 nhưng thật +500.
  Script đã dùng (ruamel, chạy trong container panel): scratchpad phiên cũ, không giữ. Đổi giá shop /ce trên Cài đặt KHÔNG tự đổi số trong mô tả.
- 20/10 tối: /cuahang làm lại kiểu chợ server (người dùng gửi ảnh CherryMC): decor-shop.categories {id, name, icon, slot, desc, sell[mẫu]},
  món bán có "cat:", danh mục thu mua lấy từ Essentials worth.yml (đọc lại mỗi lần mở). Trái mua / phải bán 1 lô / Shift+phải bán hết
  (p.performCommand("sell <vật liệu> [số]") của Essentials). Cài đặt: kiểu mới shopcats (decor_categories) + cột danh mục ở shopitems.
  CHƯA thử bằng người: bấm bán, phân trang, Bedrock (Bedrock vẫn chỉ mua như cũ).
- 20/10 khuya: Cửa hàng Kinh Đô = chợ server ĐẦY ĐỦ (người dùng gửi ảnh CherryMC): bảng giá sinh bằng tools/shop/bang-gia.py (10 danh mục,
  giá THEO TỪNG MÓN, mỗi lần mua 1 món, giá mua >= 4 lần giá bán lại; quặng/kim loại/đá quý chỉ bán; script ghi decor-shop.categories ở repo +
  data, decor_categories ở settings.yml và worth: của data/banghoi-1/plugins/Essentials/worth.yml). Món nằm TRONG danh mục (categories[].items),
  dòng mẫu "*_WOOL" mở ra mọi vật phẩm khớp trừ đồ đặc thù (DecorShop.DENY). 764 món. GOLD_NUGGET và *_POTTERY_SHERD KHÔNG có giá /sell
  (vật liệu nền Xu Quái / Tinh Luyện). Đổi giá: sửa bảng trong script rồi chạy lại, hoặc sửa trên Cài đặt › Shop (mỗi danh mục một khung).
  VIỆC CÒN: định lại xu toàn server theo bảng giá này (nghề, điểm danh, nhiệm vụ, vòng quay, gói quà, phí thị trấn, công thành…), chờ người dùng duyệt mốc.
- 20/10 khuya: sửa cửa hàng: (1) món sinh từ dòng mẫu trùng mã (offers.size() chưa tăng) -> bấm món nào cũng mua món đầu danh mục; nay bộ
  đếm nextKey. (2) Admin bán ×3: Essentials sell-multipliers double/triple + admin có "*" (essentials.sell.multiplier.triple); đã bỏ
  double/triple trong data/banghoi-1/plugins/Essentials/config.yml (file chỉ ở data). Essentials currency-symbol chỉ nhận 1 ký tự: giữ "$".
