# Dự án Server Minecraft – Bản tổng hợp ý tưởng & kế hoạch

> Tài liệu tóm tắt các thảo luận ban đầu, dùng làm "bối cảnh" khi chuyển sang Claude Project mới.
> Cập nhật: 10/2026. Giá cả và thông tin bên thứ ba là **ước lượng tham khảo**, cần kiểm tra lại trước khi chi tiền.

---

## 0. Bối cảnh người làm dự án

- Đang kinh doanh quán đồ ăn vặt (nem chua rán, bánh rán…).
- Là dev, đang tự xây **app quản lý bán hàng** gồm các module: đơn hàng, sản phẩm, khách hàng, cài đặt hệ thống, báo cáo, thuế, kết nối bên thứ 3, quản lý đa cửa hàng. Đã có kinh nghiệm làm thanh toán, báo cáo và thuế.
- Từng chơi Minecraft khoảng 10 năm trước, thích **Factions** (lập phe, đi war với phe khác), sinh tồn, xây dựng, cày tiến độ, chơi cộng đồng và nhập vai.
- Có thể dành **hơn 15 giờ mỗi tuần** cho dự án (ngoài thời gian ở quán).
- **Chưa tính** tới việc tự làm content (TikTok/YouTube).

---

## 1. Các quyết định đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| 1 | Làm **server Java (Paper) cho cả người chơi điện thoại vào** qua **GeyserMC + Floodgate** | Plugin Java nhiều nhất. Người chơi điện thoại (Bedrock) ở Việt Nam rất đông |
| 2 | **Tầm nhìn dài hạn: network nhiều chế độ** (lobby, rồi chọn chế độ) | Giống Hypixel hay các server Việt có nhiều chế độ |
| 3 | **Build từng chế độ một**, không mở tất cả cùng lúc | Tránh chia nhỏ người chơi, tránh quá tải vận hành |
| 4 | **Dựng khung network ngay từ đầu** (proxy, lobby, database chung) dù mới có 1 chế độ | Sau này thêm chế độ chỉ là gắn thêm server con |
| 5 | **Chế độ chủ lực đầu tiên (đề xuất): "Bang hội chiến"**, tức Factions kiểu mới, chạy theo mùa | Khớp sở thích và văn hóa bang hội hay công thành của người chơi Việt. *Cần xác nhận lại khi thiết kế luật chơi* |
| 6 | **Giai đoạn đầu chạy trên máy nhà**, chưa thuê máy chủ | Chưa cần tối ưu cho người chơi. Gần như 0 đồng |
| 7 | **Kiếm tiền theo đúng luật Mojang**: chỉ bán đồ trang trí và tiện ích nhỏ, **không pay-to-win** | Bền vững lâu dài, có thể làm content hay hợp tác. Pay-to-win khiến server chết nhanh |

---

## 2. Nghiên cứu thị trường (tóm tắt)

### Người chơi
- Minecraft: hơn 300 triệu bản đã bán, khoảng 170–200 triệu người chơi mỗi tháng (số liệu công bố vài năm gần đây).
- Độ tuổi chính 8–18 (đông nhưng ít tiền). Nhóm 18–30 chịu chi hơn.
- Ở Việt Nam rất nhiều người chơi trên **điện thoại (Bedrock/PE)**, nhiều người không có tài khoản bản quyền.

### Thể loại
- **Cổ điển, vẫn đông nhưng bão hòa**: Survival, Skyblock, BedWars, Prison, Towny, Factions cổ điển.
- **Mới nổi**: Lifesteal SMP, Survival PvP có kinh tế (kiểu DonutSMP), Crystal PvP / Practice, BoxPvP, Cobblemon (mod Pokémon), Anarchy, SMP theo mùa.
- Server Việt chủ yếu là Survival, Skyblock, Minigame, Towny. **Mảng Lifesteal, BoxPvP, bang hội chiến kiểu mới còn ít.**

### Luật kiếm tiền của Mojang (Minecraft Usage Guidelines)
- ✅ **Được**: thu phí vào server (ai trả cũng như nhau), donate không kèm ưu đãi riêng, bán đồ trang trí, tiền ảo chỉ dùng mua đồ trang trí, quảng cáo hoặc tài trợ.
- ❌ **Không được**: bán vũ khí, khả năng, lợi thế trong gameplay, tiền ảo đổi ra đồ mạnh. **Không được bán cape** (Mojang giữ riêng cape).
- **Cách Mojang xử lý**: đưa server vào blocklist, chỉ có tác dụng với launcher chính chủ. **Việc xử lý rất không đều**, server nhỏ ở Việt Nam hiếm khi bị đụng tới. Rủi ro tăng khi server lớn hoặc muốn hợp tác chính thức.
- **Rủi ro thực tế ở Việt Nam lớn hơn Mojang**: phụ huynh khiếu nại, hộp quà ngẫu nhiên dễ bị coi là cờ bạc, cổng thẻ hoặc tài khoản ngân hàng bị khóa, thuế, và server pay-to-win chết nhanh.

### Cách kiếm tiền theo thể loại (tóm tắt)
| Thể loại | Nên bán | Không nên bán |
|---|---|---|
| Lifesteal | Hiệu ứng khi hạ người, thông báo chết, tag, gói mùa đồ trang trí | Tim, hồi sinh, mở khóa |
| Survival kinh tế + PvP | Rank trang trí, `/nick`, pet. (Vùng xám: thêm `/home`, hàng đợi ưu tiên) | Spawner, tiền trong game, hộp quà có đồ mạnh |
| Crystal PvP / Practice | Hiệu ứng, khung bảng xếp hạng, tài trợ giải đấu | Giải thu phí tham gia rồi trả thưởng tiền (dễ bị coi là cờ bạc) |
| BoxPvP | Giao diện hộp, hiệu ứng, gói mùa đồ trang trí | Mọi thứ liên quan tiến độ |
| Cobblemon | Chỉ nhận donate (rủi ro bản quyền Pokémon) | Pokémon, bóng bắt, hộp quà |
| Anarchy | Hàng đợi ưu tiên, màu tên | — |
| SMP theo mùa | Phí vào cửa, gói thành viên, content | — |
| **Bang hội chiến** | **Đồ trang trí cho cả bang** (cờ, màu tên bang, hiệu ứng lãnh thổ, giao diện nhà chính), gói mùa, rank trang trí | TNT, sức mạnh chiếm đất, spawner, khiên bảo vệ |

### Thực tế doanh thu
- Thường chỉ **khoảng 1–5% người chơi** chịu nạp tiền.
- Server mới thường lỗ hoặc hòa vốn trong 3–6 tháng đầu.
- Muốn tăng doanh thu phải tăng **người chơi và tỉ lệ quay lại**, không phải tăng giá.

---

## 3. Thiết kế "Bang hội chiến" (định hướng, chưa chi tiết)

- Lập **bang hội**, chiếm lãnh thổ, xây thành, nâng cấp lãnh thổ và cấp bang.
- **Chiến tranh theo lịch**: chỉ đánh chiếm trong khung giờ cố định (ví dụ tối thứ 7 lúc 20h–22h), ngoài giờ lãnh thổ được bảo vệ. Không cướp lúc người chơi offline.
- **Công thành có mục tiêu** (giữ cờ hoặc chiếm điểm), không bắt buộc dùng pháo TNT (điện thoại không làm được).
- **Combat kiểu 1.8** (OldCombatMechanics) để người chơi điện thoại và PC đánh ngang nhau hơn.
- **Theo mùa** 2–3 tháng: tổng kết, trao danh hiệu, reset.
- Có chức vụ trong bang, liên minh, ngoại giao, buôn bán giữa các bang.
- **Khởi đầu**: mời sẵn vài nhóm hoặc bang trước khi mở. Cần khoảng 30 người chơi thường xuyên trở lên thì bang chiến mới vui.
- **Phương án kỹ thuật**: Towny + SiegeWar, **hoặc** plugin Factions có sẵn kèm phần bang chiến theo lịch tự viết (phần tự viết có thể đem bán sau).
- **Phương án dự phòng** nếu thấy quá nặng: Survival kinh tế + bang hội nhẹ (PvP ở khu riêng).

---

## 4. Kiến trúc kỹ thuật (network)

```
   Java (PC)   Bedrock (điện thoại)
        \        /
   PROXY: Velocity + Geyser + Floodgate
        |
   ┌────┴─────┬───────────────┬──────────┬──────────┐
 LOBBY   BANG HỘI CHIẾN   SURVIVAL nhỏ   ONEBLOCK   BEDWARS
                            (sau)         (sau)      (sau cùng)
        |
   MariaDB (MySQL) + Redis dùng chung: tài khoản, rank, đồ trang trí, party, chat
```

### Nguyên tắc cần làm đúng từ đầu
- Dùng **proxy ngay cả khi chỉ có 1 chế độ**.
- **Rank và dữ liệu người chơi lưu trên MySQL chung**, không lưu file riêng từng server.
- **Tiền và vật phẩm trong game tách riêng theo chế độ**. **Rank và đồ trang trí dùng chung toàn network**.
- Xử lý đúng **tên người chơi Bedrock** (Floodgate thêm tiền tố, ví dụ `.TenNguoiChoi`) trong database, web store, bảng xếp hạng.
- Menu dùng **Bedrock Forms** cho người chơi điện thoại.
- Chống hack phải **hỗ trợ Geyser** để không bắt nhầm người chơi điện thoại.

### Lộ trình mở chế độ
| Giai đoạn | Chế độ | Điều kiện để sang giai đoạn sau |
|---|---|---|
| 1 | Lobby + Bang hội chiến | Ổn định khoảng 30–50 người online giờ cao điểm |
| 2 | + Survival nhỏ hoặc OneBlock | Tổng khoảng 80–100 người online, có admin phụ |
| 3 | + BedWars hoặc minigame | Hàng chờ trận dưới 1–2 phút |

Không nên chọn Crystal PvP (điện thoại khó chơi) hay Cobblemon (cần mod, chỉ chạy trên Java) làm chế độ chính. Pokémon (Cobblemon) có thể là chế độ bổ sung sau.

👉 **Thiết kế khung chi tiết để thêm chế độ dễ dàng (kể cả Pokémon trên Fabric): xem `network-architecture.md`.**

---

## 5. Công cụ sẽ dùng

### Hạ tầng (đều miễn phí, trừ sao lưu)
- Ubuntu Server, Docker, **Pterodactyl Panel**
- **Velocity** (proxy), **Paper** hoặc Purpur, **Geyser + Floodgate**, ViaVersion
- **MariaDB + Redis**
- Sao lưu: restic hoặc rclone, đẩy lên kho lưu trữ đám mây (khoảng 50–100k/tháng)
- Theo dõi: spark, Uptime Kuma
- **Git** để quản lý cấu hình (repo riêng tư, không đưa mật khẩu lên)

### Plugin chung
- LuckPerms, Vault, PlaceholderAPI, DeluxeMenus, TAB
- FancyNpcs hoặc Citizens, DecentHolograms (lobby)
- Grim (chống hack, có hỗ trợ Geyser)
- DiscordSRV, NuVotifier
- Plugin cosmetics trả phí (khoảng 15–30 USD, trả 1 lần)

### Plugin Bang hội chiến
- Towny + SiegeWar (hoặc Factions kèm phần tự viết)
- EssentialsX, WorldGuard, WorldEdit
- **CoreProtect** (bắt buộc, để khôi phục khi bị phá)
- Chunky (tạo trước bản đồ), OldCombatMechanics

### Chế độ sau này
- Skyblock / OneBlock: BentoBox hoặc SuperiorSkyblock2
- BedWars: BedWars1058 hoặc BedWars2023

### Thanh toán
- **Thẻ cào**: DotMan (MineVN, mã nguồn mở) kèm một cổng gạch thẻ. **Cổng thu khoảng 15–30% mỗi thẻ.**
- **VietQR**: web store tự viết + dịch vụ báo giao dịch ngân hàng (SePay, Casso…, cần kiểm tra API và giá). Nên khuyến khích chuyển khoản (ví dụ tặng thêm 10%) vì phí thấp hơn nhiều so với thẻ cào.
- **Phương án làm sẵn**: Tebex (có MoMo, Internet Banking Việt Nam). Phí nền tảng 5% mỗi giao dịch, chưa gồm phí thanh toán.
- Gợi ý: **tự viết web store**, tận dụng kinh nghiệm từ app bán hàng.

### Phát triển và nội dung
- IntelliJ IDEA Community, Gradle, Paper API (Java/Kotlin)
- Discord (cộng đồng), OBS, CapCut (ghi hình, dựng video, **nên ghi lại các trận bang chiến ngay từ đầu**)

---

## 6. Giai đoạn đầu: chạy trên máy nhà

| Giai đoạn | Dùng máy nhà? |
|---|---|
| Dựng network, cài plugin, viết plugin, thiết kế luật chơi | ✅ Rất hợp |
| Thử kín với bạn bè (khoảng 5–20 người) | ✅ Được, cần tunnel |
| Mở công khai | ⚠️ Không nên, chuyển sang thuê máy chủ |

- **Cấu hình đủ dùng**: CPU 4 nhân trở lên, **16 GB RAM**, SSD.
- **Lưu ý mạng gia đình ở Việt Nam**: có thể bị CGNAT (không mở port được), IP thay đổi, **lộ IP và bị DDoS làm sập cả mạng nhà**. Nếu quán dùng chung đường mạng thì máy bán hàng và thanh toán QR cũng bị ảnh hưởng, nên tách riêng mạng quán. **Không mở** cổng database, RCON hay panel ra Internet.
- **Cách cho người ngoài vào**:
  - **playit.gg** (khuyên dùng giai đoạn thử): hỗ trợ cả Java (TCP) lẫn Bedrock (UDP), giấu IP nhà, có gói miễn phí.
  - TCPShield: chỉ hỗ trợ Java, không hợp với người chơi điện thoại.
  - VPS rẻ chạy Velocity, kết nối về máy nhà qua WireGuard: khoảng 100–200k/tháng, giấu IP hoàn toàn.
- **Để sau này chuyển máy dễ**: chạy bằng Docker hoặc Pterodactyl, lưu cấu hình bằng Git, sao lưu định kỳ, dùng tên miền riêng từ đầu (ví dụ `play.tenserver.vn`).

---

## 7. Chi phí dự kiến

### Giai đoạn 0: máy nhà
| Hạng mục | Chi phí |
|---|---|
| Máy chủ | 0 (tốn thêm tiền điện) |
| Tunnel playit.gg | 0 (gói miễn phí) |
| Plugin | 0 (bản miễn phí) |
| Tên miền | Khoảng 300–400k/năm |

### Giai đoạn 1: ra mắt công khai
- **Một lần**: plugin trả phí 0,5–1,5 triệu; lobby và spawn 0–5 triệu; logo và web 0–1 triệu. **Tổng khoảng 0,5–7 triệu.**
- **Hằng tháng**: VPS hoặc máy chủ game ở Việt Nam khoảng 16 GB, CPU mạnh, chống DDoS cho game, khoảng 0,5–1,5 triệu. Sao lưu, tên miền, quảng cáo (tùy chọn). **Tổng khoảng 0,6–3,6 triệu.**
- Minecraft cần **CPU mạnh trên từng nhân** hơn là nhiều nhân. **Gói VPS quá rẻ thường CPU yếu hoặc bán vượt tải**, nên dùng thử trước khi thuê.

### Giai đoạn 2–3: nhiều chế độ
- Máy chủ riêng khoảng 64 GB. Ví dụ OVH Singapore GAME-1 (Ryzen 7 9800X3D, 64 GB) khoảng **S$209,99/tháng chưa VAT (khoảng 4 triệu)**, phí cài đặt tháng đầu bằng 1 tháng tiền thuê. OVH có điều chỉnh giá nửa cuối 2026, cần kiểm tra lại.
- **Tổng khoảng 5–10 triệu/tháng.** Chỉ nâng cấp khi số người chơi thật sự cần.

### Hòa vốn giai đoạn 1 (ví dụ chi phí 1,5 triệu/tháng)
- Thẻ cào trừ khoảng 20% phí, nên cần thu khoảng 1,9 triệu, tức khoảng **24 người nạp**, mỗi người trung bình 80k.
- Với tỉ lệ người nạp 3%, cần khoảng **800 người chơi trong tháng**, tương đương khoảng **40–60 người online giờ cao điểm**.

---

## 8. Hướng kinh doanh phụ (tiềm năng, làm sau)

Người làm dự án quan tâm đến cả 3 hướng sau, và chúng hỗ trợ lẫn nhau:

1. **Dịch vụ cho chủ server ("bán xẻng cho người đào vàng")**
   - ⚠️ Phần **nhận tiền và cấp rank đã có sẵn và miễn phí** (DotMan, Card2k, Tebex có MoMo), nên **không cạnh tranh ở phần này**.
   - Hướng khác biệt: bán **"phần hậu trường"** (báo cáo doanh thu, hồ sơ người chơi, quản lý nhiều server, tổng hợp thuế, kết nối DotMan, Tebex, cổng thẻ, VietQR). Phần lớn có thể **dùng lại các module của app bán hàng**.
   - Rủi ro: thị trường nhỏ, cần kiểm chứng chủ server có chịu trả tiền hay không. **Làm sau cùng**, sau khi phỏng vấn 10–20 chủ server Việt.
2. **Bán plugin hoặc bộ cài server làm sẵn**
   - BuiltByBit (phí khoảng 9,9%), Polymart, SpigotMC. Diễn đàn Việt: minecraftvn.net, minevn.net.
   - Bán chính những thứ đã tự làm cho server mình: phần bang chiến theo lịch, công cụ cho server có cả PC và điện thoại (tự sinh menu Bedrock Forms, xử lý tên Bedrock), bộ cài làm sẵn.
3. **Server của chính mình + content**: là nơi thử nghiệm, nguồn content, và "khách hàng đầu tiên" cho hướng 1 và 2.

Các hướng khác đã cân nhắc nhưng chưa chọn: game trên Roblox, server GTA V RP (FiveM).

---

## 9. Việc cần làm tiếp theo (theo thứ tự)

- [ ] **Kiểm tra máy nhà**: CPU, RAM, ổ cứng, hệ điều hành (Windows hay Linux). Kiểm tra mạng có bị CGNAT không.
- [ ] **Thiết kế luật chơi Bang hội chiến**: lập bang, chiếm đất, lịch công thành, combat, phần thưởng mùa, luật chống lạm dụng.
- [ ] **Chọn nền tảng kỹ thuật cho bang chiến**: Towny + SiegeWar, hay Factions kèm phần tự viết. Nên thử cả hai trên máy nhà.
- [ ] **Dựng khung network trên máy nhà**: Docker hoặc Pterodactyl, Velocity, Geyser + Floodgate, lobby, MariaDB, LuckPerms.
- [ ] Cài và cấu hình chế độ Bang hội chiến cùng các plugin đi kèm.
- [ ] Đưa cấu hình lên Git, thiết lập sao lưu.
- [ ] Mua tên miền và trỏ qua playit.gg.
- [ ] **Thử kín với bạn bè**. Mời sẵn vài nhóm hoặc bang.
- [ ] Mở Discord, chuẩn bị ghi hình.
- [ ] Thiết kế **bảng rank, gói mùa và đồ trang trí** đúng luật, cùng giá cả.
- [ ] Viết **web store** (VietQR + DotMan cho thẻ cào), xử lý cả tên Java và Bedrock, cấp rank qua RCON hoặc plugin.
- [ ] Khi sẵn sàng mở công khai: chọn và thử máy chủ thuê (Việt Nam hoặc Singapore), chuyển dữ liệu, bật chống DDoS.
- [ ] Sau khoảng 6 tháng: đánh giá chỉ số (người chơi cao điểm, tỉ lệ quay lại, tỉ lệ nạp), cân nhắc mở chế độ 2 và các hướng kinh doanh phụ.

## 10. Câu hỏi còn mở
- Xác nhận cuối cùng: chế độ chủ lực là Bang hội chiến hay phương án dự phòng (Survival kinh tế + bang nhẹ)?
- Có cho người chơi crack (offline mode) vào không? Nếu có thì nhiều người hơn, nhưng là vùng xám về pháp lý và khó kiếm tiền bền vững.
- Tên server, thương hiệu, tên miền.
- Có làm content không, và ai làm?

---

## 11. Nguồn tham khảo
- Thể loại server: https://minecraft-serverlist.com/gamemodes · https://minecraft-stats.com/blog/top-10-most-popular-minecraft-servers-right-now-(2026)
- Luật kiếm tiền của Mojang: https://www.pcgamesn.com/minecraft/mojang-clarify-new-server-rules-prevent-minecraft-servers-becoming-pay-win · https://mats.coffee/blog/mojang-blocklist-deepdive · https://mc-node.net/blog/en/minecraft-server-monetization-eula-rules/
- DotMan: https://github.com/minevn/dotman
- Tebex: https://www.tebex.io/payment-methods · https://docs.tebex.io/creators/pricing-and-plans/tebex-platform-fee
- Chợ plugin: https://mclicense.org/blog/spigotmc-vs-polymart-vs-builtbybit
- OVH Singapore: https://www.ovhcloud.com/en-sg/bare-metal/game/ · https://blog.ovhcloud.com/en/posts/dedicated-servers-pricing-update-h2-2026/
- Hosting Việt Nam (tham khảo): https://vietnix.vn/vps/ · https://vpsmmo.vn/ · https://pikamc.vn/

---

## Gợi ý "Project instructions" khi tạo Claude Project mới

> Tôi đang xây dựng một network server Minecraft cho người chơi Việt Nam (Java Paper + Geyser/Floodgate cho Bedrock, proxy Velocity). Chế độ chủ lực đầu tiên là "Bang hội chiến" (Factions kiểu mới: chiến tranh theo lịch, theo mùa, combat 1.8). Giai đoạn đầu chạy trên máy nhà qua playit.gg. Kiếm tiền tuân thủ Minecraft Usage Guidelines (không pay-to-win, không bán cape). Tôi là dev, đã có app quản lý bán hàng (đơn hàng, sản phẩm, khách hàng, báo cáo, thuế, kết nối bên thứ 3, đa cửa hàng) và muốn tận dụng cho web store và thanh toán (VietQR, thẻ cào qua DotMan). Xem file kế hoạch đính kèm để biết các quyết định đã chốt, công cụ, chi phí và việc cần làm. Hãy trả lời bằng tiếng Việt, thực tế, và nói rõ khi thông tin cần kiểm tra lại.
