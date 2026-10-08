# Thiết kế chế độ Bang Hội Chiến

> Bản nháp 08/10/2026. **Nền tảng đã chốt: Towny + SiegeWar.**
> Mọi con số đánh dấu **(đề xuất)** đều chưa chốt; chỉnh sau khi chơi thử với nhóm nhỏ.
> Định hướng gốc: `docs/minecraft-server-plan.md` mục 3. Ghi chú kỹ thuật: `docs/HANDOFF.md`.

---

## 1. Cảm giác chơi muốn đạt

- **Xây dựng là chính, đánh nhau có hẹn.** Ngày thường xây thành, làm kinh tế, ngoại giao. Cuối tuần mới công thành.
- **Không mất trắng lúc offline.** Thành không bị phá, không bị trộm khi chủ không online. Chỉ mất đất/tiền qua một cuộc vây thành có lịch rõ ràng.
- **Có lý do để lập nhóm.** Một mình sống được (thị trấn nhỏ, hoà bình), nhưng muốn mạnh phải liên minh.
- **PC và điện thoại chơi được cùng nhau.** Không có cơ chế bắt buộc thao tác khó trên điện thoại (pháo TNT, redstone phức tạp).
- **Mỗi mùa có điểm kết.** Mùa 2–3 tháng, tổng kết, vinh danh, rồi bắt đầu lại cho công bằng với người mới.

## 2. Plugin

| Plugin | Vai trò | Ghi chú |
|---|---|---|
| **Towny** | Thị trấn (town), quốc gia (nation), đất, thuế, quyền xây | Modrinth `towny`, có bản 26.2 |
| **SiegeWar** | Công thành theo lịch | GitHub release (3.7.0, 28/09/2026). Cài xong chạy `/swa install` |
| **VaultUnlocked** | Lớp nối kinh tế giữa các plugin | Modrinth `vaultunlocked`, có bản 26.2 |
| **EssentialsX** | Ví tiền (economy), `/home`, `/tpa`, `/spawn`, kit | Bản chính thức mới tới 26.1.2. **Cần bản dev** cho 26.2, chưa tìm được link tải ổn định |
| **CoreProtect** | Ghi lại mọi thao tác phá/đặt, khôi phục khi bị phá | **Bắt buộc** trước khi cho người ngoài vào |
| **WorldGuard** | Bảo vệ khu spawn, chợ, khu sự kiện | Modrinth, có bản 26.2 |
| **OldCombatMechanics** | Combat kiểu 1.8 | Không có trên Modrinth, tải tay. **Cần thử với người chơi Bedrock** |
| Dynmap / BlueMap + Dynmap-Towny | Bản đồ web: lãnh thổ, cờ vây thành | Tùy chọn, làm sau |
| TownyResources, TownyCombat | Tài nguyên theo thị trấn, vai trò chiến trường | Tùy chọn, cân nhắc ở mùa 2 |

## 3. Thế giới

- **Một thế giới chính** cho mùa, có viền (world border) **(đề xuất)** bán kính **5.000 block** cho giai đoạn 30–50 người; nới rộng nếu đông.
- **Không tự xây map.** Chọn một trong hai **(chưa chốt)**:
  - **Map Trái Đất tải sẵn** (tỉ lệ 1:1000 hoặc 1:2000): hợp Towny/SiegeWar (EarthPol, CCNet dùng). Nặng (vài GB trở lên), phải **kiểm tra giấy phép** (nhiều map cấm dùng cho server thu tiền hoặc bắt ghi tên tác giả).
  - **Map tự nhiên do server sinh**: dễ nhất, không vướng bản quyền. Dùng Chunky **sinh sẵn địa hình** (không phải xây) trước khi mở để đỡ giật.
- **Công trình** (spawn, chợ): tải schematic làm sẵn, dán bằng WorldEdit. Kiểm tra giấy phép trước khi dùng.
- **Nether, End**: mở; **cấm lập thị trấn** trong Nether/End (`/tw toggle claimable` hoặc tương đương, cần kiểm tra lệnh).
- **Khu spawn** bảo vệ bằng WorldGuard: không PvP, không xây. Có chợ, bảng hướng dẫn, cổng về lobby.
- **Vây thành chỉ bật ở thế giới chính**: `/tw toggle warallowed on` ở đó, tắt ở Nether/End.
- Hết mùa: **reset thế giới hay không là một setting** (chốt 08/10/2026), không cố định trong thiết kế. Nếu reset: thế giới mới, giữ bản lưu thế giới cũ để xem lại hoặc mở chế độ "xem bảo tàng".

## 4. Thị trấn và quốc gia (Towny)

**(đề xuất, chỉnh trong cấu hình Towny)**
- Lập thị trấn: **phí 500** (tiền trong game, sẽ cân lại với kinh tế), nhận vài ô đất ban đầu.
- Mua thêm đất: giá tăng dần theo số ô (Towny có sẵn), giới hạn số ô theo số dân.
- **Phí duy trì** hằng ngày (upkeep) theo số ô: là nơi "tiêu tiền" chính, giữ kinh tế khỏi lạm phát. Bật **phá sản** (bankruptcy) để thị trấn bị cướp vẫn sống được (SiegeWar khuyên).
- Lập quốc gia: cần **ít nhất 2–3 thị trấn** **(đề xuất)**, phí cao hơn.
- **Thị trấn hoà bình** (Peaceful, có sẵn trong SiegeWar): không bị vây, nhưng có thể bị quốc gia mạnh "thu phục" không cần đánh. Dành cho người thích xây.
- Chức vụ quân sự (SiegeWar): vua/tướng (General) mới được bắt đầu vây thành, chiếm, cướp. Người chơi phải được phong quân hàm mới tính điểm trận.

## 5. Kinh tế

Giữ đơn giản ở mùa 1, chỉnh khi thấy lạm phát hoặc thiếu tiền.

- **Nguồn tiền vào: là setting** (chốt 08/10/2026), bật/tắt từng nguồn, không cố định trong thiết kế:
  - Thương nhân của server ở spawn: bán tài nguyên, giá cố định, giới hạn mỗi ngày (mặc định **bật**).
  - Nghề nghiệp (jobs): kiếm tiền khi đào, chặt, câu cá… (mặc định **tắt**, bật khi thấy thiếu tiền).
  - Buôn bán giữa người chơi: luôn có.
- **Nơi tiền ra:** phí lập thị trấn/quốc gia, mua đất, phí duy trì, **quỹ chiến tranh** khi vây thành (SiegeWar mặc định 20/ô đất của thị trấn bị vây), thuế.
- **Cướp thành** (plunder, SiegeWar): bên thắng lấy 40/ô đất của thị trấn thua → chiến tranh có lợi ích kinh tế thật.
- Tiền **riêng của chế độ này**, không dùng chung với chế độ khác (theo nguyên tắc network).
- **Không bán tiền trong game lấy tiền thật** (vi phạm luật Mojang và là pay-to-win).

## 6. Chiến tranh (SiegeWar)

Cơ chế có sẵn của SiegeWar:
- Quốc gia tấn công đặt **cờ vây thành** (banner không trắng) ngoài rìa thị trấn đối phương → bắt đầu cuộc vây, nộp quỹ chiến tranh.
- Mỗi cuộc vây gồm nhiều **phiên chiến** (battle session) 1 giờ trong một cuối tuần. Trong phiên, PvP bật trong bán kính 300 block quanh cờ (Siege Zone).
- Điểm trận: **giữ cờ** (đứng gần cờ 7 phút để chiếm, mỗi phút cộng điểm) và **hạ quân địch có quân hàm**. Chết trong vùng vây **không rơi đồ**, đồ chỉ mòn 5%.
- Hết các phiên: bên có tổng điểm cao hơn thắng. Thắng thì được quỹ chiến tranh, có thể **cướp** và/hoặc **chiếm** thị trấn (thủ đô không bị chiếm).
- Sau vây: thị trấn được **miễn vây 7 ngày** (thủ đô 14 ngày). Thị trấn bị chiếm có thể **khởi nghĩa** (revolt) để giành lại.
- Thị trấn **không bị phá, không bị trộm** trong lúc vây → khớp yêu cầu "không cướp lúc offline".

Điều chỉnh cho người chơi Việt **(cần kiểm tra tên mục cấu hình của SiegeWar khi cài)**:
- **Lịch phiên chiến là setting** (chốt 08/10/2026): giờ, ngày, số phiên mỗi cuộc vây đều chỉnh trong cấu hình SiegeWar lưu trong repo, không cố định trong thiết kế.
  Giá trị khởi đầu: mặc định SiegeWar trải 7 phiên ra cuối tuần cho nhiều múi giờ; server Việt chỉ một múi giờ → **gom vào tối thứ 7 và Chủ nhật**, ví dụ **20:00–21:00 và 21:00–22:00 mỗi tối** (4 phiên/cuộc vây).
- Mỗi quốc gia tối đa **2 cuộc vây tấn công cùng lúc** (mặc định 3) cho giai đoạn ít người.
- Trong phiên chiến, SiegeWar tắt chat chung để giảm cãi nhau; giữ nguyên, chat bang/quốc gia vẫn dùng được.
- Bật **Discord webhook** của SiegeWar khi có Discord: tự báo trận bắt đầu, kết quả.

## 7. Combat

- Hai kiểu:
  - **1.8 (cũ):** bấm càng nhanh càng mạnh, không hồi chiêu, đỡ bằng kiếm, không khiên. Lợi cho điện thoại (Bedrock vốn không có thanh hồi chiêu); hại là người dùng autoclicker được lợi lớn.
  - **1.9+ (mới):** thanh hồi chiêu ~0,6 giây, khiên, rìu phá khiên. Điện thoại không thấy rõ hồi chiêu nên dễ thiệt.
- OldCombatMechanics cho **bật/tắt từng phần** (hồi chiêu, đỡ bằng kiếm, khiên, táo vàng…) → để là **setting**, có thể chạy kiểu lai. Khởi đầu: kiểu 1.8; chỉnh sau khi Griim đánh thử trên điện thoại.
- Cần chống autoclicker (Grim hoặc tương đương) nếu giữ 1.8, trước khi mở công khai.
- Chống hack (Grim, hỗ trợ Geyser) để sau, trước khi mở công khai.

## 8. Mùa giải

- **Thời lượng (đề xuất):** 10–12 tuần.
- **Bảng xếp hạng mùa:** quốc gia nhiều thị trấn nhất, thắng nhiều trận nhất, giàu nhất; người hạ nhiều địch nhất trong vùng vây.
- **Phần thưởng mùa:** chỉ là **danh hiệu và đồ trang trí** (tag tên, màu tên bang, cờ đặc biệt dùng ở mùa sau), lưu ở cấp network nên mang sang mùa sau. **Không** mang đồ, tiền, đất sang mùa mới.
- Tuần cuối mùa: "đại chiến" (có thể nới giới hạn vây). Sau đó đóng mùa, lưu thế giới cũ, mở thế giới mới.

## 9. Chống lạm dụng

Server chạy **offline mode** (cho người chơi không bản quyền) nên tài khoản phụ rất dễ tạo. Cần:
- **Giới hạn tài khoản theo IP** khi mở công khai (AuthMe `maxRegPerIp`, hiện để 0 vì chơi qua playit nhiều người có thể chung IP: cần xem lại cách playit báo IP).
- **Tài khoản phụ làm quân** để giữ cờ: chỉ người **có quân hàm** mới tính điểm, và vua/tướng phải phong → khó dùng acc rác. Cân nhắc yêu cầu **thời gian chơi tối thiểu** trước khi được phong quân hàm.
- **Thị trấn hoà bình** lạm dụng để né chiến: SiegeWar đã cho phép thu phục thị trấn hoà bình không cần đánh; theo dõi thêm.
- **Cấm** dùng lỗi game, hack, macro; bằng chứng lưu bởi CoreProtect.
- Admin **không tham gia phe** nào trong mùa (hoặc có tài khoản riêng để chơi).

## 10. Kiếm tiền (đúng luật Mojang)

Theo `minecraft-server-plan.md`: chỉ bán **đồ trang trí**, không pay-to-win, không bán cape, không bán gì liên quan Pokémon.

Được bán ở Bang Hội Chiến **(đề xuất)**:
- Đồ trang trí **cho cả bang**: mẫu cờ đặc biệt, màu tên bang, hiệu ứng hạt ở lãnh thổ, khung bảng tên ở thủ đô.
- Đồ trang trí cá nhân: tag chat, màu tên, hiệu ứng khi hạ địch, hạt khi đi.
- Gói mùa trang trí.

**Không** được bán: tiền trong game, đất/ô đất, giảm phí duy trì, miễn vây, quân hàm, giáp/vũ khí, TNT, spawner, bất cứ thứ gì tăng sức mạnh trong trận.

⚠️ Server chạy offline mode: **chưa kiểm tra** hướng dẫn thương mại của Mojang có nói gì riêng về server offline. Đọc kỹ trước khi mở web store.

## 11. Người chơi điện thoại (Bedrock)

- Lệnh Towny/SiegeWar gõ được trên điện thoại, nhưng nhiều và khó nhớ → về sau làm **menu Bedrock Forms** cho các lệnh hay dùng (`/t`, `/n`, `/sw`), nằm trong kế hoạch network-core.
- Đặt cờ vây thành, đặt rương cướp thành: thao tác thường, điện thoại làm được.
- Hướng dẫn người mới: bảng hướng dẫn ở spawn + sách hướng dẫn tiếng Việt khi vào lần đầu.

## 12. Câu hỏi cần chốt

1. **Map**: map Trái Đất tải sẵn hay map tự nhiên (mục 3). Kích thước viền và độ dài mùa (đề xuất: bán kính 5.000, 10–12 tuần).
2. ~~Lịch phiên chiến~~ → **setting** (mục 6).
3. Combat: khởi đầu 1.8, là **setting** từng phần; chốt sau khi thử trên điện thoại (mục 7).
4. ~~Nguồn tiền vào~~ → **setting** bật/tắt từng nguồn (mục 5).
5. Có cho tài nguyên theo thị trấn (TownyResources) ngay mùa 1 không, hay để mùa 2.
6. ~~Reset thế giới mỗi mùa~~ → **setting** (mục 3).

## 13. Bước tiếp theo

1. ~~Cài plugin ở mục 2 vào `banghoi-1`, chạy `/swa install`, bật vây thành ở thế giới chính.~~ Xong 08/10/2026 (EssentialsX dùng bản dev #1832).
2. ~~Đưa các lựa chọn vào cài đặt.~~ Xong: trang Cài đặt http://localhost:8889 (`modes/banghoi/settings.schema.yml`). Còn thiếu: bật/tắt TownyResources, nghề nghiệp; chức năng mở mùa mới.
3. Chơi thử với nhóm nhỏ: lập 2 thị trấn, 2 quốc gia, đánh thử một cuộc vây với lịch rút ngắn (vài phút) để xem cơ chế.
4. Chỉnh số liệu, ghi lại vào file này.
