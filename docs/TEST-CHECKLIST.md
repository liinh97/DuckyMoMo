# Danh sách test trong game (gom 18/10/2026)

Chủ server muốn "làm hết tính năng rồi test một thể". File này gom những gì **đã code + đã nạp server nhưng chưa thử trong game**.
Đánh dấu `[x]` khi xong; lỗi thì ghi ngay dưới mục đó. Chi tiết kỹ thuật từng phần: `docs/HANDOFF.md`.

Chuẩn bị: 2–4 tài khoản (Java + Bedrock nếu được), 1 tài khoản admin (op). Nhiều mục cần **2 nước** để thử công thành:
bật công tắc vây thành, rút lịch phiên còn vài phút (Cài đặt › Chiến tranh › Lịch công thành), xong trả lại.

---

## 1. Pet chiến đấu (MyPet)
- [ ] Bắt pet: đánh con vật còn ≤ 10% máu bằng dây xích → thông báo trượt (khói, "còn N lần thử") hoặc bắt được. Hết 3 lần thì "con vật hoảng".
- [ ] Chờ 2 giây giữa 2 lần thử (đánh liền thì báo chờ, không tính lần thử). Lần thử trượt / đang chờ: con vật KHÔNG mất máu.
- [ ] Sói/mèo/ngựa ĐÃ thuần hoá của mình: bắt chắc chắn, không quay xác suất.
- [ ] Bùa bắt pet: `/buabatpet give <tên> 3` → cầm tay trái khi bắt: "+25%", mất 1 bùa mỗi lần thử.
- [ ] `/bxhpet tyle`: tỉ lệ đúng theo mùa/sự kiện (Lũ: Thủy tộc 70%). `/bxhpet`, `/bxhpet chienbinh` hiện pet.
- [ ] `/petchooseskilltree`: chỉ thấy 5 hệ; mô tả có "Nhóm loài: …". Đổi loài khác thấy mô tả nhóm khác.
- [ ] Lên cấp (`/mypetadmin exp <tên> 2000`) → tin "Pet đạt cấp 15…" ở mốc; `/petinfo` thấy kỹ năng mới (Hộ chủ, Dậm đất…).
- [ ] Tên kỹ năng tiếng Việt: Hộ chủ, Dậm đất, Héo mòn, Sét đánh, Chảy máu.
- [ ] Thú Cưỡi: loài bay (dơi, vẹt, blaze…) cấp 5 bay được (giữ phím nhảy trên không), hết quãng thì rơi không mất máu; ngựa thì KHÔNG bay.
- [ ] Pet đánh người: sát thương giảm 50%. Pet giết người → án/treo thưởng/điểm tính cho chủ.
- [ ] Cho pet ăn cá câu (chuột phải pet bằng cá EvenMoreFish): no, hồi máu, + kinh nghiệm; quá 300/ngày thì chỉ no + hồi máu.
- [ ] Trao đổi: `/pettrade <người> 100` → người kia `/pettrade accept` → trừ/cộng xu, pet sang chủ mới.
- [ ] Pet không nhận kinh nghiệm khi giết quái từ lồng quái (máy farm).
- [ ] Thanh máu trên đầu quái (HoloMobHealth), không đè chữ boss EliteMobs.
- [ ] Trang tài khoản web: pet hiện tên Việt, "Hệ · Nhóm loài", hạng #.

## 2. Đấu Trường (/warp pvp, /dautruong)
- [ ] NPC Đấu Trường ở spawn → tới sảnh PvP. Bước vào cổng → vào thẳng sân chờ; nút đổi 2v2, cược, thoát.
- [ ] Trận 1v1 thật: đếm ngược, đánh, chết không rơi đồ, chuyển xem; kết quả, ELO, chia cược.
- [ ] Thắng (còn trong 5 trận thưởng/ngày) → "+30 kinh nghiệm pet". Cùng IP → giao hữu, không thưởng.
- [ ] Không cưỡi pet được trong thế giới đấu trường. Không phá/đặt khối.

## 3. Công thành (cần 2 nước)
- [ ] Cắm cờ: quỹ chiến tranh tăng theo số thị trấn (thông báo "quỹ chiến tranh thêm"). Nước còn nợ phí thuộc địa → không cắm được.
- [ ] Phiên có/không pet theo lịch `/lichcongthanh`; phiên không cho pet → pet không gây sát thương cho người trong vùng vây.
- [ ] Cấm bay bằng pet trong vùng vây (cao hơn 3 khối → bị đẩy xuống, Rơi chậm).
- [ ] Thời tiết: đêm (giờ game) → Bóng tối trừ ai dẫn pet mắt đêm (được Nhìn đêm); mưa → đi bộ Chậm, cưỡi pet thì không;
      nắng gắt mùa khô → Đói trừ ai dẫn pet lửa. Tin nhắc khi trời đổi.
- [ ] Kho lương: `/kholuong noptat` chỉ nộp nông sản/đồ ăn (không lấy cá câu, món ăn); cầm cá/món rồi `/kholuong nop` thì nộp được; trong trận được nuôi no (thanh đói đầy), kho trừ 2 điểm/phút/người; kho cạn → Đói.
- [ ] Bên phòng thủ ít dân (bên kia gấp 2): điểm giữ cờ +15% (so số điểm SiegeWar báo với số mong đợi).
- [ ] Thắng → cướp tiền; thị trấn thua không đủ tiền → nợ trả dần 7 ngày (không phá sản).
- [ ] Chiếm thuộc địa: quá 3 thuộc địa thì không chiếm được; thông báo hạn khởi nghĩa (72 giờ trừ 12 giờ/thuộc địa khác).
- [ ] Phí thuộc địa trừ ngân khố nước mỗi ngày; thiếu thì ghi nợ (`/canbang`).
- [ ] Liên minh Hoà Bình không bao giờ thành Bá chủ, không làm chủ vùng đặc sản, không bị tính phí thuộc địa.
- [ ] Bá chủ: nước ≥ 30% thị trấn (≥ 5) → thông báo; đánh vào được hoàn 30% quỹ, +15% điểm; chỉ hưởng đặc sản 2 vùng.
- [ ] Điểm bảng xếp hạng: tấn công thắng +30, phòng thủ +20; Chiến Thần +3/hạ người có quân hàm, +5 chiếm cờ.

## 4. Mùa, câu cá, món ăn, đặc sản
- [ ] `/mua`: mùa, sự kiện (Lũ/Hạn hán), tốc độ lớn đã gộp sự kiện. `/mua sukien han` → thử Hạn hán; `/mua sukien tudong` trả lại.
- [ ] Gặt cây chín đúng mùa: thỉnh thoảng rơi thêm 1.
- [ ] Câu cá: cá theo mùa, cá biển; `/emf shop` bán; thi câu (hoặc `/emf admin competition start`) → hạng 1–3 nhận Bùa bắt pet.
- [ ] `/nauan`: nấu đủ 5 món (thiếu nguyên liệu báo rõ); ăn → hiệu ứng; ăn món khác trong 20 phút bị chặn.
- [ ] Trang Cài đặt › Món ăn ra trận › Các món: thêm/sửa món, chọn nguyên liệu (vật phẩm / cá), chọn hiệu ứng → Lưu → `/nauan` thấy ngay.
- [ ] `/dacsan`: chủ vùng sau khi có thị trấn; người phe chủ vùng gặt đặc sản ở đâu cũng thỉnh thoảng rơi thêm.

## 4b. Khai hoang, lính đánh thuê, thành tích, server vắng
- [ ] `/tpr` chưa hiện Mã Lai/Philippines/Borneo/Java; admin cộng quỹ đủ mốc (hoặc sửa need nhỏ để thử) → vùng hiện, thông báo, 10 người đầu
      tới nhận 2000 xu + danh hiệu [Tiên Phong]; `/dacsan` vùng mới có đặc sản.
- [ ] `/linhdanhthue dangky 500` (người không thuộc nước); vua `/linhdanhthue thue <tên>` → lính `/linhdanhthue nhan`: trừ ngân khố, lính nhận 450.
      Lính vào vùng vây không bị ốm, không đánh được người nước thuê, được ăn kho lương thủ đô nước thuê; hết 48 giờ thì hết quyền.
- [ ] `/thanhtich`: đếm đúng (câu cá, loài cá, quái, vùng, cấp pet, nấu món, nộp kho lương); đạt mốc nhận quà, mốc có danh hiệu báo cả server.
- [ ] Server dưới 15 người chơi/7 ngày: chỉ tiêu `/nhiemvu` còn 60%, `/bxhpet tyle` +10%.

## 4c. Kho báu, truy nã, Thái Ấp
- [ ] `/khobau give <tên>` → cầm bản đồ: lạnh/ấm/nóng; tới nơi Shift: lính canh ra; hạ hết → quà; rời xa/hết 5 phút → trả bản đồ.
- [ ] Câu cá / hạ quái thỉnh thoảng rơi bản đồ (tối đa 5/ngày). Kho báu huyền thoại báo cả server.
- [ ] `/truyna`: sau ~2 phút có lệnh đầu; tới gần thì quái phát sáng xuất hiện; người đánh nhiều nhất (kể cả pet) nhận thưởng; hết 60 phút thì trốn.
- [ ] `/thaiap mua` (5000 xu) → dịch tới mảnh 50×50 có viền lối mòn; người khác không phá/đặt/mở rương/đánh thú được; không PvP; không quái sinh ra.
- [ ] `/thaiap moi <tên>` → người đó xây được; `/thaiap ghe`, `/thaiap thich`, `/thaiap top`; `/thaiap morong` → viền to ra.
- [ ] Nấu `/nauan` trong Thái Ấp của mình: món có dòng "bếp Thái Ấp", ăn được hiệu ứng lâu hơn 50%.
- [ ] Không lập được thị trấn ở thế giới thaiap; pet MyPet gọi ra được ở đó.

## 4d. Cổng Dungeon (/dungeon), sau khi cài gói bằng /em setup
- [ ] Log banghoi-1: "Cổng Dungeon: đã gắn EliteMobs (x/18 gói đã cài)", x khớp số gói đã cài.
- [ ] Người thường (không op): menu /em vào dungeon bị báo không có quyền; /dungeon vao the_climb_dungeon vào được.
- [ ] Vượt Đỉnh Vách Đá → báo cả server người đầu tiên (chìa khoá: xem mục 4d+).
- [ ] Nhóm (party EliteMobs): người chưa có chìa không vào theo được.
- [ ] Thánh Đường Bóng Tối: server tự giấu lối vào (log "tự giấu lối vào … tại x y z"). Ban đêm tới gần thấy hạt tím, tìm ra. Đổi chỗ: /dungeon datan dark_cathedral_lair ngaunhien.
- [ ] /dungeon mua knights_castle_lair → vào mất 1 vé; mở cổng mà không vào 2 phút → hoàn vé.
- [ ] Cống Ngầm, Bắc Cực: /dungeon vao báo "chỉ qua Cổng Hoang"; /conghoang tao here rồi Shift ở ô giữa → vào được. (Vé Vận Mệnh: chưa có dungeon kiểu luck, bỏ qua.)
- [ ] /dungeon gop fireworks_lair → đủ quỹ mở 7 ngày; /dungeon mo (admin).
- [ ] Điều kiện: Chuông Địa Ngục đi 1 mình bị chặn (cần nhóm 2–4, /em party); Hoang Mạc Địa Ngục không dẫn pet cấp 10 bị chặn; Lâu Đài Hiệp Sĩ ngày thường bị chặn.
- [ ] Nhóm 2 người mở dungeon vé: trừ vé cả hai; người kia nhận thông báo và vào theo được.
- [ ] Thánh Đường Bóng Tối: tìm ra (ban đêm) → nhặt Mảnh Chìa (/dungeon cho dark_cathedral_lair <người> 3) → /dungeon vao ghép, vào được.
- [ ] /dungeon hiện ★ Dungeon của tuần; vượt nó nhận thêm 1500 xu (tối đa 3 lần/tuần).
- [ ] Vượt xong báo thời gian; phá kỷ lục báo cả server; web hiện kỷ lục.
- [ ] Kho báu huyền thoại (/khobau give, tới khi ra bậc huyền thoại): 40% có mảnh giấy chỉ đường tới dungeon ẩn hoặc Cổng Hoang chưa ai tìm.

## 4d+. Dungeon: chìa khoá, kỹ năng class, tiền
- [ ] Vượt Đỉnh Vách Đá → trùm cuối rơi "Chìa khoá Hang Thánh Địa" cho từng người; chuột phải / /dungeon vao: mất chìa, mở vĩnh viễn.
- [ ] key-tradeable false: đưa chìa cho người khác → họ không dùng được. Bật true → dùng được.
- [ ] Kỹ năng class EliteMobs ngoài dungeon (Shift + F hai lần ở world chính): có dùng được trong PvP/công thành không → quyết tắt
      allowClassAbilitiesOutsideEliteMobsWorlds.
- [ ] Đo xu rơi một lượt dungeon (trước/sau /bal) để xem có in tiền quá tay không (EconomySettings currencyShowerTierMultiplier 0.3).

## 4e. Cổng Hoang (/conghoang)
- [ ] /conghoang tao here: cổng dựng đúng kiểu biome, không phá được khối; /conghoang ds thấy toạ độ.
- [ ] Người thường tới gần thấy hạt tím + dòng nhắc; Shift ở ô giữa: tìm ra (báo server), mở menu dungeon.
- [ ] Trong 30 phút ưu tiên: người khác không lập/mở rộng thị trấn gần cổng; người tìm ra lập được.
- [ ] Thị trấn giữ cổng: dân vào miễn phí; người ngoài thấy nút trả phí, tiền vào ngân khố; /conghoang dong chặn được.
- [ ] Bị chiếm (SiegeWar): vua nước chiếm chỉnh được phí, dân nước chiếm vào miễn phí.
- [ ] /conghoang datlai gỡ sạch khối, trả lại địa hình cũ.

## 4f. Xưởng chế tạo (/xuong) — làm lại 19/10
- [ ] /xuong: menu 20 món; bấm món → bảng chế 3×3 đúng nguyên liệu; đủ đồ + xu thì chế được, thiếu thì báo.
- [ ] Lấy nhanh để thử: /xuong tang <mã> [người] (khoan_dao, dao_ham, xeng_dat, riu_tieu, tu_nung, da_nang, gat_lua, nam_cham, ban_ruong,
      tui_hu, nuoc, dung_nham, bat_thu, moc_bay, khien_than, cung_mau, no_toa, bua_ta, kiem_nhat, riu_chien).
- [ ] Khoan Đào 3×3 / Đào Hầm 5×5 / Xẻng 3×3: đào đúng mặt; không đào được trong thị trấn người khác (Towny); độ bền trừ theo số khối.
- [ ] Rìu Tiều chặt cả cây; Tự Nung ra thỏi; Đa Năng tự đổi cuốc/rìu/xẻng; Gặt Lúa gặt 3×3 + gieo lại bằng hạt trong túi.
- [ ] Hút Đồ (để trong túi) hút đồ quanh 16 khối, không hút đồ người khác vứt / quanh chỗ người chết.
- [ ] Rìu Bán: đánh vào rương của mình → bán theo giá /sell, tiền vào ví; rương người khác (không quyền) → bị chặn.
- [ ] Bắt Thú: bò/cừu thành trứng; không bắt được thú có chủ, pet, quái.
- [ ] Móc Bay kéo bay; Khiên Thần giảm 50% + phản đòn; Kiếm Nhật lướt chém (hồi 4 giây); Rìu Chiến chí mạng + chảy máu; Cung Máu hồi 1 tim.
- [ ] Món "mất tác dụng khi công thành" không dùng được trong vùng vây đang đánh.
- [ ] Phù phép CE (thử thêm ce:Blast:1 cho một món trên trang Cài đặt): món chế ra có dòng CE và dùng được.

## 4g. Cài đặt cá nhân (/caidat) — 19/10, cần 2 người
- [ ] /caidat mở menu 19 ô; bấm đổi Bật/Tắt; /caidat chat tat chạy.
- [ ] Tắt "Kênh chat chung": không thấy chat người khác, vẫn thấy tin nhắn riêng và thông báo.
- [ ] Tắt "Tin nhắn riêng" / "Nhận TPA" / "Nhận TPAHere": người kia /msg, /tpa, /tpahere bị chặn kèm câu báo; staff (duckymomo.caidat.bypass) vẫn gửi được.
- [ ] "Kêu khi bị gọi tên": người kia nhắc tên trong chat → có tiếng ting.
- [ ] Tắt "Lọc từ tục": mình thấy bản gốc, người khác vẫn thấy bản đã lọc (thử ở sảnh và Bang Hội Chiến: hai kiểu định dạng chat khác nhau).
- [ ] Tắt "Vào / rời", "Tử vong", "Dọn rác", "Sự kiện", "Khung nhắc lúc vào": không còn thấy các dòng đó.
- [ ] Tắt "Bảng bên phải", "Thanh mẹo trên cùng": ẩn ngay; vào lại vẫn giữ.
- [ ] Tắt "Hiệu ứng hạt người khác", "Máu quái trên đầu" (CHƯA CHẮC nhất: điều khiển PlayerParticles / HoloMobHealth qua reflection).
- [ ] Practice: tắt "Lời mời đấu / nhóm" → /duel <tên>, /party invite <tên> bị chặn.
- [ ] Bật "Tự gọi pet": vào Bang Hội Chiến pet tự ra; "Nhắc điểm danh": sau ~1 giờ có nhắc nếu chưa điểm danh.
- [ ] "Nhắc giờ công thành": còn 15 phút tới phiên đánh thì người có thị trấn bị vây / nước đi vây nhận nhắc.
- [ ] Trang tài khoản (web): thẻ "Cài đặt cá nhân" bấm đổi → áp dụng ngay trong game (đang online).

## 4h. Thăng Cấp (/capdo) — 19/10
- [ ] /capdo mở menu: cấp hiện tại, điều kiện cấp sau (xu / hạ gục / giờ chơi) đỏ-xanh đúng; đủ thì bấm lên cấp, trừ xu, +1 điểm.
- [ ] Hạ quái +1, hạ người +10; quái từ lồng không tính; giết lại cùng một người trong 10 phút không tính.
- [ ] Cửa hàng: đổi /craft, /disposal, /anvil, /hat dùng được ngay; "Giảm 5% Xưởng" làm giá /xuong rẻ hơn; danh hiệu Lão Luyện nhận được.
- [ ] Cấp 10: quà mốc + báo cả server. Tab hiện "[Cấp N]". Web: top 10 trang chủ, thẻ Thăng Cấp ở trang tài khoản.
- [ ] Admin thử nhanh: /capdo dat <tên> <cấp>.
- [ ] Mùa mới (đổi leaderboard.season-start, keep-on-new-season false): cấp, điểm về 0, quyền đã mua bị gỡ.

## 4i. Cốt truyện (BetonQuest + Citizens) — 19/10, đã triển khai (log sạch: 36 actions, 3 conversations, 3 npcs), CHƯA đặt NPC
Triển khai: kiểm tra không ai online (lệnh riêng) → `docker compose up -d --no-deps banghoi-1` (tải Citizens + BetonQuest mới) → `./ops/huongdan.sh`
→ `docker compose up -d --build panel site`. Log: `docker compose logs banghoi-1 --since 5m | grep -iE "Citizens|BetonQuest|cotruyen|ERROR|Exception"`.
- [ ] Log: Citizens và BetonQuest bật, không lỗi gói `cotruyen` (sai cú pháp thì BetonQuest ghi rõ dòng nào). `/bq reload` không báo lỗi.
- [ ] Đặt NPC ở spawn Bang Hội Chiến, gõ ĐÚNG tên: `/npc create Già Làng`, `/npc create Bác Thợ Rèn`, `/npc create Người Gác Cổng` (+ `/npc lookclose`).
      Chuột phải từng NPC → mở hội thoại (NPC tìm theo tên có dấu cách: nếu không mở, xem log, có thể phải đổi sang số ID: `citizens <id>` trong package.yml).
- [ ] Bảng hội thoại kiểu "dialog": Java 1.21.6+ hiện bảng có nút; **Bedrock** (Geyser) hiện form bấm được; Java bản cũ (ViaBackwards) ra sao?
      Lỗi thì Cài đặt › Cốt truyện đổi sang "Rương bấm chọn".
- [ ] Đi hết chương 1: nhận việc → hạ 20 quái (thông báo tiến độ mỗi 5 con) → về Già Làng nhận 300 xu → mang 16 sắt cho Thợ Rèn (thiếu sắt thì NPC báo thiếu)
      → đào 24 quặng (quặng tự đặt không tính) → nhận Mảnh Sao Rơi → Người Gác Cổng lấy mảnh → về Già Làng: thưởng 1500 xu + 200 kinh nghiệm, cả server thấy thông báo.
- [ ] Số lấy từ cài đặt (`%constant…%`) hiện đúng trong lời thoại và nhật ký; đổi số trên Cài đặt › Cốt truyện → `bq reload` → NPC nói số mới.
- [ ] `/journal` ra sách nhật ký tiếng Việt; vứt Mảnh Sao Rơi → nằm trong `/backpack`; Người Gác Cổng nhắc /backpack khi không có mảnh.
- [ ] Làm lại cho một người: `/bq tag <tên> list`, `/bq tag <tên> del c1_…`.
- [ ] Web: trang chủ mục Dungeon & Boss có thẻ Cốt truyện (các bước, số người hoàn thành); trang tài khoản có thẻ "Chương 1" đúng bước đang làm.
- [ ] `/huongdan` chương Bang Hội Chiến có trang CỐT TRUYỆN.

## 5. Quảng trường Vinh danh (sảnh)
- [ ] (CHƯA CẦN, làm khi gần mở server) Chọn chỗ trống ở sảnh (map sakura). Đứng, quay mặt về phía người xem: `/vinhdanh dat moinhat tieude`, rồi `bachu`, `chienthan`, `phuho`,
      `kientrucsu`, `dautruong`, `pet` ở các chỗ khác. Bảng chữ nổi + đầu người chơi đúng hướng, không đè nhau.
- [ ] `/vinhdanh xoa` bỏ đúng điểm gần nhất; `/vinhdanh tp` tới quảng trường.
- [ ] Bang Hội Chiến `/vinhdanh tam` → sảnh tự cập nhật trong 1 phút. Web mục Vinh danh khớp.
- [ ] Đầu người chơi: tài khoản bản quyền có da đúng; không bản quyền ra mặt mặc định (chấp nhận được?).

## 6. Web
- [ ] Trang chủ (máy tính + điện thoại): thanh trên cùng, menu, mọi mục tải dữ liệu (Pet, Đấu Trường, Công thành, Mùa, Vinh danh).
- [ ] Trang tài khoản: thẻ "<thị trấn>: hậu cần & chiến tranh" (kho lương, thuộc địa, nợ, Bá chủ) đúng số liệu; chức vụ tiếng Việt.
- [ ] Trang tài khoản: đăng nhập `/web`, thẻ pet, Đấu Trường, trang trí (pet trang trí chỉ liệt kê loài), nút quản trị.
- [ ] Trang quản trị: Role (chỉ thấy role/rank được quản), nút ⓘ, Cài đặt (các nhóm mới), Thư viện đồ.

## 7. Cũ hơn, vẫn chưa thử (xem HANDOFF)
- [ ] Liên minh Hoà Bình, Hội đồng, Hội Bàn Tròn, tổ chức/giấy phép, án giết dân hoà bình.
- [ ] `/tpr` (chờ 3 giây, đứng yên), `/choilai`, tài khoản phụ, quân hàm cần 5 giờ chơi.
- [ ] Điểm danh, nhiệm vụ hằng ngày, vòng quay, đổi mảnh (`/doimanh`, giá bằng cá), cửa hàng Kinh Đô, xưởng chế tạo.
- [ ] Boss / sự kiện "Trái Đất lâm nguy" (EliteMobs), chia thưởng.
- [ ] Client cũ (1.8–1.20) và Bedrock: vào được, AuthMe, form.
