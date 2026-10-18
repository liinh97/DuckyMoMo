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
- [ ] Vượt Đỉnh Vách Đá → nhận "Chìa khoá Hang Thánh Địa", tầng sau mở; báo cả server người đầu tiên.
- [ ] Nhóm (party EliteMobs): người chưa có chìa không vào theo được.
- [ ] /dungeon datan sewers_minidungeon ở chỗ giấu; người khác tới gần thấy hạt tím, tìm ra, vào được.
- [ ] /dungeon mua knights_castle_lair → vào mất 1 vé; mở cổng mà không vào 2 phút → hoàn vé.
- [ ] /dungeon cho north_pole_minidungeon <người> → Vé Vận Mệnh; dùng: 70% mở, trượt mất vé.
- [ ] /dungeon gop fireworks_lair → đủ quỹ mở 7 ngày; /dungeon mo (admin).
- [ ] Điều kiện: Chuông Địa Ngục đi 1 mình bị chặn (cần nhóm 2–4, /em party); Hoang Mạc Địa Ngục không dẫn pet cấp 10 bị chặn; Lâu Đài Hiệp Sĩ ngày thường bị chặn.
- [ ] Nhóm 2 người mở dungeon vé: trừ vé cả hai; người kia nhận thông báo và vào theo được.
- [ ] Thánh Đường Bóng Tối: tìm ra (ban đêm) → nhặt Mảnh Chìa (/dungeon cho dark_cathedral_lair <người> 3) → /dungeon vao ghép, vào được.
- [ ] /dungeon hiện ★ Dungeon của tuần; vượt nó nhận thêm 1500 xu (tối đa 3 lần/tuần).
- [ ] Vượt xong báo thời gian; phá kỷ lục báo cả server; web hiện kỷ lục.
- [ ] Kho báu huyền thoại (/khobau give, tới khi ra bậc huyền thoại): 40% có mảnh giấy chỉ đường tới dungeon ẩn.

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
