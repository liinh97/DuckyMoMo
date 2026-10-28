# Ý tưởng phù phép riêng lấy cảm hứng từ chiêu tướng LMHT

Nguồn: dữ liệu chính thức Riot Data Dragon bản 16.21.1 (championFull.json), đủ **173 tướng / 865 chiêu** (nội tại + Q W E R), tra ngày 22/10/2026.
Gom theo **cơ chế** (nhiều tướng dùng chung một kiểu), chỉ giữ cái làm được trong Minecraft theo kiểu phù phép riêng
`SkillEnchants.java` (cầm đồ chuột phải, cấp I–III, hồi chiêu theo người).

**Đặt tên riêng tiếng Việt, không dùng tên tướng / tên chiêu Riot** (thương hiệu). Cơ chế thì dùng tự do.

Ký hiệu cột **Lag**: `0` = tính một lần lúc bấm (như Lướt); `1` = chạy hẹn giờ ngắn vài giây sau khi bấm; `2` = phải lưu trạng thái lâu
hoặc kiểm mỗi bước đi / sinh thêm thực thể (có thể nặng nếu nhiều người dùng).
Cột **PvP**: `!` = mạnh trong PvP / công thành, nên tắt hoặc giảm trong vùng vây đang đánh.

Server đã có: Lướt, Hồi máu, Cầu lửa, Bay lên, Dịch chuyển, Dậm đất, Huyết chiến, Phóng bay, Khổng lồ, Thu nhỏ (SkillEnchants);
Móc câu bay, Hút máu, Khiên thần, Kiếm nhật lướt chém, Rìu chiến chí mạng (Xưởng). CrazyEnchantments có nhiều phù phép bị động
khi đánh trúng (chưa đối chiếu hết, có thể trùng nhóm F).

## A. Khống chế một mục tiêu (bắn tia / phóng vật, trúng cái đầu tiên)

| Tên gợi ý | Lấy ý từ | Làm trong Minecraft | Lag | PvP |
|---|---|---|---|---|
| Trói | Morgana Q, Lux Q, Zyra E, Ivern Q, Rengar E, Neeko E | Trúng thì đứng im 1–2 giây (chậm cực mạnh + không nhảy), vẫn đánh được | 0 | ! |
| Choáng xa | Ashe R, Xerath E, Elise E, Sejuani R | Mũi tên băng: choáng lâu hơn nếu bay xa hơn (Ashe R) | 0 | ! |
| Mê hoặc | Ahri E, Evelynn W, Seraphine R, Rakan R | Trúng thì 1,5 giây bị kéo từ từ về phía mình, không đánh được | 1 | ! |
| Hoảng sợ | Fiddlesticks Q, Nocturne E, Warwick E, Hecarim R, Shaco W | Quái bỏ chạy khỏi mình; người bị đẩy lùi + chậm | 1 | ! |
| Ru ngủ | Zoe E, Lillia R | 1 giây sau mới ngủ (đứng im tới 3 giây); đòn đầu tiên đánh thức gây ×2 sát thương | 1 | ! |
| Câm lặng | Cho'Gath W, Soraka E, Fiddlesticks E, Malzahar Q, Blitzcrank R, Garen Q | Mục tiêu **không dùng được phù phép riêng** vài giây. Chiêu khắc chế, rất hợp với hệ phù phép của server | 0 | |
| Phi tiêu mù | Teemo Q, Quinn Q, Graves W | Mù (Blindness) 2–3 giây | 0 | |
| Hoá cừu | Lulu W | 1,5 giây không gây sát thương được + chậm (huỷ sát thương mục tiêu gây ra) | 1 | ! |
| Ánh mắt hoá đá | Cassiopeia R | Hình nón trước mặt: ai **đang nhìn về phía mình** thì choáng, quay lưng thì chỉ chậm | 0 | ! |
| Lão hoá | Nasus W | Chậm tăng dần + Mỏi tay (Mining Fatigue) 4 giây | 1 | |
| Khiêu khích | Rammus E, Shen E, Galio W | Quái quanh mình quay sang đánh mình (Mob#setTarget). Hợp đánh boss theo nhóm | 0 | |
| Đánh dấu tử thần | Zed R, Tristana E, Zilean Q | Gắn dấu; 3 giây sau nổ, lặp lại X% sát thương đã gây trong 3 giây đó | 1 | ! |

## B. Kéo, đẩy, hất

| Tên gợi ý | Lấy ý từ | Làm trong Minecraft | Lag | PvP |
|---|---|---|---|---|
| Móc kéo | Blitzcrank Q, Thresh Q, Pyke Q, Kled Q | Bắn móc, kéo mục tiêu đầu tiên về mình | 0 | ! |
| Neo hai chiều | Nautilus Q | Trúng người: cả hai kéo vào nhau; trúng tường: kéo mình tới tường | 0 | |
| Câu vùng | Darius E, Mordekaiser E | Kéo mọi kẻ địch trong hình nón phía trước về sát mình | 0 | ! |
| Hố đen | Diana R, Aurelion Sol E, Rell R | Hút mọi thứ quanh 5 khối về giữa trong 2 giây | 1 | ! |
| Đập hai bên | Sett E | Kéo kẻ địch hai bên vào; có người ở cả hai phía thì choáng | 0 | ! |
| Đẩy văng vào tường | Vayne E, Poppy E | Đẩy mục tiêu lùi xa; đập tường thì choáng + thêm sát thương | 0 | |
| Cú đá rồng | Lee Sin R | Đá mục tiêu bay lùi; ai bị nó va trúng cũng bị hất tung | 1 | ! |
| Ném cả đám | Gnar R, Janna R, Alistar W, Tristana R | Hất mọi thứ quanh mình về một hướng | 0 | ! |
| Lốc hất tung | Yasuo Q, Janna Q | Bắn lốc theo đường thẳng, ai trúng bị hất lên không | 1 | |
| Tự nổ nhảy xa | Ziggs W, Tristana W | Nổ dưới chân, hất mình bay xa (không mất máu, không phá khối), người gần bị đẩy ra | 0 | |
| Ném đồng đội / kẻ địch | Renata Q, Zac Q, Sett R | Trói rồi ném mục tiêu theo hướng mình nhìn | 1 | ! |

## C. Di chuyển

| Tên gợi ý | Lấy ý từ | Làm trong Minecraft | Lag | PvP |
|---|---|---|---|---|
| Lao tới mục tiêu | Jax Q, Irelia Q, Katarina E, Diana E, Lee Sin Q | Nhìn vào sinh vật trong 10 khối, lao tới cạnh nó và chém | 0 | ! |
| Đánh dấu rồi lao | Akali E, Lee Sin Q, Vex R | Lần 1 bắn dấu; lần 2 trong 3 giây lao tới chỗ người bị dấu | 1 | ! |
| Tua thời gian | Ekko R | Bấm lần 1 đặt mốc; trong 4 giây bấm lần 2 (hoặc tự động khi hết) quay về mốc + hồi lại máu đã mất | 1 | ! |
| Lướt rồi quay về | LeBlanc W, Zoe R | Lướt tới; trong 4 giây bấm lại thì về chỗ cũ | 1 | ! |
| Bám tường | Camille E, Akshan E | Móc vào tường, đu tới rồi bật ra, đáp xuống hất tung | 1 | |
| Húc tăng tốc | Sion R, Hecarim E, Gragas E | Chạy thẳng nhanh dần 3 giây; va vào ai thì đánh văng theo quãng đã chạy | 1 | ! |
| Phi nước đại | Quinn R, Zeri R, Blitzcrank W | Tốc chạy rất cao 4 giây, bị đánh là mất | 1 | |
| Tốc biến đồng đội | Shen R, Kalista R | Dịch chuyển tới người cùng quốc gia đang ít máu + khiên cho họ | 1 | ! |

Không nên: đi xuyên tường (Kayn E, Talon E): vào nhà / thành người khác được. Cổng dịch chuyển xa, cả bản đồ (Bard E, Ryze R,
Twisted Fate R, Pantheon R): phá cân bằng di chuyển và công thành.

## D. Phòng thủ, sống sót, hỗ trợ

| Tên gợi ý | Lấy ý từ | Làm trong Minecraft | Lag | PvP |
|---|---|---|---|---|
| Khiên phép | Janna E, Lux W, Karma E, Lulu E, Diana W | Tim vàng (absorption) cho mình hoặc đồng đội đang nhìn | 0 | |
| Khiên gai | Annie E | Khiên + ai đánh mình trong 4 giây bị phản sát thương | 1 | |
| Đóng băng thời gian | Lissandra R, Zhonya | Tự đóng băng 2 giây: đứng im, bất tử, hồi chút máu | 1 | ! |
| Vũng máu | Vladimir W | 2 giây chìm xuống, không ai đánh được, hút máu người đứng gần | 1 | ! |
| Bất tử | Tryndamere R, Renata W | 4–5 giây máu không xuống dưới nửa tim; hồi chiêu 2–3 phút | 1 | ! |
| Vùng bất tử | Kindred R, Taric R | Vùng 5 khối: không ai chết trong 3 giây (cả địch lẫn ta, như Kindred) | 1 | ! |
| Hồi sinh tại chỗ | Zilean R, Anivia nội tại | Gắn lên giáp: nhận đòn chí mạng thì hồi lại 4 tim thay vì chết, hồi chiêu 5 phút (giống vật tổ) | 0 | ! |
| Né đòn rồi phản | Jax E, Fiora W | 2 giây né mọi đòn cận chiến, hết giờ choáng người xung quanh | 1 | ! |
| Chắn đạn | Braum E, Yasuo W | 4 giây chặn mũi tên / đạn bay tới từ phía trước. Rất hợp công thành có cung thủ | 1 | |
| Lá chắn phép | Sivir E, Nocturne W, Morgana E | Chặn một phù phép riêng của kẻ địch nhắm vào mình (trói, kéo, câm lặng…) | 0 | |
| Giải khống chế | Alistar R, Gangplank W, Olaf R, Milio R | Xoá hiệu ứng xấu (chậm, mù, độc…) + giảm 50% sát thương 3 giây | 0 | |
| Hồi máu đồng đội | Soraka W, Sona W, Nami W, Taric Q | Hồi máu người cùng thị trấn / quốc gia mình nhìn vào | 0 | |
| Điều ước | Soraka R | Hồi máu mọi đồng đội cùng quốc gia ở gần (bán kính lớn), hồi chiêu dài | 0 | |
| Thiền | Master Yi W | Đứng yên tối đa 3 giây: hồi máu nhanh + giảm sát thương; di chuyển là dừng | 1 | |
| Phóng to đồng đội | Lulu R | Đồng đội to ra + thêm máu tối đa 6 giây (dùng lại phần Khổng lồ) | 1 | |
| Soi sáng | Ashe E, Rengar R, Corki Q, Diana Q | Mọi người trong 30 khối phát sáng (Glowing) 5 giây, lộ cả người tàng hình | 0 | |

## E. Sát thương diện rộng

| Tên gợi ý | Lấy ý từ | Làm trong Minecraft | Lag | PvP |
|---|---|---|---|---|
| Lốc xoáy | Garen E, Wukong R, Katarina R | Xoay chém 3 giây, cứ 0,5 giây gây sát thương xung quanh | 1 | ! |
| Lưỡi rìu | Darius Q | Chém vòng: ai ở mép (2–3 khối) dính nặng và mình hồi máu, ở sát dính nhẹ | 0 | |
| Tia sáng | Lux R, Ezreal R, Xerath Q, Viktor E | Tia thẳng 15–20 khối, trúng mọi thứ trên đường | 0 | |
| Pháo kích | Ziggs R, Kog'Maw R, Veigar W, Xerath W, Brand W | Chỉ vào chỗ xa 20–30 khối, 1–1,5 giây sau nổ (không phá khối) | 1 | ! |
| Mưa đạn | Gangplank R, Miss Fortune E, Anivia R | Vùng 6 khối bị bắn từng đợt + chậm trong 4 giây | 1 | ! |
| Phun lửa | Annie W, Rumble Q, Brand… | Hình nón phía trước gây cháy | 0 | |
| Sét nảy | Brand R, Ryze Q, Sivir W, Katarina Q | Đánh một mục tiêu rồi nảy sang tối đa 4 mục tiêu gần nhau | 1 | |
| Bom dính | Zilean Q, Tristana E | Ném bom dính vào người, 3 giây sau nổ quanh họ | 1 | ! |
| Bão quạ | Fiddlesticks R, Nunu R | Vùng quanh mình gây sát thương mỗi giây 4 giây (Nunu: nổ to lúc cuối) | 1 | ! |
| Liên hoàn đao | Master Yi Q | Nhảy chém lần lượt 3 mục tiêu gần nhất rồi về chỗ cũ, không bị đánh trong lúc đó | 1 | ! |

## F. Bị động trên vũ khí / cường hoá đòn kế tiếp

(CrazyEnchantments có nhiều loại này rồi, nên đối chiếu trước khi làm.)

| Tên gợi ý | Lấy ý từ | Làm trong Minecraft | Lag | PvP |
|---|---|---|---|---|
| Đòn thứ ba / tư | Kennen, Braum, Vayne W, Ekko, Diana | Mỗi đòn thứ 3 hoặc 4 trúng cùng mục tiêu: choáng hoặc +% máu tối đa | 0 | ! |
| Chảy máu cộng dồn | Darius nội tại, Talon nội tại | Mỗi đòn +1 tầng chảy máu (tối đa 5); đủ 5 thì mình được nộ | 0 | |
| Đòn kế tiếp | Garen Q, Leona Q, Blitzcrank E, Nautilus nội tại | Bấm là cường hoá: đòn đánh kế tiếp câm lặng / choáng / hất tung | 0 | ! |
| Kết liễu | Garen R, Pyke R, Urgot R, Veigar R | Sát thương tăng theo máu đã mất của mục tiêu; giết được thì hồi chiêu ngay (Darius R) | 0 | ! |
| Cuồng huyết | Olaf nội tại, Warwick nội tại | Càng ít máu càng đánh nhanh và hút máu nhiều | 0 | |
| Rút bài | Twisted Fate W | Xoay 3 lá: đỏ = nổ vùng chậm, xanh = hồi máu, vàng = choáng; bấm lại để chốt lá | 1 | |

## G. Để sau hoặc không làm

- **Bẫy, mìn, nấm** (Caitlyn W, Jinx E, Teemo R, Jhin E, Ziggs E, Nidalee W, Shaco W): phải lưu vị trí và kiểm mỗi bước đi. Nhiều người
  đặt là nặng thật. Muốn làm thì giới hạn 3 bẫy / người, tự mất sau 60 giây.
- **Tường, cột, hộp** (Anivia W, Trundle E, Taliyah R, Yorick W, Thresh R, Jarvan IV R, Azir R): đặt khối thật, đụng Towny / CoreProtect,
  có thể chặn cửa thành, kẹt người.
- **Triệu hồi** (Annie R gấu, Heimerdinger trụ, Malzahar W, Yorick, Zyra cây, Ivern R): sinh thêm thực thể, phải dọn khi thoát / sập.
  Làm được (quái tạm 10 giây), nhưng dễ lỗi farm kinh nghiệm hoặc lợi dụng.
- **Phân thân, giả dạng** (Shaco R, Wukong W, LeBlanc nội tại, Neeko nội tại): cần NPC giả người chơi hoặc LibsDisguises.
- **Vệt độc, vùng kéo dài** (Singed Q, Kog'Maw E, Cassiopeia W, Soraka E): kiểm va chạm liên tục, nặng hơn các chiêu khác.
- **Xuyên tường, cả bản đồ** (Kayn E, Talon E, Karthus R, Twisted Fate R, Pantheon R): phá luật đất đai / công thành.
- **Hút chỉ số, lấy chiêu người khác** (Mordekaiser R, Sylas R, Viego nội tại, Trundle R): phức tạp, khó cân bằng.

## Đã làm (22/10, SkillEnchants.java, chưa test trong game)

Câm lặng `cam_lang`, Trói `troi`, Móc kéo `moc_keo`, Tua thời gian `tua_thoi_gian`, Khiên phép `khien_phep`,
Hồi máu đồng đội `hoi_mau_dong_doi`, Chắn đạn `chan_dan`, Soi sáng `soi_sang`. Thông số: đầu file SkillEnchants.java.

Đợt 2 (người dùng: "loại tính năng trùng"): bỏ ý trùng với phù phép riêng, kỹ năng Xưởng, 105 phù phép CrazyEnchantments
(vd Phi tiêu mù = CE Blindness; Lão hoá = CE Snare/Paralyze; Kết liễu = CE Execute; Phi nước đại = CE Gears; Bám tường = Móc câu bay;
Lướt rồi quay về = Tua thời gian; Câu vùng / Đập hai bên = Móc kéo / Hố đen; Phóng to đồng đội = Khổng lồ…). Làm 18 cái còn lại:
Hoảng sợ, Hoá cừu, Ánh mắt hoá đá, Khiêu khích, Đánh dấu tử thần, Hố đen, Đẩy văng, Hộ giá, Đóng băng thời gian, Bất tử, Lá chắn phép,
Giải khống chế, Lốc xoáy, Tia sáng, Pháo kích, Sét nảy, Liên hoàn đao, Rút bài. Phần còn lại của danh sách: coi như trùng, không làm.

## Gợi ý làm trước (dễ, gần như không lag, đa dạng kiểu chơi)

1. Câm lặng: khắc chế chính hệ phù phép riêng, làm PvP có chiều sâu.
2. Trói và Móc kéo: khống chế cơ bản.
3. Tua thời gian: lạ, rất hợp Minecraft, không ai có.
4. Khiên phép và Hồi máu đồng đội: thêm vai hỗ trợ cho quốc gia / công thành.
5. Chắn đạn: chống cung thủ khi công thành.
6. Soi sáng: khắc chế tàng hình và người núp.
7. Đẩy văng vào tường: kỹ năng cần ngắm, vui.


## Đợt 3 (28/10, đã làm)

| Tên | Lấy ý | Làm gì | Cấp |
|---|---|---|---|
| Tàng hình | Shaco Q | Lướt 3–5 khối, vô hình hẳn 1,5–2,5 giây, đòn đầu +30–60% sát thương | 1–3 |
| Nhảy cao | — | Bật lên ~5/9/14 khối, không mất máu khi rơi | 1–3 |
| Súng cao su | Zac E | Bấm kéo dây (tối đa 1,2 giây), bấm lại bắn mình xa 8/11/14 khối, đáp đất hất tung + 3/4/5 sát thương | 1–3 |
| Phản công | Jax E | Né mọi đòn 2 giây, rồi choáng 1 giây + 2/3/4 sát thương quanh 3 khối, +20%/đòn đã né | 1–3 |
