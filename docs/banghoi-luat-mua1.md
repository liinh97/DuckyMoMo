# Luật chơi Bang Hội Chiến: mùa 1 (đề xuất 13/10/2026, chốt một phần 14/10)

> **Đã chốt 14/10:** (1) lịch 10 tuần; (2) PvP đất hoang **mặc định bật (A), không liên quan lịch công thành**; vẫn có công tắc
> trên trang Cài đặt; (5) tài khoản phụ + 5 giờ chơi mới được phong quân hàm (đã code); (6) luật "lãnh thổ không liên quan chủ quyền thật".
> **Chốt thêm 14/10:** (3) cấm hẳn thị trấn ở Nether và End, không có phương án mềm (đã tắt `claimable`).
> **Còn treo:** (4) bán kính thu phục thị trấn hoà bình
> (giữ 1.200 tới khi thấy thị trấn hoà bình quan trọng thế nào); bảng xếp hạng chi tiết (mục 8) chờ duyệt rồi mới code.

> Bổ sung cho `docs/banghoi-design.md` (định hướng chung). File này là **luật cụ thể** cho mùa 1: map Trái Đất 1:1000,
> vùng Đông Nam Á (viền 2.600 × 2.400 block quanh 12500/-2000), spawn Hà Nội.
> Ký hiệu: **[đang chạy]** = cấu hình hiện tại trên banghoi-1 (đã đọc từ file thật 13/10);
> **[đề xuất]** = chưa áp dụng, chờ chủ server chốt; **[cần kiểm tra]** = chưa chắc plugin làm được đúng như vậy.
> Con số nào cũng sửa được sau khi chơi thử. Phần lớn đã có trên trang Cài đặt.

Quy đổi để hình dung: 1 ô đất Towny = 16 × 16 block. Vùng mùa 1 có khoảng 24.000 ô (hơn nửa là đất liền).
Bán kính vùng vây 200 block ≈ 185 km ngoài đời (cỡ một tỉnh lớn).

---

## 1. Lịch mùa 1 (10 tuần) [đề xuất]

| Thời gian | Giai đoạn | Điều gì xảy ra |
|---|---|---|
| Tuần 1–2 | **Hoà bình** | Lập thị trấn, chiếm đất, làm kinh tế. **Chưa cho vây thành** (tắt công tắc SiegeWar). PvP đất hoang vẫn bật như mọi tuần. |
| Tuần 3–9 | **Chiến tranh** | Vây thành theo lịch (mục 4). |
| Tuần 10 | **Đại chiến** | Mỗi quốc gia được vây 3 thành cùng lúc (thường 2), thêm phiên tối thứ 6 20:00. Đóng mùa Chủ nhật 22:00. |
| Sau đóng mùa | **Tổng kết** | Chốt bảng xếp hạng, trao danh hiệu, lưu world làm "bảo tàng" (chỉ xem), mở mùa 2. |

- Lý do có 2 tuần hoà bình: miễn vây cho thị trấn mới hiện chỉ 7 ngày **[đang chạy]**; ai lập sớm sẽ bị đánh khi người
  vào muộn còn chưa có gì. Tắt hẳn vây thành 2 tuần đầu thì công bằng hơn.
- **Đã có** trên trang Cài đặt > Chiến tranh > "Giai đoạn mùa và PvP": công tắc "Cho phép vây thành" (`war.siege.switches.enabled`)
  và "PvP ở đất hoang". Lúc mở mùa: tắt vây thành; đầu tuần 3: bật. PvP đất hoang luôn bật (không đổi theo giai đoạn).
  **Hiện vây thành đang BẬT** (để thử trước khi mở), PvP đất hoang BẬT.

## 2. Thị trấn

| Mục | Giá trị | |
|---|---|---|
| Phí lập thị trấn | 500 xu (`/t new <tên>`) | [đang chạy] |
| Tiền khởi đầu mỗi người | 200 xu | [đang chạy] |
| Đất được giữ | 8 ô / mỗi người dân | [đang chạy] |
| Phí mua thêm ô | 25 xu, mỗi ô sau đắt thêm 1 xu | [đang chạy] |
| Phí duy trì thị trấn | 10 xu / ngày | [đang chạy] |
| Thị trấn hết tiền | Phá sản (không bị xoá, mắc nợ) | [đang chạy] |
| Tiền đồn (outpost) | 500 xu | [đang chạy] |
| **Khoảng cách tối thiểu giữa 2 thị trấn** | Nhà chính cách nhau ≥ 10 ô (160 block) | [đề xuất] hiện là 0 (`min_distance_between_homeblocks`) |
| **Cấm lập thị trấn sát spawn** | Trong 300 block quanh spawn | [đề xuất, cần kiểm tra] Towny không đọc vùng WorldGuard; cách chắc nhất là tạo 1 "thị trấn của server" giữ đất quanh spawn |
| **Nether, End** | Không cho lập thị trấn (kể cả tiền đồn) | [ĐÃ CHỐT, đã áp dụng 14/10] `townyworld world_nether|world_the_end toggle claimable` → `claimable=false` |

### 2.1 Vì sao cấm thị trấn ở Nether và End (đã chốt 14/10)
- Lưu ý: cài đặt nằm ở `data/banghoi-1/plugins/Towny/data/worlds/*.txt` (không có trong repo). **Mùa mới tạo world mới thì phải tắt lại.**
- **Không đánh được:** vây thành chỉ bật ở world chính (Nether/End `warAllowed=false`). Thị trấn dưới đó thành chỗ trốn chiến tranh
  vĩnh viễn, cất hết tài sản vào đó là thua trận cũng không mất gì.
- **Độc chiếm tài nguyên chung:** ai lập thị trấn trùm pháo đài Nether, tàn tích bastion hay đảo chính End (rồng, cổng End, thành End có
  elytra) thì người khác không phá, không mở rương được ở đó. Một quốc gia giữ hết elytra/netherite là lợi thế lớn.
- **Nether 1 block = 8 block world chính:** thị trấn Nether gần cổng có thể chặn đường đi tắt của cả server.
- Nether/End không có viền mùa như world chính (cần kiểm tra ChunkyBorder) nên chiếm đất không giới hạn.

- **Tên thị trấn, quốc gia:** không tục, không xúc phạm, không chính trị. Map là Trái Đất thật nên thêm luật riêng (mục 7.4).
- **Thị trấn hoà bình** (`/t toggle peaceful`) **[đang chạy]**: không bị vây, không được phong quân hàm. Phải chờ 7 ngày mới có hiệu lực
  (thị trấn mới: 2 ngày). Quốc gia mạnh ở gần (trong 1.200 block) có thể "thu phục" mà không cần đánh.
  [CHƯA CHỐT] đề xuất hạ bán kính thu phục xuống **400 block** (1.200 block gần bằng nửa vùng mùa). Chủ server muốn xem thị trấn hoà bình
  quan trọng thế nào khi chơi thử rồi mới quyết; tạm giữ 1.200.

## 3. Quốc gia

| Mục | Giá trị | |
|---|---|---|
| Phí lập quốc gia | 1.000 xu (`/n new <tên>`) | [đang chạy] |
| Phí duy trì quốc gia | 100 xu / ngày | [đang chạy] |
| **Điều kiện lập** | Thị trấn có ≥ 3 người dân | [đề xuất, cần kiểm tra tên mục Towny] |
| Ai được vây thành | Vua và người có quân hàm Tướng (General) | [đang chạy, SiegeWar] |
| Lương quân đội | Bật: quốc gia trả lương theo quân hàm | [đang chạy] |

- Chỉ **quốc gia** mới tấn công được. Thị trấn lẻ chỉ phòng thủ, nên muốn đánh thì phải liên minh. Đúng với ý "có lý do để lập nhóm".
- **Quân hàm** (`/n rank add <tên> <quân hàm>`): chỉ người có quân hàm mới được tính điểm khi giữ cờ.
  **[đã code 14/10]** chỉ được phong quân hàm (`guard`, `private`…`general`) khi người đó đã chơi ≥ **5 giờ** ở Bang Hội Chiến
  (thống kê thời gian chơi của Minecraft, có tính cả lúc đứng AFK). Lệnh `/t rank add`, `/n rank add` bị chặn kèm lời giải thích.
  Số giờ chỉnh trên trang Cài đặt. Admin (op) không bị chặn.

## 4. Công thành (SiegeWar)

### 4.1 Quy trình một cuộc vây

1. **Thứ 5**: Vua hoặc Tướng cắm **cờ vây** (banner không màu trắng) ngay ngoài rìa thị trấn địch, cách tối đa 1 ô **[đang chạy]**
   (chỉ được bắt đầu vây vào thứ 5). Quốc gia tấn công nộp **quỹ chiến tranh 20 xu × số ô** của thị trấn bị vây
   (thị trấn 40 ô thì quỹ là 800 xu).
2. **Thứ 7, Chủ nhật, 20:00 và 21:00**: 4 phiên đánh, mỗi phiên 60 phút **[đang chạy]**. Không có cuộc vây nào thì phiên tự huỷ.
3. Trong phiên, PvP bật trong **vùng vây** (bán kính 200 block quanh cờ). Người không thuộc hai phe vào vùng vây sẽ bị
   "ốm" sau 20 giây.
4. **Điểm**:
   - Đứng gần cờ đủ **7 phút** thì chiếm được cờ. Giữ cờ được **10 điểm/phút** cho mỗi người.
   - Hạ một người có quân hàm của phe kia được **150 điểm**.
   - Giành lại cờ từ tay địch thì điểm nhân 3.
5. **Chết trong vùng vây: KHÔNG rơi đồ**, đồ chỉ mất 5% độ bền **[đang chạy]**. Không ai mất trắng vì một trận thua.
6. Hết 4 phiên, bên nhiều điểm hơn thắng:
   - **Tấn công thắng**: lấy lại quỹ chiến tranh. Được **cướp 40 xu × số ô** từ ngân khố thị trấn thua, và/hoặc **chiếm đóng**
     (thị trấn thành thuộc địa, nộp thuế chiếm đóng tối đa 1 xu/ô/ngày). **Thủ đô không bị chiếm** **[đang chạy]**.
   - **Phòng thủ thắng**: nhận quỹ chiến tranh của bên tấn công.
7. Sau trận: thị trấn được **miễn vây 7 ngày**. Thuộc địa được **khởi nghĩa** (revolt) để giành lại tự do sau 72 giờ.

### 4.2 Giới hạn
- Mỗi quốc gia tối đa **2 cuộc vây tấn công cùng lúc** **[đang chạy]**, tuần đại chiến **3** **[đề xuất]**.
- Không đặt được obsidian, băng ở đất hoang; không đổ nước, dung nham; TNT ở đất hoang không phá block **[đang chạy]**
  (chống xây tường thần tốc, chống "lấp" cờ).
- Thị trấn đang bị vây: không mời thêm dân, không mua hay bán đất **[đang chạy]**.
- Người ngoài không dùng `/t spawn` vào vùng vây hay thị trấn đang bị vây được **[đang chạy]**. `/tpr` hiện **chưa** né vùng vây
  (chỉ né đất thị trấn): nên sửa trước khi mở chiến tranh **[đề xuất]**.
- Trong phiên đánh, chat chung tắt đến 10 phút sau trận (chat thị trấn, quốc gia vẫn dùng được) **[đang chạy]**.

### 4.3 Nên bật thêm
- **Báo trận lên Discord** (webhook có sẵn trong SiegeWar) khi có kênh Discord.
- **Thông báo trong game** 30 phút và 5 phút trước mỗi phiên [đề xuất, viết thêm trong DuckyMoMoCore hoặc dùng lệnh hẹn giờ].

## 5. PvP ngoài công thành: **CẦN CHỐT**

**[Đang chạy]**: đất hoang **bật PvP**, chết **rơi đồ**. Trong thị trấn PvP tắt, ở spawn tắt. Người mới được bảo vệ 15 phút.
Đang đánh nhau mà thoát game thì chết và rơi đồ. Đang đánh nhau thì không dùng được lệnh nào ngoài nhắn tin (nên không `/tpr`, `/home`, `/spawn` để trốn được).

**ĐÃ CHỐT 14/10: A, PvP đất hoang mặc định BẬT, không liên quan lịch công thành.** Công tắc vẫn có (DuckyMoMoCore `pvp-rules.wild-pvp`, trang Cài đặt).
Khi tắt: người đánh người ở đất hoang bị chặn (cả cung, thuốc, TNT, thú nuôi), trừ khi nạn nhân đang trong vùng vây **và** đang có phiên đánh.
Trong thị trấn vẫn theo cài đặt PvP của thị trấn. Hiện đang **BẬT**.

Ba lựa chọn đã cân nhắc:

| | Đất hoang | Chết ngoài vùng vây | Hợp với |
|---|---|---|---|
| **A** (đang chạy) | PvP bật | Rơi đồ | Kịch tính, đi xa phải cẩn thận. Người chơi điện thoại dễ thiệt. |
| **B** | PvP tắt (chỉ đánh trong vùng vây khi có trận) | Không xảy ra | Đúng nhất với ý "đánh nhau có hẹn", hiền, hợp người thích xây. |
| **C** (lai) | PvP bật | Giữ đồ, chỉ mất 10% tiền mang theo | Vẫn có đánh nhau dọc đường nhưng không mất trắng. [cần kiểm tra cách làm] |

**Gợi ý: B cho 2 tuần hoà bình, sau đó A hoặc C.** Server mới ít người, người mới bị giết mất hết đồ ngay hôm đầu thường bỏ luôn.

## 6. Kinh tế (tóm tắt, chi tiết trên trang Cài đặt)

- Tiền vào: nghề (đào, chặt, săn quái… tự động), bán cho server (`/sell`, biển [Sell]), buôn bán giữa người chơi (`/ah`, shop rương, `/trade`).
- Tiền ra: lập/duy trì thị trấn và quốc gia, mua đất, quỹ chiến tranh, phí `/tpr` (đang 0), shop phù phép.
- Mục tiêu cân bằng: một người chơi bình thường **2–3 giờ** đủ tiền lập thị trấn (500 xu). Chơi thử thấy nhanh hay chậm hơn thì chỉnh giá bán và tiền nghề.
- Tiền chỉ dùng trong Bang Hội Chiến của mùa đó. **Không bán tiền trong game lấy tiền thật.**

## 7. Luật ứng xử (đưa vào chương "Luật server" của `/huongdan`)

### 7.1 Hack, lỗi game
- Cấm hack, x-ray, autoclicker, macro, client "tối ưu PvP". Có chống hack Grim; CoreProtect ghi lại mọi block.
- Cấm lợi dụng lỗi game (nhân đồ, xuyên tường, kẹt chunk). Thấy lỗi thì báo admin, **được thưởng** (danh hiệu hoặc tiền trong game).

### 7.2 Tài khoản phụ
- **[chốt 14/10] Tạm thời** mỗi người tối đa **2 tài khoản** (kéo người chơi); server đông thì siết còn 1 (xem 10.4). Tài khoản phụ **phải khai**. Tài khoản phụ **không được phong quân hàm, không được đánh trận,
  không được ở quốc gia đối địch** với tài khoản chính. Giấu tài khoản phụ mà bị phát hiện = vi phạm nặng.
- **[đã code 14/10]** admin ghi bằng `/dmm phu add <phụ> <chính>` (`remove`, `list`), lưu ở `plugins/DuckyMoMoCore/taikhoanphu.yml`.
  Tài khoản phụ bị chặn phong quân hàm và không gây sát thương cho người chơi trong vùng vây lúc có phiên.
- **[đã kiểm tra 14/10] Qua playit, server KHÔNG thấy IP thật**: mọi người đều hiện `127.21.235.67` (địa chỉ của agent playit), nên
  không giới hạn đăng ký theo IP được và không tự phát hiện tài khoản phụ được. Cách có IP thật: bật Proxy Protocol cho tunnel trên trang
  playit + `haproxy-protocol = true` trong Velocity (chưa chắc playit hỗ trợ cho tunnel Bedrock UDP), hoặc chuyển sang VPS (dự định
  khi chạy thật). Tới lúc đó luật tài khoản phụ dựa vào khai báo + báo cáo.

### 7.3 Chiến tranh
- **Cấm vây giả** (hai quốc gia bắt tay đánh nhau để chuyển tiền hoặc lấy thành tích mùa). Trận bị xác định là dàn xếp thì không tính
  điểm mùa, tiền bị thu lại.
- Cấm giết đi giết lại một người vừa hồi sinh ngoài vùng vây (spawn-kill). Spawn và thị trấn đã chặn PvP.
- Lời hứa, hợp đồng miệng giữa người chơi: admin **không bảo đảm**. Muốn an toàn thì dùng `/trade`, chợ `/ah`, shop rương.
  Admin chỉ can thiệp khi có lợi dụng lỗi hoặc hack.

### 7.4 Tên và chat, riêng vì map là Trái Đất thật
- **Lãnh thổ trong game không liên quan tới chủ quyền hay biên giới ngoài đời.** Cấm đặt tên, cấm chat kích động dân tộc,
  vùng miền, tôn giáo, chính trị, tranh chấp biên giới hay biển đảo thật. Admin đổi tên hoặc xoá thị trấn vi phạm.
- Không chửi bới, quấy rối, phân biệt; không spam; không quảng cáo server khác (đã có trong luật hiện tại).

### 7.5 Admin
- Tài khoản có quyền admin **không vào phe nào**. Muốn chơi thì dùng tài khoản riêng không có quyền, công khai cho mọi người biết.
- Mọi lần admin can thiệp (trả đồ, đổi tên, xoá thị trấn) ghi vào kênh log trên Discord hoặc trang Dozzle.

### 7.6 Mức phạt
| Lần | Vi phạm nhẹ (chat, spam) | Vi phạm nặng (hack, lỗi game, vây giả) |
|---|---|---|
| 1 | Nhắc nhở hoặc cấm chat 30 phút | Cấm 7 ngày, thu hồi những gì có được do vi phạm |
| 2 | Cấm chat 1 ngày | Cấm hết mùa |
| 3 | Cấm vào 3 ngày | Cấm vĩnh viễn |

## 8. Bảng xếp hạng và phần thưởng mùa (chi tiết, CHỜ DUYỆT)

Nguyên tắc: tính **tự động** từ dữ liệu Towny/SiegeWar/Essentials (SiegeWar có sẵn sự kiện kết thúc cuộc vây, chiếm cờ, cướp thành),
ai cũng xem được điểm của mình bất cứ lúc nào, không có giải nào mua được bằng tiền thật.

### 8.1 Bá Chủ (quốc gia)

**Điểm Bá Chủ = điểm lãnh thổ (cộng mỗi tuần) + điểm chiến trận.**

| Nguồn | Điểm | Khi nào |
|---|---|---|
| Mỗi thị trấn đang kiểm soát (của mình + thuộc địa) | +10 | Chụp mỗi tối Chủ nhật 23:00 (sau phiên đánh cuối tuần) |
| Mỗi 10 ô đất (cả thuộc địa) | +1 | Cùng lúc chụp |
| Tấn công thắng một cuộc vây | +30 | Khi cuộc vây kết thúc |
| Phòng thủ thắng | +20 | Khi cuộc vây kết thúc |
| Thuộc địa khởi nghĩa thành công (cho quốc gia mới của thị trấn đó) | +20 | Khi cuộc vây kết thúc |
| Tuần 10 (đại chiến) | điểm lãnh thổ ×2 | Lần chụp cuối mùa |

- Vì sao cộng mỗi tuần: giữ đất lâu được thưởng, không phải chỉ ai mạnh nhất đêm cuối. Vì sao có điểm trận: đánh thắng mới lên nhanh.
- **Trận không tính điểm:** thị trấn bị vây có dưới 3 dân hoặc lập chưa đủ 3 ngày (chống lập thị trấn rác cho đồng minh đánh lấy điểm);
  trận admin xác định là dàn xếp.
- Hoà điểm: xét số trận thắng, rồi tổng số ô.
- Hai tuần hoà bình chỉ có điểm lãnh thổ.

### 8.2 Chiến Thần (cá nhân)

| Nguồn | Điểm |
|---|---|
| Hạ một người **có quân hàm** của phe kia trong vùng vây lúc có phiên | +3 |
| Ở trong nhóm chiếm cờ thành công (đủ 7 phút) | +5 |
| Bị hạ | không trừ (không ai ngại ra trận) |

- Hạ cùng một người: tối đa 3 lần mỗi phiên được tính (chống "nuôi" một người để cày).
- Tài khoản phụ không có điểm; hạ tài khoản phụ của chính mình hoặc người cùng quốc gia không có điểm.
- Ngoài vùng vây (PvP đất hoang giai đoạn A) **không** tính, để không khuyến khích giết người mới.

### 8.3 Đại Phú Hộ (cá nhân)
- Số tiền trong ví lúc đóng mùa (đã có hologram top 5 ở spawn).
- Tiền nhận từ tài khoản phụ của mình không tính; admin soát top 3 bằng lịch sử `/pay` trước khi công bố.

### 8.4 Kiến Trúc Sư (thị trấn)
- Tuần 9: thị trấn đăng ký tham gia (`/bxh dangky`), server tạo điểm tham quan cho từng thị trấn.
- Tuần 10: mỗi người đã chơi ≥ 5 giờ bỏ **1 phiếu** cho thị trấn **không phải của mình** (`/binhchon <thị trấn>`).
- Top 3 phiếu vào chung kết, ban giám khảo (admin + builder) chọn giải nhất. Phiếu từ tài khoản phụ không tính.

### 8.5 Xem bảng xếp hạng ở đâu
- **Hologram ở spawn**: top 5 Bá Chủ, top 5 Chiến Thần (cạnh bảng Top giàu nhất đã có).
- **`/bxh`**: menu (Java: rương, điện thoại: form): bảng từng giải, điểm của bạn và quốc gia bạn, chi tiết điểm đến từ đâu.
- **Tối Chủ nhật** sau khi chụp điểm: tin tổng kết tuần trong game (và Discord khi có bot): quốc gia lên/xuống hạng, trận nổi bật.
- Trang web (Trạm 6) có thể làm sau.

### 8.6 Phần thưởng: **chỉ đồ trang trí, đúng luật Mojang**

| Giải | Hạng 1 | Hạng 2–3 |
|---|---|---|
| Bá Chủ | Danh hiệu vàng `[Bá Chủ S1]` cho cả quốc gia, tượng ở Đền Vinh Danh, mẫu cờ riêng dùng ở mùa 2 | `[Á Chủ S1]`, tên khắc ở Đền Vinh Danh |
| Chiến Thần | `[Chiến Thần S1]` + hiệu ứng hạt khi hạ địch (mùa sau) | `[Dũng Tướng S1]` |
| Đại Phú Hộ | `[Phú Hộ S1]` | tên khắc ở Đền Vinh Danh |
| Kiến Trúc Sư | `[Kiến Trúc Sư S1]` cho cả thị trấn; công trình được dựng lại ở spawn mùa 2 (nếu chủ đồng ý) | tên khắc ở Đền Vinh Danh |
| Mọi người chơi ≥ 10 giờ | huy hiệu `[Mùa 1]` | |

- **Danh hiệu cả quốc gia/thị trấn** chỉ cho người là thành viên từ **trước tuần 9** và chơi ≥ 10 giờ trong mùa (không vào quốc gia đứng đầu ngày
  cuối để ăn theo).
- Danh hiệu là prefix/suffix LuckPerms toàn network, giữ mãi, người chơi bật/tắt hiển thị được.
- Không bao giờ thưởng: tiền, đồ, đất, quân hàm sang mùa sau.

### 8.7 Công bố và khiếu nại
- Đóng mùa Chủ nhật tuần 10 22:00 → khoá điểm → 3 ngày khiếu nại (admin soát top bằng CoreProtect, lịch sử giao dịch, log trận) → trao thưởng.

### 8.8 Phải code gì (chưa làm)
Module `Leaderboard` trong DuckyMoMoCore: chụp điểm lãnh thổ hằng tuần (Towny), nghe `SiegeEndEvent`/`BannerControlSessionEndedEvent`
(SiegeWar) và cái chết trong vùng vây, lưu MariaDB (bảng theo mùa), lệnh `/bxh`, `/binhchon`, placeholder cho hologram, tin tổng kết tuần.
Ước lượng: một buổi làm + một buổi thử với 2 quốc gia.

## 10. Liên minh Hoà Bình, Hội Bàn Tròn và tự quản (đang bàn 14/10, CHƯA CODE)

> Thay cho cơ chế "thị trấn tự bật hoà bình" của SiegeWar ở mục 2. Phần **[chốt]** do chủ server quyết trong buổi bàn 14/10;
> phần **[treo]** là đề xuất của Claude chờ trả lời. Code sau khi chốt hết (ước 2–3 buổi).

### 10.1 Nguyên tắc tự quản [chốt]
- Người chơi quản lý lẫn nhau; **admin chủ yếu quan sát**, chỉ xử hack, lỗi game, gian lận kỹ thuật (tài khoản phụ bị giấu…) và việc ngoài luật server.
- Admin không sửa quyết định của các hội, trừ khi quyết định đó dựa trên gian lận kỹ thuật. Admin không vào phe nào.
- Phân vai [treo]: Hội Bàn Tròn (án giết dân hoà bình, vây giả, khiếu nại bảng xếp hạng); Hội đồng Hoà Bình (việc nội bộ vùng hoà bình);
  vua/thị trưởng (nội bộ quốc gia, Towny có sẵn); Helper/Mod (chat, quấy rối, tên kích động); Admin (gian lận kỹ thuật).
- Kiềm chế lẫn nhau [treo]: phiếu bầu công khai có tên; người dính líu không được bỏ phiếu; Hội Bàn Tròn có thể đòi thu hồi giấy phép (2/3);
  đại diện hoà bình nhiệm kỳ 1 tháng, tối đa 2 nhiệm kỳ liền, bãi nhiệm 2/3; ghế Hội Bàn Tròn thay theo bảng Bá Chủ mỗi tuần;
  kháng án chỉ khi có bằng chứng mới, mở lại 1 lần.
- Tự động thay thế khi một hội tê liệt [treo]: không đủ người họp → mức sàn; đại diện không chọn nơi họp → bốc ngẫu nhiên;
  không ai ứng cử → thị trưởng thị trấn hoà bình lớn nhất tạm giữ; giấy phép hết phí → tự treo.

### 10.2 Liên minh Hoà Bình [chốt hướng]
- Cả server chỉ có **một Liên minh Hoà Bình**. Muốn hoà bình **bắt buộc** gia nhập liên minh; không thị trấn nào tự bật hoà bình được.
- Đề xuất [treo]: liên minh là quốc gia do server quản lý, thủ đô = khu spawn (kinh đô); `/hoabinh` để xin vào/rời; plugin tự bật/tắt hoà bình
  (lệnh admin của SiegeWar); vào chờ 2 ngày, rời mất ngay, 14 ngày sau mới vào lại; tắt cơ chế thu phục (subvert) của SiegeWar;
  thị trấn hoà bình không tính điểm Bá Chủ.
- **Đất hoà bình mở rộng mãi được, nhưng giá tăng theo** [chốt]; **đất mới phải liền sát đất cũ** (lan rộng như vết dầu, không cách nhau) [chốt].
  Đề xuất [treo]: vùng hoà bình = đất các thị trấn trong liên minh; giá một ô = 25 + 0,5 × tổng số ô của cả liên minh; chạm chéo không tính
  là liền kề; thị trấn hoà bình mới cũng phải lập sát vùng cũ; phí bảo hộ hằng ngày theo ô; đất đã có chủ thì không lấn được.
- **Vùng vây được phép trùm lên đất hoà bình, không miễn trừ** [chốt]: mục tiêu chính vẫn là công thành, cờ cắm gần thì dân hoà bình
  phải **di tản** (SiegeWar sẵn có: dân hoà bình trong vùng vây bị cảnh báo rồi "bệnh chiến tranh"). Giết dân hoà bình trong vùng vây lúc
  có phiên không mở án. Báo trước [treo]: thứ 5 khi cắm cờ báo toàn server + tin riêng/thư cho dân hoà bình có đất trong vùng;
  vẽ vùng vây lên bản đồ web; nhắc 1 giờ và 10 phút trước phiên; Discord.
- Dân hoà bình chơi gì [treo]: thương nhân, thợ thủ công/nông trại, kiến trúc sư (xây thuê), dịch vụ (nhà trọ, đường sá), ngoại giao
  (1 ghế Hội Bàn Tròn), tổ chức sự kiện, thám hiểm (ra ngoài vùng tự chịu rủi ro). Không: đánh trận, quân hàm, chủ động giết người
  (chỉ tự vệ), làm tay sai/kho/do thám cho riêng một quốc gia.
- Nâng tầm vùng hoà bình [treo]: chợ đấu giá `/ah` chỉ mở trong vùng hoà bình, giá bán server ở chợ kinh đô +20%, shop rương miễn thuế,
  cho thuê ô chợ, sự kiện và toà nghị sự ở kinh đô; thương quán (ô sứ quán Towny) ở đất các quốc gia; kỳ quan; cửa hàng trang trí và
  danh hiệu mua bằng xu (không bán bằng tiền thật); giải "Thịnh Vượng".
- **Giấy phép tổ chức** [chốt hướng: quyền thêm chỉ cho tổ chức được duyệt]. Đề xuất [treo]: ≥ 3 người lập tổ chức; Hội đồng Hoà Bình
  duyệt; phí hằng tuần; số suất có hạn; hết mùa hết hạn. Loại: Thương Hội (thêm shop, thêm món `/ah`, NPC bán hàng, `/quangcao`),
  Tổ chức Sự kiện (`/sukien`, đấu trường không mất đồ, thưởng bằng quỹ tự bỏ), Toà soạn (tuỳ chọn). Không quyền nào tạo đồ/tiền từ
  hư không hoặc mang tính admin. Cần kiểm tra tên quyền của QuickShop, zAuctionHouse, Shopkeepers.

### 10.3 Án giết dân hoà bình và Hội Bàn Tròn
- **Giết dân hoà bình vẫn làm được nhưng bị phạt; Hội Bàn Tròn gồm chủ các thành lớn quyết mức phạt** [chốt].
- **Vi phạm gần giờ công thành thì có thể bị cấm công thành** [chốt]. Cấm công thành = **không được vào vùng vây**, kể cả ngoài giờ đánh
  (đứng xem cũng che tầm nhìn, chắn đường) [chốt]. Thị trấn nhà nằm trong vùng vây: được ở trong đất thị trấn mình, không bước ra đất
  hoang trong vùng [chốt]. Đề xuất [treo]: đang có án chưa xử thì đình chỉ tham chiến; Hội có mức "cấm công thành 1–3 tuần"; vua/tướng
  bị cấm thì không cắm cờ vây được.
- **Hội Bàn Tròn họp trực tiếp tại một địa điểm trong vùng hoà bình (ngẫu nhiên hoặc đại diện hoà bình chọn); phải có mặt, không bỏ
  phiếu từ xa bằng lệnh** [chốt]. Đề xuất [treo]: họp thứ 4 21:00, 45 phút; họp bất thường báo trước 24 giờ; bỏ phiếu khi đứng trong
  ~16 block quanh "bàn tròn"; cử sứ thần cùng quốc gia họp thay; cần ≥ 3 ghế; tuỳ chọn chặn dịch chuyển vào 150 block quanh nơi họp
  từ 30 phút trước; người xem được tới dự, kết quả báo toàn server.
- Đề xuất [treo]: tự vệ (nạn nhân đánh trước trong 30 giây) không thành án; chỉ tính giết trực tiếp; mức sàn tự động 100 xu bồi thường
  + cấm vào đất hoà bình tới khi xử; Hội chỉ được phạt thêm (phạt nặng, truy nã, tù, lưu đày, cấm công thành), không được tha mức sàn;
  tái phạm gấp đôi mức sàn; thành viên Hội = thủ lĩnh 5 quốc gia đứng đầu Bá Chủ + 1 đại diện hoà bình; tài khoản phụ cài vào để gài
  bẫy thì chủ bị cấm hết mùa và án được huỷ.

### 10.4 Tài khoản [chốt]
- **Tạm cho phép mỗi người 2 tài khoản** để kéo người chơi; **khi server đông thì siết còn 1**.
- Không có cách kỹ thuật chặn theo máy (Java không bản quyền không gửi mã máy; qua playit không thấy IP; IP ở Việt Nam hay dùng chung).
  Lớp bảo vệ chính đề xuất [treo]: mọi quyền lực tính theo **người chơi hoạt động thật** (giờ chơi không tính AFK): đất 8 ô/dân chỉ tính
  dân chơi ≥ 3 giờ/7 ngày; lập thị trấn ≥ 3 giờ; quân hàm và bỏ phiếu ≥ 5 giờ; thị trấn cần ≥ 3 dân hoạt động mới tính điểm/phiếu;
  vào thị trấn mới 3 ngày chưa có quyền, rời thị trấn 3 ngày mới vào nơi khác. Khi lên VPS: AuthMe tối đa 3 đăng ký/IP/ngày, nhiều tài
  khoản cùng IP chỉ đánh dấu cho staff xem. Đề xuất: người chơi tự khai tài khoản phụ bằng lệnh, tài khoản chính xác nhận (thay cho
  admin ghi bằng `/dmm phu`).

## 11. Bảng chốt 14/10 (45 mục): đè lên các chỗ [treo] ở mục 8 và 10

> Chủ server trả lời bảng 45 mục. "Cài đặt" = phải có trên trang Cài đặt vì chưa chốt con số/ngày giờ.

**A. Liên minh Hoà Bình**
1. Liên minh do **server quản lý, thủ đô là khu spawn**. Ý thêm của chủ server: **lập một hội được giao quyền xử lý vấn đề gameplay**
   (tức Hội đồng Hoà Bình điều hành thay server; admin không trực tiếp quản).
2. Vào chờ 2 ngày; rời mất hoà bình ngay; 14 ngày sau mới vào lại.
3. **Tắt thu phục** (subvert) của SiegeWar (chốt sau khi giải thích): đã áp dụng 14/10, `peaceful_towns.subvert_enabled = false`, có trên trang Cài đặt.
4. Thị trấn hoà bình không tính điểm Bá Chủ.
5. Giá ô đất hoà bình = 25 + 0,5 × tổng số ô của liên minh (tạm, nên là cài đặt).
6. Chạm chéo không tính là liền kề.
7. Phí bảo hộ 0,5 xu/ô/ngày (tạm, cài đặt).
8. Thông báo vây thành: làm hết. **Báo trước 2 ngày so với ngày công thành**, là cài đặt (lịch công thành chưa chốt).
9. Đặc quyền thương mại **(b)**: chợ kinh đô bán cho server +20%; shop rương trong vùng hoà bình miễn thuế, nơi khác 5%; cho thuê ô chợ;
   sự kiện + toà nghị sự ở kinh đô; **chợ đấu giá `/ah` chỉ mở trong vùng hoà bình**.
10. Hạn chế dân hoà bình (a): không tiền đồn; chỉ tự vệ (giết người không phải tự vệ → bị đuổi); cấm làm tay sai. Không giảm tiền nghề.
11. Án chỉ mở khi dân hoà bình bị giết **trong đất hoà bình + dải đệm 100 block**; đất thị trấn hoà bình luôn tắt PvP.
12. Giấy phép (tạm a): ≥ 3 người lập tổ chức, Hội đồng Hoà Bình duyệt (không cần admin), 100 xu/tuần, hết mùa hết hạn;
    Thương Hội (3 suất), Tổ chức Sự kiện (2 suất).
13. Đại diện hoà bình: thị trưởng hoà bình bầu, 1 tháng, tối đa 2 nhiệm kỳ liền, bãi nhiệm 2/3.
14. Làm trước: thương quán (ô sứ quán) + cửa hàng trang trí và danh hiệu mua bằng xu. Để sau: kỳ quan, giải Thịnh Vượng.

**B. Hội Bàn Tròn và án**
15. Thành viên: thủ lĩnh 5 quốc gia đứng đầu Bá Chủ + 1 đại diện hoà bình.
16. Mức sàn tự động (bồi thường + cấm vào đất hoà bình tới khi xử, tái phạm ×2): **số tiền là cài đặt** (chưa cân kinh tế).
17. Tự vệ 30 giây không thành án; chỉ tính giết trực tiếp.
18. Mức phạt: phạt nặng ×3, truy nã 3 ngày, tù 30–60 phút, lưu đày 14 ngày, cấm công thành 1–3 tuần.
19. Hội chỉ được phạt thêm, không tha mức sàn.
20. Đang có án chưa xử thì đình chỉ tham chiến; vua/tướng bị cấm không cắm cờ vây.
21. **Giờ họp là cài đặt** (mặc định thứ 4 21:00); **không giới hạn thời lượng, xử hết vụ thì tan**. Họp bất thường báo trước 24 giờ.
22. Địa điểm: đại diện hoà bình chọn trong các thị trấn hoà bình có bàn tròn, hạn 24 giờ trước; quá hạn bốc ngẫu nhiên.
23. Cho phép cử sứ thần cùng quốc gia họp thay.
24. Chặn dịch chuyển vào 150 block quanh nơi họp từ 30 phút trước giờ họp.
25. Cần ≥ 3 ghế có mặt.
26. Kháng án: chỉ khi có bằng chứng mới, đa số phiếu, mở lại 1 lần.
27. Hội Bàn Tròn được đòi thu hồi giấy phép tổ chức (2/3).

**C. Bảng xếp hạng**: 28 Bá Chủ, 29 Chiến Thần như mục 8; 30 Kiến Trúc Sư = người chơi bỏ phiếu thuần (≥ 5 giờ, 1 phiếu, nhiều nhất thắng);
31 phần thưởng như mục 8.6; 32 giải Đệ Nhất Thương Nhân để sau; 33 khiếu nại: Hội Bàn Tròn (luật chơi), admin (gian lận).

**D. Tài khoản**
34. Đếm giờ chơi thật, không tính AFK.
35. Dân hoạt động 3 giờ/7 ngày; lập thị trấn 3 giờ; quân hàm và bỏ phiếu 5 giờ; vào/rời thị trấn chờ 3 ngày.
36. Người chơi tự khai tài khoản phụ bằng lệnh, tài khoản chính xác nhận.
37. Khi lên VPS: **tối đa 2 đăng ký mỗi IP mỗi ngày**; nhiều tài khoản cùng IP chỉ đánh dấu cho staff.

**E. Đất đai và chiến tranh**
38. Nhà chính hai thị trấn cách ≥ 10 ô (160 block): đã áp dụng 14/10 (`min_distance_between_homeblocks = 10`, trang Cài đặt).
39. Vòng 300 block quanh spawn chỉ dành cho Liên minh Hoà Bình.
40. Lập quốc gia: 1 thị trấn có ≥ 3 dân hoạt động. **Cắm cờ vây thành (chinh phục): quốc gia phải có ≥ 2 thị trấn**; phòng thủ ai cũng đánh.
    Cả hai là cài đặt. (Chốt 14/10.)
41. Tuần 10 đại chiến (3 cuộc vây/quốc gia, thêm phiên thứ 6 20:00): **là cài đặt** (chưa chốt ngày giờ).

**F. Chơi lại `/choilai`**: 42 7 ngày/lần, tối đa 3 lần/mùa; 43 **(b)** quà tân thủ + tiền khởi đầu chỉ lần đầu trong mùa
(chưa biết giá trị đồng xu nên chọn chặt); 44 bắt tự dỡ shop rương và rút hàng đấu giá trước.
Reset xoá: đồ, rương Ender, XP, tiền về mức khởi đầu, cấp nghề, nhà, giường, rời thị trấn + mất quân hàm, giờ chơi thật, chờ `/tpr`;
về spawn + bảo vệ người mới. Giữ: rank donate, danh hiệu, **án và lệnh cấm**, tài khoản. Điều kiện: không là thị trưởng/vua, không
đang đánh nhau, không có án chưa xử, thị trấn không đang bị vây. Xác nhận `/choilai xacnhan` trong 30 giây; lưu bản sao dữ liệu 7 ngày.

**G.** 45 commit phần đã làm (14/10).

**Đã code đợt 1 (14/10, chưa thử trong game):** giờ chơi thật (`Activity`, /hoatdong), luật đất đai (`TownRules`: lập thị trấn 3 giờ,
quốc gia 3 dân hoạt động, cắm cờ chinh phục cần 2 thị trấn, vòng 300 block quanh spawn chỉ dành cho `reserved-nation`, đổi thị trấn chờ
3 ngày, đất theo dân hoạt động bằng bonus âm), phong quân hàm theo giờ chơi thật + ở thị trấn đủ 3 ngày, tự khai tài khoản phụ
(/taikhoanphu, tối đa 2), /choilai (`PlayerReset` + `Integrations`). Tất cả có trên trang Cài đặt (Thị trấn > "Đất đai và người chơi thật",
Hệ thống > "Chơi lại từ đầu"). Tắt thu phục (3) và khoảng cách thị trấn (38) đã áp dụng. Giờ chơi thật bắt đầu đếm từ 14/10:
người chơi cũ đều có 0 giờ (admin/op bỏ qua bằng quyền `duckymomo.town.bypass`).

**Đã code đợt 2 (14/10, chưa thử trong game): Liên minh Hoà Bình** (`PeaceAlliance`): /hoabinh vao|roi|danhsach; không ai tự bật hoà bình;
plugin bật/tắt hoà bình bằng `swa town <t> setpeaceful`; vào chờ 2 ngày, rời mất ngay, 14 ngày mới vào lại; thị trấn phải chạm đất liên minh;
lập thị trấn trong vòng 300 block quanh spawn phải sát đất hoà bình và tự vào liên minh; không tiền đồn; giá ô = 25 + 0,5 × tổng ô các thị trấn
hoà bình (không tính kinh đô); phí bảo hộ 0,5 xu/ô/ngày cộng vào phí duy trì; đất hoà bình luôn tắt PvP; /ah chỉ mở trong đất hoà bình;
/sell trong kinh đô +20%; shop rương trong đất hoà bình miễn thuế, ngoài 5%; báo vây thành (toàn server + tin/thư cho dân hoà bình có đất trong
vùng vây, nhắc trước 2 ngày, 60 và 10 phút trước phiên); vùng vây vẽ đỏ, liên minh tô xanh trên bản đồ web. SiegeWar
`allow_peaceful_capitals: true` (kinh đô hoà bình). **Chưa lập liên minh**: admin đứng giữa spawn gõ `/dmm hoabinh setup` rồi
`/dmm hoabinh claim 14`. Còn lại cho đợt 3: Hội đồng Hoà Bình, đại diện, giấy phép tổ chức, án giết dân hoà bình (dải đệm 100 block),
Hội Bàn Tròn; mục 14 (thương quán, cửa hàng trang trí bằng xu).

**Đã code đợt 3-4 + mục 14 (14/10, CHƯA THỬ TRONG GAME):**
- `PeaceCouncil` (/hoidong): thị trưởng thị trấn hoà bình có >= 3 dân hoạt động có phiếu; bầu đại diện 30 ngày, bầu trong 2 ngày cuối,
  tối đa 2 nhiệm kỳ liền, bãi nhiệm 2/3, không ai đắc cử thì thị trưởng thị trấn lớn nhất tạm giữ; kiến nghị bỏ phiếu từ xa 3 ngày:
  duyệt giấy phép, đuổi thị trấn (quá bán, thị trấn bị đuổi không bỏ phiếu), bãi nhiệm.
- `Organizations` (/tochuc, /quangcao, /sukien): tổ chức >= 3 dân hoà bình, giấy phép Thương Hội (3 suất: QuickShop 30 shop thay vì 10,
  `zauctionhouse.max.15`, /quangcao 1 lần/giờ) và Tổ chức Sự kiện (2 suất: /sukien tao|thuong|dautruong|ket, chữ nổi, dịch chuyển 24 giờ);
  phí 100 xu/tuần từ quỹ (/tochuc gop), hết tiền thì treo, hết mùa hết hạn; nhóm LuckPerms `giayphep-thuonghoi|sukien` (server=banghoi-1).
  Chỉ /sukien dautruong mới đổi ô thành đấu trường; Towny `keep_inventory_on_death_in_arena: true`.
- `Leaderboard` (/bxh, /binhchon, /danhhieu): điểm như mục 8 (chụp tuần Chủ nhật 23:00, SiegeEndEvent, chết trong vùng vây, chiếm cờ),
  /bxh dongmua → 3 ngày → /bxh traothuong trao danh hiệu (LuckPerms suffix khi chọn bằng /danhhieu). Placeholder `%dmm_bachu_N_ten|diem%`,
  `%dmm_chienthan_N_...%` (/bxh hologram in lệnh tạo hologram). **Phải đặt `leaderboard.season-start`** (trang Cài đặt) khi mở mùa.
- `Justice` (/bantron, /an): như mục 10.3/11B; mức phạt: giu, phatnang, truyna, tu, luuday, camct1-3; kiến nghị trudiem/thuhoi/mở lại;
  không có việc thì không họp. Ghế hoà bình vẫn được bỏ phiếu ở vụ có nạn nhân hoà bình (`peace-seat-votes-on-peace-victims`), nếu không
  ghế này bị tránh ở mọi vụ (quy tắc "cùng quốc gia với nạn nhân"). **Admin phải đặt nhà tù**: `/setjail nhatu` (Essentials, tính giờ lúc online).
- `DecorShop` = **Cửa hàng Kinh Đô** (đổi 14/10 tối theo chủ server): 3 mục vật liệu cơ bản (10 món, 64 cái, ví dụ 100 xu = 64 đất; chỉ
  server bán, giá mỗi cái phải > giá /sell không thì tự bỏ), trang trí (18), danh hiệu (3). Mọi người mua qua **NPC ở khu shop spawn**
  (`/dmm shopnpc`); **rank ủng hộ được gõ /cuahang nhưng chỉ khi đứng trong kinh đô** (quyền `duckymomo.cuahang` cho nhóm namtuoc,
  các bậc trên thừa kế). Đây là ngoại lệ duy nhất không thuần trang trí trong rank, chủ server chốt; rủi ro thấp vì không mua từ xa được.
- **Dân làng đổi đồ (Shopkeepers, đổi vật lấy vật, không dùng tiền)**: hướng của chủ server là cây nâng cấp: đồ thường → đồ chế biến
  → "mảnh" → mảnh + nguyên liệu khác ra giáp/vũ khí phù phép. Bảng Claude đưa (đá→đá nhẵn, Mảnh Rèn/Tinh Thể/Ma Thuật, đồ cấp III…) CHỈ
  LÀ VÍ DỤ, chi tiết cần tham khảo thêm. Lưu ý khi chốt: mỗi mảnh cần ít nhất một nguyên liệu khó farm (máy farm đá cuội/sắt), đồ đổi
  không vượt bàn phù phép; Shopkeepers mặc định đổi không giới hạn lượt (chưa kiểm tra addon có giới hạn ngày không).
- Thương quán: dùng ô sứ quán (embassy) có sẵn của Towny, không cần code.
- Chưa làm: trang web xem hồ sơ Hội Bàn Tròn (trong game xem bằng /bantron lichsu); giải Đệ Nhất Thương Nhân, kỳ quan (để sau).

## 9. Những việc phải làm sau khi chốt
1. Sửa cấu hình theo các mục **[đề xuất]** đã chốt (khoảng cách giữa thị trấn, cấm sát spawn; Nether/End chờ chốt). Đã xong: công tắc vây thành, PvP đất hoang, giờ phong quân hàm, tài khoản phụ.
2. Viết vào `/huongdan` (chương Luật và Bang Hội Chiến) + trang Cẩm nang.
3. Code thêm (nếu chốt): chặn phong quân hàm khi chơi chưa đủ giờ; báo trước phiên đánh; bảng xếp hạng quốc gia ở spawn.
4. Chơi thử với nhóm nhỏ: 2 quốc gia, rút lịch phiên còn vài phút, kiểm tra điểm, cướp, chiếm, khởi nghĩa (bước 3 của `banghoi-design.md`).
