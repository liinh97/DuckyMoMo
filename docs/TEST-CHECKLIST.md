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

## 4c. Kho báu, lệnh săn, Thái Ấp
- [ ] `/khobau give <tên>` → cầm bản đồ: lạnh/ấm/nóng; tới nơi Shift: lính canh ra; hạ hết → quà; rời xa/hết 5 phút → trả bản đồ.
- [ ] Câu cá / hạ quái thỉnh thoảng rơi bản đồ (tối đa 5/ngày). Kho báu huyền thoại báo cả server.
- [ ] `/lenhsan` (tên cũ /truyna): sau ~2 phút có lệnh đầu; tới gần thì quái phát sáng xuất hiện; người đánh nhiều nhất (kể cả pet) nhận thưởng; hết 60 phút thì trốn.
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
- [ ] Làm lại cho một người: `/bq tag <tên> list`, `/bq tag <tên> del cotruyen>c1_…` (tag ghi kèm tên gói, dấu >).
- [ ] Web: trang chủ mục Dungeon & Boss có thẻ Cốt truyện (các bước, số người hoàn thành); trang tài khoản có thẻ "Chương 1" đúng bước đang làm.
- [ ] `/huongdan` chương Bang Hội Chiến có trang CỐT TRUYỆN.

## 4j. Lữ khách (/lukhach) — 19/10, đã triển khai, CHƯA thử trong game
- [ ] Log banghoi-1: "Lữ khách: 4 lữ khách…"; khoảng 30 giây sau khi bật có 4 dòng "Lữ khách <tên> -> x y z (vùng)"; cả server thấy tin đồn 📜.
      Sảnh + practice vẫn có "Enabling DuckyMoMoCore", không NoClassDefFoundError (lớp Citizens chỉ ở CitizensBridge).
- [ ] `/lukhach` (người chơi): 4 người, đang ở vùng nào. `/lukhach ds` (admin): toạ độ + [Tới] → tới nơi thấy NPC đúng tên, quay mặt theo mình.
- [ ] Chuột phải lữ khách → hội thoại (cùng kiểu bảng với chương 1). Chưa mở mùa: hỏi "Nơi đó ở đâu?" → "Cánh cổng … chưa mở ra".
- [ ] Có cổng (thử: `/conghoang tao` đặt cổng; kiểm `/papi parse me %dmmlk_cong_cong%` = co, `%dmmlk_cong_tindon%`): lữ khách của đúng nơi đó đọc tin đồn;
      có người tìm ra → nói ai tìm, ở vùng nào; thị trấn giữ cổng → nói tên thị trấn.
- [ ] Đi qua Cổng Hoang → chat "… hẳn sẽ muốn nghe chuyện …", `/bq tag <tên> list` có `cotruyen>lk_<mã>_qua` → về lữ khách kể → +1000 xu, nhật ký "ĐÃ KỂ"; nói lại thì chỉ câu kết.
- [ ] Khởi động lại Bang Hội Chiến: log có "Citizens saves.yml: sửa kiểu NPC của N NPC" và "[Citizens] Loaded N NPCs" (N > 0), KHÔNG có
      "Lữ khách … ->" mới (lữ khách giữ chỗ cũ); NPC chương 1 tạo bằng /npc create vẫn còn.
- [ ] `/lukhach doi all` dời ngay + tin đồn mới; NPC cũ biến mất khỏi chỗ cũ (không bị nhân đôi). Restart server: NPC vẫn đúng chỗ, không tạo thêm.
- [ ] Cài đặt › Cốt truyện › Lữ khách: đổi giờ dời/thưởng → lưu không lỗi. Web: trang chủ (Cốt truyện › Lữ khách), trang tài khoản thẻ 🧭 Lữ khách.
- [ ] (Lưu ý) Người Gác Cổng (chương 1) giờ nhắc /lukhach.

## 4k. Kỹ năng nghề AuraSkills (/skills) — 19/10
- [ ] Log banghoi-1: AuraSkills 2.4.0 bật, không lỗi đọc file (skills/abilities/mana_abilities/stats/rewards/loot); "Kỹ năng nghề: làm lại mỗi mùa".
      `/skills` chỉ có 6 kỹ năng (Trồng trọt, Đốn gỗ, Đào mỏ, Câu cá, Đào đất, Pha chế), chữ tiếng Việt hết (menu, khả năng, /skills top, thông báo lên cấp; còn sót tiếng Anh thì chụp lại), trần 20.
- [ ] Đào quặng/chặt gỗ/trồng/câu cá lên kinh nghiệm; đặt rồi phá lại khối không lên; trong vùng spawn và Thái Ấp không lên; AFK máy câu không lên.
- [ ] Thử nhanh: `/skills skill setlevel <tên> mining 20` → nhận đủ thưởng 200/400/700/1200 xu? (setlevel có phát thưởng mốc không: kiểm), `/stats` Sức mạnh +1.
      Max cả 6: máu thêm đúng 1 tim, sát thương +2%. Không có chí mạng.
- [ ] Khả năng mana (cấp ≥10): chuột phải cầm rìu rồi chặt gỗ → đốn cả cây ~5 giây, hồi chiêu 5 phút; cuốc → đào nhanh; xẻng → xúc đất; cuốc đất → tự gieo.
      Bedrock dùng được không (chạm giữ?). Không có khả năng mana đánh nhau.
- [ ] Đấu Trường (`dautruong`): vào trận máu đúng 10 tim, sát thương không cộng; ra khỏi Đấu Trường máu trở lại như cũ.
- [ ] Câu cá: vẫn ra cá EvenMoreFish, không ra thêm đồ "rare/epic" của AuraSkills; đào đất không rơi đồ lạ.
- [ ] Cài đặt › Bang Hội Chiến › Nghề › Kỹ năng nghề: đổi trần (vd 25), thưởng cấp 5 → lưu không lỗi, `/skills` thấy số mới (thưởng: xem file rewards/*.yml đổi đủ 6 file).
- [ ] Mùa mới (đổi leaderboard.season-start): người vào game được báo "kỹ năng nghề đã làm lại", `/skills` về 0, máu/sát thương cộng thêm biến mất.
- [ ] Web: trang chủ thẻ ⚒ Kỹ năng nghề (danh sách, thưởng, top 10); trang tài khoản thẻ Kỹ năng nghề đúng cấp.

## 4l. Việt hoá toàn server — 19/10
Đổi ngôn ngữ máy sang English rồi thử, vẫn phải thấy tiếng Việt (Towny, SiegeWar, QuickShop, SkinsRestorer, Essentials đều ép tiếng Việt).
- [ ] Essentials: /tpa, /home, /warp, /bal, /pay, /msg, /kit, bị cấm chat… tiếng Việt; Bang Hội Chiến + Practice.
- [ ] Towny/SiegeWar: /t, /n, /res, /plot, thông báo vây thành, Ốm Chiến Tranh; trang quản trị › Hệ thống vẫn hiện RAM/uptime (panel đọc gc tiếng Việt).
- [ ] QuickShop: tạo shop rương, mua bán, menu /qs browse (nếu tiếng Anh: khởi động lại banghoi-1 rồi thử lại).
- [ ] zAuctionHouse /ah: chợ, đăng bán, xác nhận, đồ hết hạn, lịch sử, tuỳ chọn; mô tả món (Người bán, Giá, Hết hạn sau…).
- [ ] Shopkeepers: mua bán với người bán ở spawn; tự mở shop (vật phẩm tạo shop) → trình sửa tiếng Việt; ShopkeepersAddon menu tìm shop.
- [ ] EvenMoreFish /emf: menu chính, bán cá, nhật ký, thi câu; CrazyEnchantments /ce: tên phù phép trên đồ MỚI tiếng Việt (đồ cũ có thể còn dòng
      tiếng Anh tới khi CE vẽ lại), cuộn giấy, bụi, thợ rèn, thợ tái chế. TradePro /trade. AuraSkills /skills.
- [ ] Practice: Duels (/duel, /party, hàng chờ, /kit, bộ đồ tự tạo, xem lại trận) tiếng Việt, Bedrock dùng menu form tiếng Việt.
- [ ] Sau khi khởi động lại proxy (nhớ tạo lại relay): màn kiểm tra chống bot Sonar, /skin (SkinsRestorer), tin Geyser cho Bedrock.
- [ ] Sau khởi động lại: SimplePets (/pet), PlayerParticles (/pp).
- [ ] WorldGuard: phá khối ở spawn → "Khu an toàn! Ở spawn không làm việc này được."

## 4m. Cẩm nang mới (/hd) + web mục Lệnh — 19/10 tối
- [ ] Máy tính: `/hd` mục lục vừa 1 trang (11 chương, dòng "Bấm tên chương" không bị cắt), bấm từng chương nhảy đúng trang; trang "(tiếp)" đọc liền mạch.
- [ ] `/hd pet` báo đúng số trang; sảnh mở mặc định chương Bắt đầu, Bang Hội Chiến chương Bang Hội: cơ bản, Practice chương Practice.
- [ ] Điện thoại (Bedrock): bảng 11 nút, nội dung từng chương không lỗi màu.
- [ ] (HOÃN, người dùng 19/10: sửa giá/phí/lịch rồi chỉnh sau) Đọc lướt số liệu trong sách có khớp thực tế không (giá thị trấn, lịch công thành T7/CN 20:00–21:00, phí tổ chức, Thái Ấp…).
- [ ] Web: thanh lịch mùa 13 ô; mục "Lệnh hay dùng" trên điện thoại; thẻ Tổ chức có "Sự kiện sắp diễn ra" (thử `/sukien tao 5 Thử` khi có tổ chức có giấy phép).
- [ ] (HOÃN, người dùng 19/10: "từ từ bổ sung sau") Voice chat: máy tính cài mod Simple Voice Chat, vào mc.goc6.io.vn, phím V báo đã kết nối → sửa camnang mục Simple Voice Chat (bỏ "CHƯA BẬT").

## 4n. Thái Ấp Sáng Tạo (sangtao-1) — 19/10, đã triển khai, CHƯA thử bằng người
- [ ] Có Thái Ấp: `/thaiap sangtao` → đứng trước cổng tím; bước vào → sang sangtao-1, chế độ sáng tạo, đứng đúng mảnh đất của mình (cùng cỡ).
- [ ] Người được mời (`/thaiap moi`) qua cổng → vào mảnh đất của chủ, xây được; người lạ bước vào cổng → bị chặn, đẩy ra trước cổng.
- [ ] `/server sangtao-1` không qua cổng → báo và về sảnh. Sảnh: menu la bàn KHÔNG có Thái Ấp Sáng Tạo.
- [ ] Ở sangtao-1: không phá được đất người khác, không ném đồ, TNT không nổ, trứng sinh vật dừng ở 40 con; chat có prefix rank.
- [ ] `/thaiap ve` → về Bang Hội Chiến, đứng trước cổng (không bị chuyển ngược), túi đồ sinh tồn còn nguyên, không có đồ sáng tạo.
- [ ] Điện thoại (Bedrock) qua cổng và về được.
- [ ] Mở rộng / đặt tên ở Bang Hội Chiến → bên sáng tạo đổi theo trong 30 giây.

## 4o. Bedrock NetherNet (bắt buộc từ 27/10) — 19/10, đã bật, CHƯA thử bằng điện thoại
- [ ] Điện thoại bản Bedrock 26.40+ vào mc.goc6.io.vn cổng 19132: hiện hỏi "tin tưởng máy chủ" lần đầu → đồng ý → vào được, chơi mượt (WebRTC UDP 19133).
- [ ] Điện thoại bản cũ hơn vẫn vào (RakNet) tới khi cập nhật. Sau 27/10 thử lại với bản 26.60.
- [ ] Log proxy khi người Bedrock vào: không lỗi signaling/WebRTC (`docker logs duckymomo-proxy-1 | grep -i -E "nethernet|webrtc|signal"`).

## 4p. Bảo mật + hiệu năng — 19/10
- [ ] Sai mật khẩu 3 lần → hiện yêu cầu /captcha <mã>, gõ đúng thì login tiếp được.
- [ ] /changepassword sang mật khẩu "123456" → bị từ chối (mật khẩu cấm).
- [ ] Điện thoại (Bedrock) vẫn tự đăng nhập như cũ.
- [ ] Linh vẫn dùng được lệnh admin ở sảnh sau khi bỏ OP (/lp, /dmm, /gamemode…).
- [ ] Sáng Tạo: lấy đồ thường trong túi sáng tạo bình thường; đồ chế (lệnh /give của client mod, NBT editor) bị đổi thành bản trơn.
- [ ] Bang Hội Chiến: mạch redstone thường (cửa, đèn, máy gặt) chạy đúng; TNT nổ phá như cũ; đồ rơi gần nhau gộp lại.
- [ ] Vào lại trong 1 giờ từ cùng mạng: khỏi /login; quá 1 giờ hoặc đổi mạng (wifi ↔ 4G): phải /login.
- [ ] /web lấy mã, để 10–30 phút rồi mới đăng nhập web: vẫn được (mã sống 60 phút).
- [ ] Sao lưu ngoài máy: sau khi cấu hình ./ops/saoluu-ngoai.sh, `./ops/saoluu-ngoai.sh xem` thấy dung lượng trên mây ≈ kho ở F:.
- [ ] Lúc đông người: `/spark tps` (mspt < 50), nếu lag thì chạy profiler (hỏi trước khi tải báo cáo lên spark).

## 4q. Tinh Luyện (/tinhluyen) — 19/10
- [ ] /tinhluyen: menu hiện 12 món, đủ/thiếu tô xanh/đỏ; mang 64 khối than → bấm → nhận 1 Tinh Luyện Than; Shift+bấm với 128 khối → 2.
- [ ] Không đặt / không bỏ vào bàn chế, đe, bàn rèn được món tinh luyện; đổi tên bằng đe không được.
- [ ] /sell hand cầm món → bán đúng giá (bảng /tinhluyen ds); /sell all bán cả món tinh luyện lẫn đồ thường; /tinhluyen ban tatca.
- [ ] Giết quái thường ~20 con → vài Xu Quái; quái từ lồng quái không rơi; quái EliteMobs rơi nhiều hơn.
- [ ] Shopkeepers: /tinhluyen give kimcuong 1 → đặt trade (vd 1 Tinh Luyện Kim Cương → đồ X) → người chơi khác đổi được bằng món tự tinh luyện.
- [ ] Xưởng: thêm "sat:1" ở 1 món trên Cài đặt → lưu → /xuong thấy "Cần thêm 1x Tinh Luyện Sắt", thiếu thì không chế được.
- [ ] Điện thoại (Bedrock): menu /tinhluyen bấm được.

## 4r. Danh phận + Balo — 19/10
- [ ] Người mới vào Bang Hội Chiến: tiền tố [Tị Nạn]; /sethome cái thứ 2 bị từ chối (1 nhà); /ah sell được 2 món, món thứ 3 bị chặn; /balo báo chưa có.
- [ ] /danhphan: 4 thẻ, yêu cầu ✔/✘ đúng (giờ /hoatdong, cấp /capdo, chương 1). Thử nhanh: /danhphan dat <tên> dan → tiền tố [Dân], 2 nhà, 1 balo, 4 món.
- [ ] Xong chương 1 (Già Làng) → có quyền duckymomo.cotruyen.c1 (/lp user <tên> permission check duckymomo.cotruyen.c1).
- [ ] Đủ 6 giờ + chương 1 → bấm Dân trong menu → cả server được báo.
- [ ] /balo: để đồ, thoát game, vào lại còn nguyên; đang đánh người không mở được; /choilai mất đồ balo; admin /balo xem <tên>.
- [ ] Rank Sứ Giả: /danhphan có thẻ ◆ Sứ Giả (yêu cầu + bấm hiện cách liên hệ); /lp user <tên> parent add media → tiền tố, 5 nhà, 5 balo,
      5 món chợ, 5 đơn /donhang, vé vòng quay 48 phút, /kit media (3 ngày sau mới nhận lại).
- [ ] Trang Cài đặt › Người mới › Danh phận: đổi số nhà Dân thành 3 → Lưu → người bậc Dân đặt được nhà thứ 3 (không gõ lệnh lp).

## 4s. Chương trình Sứ Giả (/sugia) — 19/10
- [ ] Streamer: /sugia dangky https://www.tiktok.com/@<kênh> → trang quản trị › Sứ Giả tab "Chờ duyệt" có đơn, admin online được báo.
- [ ] Đang live TikTok thật: /sugia live → cả server thấy "🔴 LIVE … [Bấm để xem]"; sau 10–15 phút trang Sứ Giả có phút "Xác minh" tăng
      (nếu chỉ tăng "Chưa xác minh" = TikTok chặn/đổi API → báo Claude). Tắt live trên TikTok → ~30 phút sau buổi tự kết thúc.
- [ ] Bấm Duyệt → người đó có tiền tố ◆ Sứ Giả + quyền lợi; /lp user <tên> parent info thấy media có thời hạn 14 ngày. Gia hạn / Gỡ chạy đúng.
- [ ] Đứng AFK trong lúc live → phút không tăng. Thoát game → buổi kết thúc. /sugia baocao hiện ở cuối trang Sứ Giả.

## 4t. 20/10: Thư viện đồ trung tâm, Vàng, tủ skin, đăng ký — đã triển khai, CHƯA thử bằng người
- [ ] Trang Thư viện đồ: nhóm theo đầu mục, tìm món, sửa công dụng; xoá món đang dùng phải bị chặn.
- [ ] Xưởng /xuong: chế Khoan Đào → đào 3×3, độ bền giảm, có dòng hạn; Kiếm Nhật lướt chém; Khiên Thần giảm sát thương.
- [ ] /nauan: nấu Cơm nắm, ăn ra Máu ảo; chờ 20 phút mới ăn món khác. Admin `/dmm laydo canh_chua` rồi ăn cũng ra hiệu ứng.
- [ ] /buabatpet give <tên> 2 → bùa cầm tay trái khi bắt pet +25%.
- [ ] /tinhluyen: menu, tinh luyện, bán; Xu Quái rơi khi giết quái; shop dân làng còn nhận món Tinh Luyện cũ.
- [ ] /doimanh give <tên> lamnguy 5 → mảnh ra đúng tên, đổi được quà.
- [ ] Trang Boss: thêm đồ rơi (vd kiem_nhat 100%, người đánh nhiều nhất) → gọi boss → đánh chết → nhận đồ + tin nhắn "Đồ rơi từ boss".
- [ ] Cài đặt: Xưởng / Món ăn / Mảnh / Bùa chọn món từ thư viện, lưu được.
- [ ] Vàng: /vang (bấm 2 lần mua), /rank mua → QR hiện trên tay không tốn ô, cất là trả món cũ; web thẻ Vàng; trang Donate duyệt = cộng Vàng.
- [ ] Tủ skin /tuskin + web (tải PNG); dân tị nạn không đổi được skin.
- [ ] Đăng ký: tài khoản thứ 3 cùng IP bị chặn; tên trong whitelist thì không; captcha bản đồ Sonar khi vào lần đầu.
- [ ] /caidat Font tiếng Việt bật/tắt đổi gói tài nguyên; /menu (Shift+F) đủ nút; không còn chữ đậm nhòe.

## 5. Quảng trường Danh vọng (sảnh)
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
