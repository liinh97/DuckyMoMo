# Thiết kế Bang Hội Chiến (bản nháp 1)

> Viết 07/10/2026. Nền tảng: **Towny + SiegeWar** (đã chốt).
> Đây là **đề xuất** để chạy thử với bạn bè. Mục có **[cần chốt]** là chủ server quyết định.
> Số liệu là điểm xuất phát, sẽ chỉnh sau khi chơi thử.
> Cơ chế SiegeWar lấy theo wiki và mã nguồn chính thức (nguồn ở cuối file). Tên mục cấu hình của Towny **chưa kiểm tra**, phải đối chiếu với file `config.yml` sinh ra ở lần chạy đầu.

---

## 1. Mục tiêu

- Đánh nhau **có lịch**: ai cũng biết trước giờ chiến, đi làm hay đi học vẫn tham gia được. Không ai bị phá nhà lúc offline.
- **Điện thoại và PC đánh ngang nhau** nhiều nhất có thể: chiếm cờ quan trọng hơn kỹ năng click, combat kiểu 1.8.
- Chạy được với **khoảng 15–40 người** (giai đoạn bạn bè). Đông hơn thì mới nới giới hạn.
- **Theo mùa**: mỗi mùa có kết thúc và có vinh danh, người mới không bị bỏ quá xa.
- Admin không phải trông trận hằng ngày, SiegeWar tự vận hành.

## 2. Thuật ngữ trong game (Towny → tiếng Việt)

| Towny / SiegeWar | Gọi trong game | Ý nghĩa |
|---|---|---|
| Town | **Thành** | Nhóm người chơi, có đất riêng (claim theo ô 16x16) |
| Mayor | **Thành chủ** | Người lập thành |
| Nation | **Bang** | Nhiều thành gộp lại. Thành của người lập bang là **kinh thành** (capital) |
| King | **Bang chủ** | |
| General | **Đại tướng** | Được mở và bỏ cuộc vây, cướp, chiếm thành |
| Private … Colonel | **Binh sĩ** (các cấp) | Có quân hàm thì mới ghi điểm trong trận |
| Guard (town rank) | **Vệ binh** | Quân hàm của thành, dùng khi thủ thành |
| Siege | **Công thành** | Một bang tấn công một thành |
| Battle Session | **Trận** | 1 giờ giao tranh trong một cuộc công thành |
| Siege Banner | **Cờ công thành** | Đặt ngoài biên giới thành để mở cuộc vây; hai bên tranh nhau giữ |
| War Chest | **Rương chiến** | Tiền đặt cọc khi mở vây; bên thắng lấy |
| Plunder | **Cướp** | Lấy tiền của thành thua |
| Invade / Occupied | **Chiếm** / **Bị chiếm** | Thành thua bị sáp nhập vào bang thắng |
| Revolt | **Khởi nghĩa** | Thành bị chiếm vây lại để giành tự do |
| Peaceful town | **Thành hoà bình** | Không bị công thành, nhưng bang lớn gần đó chiếm được ngay |

Đổi chữ hiển thị bằng file ngôn ngữ của Towny và SiegeWar (việc làm sau, không bắt buộc lúc thử).

## 3. Vòng chơi của người chơi

1. **Vào server** → lobby → chọn Bang Hội Chiến → xuất hiện ở spawn (khu an toàn, WorldGuard).
2. **Tuần đầu**: đi xa spawn, lập thành (`/t new <tên>`) hoặc xin vào thành có sẵn. Thành mới được **miễn công thành 7 ngày**.
3. **Xây và làm giàu**: đào, farm, bán đồ ở chợ, đóng thuế thành để giữ đất.
4. **Lập hoặc gia nhập bang**, kết liên minh, phong quân hàm.
5. **Thứ 5**: bang chủ hoặc đại tướng cắm cờ cạnh thành địch để mở vây.
6. **Thứ 6 → Chủ nhật**: các trận theo giờ cố định. Giữ cờ và hạ địch để kiếm điểm.
7. **Kết thúc vây**: bên thắng lấy rương chiến; nếu bên công thắng thì được cướp và/hoặc chiếm thành.
8. **Cuối mùa**: tổng kết, trao danh hiệu, mở thế giới mới.

## 4. Lịch chiến (đề xuất)

Giờ server: `Asia/Ho_Chi_Minh` (đã đặt `TZ` trong `compose.yaml`; cần kiểm tra SiegeWar có dùng đúng múi giờ này không ở lần chạy đầu).

| Việc | Mặc định của SiegeWar | Đề xuất DuckyMoMo | Lý do |
|---|---|---|---|
| Ngày được mở vây | Thứ 5 | **Thứ 5** | Giữ nguyên, có 1 ngày để chuẩn bị |
| Số trận mỗi cuộc vây | 7 | **5** | Ít người, 7 trận thì quá dài và mệt |
| Độ dài mỗi trận | 60 phút | **60 phút** | |
| Giờ trận | T7, CN: 03:00, 11:00, 19:00; T2: 03:00 | **T6 20:30; T7 15:00, 20:30; CN 15:00, 20:30** | Giờ người Việt hay online: tối các ngày, chiều cuối tuần |
| Huỷ trận nếu không có cuộc vây nào | Có | **Có** | |
| Số cuộc vây tấn công tối đa mỗi bang | 3 | **1** (nâng lên 2 khi > 40 người) | Bang lớn không thể đè nhiều thành cùng lúc |
| Miễn công thành cho thành mới | 168 giờ | **168 giờ** | |
| Miễn công thành sau một cuộc vây | 168 giờ (kinh thành gấp đôi) | **168 giờ** | |
| Phải chờ trước khi khởi nghĩa | 72 giờ | **72 giờ** | |
| Bán kính vùng chiến | 200 ô | **150 ô** | Bản đồ nhỏ, ít người, giao tranh tập trung hơn |
| Siege Assembly (10 phút ghi điểm trước khi được mở vây) | Tắt | **Bật** | Chặn trò tự vây thành của mình bằng acc phụ để lấy thời gian miễn công thành |

Cấu hình tương ứng trong `plugins/SiegeWar/config.yml` (tên mục lấy từ mã nguồn SiegeWar, đối chiếu lại với file thật):
```yaml
war.siege.quantities.max_active_siege_attacks_per_nation: 1
war.siege.times.duration_battle_sessions: 5
war.siege.times.siege_immunity_time_new_town_hours: 168
war.siege.times.siege_immunity_post_siege_hours: 168
war.siege.times.revolt_immunity_post_siege_hours: 72
war.siege.distances.siegezone_radius_blocks: 150
war.siege.siege_assemblies.assemblies_enabled: true
siege_start_day_limiter.allowed_siege_start_days: thursday
battle_session_scheduler.start_times.friday: "20:30"
battle_session_scheduler.start_times.saturday: "15:00,20:30"
battle_session_scheduler.start_times.sunday: "15:00,20:30"
battle_session_scheduler.start_times.monday: ""
battle_session_scheduler.session_duration_minutes: 60
```
(Viết dạng có dấu chấm cho gọn; trong file YAML thật là các mục lồng nhau.)

**[cần chốt]** Giờ trận. Đề xuất trên giả định người chơi chủ yếu là học sinh, sinh viên, người đi làm.
Nếu bạn bè chơi được giờ khác thì đổi ở đây trước khi mở.

## 5. Luật PvP và bảo vệ

| Khu vực | PvP | Phá, lấy đồ | Ghi chú |
|---|---|---|---|
| Spawn (WorldGuard) | Tắt | Tắt | Chợ, bảng hướng dẫn, cổng ra hoang dã |
| Trong thành | Tắt (mặc định Towny) | Chỉ người trong thành | Không ai phá được nhà lúc offline |
| Hoang dã | **[cần chốt]** Bật hay tắt | Được | Bật thì giống Factions hơn nhưng người mới dễ bị giết. Đề xuất: **bật**, nhưng chết ở hoang dã chỉ rơi một phần đồ (cần plugin hoặc tự viết, để sau) |
| Vùng chiến lúc có trận | **Bật** | Không phá được thành | SiegeWar tự bật PvP trong bán kính quanh cờ |

- Trong lúc vây, **thành không bị phá và không bị lấy đồ** (đặc tính của SiegeWar). Mất mát chỉ là tiền (cướp) và quyền sở hữu thành (chiếm).
- Chết trong vùng chiến: **không rơi đồ, đồ bị giảm 5% độ bền** (mặc định SiegeWar). Giữ nguyên. Thua trận không mất trắng nên người chơi dám ra trận.
- Trong giờ trận, chat chung và `/tell` bị tắt để bớt chửi nhau, chat bang và liên minh vẫn dùng được (mặc định SiegeWar). Giữ nguyên.

## 6. Thành và bang

| Mục | Đề xuất | Ghi chú |
|---|---|---|
| Phí lập thành | Thấp, khoảng 1–2 ngày cày | Để người mới lập được thành sớm |
| Phí lập bang | Bằng khoảng 5 lần phí lập thành | Bang phải là nhóm thật, không phải 1 người |
| Phí duy trì mỗi ngày | Tăng theo số ô đất | Thành ôm đất mà không ai chơi sẽ tự nghèo dần rồi giải tán |
| Thành hoà bình | **Bật** | Cho người thích xây dựng; đổi lại bang mạnh gần đó chiếm được ngay |
| Kinh thành | Không bị chiếm (mặc định SiegeWar) | Bang không thể bị xoá sổ trong một cuộc vây |
| Phá sản thành | **Bật** (SiegeWar khuyên) | Thành bị cướp sạch tiền vẫn còn đất, không bị xoá ngay |

- Tiền dùng **EssentialsX Economy** qua Vault. Nguồn tiền: bán đồ cho chợ server (giá cố định), giao dịch giữa người chơi, thưởng sự kiện.
  **Không có nguồn tiền nào từ nạp thẻ.**
- Số liệu cụ thể (tiền mỗi ô, giá chợ) để lúc chạy thử mới đặt. SiegeWar sẽ cảnh báo khi giá trị tiền đặt quá cao hoặc quá thấp so với kinh tế server.
- Sau khi cài: chạy `/swa install` (SiegeWar tự chỉnh cấu hình và quyền cần thiết của Towny), rồi `/tw toggle warallowed on` ở thế giới chính.

## 7. Bản đồ

- Một thế giới chính, **viền 5.000 x 5.000** ô lúc thử với bạn bè (`/worldborder`), tạo trước bằng Chunky để không giật.
  Nới rộng khi đông người.
- Spawn ở giữa, bán kính khoảng 150 ô là khu an toàn. **Cấm lập thành trong khoảng 500 ô quanh spawn** (Towny có giới hạn khoảng cách tới spawn; tên mục cần kiểm tra).
- Nether, End: **[cần chốt]** mở luôn hay khoá End đến tuần 3 để có sự kiện mở End.

## 8. Mùa giải

Đề xuất **mùa 8 tuần**:

| Tuần | Diễn biến |
|---|---|
| 1–2 | **Xây dựng**: không ai bị công thành (`/swa siegeimmunity alltowns ...` cho toàn server 14 ngày) |
| 3–7 | **Chiến tranh**: vây bình thường mỗi cuối tuần |
| 8 | **Đại chiến**: cho phép 2 cuộc vây mỗi bang, thêm trận tối thứ 5 |
| Cuối tuần 8 | Tổng kết → vinh danh → chụp ảnh bản đồ → mở thế giới mới |

**Tính điểm mùa cho bang** (SiegeWar không tự tính, giai đoạn đầu admin tính tay; sau này network-core tự đọc dữ liệu Towny):
- +3 điểm mỗi thành đang giữ (cả thành của mình lẫn thành chiếm được) lúc kết thúc mùa.
- +2 điểm mỗi cuộc vây thắng (công hay thủ đều tính).
- +1 điểm mỗi trận thắng.

**Phần thưởng mùa** (chỉ trang trí, giữ sang mùa sau):
- Bang hạng 1–3: danh hiệu trước tên (`[Bá chủ mùa 1]`…), cờ bang in vào khu vinh danh ở spawn thế giới mới.
- Người hạ nhiều địch nhất, người giữ cờ lâu nhất: danh hiệu riêng.
- **Không thưởng đồ, tiền, đất, hay lợi thế cho mùa sau**, để người mới vào mùa sau không bị bỏ xa.

## 9. Chống lạm dụng (quan trọng vì cho crack vào)

Cho crack vào thì tạo acc phụ rất dễ (chỉ cần đổi tên). Các trò cần chặn:

| Trò | Cách chặn |
|---|---|
| Lập thành giả rồi tự vây để lấy thời gian miễn công thành | **Bật Siege Assembly** (mục 4): phải ghi đủ điểm trong 10 phút mới được mở vây |
| Acc phụ đứng cho địch giết để cày điểm hạ địch (150 điểm/lần) | Chỉ người có **quân hàm** mới bị tính điểm, và quân hàm do bang chủ phong. Luật: **mỗi người chỉ dùng 1 acc trong chiến tranh**. Admin đối chiếu IP trong database `librelogin` khi có tố cáo |
| Acc phụ làm gián điệp trong bang địch | Việc của các bang tự lo (không cấm, là một phần của chơi ngoại giao). Admin không can thiệp |
| Vây bằng cách bao quanh thành địch bằng thành của mình | Luật của wiki SiegeWar: **cấm bao vây thành khác bằng thành** |
| Cắm cờ ở chỗ không lên được (trên trời, dưới hang) | Luật: **cờ đặt ngang độ cao của thành, đường tới cờ đi được** |
| Phá địa hình ở vùng chiến | Luật: không phá hoại vùng chiến; được xây tường và đồn nhỏ |
| Phá hoại, trộm đồ ngoài chiến tranh | Towny đã chặn trong thành; ngoài thành dùng **CoreProtect** để kiểm tra và khôi phục |

- `ip-limit` của LibreLogin để tắt (mặc định), vì ở Việt Nam nhiều người dùng chung IP (quán net, ký túc xá, 4G).
- Về sau (network-core): acc mới phải chơi đủ vài giờ mới nhận được quân hàm. **Việc này phải tự viết**, Towny và SiegeWar không có sẵn.

## 10. Combat

- **OldCombatMechanics**: bỏ thời gian hồi đòn, giống 1.8. Người chơi điện thoại đánh ngang PC hơn.
- **[cần chốt]** Có cho dùng vật phẩm làm lệch cân bằng không: ngọc toàn mạng (totem), cung nỏ xuyên giáp, đinh ba, đồ netherite.
  Đề xuất lúc thử: giữ nguyên hết, xem trận thật rồi mới cấm.
- Không bắt buộc pháo TNT. SiegeWar thắng bằng giữ cờ và hạ địch, nên người chơi điện thoại vẫn đóng góp được.

## 11. Bán gì được (đúng luật Mojang)

| Được bán (chỉ trang trí) | Không bán |
|---|---|
| Màu tên bang, khung tên trong chat | Tiền trong game, đồ, kit |
| Mẫu cờ bang đặc biệt, hiệu ứng khi chiếm cờ | Quân hàm, thêm lượt vây, thêm thời gian miễn công thành |
| Hiệu ứng hạt quanh người, hiệu ứng khi hạ địch | Thêm ô đất, giảm phí duy trì |
| Danh hiệu trang trí không gắn với thành tích | Danh hiệu mùa (phải tự giành) |
| Gói mùa trang trí (season pass) | Bất cứ thứ gì giúp thắng trận |

- **Không bán cape**, không bán gì liên quan Pokémon (luật chung của network).
- **[cần chốt]** Có mở bán cho người chơi crack không (câu hỏi mở trong `docs/HANDOFF.md`).

## 12. Plugin cần có để chạy bản thiết kế này

Đã liệt kê trong `modes/banghoi/README.md`: Towny, SiegeWar, Vault, EssentialsX, CoreProtect, WorldGuard, OldCombatMechanics, Chunky.
Có thể thêm sau (không bắt buộc lúc thử):
- **Dynmap + Dynmap-Towny**: bản đồ web thấy lãnh thổ các bang và vị trí cờ công thành. Rất hợp để quảng bá.
- **TownyResources**: mỗi thành sinh tài nguyên, bang chiếm được thì ăn chia. Thêm lý do để đi chiếm thành.
- **DiscordSRV** hoặc webhook Discord có sẵn của SiegeWar: báo trận bắt đầu và kết thúc lên Discord.

## 13. Kế hoạch chạy thử

1. Cài plugin, chạy `/swa install`, áp cấu hình ở mục 4, chép cấu hình về `modes/banghoi/server/plugins/` để lưu Git.
2. Thử một mình với 2 acc (1 bản quyền, 1 crack): lập 2 thành, 2 bang, phong quân hàm, cắm cờ.
   Tạm thêm giờ trận gần giờ hiện tại để thử luôn, thử xong thì trả lại lịch.
3. Rủ 4–6 bạn chơi 1 tuần, có ít nhất 1 cuộc vây thật.
4. Ghi lại: trận có vui không, giờ trận có hợp không, tiền có lạm phát không → sửa bản nháp này.

## 14. Câu hỏi cần chốt (tóm tắt)

- [ ] Giờ trận (mục 4).
- [ ] PvP ở hoang dã bật hay tắt (mục 5).
- [ ] Nether, End mở ngay hay mở theo sự kiện (mục 7).
- [ ] Độ dài mùa: 8 tuần có hợp không (mục 8).
- [ ] Cấm hay giữ totem, netherite… (mục 10).
- [ ] Có bán cho người chơi crack không (mục 11).

## Nguồn
- SiegeWar User Guide: https://github.com/TownyAdvanced/SiegeWar/wiki/Siege-War-User-Guide
- SiegeWar Installation: https://github.com/TownyAdvanced/SiegeWar/wiki/Siege-War-Installation
- Mục cấu hình SiegeWar (giá trị mặc định): https://github.com/TownyAdvanced/SiegeWar/blob/master/src/main/java/com/gmail/goosius/siegewar/settings/ConfigNodes.java
- Towny wiki: https://github.com/TownyAdvanced/Towny/wiki
